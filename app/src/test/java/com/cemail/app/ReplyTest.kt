package com.cemail.app

import com.cemail.app.data.gmail.GmailMessage
import com.cemail.app.data.gmail.Header
import com.cemail.app.data.gmail.MessagePart
import com.cemail.app.data.gmail.MimeBuilder
import com.cemail.app.data.gmail.ReplyInfoParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplyTest {
    private fun message(vararg headers: Pair<String, String>) = GmailMessage(
        id = "m1", threadId = "t1",
        payload = MessagePart(headers = headers.map { Header(it.first, it.second) }),
    )

    @Test
    fun readsAddressesFromHeadersWithNames() {
        assertEquals(
            listOf("ana@x.com", "B@y.org"),
            ReplyInfoParser.addresses("\"Ana, la jefa\" <ana@x.com>, Bob <B@y.org>, ana@X.com"),
        )
    }

    @Test
    fun plainReplyGoesToReplyToOrSender() {
        val info = ReplyInfoParser.parse(message("From" to "Ana <ana@x.com>", "Reply-To" to "soporte@x.com", "To" to "yo@me.com"))
        assertEquals(listOf("soporte@x.com") to emptyList<String>(), ReplyInfoParser.recipients(info, "yo@me.com", all = false))
        assertEquals("t1", info.threadId)
    }

    @Test
    fun replyAllCopiesEveryoneButMe() {
        val info = ReplyInfoParser.parse(message("From" to "ana@x.com", "To" to "yo@me.com, luis@y.org", "Cc" to "carla@z.net, YO@ME.COM, ana@x.com"))
        val (to, cc) = ReplyInfoParser.recipients(info, "yo@me.com", all = true)
        assertEquals(listOf("ana@x.com"), to)
        assertEquals(listOf("luis@y.org", "carla@z.net"), cc)
    }

    @Test
    fun answeringMyOwnMessageGoesToItsRecipients() {
        val info = ReplyInfoParser.parse(message("From" to "yo@me.com", "To" to "luis@y.org", "Cc" to "carla@z.net"))
        assertEquals(listOf("luis@y.org") to emptyList<String>(), ReplyInfoParser.recipients(info, "yo@me.com", all = false))
        assertEquals(listOf("luis@y.org") to listOf("carla@z.net"), ReplyInfoParser.recipients(info, "yo@me.com", all = true))
    }

    @Test
    fun repliesCarryTheThreadHeaders() {
        val raw = String(MimeBuilder.build("a@b.com", "Re: Hola", "ok", inReplyTo = "<m1@x>", references = "<m0@x> <m1@x>"), Charsets.US_ASCII)
        assertTrue(raw.contains("In-Reply-To: <m1@x>"))
        assertTrue(raw.contains("References: <m0@x> <m1@x>"))
    }
}
