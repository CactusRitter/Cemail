package com.cemail.app.data.gmail

import java.net.URLEncoder
import java.util.Base64
import java.util.UUID

/** Builds the raw RFC 822 message that Gmail's `messages.send` expects (UTF-8; multipart when it has files). */
object MimeBuilder {
    private val CRLF = "\r\n".toByteArray()

    fun build(to: String, subject: String, body: String, attachments: List<OutAttachment> = emptyList()): ByteArray {
        val encodedBody = Base64.getMimeEncoder(76, CRLF).encodeToString(body.toByteArray(Charsets.UTF_8))
        val head = StringBuilder().apply {
            append("To: ").append(to.replace(Regex("[\r\n]"), " ")).append("\r\n")
            append("Subject: ").append(encodeHeader(subject)).append("\r\n")
            append("MIME-Version: 1.0\r\n")
        }
        if (attachments.isEmpty()) {
            head.append("Content-Type: text/plain; charset=UTF-8\r\n")
            head.append("Content-Transfer-Encoding: base64\r\n\r\n")
            head.append(encodedBody)
            return head.toString().toByteArray(Charsets.US_ASCII)
        }
        val boundary = "cemail_" + UUID.randomUUID().toString().replace("-", "")
        head.append("Content-Type: multipart/mixed; boundary=\"$boundary\"\r\n\r\n")
        head.append("--$boundary\r\n")
        head.append("Content-Type: text/plain; charset=UTF-8\r\n")
        head.append("Content-Transfer-Encoding: base64\r\n\r\n")
        head.append(encodedBody).append("\r\n")
        for (file in attachments) {
            val ascii = file.name.map { if (it.code in 32..126 && it != '"' && it != '\\') it else '_' }.joinToString("")
            val star = URLEncoder.encode(file.name, "UTF-8").replace("+", "%20")
            head.append("--$boundary\r\n")
            head.append("Content-Type: ${file.mimeType.ifBlank { "application/octet-stream" }}; name=\"$ascii\"\r\n")
            head.append("Content-Disposition: attachment; filename=\"$ascii\"; filename*=UTF-8''$star\r\n")
            head.append("Content-Transfer-Encoding: base64\r\n\r\n")
            head.append(Base64.getMimeEncoder(76, CRLF).encodeToString(file.bytes)).append("\r\n")
        }
        head.append("--$boundary--\r\n")
        return head.toString().toByteArray(Charsets.US_ASCII)
    }

    /** ASCII subjects go as they are; anything else becomes an RFC 2047 encoded word. */
    fun encodeHeader(value: String): String {
        val clean = value.replace(Regex("[\r\n]"), " ")
        if (clean.all { it.code in 32..126 }) return clean
        return "=?UTF-8?B?" + Base64.getEncoder().encodeToString(clean.toByteArray(Charsets.UTF_8)) + "?="
    }
}
