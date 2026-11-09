package com.cemail.app

import com.cemail.app.data.mail.Mail
import com.cemail.app.data.mail.matches
import com.cemail.app.ui.mail.SnoozeOption
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MailLogicTest {
    private val mail = Mail(
        id = 1, senderName = "Lucia Ortega", senderEmail = "lucia@example.com",
        subject = "Photos", body = "Lake photos from the hike", timeMillis = 0,
        read = false, hasAttachment = true,
    )

    @Test fun searchMatchesFreeWordsAnywhere() {
        assertTrue(mail.matches("lake"))
        assertTrue(mail.matches("LUCIA photos"))
        assertFalse(mail.matches("invoice"))
    }

    @Test fun searchOperators() {
        assertTrue(mail.matches("from:lucia is:unread has:attachment"))
        assertFalse(mail.matches("is:starred"))
        assertFalse(mail.matches("from:mateo"))
    }

    @Test fun snoozeOptionsAreInTheFutureAtTheRightHour() {
        val now = System.currentTimeMillis()
        SnoozeOption.entries.forEach { assertTrue(it.at(now) > now) }
        val c = Calendar.getInstance().apply { timeInMillis = SnoozeOption.NEXT_WEEK.at(now) }
        assertEquals(Calendar.MONDAY, c.get(Calendar.DAY_OF_WEEK))
        assertEquals(8, c.get(Calendar.HOUR_OF_DAY))
        val w = Calendar.getInstance().apply { timeInMillis = SnoozeOption.WEEKEND.at(now) }
        assertEquals(Calendar.SATURDAY, w.get(Calendar.DAY_OF_WEEK))
    }

    @Test fun snoozedMailIsOnlyHiddenUntilItsTime() {
        val m = mail.copy(snoozedUntil = 1_000)
        assertTrue(m.isSnoozed(500))
        assertFalse(m.isSnoozed(1_500))
    }
}
