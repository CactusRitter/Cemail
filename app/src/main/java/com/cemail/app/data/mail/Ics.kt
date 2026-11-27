package com.cemail.app.data.mail

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters

/** One event of an .ics file. All-day events run from UTC midnight to UTC midnight (end excluded), as Android wants. */
data class IcsEvent(
    val title: String,
    val location: String,
    val description: String,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val rrule: String?,
    val uid: String?,
    /** Set when this entry only changes one occurrence of a repeating event. */
    val recurrenceId: String?,
    val cancelled: Boolean,
)

/**
 * A small reader of iCalendar (RFC 5545) invitations. The file comes from whoever sent the mail, so it is not
 * trusted: sizes are limited, nothing is followed or executed, and what cannot be understood is skipped.
 */
object Ics {
    const val MAX_CHARS = 512 * 1024
    private const val MAX_EVENTS = 20
    private const val MAX_SHORT = 500
    private const val MAX_LONG = 8000

    private class Property(val name: String, val params: Map<String, String>, val value: String)

    /** The STANDARD or DAYLIGHT half of a time zone described inside the file. */
    private class Observance(val daylight: Boolean) {
        var offset: ZoneOffset? = null
        var month: Int? = null
        var week = 0
        var day: DayOfWeek? = null
        var hour = 2
    }

    private class Event {
        val props = HashMap<String, Property>()
    }

    /** The events of [text]; empty if there is none that can be read. [zone] is the phone's, for times without one. */
    fun parse(text: String, zone: ZoneId = ZoneId.systemDefault()): List<IcsEvent> {
        if (text.length > MAX_CHARS) return emptyList()
        return try {
            read(text, zone)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun read(text: String, zone: ZoneId): List<IcsEvent> {
        val stack = ArrayList<String>()
        val events = ArrayList<Event>()
        val zones = HashMap<String, MutableList<Observance>>()
        var zoneId = ""
        var method = ""
        var event: Event? = null
        var observance: Observance? = null
        for (line in unfold(text)) {
            val p = property(line) ?: continue
            when (p.name) {
                "BEGIN" -> {
                    val name = p.value.trim().uppercase()
                    if (stack.size > 8) continue
                    stack += name
                    if (name == "VEVENT") event = if (events.size < MAX_EVENTS) Event().also { events += it } else null
                    if (name == "VTIMEZONE") zoneId = ""
                    if (name == "STANDARD" || name == "DAYLIGHT") observance = Observance(name == "DAYLIGHT")
                }
                "END" -> {
                    val name = p.value.trim().uppercase()
                    val at = stack.lastIndexOf(name)
                    if (at >= 0) while (stack.size > at) stack.removeAt(stack.lastIndex)
                    if (name == "VEVENT") event = null
                    if (name == "STANDARD" || name == "DAYLIGHT") {
                        observance?.let { zones.getOrPut(zoneId) { ArrayList() }.add(it) }
                        observance = null
                    }
                }
                else -> when (stack.lastOrNull()) {
                    // Only the event's own lines: an alarm inside it has its own DESCRIPTION and SUMMARY.
                    "VEVENT" -> event?.props?.putIfAbsent(p.name, p)
                    "VCALENDAR" -> if (p.name == "METHOD") method = p.value.trim().uppercase()
                    "VTIMEZONE" -> if (p.name == "TZID") zoneId = p.value.trim()
                    "STANDARD", "DAYLIGHT" -> observance?.let { readObservance(it, p) }
                }
            }
        }
        val found = events.mapNotNull { build(it, method, zones, zone) }
        // A changed occurrence of a series that is also in the file adds nothing to show.
        val series = found.filter { it.recurrenceId == null }.mapNotNull { it.uid }.toSet()
        return found.filter { it.recurrenceId == null || it.uid !in series }
    }

    /** Long lines are cut with a line break followed by a space or tab: join them again. */
    private fun unfold(text: String): List<String> {
        val lines = ArrayList<String>()
        val current = StringBuilder()
        for (raw in text.removePrefix("\uFEFF").lineSequence()) {
            if (raw.startsWith(" ") || raw.startsWith("\t")) {
                current.append(raw, 1, raw.length)
            } else {
                if (current.isNotEmpty()) lines += current.toString()
                current.setLength(0)
                current.append(raw)
            }
        }
        if (current.isNotEmpty()) lines += current.toString()
        return lines
    }

    /** `NAME;PARAM=a;PARAM="b:c":value` */
    private fun property(line: String): Property? {
        var quoted = false
        var colon = -1
        val cuts = ArrayList<Int>()
        for (i in line.indices) {
            val c = line[i]
            if (c == '"') quoted = !quoted
            if (!quoted && c == ';') cuts += i
            if (!quoted && c == ':') {
                colon = i
                break
            }
        }
        if (colon <= 0) return null
        val head = line.substring(0, colon)
        val name = head.substring(0, cuts.firstOrNull() ?: head.length).trim().uppercase()
        val params = HashMap<String, String>()
        cuts.take(16).forEachIndexed { n, from ->
            val part = head.substring(from + 1, cuts.getOrNull(n + 1) ?: head.length)
            val eq = part.indexOf('=')
            if (eq > 0) params[part.substring(0, eq).uppercase()] = part.substring(eq + 1).trim().removeSurrounding("\"")
        }
        return Property(name, params, line.substring(colon + 1))
    }

    private fun unescape(value: String): String {
        if ('\\' !in value) return value
        val out = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                val next = value[++i]
                out.append(if (next == 'n' || next == 'N') '\n' else next)
            } else {
                out.append(c)
            }
            i++
        }
        return out.toString()
    }

    private fun build(event: Event, method: String, zones: Map<String, List<Observance>>, zone: ZoneId): IcsEvent? {
        fun text(name: String, max: Int) = event.props[name]?.value?.let(::unescape)?.trim()?.take(max).orEmpty()
        val start = event.props["DTSTART"] ?: return null
        val allDay = isDate(start)
        val startMillis = millis(start, zones, zone) ?: return null
        val endMillis = event.props["DTEND"]?.let { millis(it, zones, zone) }
            ?: event.props["DURATION"]?.let { duration(it.value) }?.let { startMillis + it }
            ?: startMillis
        // Without a usable end: a whole day, or one hour.
        val end = if (endMillis > startMillis) endMillis else startMillis + if (allDay) DAY else HOUR
        return IcsEvent(
            title = text("SUMMARY", MAX_SHORT).replace('\n', ' '),
            location = text("LOCATION", MAX_SHORT).replace('\n', ' '),
            description = text("DESCRIPTION", MAX_LONG),
            startMillis = startMillis,
            endMillis = end,
            allDay = allDay,
            rrule = event.props["RRULE"]?.value?.trim()?.takeIf { it.length <= MAX_SHORT && it.contains("FREQ=", true) },
            uid = event.props["UID"]?.value?.trim()?.takeIf { it.isNotEmpty() },
            recurrenceId = event.props["RECURRENCE-ID"]?.value?.trim(),
            cancelled = method == "CANCEL" || event.props["STATUS"]?.value?.trim().equals("CANCELLED", true),
        )
    }

    private const val HOUR = 3_600_000L
    private const val DAY = 24 * HOUR
    private val DATE = Regex("""(\d{4})(\d{2})(\d{2})""")
    private val DATE_TIME = Regex("""(\d{4})(\d{2})(\d{2})T(\d{2})(\d{2})(\d{2})?(Z)?""")
    private val DURATION = Regex("""\+?P(?:(\d{1,4})W)?(?:(\d{1,5})D)?(?:T(?:(\d{1,6})H)?(?:(\d{1,6})M)?(?:(\d{1,6})S)?)?""")

    private fun isDate(p: Property) = p.params["VALUE"].equals("DATE", true) || DATE.matches(p.value.trim())

    private fun millis(p: Property, zones: Map<String, List<Observance>>, phone: ZoneId): Long? = try {
        val value = p.value.trim()
        if (isDate(p)) {
            val (y, m, d) = DATE.matchEntire(value.take(8))!!.destructured
            LocalDate.of(y.toInt(), m.toInt(), d.toInt()).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        } else {
            val match = DATE_TIME.matchEntire(value)!!
            val n = match.groupValues.drop(1).take(6).map { it.toIntOrNull() ?: 0 }
            val local = LocalDateTime.of(n[0], n[1], n[2], n[3], n[4], minOf(n[5], 59))
            val at = if (match.groupValues[7] == "Z") ZoneOffset.UTC else zoneOf(p.params["TZID"], local, zones, phone)
            local.atZone(at).toInstant().toEpochMilli()
        }
    } catch (_: Exception) {
        null
    }

    private fun duration(value: String): Long? {
        val m = DURATION.matchEntire(value.trim()) ?: return null
        val (w, d, h, min, s) = m.destructured
        fun n(x: String) = x.toLongOrNull() ?: 0L
        return (n(w) * 7 * 24 * 3600 + n(d) * 24 * 3600 + n(h) * 3600 + n(min) * 60 + n(s)) * 1000
    }

    private val IANA = Regex("""(Africa|America|Antarctica|Asia|Atlantic|Australia|Europe|Indian|Pacific|Etc)/[A-Za-z0-9_+\-/]+""")

    /** The zone names Outlook and Exchange write instead of the standard ones (the usual ones). */
    private val WINDOWS = mapOf(
        "Romance Standard Time" to "Europe/Paris", "W. Europe Standard Time" to "Europe/Berlin",
        "Central Europe Standard Time" to "Europe/Budapest", "Central European Standard Time" to "Europe/Warsaw",
        "GMT Standard Time" to "Europe/London", "Greenwich Standard Time" to "Atlantic/Reykjavik",
        "E. Europe Standard Time" to "Europe/Chisinau", "FLE Standard Time" to "Europe/Kiev",
        "GTB Standard Time" to "Europe/Bucharest", "Russian Standard Time" to "Europe/Moscow",
        "Turkey Standard Time" to "Europe/Istanbul", "Israel Standard Time" to "Asia/Jerusalem",
        "South Africa Standard Time" to "Africa/Johannesburg", "Arabian Standard Time" to "Asia/Dubai",
        "India Standard Time" to "Asia/Kolkata", "China Standard Time" to "Asia/Shanghai",
        "Singapore Standard Time" to "Asia/Singapore", "Tokyo Standard Time" to "Asia/Tokyo",
        "Korea Standard Time" to "Asia/Seoul", "AUS Eastern Standard Time" to "Australia/Sydney",
        "New Zealand Standard Time" to "Pacific/Auckland", "Eastern Standard Time" to "America/New_York",
        "Central Standard Time" to "America/Chicago", "Mountain Standard Time" to "America/Denver",
        "US Mountain Standard Time" to "America/Phoenix", "Pacific Standard Time" to "America/Los_Angeles",
        "Alaskan Standard Time" to "America/Anchorage", "Hawaiian Standard Time" to "Pacific/Honolulu",
        "Atlantic Standard Time" to "America/Halifax", "SA Pacific Standard Time" to "America/Bogota",
        "Central Standard Time (Mexico)" to "America/Mexico_City", "Argentina Standard Time" to "America/Buenos_Aires",
        "E. South America Standard Time" to "America/Sao_Paulo",
    )

    private fun zoneOf(tzid: String?, local: LocalDateTime, zones: Map<String, List<Observance>>, phone: ZoneId): ZoneId {
        val id = tzid?.trim().orEmpty()
        if (id.isEmpty()) return phone
        known(id)?.let { return it }
        // An invented name: the file itself says its offsets.
        val parts = zones[id].orEmpty().filter { it.offset != null }
        val standard = parts.lastOrNull { !it.daylight }
        val daylight = parts.lastOrNull { it.daylight }
        if (standard == null) return daylight?.offset ?: phone
        val winterOffset = standard.offset ?: return phone
        if (daylight == null) return winterOffset
        val summer = change(daylight, local.year) ?: return winterOffset
        val winter = change(standard, local.year) ?: return winterOffset
        val inSummer = if (summer < winter) local >= summer && local < winter else local >= summer || local < winter
        return if (inSummer) daylight.offset ?: winterOffset else winterOffset
    }

    private fun known(id: String): ZoneId? {
        for (name in listOfNotNull(id, IANA.find(id)?.value, WINDOWS[id])) {
            try {
                return ZoneId.of(name)
            } catch (_: Exception) {
                // not a name Java knows: try the next form
            }
        }
        return null
    }

    private fun readObservance(o: Observance, p: Property) {
        val value = p.value.trim()
        when (p.name) {
            "TZOFFSETTO" -> o.offset = try { ZoneOffset.of(value.take(5)) } catch (_: Exception) { null }
            "DTSTART" -> DATE_TIME.matchEntire(value)?.let { o.hour = it.groupValues[4].toInt().coerceIn(0, 23) }
            "RRULE" -> {
                val rule = value.split(';').associate { it.substringBefore('=').uppercase() to it.substringAfter('=') }
                o.month = rule["BYMONTH"]?.toIntOrNull()?.takeIf { it in 1..12 }
                val byDay = rule["BYDAY"] ?: return
                o.week = byDay.dropLast(2).toIntOrNull() ?: 1
                o.day = DayOfWeek.entries.firstOrNull { it.name.startsWith(byDay.takeLast(2).uppercase()) }
            }
        }
    }

    /** When [o] starts in [year] (for example the last Sunday of March at 2). */
    private fun change(o: Observance, year: Int): LocalDateTime? {
        val month = o.month ?: return null
        val day = o.day ?: return null
        if (o.week !in -5..5 || o.week == 0) return null
        return LocalDate.of(year, month, 1).with(TemporalAdjusters.dayOfWeekInMonth(o.week, day)).atTime(o.hour, 0)
    }
}
