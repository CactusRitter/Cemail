package com.cemail.app.data.gmail

import java.nio.charset.Charset
import java.util.Base64

/** The parts of an .eml file that matter for importing a signature. */
class ParsedEml(
    val subject: String?,
    val html: String?,
    val text: String?,
    /** Pictures that travel inside the message: Content-ID → bytes and type. */
    val images: Map<String, EmlImage>,
)

class EmlImage(val mimeType: String, val bytes: ByteArray)

/**
 * A small reader for RFC 822 / MIME messages (what an .eml file holds): headers, nested multipart parts,
 * base64 and quoted-printable bodies and charsets. It only extracts what the signature import needs.
 */
object EmlParser {
    fun parse(bytes: ByteArray): ParsedEml {
        val top = parsePart(String(bytes, Charsets.ISO_8859_1))
        var html: String? = null
        var text: String? = null
        val images = LinkedHashMap<String, EmlImage>()
        fun walk(part: Part) {
            val type = part.type
            when {
                type.startsWith("multipart/") -> part.children.forEach(::walk)
                type == "text/html" && html == null && !part.isAttachment -> html = part.decodeText()
                type == "text/plain" && text == null && !part.isAttachment -> text = part.decodeText()
                type.startsWith("image/") -> {
                    val cid = part.header("Content-ID")?.trim()?.removePrefix("<")?.removeSuffix(">")
                    if (!cid.isNullOrEmpty()) images[cid] = EmlImage(type, part.decodeBytes())
                }
            }
        }
        walk(top)
        return ParsedEml(top.header("Subject")?.let(::decodeWords), html, text, images)
    }

    private class Part(val headers: Map<String, String>, val body: String, val children: List<Part>) {
        fun header(name: String) = headers[name.lowercase()]
        val type: String get() = (header("Content-Type") ?: "text/plain").substringBefore(';').trim().lowercase()
        val isAttachment: Boolean get() = header("Content-Disposition")?.trimStart()?.startsWith("attachment", true) == true
        private val charset: Charset get() = parameter(header("Content-Type"), "charset")
            ?.let { runCatching { Charset.forName(it) }.getOrNull() } ?: Charsets.UTF_8

        fun decodeBytes(): ByteArray = when ((header("Content-Transfer-Encoding") ?: "7bit").trim().lowercase()) {
            "base64" -> Base64.getMimeDecoder().decode(body.trim())
            "quoted-printable" -> decodeQuotedPrintable(body)
            else -> body.toByteArray(Charsets.ISO_8859_1)
        }

        fun decodeText(): String = String(decodeBytes(), charset)
    }

    private fun parsePart(raw: String): Part {
        val split = Regex("\r?\n\r?\n").find(raw)
        val headerText = if (split == null) raw else raw.substring(0, split.range.first)
        val body = if (split == null) "" else raw.substring(split.range.last + 1)
        val headers = LinkedHashMap<String, String>()
        // Folded header lines continue on the next line, which starts with a space or a tab.
        headerText.replace(Regex("\r?\n[ \t]+"), " ").lines().forEach { line ->
            val colon = line.indexOf(':')
            if (colon > 0) headers.putIfAbsent(line.substring(0, colon).trim().lowercase(), line.substring(colon + 1).trim())
        }
        val contentType = headers["content-type"].orEmpty()
        val boundary = parameter(contentType, "boundary")
        val children = if (contentType.lowercase().startsWith("multipart/") && boundary != null) {
            body.split("--$boundary").drop(1)
                .filterNot { it.startsWith("--") }
                .map { it.removePrefix("\r\n").removePrefix("\n") }
                .map(::parsePart)
        } else {
            emptyList()
        }
        return Part(headers, body, children)
    }

    /** `name="value"` or `name=value` inside a header such as Content-Type. */
    private fun parameter(header: String?, name: String): String? {
        if (header == null) return null
        val match = Regex("""(?:^|;)\s*$name\s*=\s*("([^"]*)"|[^;\s]*)""", RegexOption.IGNORE_CASE).find(header) ?: return null
        return match.groupValues[2].ifEmpty { match.groupValues[1] }.trim('"')
    }

    private fun decodeQuotedPrintable(body: String): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val text = body.replace(Regex("=\r?\n"), "")
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '=' && i + 2 < text.length + 0 && text.substring(i + 1, minOf(i + 3, text.length)).matches(Regex("[0-9A-Fa-f]{2}"))) {
                out.write(text.substring(i + 1, i + 3).toInt(16))
                i += 3
            } else {
                out.write(c.code and 0xFF)
                i++
            }
        }
        return out.toByteArray()
    }

    /** RFC 2047 encoded words (`=?UTF-8?B?...?=`) in a header value. */
    fun decodeWords(value: String): String = Regex("""=\?([^?]+)\?([BbQq])\?([^?]*)\?=""").replace(value) { m ->
        val charset = runCatching { Charset.forName(m.groupValues[1]) }.getOrDefault(Charsets.UTF_8)
        val data = m.groupValues[3]
        val bytes = if (m.groupValues[2].equals("B", true)) {
            Base64.getMimeDecoder().decode(data)
        } else {
            decodeQuotedPrintable(data.replace('_', ' '))
        }
        String(bytes, charset)
    }
}
