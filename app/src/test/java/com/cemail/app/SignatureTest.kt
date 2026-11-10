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

/** The same structure as a signature written in Gmail (nested tables, remote icons), with invented data. */
class GmailStyleSignatureTest {
    private val eml = """
        MIME-Version: 1.0
        Subject: Prueba
        Content-Type: multipart/mixed; boundary="mix"

        --mix
        Content-Type: multipart/alternative; boundary="alt"

        --alt
        Content-Type: text/plain; charset="UTF-8"

        Buenos dias, texto del mensaje
        --alt
        Content-Type: text/html; charset="UTF-8"

        <div dir="ltr"><div>Buenos dias, texto del mensaje</div><div><div dir="ltr" class="gmail_signature" data-smartmail="gmail_signature"><div dir="ltr"><table cellspacing="0" cellpadding="0" border="0" style="width:450px;color:rgb(68,68,68)"><tbody><tr><td style="border-bottom:2px solid rgb(2,60,79)"><span style="font-size:16px;font-weight:700">Ana Ejemplo</span><span style="display:block">Ingeniera</span><a href="https://example.com/in/ana"><img src="https://img.icons8.com/ios-filled/48/023C4F/linkedin.png" alt="LinkedIn" width="22" height="22"></a></td></tr><tr><td><strong>Phone:</strong>&nbsp;+00 000 000</td></tr></tbody></table></div></div></div></div>
        --alt--
        --mix
        Content-Type: application/pdf; name="x.pdf"
        Content-Transfer-Encoding: base64

        JVBERi0xLjQK
        --mix--
    """.trimIndent().replace("\n", "\r\n")

    @Test
    fun importsTheSignatureOfAGmailMessageWithAnAttachment() {
        val signature = SignatureHtml.extract(EmlParser.parse(eml.toByteArray()))!!
        assertTrue(signature.contains("Ana Ejemplo"))
        assertTrue(signature.contains("Phone:"))
        assertTrue(signature.contains("https://img.icons8.com/ios-filled/48/023C4F/linkedin.png"))
        assertTrue(signature.contains("border-bottom"))
        assertFalse(signature.contains("texto del mensaje"))
        val (html, images) = SignatureHtml.merge("<p>Hola</p>", signature)
        assertEquals(0, images.size)
        assertTrue(html.startsWith("<p>Hola</p>"))
        assertEquals("Ana Ejemplo\nIngeniera", SignatureHtml.toText(signature).lines().take(2).joinToString("\n"))
    }
}
