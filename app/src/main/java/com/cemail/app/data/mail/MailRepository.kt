package com.cemail.app.data.mail

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** What the UI needs from the mail store: the demo store, or a Gmail account cached in Room. */
interface MailRepository {
    val mails: Flow<List<Mail>>

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
    suspend fun refresh(): Boolean
}

/** In-memory store seeded with example messages (used when no account is signed in). */
class DemoMailRepository(seed: List<Mail>) : MailRepository {
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
    override suspend fun refresh() = true
}
