package com.cemail.app.ui.mail

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cemail.app.R
import com.cemail.app.data.mail.DateSection
import com.cemail.app.data.mail.Folder
import com.cemail.app.data.mail.Label
import com.cemail.app.data.gmail.AttachmentInfo
import com.cemail.app.data.gmail.OutAttachment
import com.cemail.app.data.mail.Mail
import com.cemail.app.data.mail.MailBox
import com.cemail.app.data.mail.MailRepository
import com.cemail.app.data.mail.matches
import com.cemail.app.data.mail.withKnownNames
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
data class Draft(
    val to: String = "",
    val subject: String = "",
    val body: String = "",
    val attachments: List<OutAttachment> = emptyList(),
)

private data class Ui(
    val box: MailBox = MailBox.INBOX,
    val query: String = "",
    val searching: Boolean = false,
    val selection: Set<Long> = emptySet(),
    val openId: Long? = null,
    val draft: Draft? = null,
    val snoozeTargets: Set<Long>? = null,
    val undo: UndoEvent? = null,
    val labelId: String? = null,
    val expanded: Set<String> = emptySet(),
    val collapsedSections: Set<DateSection> = emptySet(),
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
    val labels: List<Label> = emptyList(),
    val labelId: String? = null,
    val expanded: Set<String> = emptySet(),
    val unreadByLabel: Map<String, Int> = emptyMap(),
    val collapsedSections: Set<DateSection> = emptySet(),
)

class MailViewModel(private val repo: MailRepository) : ViewModel() {
    private val ui = MutableStateFlow(Ui())
    private var undoCounter = 0L

    val state: StateFlow<MailUiState> = combine(repo.mails, ui, repo.syncing, repo.labels) { rawMails, u, syncing, labels ->
        val mails = withKnownNames(rawMails)
        val now = System.currentTimeMillis()
        val inBox = if (u.labelId != null) mails.filter { u.labelId in it.labelIds && it.folder != Folder.TRASH } else mails.filter { it.inBox(u.box, now) }
        val shown = (if (u.query.isBlank()) inBox else mails.filter { it.folder != Folder.TRASH && it.matches(u.query) })
            .sortedByDescending { it.timeMillis }
        val showPinned = u.box == MailBox.INBOX && u.labelId == null && u.query.isBlank()
        MailUiState(
            box = u.box, query = u.query, searching = u.searching, selection = u.selection, syncing = syncing,
            labels = labels, labelId = u.labelId, expanded = u.expanded, collapsedSections = u.collapsedSections,
            unreadByLabel = labels.associate { l -> l.id to mails.count { !it.read && l.id in it.labelIds && it.folder != Folder.TRASH } },
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
    fun selectBox(box: MailBox) = ui.update { it.copy(box = box, labelId = null, query = "", searching = false, selection = emptySet()) }
    fun selectLabel(id: String) = ui.update { it.copy(labelId = id, query = "", searching = false, selection = emptySet()) }
    fun toggleSection(section: DateSection) = ui.update {
        it.copy(collapsedSections = if (section in it.collapsedSections) it.collapsedSections - section else it.collapsedSections + section)
    }
    fun toggleExpanded(fullName: String) = ui.update {
        it.copy(expanded = if (fullName in it.expanded) it.expanded - fullName else it.expanded + fullName)
    }
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
        // send() puts it in Sent at once and delivers it through Gmail (a failure leaves it in Drafts).
        repo.send(d.toMail(repo.nextId(), me, Folder.SENT))
        ui.update { it.copy(undo = UndoEvent(++undoCounter, R.string.done_sent, emptyList())) }
    }

    private fun Draft.toMail(id: Long, me: String, folder: Folder) = Mail(
        id = id, senderName = to, senderEmail = me, subject = subject, body = body,
        timeMillis = System.currentTimeMillis(), folder = folder, read = true,
        hasAttachment = attachments.isNotEmpty(), outgoing = attachments,
    )

    /** Adds files to the message being written, within Gmail's size limit; returns false if some did not fit. */
    fun attach(files: List<OutAttachment>): Boolean {
        val d = ui.value.draft ?: return true
        var total = d.attachments.sumOf { it.size.toLong() }
        val accepted = files.filter { f -> (total + f.size <= MAX_ATTACHMENT_BYTES).also { ok -> if (ok) total += f.size } }
        ui.update { it.copy(draft = d.copy(attachments = d.attachments + accepted)) }
        return accepted.size == files.size
    }

    fun detach(file: OutAttachment) = ui.update { s -> s.draft?.let { s.copy(draft = it.copy(attachments = it.attachments - file)) } ?: s }

    suspend fun attachments(id: Long): List<AttachmentInfo> = repo.attachments(id)
    suspend fun attachmentFile(id: Long, attachment: AttachmentInfo): java.io.File? = repo.attachmentFile(id, attachment)

    /** Automatic check for new mail: cheap and silent (a failure is simply retried at the next one). */
    fun poll() {
        viewModelScope.launch { repo.refresh(quick = true) }
    }

    suspend fun inlineImages(id: Long): Map<String, String> = repo.inlineImages(id)
    suspend fun full(id: Long): Mail? = repo.full(id)

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

/** Gmail refuses messages over 25 MB; files grow by a third when encoded, so the files may add up to 10 MB. */
const val MAX_ATTACHMENT_BYTES = 10L * 1024 * 1024
