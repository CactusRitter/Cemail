package com.cemail.app.data.mail

/** Real folders a message can live in. Starred and Snoozed are views over these. */
enum class Folder { INBOX, SENT, DRAFTS, ARCHIVE, TRASH, SPAM, OUTBOX }

private val WHITESPACE = Regex("""\s+""")
private val IMAGE_PLACEHOLDER = Regex("""\[(image|cid):[^\]]*\]""", RegexOption.IGNORE_CASE)

/** What the list screen can show: the folders plus the two virtual views. */
enum class MailBox { INBOX, STARRED, SNOOZED, SENT, DRAFTS, OUTBOX, ARCHIVE, SPAM, TRASH }

data class Mail(
    val id: Long,
    val senderName: String,
    val senderEmail: String,
    val subject: String,
    val body: String,
    val timeMillis: Long,
    val folder: Folder = Folder.INBOX,
    val read: Boolean = false,
    val starred: Boolean = false,
    /** Spark-style pin: stays at the top of the inbox. */
    val pinned: Boolean = false,
    /** Spark-style snooze: hidden from the inbox until this time. */
    val snoozedUntil: Long? = null,
    val hasAttachment: Boolean = false,
    /** Gmail label ids (user labels and system ones) this message has. */
    val labelIds: Set<String> = emptySet(),
    /** Original HTML of the message, or empty when it only has plain text; null if not downloaded yet. */
    val bodyHtml: String? = null,
    /** The account the message belongs to (empty for the demo store). */
    val accountId: String = "",
    /** Files attached while writing; only used to send (they are not kept in saved drafts). */
    val outgoing: List<com.cemail.app.data.gmail.OutAttachment> = emptyList(),
    /** Copy and blind-copy recipients, and the formatted (HTML) text, used only when sending. */
    val cc: String = "",
    val bcc: String = "",
    val outgoingHtml: String? = null,
    /** Gmail thread and message ids of the message being answered (replies only). */
    val threadId: String? = null,
    val inReplyTo: String? = null,
    val references: String? = null,
    /** Pictures inside the formatted text (the signature's), sent as inline parts. */
    val outgoingImages: List<com.cemail.app.data.gmail.InlineImage> = emptyList(),
    /** Outbox only: when the message is to be sent. */
    val sendAt: Long? = null,
) {
    /** One-line summary for the list: no line breaks and no "[image: ...]" placeholders of text-only versions. */
    val preview: String get() = body.replace(IMAGE_PLACEHOLDER, " ").replace(WHITESPACE, " ").trim()
    fun isSnoozed(now: Long) = snoozedUntil != null && snoozedUntil > now

    /** In the Trash or the Spam folder: left out of search, Starred and conversations. */
    val discarded: Boolean get() = folder == Folder.TRASH || folder == Folder.SPAM
}

/** Simple Gmail-like search: free words plus `from:`, `is:unread`, `is:starred`, `has:attachment`. */
fun Mail.matches(query: String): Boolean {
    var ok = true
    val words = mutableListOf<String>()
    for (token in query.trim().lowercase().split(WHITESPACE).filter { it.isNotEmpty() }) {
        when {
            token == "is:unread" -> ok = ok && !read
            token == "is:starred" -> ok = ok && starred
            token == "has:attachment" -> ok = ok && hasAttachment
            token.startsWith("from:") -> {
                val f = token.removePrefix("from:")
                ok = ok && (senderName.lowercase().contains(f) || senderEmail.lowercase().contains(f))
            }
            else -> words += token
        }
    }
    val hay = "$senderName $senderEmail $subject $body".lowercase()
    return ok && words.all { hay.contains(it) }
}

/** A Gmail label created by the user. A name like "Shopping/Amazon" means it sits inside "Shopping". */
data class Label(val id: String, val name: String)

/** A label in the folder tree. [id] is null for a parent that exists only as part of a longer name. */
data class LabelNode(val name: String, val fullName: String, val id: String?, val children: List<LabelNode>)

/** Builds the nested folder tree from Gmail's flat label names ("A/B/C"). Siblings are sorted by name. */
fun buildLabelTree(labels: List<Label>): List<LabelNode> {
    val idByName = labels.associate { it.name to it.id }
    val allPaths = labels.flatMap { label ->
        val parts = label.name.split("/")
        (1..parts.size).map { parts.take(it).joinToString("/") }
    }.toSet()
    fun childrenOf(parent: String?): List<LabelNode> = allPaths
        .filter { path ->
            if (parent == null) !path.contains("/") else path.startsWith("$parent/") && !path.removePrefix("$parent/").contains("/")
        }
        .sortedBy { it.lowercase() }
        .map { path -> LabelNode(path.substringAfterLast("/"), path, idByName[path], childrenOf(path)) }
    return childrenOf(null)
}

/**
 * Messages that arrived with a bare address get the name other messages use for that address, so a
 * friend shows up by name everywhere once any of their messages carries it.
 */
fun withKnownNames(mails: List<Mail>): List<Mail> {
    fun received(m: Mail) = m.folder != Folder.SENT && m.folder != Folder.DRAFTS
    fun nameless(m: Mail) = m.senderName.isBlank() || m.senderName.equals(m.senderEmail, ignoreCase = true)
    val names = HashMap<String, String>()
    for (m in mails) if (received(m) && !nameless(m)) names.putIfAbsent(m.senderEmail.lowercase(), m.senderName)
    if (names.isEmpty()) return mails
    return mails.map { m ->
        if (received(m) && nameless(m)) names[m.senderEmail.lowercase()]?.let { m.copy(senderName = it) } ?: m else m
    }
}
