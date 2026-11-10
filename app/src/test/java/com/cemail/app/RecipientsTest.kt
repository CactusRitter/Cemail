package com.cemail.app

import com.cemail.app.ui.mail.cleanRecipients
import com.cemail.app.ui.mail.committedRecipients
import org.junit.Assert.assertEquals
import org.junit.Test

class RecipientsTest {
    @Test
    fun splitsOnCommasAndSemicolonsAndTrims() {
        assertEquals(listOf("a@x.com", "b@y.org", "c@z.net"), cleanRecipients(" a@x.com ,b@y.org; c@z.net, "))
        assertEquals(emptyList<String>(), cleanRecipients(" , ; "))
    }

    @Test
    fun committedRecipientsEndWithASeparatorSoNothingIsBeingTyped() {
        assertEquals("a@x.com, b@y.org, ", committedRecipients("a@x.com, b@y.org"))
        assertEquals("a@x.com, ", committedRecipients("a@x.com"))
        assertEquals("", committedRecipients(""))
    }
}
