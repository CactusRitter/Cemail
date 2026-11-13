package com.cemail.app.data.mail

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** What the UI needs from the mail store: the demo store, or a Gmail account cached in Room. */
interface MailRepository {
    val mails: Flow<List<Mail>>

    /** The Gmail labels the user created, shown as folders. */
    val labels: Flow<List<Label>>

    /** The labels the user created in the account that owns a message (empty if that account is not this store's). */
    suspend fun userLabels(accountId: String): List<Label>

    /** Turns the instant push on ([topic] is the Pub/Sub topic Gmail publishes to) or off (null). Only Gmail has it. */
    suspend fun setPush(topic: String?) {}

    /** Every user label of the account, including the ones switched off in Settings (which [userLabels] leaves out). */
    suspend fun allLabels(accountId: String): List<Label> = userLabels(accountId)

    /** true while a sync with the server is running. */
    val syncing: StateFlow<Boolean>

    fun update(ids: Set<Long>, transform: (Mail) -> Mail)
    fun restore(previous: List<Mail>)
    fun snapshot(ids: Set<Long>): List<Mail>

    /** Saves a message that only lives on the phone (a draft). */
    fun add(mail: Mail)

    /** Sends a message; it shows up in Sent. */
    fun send(mail: Mail)
    fun nextId(): Long
    fun remove(id: Long)

    /**
     * Saves [mail] as a draft in place of the draft [replacing] (if any): one copy only, here and, for accounts that
     * keep drafts on the server, there too. It is only called when the compose screen closes, never while typing.
     */
    fun saveDraft(mail: Mail, replacing: Long?) {
        replacing?.let(::remove)
        add(mail)
    }

    /** Throws a saved draft away for good, here and on the server (after sending it, or when it was emptied). */
    fun discardDraft(id: Long) = remove(id)

    /** Brings the local copy up to date with the server. Returns false if it failed. */
    suspend fun refresh(quick: Boolean = false): Boolean

    /** Fetches the newest messages of one label now, also if it does not sync on its own (Gmail). false if it failed. */
    suspend fun refreshLabel(labelId: String): Boolean = true

    /**
     * The whole message (text and HTML). The list holds only the start of each text, so what is opened is
     * read from here. null if it is gone.
     */
    suspend fun full(id: Long): Mail?

    /** What a reply to this message needs (thread, Message-ID, recipients); null offline or in the demo. */
    suspend fun replyInfo(id: Long): Pair<com.cemail.app.data.gmail.ReplyInfo, String?>?

    /** Saves the message as an .eml file in the cache and returns it (needs a connection); null if it fails. */
    suspend fun emlFile(id: Long): java.io.File?

    /** The files attached to a message (needs a connection; empty if there are none or it fails). */
    suspend fun attachments(id: Long): List<com.cemail.app.data.gmail.AttachmentInfo>

    /** Downloads one attachment to the phone's cache (once) and returns the file, or null if it fails. */
    suspend fun attachmentFile(id: Long, attachment: com.cemail.app.data.gmail.AttachmentInfo): java.io.File?

    /** Puts the message in the Outbox to be sent at [at] (a few seconds ahead for "undo send"). Returns its id. */
    suspend fun schedule(mail: Mail, at: Long): Long

    /** Takes a message out of the Outbox (it will not be sent). */
    fun cancelScheduled(id: Long)

    /** Sends a message of the Outbox now (called when its time comes). true if it went out. */
    suspend fun deliver(id: Long): Boolean

    /** true if the conversation of this message is muted. */
    suspend fun isMuted(id: Long): Boolean

    /** Mutes (archiving it now, and every new message of it from now on) or unmutes the conversation of a message. */
    fun setMuted(id: Long, muted: Boolean)

    /** How to leave the mailing list this message came from (from its headers; needs a connection), or null. */
    suspend fun unsubscribeInfo(id: Long): UnsubscribeInfo?

    /** Ids of the messages whose subject, sender or whole text contain every word of [query] (works offline). */
    suspend fun searchIds(query: String): Set<Long>

    /**
     * Brings up to [OLDER_PAGE] messages of [place] older than the oldest one on the phone (Load more at the end of a
     * list). They stay on the phone afterwards, also when they are older than the automatic sync reaches. Returns how
     * many new messages came (0: there are no more), or -1 if it failed.
     */
    suspend fun loadOlder(place: MailPlace): Int = 0

    /**
     * Looks for [query] on the server, in the whole mailbox and any date, and keeps on the phone what it finds (it then
     * shows up in [searchIds]). Returns how many new messages came, or -1 if it failed.
     */
    suspend fun searchServer(query: String): Int = 0

    /** Deletes for good everything in the Trash or Spam folder. false if the server refused (nothing is removed then). */
    suspend fun emptyFolder(folder: Folder): Boolean

    /** The pictures embedded in a message, as Content-ID → `data:` URI (downloaded once, then kept on the phone). */
    suspend fun inlineImages(id: Long): Map<String, String>
}

/** In-memory store seeded with example messages (used when no account is signed in). */
class DemoMailRepository(seed: List<Mail>, private val demoLabels: List<Label> = emptyList()) : MailRepository {
    override val labels: Flow<List<Label>> = kotlinx.coroutines.flow.flowOf(demoLabels)
    override suspend fun userLabels(accountId: String): List<Label> = demoLabels
    private val state = MutableStateFlow(seed)
    override val mails: StateFlow<List<Mail>> = state.asStateFlow()
    override val syncing: StateFlow<Boolean> = MutableStateFlow(false)
    private var counter = (seed.maxOfOrNull { it.id } ?: 0L) + 1

    override fun update(ids: Set<Long>, transform: (Mail) -> Mail) =
        state.update { list -> list.map { if (it.id in ids) transform(it) else it } }

    override fun restore(previous: List<Mail>) {
        val byId = previous.associateBy { it.id }
        state.update { list -> list.map { byId[it.id] ?: it } }
    }

    override fun snapshot(ids: Set<Long>) = state.value.filter { it.id in ids }
    override fun add(mail: Mail) = state.update { listOf(mail) + it }
    override fun send(mail: Mail) = add(mail)
    override fun nextId() = counter++
    override fun remove(id: Long) = state.update { l -> l.filter { it.id != id } }
    override suspend fun refresh(quick: Boolean) = true
    override suspend fun full(id: Long): Mail? = state.value.firstOrNull { it.id == id }
    override suspend fun replyInfo(id: Long): Pair<com.cemail.app.data.gmail.ReplyInfo, String?>? = null
    override suspend fun emlFile(id: Long): java.io.File? = null
    override suspend fun attachments(id: Long) = emptyList<com.cemail.app.data.gmail.AttachmentInfo>()
    override suspend fun attachmentFile(id: Long, attachment: com.cemail.app.data.gmail.AttachmentInfo): java.io.File? = null
    override suspend fun inlineImages(id: Long) = emptyMap<String, String>()
    override suspend fun searchIds(query: String): Set<Long> = emptySet()
    override suspend fun unsubscribeInfo(id: Long): UnsubscribeInfo? = null
    override suspend fun isMuted(id: Long) = false
    override fun setMuted(id: Long, muted: Boolean) = Unit
    override suspend fun schedule(mail: Mail, at: Long): Long {
        add(mail.copy(folder = Folder.SENT))
        return 0
    }
    override fun cancelScheduled(id: Long) = Unit
    override suspend fun deliver(id: Long) = false
    override suspend fun emptyFolder(folder: Folder): Boolean {
        state.update { l -> l.filter { it.folder != folder } }
        return true
    }
}

/** Where Load more looks: a folder, the starred messages or a label. */
sealed interface MailPlace {
    data class InFolder(val folder: Folder) : MailPlace
    data object Starred : MailPlace
    data class InLabel(val id: String) : MailPlace
}

/** How many older messages one Load more brings, and the most a server search keeps. */
const val OLDER_PAGE = 50

/** The sum of several accounts' answers to Load more or a server search: -1 only if one failed and nothing came. */
fun combineCounts(counts: List<Int>): Int {
    val got = counts.filter { it > 0 }.sum()
    return if (got == 0 && counts.any { it < 0 }) -1 else got
}

/** How many matches (ids only) a server search looks through to find the ones the phone does not have yet. */
const val SEARCH_SCAN = 500
