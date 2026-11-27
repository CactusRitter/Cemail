package com.cemail.app.data.mail

import com.cemail.app.data.gmail.GmailMessage
import com.cemail.app.data.gmail.MessagePart
import com.cemail.app.data.gmail.decodeText
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.Base64
import java.util.Properties
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.Session
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeUtility

/** A calendar file of a Gmail message: the bytes when Gmail sent them along, else the id to download them. */
class CalendarPart(val data: ByteArray?, val attachmentId: String?)

/** Finds the invitations (text/calendar parts and .ics files) inside a message. */
object CalendarParts {
    const val MAX_FILES = 3
    const val MAX_BYTES = Ics.MAX_CHARS

    fun isCalendar(mimeType: String, filename: String?): Boolean {
        val mime = mimeType.substringBefore(';').trim().lowercase()
        return mime == "text/calendar" || mime == "application/ics" || mime == "text/x-vcalendar" ||
            filename?.trim()?.endsWith(".ics", ignoreCase = true) == true
    }

    /** Gmail: the parts that already carry their text win, so an invitation sent twice is not downloaded again. */
    fun find(message: GmailMessage): List<CalendarPart> {
        val found = mutableListOf<CalendarPart>()
        fun walk(part: MessagePart) {
            val body = part.body
            if (body != null && body.size <= MAX_BYTES && isCalendar(part.mimeType, part.filename)) {
                val data = body.data?.let { runCatching { Base64.getUrlDecoder().decode(it) }.getOrNull() }
                if (data != null || body.attachmentId != null) found += CalendarPart(data, body.attachmentId)
            }
            part.parts.forEach(::walk)
        }
        message.payload?.let(::walk)
        return found.filter { it.data != null }.ifEmpty { found }.take(MAX_FILES)
    }

    /** IMAP (and the raw MIME of a Microsoft meeting message). */
    fun find(message: Part): List<ByteArray> {
        val found = mutableListOf<ByteArray>()
        fun walk(part: Part, depth: Int) {
            if (depth > 8 || found.size >= MAX_FILES) return
            if (part.isMimeType("multipart/*")) {
                val multipart = part.content as Multipart
                for (i in 0 until multipart.count) walk(multipart.getBodyPart(i), depth + 1)
                return
            }
            val name = part.fileName?.let { runCatching { MimeUtility.decodeText(it) }.getOrDefault(it) }
            if (isCalendar(part.contentType.orEmpty(), name)) {
                val bytes = part.inputStream.use { readLimited(it) } ?: return
                if (found.none { it.contentEquals(bytes) }) found += bytes
            }
        }
        try {
            walk(message, 0)
        } catch (_: Exception) {
            // a broken part: keep what was read
        }
        return found
    }

    fun find(raw: ByteArray): List<ByteArray> = try {
        find(MimeMessage(Session.getInstance(Properties()), ByteArrayInputStream(raw)))
    } catch (_: Exception) {
        emptyList()
    }

    /** null if it is longer than the limit. */
    private fun readLimited(input: InputStream): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) return out.toByteArray()
            out.write(buffer, 0, n)
            if (out.size() > MAX_BYTES) return null
        }
    }

    fun text(bytes: ByteArray): String {
        val plain = String(bytes, Charsets.UTF_8)
        if ('\uFFFD' !in plain) return plain
        // Some programs cut long lines in the middle of an accented letter: join them before reading the letters.
        val joined = String(bytes, Charsets.ISO_8859_1).replace(Regex("\r?\n[ \t]"), "").toByteArray(Charsets.ISO_8859_1)
        return decodeText(joined, Charsets.ISO_8859_1)
    }

    /**
     * The calendar files of the message [remoteId], asked to the server with [fetch] the first time and then read
     * from [dir] (also when there are none, so opening a message again costs nothing).
     */
    suspend fun cached(dir: File, remoteId: String, fetch: suspend () -> List<ByteArray>): List<String> {
        val key = MessageDigest.getInstance("SHA-256").digest(remoteId.toByteArray()).take(16)
            .joinToString("") { "%02x".format(it) }
        val folder = File(dir, "ics/$key")
        val index = File(folder, "count")
        index.takeIf { it.exists() }?.readText()?.toIntOrNull()?.let { count ->
            val files = (0 until count).map { File(folder, "$it.ics") }
            if (files.all { it.exists() }) return files.map { text(it.readBytes()) }
        }
        val got = fetch().filter { it.size <= MAX_BYTES }.take(MAX_FILES)
        folder.mkdirs()
        got.forEachIndexed { n, bytes -> File(folder, "$n.ics").writeBytes(bytes) }
        index.writeText(got.size.toString())
        return got.map(::text)
    }
}
