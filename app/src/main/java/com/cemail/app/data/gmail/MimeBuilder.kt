package com.cemail.app.data.gmail

import java.util.Base64

/** Builds the raw RFC 822 message that Gmail's `messages.send` expects (plain text, UTF-8). */
object MimeBuilder {
    fun build(to: String, subject: String, body: String): ByteArray {
        val encodedBody = Base64.getMimeEncoder(76, "\r\n".toByteArray()).encodeToString(body.toByteArray(Charsets.UTF_8))
        val text = buildString {
            append("To: ").append(to.replace(Regex("[\r\n]"), " ")).append("\r\n")
            append("Subject: ").append(encodeHeader(subject)).append("\r\n")
            append("MIME-Version: 1.0\r\n")
            append("Content-Type: text/plain; charset=UTF-8\r\n")
            append("Content-Transfer-Encoding: base64\r\n")
            append("\r\n")
            append(encodedBody)
        }
        return text.toByteArray(Charsets.US_ASCII)
    }

    /** ASCII subjects go as they are; anything else becomes an RFC 2047 encoded word. */
    fun encodeHeader(value: String): String {
        val clean = value.replace(Regex("[\r\n]"), " ")
        if (clean.all { it.code in 32..126 }) return clean
        return "=?UTF-8?B?" + Base64.getEncoder().encodeToString(clean.toByteArray(Charsets.UTF_8)) + "?="
    }
}
