package com.cemail.app

import com.cemail.app.data.gmail.GmailMessage
import com.cemail.app.data.gmail.MessagePart
import com.cemail.app.data.gmail.PartBody
import com.cemail.app.data.mail.CalendarParts
import com.cemail.app.data.mail.Ics
import java.time.Instant
import java.time.ZoneId
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IcsTest {
    private val madrid = ZoneId.of("Europe/Madrid")

    private fun calendar(vararg lines: String) = (listOf("BEGIN:VCALENDAR") + lines + "END:VCALENDAR").joinToString("\r\n")
    private fun event(vararg lines: String) = calendar("BEGIN:VEVENT", *lines, "END:VEVENT")
    private fun at(iso: String) = Instant.parse(iso).toEpochMilli()
    private fun one(text: String) = Ics.parse(text, madrid).single()

    @Test
    fun foldedLinesEscapesAndQuotedParameters() {
        val e = one(
            event(
                "SUMMARY:Cena con Ana\\, Luis", " a y Eva",
                "DESCRIPTION;ALTREP=\"cid:a;b:c\":Uno\\nDos\\; tres \\\\",
                "LOCATION:Calle Mayor 1\\, Madrid",
                "DTSTART:20261201T190000Z",
                "DTEND:20261201T210000Z",
                "BEGIN:VALARM", "DESCRIPTION:Reminder", "SUMMARY:no", "END:VALARM",
            ),
        )
        assertEquals("Cena con Ana, Luisa y Eva", e.title)
        assertEquals("Uno\nDos; tres \\", e.description)
        assertEquals("Calle Mayor 1, Madrid", e.location)
        assertEquals(at("2026-12-01T19:00:00Z"), e.startMillis)
        assertEquals(at("2026-12-01T21:00:00Z"), e.endMillis)
    }

    @Test
    fun timeZones() {
        fun start(tz: String) = one(event("DTSTART$tz:20260701T100000")).startMillis
        assertEquals(at("2026-07-01T14:00:00Z"), start(";TZID=America/New_York"))
        assertEquals(at("2026-07-01T08:00:00Z"), start(";TZID=\"W. Europe Standard Time\""))
        assertEquals(at("2026-07-01T08:00:00Z"), start(";TZID=/mozilla.org/x/Europe/Berlin"))
        // Without a zone, or with one nobody knows: the phone's.
        assertEquals(at("2026-07-01T08:00:00Z"), start(""))
        assertEquals(at("2026-07-01T08:00:00Z"), start(";TZID=Nowhere"))
    }

    @Test
    fun zoneDescribedInTheFile() {
        fun parse(date: String) = one(
            calendar(
                "BEGIN:VTIMEZONE", "TZID:Custom",
                "BEGIN:STANDARD", "DTSTART:16011028T030000", "RRULE:FREQ=YEARLY;BYDAY=-1SU;BYMONTH=10", "TZOFFSETTO:-0500", "END:STANDARD",
                "BEGIN:DAYLIGHT", "DTSTART:16010325T020000", "RRULE:FREQ=YEARLY;BYDAY=-1SU;BYMONTH=3", "TZOFFSETTO:-0400", "END:DAYLIGHT",
                "END:VTIMEZONE",
                "BEGIN:VEVENT", "DTSTART;TZID=Custom:$date", "END:VEVENT",
            ),
        ).startMillis
        assertEquals(at("2026-07-01T14:00:00Z"), parse("20260701T100000"))
        assertEquals(at("2026-12-01T15:00:00Z"), parse("20261201T100000"))
    }

    @Test
    fun allDayDurationAndMissingEnd() {
        val day = one(event("DTSTART;VALUE=DATE:20261224", "DTEND;VALUE=DATE:20261227"))
        assertTrue(day.allDay)
        assertEquals(at("2026-12-24T00:00:00Z"), day.startMillis)
        assertEquals(at("2026-12-27T00:00:00Z"), day.endMillis)
        assertEquals(at("2026-12-25T00:00:00Z"), one(event("DTSTART;VALUE=DATE:20261224")).endMillis)
        assertEquals(at("2026-12-01T11:30:00Z"), one(event("DTSTART:20261201T100000Z", "DURATION:PT1H30M")).endMillis)
        assertEquals(at("2026-12-01T11:00:00Z"), one(event("DTSTART:20261201T100000Z")).endMillis)
    }

    @Test
    fun severalEventsSeriesAndCancellations() {
        val events = Ics.parse(
            calendar(
                "BEGIN:VEVENT", "UID:a", "DTSTART:20261201T100000Z", "RRULE:FREQ=WEEKLY;COUNT=3", "END:VEVENT",
                "BEGIN:VEVENT", "UID:a", "RECURRENCE-ID:20261208T100000Z", "DTSTART:20261208T120000Z", "END:VEVENT",
                "BEGIN:VEVENT", "UID:b", "DTSTART:20261202T100000Z", "STATUS:CANCELLED", "END:VEVENT",
            ),
            madrid,
        )
        assertEquals(2, events.size)
        assertEquals("FREQ=WEEKLY;COUNT=3", events[0].rrule)
        assertEquals(false, events[0].cancelled)
        assertEquals(true, events[1].cancelled)
        assertTrue(one(calendar("METHOD:CANCEL", "BEGIN:VEVENT", "DTSTART:20261201T100000Z", "END:VEVENT")).cancelled)
    }

    @Test
    fun brokenFilesDoNotFail() {
        assertTrue(Ics.parse("", madrid).isEmpty())
        assertTrue(Ics.parse("not a calendar\n;;;:::\n\"", madrid).isEmpty())
        assertTrue(Ics.parse(event("SUMMARY:no start"), madrid).isEmpty())
        assertTrue(Ics.parse(event("DTSTART:99999999T999999"), madrid).isEmpty())
        assertTrue(Ics.parse("x".repeat(Ics.MAX_CHARS + 1), madrid).isEmpty())
        assertTrue(Ics.parse("END:VEVENT\nEND:VCALENDAR", madrid).isEmpty())
        // Cut off in the middle: what is complete is still shown.
        val cut = "BEGIN:VCALENDAR\nBEGIN:VEVENT\nSUMMARY:Half\nDTSTART:20261201T100000Z\nDESCRIP"
        assertEquals("Half", one(cut).title)
        assertNull(one(event("DTSTART:20261201T100000Z", "RRULE:rubbish")).rrule)
        val many = calendar(*Array(100) { "BEGIN:VEVENT\nDTSTART:20261201T100000Z\nEND:VEVENT" })
        assertEquals(20, Ics.parse(many, madrid).size)
    }

    @Test
    fun findsTheInvitationInAGmailMessage() {
        val data = Base64.getUrlEncoder().encodeToString("BEGIN:VCALENDAR".toByteArray())
        val message = GmailMessage(
            id = "1",
            payload = MessagePart(
                mimeType = "multipart/mixed",
                parts = listOf(
                    MessagePart(mimeType = "text/calendar", body = PartBody(size = 15, data = data)),
                    MessagePart(mimeType = "application/ics", filename = "invite.ics", body = PartBody(size = 15, attachmentId = "x")),
                ),
            ),
        )
        assertEquals("BEGIN:VCALENDAR", CalendarParts.find(message).single().data?.let { String(it) })
    }

    @Test
    fun findsTheInvitationInMime() {
        val raw = listOf(
            "Content-Type: multipart/alternative; boundary=b",
            "",
            "--b",
            "Content-Type: text/plain",
            "",
            "Hello",
            "--b",
            "Content-Type: text/calendar; method=REQUEST; charset=UTF-8",
            "Content-Transfer-Encoding: base64",
            "",
            Base64.getEncoder().encodeToString(event("SUMMARY:Reunión", "DTSTART:20261201T100000Z").toByteArray()),
            "--b--",
            "",
        ).joinToString("\r\n")
        val found = CalendarParts.find(raw.toByteArray())
        assertEquals("Reunión", one(CalendarParts.text(found.single())).title)
    }
}
