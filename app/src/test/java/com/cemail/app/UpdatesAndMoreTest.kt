package com.cemail.app

import com.cemail.app.data.mail.combineCounts
import com.cemail.app.update.Versions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatesAndMoreTest {
    @Test
    fun newerVersionsAreRecognised() {
        assertTrue(Versions.isNewer("2.0.0", "1.10.1"))
        assertTrue(Versions.isNewer("v1.10.2", "1.10.1"))
        assertTrue(Versions.isNewer("1.10.0", "1.9.9")) // by number, not by text
        assertTrue(Versions.isNewer("2.0.1", "2.0"))
    }

    @Test
    fun sameOrOlderOrOddVersionsAreNot() {
        assertFalse(Versions.isNewer("2.0.0", "2.0.0"))
        assertFalse(Versions.isNewer("2.0", "2.0.0"))
        assertFalse(Versions.isNewer("1.9.1", "1.10.0"))
        assertFalse(Versions.isNewer("latest", "1.0.0"))
    }

    @Test
    fun accountsAddUpAndFailOnlyWhenNothingCame() {
        assertEquals(7, combineCounts(listOf(3, 4)))
        assertEquals(3, combineCounts(listOf(3, -1))) // one account failed, but something came
        assertEquals(-1, combineCounts(listOf(0, -1)))
        assertEquals(0, combineCounts(listOf(0, 0)))
    }
}
