package com.cemail.app.data.graph

import com.cemail.app.AppLog
import com.cemail.app.data.db.MessageDao
import com.cemail.app.data.db.MessageEntity
import com.cemail.app.data.gmail.AttachmentFinder
import com.cemail.app.data.gmail.AttachmentInfo
import com.cemail.app.data.gmail.GmailHttpException
import com.cemail.app.data.gmail.MimeBuilder
import com.cemail.app.data.gmail.ReplyInfo
import com.cemail.app.data.gmail.ReplyInfoParser
import com.cemail.app.data.mail.Folder
import com.cemail.app.data.mail.Label
import com.cemail.app.data.mail.Mail
import com.cemail.app.data.mail.MailRepository
import com.cemail.app.data.mail.capped
import com.cemail.app.data.mail.toEntity
import com.cemail.app.data.mail.toMail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.File
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

/**
 * One Microsoft account (Outlook.com, Hotmail, Microsoft 365) cached in Room, read through Microsoft Graph. Like the
 * other repositories, the UI reads only from Room and the network writes into it. Microsoft's folders map to
 * Cemail's folders; its conversation id becomes the thread id. Messages are listed folder by folder (a window of the
 * newest ones), not with Graph's delta queries yet.
 */
class MicrosoftMailRepository(
    private val accountId: String,
    private val api: GraphApi,
    private val dao: MessageDao,
    private val scope: CoroutineScope,
    private val attachmentsDir: File,
    private val selfEmail: suspend () -> String? = { null },
    private val onNewMail: (accountId: String, mails: List<Mail>) -> Unit = { _, _ -> },
) : MailRepository {
    override val mails: StateFlow<List<Mail>> = dao.observe(accountId)
        .map { list -> list.map { it.toMail() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override val labels: Flow<List<Label>> = flowOf(emptyList())
    override suspend fun userLabels(accountId: String): List<Label> = emptyList()

    private val _syncing = MutableStateFlow(false)
    override val syncing: StateFlow<Boolean> = _syncing

    /** Changes still on their way to Microsoft: a sync in the meantime would see half-moved messages. */
    private val pending = AtomicInteger(0)

    override fun snapshot(ids: Set<Long>) = mails.value.filter { it.id in ids }

    override fun update(ids: Set<Long>, transform: (Mail) -> Mail) {
        write(snapshot(ids).map { it to transform(it) })
    }

    override fun restore(previous: List<Mail>) {
        val current = mails.value.associateBy { it.id }
        write(previous.mapNotNull { old -> current[old.id]?.let { it to old } })
    }

    private fun write(changes: List<Pair<Mail, Mail>>) {
        pending.incrementAndGet()
        scope.launch(Dispatchers.IO) {
            try {
                for ((before, after) in changes) {
                    dao.updateFlags(after.id, after.folder.name, after.read, after.starred, after.pinned, after.snoozedUntil)
                    val remote = dao.remoteIdOf(after.id) ?: continue
                    try {
                        if (before.read != after.read || before.starred != after.starred) {
                            api.update(remote, after.read.takeIf { it != before.read }, after.starred.takeIf { it != before.starred })
                        }
                        if (before.folder != after.folder) {
                            val target = wellKnown(after.folder)
                            if (target != null) api.move(remote, target)
                        }
                    } catch (e: Exception) {
                        AppLog.e("graph", "could not apply the change on the server", e)
                    }
                }
            } finally {
                pending.decrementAndGet()
            }
        }
    }

    override fun add(mail: Mail) {
        scope.launch(Dispatchers.IO) { dao.insert(mail.toEntity(accountId, remoteId = null).copy(localId = 0)) }
    }

    override fun send(mail: Mail) {
        scope.launch(Dispatchers.IO) {
            val localId = dao.insert(mail.toEntity(accountId, remoteId = null).copy(localId = 0, folder = Folder.SENT.name))
            try {
                api.sendMime(
                    MimeBuilder.build(
                        mail.senderName, mail.subject, mail.body, mail.outgoing, mail.cc, mail.bcc, mail.outgoingHtml,
                        mail.inReplyTo, mail.references, mail.outgoingImages,
                    ),
                )
                AppLog.i("send", "sent through Microsoft Graph, ${mail.outgoing.size} attachment(s)")
                dao.delete(localId) // the real copy arrives with the next sync
                refresh()
            } catch (e: Exception) {
                AppLog.e("send", "could not send, kept as a draft", e)
                dao.updateFolder(localId, Folder.DRAFTS.name)
            }
        }
    }

    override fun nextId() = 0L

    override fun remove(id: Long) {
        scope.launch(Dispatchers.IO) { dao.delete(id) }
    }

    override suspend fun refresh(quick: Boolean): Boolean {
        if (pending.get() > 0) return true // changes are on their way; the next sync will see them done
        if (!_syncing.compareAndSet(expect = false, update = true)) return true
        return try {
            sync(quick)
            true
        } catch (e: Exception) {
            AppLog.e("sync", "...${accountId.takeLast(4)} Microsoft ${if (quick) "quick" else "full"} sync failed", e)
            false
        } finally {
            _syncing.value = false
        }
    }

    private suspend fun sync(quick: Boolean) {
        val cached = dao.remoteOnes(accountId).associateBy { it.remoteId!! }
        val seen = HashSet<String>()
        val done = HashSet<Folder>()
        val arrived = ArrayList<MessageEntity>()
        for (folder in FOLDERS) {
            if (quick && folder != Folder.INBOX) continue
            val target = wellKnown(folder) ?: continue
            try {
                val listed = api.messages(target, if (folder == Folder.INBOX) INBOX_LIMIT else OTHER_LIMIT)
                for (m in listed) {
                    seen += m.id
                    val old = cached[m.id]
                    val read = m.isRead
                    val starred = m.flag?.flagStatus == "flagged"
                    if (old == null) {
                        val entity = m.toEntity(accountId, folder)
                        dao.insert(entity)
                        if (!entity.read && folder == Folder.INBOX && entity.timeMillis > System.currentTimeMillis() - RECENT_MILLIS) {
                            arrived += entity
                        }
                    } else if (old.read != read || old.starred != starred || old.folder != folder.name) {
                        dao.updateState(old.localId, folder.name, read, starred, old.labelIds)
                    }
                }
                done += folder
            } catch (e: GmailHttpException) {
                if (e.code == 401 || e.code == 403) throw e // signed out or no permission: the whole sync fails
                AppLog.e("sync", "...${accountId.takeLast(4)} a Microsoft folder could not be read", e)
            }
        }
        val gone = cached.filter { (id, row) -> id !in seen && runCatching { Folder.valueOf(row.folder) }.getOrNull() in done }
            .values.map { it.localId }
        gone.chunked(500).forEach { dao.deleteAll(accountId, it) }
        AppLog.i("sync", "...${accountId.takeLast(4)} Microsoft ${if (quick) "quick" else "full"} ok: ${done.size} folder(s), ${seen.size} listed, ${arrived.size} new unread, ${gone.size} removed")
        if (cached.isNotEmpty() && arrived.isNotEmpty()) {
            onNewMail(accountId, arrived.sortedByDescending { it.timeMillis }.map { it.toMail() })
        }
    }

    override suspend fun full(id: Long): Mail? = withContext(Dispatchers.IO) {
        try {
            val light = dao.light(id)?.takeIf { it.accountId == accountId } ?: return@withContext null
            val body = StringBuilder()
            var start = 1
            while (true) {
                val piece = dao.bodyChunk(id, start, CHUNK).orEmpty()
                body.append(piece)
                if (piece.length < CHUNK) break
                start += CHUNK
            }
            var html: StringBuilder? = null
            start = 1
            while (true) {
                val piece = dao.htmlChunk(id, start, CHUNK) ?: break
                (html ?: StringBuilder().also { html = it }).append(piece)
                if (piece.length < CHUNK) break
                start += CHUNK
            }
            light.copy(body = body.toString(), bodyHtml = html?.toString()).toMail()
        } catch (e: Exception) {
            AppLog.e("db", "could not read message $id", e)
            null
        }
    }

    override suspend fun replyInfo(id: Long): Pair<ReplyInfo, String?>? = withContext(Dispatchers.IO) {
        val remote = dao.remoteIdOf(id, accountId) ?: return@withContext null
        try {
            val m = api.message(remote, "internetMessageId,internetMessageHeaders,conversationId,from,toRecipients,ccRecipients,replyTo")
            fun addresses(list: List<GraphRecipient>) = list.mapNotNull { it.emailAddress?.address }
            ReplyInfo(
                threadId = m.conversationId,
                messageId = m.internetMessageId?.trim()?.takeIf { it.isNotEmpty() },
                references = m.internetMessageHeaders.firstOrNull { it.name.equals("References", true) }?.value?.trim()?.takeIf { it.isNotEmpty() },
                replyTo = addresses(m.replyTo),
                from = listOfNotNull(m.from?.emailAddress?.address),
                to = addresses(m.toRecipients),
                cc = addresses(m.ccRecipients),
            ) to selfEmail()
        } catch (e: Exception) {
            AppLog.e("reply", "could not read the headers of the message", e)
            null
        }
    }

    override suspend fun emlFile(id: Long): File? = withContext(Dispatchers.IO) {
        val remote = dao.remoteIdOf(id, accountId) ?: return@withContext null
        try {
            val subject = dao.light(id)?.subject.orEmpty()
            val dir = File(attachmentsDir, "eml").apply { mkdirs() }
            File(dir, AttachmentFinder.safeFileName(subject.ifBlank { "message" }).take(60) + ".eml").also { it.writeBytes(api.raw(remote)) }
        } catch (e: Exception) {
            AppLog.e("eml", "could not save the message as .eml", e)
            null
        }
    }

    override suspend fun attachments(id: Long): List<AttachmentInfo> = withContext(Dispatchers.IO) {
        val remote = dao.remoteIdOf(id, accountId) ?: return@withContext emptyList()
        try {
            api.attachments(remote)
        } catch (e: Exception) {
            AppLog.e("attach", "could not list attachments", e)
            emptyList()
        }
    }

    override suspend fun attachmentFile(id: Long, attachment: AttachmentInfo): File? = withContext(Dispatchers.IO) {
        val remote = dao.remoteIdOf(id, accountId) ?: return@withContext null
        try {
            val dir = File(attachmentsDir, remote.filter { it.isLetterOrDigit() }.takeLast(40)).apply { mkdirs() }
            val file = File(dir, AttachmentFinder.safeFileName(attachment.filename))
            if (file.exists() && file.length() > 0) return@withContext file
            file.writeBytes(api.attachmentBytes(remote, attachment.attachmentId))
            file
        } catch (e: Exception) {
            AppLog.e("attach", "download failed", e)
            null
        }
    }

    /** Pictures embedded in these messages are not fetched yet: they simply stay out of the page. */
    override suspend fun inlineImages(id: Long): Map<String, String> = emptyMap()

    private fun wellKnown(folder: Folder) = when (folder) {
        Folder.INBOX -> "inbox"
        Folder.SENT -> "sentitems"
        Folder.DRAFTS -> "drafts"
        Folder.TRASH -> "deleteditems"
        Folder.ARCHIVE -> "archive"
    }

    private companion object {
        val FOLDERS = listOf(Folder.INBOX, Folder.SENT, Folder.DRAFTS, Folder.TRASH, Folder.ARCHIVE)
        const val INBOX_LIMIT = 100
        const val OTHER_LIMIT = 40
        const val RECENT_MILLIS = 3L * 24 * 60 * 60 * 1000
        const val CHUNK = 250_000
    }
}

private fun GraphMessage.toEntity(accountId: String, folder: Folder): MessageEntity {
    val isHtml = body?.contentType.equals("html", ignoreCase = true)
    val html = if (isHtml) body?.content.orEmpty() else ""
    val plain = if (isHtml) Jsoup.parse(html).text() else body?.content.orEmpty()
    return MessageEntity(
        accountId = accountId, remoteId = id, folder = folder.name,
        senderName = from?.emailAddress?.name?.takeIf { it.isNotBlank() } ?: from?.emailAddress?.address.orEmpty(),
        senderEmail = from?.emailAddress?.address.orEmpty(), subject = subject.orEmpty(), body = plain,
        timeMillis = receivedDateTime?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L,
        read = isRead, starred = flag?.flagStatus == "flagged", pinned = false, snoozedUntil = null,
        hasAttachment = hasAttachments, labelIds = "", bodyHtml = html, threadId = conversationId,
    ).capped()
}
