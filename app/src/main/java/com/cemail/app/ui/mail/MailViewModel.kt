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
import com.cemail.app.data.gmail.ReplyInfoParser
import com.cemail.app.data.gmail.SignatureHtml
import com.cemail.app.data.mail.Mail
import com.cemail.app.data.mail.MailBox
import com.cemail.app.data.mail.MailRepository
import com.cemail.app.data.mail.matches
import com.cemail.app.data.mail.withKnownNames
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

/** Something the user just did, offered to be undone for a few seconds (Spark-style). */
data class UndoEvent(
    val id: Long,
    @StringRes val message: Int,
    val previous: List<Mail>,
    /** "Undo send": the Outbox message to take back, and the draft to give back to the user. */
    val cancelSend: Long? = null,
    val restore: Draft? = null,
    /** How long the bar stays (an undoable send stays as long as its delay). */
    val durationMillis: Long = 5_000,
)

/** A message being written. */
data class Draft(
    val to: String = "",
    val subject: String = "",
    val body: String = "",
    val cc: String = "",
    val bcc: String = "",
    /** The text with its formatting, as HTML (null while nothing is formatted or in plain replies). */
    val html: String? = null,
    val attachments: List<OutAttachment> = emptyList(),
    /** Set while answering a message: which one, and the ids that keep the reply in its conversation. */
    val replyToId: Long? = null,
    /** true once the account's signature was put into the text (it is added only once). */
    val signed: Boolean = false,
    /** The account's HTML signature, added when the message is sent (null = none, or removed for this message). */
    val signatureHtml: String? = null,
    /** The account (id) the message is sent from; null until the compose screen picks the default one. */
    val fromAccountId: String? = null,
    val threadId: String? = null,
    val inReplyTo: String? = null,
    val references: String? = null,
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
    /** The other messages of the open message's conversation, newest first. */
    val thread: List<Mail> = emptyList(),
)

class MailViewModel(
    private val repo: MailRepository,
    /** The store of another account (by id), so a message can be sent from an account other than the one on screen. */
    private val outgoing: (String) -> MailRepository? = { null },
) : ViewModel() {
    private val ui = MutableStateFlow(Ui())

    /** Where a message written from [accountId] is saved and sent: that account's own store, or this one. */
    private fun sender(accountId: String?): MailRepository = accountId?.let(outgoing) ?: repo
    private var undoCounter = 0L

    /** Ids found by the full-text search for the current query (empty while there is no query or it uses operators). */
    private val searchHits = MutableStateFlow<Set<Long>>(emptySet())

    val state: StateFlow<MailUiState> = combine(repo.mails, ui, repo.syncing, repo.labels, searchHits) { rawMails, u, syncing, labels, hits ->
        val mails = withKnownNames(rawMails)
        val now = System.currentTimeMillis()
        val inBox = if (u.labelId != null) mails.filter { u.labelId in it.labelIds && !it.discarded } else mails.filter { it.inBox(u.box, now) }
        val shown = (if (u.query.isBlank()) inBox else mails.filter { !it.discarded && (it.matches(u.query) || it.id in hits) })
            .sortedByDescending { it.timeMillis }
        val showPinned = u.box == MailBox.INBOX && u.labelId == null && u.query.isBlank()
        val open = u.openId?.let { id -> mails.firstOrNull { it.id == id } }
        val conversation = open?.threadId?.let { thread ->
            mails.filter { it.threadId == thread && it.accountId == open.accountId && it.id != open.id && !it.discarded }
                .sortedByDescending { it.timeMillis }
        }.orEmpty()
        MailUiState(
            thread = conversation,
            box = u.box, query = u.query, searching = u.searching, selection = u.selection, syncing = syncing,
            labels = labels, labelId = u.labelId, expanded = u.expanded, collapsedSections = u.collapsedSections,
            unreadByLabel = labels.associate { l -> l.id to mails.count { !it.read && l.id in it.labelIds && !it.discarded } },
            open = open,
            draft = u.draft, snoozeTargets = u.snoozeTargets, undo = u.undo,
            pinned = if (showPinned) shown.filter { it.pinned } else emptyList(),
            others = if (showPinned) shown.filter { !it.pinned } else shown,
            // The Trash shows no count (what is in it is not pending).
            unreadByBox = MailBox.entries.associateWith { b -> if (b == MailBox.TRASH) 0 else mails.count { !it.read && it.inBox(b, now) } },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MailUiState())

    private fun Mail.inBox(box: MailBox, now: Long) = when (box) {
        MailBox.INBOX -> folder == Folder.INBOX && !isSnoozed(now)
        MailBox.STARRED -> starred && !discarded
        MailBox.SNOOZED -> folder == Folder.INBOX && isSnoozed(now)
        MailBox.SENT -> folder == Folder.SENT
        MailBox.DRAFTS -> folder == Folder.DRAFTS
        MailBox.ARCHIVE -> folder == Folder.ARCHIVE
        MailBox.OUTBOX -> folder == Folder.OUTBOX
        MailBox.SPAM -> folder == Folder.SPAM
        MailBox.TRASH -> folder == Folder.TRASH
    }

    init {
        refresh()
        // The full-text search follows what is typed, a moment after the last key.
        viewModelScope.launch {
            ui.map { it.query }.distinctUntilChanged().debounce(200).collect { query ->
                searchHits.value = if (query.isBlank()) emptySet() else repo.searchIds(query)
            }
        }
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

    /** How long the "Undo" bar stays after an action; the screen keeps it in line with Settings. */
    @Volatile var actionUndoMillis: Long = 5_000

    // --- actions (each one can be undone) ---
    private fun act(ids: Set<Long>, @StringRes message: Int?, closeOpen: Boolean = false, transform: (Mail) -> Mail) {
        if (ids.isEmpty()) return
        val before = repo.snapshot(ids)
        // An action that changes nothing (a swipe that fires twice, say) must not replace the "Undo" of the one before it.
        if (before.isEmpty() || before.all { transform(it) == it }) return
        repo.update(ids, transform)
        ui.update {
            it.copy(
                selection = it.selection - ids,
                undo = message?.let { m -> UndoEvent(++undoCounter, m, before, durationMillis = actionUndoMillis) } ?: it.undo,
                openId = if (closeOpen && it.openId in ids) null else it.openId,
            )
        }
    }

    fun archive(ids: Set<Long>) = act(ids, R.string.done_archived, true) { it.copy(folder = Folder.ARCHIVE, snoozedUntil = null) }
    fun trash(ids: Set<Long>) = act(ids, R.string.done_trashed, true) { it.copy(folder = Folder.TRASH, snoozedUntil = null) }
    /** Deletes everything in the Trash or Spam for good (the screen asked first); tells how it went. */
    fun emptyFolder(box: MailBox) {
        val folder = when (box) {
            MailBox.TRASH -> Folder.TRASH
            MailBox.SPAM -> Folder.SPAM
            else -> return
        }
        viewModelScope.launch {
            val ok = repo.emptyFolder(folder)
            ui.update { it.copy(undo = UndoEvent(++undoCounter, if (ok) R.string.done_emptied else R.string.empty_failed, emptyList())) }
        }
    }
    fun spam(ids: Set<Long>) = act(ids, R.string.done_spammed, true) { it.copy(folder = Folder.SPAM, snoozedUntil = null) }
    fun moveToInbox(ids: Set<Long>) = act(ids, R.string.done_marked, true) { it.copy(folder = Folder.INBOX, snoozedUntil = null) }
    /** [withUndo]: the swipe gesture offers to undo it; tapping the star does not. */
    fun toggleStar(ids: Set<Long>, withUndo: Boolean = false) {
        val turnOn = repo.snapshot(ids).any { !it.starred }
        act(ids, if (withUndo) R.string.done_marked else null) { it.copy(starred = turnOn) }
    }
    fun togglePin(ids: Set<Long>) {
        val turnOn = repo.snapshot(ids).any { !it.pinned }
        act(ids, if (turnOn) R.string.done_pinned else R.string.done_marked) { it.copy(pinned = turnOn) }
    }
    fun markRead(ids: Set<Long>, read: Boolean) = act(ids, R.string.done_marked, true) { it.copy(read = read) }
    /** Puts a label of the user on the messages, or takes it off. */
    fun setLabel(ids: Set<Long>, labelId: String, on: Boolean) =
        act(ids, null) { it.copy(labelIds = if (on) it.labelIds + labelId else it.labelIds - labelId) }
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
        com.cemail.app.AppLog.i("undo", "restoring ${u.previous.size} message(s), folder before: ${u.previous.map { it.folder }}")
        if (u.previous.isNotEmpty()) repo.restore(u.previous)
        u.cancelSend?.let { id ->
            sender(u.restore?.fromAccountId).cancelScheduled(id)
            u.restore?.let { draft -> ui.update { it.copy(draft = draft) } }
        }
        ui.update { it.copy(undo = null) }
    }
    fun dismissUndo(id: Long) = ui.update { if (it.undo?.id == id) it.copy(undo = null) else it }

    // --- compose ---
    fun newDraft(draft: Draft = Draft()) = ui.update { it.copy(draft = draft) }

    /**
     * Opens the compose screen at once with [base] (quoted text, subject), then, if the message's headers can be
     * read, fills in the real recipients and the ids that keep the reply in the same conversation.
     * [all] = "reply all"; a forward ([forward]) starts a new conversation with nobody filled in.
     */
    fun startReply(base: Draft, messageId: Long, all: Boolean, forward: Boolean) {
        ui.update { it.copy(draft = base.copy(replyToId = messageId)) }
        if (forward) return
        viewModelScope.launch {
            val (info, me) = repo.replyInfo(messageId) ?: return@launch
            val (to, cc) = ReplyInfoParser.recipients(info, me, all)
            val chain = listOfNotNull(info.references, info.messageId).joinToString(" ").ifBlank { null }
            ui.update { s ->
                val d = s.draft?.takeIf { it.replyToId == messageId } ?: return@update s
                s.copy(
                    draft = d.copy(
                        to = if (to.isEmpty()) d.to else committedRecipients(to.joinToString(", ")),
                        cc = if (cc.isEmpty()) d.cc else committedRecipients(cc.joinToString(", ")),
                        threadId = info.threadId, inReplyTo = info.messageId, references = chain,
                    ),
                )
            }
        }
    }
    fun discardDraft() = ui.update { it.copy(draft = null) }
    fun updateDraft(draft: Draft) = ui.update { it.copy(draft = draft) }

    /** Changes the draft being written from its latest state (several fields can be edited at the same time). */
    fun editDraft(transform: (Draft) -> Draft) = ui.update { s -> s.draft?.let { s.copy(draft = transform(it)) } ?: s }

    /** Re-opens a saved draft for editing (the stored copy is removed; it is saved again on close). */
    fun editDraft(mail: Mail) {
        // A scheduled message is taken out of the Outbox; its attachments come back into the editor.
        val attachments = if (mail.folder == Folder.OUTBOX) {
            com.cemail.app.data.mail.Outbox.load(mail.id).first.also { repo.cancelScheduled(mail.id) }
        } else {
            repo.remove(mail.id)
            emptyList()
        }
        ui.update {
            it.copy(
                draft = Draft(
                    to = mail.senderName, subject = mail.subject, body = mail.body,
                    cc = mail.cc, bcc = mail.bcc, html = mail.bodyHtml?.takeIf { h -> h.isNotBlank() }, attachments = attachments,
                    signed = true, fromAccountId = mail.accountId.takeIf { id -> id.isNotBlank() }, threadId = mail.threadId, inReplyTo = mail.inReplyTo, references = mail.references,
                ),
            )
        }
    }

    /** Leaves the compose screen: an empty draft is dropped, anything else is kept in Drafts. */
    fun closeCompose(me: String) {
        val d = ui.value.draft ?: return
        ui.update { it.copy(draft = null) }
        if (d.to.isNotBlank() || d.subject.isNotBlank() || d.body.isNotBlank()) {
            val store = sender(d.fromAccountId)
            store.add(d.toMail(store.nextId(), me, Folder.DRAFTS))
            ui.update { it.copy(undo = UndoEvent(++undoCounter, R.string.done_draft_saved, emptyList())) }
        }
    }

    /**
     * Sends the message. With an undo delay ([undoMillis]) or a chosen time ([at]) it waits in the Outbox first: it is
     * a saved row, so it goes out even if the app closes meanwhile, and Undo takes it back into the editor.
     */
    fun send(me: String, undoMillis: Long = 0, at: Long? = null) {
        val d = ui.value.draft ?: return
        ui.update { it.copy(draft = null) }
        val store = sender(d.fromAccountId)
        val mail = d.toMail(store.nextId(), me, Folder.SENT)
        val sendAt = at ?: undoMillis.takeIf { it > 0 }?.let { System.currentTimeMillis() + it }
        if (sendAt == null) {
            // send() puts it in Sent at once and delivers it (a failure leaves it in Drafts).
            store.send(mail)
            ui.update { it.copy(undo = UndoEvent(++undoCounter, R.string.done_sent, emptyList())) }
            return
        }
        viewModelScope.launch {
            val id = store.schedule(mail, sendAt)
            ui.update {
                it.copy(
                    undo = UndoEvent(
                        ++undoCounter, if (at != null) R.string.done_scheduled else R.string.done_sent, emptyList(),
                        cancelSend = id, restore = d, durationMillis = if (at != null) 8_000 else maxOf(5_000, undoMillis),
                    ),
                )
            }
        }
    }

    private fun Draft.toMail(id: Long, me: String, folder: Folder): Mail {
        // An HTML signature goes after the text, in both halves of the message (its pictures become inline parts).
        var finalBody = body
        var finalHtml = html
        var images = emptyList<com.cemail.app.data.gmail.InlineImage>()
        signatureHtml?.let { signature ->
            val (merged, inline) = SignatureHtml.merge(html ?: SignatureHtml.plainToHtml(body), signature)
            finalHtml = merged
            images = inline
            finalBody = body.trimEnd() + "\n\n-- \n" + SignatureHtml.toText(signature)
        }
        return Mail(
            id = id, accountId = fromAccountId.orEmpty(), senderName = cleanRecipients(to).joinToString(", "), senderEmail = me, subject = subject, body = finalBody,
            timeMillis = System.currentTimeMillis(), folder = folder, read = true,
            hasAttachment = attachments.isNotEmpty(), outgoing = attachments,
            cc = cleanRecipients(cc).joinToString(", "), bcc = cleanRecipients(bcc).joinToString(", "),
            outgoingHtml = finalHtml, outgoingImages = images,
            threadId = threadId, inReplyTo = inReplyTo, references = references,
        )
    }

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
    suspend fun unsubscribeInfo(id: Long) = repo.unsubscribeInfo(id)
    suspend fun isMuted(id: Long) = repo.isMuted(id)

    /** Mutes or unmutes the conversation of a message; muting also closes it and says so. */
    fun setMuted(id: Long, muted: Boolean) {
        repo.setMuted(id, muted)
        if (muted) ui.update { it.copy(openId = null, undo = UndoEvent(++undoCounter, R.string.done_muted, emptyList())) }
    }
    suspend fun userLabels(accountId: String) = repo.userLabels(accountId)

    /** The people who wrote to the user, to offer as recipients while typing. */
    suspend fun senders(): List<com.cemail.app.data.mail.Suggestion> =
        com.cemail.app.data.mail.sendersOf(repo.mails.first())
    suspend fun emlFile(id: Long): java.io.File? = repo.emlFile(id)

    class Factory(
        private val repo: MailRepository,
        private val outgoing: (String) -> MailRepository? = { null },
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MailViewModel(repo, outgoing) as T
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
