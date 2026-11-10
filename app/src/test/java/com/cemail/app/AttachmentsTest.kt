package com.cemail.app

import com.cemail.app.data.gmail.AttachmentFinder
import com.cemail.app.data.gmail.GmailMessage
import com.cemail.app.data.gmail.Header
import com.cemail.app.data.gmail.MessagePart
import com.cemail.app.data.gmail.MimeBuilder
import com.cemail.app.data.gmail.OutAttachment
import com.cemail.app.data.gmail.PartBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentsTest {
    private fun part(name: String, mime: String, attachmentId: String?, vararg headers: Header) =
        MessagePart(mimeType = mime, filename = name, headers = headers.toList(), body = PartBody(size = 1234, attachmentId = attachmentId))

    @Test
    fun listsFilesButNotInlinePicturesOrTextParts() {
        val message = GmailMessage(
            id = "1",
            payload = MessagePart(
                mimeType = "multipart/mixed",
                parts = listOf(
                    MessagePart(mimeType = "text/plain", body = PartBody(size = 5, data = "aGVsbG8")),
                    part("factura.pdf", "application/pdf", "att1"),
                    part("logo.png", "image/png", "att2", Header("Content-ID", "<logo>")),
                    part("foto.jpg", "image/jpeg", "att3"),
                    MessagePart(mimeType = "multipart/related", parts = listOf(part("datos.xlsx", "application/vnd.ms-excel", "att4"))),
                ),
            ),
        )
        val names = AttachmentFinder.find(message).map { it.filename }
        assertEquals(listOf("factura.pdf", "foto.jpg", "datos.xlsx"), names)
    }

    @Test
    fun fileNamesAreMadeSafe() {
        assertEquals("a_b_c.txt", AttachmentFinder.safeFileName("a/b\\c.txt"))
        assertEquals("attachment", AttachmentFinder.safeFileName("..."))
        assertEquals("informe año.pdf", AttachmentFinder.safeFileName("informe año.pdf"))
    }

    @Test
    fun messageWithoutFilesStaysPlainText() {
        val raw = String(MimeBuilder.build("a@b.com", "Hola", "texto"), Charsets.US_ASCII)
        assertTrue(raw.contains("Content-Type: text/plain; charset=UTF-8"))
        assertTrue(!raw.contains("multipart"))
    }

    @Test
    fun messageWithFilesIsMultipart() {
        val file = OutAttachment("informe año.pdf", "application/pdf", ByteArray(100) { it.toByte() })
        val raw = String(MimeBuilder.build("a@b.com", "Hola", "texto", listOf(file)), Charsets.US_ASCII)
        assertTrue(raw.contains("Content-Type: multipart/mixed; boundary=\""))
        val boundary = Regex("boundary=\"([^\"]+)\"").find(raw)!!.groupValues[1]
        assertEquals(3, Regex("--" + Regex.escape(boundary)).findAll(raw).count()) // text, file, closing
        assertTrue(raw.contains("filename=\"informe a_o.pdf\"; filename*=UTF-8''informe%20a%C3%B1o.pdf"))
        assertTrue(raw.trimEnd().endsWith("--$boundary--"))
    }
}
