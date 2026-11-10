package com.cemail.app.data.mail

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** The groups the message list can be split into. "Older" is everything more than a week old. */
enum class DateSection { TODAY, YESTERDAY, LAST_WEEK, OLDER }

/** Which section a message belongs to, by calendar days (not by 24-hour periods). */
fun dateSectionOf(timeMillis: Long, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): DateSection {
    val day = Instant.ofEpochMilli(timeMillis).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    return when (ChronoUnit.DAYS.between(day, today)) {
        in Long.MIN_VALUE..0L -> DateSection.TODAY
        1L -> DateSection.YESTERDAY
        in 2L..7L -> DateSection.LAST_WEEK
        else -> DateSection.OLDER
    }
}
