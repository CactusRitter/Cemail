package com.cemail.app.data.mail

/** Real folders a message can live in. Starred and Snoozed are views over these. */
enum class Folder { INBOX, SENT, DRAFTS, ARCHIVE, TRASH }

private val WHITESPACE = Regex("""\s+""")

/** What the list screen can show: the folders plus the two virtual views. */
enum class MailBox { INBOX, STARRED, SNOOZED, SENT, DRAFTS, ARCHIVE, TRASH }

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
) {
    val preview: String get() = body.replace(WHITESPACE, " ").trim()
    fun isSnoozed(now: Long) = snoozedUntil != null && snoozedUntil > now
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
