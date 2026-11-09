package com.cemail.app.data.mail

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * What the UI needs from the mail store. The Gmail/Room implementation arrives in phases 1-2;
 * until then [DemoMailRepository] keeps the app usable and testable without an account.
 */
interface MailRepository {
    val mails: Flow<List<Mail>>
    fun update(ids: Set<Long>, transform: (Mail) -> Mail)
    fun restore(previous: List<Mail>)
    fun snapshot(ids: Set<Long>): List<Mail>
    fun add(mail: Mail)
    fun nextId(): Long
    fun remove(id: Long)
}

/** In-memory store seeded with example messages. */
class DemoMailRepository(seed: List<Mail>) : MailRepository {
    private val state = MutableStateFlow(seed)
    override val mails: StateFlow<List<Mail>> = state.asStateFlow()
    private var counter = (seed.maxOfOrNull { it.id } ?: 0L) + 1

    override fun update(ids: Set<Long>, transform: (Mail) -> Mail) =
        state.update { list -> list.map { if (it.id in ids) transform(it) else it } }

    override fun restore(previous: List<Mail>) {
        val byId = previous.associateBy { it.id }
        state.update { list -> list.map { byId[it.id] ?: it } }
    }

    override fun snapshot(ids: Set<Long>) = state.value.filter { it.id in ids }
    override fun add(mail: Mail) = state.update { listOf(mail) + it }
    override fun nextId() = counter++
    override fun remove(id: Long) = state.update { l -> l.filter { it.id != id } }
}
