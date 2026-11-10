package com.cemail.app.data.imap

import com.cemail.app.AppLog
import com.cemail.app.data.db.LabelDao
import com.cemail.app.data.db.MessageDao
import com.cemail.app.data.db.MessageEntity
import com.cemail.app.data.gmail.AttachmentFinder
import com.cemail.app.data.gmail.AttachmentInfo
import com.cemail.app.data.gmail.MimeBuilder
import com.cemail.app.data.gmail.ReplyInfo
import com.cemail.app.data.gmail.ReplyInfoParser
import com.cemail.app.data.mail.Folder
import com.cemail.app.data.mail.Label
import com.cemail.app.data.mail.Mail
import com.cemail.app.data.mail.MailRepository
import com.cemail.app.data.mail.SyncMarks
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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File

/**
 * One IMAP/SMTP account cached in Room, like [com.cemail.app.data.mail.RoomMailRepository] does for Gmail: the UI reads
 * only from Room and the network writes into it. A message is identified on the server by its folder and UID, kept as
 * "folder|uid" in `remoteId`. Folders are not labels here: a message lives in exactly one of them.
 */
class ImapMailRepository(
    private val accountId: String,
    private val client: ImapClient,
    private val dao: MessageDao,
    private val scope: CoroutineScope,
    private val attachmentsDir: File,
    private val selfEmail: suspend () -> String? = { null },
    private val onNewMail: (accountId: String, mails: List<Mail>) -> Unit = { _, _ -> },
    private val marks: SyncMarks = SyncMarks.InMemory(),
) : MailRepository {
    override val mails: StateFlow<List<Mail>> = dao.observe(accountId)
        .map { list -> list.map { it.toMail() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override val labels: Flow<List<Label>> = flowOf(emptyList())
    override suspend fun userLabels(accountId: String): List<Label> = emptyList()

    private val _syncing = MutableStateFlow(false)
    override val syncing: StateFlow<Boolean> = _syncing

    override fun snapshot(ids: Set<Long>) = mails.value.filter { it.id in ids }

    override fun update(ids: Set<Long>, transform: (Mail) -> Mail) {
        write(snapshot(ids).map { it to transform(it) })
    }

    override fun restore(previous: List<Mail>) {
        val current = mails.value.associateBy { it.id }
        write(previous.mapNotNull { old -> current[old.id]?.let { it to old } })
    }

    /** Applies the change here at once and then on the server (a failure is put right by the next sync). */
    private fun write(changes: List<Pair<Mail, Mail>>) {
        scope.launch(Dispatchers.IO) {
            for ((before, after) in changes) {
                dao.updateFlags(after.id, after.folder.name, after.read, after.starred, after.pinned, after.snoozedUntil)
                val remote = dao.remoteIdOf(after.id) ?: continue
                val (path, uid) = parseRemote(remote)
                if (before.read == after.read && before.starred == after.starred && before.folder == after.folder) continue
                try {
                    client.withStore { store ->
                        client.inFolder(store, path) { folder ->
                            if (before.read != after.read) client.setSeen(folder, uid, after.read)
                            if (before.starred != after.starred) client.setFlagged(folder, uid, after.starred)
                            if (before.folder != after.folder) {
                                val target = marks.get("role.${roleOf(after.folder)}")
                                when {
                                    target != null && target != path -> client.move(store, folder, uid, target)
                                    target == null && after.folder == Folder.TRASH -> client.expunge(folder, uid)
                                    else -> AppLog.i("imap", "no folder for ${after.folder}: the message stays where it is on the server")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    AppLog.e("imap", "could not apply the change on the server", e)
                }
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
                val raw = MimeBuilder.build(
                    mail.senderName, mail.subject, mail.body, mail.outgoing, mail.cc, mail.bcc, mail.outgoingHtml,
                    mail.inReplyTo, mail.references, mail.outgoingImages,
                )
                val sent = client.send(raw)
                AppLog.i("send", "sent over SMTP, ${mail.outgoing.size} attachment(s)")
                // SMTP servers do not keep a copy: it is added to Sent here.
                marks.get("role.SENT")?.let { path ->
                    try {
                        client.withStore { store -> client.appendTo(store, path, sent, seen = true) }
                    } catch (e: Exception) {
                        AppLog.e("send", "sent, but the copy in Sent could not be saved", e)
                    }
                }
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
        if (!_syncing.compareAndSet(expect = false, update = true)) return true
        return try {
            withContext(Dispatchers.IO) { sync(quick) }
            true
        } catch (e: Exception) {
            AppLog.e("sync", "...${accountId.takeLast(4)} IMAP ${if (quick) "quick" else "full"} sync failed", e)
            false
        } finally {
            _syncing.value = false
        }
    }

    private fun <T> db(block: suspend () -> T): T = runBlocking { block() } // already on an IO thread

    private fun sync(quick: Boolean) {
        client.withStore { store ->
            val folders = client.folders(store)
            val byRole = folders.groupBy { it.role }.mapValues { it.value.first().path }
            for (role in listOf(Role.INBOX, Role.SENT, Role.DRAFTS, Role.TRASH, Role.ARCHIVE)) {
                byRole[role]?.let { marks.put("role.$role", it) }
            }
            val wanted = folders.filter { it.role in SYNCED }.sortedBy { it.role.ordinal }
                .filter { !quick || it.role == Role.INBOX }
            val cached = db { dao.remoteOnes(accountId) }.associateBy { it.remoteId!! }.toMutableMap()
            val seen = HashSet<String>()
            val done = HashSet<String>()
            val arrived = ArrayList<MessageEntity>()
            for (f in wanted) {
                try {
                    client.inFolder(store, f.path) { folder ->
                        val validity = folder.uidValidity.toString()
                        if (marks.get("uidv.${f.path}") != validity) {
                            // The server renumbered this folder: what is cached from it no longer matches.
                            cached.entries.filter { folderOf(it.key) == f.path }.forEach { (key, row) ->
                                db { dao.delete(row.localId) }
                                cached.remove(key)
                            }
                            marks.put("uidv.${f.path}", validity)
                        }
                        val limit = if (f.role == Role.INBOX) INBOX_LIMIT else OTHER_LIMIT
                        for (m in client.window(folder, limit)) {
                            val remote = "${f.path}|${m.uid}"
                            seen += remote
                            val old = cached[remote]
                            if (old == null) {
                                val parsed = client.parse(m.message)
                                val entity = MessageEntity(
                                    accountId = accountId, remoteId = remote, folder = folderOf(f.role).name,
                                    senderName = parsed.fromName, senderEmail = parsed.fromEmail, subject = parsed.subject,
                                    body = parsed.body, timeMillis = parsed.timeMillis, read = m.seen, starred = m.flagged,
                                    pinned = false, snoozedUntil = null, hasAttachment = parsed.hasAttachment,
                                    labelIds = "", bodyHtml = parsed.html, threadId = null,
                                ).capped()
                                db { dao.insert(entity) }
                                if (!entity.read && f.role == Role.INBOX && entity.timeMillis > System.currentTimeMillis() - RECENT_MILLIS) {
                                    arrived += entity
                                }
                            } else if (old.read != m.seen || old.starred != m.flagged) {
                                db { dao.updateState(old.localId, old.folder, m.seen, m.flagged, old.labelIds) }
                            }
                        }
                    }
                    done += f.path
                } catch (e: Exception) {
                    // One folder failing must not stop the others; its messages are left as they are.
                    AppLog.e("sync", "...${accountId.takeLast(4)} IMAP folder could not be read", e)
                }
            }
            val gone = cached.filter { (remote, _) -> folderOf(remote) in done && remote !in seen }.values.map { it.localId }
            gone.chunked(500).forEach { chunk -> db { dao.deleteAll(accountId, chunk) } }
            AppLog.i("sync", "...${accountId.takeLast(4)} IMAP ${if (quick) "quick" else "full"} ok: ${done.size} folder(s), ${seen.size} listed, ${arrived.size} new unread, ${gone.size} removed")
            // The first sync of an account brings in its whole window: that is not "new mail".
            if (cached.isNotEmpty() && arrived.isNotEmpty()) {
                onNewMail(accountId, arrived.sortedByDescending { it.timeMillis }.map { it.toMail() })
            }
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
        val (path, uid) = parseRemote(remote)
        try {
            val h = client.withStore { store ->
                client.inFolder(store, path, readOnly = true) { client.headers(it, uid, ReplyInfoParser.HEADERS) }
            }
            ReplyInfo(
                threadId = null,
                messageId = h["Message-ID"]?.trim()?.takeIf { it.isNotEmpty() },
                references = h["References"]?.trim()?.takeIf { it.isNotEmpty() },
                replyTo = ReplyInfoParser.addresses(h["Reply-To"]),
                from = ReplyInfoParser.addresses(h["From"]),
                to = ReplyInfoParser.addresses(h["To"]),
                cc = ReplyInfoParser.addresses(h["Cc"]),
            ) to selfEmail()
        } catch (e: Exception) {
            AppLog.e("reply", "could not read the headers of the message", e)
            null
        }
    }

    override suspend fun emlFile(id: Long): File? = withContext(Dispatchers.IO) {
        val remote = dao.remoteIdOf(id, accountId) ?: return@withContext null
        val (path, uid) = parseRemote(remote)
        try {
            val bytes = client.withStore { store -> client.inFolder(store, path, readOnly = true) { client.raw(it, uid) } }
                ?: return@withContext null
            val subject = dao.light(id)?.subject.orEmpty()
            val dir = File(attachmentsDir, "eml").apply { mkdirs() }
            File(dir, AttachmentFinder.safeFileName(subject.ifBlank { "message" }).take(60) + ".eml").also { it.writeBytes(bytes) }
        } catch (e: Exception) {
            AppLog.e("eml", "could not save the message as .eml", e)
            null
        }
    }

    override suspend fun attachments(id: Long): List<AttachmentInfo> = withContext(Dispatchers.IO) {
        val remote = dao.remoteIdOf(id, accountId) ?: return@withContext emptyList()
        val (path, uid) = parseRemote(remote)
        try {
            client.withStore { store -> client.inFolder(store, path, readOnly = true) { client.attachments(it, uid) } }
        } catch (e: Exception) {
            AppLog.e("attach", "could not list attachments", e)
            emptyList()
        }
    }

    override suspend fun attachmentFile(id: Long, attachment: AttachmentInfo): File? = withContext(Dispatchers.IO) {
        val remote = dao.remoteIdOf(id, accountId) ?: return@withContext null
        val (path, uid) = parseRemote(remote)
        try {
            val dir = File(attachmentsDir, remote.filter { it.isLetterOrDigit() }).apply { mkdirs() }
            val file = File(dir, AttachmentFinder.safeFileName(attachment.filename))
            if (file.exists() && file.length() > 0) return@withContext file
            val bytes = client.withStore { store ->
                client.inFolder(store, path, readOnly = true) { client.attachmentBytes(it, uid, attachment.attachmentId) }
            } ?: return@withContext null
            file.writeBytes(bytes)
            file
        } catch (e: Exception) {
            AppLog.e("attach", "download failed", e)
            null
        }
    }

    /** Pictures embedded in IMAP messages are not fetched yet: they simply stay out of the page. */
    override suspend fun inlineImages(id: Long): Map<String, String> = emptyMap()

    private fun folderOf(remote: String) = remote.substringBeforeLast('|')

    private fun parseRemote(remote: String): Pair<String, Long> =
        remote.substringBeforeLast('|') to remote.substringAfterLast('|').toLong()

    private fun folderOf(role: Role) = when (role) {
        Role.INBOX -> Folder.INBOX
        Role.SENT -> Folder.SENT
        Role.DRAFTS -> Folder.DRAFTS
        Role.TRASH -> Folder.TRASH
        else -> Folder.ARCHIVE
    }

    private fun roleOf(folder: Folder) = when (folder) {
        Folder.INBOX -> Role.INBOX
        Folder.SENT -> Role.SENT
        Folder.DRAFTS -> Role.DRAFTS
        Folder.TRASH -> Role.TRASH
        Folder.ARCHIVE -> Role.ARCHIVE
    }

    private companion object {
        val SYNCED = setOf(Role.INBOX, Role.SENT, Role.DRAFTS, Role.TRASH, Role.ARCHIVE)
        const val INBOX_LIMIT = 80
        const val OTHER_LIMIT = 30
        const val RECENT_MILLIS = 3L * 24 * 60 * 60 * 1000
        const val CHUNK = 250_000
    }
}
