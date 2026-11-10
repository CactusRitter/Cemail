package com.cemail.app.data.mail

import com.cemail.app.data.db.LabelDao
import com.cemail.app.data.db.LabelEntity
import com.cemail.app.data.db.MessageDao
import com.cemail.app.data.db.MessageEntity
import com.cemail.app.data.gmail.GmailApi
import com.cemail.app.data.gmail.GmailParser
import com.cemail.app.data.gmail.InlineImages
import com.cemail.app.data.gmail.MimeBuilder
import java.io.File
import java.util.Base64
import kotlinx.coroutines.withContext
import com.cemail.app.data.gmail.ParsedMessage
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
import kotlinx.coroutines.flow.stateIn
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
) : MailRepository {
    override val mails: StateFlow<List<Mail>> = dao.observe(accountId)
        .map { list -> list.map { it.toMail() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override val labels: Flow<List<Label>> = labelDao.observe(accountId)
        .map { list -> list.map { Label(it.id, it.name) } }

    private val _syncing = MutableStateFlow(false)
    override val syncing: StateFlow<Boolean> = _syncing

    override fun snapshot(ids: Set<Long>) = mails.value.filter { it.id in ids }

    override fun update(ids: Set<Long>, transform: (Mail) -> Mail) {
        val changes = snapshot(ids).map { it to transform(it) }
        write(changes)
    }

    override fun restore(previous: List<Mail>) {
        val current = mails.value.associateBy { it.id }
        write(previous.mapNotNull { old -> current[old.id]?.let { it to old } })
    }

    private fun write(changes: List<Pair<Mail, Mail>>) {
        scope.launch(Dispatchers.IO) {
            for ((before, after) in changes) {
                val entity = dao.get(after.id) ?: continue
                dao.update(entity.copy(
                    folder = after.folder.name, read = after.read, starred = after.starred,
                    pinned = after.pinned, snoozedUntil = after.snoozedUntil,
                ))
                val remote = entity.remoteId ?: continue
                val (add, remove) = LabelDiff.between(before, after)
                if (add.isNotEmpty() || remove.isNotEmpty()) {
                    try { api.modify(remote, add, remove) } catch (_: Exception) { /* next sync puts the server state back */ }
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
                api.send(MimeBuilder.build(mail.senderName, mail.subject, mail.body))
                dao.delete(localId) // the real copy arrives with the next sync
                refresh()
            } catch (_: Exception) {
                // Keep it as a draft so the text is not lost.
                dao.get(localId)?.let { dao.update(it.copy(folder = Folder.DRAFTS.name)) }
            }
        }
    }

    override fun nextId() = 0L
    override fun remove(id: Long) {
        scope.launch(Dispatchers.IO) { dao.delete(id) }
    }

    override suspend fun refresh(): Boolean {
        if (!_syncing.compareAndSet(expect = false, update = true)) return true
        return try {
            sync()
            true
        } catch (_: Exception) {
            false
        } finally {
            _syncing.value = false
        }
    }

    override suspend fun inlineImages(id: Long): Map<String, String> = withContext(Dispatchers.IO) {
        val remote = dao.get(id)?.remoteId ?: return@withContext emptyMap()
        val dir = File(inlineDir, remote)
        val index = File(dir, "index")
        try {
            if (!index.exists()) {
                dir.mkdirs()
                val lines = mutableListOf<String>()
                InlineImages.find(api.getMessage(remote)).forEachIndexed { n, part ->
                    val bytes = part.data ?: part.attachmentId?.let { api.getAttachment(remote, it) } ?: return@forEachIndexed
                    if (bytes.size > MAX_INLINE_BYTES) return@forEachIndexed
                    File(dir, "$n").writeBytes(bytes)
                    lines += "${part.cid}\t${part.mimeType}\t$n"
                }
                index.writeText(lines.joinToString("\n"))
            }
            index.readLines().filter { it.isNotBlank() }.associate { line ->
                val (cid, mime, file) = line.split("\t")
                cid to "data:$mime;base64," + Base64.getEncoder().encodeToString(File(dir, file).readBytes())
            }
        } catch (_: Exception) {
            emptyMap() // offline or the message is gone: the pictures simply are not shown
        }
    }

    private suspend fun sync() {
        syncLabels()
        val remoteIds = api.listMessageIds(QUERY, LIMIT)
        val cached = dao.remoteOnes(accountId).associateBy { it.remoteId!! }
        val gate = Semaphore(6)
        coroutineScope {
            remoteIds.map { id ->
                async(Dispatchers.IO) {
                    gate.withPermit {
                        val old = cached[id]
                        if (old == null || old.bodyHtml == null) {
                            // New message, or one saved before HTML was kept: download it whole.
                            val entity = GmailParser.parse(api.getMessage(id)).toEntity(accountId, id)
                            if (old == null) {
                                dao.insert(entity)
                            } else {
                                dao.update(entity.copy(localId = old.localId, pinned = old.pinned, snoozedUntil = old.snoozedUntil))
                            }
                        } else {
                            val labels = api.getLabels(id).toSet()
                            val state = LabelDiff.stateOf(labels)
                            val joined = labels.sorted().joinToString(" ")
                            if (state.folder.name != old.folder || state.read != old.read || state.starred != old.starred ||
                                joined != old.labelIds
                            ) {
                                dao.update(old.copy(folder = state.folder.name, read = state.read, starred = state.starred, labelIds = joined))
                            }
                        }
                    }
                }
            }.awaitAll()
        }
        val gone = cached.filterKeys { it !in remoteIds }.values.map { it.localId }
        gone.chunked(500).forEach { dao.deleteAll(accountId, it) }
    }

    private suspend fun syncLabels() {
        val labels = api.listUserLabels()
        labelDao.clear(accountId)
        labelDao.insertAll(labels.map { LabelEntity(accountId, it.id, it.name) })
    }

    private companion object {
        const val QUERY = "in:anywhere -in:spam -in:chats"
        const val LIMIT = 120
        const val MAX_INLINE_BYTES = 4 * 1024 * 1024
    }
}

private fun MessageEntity.toMail() = Mail(
    id = localId, senderName = senderName, senderEmail = senderEmail, subject = subject, body = body,
    timeMillis = timeMillis, folder = Folder.valueOf(folder), read = read, starred = starred, pinned = pinned,
    snoozedUntil = snoozedUntil, hasAttachment = hasAttachment,
    labelIds = labelIds.split(" ").filter { it.isNotEmpty() }.toSet(), bodyHtml = bodyHtml,
)

private fun Mail.toEntity(accountId: String, remoteId: String?) = MessageEntity(
    localId = id, accountId = accountId, remoteId = remoteId, folder = folder.name, senderName = senderName,
    senderEmail = senderEmail, subject = subject, body = body, timeMillis = timeMillis, read = read,
    starred = starred, pinned = pinned, snoozedUntil = snoozedUntil, hasAttachment = hasAttachment,
    labelIds = labelIds.sorted().joinToString(" "), bodyHtml = bodyHtml ?: "",
)

private fun ParsedMessage.toEntity(accountId: String, remoteId: String): MessageEntity {
    val state = LabelDiff.stateOf(labels)
    return MessageEntity(
        accountId = accountId, remoteId = remoteId, folder = state.folder.name,
        senderName = fromName, senderEmail = fromEmail, subject = subject, body = body, timeMillis = timeMillis,
        read = state.read, starred = state.starred, pinned = false, snoozedUntil = null, hasAttachment = hasAttachment,
        labelIds = labels.sorted().joinToString(" "), bodyHtml = html,
    )
}

/** Mapping between Gmail labels and Cemail's folder / read / starred. */
object LabelDiff {
    data class State(val folder: Folder, val read: Boolean, val starred: Boolean)

    fun stateOf(labels: Set<String>) = State(
        folder = when {
            "TRASH" in labels -> Folder.TRASH
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
                else -> Unit
            }
        }
        if (before.read != after.read) if (after.read) remove += "UNREAD" else add += "UNREAD"
        if (before.starred != after.starred) if (after.starred) add += "STARRED" else remove += "STARRED"
        return add to remove
    }
}
