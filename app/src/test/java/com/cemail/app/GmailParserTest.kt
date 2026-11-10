package com.cemail.app

import com.cemail.app.data.gmail.GmailMessage
import com.cemail.app.data.gmail.GmailParser
import com.cemail.app.data.gmail.Header
import com.cemail.app.data.gmail.MessagePart
import com.cemail.app.data.gmail.MimeBuilder
import com.cemail.app.data.gmail.PartBody
import com.cemail.app.data.mail.Folder
import com.cemail.app.data.mail.LabelDiff
import com.cemail.app.data.mail.Mail
import java.util.Base64
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GmailParserTest {
    private fun b64(s: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(s.toByteArray())

    private fun message(parts: List<MessagePart>, headers: List<Header>) = GmailMessage(
        id = "1", labelIds = listOf("INBOX", "UNREAD"), snippet = "snip", internalDate = "1700000000000",
        payload = MessagePart(mimeType = "multipart/alternative", headers = headers, parts = parts),
    )

    @Test fun prefersPlainTextAndReadsHeaders() {
        val m = message(
            listOf(
                MessagePart(mimeType = "text/plain", body = PartBody(data = b64("Hola mundo"))),
                MessagePart(mimeType = "text/html", body = PartBody(data = b64("<p>Hola <b>html</b></p>"))),
            ),
            listOf(Header("From", "\"López, Ana\" <ana@x.com>"), Header("Subject", "Asunto"), Header("To", "yo@x.com")),
        )
        val p = GmailParser.parse(m)
        assertEquals("Hola mundo", p.body)
        assertEquals("López, Ana", p.fromName)
        assertEquals("ana@x.com", p.fromEmail)
        assertEquals(1700000000000L, p.timeMillis)
    }

    @Test fun htmlOnlyBecomesReadableText() {
        val m = message(
            listOf(MessagePart(mimeType = "text/html", body = PartBody(data = b64("<div>Uno</div><p>Dos<br>tres</p><style>x{}</style>")))),
            listOf(Header("From", "a@b.c")),
        )
        assertEquals("Uno\nDos\ntres", GmailParser.parse(m).body)
    }

    @Test fun detectsAttachmentsAndDecodesEncodedWords() {
        val m = message(
            listOf(
                MessagePart(mimeType = "text/plain", body = PartBody(data = b64("x"))),
                MessagePart(mimeType = "application/pdf", filename = "a.pdf", body = PartBody(attachmentId = "att1")),
            ),
            listOf(Header("From", "=?UTF-8?B?w4FuZ2Vs?= <a@b.c>"), Header("Subject", "=?UTF-8?Q?Caf=C3=A9_ma=C3=B1ana?=")),
        )
        val p = GmailParser.parse(m)
        assertTrue(p.hasAttachment)
        assertEquals("Ángel", p.fromName)
        assertEquals("Café mañana", p.subject)
    }

    @Test fun parsesRealGmailJson() {
        val json = """{"id":"abc","threadId":"t","labelIds":["INBOX"],"snippet":"s","internalDate":"5","unknown":1,
            "payload":{"mimeType":"text/plain","headers":[{"name":"Subject","value":"Hi"}],"body":{"size":2,"data":"aGk"}}}"""
        val msg = Json { ignoreUnknownKeys = true }.decodeFromString<GmailMessage>(json)
        assertEquals("hi", GmailParser.parse(msg).body)
    }

    @Test fun labelsMapToFolders() {
        assertEquals(Folder.INBOX, LabelDiff.stateOf(setOf("INBOX", "SENT")).folder)
        assertEquals(Folder.TRASH, LabelDiff.stateOf(setOf("TRASH", "INBOX")).folder)
        assertEquals(Folder.ARCHIVE, LabelDiff.stateOf(setOf("CATEGORY_UPDATES")).folder)
        assertEquals(false, LabelDiff.stateOf(setOf("INBOX", "UNREAD")).read)
        assertEquals(true, LabelDiff.stateOf(setOf("STARRED")).starred)
    }

    @Test fun diffBetweenMailStatesGivesLabelChanges() {
        val a = Mail(1, "n", "e", "s", "b", 0, read = false)
        val (add, remove) = LabelDiff.between(a, a.copy(folder = Folder.ARCHIVE, read = true, starred = true))
        assertEquals(listOf("STARRED"), add)
        assertEquals(listOf("INBOX", "TRASH", "UNREAD"), remove)
    }

    @Test fun mimeEncodesNonAsciiSubjectAndBody() {
        val raw = String(MimeBuilder.build("a@b.c", "Café", "Hola ñ"), Charsets.US_ASCII)
        assertTrue(raw.startsWith("To: a@b.c\r\nSubject: =?UTF-8?B?"))
        assertTrue(raw.contains("Content-Transfer-Encoding: base64"))
        assertTrue(raw.endsWith(Base64.getEncoder().encodeToString("Hola ñ".toByteArray())))
    }
}
