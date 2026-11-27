package com.cemail.app

import com.cemail.app.data.mail.Appointments
import com.cemail.app.data.mail.FoundDate
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppointmentsTest {
    private val madrid = ZoneId.of("Europe/Madrid")
    private fun at(iso: String) = Instant.parse(iso).toEpochMilli()
    private val sent = at("2026-11-27T08:00:00Z")
    private val tuesday = at("2026-12-01T09:00:00Z")

    private fun found(text: String, part: String, millis: Long = tuesday, time: Boolean = true) =
        FoundDate(text.indexOf(part), text.indexOf(part) + part.length, millis, time)

    private fun suggest(text: String, vararg dates: FoundDate, subject: String = "Hola") =
        Appointments.suggestions(text, subject, sent, dates.toList(), madrid)

    @Test
    fun appointmentsInThreeLanguages() {
        for ((text, part) in listOf(
            "Tenemos reunión el martes a las 10:00." to "el martes a las 10:00",
            "Our meeting is on Tuesday at 10." to "on Tuesday at 10",
            "Der Zahnarzttermin ist am Dienstag um 10 Uhr." to "am Dienstag um 10 Uhr",
        )) {
            val event = suggest(text, found(text, part)).single()
            assertEquals(tuesday, event.startMillis)
            assertEquals(tuesday + 3_600_000, event.endMillis)
        }
    }

    @Test
    fun dateOnlyIsAWholeDayAndTheTitleLosesPrefixes() {
        val text = "La fiesta es el 5 de diciembre."
        val date = found(text, "el 5 de diciembre", at("2026-12-04T23:00:00Z"), time = false)
        val event = suggest(text, date, subject = "RE: Fwd: AW: Plan").single()
        assertTrue(event.allDay)
        assertEquals(at("2026-12-05T00:00:00Z"), event.startMillis)
        assertEquals(at("2026-12-06T00:00:00Z"), event.endMillis)
        assertEquals("Plan", event.title)
    }

    @Test
    fun rejected() {
        val noCue = "El informe se envió el martes a las 10:00."
        assertTrue(suggest(noCue, found(noCue, "el martes a las 10:00")).isEmpty())
        // The subject can be what says it is a meeting.
        assertEquals(1, suggest(noCue, found(noCue, "el martes a las 10:00"), subject = "Reunión").size)
        val bill = "Your invoice for the dinner is due on Tuesday."
        assertTrue(suggest(bill, found(bill, "on Tuesday")).isEmpty())
        val offer = "Book your flight: 20% off until Tuesday"
        assertTrue(suggest(offer, found(offer, "Tuesday")).isEmpty())
        val past = "What a great dinner on Monday."
        assertTrue(suggest(past, found(past, "on Monday", at("2026-11-23T18:00:00Z"))).isEmpty())
        val far = "Meeting in 2030."
        assertTrue(suggest(far, found(far, "2030", at("2030-01-01T10:00:00Z"))).isEmpty())
        val elsewhere = "Dinner was lovely.\n\nThe report is from Tuesday."
        assertTrue(suggest(elsewhere, found(elsewhere, "Tuesday")).isEmpty())
    }

    @Test
    fun joinsDayAndTimeAndKeepsAtMostTwo() {
        val text = "Quedamos el martes, a las 10:00."
        val day = found(text, "el martes", at("2026-12-01T11:00:00Z"), time = false)
        val time = found(text, "a las 10:00", at("2026-11-27T09:00:00Z"))
        assertEquals(tuesday, suggest(text, day, time).single().startMillis)
        val many = "meeting A, meeting B, meeting C"
        val dates = listOf("A", "B", "C").mapIndexed { n, p -> found(many, p, tuesday + n * 86_400_000L) }
        assertEquals(2, suggest(many, *dates.toTypedArray()).size)
    }

    @Test
    fun anHourTakesTheDaySaidBeforeItOrIsDropped() {
        val text = "Hast du morgen Zeit? Wir sehen uns um 19 Uhr zum Abendessen."
        val day = found(text, "morgen", at("2026-11-27T23:00:00Z"), time = false)
        val hour = found(text, "um 19 Uhr", at("2026-11-27T18:00:00Z"))
        assertEquals(at("2026-11-28T18:00:00Z"), suggest(text, day, hour).single().startMillis)
        assertTrue(suggest(text, hour).isEmpty())
    }

    @Test
    fun correctionsOfWhatWasReadWrong() {
        fun start(text: String, part: String, millis: Long, time: Boolean) =
            suggest(text, found(text, part, millis, time)).single().startMillis
        val night = "Quedamos el sábado a las 9 de la noche."
        assertEquals(at("2026-11-28T20:00:00Z"), start(night, "sábado a las 9", at("2026-11-28T08:00:00Z"), true))
        val after = "Can we meet the day after tomorrow?"
        assertEquals(at("2026-11-29T00:00:00Z"), start(after, "tomorrow", at("2026-11-27T23:00:00Z"), false))
        // Sent on a Friday.
        val coming = "¿Nos vemos el viernes que viene?"
        assertEquals(at("2026-12-04T00:00:00Z"), start(coming, "viernes", at("2026-11-26T23:00:00Z"), false))
    }

    @Test
    fun quotedTextAndSignaturesAreNotRead() {
        fun own(body: String) = Appointments.ownText(body).trim()
        assertEquals("Hi", own("Hi\n> dinner on Friday"))
        assertEquals("Vale", own("Vale\n\nEl mar, 1 dic 2026, Ana <a@b.c> escribió:\nquedamos el lunes"))
        assertEquals("Ok", own("Ok\nOn Tue, 1 Dec 2026 at 10:00, Ana Example\n<a@b.c> wrote:\n> x"))
        assertEquals("Ja", own("Ja\nAm 01.12.26 schrieb Ana <a@b.c>:\nTreffen am Montag"))
        assertEquals("Ok", own("Ok\nFrom: Ana\nSent: Monday\nSubject: x\nmeeting Friday"))
        assertEquals("Ok", own("Ok\n-- \nOffice hours: meeting on Mondays"))
        assertEquals("Hallo", own("Hallo\nViele Grüße\nAna, Termine Montag 10 Uhr"))
        assertEquals("Mira", own("Mira\n---------- Forwarded message ---------\ncena el viernes"))
    }

    @Test
    fun language() {
        assertEquals("es", Appointments.language("Hola, nos vemos el martes para la cena", "en"))
        assertEquals("de", Appointments.language("Hallo, wir treffen uns und essen", "en"))
        assertEquals("en", Appointments.language("See you on Friday at the office", "es"))
        assertEquals("de", Appointments.language("12345", "de"))
        assertEquals("en", Appointments.language("12345", "fr"))
    }
}
