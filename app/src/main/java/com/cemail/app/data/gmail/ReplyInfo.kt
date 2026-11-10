package com.cemail.app.data.gmail

/** What a reply needs to know about the message it answers (read from its headers when replying). */
class ReplyInfo(
    val threadId: String?,
    /** The Message-ID header: the reply points at it so mail apps keep the conversation together. */
    val messageId: String?,
    val references: String?,
    val replyTo: List<String>,
    val from: List<String>,
    val to: List<String>,
    val cc: List<String>,
)

object ReplyInfoParser {
    val HEADERS = listOf("Message-ID", "References", "Reply-To", "From", "To", "Cc")

    fun parse(message: GmailMessage): ReplyInfo {
        val headers = message.payload?.headers.orEmpty()
        fun header(name: String) = headers.firstOrNull { it.name.equals(name, ignoreCase = true) }?.value
        return ReplyInfo(
            threadId = message.threadId,
            messageId = header("Message-ID")?.trim()?.takeIf { it.isNotEmpty() },
            references = header("References")?.trim()?.takeIf { it.isNotEmpty() },
            replyTo = addresses(header("Reply-To")),
            from = addresses(header("From")),
            to = addresses(header("To")),
            cc = addresses(header("Cc")),
        )
    }

    private val ADDRESS = Regex("""[A-Za-z0-9._%+'-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}""")

    /** The e-mail addresses in an address header (names are dropped), without repeats. */
    fun addresses(header: String?): List<String> =
        ADDRESS.findAll(header.orEmpty()).map { it.value }.distinctBy { it.lowercase() }.toList()

    /**
     * Who a reply goes to and who is copied. A plain reply goes to Reply-To (or the sender); "reply all" also
     * copies everyone else on the message except [me]. Answering one's own message goes to its recipients.
     */
    fun recipients(info: ReplyInfo, me: String?, all: Boolean): Pair<List<String>, List<String>> {
        fun isMe(a: String) = me != null && a.equals(me, ignoreCase = true)
        val sender = info.replyTo.ifEmpty { info.from }
        val ownMessage = info.from.any(::isMe)
        val to = (if (ownMessage) info.to else sender).filterNot { isMe(it) && !ownMessage }
        val cc = if (!all) {
            emptyList()
        } else {
            (if (ownMessage) info.cc else info.to + info.cc)
                .filterNot { isMe(it) }
                .filter { a -> to.none { it.equals(a, ignoreCase = true) } }
                .distinctBy { it.lowercase() }
        }
        return to to cc
    }
}
