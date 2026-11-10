package com.cemail.app

import com.cemail.app.data.gmail.GmailMessage
import com.cemail.app.data.gmail.GmailParser
import com.cemail.app.data.gmail.Header
import com.cemail.app.data.gmail.HtmlSanitizer
import com.cemail.app.data.gmail.InlineImages
import com.cemail.app.data.gmail.MessagePart
import com.cemail.app.data.gmail.PartBody
import com.cemail.app.data.gmail.decodeText
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InlineAndEncodingTest {
    private fun b64(bytes: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    @Test fun utf8TextWinsOverAWrongDeclaredCharset() {
        val text = "Hallo César, für München"
        assertEquals(text, decodeText(text.toByteArray(Charsets.UTF_8), Charsets.ISO_8859_1))
    }

    @Test fun realLatin1TextStillUsesTheDeclaredCharset() {
        val text = "für München"
        assertEquals(text, decodeText(text.toByteArray(Charsets.ISO_8859_1), Charsets.ISO_8859_1))
    }

    @Test fun parserDecodesUtf8BodyDeclaredAsLatin1() {
        val part = MessagePart(
            mimeType = "text/html",
            headers = listOf(Header("Content-Type", "text/html; charset=\"iso-8859-1\"")),
            body = PartBody(data = b64("<p>benötigt</p>".toByteArray(Charsets.UTF_8))),
        )
        val parsed = GmailParser.parse(GmailMessage(id = "1", payload = part))
        assertTrue(parsed.html.contains("benötigt"))
    }

    @Test fun findsInlinePicturesByContentId() {
        val message = GmailMessage(
            id = "1",
            payload = MessagePart(
                mimeType = "multipart/related",
                parts = listOf(
                    MessagePart(mimeType = "text/html", body = PartBody(data = b64("<img src=\"cid:logo@x\">".toByteArray()))),
                    MessagePart(
                        mimeType = "image/png", filename = "logo.png",
                        headers = listOf(Header("Content-ID", "<logo@x>")), body = PartBody(attachmentId = "att"),
                    ),
                    MessagePart(mimeType = "application/pdf", filename = "a.pdf", headers = listOf(Header("Content-ID", "<doc>")), body = PartBody(attachmentId = "b")),
                ),
            ),
        )
        val parts = InlineImages.find(message)
        assertEquals(1, parts.size)
        assertEquals("logo@x", parts[0].cid)
        assertEquals("att", parts[0].attachmentId)
        assertNull(parts[0].data)
    }

    @Test fun sanitizerPutsInlinePicturesInAndDropsUnknownOnes() {
        val out = HtmlSanitizer.clean(
            """<img src="cid:logo@x" alt="Logo"><img src="cid:missing" alt="M">""",
            mapOf("logo@x" to "data:image/png;base64,AAAA"),
        )
        assertTrue(out.contains("data:image/png;base64,AAAA"))
        assertFalse(out.contains("cid:"))
        assertTrue(out.contains("M") && !out.contains("alt=\"M\"")) // no source: its description is shown as text
    }

    @Test fun pageTitleAndStyleSheetsAreNotShownAsText() {
        val out = HtmlSanitizer.clean("<html><head><title>Pago realizado</title><style>p{color:red}</style></head><body><p>Hola</p></body></html>")
        assertFalse(out.contains("Pago realizado"))
        assertFalse(out.contains("color:red"))
        assertTrue(out.contains("Hola"))
    }

    @Test fun remoteCssBackgroundsCountAsRemoteImages() {
        val html = """<td style="background:url('https://x.example/bg.png')">x</td>"""
        assertTrue(HtmlSanitizer.hasRemoteImages("<table><tr>$html</tr></table>"))
        assertTrue(HtmlSanitizer.clean("<table><tr>$html</tr></table>").contains("https://x.example/bg.png"))
        assertFalse(HtmlSanitizer.hasRemoteImages("""<img src="cid:a"><p>x</p>"""))
    }
}
