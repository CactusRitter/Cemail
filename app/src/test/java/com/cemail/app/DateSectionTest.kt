package com.cemail.app

import com.cemail.app.data.mail.DateSection
import com.cemail.app.data.mail.dateSectionOf
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class DateSectionTest {
    private val zone = ZoneId.of("Europe/Madrid")
    private val now = ZonedDateTime.of(2026, 11, 10, 8, 0, 0, 0, zone)
    private fun section(daysAgo: Long, hour: Int = 12) =
        dateSectionOf(now.minusDays(daysAgo).withHour(hour).toInstant().toEpochMilli(), now.toInstant().toEpochMilli(), zone)

    @Test fun groupsByCalendarDay() {
        assertEquals(DateSection.TODAY, section(0, hour = 1))
        assertEquals(DateSection.YESTERDAY, section(1, hour = 23)) // 9 hours ago, but yesterday
        assertEquals(DateSection.LAST_WEEK, section(2))
        assertEquals(DateSection.LAST_WEEK, section(7))
        assertEquals(DateSection.OLDER, section(8))
        assertEquals(DateSection.OLDER, section(400))
    }

    @Test fun futureDatesCountAsToday() {
        assertEquals(DateSection.TODAY, dateSectionOf(now.plusHours(5).toInstant().toEpochMilli(), now.toInstant().toEpochMilli(), zone))
    }
}
