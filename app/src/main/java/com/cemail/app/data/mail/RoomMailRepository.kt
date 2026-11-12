package com.cemail.app.data.mail

import com.cemail.app.data.db.LabelDao
import com.cemail.app.data.db.LabelEntity
import com.cemail.app.data.db.MessageDao
import com.cemail.app.data.db.MessageEntity
import com.cemail.app.data.db.MutedThread
import com.cemail.app.data.gmail.GmailApi
import com.cemail.app.data.gmail.GmailHttpException
import com.cemail.app.data.gmail.GmailMessage
import com.cemail.app.data.gmail.GmailParser
import com.cemail.app.data.gmail.InlineImages
import com.cemail.app.data.gmail.MimeBuilder
import java.io.File
import com.cemail.app.AppLog
import com.cemail.app.data.gmail.AttachmentFinder
import com.cemail.app.data.gmail.AttachmentInfo
import com.cemail.app.data.gmail.ReplyInfo
import java.util.Base64
import kotlinx.coroutines.withContext
import com.cemail.app.data.gmail.ParsedMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * One Gmail account cached in Room. The UI reads only from Room; the network writes into Room.
 * Actions are applied locally first and then pushed to Gmail (a failed push is corrected by the next sync).
 */
class RoomMailRepository(
    private val accountId: String,
    private val dao: MessageDao,
    private val labelDao: LabelDao,
    private val api: GmailApi,
    private val scope: CoroutineScope,
    /** Folder where this account's embedded pictures are kept, one sub-folder per message. */
    private val inlineDir: File,
    /** Where downloaded attachments are kept (the cache: Android may clear it). */
    private val attachmentsDir: File,
    /** This account's own address (to leave it out of "reply all"); null if unknown. */
    private val selfEmail: suspend () -> String? = { null },
    /** Called with the unread Inbox messages that a sync found for the first time (never on the first sync). */
    private val onNewMail: (accountId: String, mails: List<Mail>) -> Unit = { _, _ -> },
    /** Where the sync keeps its place in the mailbox's history. */
    /**
     * User labels the user switched off in Settings: they stay in the menu, but their archived messages are not
     * fetched on their own (push, periodic checks); pulling down inside the label brings them ([refreshLabel]).
     */
    private val hidden: StateFlow<Set<String>> = kotlinx.coroutines.flow.MutableStateFlow(emptySet()),
    private val marks: SyncMarks = SyncMarks.InMemory(),
    /** The name the user chose to send with (Settings); null lets Gmail use the account's own. */
    private val senderName: () -> String? = { null },
) : MailRepository {
    override val mails: StateFlow<List<Mail>> = dao.observe(accountId)
        .map { list -> list.map { it.toMail() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override suspend fun userLabels(accountId: String): List<Label> =
        if (accountId == this.accountId) labels.first() else emptyList()

    /** Every label, also the ones that do not sync on their own: a label never disappears from the menu. */
    override val labels: Flow<List<Label>> = labelDao.observe(accountId).map { list -> list.map { Label(it.id, it.name) } }

    override suspend fun setPush(topic: String?) {
        if (topic == null) api.stopWatching() else api.watch(topic)
    }

    override suspend fun allLabels(accountId: String): List<Label> =
        if (accountId == this.accountId) labelDao.observe(accountId).first().map { Label(it.id, it.name) } else emptyList()

    /**
     * True for an archived message whose only user labels do not sync on their own: the automatic sync leaves it
     * out (it is fetched when the user pulls down inside one of those labels).
     */
    private fun MessageEntity.hiddenByLabels(): Boolean {
        val off = hidden.value
        if (off.isEmpty() || folder != Folder.ARCHIVE.name) return false
        val own = labelIds.split(" ").filter { it.startsWith("Label_") }
        return own.isNotEmpty() && own.all { it in off }
    }

    /**
     * Carries a user label: kept even when it falls out of the automatic window, so what a pull-to-refresh inside a
     * label brought stays there (Gmail's history still removes it when it is deleted on the server).
     */
    private fun MessageEntity.inManualLabel(): Boolean = labelIds.split(" ").any { it.startsWith("Label_") }

    /**
     * Pull-to-refresh inside a label: its newest messages are fetched now, also when the label does not sync on its
     * own. They stay on the phone afterwards (never announced as new mail).
     */
    override suspend fun refreshLabel(labelId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val ids = api.listLabelMessageIds(labelId, LABEL_LIMIT)
            val cached = dao.remoteOnes(accountId).associateBy { it.remoteId!! }
            val gate = Semaphore(6)
            val ignored = java.util.concurrent.ConcurrentLinkedQueue<MessageEntity>()
            coroutineScope {
                ids.map { id ->
                    async(Dispatchers.IO) {
                        gate.withPermit {
                            val old = cached[id]
                            try {
                                if (old == null || old.bodyHtml == null) ingest(id, old, ignored, force = true)
                                else applyLabels(old, api.getLabels(id).toSet())
                            } catch (e: GmailHttpException) {
                                if (e.code != 404) throw e
                            }
                        }
                    }
                }.awaitAll()
            }
            AppLog.i("sync", "...${accountId.takeLast(4)} label refreshed: ${ids.size} listed, ${ids.count { it !in cached }} new")
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.e("sync", "...${accountId.takeLast(4)} label refresh failed", e)
            false
        }
    }

    private val _syncing = MutableStateFlow(false)
    override val syncing: StateFlow<Boolean> = _syncing

    override fun snapshot(ids: Set<Long>) = mails.value.filter { it.id in ids }

    override fun update(ids: Set<Long>, transform: (Mail) -> Mail) {
        val changes = snapshot(ids).map { it to transform(it) }
        write(changes)
    }

    override fun restore(previous: List<Mail>) {
        val current = mails.value.associateBy { it.id }
        AppLog.i("undo", "...${accountId.takeLast(4)} restore: ${previous.count { it.id in current }} of ${previous.size} found now")
        write(previous.mapNotNull { old -> current[old.id]?.let { it to old } })
    }

    // Messages the user just changed here. The server copy is about to catch up, so a sync that read it a moment ago (the
    // push that our own change triggers, for instance) must not put the old state back over an Undo.
    private val touchedUntil = java.util.concurrent.ConcurrentHashMap<Long, Long>()
    private val inFlight = java.util.concurrent.ConcurrentHashMap<Long, Int>()
    private val modifyLock = kotlinx.coroutines.sync.Mutex()
    private fun touched(localId: Long) = (touchedUntil[localId] ?: 0L) > System.currentTimeMillis()

    private fun write(changes: List<Pair<Mail, Mail>>) {
        // Marked at once, before the coroutine starts, so a sync that is already running sees it too.
        for ((_, after) in changes) {
            inFlight.merge(after.id, 1) { a, b -> a + b }
            touchedUntil[after.id] = Long.MAX_VALUE
        }
        scope.launch(Dispatchers.IO) {
            for ((before, after) in changes) {
                try {
                    // Only the flags are written, so the (possibly huge) texts are never loaded here.
                    dao.updateFlags(after.id, after.folder.name, after.read, after.starred, after.pinned, after.snoozedUntil)
                    if (after.labelIds != before.labelIds) dao.updateLabelIds(after.id, after.labelIds.sorted().joinToString(" "))
                    val remote = dao.remoteIdOf(after.id)
                    val (add, remove) = LabelDiff.between(before, after)
                    if (remote != null && (add.isNotEmpty() || remove.isNotEmpty())) {
                        // One at a time and in order, so an Undo reaches Gmail after the change it undoes.
                        modifyLock.withLock {
                            try { api.modify(remote, add, remove) } catch (_: Exception) { /* next sync puts the server state back */ }
                        }
                    }
                } finally {
                    val left = inFlight.merge(after.id, -1) { a, b -> a + b } ?: 0
                    if (left <= 0) {
                        inFlight.remove(after.id)
                        touchedUntil[after.id] = System.currentTimeMillis() + TOUCH_GRACE_MILLIS
                    }
                }
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
            try {
                // The same draft on Gmail is updated (not a new one each time), so there is only ever one copy.
                val remote = api.saveDraft(mimeOf(mail), mail.threadId, before) ?: return@launch
                dao.linkDraft(localId, accountId, remote)
                AppLog.i("draft", "saved on the server${if (before != null) " (updated)" else ""}")
            } catch (e: Exception) {
                AppLog.e("draft", "could not save on the server, kept on the phone", e)
                // Still tied to its old copy on the server, so the next save updates that one instead of adding another.
                if (before != null) dao.linkDraft(localId, accountId, before)
            }
        }
    }

    override fun discardDraft(id: Long) {
        scope.launch(Dispatchers.IO) {
            val remote = dao.remoteIdOf(id, accountId)
            dao.delete(id)
            if (remote != null) {
                runCatching { api.deleteDraft(remote) }
                    .recoverCatching { kotlinx.coroutines.delay(3_000); api.deleteDraft(remote) }
                    .exceptionOrNull()?.let { AppLog.e("draft", "could not delete on the server", it) }
            }
        }
    }

    private suspend fun mimeOf(mail: Mail) = MimeBuilder.build(
        mail.senderName, mail.subject, mail.body, mail.outgoing, mail.cc, mail.bcc, mail.outgoingHtml,
        mail.inReplyTo, mail.references, mail.outgoingImages, from = MimeBuilder.fromHeader(senderName(), selfEmail()),
    )

    override fun send(mail: Mail) {
        scope.launch(Dispatchers.IO) {
            val localId = dao.insert(mail.toEntity(accountId, remoteId = null).copy(localId = 0, folder = Folder.SENT.name))
            try {
                api.send(MimeBuilder.build(
                    mail.senderName, mail.subject, mail.body, mail.outgoing, mail.cc, mail.bcc, mail.outgoingHtml,
                    mail.inReplyTo, mail.references, mail.outgoingImages, from = MimeBuilder.fromHeader(senderName(), selfEmail()),
                ), mail.threadId)
                AppLog.i("send", "sent, ${mail.outgoing.size} attachment(s)")
                dao.delete(localId) // the real copy arrives with the next sync
                refresh()
            } catch (e: Exception) {
                AppLog.e("send", "could not send, kept as a draft", e)
                // Keep it as a draft so the text is not lost.
                dao.updateFolder(localId, Folder.DRAFTS.name)
            }
        }
    }

    /** The Gmail way of sending, shared by a normal send and by the Outbox. */
    private suspend fun transmit(mail: Mail) {
        api.send(
            MimeBuilder.build(
                mail.senderName, mail.subject, mail.body, mail.outgoing, mail.cc, mail.bcc, mail.outgoingHtml,
                mail.inReplyTo, mail.references, mail.outgoingImages, from = MimeBuilder.fromHeader(senderName(), selfEmail()),
            ),
            mail.threadId,
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
                // What is already in the Inbox goes to the archive now.
                write(mails.value.filter { it.accountId == accountId && it.threadId == thread && it.folder == Folder.INBOX }
                    .map { it to it.copy(folder = Folder.ARCHIVE, read = true) })
            } else {
                dao.unmute(accountId, thread)
            }
        }
    }

    /** A message of a muted conversation does not stay in the Inbox: it is archived on the server and here. */
    private suspend fun applyMute(entity: MessageEntity): MessageEntity {
        val thread = entity.threadId ?: return entity
        if (entity.folder != Folder.INBOX.name || dao.mutedCount(accountId, thread) == 0) return entity
        try { api.modify(entity.remoteId!!, emptyList(), listOf("INBOX", "UNREAD")) } catch (_: Exception) { /* next sync corrects it */ }
        return entity.copy(folder = Folder.ARCHIVE.name, read = true)
    }

    override suspend fun unsubscribeInfo(id: Long): UnsubscribeInfo? = withContext(Dispatchers.IO) {
        val remote = dao.remoteIdOf(id, accountId) ?: return@withContext null
        try {
            val h = api.getHeaders(remote, Unsubscribe.HEADERS)
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
        // The sync runs in the app's scope, not in the caller's: a screen that goes away (its ViewModel is replaced
        // when the accounts shown change) must not cancel a sync that the next screen counts on.
        return scope.async(Dispatchers.IO) {
            try {
                sync(quick)
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e("sync", "...${accountId.takeLast(4)} ${if (quick) "quick" else "full"} sync failed", e)
                false
            } finally {
                _syncing.value = false
            }
        }.await()
    }

    override suspend fun emptyFolder(folder: Folder): Boolean = withContext(Dispatchers.IO) {
        val query = when (folder) {
            Folder.TRASH -> "in:trash"
            Folder.SPAM -> "in:spam"
            else -> return@withContext false
        }
        try {
            val ids = api.listMessageIds(query, 1000)
            ids.chunked(1000).forEach { api.batchDelete(it) }
            dao.deleteRemoteInFolder(accountId, folder.name)
            AppLog.i("empty", "...${accountId.takeLast(4)} Gmail ${folder.name} emptied (${ids.size})")
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
            val file = File(dir, AttachmentFinder.safeFileName(subject.ifBlank { "message" }).take(60) + ".eml")
            file.writeBytes(api.getRaw(remote))
            AppLog.i("eml", "saved a message as .eml (${file.length()} bytes)")
            file
        } catch (e: Exception) {
            AppLog.e("eml", "could not save the message as .eml", e)
            null
        }
    }

    override suspend fun replyInfo(id: Long): Pair<ReplyInfo, String?>? = withContext(Dispatchers.IO) {
        val remote = dao.remoteIdOf(id, accountId) ?: return@withContext null
        try {
            api.getReplyInfo(remote) to selfEmail()
        } catch (e: Exception) {
            AppLog.e("reply", "could not read the headers of the message", e)
            null
        }
    }

    override suspend fun attachments(id: Long): List<AttachmentInfo> = withContext(Dispatchers.IO) {
        val remote = dao.remoteIdOf(id, accountId) ?: return@withContext emptyList()
        try {
            AttachmentFinder.find(api.getMessage(remote))
        } catch (e: Exception) {
            AppLog.e("attach", "could not list attachments", e)
            emptyList()
        }
    }

    override suspend fun attachmentFile(id: Long, attachment: AttachmentInfo): File? = withContext(Dispatchers.IO) {
        val remote = dao.remoteIdOf(id, accountId) ?: return@withContext null
        try {
            val dir = File(attachmentsDir, remote.filter { it.isLetterOrDigit() }).apply { mkdirs() }
            val file = File(dir, AttachmentFinder.safeFileName(attachment.filename))
            if (file.exists() && file.length() > 0) return@withContext file
            // Gmail may hand out a new attachment id each time the message is read: look it up again.
            val fresh = AttachmentFinder.find(api.getMessage(remote))
                .firstOrNull { it.filename == attachment.filename && it.size == attachment.size }
                ?: return@withContext null
            file.writeBytes(api.getAttachment(remote, fresh.attachmentId))
            AppLog.i("attach", "downloaded one file (${file.length()} bytes)")
            file
        } catch (e: Exception) {
            AppLog.e("attach", "download failed", e)
            null
        }
    }

    override suspend fun full(id: Long): Mail? = withContext(Dispatchers.IO) {
        try {
            val light = dao.light(id)?.takeIf { it.accountId == accountId } ?: return@withContext null
            // Texts are read in pieces: a very long one (stored before they were capped) must not break the cursor.
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
                val piece = dao.htmlChunk(id, start, CHUNK) ?: break // null: the HTML was never downloaded
                (html ?: StringBuilder().also { html = it }).append(piece)
                if (piece.length < CHUNK) break
                start += CHUNK
            }
            light.copy(body = body.toString(), bodyHtml = html?.toString()).toMail()
        } catch (e: Exception) {
            AppLog.e("db", "could not read message ${id}", e)
            null
        }
    }

    override suspend fun inlineImages(id: Long): Map<String, String> = withContext(Dispatchers.IO) {
        val remote = dao.remoteIdOf(id, accountId) ?: return@withContext emptyMap()
        val dir = File(inlineDir, remote)
        try {
            saveInlineImages(remote, null) // normally already done by the sync: then this returns at once
            File(dir, INLINE_INDEX).readLines().filter { it.isNotBlank() }.associate { line ->
                val (cid, mime, file) = line.split("\t")
                cid to "data:$mime;base64," + Base64.getEncoder().encodeToString(File(dir, file).readBytes())
            }
        } catch (_: Exception) {
            emptyMap() // offline or the message is gone: the pictures simply are not shown
        }
    }

    /**
     * Downloads the pictures embedded in a message and keeps them on the phone, unless that was done before.
     * [message] is the full message when the caller already has it (saves one request).
     */
    private suspend fun saveInlineImages(remote: String, message: GmailMessage?) {
        val dir = File(inlineDir, remote)
        val index = File(dir, INLINE_INDEX)
        if (index.exists()) return
        val parts = InlineImages.find(message ?: api.getMessage(remote))
        dir.mkdirs()
        val lines = coroutineScope {
            parts.mapIndexed { n, part ->
                async(Dispatchers.IO) {
                    val bytes = part.data ?: part.attachmentId?.let { api.getAttachment(remote, it) }
                    if (bytes == null || bytes.size > MAX_INLINE_BYTES) {
                        null
                    } else {
                        File(dir, "$n").writeBytes(bytes)
                        "${part.cid}\t${part.mimeType}\t$n"
                    }
                }
            }.awaitAll().filterNotNull()
        }
        index.writeText(lines.joinToString("\n"))
    }

    /**
     * @param quick only looks for new and removed messages (one request when nothing changed); used by the
     * automatic checks. A full sync also brings label, read and star changes made elsewhere.
     */
    private suspend fun sync(quick: Boolean) {
        val start = marks.historyId()
        val fullDue = System.currentTimeMillis() - marks.lastFullMillis() > FULL_EVERY_MILLIS
        if (start != null && !fullDue) {
            try {
                delta(start, quick)
                return
            } catch (e: GmailHttpException) {
                if (e.code != 404) throw e
                AppLog.i("sync", "...${accountId.takeLast(4)} history too old, full sync")
            }
        }
        fullSync(quick)
    }

    /** Only what changed since [startId] (Gmail's history): a couple of requests when nothing happened. */
    private suspend fun delta(startId: String, quick: Boolean) {
        val cached = dao.remoteOnes(accountId).associateBy { it.remoteId!! }
        // An empty copy (account added again) has nothing to apply changes to: list everything instead.
        if (cached.isEmpty()) throw GmailHttpException(404, "empty local copy")
        if (!quick) syncLabels()
        val changes = api.changesSince(startId)
        val gate = Semaphore(6)
        val arrived = java.util.concurrent.ConcurrentLinkedQueue<MessageEntity>()
        coroutineScope {
            // Cached messages whose HTML was never kept (a migration clears it) are downloaded again here too.
            val missing = cached.filterValues { it.bodyHtml == null }.keys
            (changes.added + changes.relabelled + missing).map { id ->
                async(Dispatchers.IO) {
                    gate.withPermit {
                        val old = cached[id]
                        try {
                            if (old == null || old.bodyHtml == null) {
                                if (old != null || id in changes.added) ingest(id, old, arrived)
                            } else {
                                applyLabels(old, api.getLabels(id).toSet())
                            }
                        } catch (e: GmailHttpException) {
                            if (e.code != 404) throw e // gone already: the deleted list of a later sync drops it
                        }
                    }
                }
            }.awaitAll()
        }
        val gone = changes.deleted.mapNotNull { cached[it]?.localId }
        gone.chunked(500).forEach { dao.deleteAll(accountId, it) }
        marks.save(changes.historyId, fullToo = false)
        AppLog.i("sync", "...${accountId.takeLast(4)} ${if (quick) "quick" else "full"} delta ok: ${changes.added.size} added, ${changes.relabelled.size} relabelled, ${gone.size} removed, ${arrived.size} new unread")
        if (arrived.isNotEmpty()) {
            onNewMail(accountId, arrived.sortedByDescending { it.timeMillis }.map { it.toMail() })
        }
    }

    /**
     * Downloads a message whole and stores it (replacing [old]); chats are not kept. [force] keeps it even when it
     * only carries labels that do not sync on their own (the user asked for that label).
     */
    private suspend fun ingest(id: String, old: MessageEntity?, arrived: java.util.Queue<MessageEntity>, force: Boolean = false) {
        val message = api.getMessage(id)
        if (message.labelIds.any { it == "CHAT" }) {
            if (old != null) dao.delete(old.localId)
            return
        }
        val entity = applyMute(GmailParser.parse(message).toEntity(accountId, id).capped())
        if (!force && entity.hiddenByLabels()) return // not fetched on its own (a copy fetched by hand stays)
        // Embedded pictures first, so they are already there when the message shows up.
        if (entity.bodyHtml?.contains("cid:", ignoreCase = true) == true) {
            try { saveInlineImages(id, message) } catch (_: Exception) { /* retried when the message is opened */ }
        }
        if (old == null) {
            // The new row's id is what the notification (and its buttons) points at.
            val localId = dao.insert(entity)
            if (!entity.read && entity.folder == Folder.INBOX.name && entity.timeMillis > System.currentTimeMillis() - RECENT_MILLIS) {
                arrived.add(entity.copy(localId = localId)) // not an old message that only now fell inside the synced window
            }
        } else {
            dao.update(entity.copy(localId = old.localId, pinned = old.pinned, snoozedUntil = old.snoozedUntil))
        }
    }

    /** Puts the labels the server has on the cached copy (and drops it if it became spam). */
    private suspend fun applyLabels(old: MessageEntity, labels: Set<String>) {
        if (touched(old.localId)) return // changed here a moment ago: what the server says may be older than that
        val state = LabelDiff.stateOf(labels)
        val joined = labels.sorted().joinToString(" ")
        if (state.folder.name != old.folder || state.read != old.read || state.starred != old.starred || joined != old.labelIds) {
            dao.updateState(old.localId, state.folder.name, state.read, state.starred, joined)
        }
    }

    private suspend fun fullSync(quick: Boolean) {
        // Taken before listing: anything that changes meanwhile shows up in the next delta.
        val historyAtStart = try { api.currentHistoryId() } catch (_: Exception) { null }
        if (!quick) syncLabels()
        // The newest messages, and the newest spam apart so that a flood of spam never pushes real mail out of the window.
        val remoteIds = (api.listMessageIds(QUERY, LIMIT) + api.listMessageIds("in:spam", SPAM_LIMIT)).distinct()
        val cached = dao.remoteOnes(accountId).associateBy { it.remoteId!! }
        val gate = Semaphore(6)
        val arrived = java.util.concurrent.ConcurrentLinkedQueue<MessageEntity>()
        coroutineScope {
            remoteIds.map { id ->
                async(Dispatchers.IO) {
                    gate.withPermit {
                        val old = cached[id]
                        if (old == null || old.bodyHtml == null) {
                            // New message, or one saved before HTML was kept: download it whole.
                            val message = api.getMessage(id)
                            val entity = applyMute(GmailParser.parse(message).toEntity(accountId, id).capped())
                            if (entity.hiddenByLabels()) return@withPermit // not fetched on its own (a copy fetched by hand stays)
                            // Embedded pictures first, so they are already there when the message shows up.
                            if (entity.bodyHtml?.contains("cid:", ignoreCase = true) == true) {
                                try { saveInlineImages(id, message) } catch (_: Exception) { /* retried when the message is opened */ }
                            }
                            if (old == null) {
                                val localId = dao.insert(entity)
                                if (!entity.read && entity.folder == Folder.INBOX.name && entity.timeMillis > System.currentTimeMillis() - RECENT_MILLIS) {
                                    arrived.add(entity.copy(localId = localId)) // not an old message that only now fell inside the synced window
                                }
                            } else {
                                dao.update(entity.copy(localId = old.localId, pinned = old.pinned, snoozedUntil = old.snoozedUntil))
                            }
                        } else if (!quick && !touched(old.localId)) {
                            val labels = api.getLabels(id).toSet()
                            val state = LabelDiff.stateOf(labels)
                            val joined = labels.sorted().joinToString(" ")
                            if (state.folder.name != old.folder || state.read != old.read || state.starred != old.starred ||
                                joined != old.labelIds
                            ) {
                                dao.updateState(old.localId, state.folder.name, state.read, state.starred, joined)
                            }
                        }
                    }
                }
            }.awaitAll()
        }
        // Messages of labels fetched by hand stay although they are older than the window (Gmail's history still
        // removes them if they are deleted).
        val gone = cached.filterKeys { it !in remoteIds }.values.filterNot { it.inManualLabel() }.map { it.localId }
        gone.chunked(500).forEach { dao.deleteAll(accountId, it) }
        if (historyAtStart != null) marks.save(historyAtStart, fullToo = true)
        AppLog.i("sync", "...${accountId.takeLast(4)} ${if (quick) "quick" else "full"} ok:${remoteIds.size} listed, ${cached.size} cached, ${arrived.size} new unread, ${gone.size} removed")
        // The first sync of an account brings in its whole history: that is not "new mail".
        if (cached.isNotEmpty() && arrived.isNotEmpty()) {
            onNewMail(accountId, arrived.sortedByDescending { it.timeMillis }.map { it.toMail() })
        }
        if (!quick) {
            // Messages saved by an older version may still lack their embedded pictures: fetch them in the background.
            scope.launch(Dispatchers.IO) {
                dao.remoteIdsWithInlineImages(accountId)
                    .forEach { try { saveInlineImages(it, null) } catch (_: Exception) { /* next time */ } }
            }
        }
    }

    private suspend fun syncLabels() {
        val labels = api.listUserLabels()
        labelDao.clear(accountId)
        labelDao.insertAll(labels.map { LabelEntity(accountId, it.id, it.name) })
    }

    private companion object {
        const val QUERY = "in:anywhere -in:spam -in:chats"
        const val LIMIT = 120
        const val SPAM_LIMIT = 30
        /** How many of a label's newest messages a pull-to-refresh inside it brings. */
        const val LABEL_LIMIT = 50
        /** How long after a local change the server's labels are not trusted over the phone's. */
        const val TOUCH_GRACE_MILLIS = 8_000L
        const val MAX_INLINE_BYTES = 4 * 1024 * 1024
        const val RECENT_MILLIS = 3L * 24 * 60 * 60 * 1000
        const val CHUNK = 250_000

        /** A real listing at least this often, so messages that fell out of the window or were missed are corrected. */
        const val FULL_EVERY_MILLIS = 6L * 60 * 60 * 1000

        /** Renamed whenever the way embedded pictures are found changes, so they are fetched again. */
        const val INLINE_INDEX = "index.v2"
    }
}

/** No single row may come close to Android's 2 MB cursor window: very long texts are cut. */
internal fun MessageEntity.capped() = copy(body = body.take(400_000), bodyHtml = bodyHtml?.take(1_200_000))

internal fun MessageEntity.toMail() = Mail(
    id = localId, senderName = senderName, senderEmail = senderEmail, subject = subject, body = body,
    timeMillis = timeMillis, folder = Folder.valueOf(folder), read = read, starred = starred, pinned = pinned,
    snoozedUntil = snoozedUntil, hasAttachment = hasAttachment,
    labelIds = labelIds.split(" ").filter { it.isNotEmpty() }.toSet(), bodyHtml = bodyHtml, accountId = accountId,
    threadId = threadId,
    sendAt = sendAt,
    cc = cc, bcc = bcc,
    inReplyTo = replyRefs.substringBefore('\n').ifBlank { null }, references = replyRefs.substringAfter('\n', "").ifBlank { null },
)

internal fun Mail.toEntity(accountId: String, remoteId: String?) = MessageEntity(
    localId = id, accountId = accountId, remoteId = remoteId, folder = folder.name, senderName = senderName,
    senderEmail = senderEmail, subject = subject, body = body, timeMillis = timeMillis, read = read,
    starred = starred, pinned = pinned, snoozedUntil = snoozedUntil, hasAttachment = hasAttachment,
    labelIds = labelIds.sorted().joinToString(" "), bodyHtml = outgoingHtml ?: bodyHtml ?: "", threadId = threadId,
    cc = cc, bcc = bcc, replyRefs = inReplyTo.orEmpty() + "\n" + references.orEmpty(),
    sendAt = sendAt,
)

private fun ParsedMessage.toEntity(accountId: String, remoteId: String): MessageEntity {
    val state = LabelDiff.stateOf(labels)
    return MessageEntity(
        accountId = accountId, remoteId = remoteId, folder = state.folder.name,
        senderName = fromName, senderEmail = fromEmail, subject = subject, body = body, timeMillis = timeMillis,
        read = state.read, starred = state.starred, pinned = false, snoozedUntil = null, hasAttachment = hasAttachment,
        labelIds = labels.sorted().joinToString(" "), bodyHtml = html, threadId = threadId,
    )
}

/** Mapping between Gmail labels and Cemail's folder / read / starred. */
object LabelDiff {
    data class State(val folder: Folder, val read: Boolean, val starred: Boolean)

    fun stateOf(labels: Set<String>) = State(
        folder = when {
            "TRASH" in labels -> Folder.TRASH
            "SPAM" in labels -> Folder.SPAM
            "DRAFT" in labels -> Folder.DRAFTS
            "INBOX" in labels -> Folder.INBOX
            "SENT" in labels -> Folder.SENT
            else -> Folder.ARCHIVE
        },
        read = "UNREAD" !in labels,
        starred = "STARRED" in labels,
    )

    /** Labels to add and remove on Gmail to turn [before] into [after]. */
    fun between(before: Mail, after: Mail): Pair<List<String>, List<String>> {
        val add = mutableListOf<String>()
        val remove = mutableListOf<String>()
        if (before.folder != after.folder) {
            when (after.folder) {
                Folder.INBOX -> { add += "INBOX"; remove += "TRASH" }
                Folder.ARCHIVE -> { remove += "INBOX"; remove += "TRASH" }
                Folder.TRASH -> { add += "TRASH"; remove += "INBOX" }
                Folder.SPAM -> { add += "SPAM"; remove += "INBOX" }
                else -> Unit
            }
        }
        // Taking a message out of Spam (to the Inbox, the archive or the trash) removes the Spam label.
        if (before.folder == Folder.SPAM && after.folder != Folder.SPAM) remove += "SPAM"
        if (before.read != after.read) if (after.read) remove += "UNREAD" else add += "UNREAD"
        if (before.starred != after.starred) if (after.starred) add += "STARRED" else remove += "STARRED"
        // Labels the user created (their ids look like "Label_12") put on or taken off from the message.
        add += (after.labelIds - before.labelIds).filter { it.startsWith("Label_") }
        remove += (before.labelIds - after.labelIds).filter { it.startsWith("Label_") }
        return add to remove
    }
}
