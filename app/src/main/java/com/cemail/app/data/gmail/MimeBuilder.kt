package com.cemail.app.data.gmail

import java.net.URLEncoder
import java.util.Base64
import java.util.UUID

/**
 * Builds the raw RFC 822 message that Gmail's `messages.send` expects (UTF-8). The text goes as plain text, or as
 * plain text + HTML ("alternative") when it was written with formatting, and everything is wrapped in "mixed"
 * when there are attached files.
 */
object MimeBuilder {
    private val CRLF = "\r\n".toByteArray()

    fun build(
        to: String,
        subject: String,
        body: String,
        attachments: List<OutAttachment> = emptyList(),
        cc: String = "",
        bcc: String = "",
        html: String? = null,
        inReplyTo: String? = null,
        references: String? = null,
    ): ByteArray {
        val head = StringBuilder().apply {
            append("To: ").append(oneLine(to)).append("\r\n")
            if (cc.isNotBlank()) append("Cc: ").append(oneLine(cc)).append("\r\n")
            // Gmail delivers to the Bcc addresses and removes the header from the copies it sends.
            if (bcc.isNotBlank()) append("Bcc: ").append(oneLine(bcc)).append("\r\n")
            append("Subject: ").append(encodeHeader(subject)).append("\r\n")
            // A reply points at the message it answers, so the conversation stays together in every mail app.
            if (!inReplyTo.isNullOrBlank()) append("In-Reply-To: ").append(oneLine(inReplyTo)).append("\r\n")
            if (!references.isNullOrBlank()) append("References: ").append(oneLine(references)).append("\r\n")
            append("MIME-Version: 1.0\r\n")
        }
        val text = textPart(body)
        val content = if (html.isNullOrBlank()) {
            text
        } else {
            val alt = boundary()
            "Content-Type: multipart/alternative; boundary=\"$alt\"\r\n\r\n" +
                "--$alt\r\n$text\r\n--$alt\r\n${htmlPart(html)}\r\n--$alt--"
        }
        if (attachments.isEmpty()) return (head.toString() + content).toByteArray(Charsets.US_ASCII)

        val mixed = boundary()
        val message = StringBuilder(head)
        message.append("Content-Type: multipart/mixed; boundary=\"$mixed\"\r\n\r\n")
        message.append("--$mixed\r\n").append(content).append("\r\n")
        for (file in attachments) {
            val ascii = file.name.map { if (it.code in 32..126 && it != '"' && it != '\\') it else '_' }.joinToString("")
            val star = URLEncoder.encode(file.name, "UTF-8").replace("+", "%20")
            message.append("--$mixed\r\n")
            message.append("Content-Type: ${file.mimeType.ifBlank { "application/octet-stream" }}; name=\"$ascii\"\r\n")
            message.append("Content-Disposition: attachment; filename=\"$ascii\"; filename*=UTF-8''$star\r\n")
            message.append("Content-Transfer-Encoding: base64\r\n\r\n")
            message.append(Base64.getMimeEncoder(76, CRLF).encodeToString(file.bytes)).append("\r\n")
        }
        message.append("--$mixed--\r\n")
        return message.toString().toByteArray(Charsets.US_ASCII)
    }

    private fun boundary() = "cemail_" + UUID.randomUUID().toString().replace("-", "")

    private fun oneLine(value: String) = value.replace(Regex("[\r\n]"), " ")

    private fun textPart(body: String) =
        "Content-Type: text/plain; charset=UTF-8\r\nContent-Transfer-Encoding: base64\r\n\r\n" +
            Base64.getMimeEncoder(76, CRLF).encodeToString(body.toByteArray(Charsets.UTF_8))

    private fun htmlPart(html: String) =
        "Content-Type: text/html; charset=UTF-8\r\nContent-Transfer-Encoding: base64\r\n\r\n" +
            Base64.getMimeEncoder(76, CRLF).encodeToString(html.toByteArray(Charsets.UTF_8))

    /** ASCII subjects go as they are; anything else becomes an RFC 2047 encoded word. */
    fun encodeHeader(value: String): String {
        val clean = value.replace(Regex("[\r\n]"), " ")
        if (clean.all { it.code in 32..126 }) return clean
        return "=?UTF-8?B?" + Base64.getEncoder().encodeToString(clean.toByteArray(Charsets.UTF_8)) + "?="
    }
}
