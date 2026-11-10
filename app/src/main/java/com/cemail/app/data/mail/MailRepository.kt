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

    /** Brings the local copy up to date with the server. Returns false if it failed. */
    suspend fun refresh(quick: Boolean = false): Boolean

    /**
     * The whole message (text and HTML). The list holds only the start of each text, so what is opened is
     * read from here. null if it is gone.
     */
    suspend fun full(id: Long): Mail?

    /** What a reply to this message needs (thread, Message-ID, recipients); null offline or in the demo. */
    suspend fun replyInfo(id: Long): Pair<com.cemail.app.data.gmail.ReplyInfo, String?>?

    /** The files attached to a message (needs a connection; empty if there are none or it fails). */
    suspend fun attachments(id: Long): List<com.cemail.app.data.gmail.AttachmentInfo>

    /** Downloads one attachment to the phone's cache (once) and returns the file, or null if it fails. */
    suspend fun attachmentFile(id: Long, attachment: com.cemail.app.data.gmail.AttachmentInfo): java.io.File?

    /** The pictures embedded in a message, as Content-ID → `data:` URI (downloaded once, then kept on the phone). */
    suspend fun inlineImages(id: Long): Map<String, String>
}

/** In-memory store seeded with example messages (used when no account is signed in). */
class DemoMailRepository(seed: List<Mail>, demoLabels: List<Label> = emptyList()) : MailRepository {
    override val labels: Flow<List<Label>> = kotlinx.coroutines.flow.flowOf(demoLabels)
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
    override suspend fun attachments(id: Long) = emptyList<com.cemail.app.data.gmail.AttachmentInfo>()
    override suspend fun attachmentFile(id: Long, attachment: com.cemail.app.data.gmail.AttachmentInfo): java.io.File? = null
    override suspend fun inlineImages(id: Long) = emptyMap<String, String>()
}
