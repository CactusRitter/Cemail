package com.cemail.app.data.graph

import com.cemail.app.AppLog
import com.cemail.app.data.db.MessageDao
import com.cemail.app.data.db.MessageEntity
import com.cemail.app.data.db.KeptMessage
import com.cemail.app.data.db.MutedThread
import com.cemail.app.data.gmail.AttachmentFinder
import com.cemail.app.data.gmail.AttachmentInfo
import com.cemail.app.data.gmail.GmailHttpException
import com.cemail.app.data.gmail.MimeBuilder
import com.cemail.app.data.gmail.ReplyInfo
import com.cemail.app.data.gmail.ReplyInfoParser
import com.cemail.app.data.mail.Folder
import com.cemail.app.data.mail.Label
import com.cemail.app.data.mail.Mail
import com.cemail.app.data.mail.MailPlace
import com.cemail.app.data.mail.MailRepository
import com.cemail.app.data.mail.OLDER_PAGE
import com.cemail.app.data.mail.SEARCH_SCAN
import com.cemail.app.data.mail.capped
import com.cemail.app.data.mail.Unsubscribe
import com.cemail.app.data.mail.UnsubscribeInfo
import com.cemail.app.data.mail.cancelOutbox
import com.cemail.app.data.mail.deliverOutbox
import com.cemail.app.data.mail.scheduleOutbox
import com.cemail.app.data.mail.toEntity
import com.cemail.app.data.mail.ftsQuery
import com.cemail.app.data.mail.toMail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
    /** Where this account's Graph subscription (id and secret) is kept between runs; null disables push. */
    private val pushPrefs: android.content.SharedPreferences? = null,
    /** How many messages the automatic sync keeps in the Inbox (Settings → Synced messages); 0 = the usual amount. */
    private val windowSize: () -> Int = { 0 },
) : MailRepository {
    // No "name for sending" here: Microsoft always puts the Microsoft account's own name on outgoing mail.
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
                    } catch (e: com.cemail.app.data.gmail.GmailHttpException) {
                        // 404: the message is no longer there under that id (a retried move that had in fact gone through): done.
                        if (e.code != 404) AppLog.e("graph", "could not apply the change on the server", e)
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

    override fun saveDraft(mail: Mail, replacing: Long?) {
        scope.launch(Dispatchers.IO) {
            val before = replacing?.let { dao.remoteIdOf(it, accountId) }
            replacing?.let { dao.delete(it) }
            val localId = dao.insert(mail.toEntity(accountId, remoteId = null).copy(localId = 0, folder = Folder.DRAFTS.name))
            // Microsoft cannot replace a draft's MIME in place: the new copy is saved and the old one deleted for good,
            // so Drafts still holds just one.
            val remote = try {
                api.createDraft(
                    MimeBuilder.build(
                        mail.senderName, mail.subject, mail.body, mail.outgoing, mail.cc, mail.bcc, mail.outgoingHtml,
                        mail.inReplyTo, mail.references, mail.outgoingImages,
                    ),
                )
            } catch (e: Exception) {
                AppLog.e("draft", "could not save on Microsoft, kept on the phone", e)
                // Still tied to its old copy there, so the next save replaces that one instead of leaving it behind.
                if (before != null) dao.linkDraft(localId, accountId, before)
                return@launch
            }
            dao.linkDraft(localId, accountId, remote)
            if (before != null) {
                // The old copy must go, or Drafts would hold two: a second try if Microsoft is briefly unavailable.
                val gone = dropRemoteDraft(before)
                if (!gone) AppLog.e("draft", "could not delete the old copy on Microsoft")
            }
            AppLog.i("draft", "saved on Microsoft${if (before != null) " (old copy replaced)" else ""}")
        }
    }

    override fun discardDraft(id: Long) {
        scope.launch(Dispatchers.IO) {
            val remote = dao.remoteIdOf(id, accountId)
            dao.delete(id)
            if (remote != null) {
                // A second try if Microsoft is briefly unavailable; if both fail the draft comes back with the next sync.
                if (!dropRemoteDraft(remote)) AppLog.e("draft", "could not delete on Microsoft")
            }
        }
    }

    /**
     * Takes a draft's copy off Microsoft: deleted for good or, if Microsoft refuses that, moved to Deleted Items (out of
     * Drafts either way); a second try after a short pause. False if it is still there.
     */
    private suspend fun dropRemoteDraft(id: String): Boolean {
        repeat(2) { attempt ->
            if (attempt > 0) kotlinx.coroutines.delay(3_000)
            if (runCatching { api.permanentDelete(id) }.isSuccess) return true
            if (runCatching { api.delete(id) }.isSuccess) {
                AppLog.i("draft", "copy moved to Deleted Items (permanent delete refused)")
                return true
            }
        }
        return false
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

    /** The Microsoft way of sending (Graph, which keeps the copy in Sent Items). Shared by a normal send and the Outbox. */
    private suspend fun transmit(mail: Mail) {
        api.sendMime(
            MimeBuilder.build(
                mail.senderName, mail.subject, mail.body, mail.outgoing, mail.cc, mail.bcc, mail.outgoingHtml,
                mail.inReplyTo, mail.references, mail.outgoingImages,
            ),
        )
    }

    override suspend fun isMuted(id: Long): Boolean = withContext(Dispatchers.IO) {
        val thread = dao.threadIdOf(id) ?: return@withContext false
        dao.mutedCount(accountId, thread) > 0
    }

    override fun setMuted(id: Long, muted: Boolean) {
        scope.launch(Dispatchers.IO) {
            val thread = dao.threadIdOf(id) ?: return@launch
            if (muted) {
                dao.mute(MutedThread(accountId, thread))
                write(mails.value.filter { it.accountId == accountId && it.threadId == thread && it.folder == Folder.INBOX }
                    .map { it to it.copy(folder = Folder.ARCHIVE, read = true) })
            } else {
                dao.unmute(accountId, thread)
            }
        }
    }

    override suspend fun unsubscribeInfo(id: Long): UnsubscribeInfo? = withContext(Dispatchers.IO) {
        val remote = dao.remoteIdOf(id, accountId) ?: return@withContext null
        try {
            val h = api.message(remote, "internetMessageHeaders").internetMessageHeaders
            fun header(name: String) = h.firstOrNull { it.name.equals(name, true) }?.value
            Unsubscribe.parse(header("List-Unsubscribe"), header("List-Unsubscribe-Post"))
        } catch (e: Exception) {
            AppLog.e("unsubscribe", "could not read the headers", e)
            null
        }
    }

    override suspend fun searchIds(query: String): Set<Long> = withContext(Dispatchers.IO) {
        val q = ftsQuery(query) ?: return@withContext emptySet()
        try {
            dao.searchIds(accountId, q).toSet()
        } catch (e: Exception) {
            AppLog.e("search", "full-text search failed", e)
            emptySet()
        }
    }

    override suspend fun schedule(mail: Mail, at: Long): Long = withContext(Dispatchers.IO) { dao.scheduleOutbox(accountId, mail, at) }

    override fun cancelScheduled(id: Long) {
        scope.launch(Dispatchers.IO) { dao.cancelOutbox(id) }
    }

    override suspend fun deliver(id: Long): Boolean = withContext(Dispatchers.IO) {
        deliverOutbox(dao, accountId, id, ::transmit).also { if (it) refresh() }
    }

    override fun nextId() = 0L

    override fun remove(id: Long) {
        scope.launch(Dispatchers.IO) { dao.delete(id) }
    }

    /**
     * Instant notifications: keeps a Graph subscription alive (renewing it, or creating it again if it expired) and tells
     * the relay which address it belongs to. [topic] is only a switch here: null turns the subscription off.
     */
    override suspend fun setPush(topic: String?) {
        val prefs = pushPrefs ?: return
        val id = prefs.getString("subId", null)
        if (topic == null) {
            if (id != null) api.unsubscribe(id)
            prefs.edit().remove("subId").remove("subState").apply()
            return
        }
        val expires = Instant.now().plusSeconds(4200L * 60)
        var state = prefs.getString("subState", null)
        var current = id
        if (current == null || state == null || !api.renewSubscription(current, expires)) {
            state = java.util.UUID.randomUUID().toString()
            current = api.subscribe(com.cemail.app.data.push.Push.graphUrl(), state, expires)
            prefs.edit().putString("subId", current).putString("subState", state).apply()
        }
        val email = selfEmail() ?: return
        com.cemail.app.data.push.Push.registerSubscription(current, email.lowercase(), state)
    }

    override suspend fun refresh(quick: Boolean): Boolean {
        if (pending.get() > 0) return true // changes are on their way; the next sync will see them done
        if (!_syncing.compareAndSet(expect = false, update = true)) return true
        // Runs in the app's scope: see RoomMailRepository.refresh.
        return scope.async(Dispatchers.IO) {
            try {
                sync(quick)
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e("sync", "...${accountId.takeLast(4)} Microsoft ${if (quick) "quick" else "full"} sync failed", e)
                false
            } finally {
                _syncing.value = false
            }
        }.await()
    }

    override suspend fun loadOlder(place: MailPlace): Int = withContext(Dispatchers.IO) {
        // Microsoft has folders only: no labels, and the starred messages are not a folder of their own.
        val folder = (place as? MailPlace.InFolder)?.folder ?: return@withContext 0
        val target = wellKnown(folder)?.takeIf { folder != Folder.DRAFTS } ?: return@withContext 0
        try {
            // The newest messages of the folder the phone does not have yet (so a gap is filled, never skipped): their
            // ids first, which is cheap, then each of the missing ones whole.
            val cached = dao.remoteOnes(accountId).mapNotNull { it.remoteId }.toHashSet()
            val missing = api.ids(target, dao.countIn(accountId, folder.name) + OLDER_PAGE).filter { it !in cached }.take(OLDER_PAGE)
            bring(missing.map { api.listed(it) to folder }, "load more")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.e("sync", "...${accountId.takeLast(4)} Microsoft load more failed", e)
            -1
        }
    }

    override suspend fun searchServer(query: String): Int = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext 0
        try {
            // Where mail is usually looked for; each folder answers with its best matches.
            // The newest matches the phone does not have yet: each search goes further back than the one before.
            val cached = dao.remoteOnes(accountId).mapNotNull { it.remoteId }.toHashSet()
            val found = SEARCHED.flatMap { folder ->
                api.search(wellKnown(folder)!!, query, SEARCH_SCAN / SEARCHED.size).filter { it !in cached }
                    .take(OLDER_PAGE / SEARCHED.size).map { api.listed(it) to folder }
            }
            bring(found, "server search")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.e("search", "...${accountId.takeLast(4)} Microsoft server search failed", e)
            -1
        }
    }

    /** Saves the messages the phone does not have yet and keeps all of them. Returns how many were new. */
    private suspend fun bring(found: List<Pair<GraphMessage, Folder>>, what: String): Int {
        val cached = dao.remoteOnes(accountId).associateBy { it.remoteId!! }
        var fresh = 0
        for ((m, folder) in found) {
            if (m.id in cached) continue
            dao.insert(m.toEntity(accountId, folder)) // never announced as new mail
            fresh++
        }
        dao.keep(found.map { KeptMessage(accountId, it.first.id) })
        AppLog.i("sync", "...${accountId.takeLast(4)} Microsoft $what: ${found.size} listed, $fresh new")
        return fresh
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
                val size = windowSize()
                val listed = api.messages(target, if (folder == Folder.INBOX) size.takeIf { it > 0 } ?: INBOX_LIMIT else (size * 2 / 5).takeIf { it > 0 } ?: OTHER_LIMIT)
                for (m in listed) {
                    seen += m.id
                    val old = cached[m.id]
                    val read = m.isRead
                    val starred = m.flag?.flagStatus == "flagged"
                    if (old == null) {
                        var entity = m.toEntity(accountId, folder)
                        // A message of a muted conversation goes straight to the archive.
                        val thread = entity.threadId
                        if (folder == Folder.INBOX && thread != null && dao.mutedCount(accountId, thread) > 0) {
                            try { api.update(m.id, true, null); api.move(m.id, "archive") } catch (_: Exception) { /* next sync */ }
                            entity = entity.copy(folder = Folder.ARCHIVE.name, read = true)
                        }
                        val localId = dao.insert(entity) // the notification points at this row
                        if (!entity.read && folder == Folder.INBOX && entity.timeMillis > System.currentTimeMillis() - RECENT_MILLIS) {
                            arrived += entity.copy(localId = localId)
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
        val kept = dao.keptIds(accountId).toHashSet()
        val gone = cached.filter { (id, row) -> id !in seen && id !in kept && runCatching { Folder.valueOf(row.folder) }.getOrNull() in done }
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

    override suspend fun emptyFolder(folder: Folder): Boolean = withContext(Dispatchers.IO) {
        try {
            val target = wellKnown(folder) ?: return@withContext false
            // Deleting from Deleted Items or Junk is permanent.
            val ids = api.messages(target, 1000).map { it.id }
            ids.forEach { api.delete(it) }
            dao.deleteRemoteInFolder(accountId, folder.name)
            AppLog.i("empty", "...${accountId.takeLast(4)} Microsoft ${folder.name} emptied (${ids.size})")
            true
        } catch (e: Exception) {
            AppLog.e("empty", "could not empty ${folder.name}", e)
            false
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

    /** The pictures embedded in a message, fetched when it is opened and then kept in the cache folder. */
    override suspend fun inlineImages(id: Long): Map<String, String> = withContext(Dispatchers.IO) {
        val remote = dao.remoteIdOf(id, accountId) ?: return@withContext emptyMap()
        try {
            val dir = File(attachmentsDir, "inline/" + remote.filter { it.isLetterOrDigit() }.takeLast(60))
            com.cemail.app.data.mail.InlineCache.load(dir) { runBlocking { api.inlineImages(remote) } }
        } catch (e: Exception) {
            AppLog.e("inline", "could not fetch the embedded pictures", e)
            emptyMap()
        }
    }

    private fun wellKnown(folder: Folder): String? = when (folder) {
        Folder.INBOX -> "inbox"
        Folder.SENT -> "sentitems"
        Folder.DRAFTS -> "drafts"
        Folder.TRASH -> "deleteditems"
        Folder.ARCHIVE -> "archive"
        Folder.SPAM -> "junkemail"
        Folder.OUTBOX -> null
    }

    private companion object {
        val FOLDERS = listOf(Folder.INBOX, Folder.SENT, Folder.DRAFTS, Folder.TRASH, Folder.ARCHIVE, Folder.SPAM)
        val SEARCHED = listOf(Folder.INBOX, Folder.SENT, Folder.ARCHIVE)
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
