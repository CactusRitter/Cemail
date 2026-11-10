package com.cemail.app.data.gmail

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.nio.charset.Charset
import java.util.Base64

/** A Gmail message boiled down to what Cemail shows. */
data class ParsedMessage(
    val subject: String,
    val fromName: String,
    val fromEmail: String,
    val to: String,
    val body: String,
    val timeMillis: Long,
    val hasAttachment: Boolean,
    val labels: Set<String>,
    /** The HTML part as sent by the author (unsanitized: run it through HtmlSanitizer before showing it). */
    val html: String = "",
    /** Gmail's conversation id; messages of one conversation share it. */
    val threadId: String? = null,
)

/** Turns Gmail API messages into [ParsedMessage]. Pure Kotlin, covered by unit tests. */
object GmailParser {
    private val NEWLINES = Regex("""\n{3,}""")
    private val ENCODED_WORD = Regex("""=\?([^?]+)\?([bBqQ])\?([^?]*)\?=""")
    /** Whitespace between two encoded words is not part of the text (RFC 2047). */
    private val ADJACENT_WORDS = Regex("""\?=\s+=\?""")
    private val ADDRESS =Regex("""^\s*"?([^"<]*?)"?\s*<([^>]+)>\s*$""")

    fun parse(message: GmailMessage): ParsedMessage {
        val payload = message.payload
        val headers = payload?.headers.orEmpty()
        fun header(name: String) = headers.firstOrNull { it.name.equals(name, ignoreCase = true) }?.value.orEmpty()
        val (name, email) = parseAddress(decodeWords(header("From")))
        val plain = StringBuilder()
        val html = StringBuilder()
        var attachment = false
        fun walk(part: MessagePart) {
            if (part.filename.isNotEmpty() && part.body?.attachmentId != null) attachment = true
            val data = part.body?.data
            if (data != null && part.filename.isEmpty()) {
                when {
                    part.mimeType.equals("text/plain", true) -> plain.append(decodeBody(data, part))
                    part.mimeType.equals("text/html", true) -> html.append(decodeBody(data, part))
                }
            }
            part.parts.forEach(::walk)
        }
        if (payload != null) walk(payload)
        val body = when {
            plain.isNotBlank() -> plain.toString().trim()
            html.isNotBlank() -> htmlToText(html.toString())
            else -> message.snippet
        }
        return ParsedMessage(
            subject = decodeWords(header("Subject")),
            fromName = name.ifBlank { email },
            fromEmail = email,
            to = decodeWords(header("To")),
            body = body,
            timeMillis = message.internalDate?.toLongOrNull() ?: 0L,
            hasAttachment = attachment,
            labels = message.labelIds.toSet(),
            html = html.toString(),
            threadId = message.threadId,
        )
    }

    /** "Ana López <ana@x.com>" → ("Ana López", "ana@x.com"); a bare address gives an empty name. */
    fun parseAddress(value: String): Pair<String, String> {
        val match = ADDRESS.matchEntire(value)
        return if (match != null) match.groupValues[1].trim() to match.groupValues[2].trim() else "" to value.trim()
    }

    /** Decodes RFC 2047 encoded words ("=?UTF-8?B?...?=") that Gmail leaves in headers. */
    fun decodeWords(value: String): String = ENCODED_WORD.replace(value.replace(ADJACENT_WORDS, "?==?")) { m ->
        try {
            val charset = Charset.forName(m.groupValues[1])
            val text = m.groupValues[3]
            val bytes = if (m.groupValues[2].equals("b", true)) {
                Base64.getMimeDecoder().decode(text)
            } else {
                decodeQuoted(text)
            }
            String(bytes, charset)
        } catch (_: Exception) {
            m.value
        }
    }

    private fun decodeQuoted(text: String): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '_') {
                out.write(' '.code)
            } else if (c == '=' && i + 2 < text.length) {
                out.write(text.substring(i + 1, i + 3).toInt(16))
                i += 2
            } else {
                out.write(c.code)
            }
            i++
        }
        return out.toByteArray()
    }

    private fun decodeBody(data: String, part: MessagePart): String {
        val charsetName = part.headers
            .firstOrNull { it.name.equals("Content-Type", true) }?.value
            ?.let { Regex("""charset="?([\w.-]+)"?""", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1) }
        val charset = try {
            if (charsetName != null) Charset.forName(charsetName) else Charsets.UTF_8
        } catch (_: Exception) {
            Charsets.UTF_8
        }
        return decodeText(Base64.getUrlDecoder().decode(data), charset)
    }

    /** Readable plain text from HTML: block elements and <br> become line breaks. */
    fun htmlToText(html: String): String {
        val doc = Jsoup.parse(html)
        doc.select("script,style,head").remove()
        val sb = StringBuilder()
        fun walk(node: Node) {
            when (node) {
                is TextNode -> sb.append(node.text())
                is Element -> {
                    if (node.tagName() == "br") sb.append('\n')
                    val block = node.isBlock
                    if (block && sb.isNotEmpty() && !sb.endsWith("\n")) sb.append('\n')
                    node.childNodes().forEach(::walk)
                    if (block && !sb.endsWith("\n")) sb.append('\n')
                }
            }
        }
        doc.body().let(::walk)
        return sb.toString().replace(NEWLINES, "\n\n").trim()
    }
}
