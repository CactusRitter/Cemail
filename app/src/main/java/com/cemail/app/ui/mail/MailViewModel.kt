package com.cemail.app.ui.mail

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cemail.app.R
import com.cemail.app.data.mail.Folder
import com.cemail.app.data.mail.Mail
import com.cemail.app.data.mail.MailBox
import com.cemail.app.data.mail.MailRepository
import com.cemail.app.data.mail.matches
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

/** Something the user just did, offered to be undone for a few seconds (Spark-style). */
data class UndoEvent(val id: Long, @StringRes val message: Int, val previous: List<Mail>)

/** A message being written. */
data class Draft(val to: String = "", val subject: String = "", val body: String = "")

private data class Ui(
    val box: MailBox = MailBox.INBOX,
    val query: String = "",
    val searching: Boolean = false,
    val selection: Set<Long> = emptySet(),
    val openId: Long? = null,
    val draft: Draft? = null,
    val snoozeTargets: Set<Long>? = null,
    val undo: UndoEvent? = null,
)

data class MailUiState(
    val box: MailBox = MailBox.INBOX,
    val query: String = "",
    val searching: Boolean = false,
    val selection: Set<Long> = emptySet(),
    val open: Mail? = null,
    val draft: Draft? = null,
    val snoozeTargets: Set<Long>? = null,
    val undo: UndoEvent? = null,
    val pinned: List<Mail> = emptyList(),
    val others: List<Mail> = emptyList(),
    val unreadByBox: Map<MailBox, Int> = emptyMap(),
    val syncing: Boolean = false,
)

class MailViewModel(private val repo: MailRepository) : ViewModel() {
    private val ui = MutableStateFlow(Ui())
    private var undoCounter = 0L

    val state: StateFlow<MailUiState> = combine(repo.mails, ui, repo.syncing) { mails, u, syncing ->
        val now = System.currentTimeMillis()
        val inBox = mails.filter { it.inBox(u.box, now) }
        val shown = (if (u.query.isBlank()) inBox else mails.filter { it.folder != Folder.TRASH && it.matches(u.query) })
            .sortedByDescending { it.timeMillis }
        val showPinned = u.box == MailBox.INBOX && u.query.isBlank()
        MailUiState(
            box = u.box, query = u.query, searching = u.searching, selection = u.selection, syncing = syncing,
            open = u.openId?.let { id -> mails.firstOrNull { it.id == id } },
            draft = u.draft, snoozeTargets = u.snoozeTargets, undo = u.undo,
            pinned = if (showPinned) shown.filter { it.pinned } else emptyList(),
            others = if (showPinned) shown.filter { !it.pinned } else shown,
            unreadByBox = MailBox.entries.associateWith { b -> mails.count { !it.read && it.inBox(b, now) } },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MailUiState())

    private fun Mail.inBox(box: MailBox, now: Long) = when (box) {
        MailBox.INBOX -> folder == Folder.INBOX && !isSnoozed(now)
        MailBox.STARRED -> starred && folder != Folder.TRASH
        MailBox.SNOOZED -> folder == Folder.INBOX && isSnoozed(now)
        MailBox.SENT -> folder == Folder.SENT
        MailBox.DRAFTS -> folder == Folder.DRAFTS
        MailBox.ARCHIVE -> folder == Folder.ARCHIVE
        MailBox.TRASH -> folder == Folder.TRASH
    }

    init {
        refresh()
    }

    /** Syncs with the server; a failure is told with the same bar that offers Undo. */
    fun refresh() {
        viewModelScope.launch {
            if (!repo.refresh()) ui.update { it.copy(undo = UndoEvent(++undoCounter, R.string.sync_failed, emptyList())) }
        }
    }

    // --- navigation ---
    fun selectBox(box: MailBox) = ui.update { it.copy(box = box, query = "", searching = false, selection = emptySet()) }
    fun setSearching(on: Boolean) = ui.update { it.copy(searching = on, query = if (on) it.query else "") }
    fun setQuery(q: String) = ui.update { it.copy(query = q) }
    fun open(id: Long) {
        ui.update { it.copy(openId = id) }
        repo.update(setOf(id)) { it.copy(read = true) }
    }
    fun closeMail() = ui.update { it.copy(openId = null) }

    // --- selection ---
    fun toggleSelect(id: Long) = ui.update {
        it.copy(selection = if (id in it.selection) it.selection - id else it.selection + id)
    }
    fun clearSelection() = ui.update { it.copy(selection = emptySet()) }

    // --- actions (each one can be undone) ---
    private fun act(ids: Set<Long>, @StringRes message: Int?, closeOpen: Boolean = false, transform: (Mail) -> Mail) {
        if (ids.isEmpty()) return
        val before = repo.snapshot(ids)
        repo.update(ids, transform)
        ui.update {
            it.copy(
                selection = it.selection - ids,
                undo = message?.let { m -> UndoEvent(++undoCounter, m, before) } ?: it.undo,
                openId = if (closeOpen && it.openId in ids) null else it.openId,
            )
        }
    }

    fun archive(ids: Set<Long>) = act(ids, R.string.done_archived, true) { it.copy(folder = Folder.ARCHIVE, snoozedUntil = null) }
    fun trash(ids: Set<Long>) = act(ids, R.string.done_trashed, true) { it.copy(folder = Folder.TRASH, snoozedUntil = null) }
    fun moveToInbox(ids: Set<Long>) = act(ids, R.string.done_marked, true) { it.copy(folder = Folder.INBOX, snoozedUntil = null) }
    fun toggleStar(ids: Set<Long>) {
        val turnOn = repo.snapshot(ids).any { !it.starred }
        act(ids, null) { it.copy(starred = turnOn) }
    }
    fun togglePin(ids: Set<Long>) {
        val turnOn = repo.snapshot(ids).any { !it.pinned }
        act(ids, if (turnOn) R.string.done_pinned else R.string.done_marked) { it.copy(pinned = turnOn) }
    }
    fun markRead(ids: Set<Long>, read: Boolean) = act(ids, R.string.done_marked, true) { it.copy(read = read) }
    fun askSnooze(ids: Set<Long>) = ui.update { it.copy(snoozeTargets = ids) }
    fun dismissSnooze() = ui.update { it.copy(snoozeTargets = null) }
    fun snooze(option: SnoozeOption) {
        val ids = ui.value.snoozeTargets ?: return
        ui.update { it.copy(snoozeTargets = null) }
        val until = option.at(System.currentTimeMillis())
        act(ids, R.string.done_snoozed, true) { it.copy(snoozedUntil = until, read = true) }
    }
    fun undo() {
        val u = ui.value.undo ?: return
        if (u.previous.isNotEmpty()) repo.restore(u.previous)
        ui.update { it.copy(undo = null) }
    }
    fun dismissUndo(id: Long) = ui.update { if (it.undo?.id == id) it.copy(undo = null) else it }

    // --- compose ---
    fun newDraft(draft: Draft = Draft()) = ui.update { it.copy(draft = draft) }
    fun discardDraft() = ui.update { it.copy(draft = null) }
    fun updateDraft(draft: Draft) = ui.update { it.copy(draft = draft) }

    /** Re-opens a saved draft for editing (the stored copy is removed; it is saved again on close). */
    fun editDraft(mail: Mail) {
        repo.remove(mail.id)
        ui.update { it.copy(draft = Draft(mail.senderName, mail.subject, mail.body)) }
    }

    /** Leaves the compose screen: an empty draft is dropped, anything else is kept in Drafts. */
    fun closeCompose(me: String) {
        val d = ui.value.draft ?: return
        ui.update { it.copy(draft = null) }
        if (d.to.isNotBlank() || d.subject.isNotBlank() || d.body.isNotBlank()) {
            repo.add(d.toMail(repo.nextId(), me, Folder.DRAFTS))
            ui.update { it.copy(undo = UndoEvent(++undoCounter, R.string.done_draft_saved, emptyList())) }
        }
    }

    fun send(me: String) {
        val d = ui.value.draft ?: return
        ui.update { it.copy(draft = null) }
        repo.add(d.toMail(repo.nextId(), me, Folder.SENT))
        ui.update { it.copy(undo = UndoEvent(++undoCounter, R.string.done_sent, emptyList())) }
    }

    private fun Draft.toMail(id: Long, me: String, folder: Folder) = Mail(
        id = id, senderName = to, senderEmail = me, subject = subject, body = body,
        timeMillis = System.currentTimeMillis(), folder = folder, read = true,
    )

    class Factory(private val repo: MailRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MailViewModel(repo) as T
    }
}

enum class SnoozeOption(@StringRes val label: Int) {
    LATER(R.string.snooze_later), TOMORROW(R.string.snooze_tomorrow),
    WEEKEND(R.string.snooze_weekend), NEXT_WEEK(R.string.snooze_next_week);

    fun at(now: Long): Long {
        val c = Calendar.getInstance().apply { timeInMillis = now }
        fun morning() { c.set(Calendar.HOUR_OF_DAY, 8); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0) }
        when (this) {
            LATER -> c.add(Calendar.HOUR_OF_DAY, 3)
            TOMORROW -> { c.add(Calendar.DAY_OF_YEAR, 1); morning() }
            WEEKEND -> {
                do { c.add(Calendar.DAY_OF_YEAR, 1) } while (c.get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY)
                morning()
            }
            NEXT_WEEK -> {
                do { c.add(Calendar.DAY_OF_YEAR, 1) } while (c.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY)
                morning()
            }
        }
        return c.timeInMillis
    }
}
