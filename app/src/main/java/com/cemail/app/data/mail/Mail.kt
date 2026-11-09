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
