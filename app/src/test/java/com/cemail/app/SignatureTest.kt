package com.cemail.app

import com.cemail.app.data.gmail.EmlParser
import com.cemail.app.data.gmail.MimeBuilder
import com.cemail.app.data.gmail.SignatureHtml
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignatureTest {
    private val png = Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))

    private val eml = """
        From: Cesar <cesar@example.com>
        Subject: =?UTF-8?B?SG9sYSDDsQ==?=
        MIME-Version: 1.0
        Content-Type: multipart/related; boundary="rel"

        --rel
        Content-Type: multipart/alternative; boundary="alt"

        --alt
        Content-Type: text/plain; charset="UTF-8"

        Hola
        --alt
        Content-Type: text/html; charset="UTF-8"
        Content-Transfer-Encoding: quoted-printable

        <div>Hola, qu=C3=A9 tal</div><script>alert(1)</script>
        <div class=3D"gmail_signature"><b>C=C3=A9sar</b><br><img src=3D"cid:logo123"></div>
        --alt--
        --rel
        Content-Type: image/png; name="logo.png"
        Content-ID: <logo123>
        Content-Transfer-Encoding: base64

        $png
        --rel--
    """.trimIndent().replace("\n", "\r\n")

    @Test
    fun readsSubjectHtmlAndInlinePicture() {
        val parsed = EmlParser.parse(eml.toByteArray())
        assertEquals("Hola ñ", parsed.subject)
        assertTrue(parsed.html!!.contains("qué tal"))
        assertEquals("Hola", parsed.text!!.trim())
        assertEquals(8, parsed.images["logo123"]!!.bytes.size)
    }

    @Test
    fun extractsOnlyTheSignatureBlockAndInlinesItsPicture() {
        val signature = SignatureHtml.extract(EmlParser.parse(eml.toByteArray()))!!
        assertTrue(signature.contains("César"))
        assertFalse(signature.contains("qué tal"))
        assertFalse(signature.contains("script"))
        assertTrue(signature.contains("src=\"data:image/png;base64,"))
    }

    @Test
    fun mergingTurnsDataPicturesIntoInlineParts() {
        val signature = SignatureHtml.extract(EmlParser.parse(eml.toByteArray()))!!
        val (html, images) = SignatureHtml.merge("<p>Hola</p>", signature)
        assertEquals(1, images.size)
        assertTrue(html.contains("src=\"cid:${images[0].cid}\""))
        val raw = String(MimeBuilder.build("a@b.com", "x", "Hola", html = html, inlineImages = images), Charsets.US_ASCII)
        assertTrue(raw.contains("multipart/related"))
        assertTrue(raw.contains("Content-ID: <${images[0].cid}>"))
    }

    @Test
    fun plainTextOfASignatureKeepsItsLines() {
        assertEquals("César\nCEO", SignatureHtml.toText("<div><b>César</b><br>CEO</div>"))
    }
}
