package com.cemail.app.data.imap

import com.cemail.app.AppLog
import com.cemail.app.data.db.LabelDao
import com.cemail.app.data.db.MessageDao
import com.cemail.app.data.db.KeptMessage
import com.cemail.app.data.db.MessageEntity
import com.cemail.app.data.gmail.AttachmentFinder
import com.cemail.app.data.gmail.AttachmentInfo
import com.cemail.app.data.gmail.MimeBuilder
import com.cemail.app.data.gmail.ReplyInfo
import com.cemail.app.data.gmail.ReplyInfoParser
import com.cemail.app.data.mail.Folder
import com.cemail.app.data.mail.Label
import com.cemail.app.data.mail.Mail
import com.cemail.app.data.mail.MailPlace
import com.cemail.app.data.mail.MailRepository
import com.cemail.app.data.mail.OLDER_PAGE
import com.cemail.app.data.mail.InlineCache
import com.cemail.app.data.mail.SyncMarks
import com.cemail.app.data.mail.Unsubscribe
import com.cemail.app.data.mail.UnsubscribeInfo
import com.cemail.app.data.mail.cancelOutbox
import com.cemail.app.data.mail.deliverOutbox
import com.cemail.app.data.mail.scheduleOutbox
import com.cemail.app.data.mail.capped
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
    /** The name the user chose to send with (Settings); null keeps the one given when the account was added. */
    private val senderName: () -> String? = { null },
    /** How many messages the automatic sync keeps in the Inbox (Settings → Synced messages); 0 = the usual amount. */
    private val windowSize: () -> Int = { 0 },
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
                val sent = client.send(raw, senderName())
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

    /** The IMAP way of sending: SMTP, then a copy in Sent. Shared by a normal send and by the Outbox. */
    private suspend fun transmit(mail: Mail) = withContext(Dispatchers.IO) {
        val raw = MimeBuilder.build(
            mail.senderName, mail.subject, mail.body, mail.outgoing, mail.cc, mail.bcc, mail.outgoingHtml,
            mail.inReplyTo, mail.references, mail.outgoingImages,
        )
        val sent = client.send(raw, senderName())
        marks.get("role.SENT")?.let { path ->
            try {
                client.withStore { store -> client.appendTo(store, path, sent, seen = true) }
            } catch (e: Exception) {
                AppLog.e("send", "sent, but the copy in Sent could not be saved", e)
            }
        }
    }

    /** IMAP messages have no conversation id here, so there is nothing to mute. */
    override suspend fun isMuted(id: Long): Boolean = false
    override fun setMuted(id: Long, muted: Boolean) = Unit

    override suspend fun unsubscribeInfo(id: Long): UnsubscribeInfo? = withContext(Dispatchers.IO) {
        val remote = dao.remoteIdOf(id, accountId) ?: return@withContext null
        val (path, uid) = parseRemote(remote)
        try {
            val h = client.withStore { store -> client.inFolder(store, path, readOnly = true) { client.headers(it, uid, Unsubscribe.HEADERS) } }
            Unsubscribe.parse(h["List-Unsubscribe"], h["List-Unsubscribe-Post"])
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

    override suspend fun refresh(quick: Boolean): Boolean {
        if (!_syncing.compareAndSet(expect = false, update = true)) return true
        // Runs in the app's scope: see RoomMailRepository.refresh.
        return scope.async(Dispatchers.IO) {
            try {
                sync(quick)
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e("sync", "...${accountId.takeLast(4)} IMAP ${if (quick) "quick" else "full"} sync failed", e)
                false
            } finally {
                _syncing.value = false
            }
        }.await()
    }

    override suspend fun loadOlder(place: MailPlace): Int = withContext(Dispatchers.IO) {
        // IMAP has folders only (no labels, and the starred messages are spread over all of them).
        val folder = (place as? MailPlace.InFolder)?.folder?.takeIf { it != Folder.DRAFTS && it != Folder.OUTBOX } ?: return@withContext 0
        try {
            client.withStore { store ->
                val path = marks.get("role.${roleOf(folder)}") ?: return@withStore 0
                // What the phone already holds of that folder: Load more brings the newest of the rest.
                val have = db { dao.remoteOnes(accountId) }.mapNotNull { row ->
                    row.remoteId?.takeIf { folderOf(it) == path }?.let { parseRemote(it).second }
                }.toHashSet()
                client.inFolder(store, path, readOnly = true) { f ->
                    bring(path, folder, client.missingNewest(f, have, OLDER_PAGE), "load more")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.e("sync", "...${accountId.takeLast(4)} IMAP load more failed", e)
            -1
        }
    }

    override suspend fun searchServer(query: String): Int = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext 0
        try {
            client.withStore { store ->
                // Where mail is usually looked for; each folder answers with its newest matches.
                var fresh = 0
                val remotes = db { dao.remoteOnes(accountId) }.mapNotNull { it.remoteId }
                for (folder in SEARCHED) {
                    val path = marks.get("role.${roleOf(folder)}") ?: continue
                    val have = remotes.filter { folderOf(it) == path }.map { parseRemote(it).second }.toHashSet()
                    fresh += client.inFolder(store, path, readOnly = true) { f ->
                        bring(path, folder, client.search(f, query, have, OLDER_PAGE / SEARCHED.size), "server search")
                    }
                }
                fresh
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.e("search", "...${accountId.takeLast(4)} IMAP server search failed", e)
            -1
        }
    }

    /** Saves the messages of [path] the phone does not have yet and keeps all of them. Returns how many were new. */
    private fun bring(path: String, folder: Folder, messages: List<RemoteMessage>, what: String): Int {
        val cached = db { dao.remoteOnes(accountId) }.mapNotNull { it.remoteId }.toHashSet()
        var fresh = 0
        for (m in messages) {
            val remote = "$path|${m.uid}"
            if (remote in cached) continue
            db { dao.insert(entityOf(remote, folder, m)) } // never announced as new mail
            fresh++
        }
        db { dao.keep(messages.map { KeptMessage(accountId, "$path|${it.uid}") }) }
        AppLog.i("sync", "...${accountId.takeLast(4)} IMAP $what: ${messages.size} listed, $fresh new")
        return fresh
    }

    /** The row for a message of the server, read whole. */
    private fun entityOf(remote: String, folder: Folder, m: RemoteMessage): MessageEntity {
        val parsed = client.parse(m.message)
        return MessageEntity(
            accountId = accountId, remoteId = remote, folder = folder.name,
            senderName = parsed.fromName, senderEmail = parsed.fromEmail, subject = parsed.subject,
            body = parsed.body, timeMillis = parsed.timeMillis, read = m.seen, starred = m.flagged,
            pinned = false, snoozedUntil = null, hasAttachment = parsed.hasAttachment,
            labelIds = "", bodyHtml = parsed.html, threadId = null,
        ).capped()
    }

    private fun <T> db(block: suspend () -> T): T = runBlocking { block() } // already on an IO thread

    private fun sync(quick: Boolean) {
        client.withStore { store ->
            val folders = client.folders(store)
            val byRole = folders.groupBy { it.role }.mapValues { it.value.first().path }
            for (role in listOf(Role.INBOX, Role.SENT, Role.DRAFTS, Role.TRASH, Role.ARCHIVE, Role.JUNK)) {
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
                        val size = windowSize()
                        val limit = if (f.role == Role.INBOX) size.takeIf { it > 0 } ?: INBOX_LIMIT else (size * 2 / 5).takeIf { it > 0 } ?: OTHER_LIMIT
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
                                val localId = db { dao.insert(entity) } // the notification points at this row
                                if (!entity.read && f.role == Role.INBOX && entity.timeMillis > System.currentTimeMillis() - RECENT_MILLIS) {
                                    arrived += entity.copy(localId = localId)
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
            val kept = db { dao.keptIds(accountId) }.toHashSet()
            val gone = cached.filter { (remote, _) -> folderOf(remote) in done && remote !in seen && remote !in kept }.values.map { it.localId }
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

    override suspend fun emptyFolder(folder: Folder): Boolean = withContext(Dispatchers.IO) {
        val path = marks.get("role.${roleOf(folder)}") ?: return@withContext false
        try {
            client.withStore { store -> client.inFolder(store, path) { client.expungeAll(it) } }
            dao.deleteRemoteInFolder(accountId, folder.name)
            AppLog.i("empty", "...${accountId.takeLast(4)} IMAP ${folder.name} emptied")
            true
        } catch (e: Exception) {
            AppLog.e("empty", "could not empty ${folder.name}", e)
            false
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

    /** The pictures embedded in a message, fetched when it is opened and then kept in the cache folder. */
    override suspend fun inlineImages(id: Long): Map<String, String> = withContext(Dispatchers.IO) {
        val remote = dao.remoteIdOf(id, accountId) ?: return@withContext emptyMap()
        val (path, uid) = parseRemote(remote)
        try {
            val dir = File(attachmentsDir, "inline/" + remote.filter { it.isLetterOrDigit() }.takeLast(60))
            InlineCache.load(dir) {
                client.withStore { store -> client.inFolder(store, path, readOnly = true) { client.inlineImages(it, uid) } }
            }
        } catch (e: Exception) {
            AppLog.e("inline", "could not fetch the embedded pictures", e)
            emptyMap()
        }
    }

    private fun folderOf(remote: String) = remote.substringBeforeLast('|')

    private fun parseRemote(remote: String): Pair<String, Long> =
        remote.substringBeforeLast('|') to remote.substringAfterLast('|').toLong()

    private fun folderOf(role: Role) = when (role) {
        Role.INBOX -> Folder.INBOX
        Role.SENT -> Folder.SENT
        Role.DRAFTS -> Folder.DRAFTS
        Role.TRASH -> Folder.TRASH
        Role.JUNK -> Folder.SPAM
        else -> Folder.ARCHIVE
    }

    private fun roleOf(folder: Folder) = when (folder) {
        Folder.INBOX -> Role.INBOX
        Folder.SENT -> Role.SENT
        Folder.DRAFTS -> Role.DRAFTS
        Folder.TRASH -> Role.TRASH
        Folder.ARCHIVE -> Role.ARCHIVE
        Folder.SPAM -> Role.JUNK
        Folder.OUTBOX -> Role.OTHER
    }

    private companion object {
        val SYNCED = setOf(Role.INBOX, Role.SENT, Role.DRAFTS, Role.TRASH, Role.ARCHIVE, Role.JUNK)
        val SEARCHED = listOf(Folder.INBOX, Folder.SENT, Folder.ARCHIVE)
        const val INBOX_LIMIT = 80
        const val OTHER_LIMIT = 30
        const val RECENT_MILLIS = 3L * 24 * 60 * 60 * 1000
        const val CHUNK = 250_000
    }
}
