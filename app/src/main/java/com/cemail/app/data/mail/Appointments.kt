package com.cemail.app.data.mail

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** A date or time found in a text (by ML Kit), with where it was written. */
data class FoundDate(val from: Int, val to: Int, val millis: Long, val hasTime: Boolean)

/**
 * Decides which dates written in a message look like an appointment. Deliberately strict: a missed suggestion is
 * better than a wrong one. Rules: only what the sender wrote (no quoted mail, no signature); not before the message
 * and at most about a year after it; a word of meeting up near the date (or in the subject); nothing about bills,
 * offers or expiry; at most two.
 */
object Appointments {
    private const val MAX_TEXT = 3000
    private const val MAX_SUGGESTIONS = 2
    private const val MAX_AHEAD = 400L * 24 * 3_600_000
    private const val HOUR = 3_600_000L
    private const val DAY = 24 * HOUR
    private const val NEAR = 140

    private fun regex(pattern: String) = Regex(pattern, RegexOption.IGNORE_CASE)

    /** A line from which on nothing was written for this message: an earlier mail or a signature. */
    private val END = listOf(
        regex("""^(On|El|Am)\s.{0,200}(wrote|escribi[oó]|schrieb.{0,100}):$"""),
        regex("""^.{0,200}(wrote|escribi[oó]):$"""),
        regex("""^-{2,}\s*(Original Message|Forwarded message|Mensaje original|Mensaje reenviado|Urspr[uü]ngliche Nachricht|Weitergeleitete Nachricht).*"""),
        regex("""^_{10,}$"""),
        regex("""^--\s?$"""),
        regex("""^(Sent from|Get Outlook|Enviado desde|Obtener Outlook|Gesendet von|Von meinem\s.{0,40}gesendet).*"""),
        regex(
            """^(Saludos|Un saludo|Un abrazo|Atentamente|Cordialmente|Best regards|Kind regards|Regards|Best wishes|Cheers|Sincerely|Viele Gr[uü](ß|ss)e|Liebe Gr[uü](ß|ss)e|Beste Gr[uü](ß|ss)e|Mit freundlichen Gr[uü](ß|ss)en|VG|LG)[\s,.!]*$""",
        ),
    )
    private val HEADER_FROM = regex("""^\*?(From|De|Von):\*?\s.+""")
    private val HEADER_DATE = regex("""^\*?(Sent|Date|Enviado|Fecha|Gesendet|Datum):.*""")
    private val WRAPPED_REPLY = regex("""^(On|El|Am)\s.+""")

    /** What the sender wrote in this message. */
    fun ownText(body: String): String {
        val lines = body.take(MAX_TEXT * 4).lines()
        val kept = ArrayList<String>()
        for ((n, raw) in lines.withIndex()) {
            val line = raw.trim()
            if (line.startsWith(">")) continue
            if (END.any { it.matches(line) }) {
                // "On Tue ... <a@b.c>" cut in two lines: the first half goes too.
                if (kept.isNotEmpty() && WRAPPED_REPLY.matches(kept.last().trim()) && !WRAPPED_REPLY.matches(line)) {
                    kept.removeAt(kept.lastIndex)
                }
                break
            }
            if (HEADER_FROM.matches(line) && lines.drop(n + 1).take(3).any { HEADER_DATE.matches(it.trim()) }) break
            kept += raw
        }
        return kept.joinToString("\n").take(MAX_TEXT)
    }

    private val CUE = regex(
        """(?<!\p{L})(""" +
            // español
            """reuni[oó]n|reuniones|reunirnos|cita|quedamos|quedar|quedada|nos vemos|vernos|verte|te espero|cena|cenamos|cenar|comida|comemos|comer|almuerzo|desayuno|caf[eé]|tomar algo|llamada|videollamada|te llamo|entrevista|consulta|vuelo|reserva|evento|fiesta|cumpleaños|clase|taller|visita|concierto|boda|partido|ensayo|presentaci[oó]n|""" +
            // English
            """meeting|meet|meet-up|meetup|appointment|call|dinner|lunch|breakfast|drinks|coffee|see you|interview|flight|reservation|booking|party|birthday|class|lesson|workshop|visit|concert|wedding|webinar|session|presentation|""" +
            // Deutsch
            """\p{L}*termin|\p{L}*besprechung|\p{L}*gespr[aä]ch|\p{L}*feier|treffen|treffe|trefft|abendessen|mittagessen|fr[uü]hst[uü]ck|essen gehen|anruf|telefonat|telefonieren|sehen wir uns|wir sehen uns|flug|reservierung|buchung|geburtstag|kurs|unterricht|besuch|konzert|hochzeit|verabredung|kaffee""" +
            """)(?!\p{L})""",
    )
    private val NOT_AN_APPOINTMENT = regex(
        """%|(?<!\p{L})(factura|recibo|vence|vencimiento|caduca|oferta|descuento|rebajas|invoice|payment|due|expires?|offer|sale|discount|rechnung|zahlung|f[aä]llig|angebot|rabatt|g[uü]ltig)(?!\p{L})""",
    )
    private val PREFIX = regex("""^\s*((re|fwd?|rv|aw|wg|tr|antw)(\[\d+])?\s*:\s*)+""")

    /** The subject without "Re:", "Fwd:" and their relatives. */
    fun title(subject: String) = subject.replace(PREFIX, "").trim()

    /** The paragraph around a date, a bounded stretch of it. */
    private fun around(text: String, from: Int, to: Int): String {
        var start = maxOf(0, from - NEAR)
        var end = minOf(text.length, to + NEAR)
        val before = text.lastIndexOf("\n\n", from)
        if (before >= start) start = before
        val after = text.indexOf("\n\n", to)
        if (after in 0 until end) end = after
        return text.substring(start, end)
    }

    /** An hour written without its day: "10 Uhr", "a las 10:00", "at 8 pm". */
    private val TIME_ONLY = regex("""((um|at|a las|a la|sobre las|gegen|ab)\s+)?\d{1,2}([:.h]\d{2})?\s*(uhr|a\.?\s?m\.?|p\.?\s?m\.?|h|horas)?[.,]?""")
    private const val SAME_PHRASE = 80

    /**
     * A day/month without year, in English: only after "on", "date:" or a weekday, or right before an hour, so that
     * "24/7" or "rated 5/10" are not days.
     */
    private val DAY_MONTH = regex("""(?<![\p{L}\d/.])(\d{1,2})/(\d{1,2})(?![\p{L}\d/]|[.,:]\d)""")
    private val DATE_BEFORE = regex("""(?<!\p{L})(on( the)?|date:?|(mon|tues|wednes|thurs|fri|satur|sun)day,?( the)?)\s+$""")
    private val HOUR_AFTER = regex("""^,?\s+(at\s+\d|\d{1,2}(:\d{2}|\s?[ap]\.?m(?!\p{L})))""")
    private const val JUST_PAST = 60L
    private val SAME_DAY = regex("""^,?\s*(the\s+)?$""")

    /**
     * ML Kit reads "12/03" as December 2003 whatever the locale, so those days are read here: day first, and the next
     * time that day comes after [sent] (one just gone by stays in the past, and is not suggested).
     */
    fun dayMonth(text: String, dates: List<FoundDate>, sent: Long, zone: ZoneId): List<FoundDate> {
        val sentDay = Instant.ofEpochMilli(sent).atZone(zone).toLocalDate()
        var result = dates
        for (match in DAY_MONTH.findAll(text)) {
            val from = match.range.first
            val to = match.range.last + 1
            if (!DATE_BEFORE.containsMatchIn(text.substring(maxOf(0, from - 20), from)) &&
                !HOUR_AFTER.containsMatchIn(text.substring(to, minOf(text.length, to + 12)))
            ) {
                continue
            }
            val (day, month) = match.destructured
            fun inYear(year: Int) = runCatching { LocalDate.of(year, month.toInt(), day.toInt()) }.getOrNull()
            val date = inYear(sentDay.year)?.takeIf { !it.isBefore(sentDay.minusDays(JUST_PAST)) }
                ?: inYear(sentDay.year + 1) ?: continue
            // With them goes a weekday written just before: "on Friday 5/12" is one day, the numbered one.
            val overlapping = result.filter {
                (it.from < to && it.to > from) || (!it.hasTime && it.to <= from && SAME_DAY.matches(text.substring(it.to, from)))
            }
            // An hour found together with the date is kept.
            val timed = overlapping.firstOrNull { it.hasTime }
            val read = if (timed == null) {
                FoundDate(from, to, date.atStartOfDay(zone).toInstant().toEpochMilli(), false)
            } else {
                val time = Instant.ofEpochMilli(timed.millis).atZone(zone).toLocalTime()
                FoundDate(minOf(from, timed.from), maxOf(to, timed.to), date.atTime(time).atZone(zone).toInstant().toEpochMilli(), true)
            }
            result = result - overlapping.toSet() + read
        }
        return result
    }

    /**
     * "am Dienstag um 10 Uhr" is often found as a day and an hour apart: they become one moment. An hour with no day
     * written near it is dropped, as which day it means would be a guess.
     */
    private fun joined(text: String, dates: List<FoundDate>, zone: ZoneId): List<FoundDate> {
        fun isDay(d: FoundDate) = !d.hasTime
        fun isHour(d: FoundDate) = d.hasTime && TIME_ONLY.matches(text.substring(d.from, d.to).trim())
        fun combined(day: FoundDate, hour: FoundDate): FoundDate {
            val at = Instant.ofEpochMilli(day.millis).atZone(zone).toLocalDate()
                .atTime(Instant.ofEpochMilli(hour.millis).atZone(zone).toLocalTime())
                .atZone(zone).toInstant().toEpochMilli()
            return FoundDate(minOf(day.from, hour.from), maxOf(day.to, hour.to), at, true)
        }
        val sorted = dates.sortedBy { it.from }
        val used = HashSet<FoundDate>()
        val moments = ArrayList<FoundDate>()
        for ((n, hour) in sorted.withIndex()) {
            if (!isHour(hour)) continue
            used += hour
            // The day said just before ("morgen ... um 19 Uhr"), or else right after ("um 10 Uhr am Dienstag").
            val before = sorted.take(n).lastOrNull(::isDay)
                ?.takeIf { hour.from - it.to in 0..SAME_PHRASE && "\n\n" !in text.substring(it.to, hour.from) }
            val day = before ?: sorted.getOrNull(n + 1)?.takeIf { isDay(it) && it.from - hour.to in 0..12 }
            if (day != null) {
                used += day
                moments += combined(day, hour)
            }
        }
        return (sorted.filter { it !in used } + moments).sortedBy { it.from }
    }

    private val EVENING = regex("""^[^.!?\n]{0,8}?(de la (tarde|noche)|in the (evening|afternoon)|at night|tonight|abends|nachmittags)""")
    private val DAY_AFTER = regex("""day after\s+$""")
    private val COMING = regex("""que viene|(?<!\p{L})(next|n[aä]chsten)(?!\p{L})""")

    /** What ML Kit was seen to miss: "9 de la noche", "the day after tomorrow", "el viernes que viene" said on a Friday. */
    private fun adjusted(text: String, date: FoundDate, sent: Long, zone: ZoneId): FoundDate {
        var at = Instant.ofEpochMilli(date.millis).atZone(zone)
        val after = text.substring(date.to, minOf(text.length, date.to + 32))
        if (date.hasTime && at.hour in 1..11 && EVENING.containsMatchIn(after)) at = at.plusHours(12)
        if (DAY_AFTER.containsMatchIn(text.substring(maxOf(0, date.from - 16), date.from))) at = at.plusDays(1)
        val sentDay = Instant.ofEpochMilli(sent).atZone(zone).toLocalDate()
        if (at.toLocalDate() == sentDay && COMING.containsMatchIn(text.substring(date.from, minOf(text.length, date.to + 12)))) {
            at = at.plusDays(7)
        }
        return date.copy(millis = at.toInstant().toEpochMilli())
    }

    /** The events to propose for [dates] found in [text] (the [ownText] of a message sent at [sent]). */
    fun suggestions(text: String, subject: String, sent: Long, dates: List<FoundDate>, zone: ZoneId): List<IcsEvent> {
        val title = title(subject)
        val subjectCue = CUE.containsMatchIn(title)
        if (NOT_AN_APPOINTMENT.containsMatchIn(title)) return emptyList()
        val sentDay = Instant.ofEpochMilli(sent).atZone(zone).toLocalDate()
        val valid = dates.filter { it.from >= 0 && it.to <= text.length && it.from < it.to }
        val found = joined(text, valid, zone).map { adjusted(text, it, sent, zone) }.filter { date ->
            val near = around(text, date.from, date.to)
            val day = Instant.ofEpochMilli(date.millis).atZone(zone).toLocalDate()
            val future = if (date.hasTime) date.millis > sent else !day.isBefore(sentDay)
            future && date.millis - sent < MAX_AHEAD &&
                (subjectCue || CUE.containsMatchIn(near)) && !NOT_AN_APPOINTMENT.containsMatchIn(near)
        }
        fun day(date: FoundDate) = Instant.ofEpochMilli(date.millis).atZone(zone).toLocalDate()
        val timedDays = found.filter { it.hasTime }.map(::day).toSet()
        return found
            .filter { it.hasTime || day(it) !in timedDays }
            .distinctBy { if (it.hasTime) it.millis else day(it) }
            .take(MAX_SUGGESTIONS)
            .map { date ->
                // Whole days are stored as UTC days, like the ones of an .ics file.
                val start = if (date.hasTime) date.millis else day(date).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
                IcsEvent(
                    title = title, location = "", description = "", startMillis = start,
                    endMillis = start + if (date.hasTime) HOUR else DAY, allDay = !date.hasTime,
                    rrule = null, uid = null, recurrenceId = null, cancelled = false,
                )
            }
    }

    private val WORDS = mapOf(
        "es" to setOf("el", "la", "los", "las", "que", "de", "y", "por", "para", "con", "nos", "un", "una", "hola", "te", "del"),
        "en" to setOf("the", "and", "you", "to", "of", "is", "for", "on", "we", "hi", "hello", "see", "are", "with", "your", "at"),
        "de" to setOf("der", "die", "das", "und", "ist", "ich", "wir", "um", "mit", "für", "zu", "du", "hallo", "nicht", "uns", "dich"),
    )

    /** "es", "en" or "de" by the common words of [text]; [fallback] when it cannot be told. */
    fun language(text: String, fallback: String): String {
        val words = text.take(MAX_TEXT).lowercase().split(Regex("""[^\p{L}]+"""))
        val scores = WORDS.mapValues { (_, common) -> words.count { it in common } }
        val best = scores.maxByOrNull { it.value } ?: return "en"
        if (best.value > 0 && scores.values.count { it == best.value } == 1) return best.key
        return if (fallback in WORDS) fallback else "en"
    }
}
