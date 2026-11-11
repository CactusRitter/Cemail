package com.cemail.app.data

import com.cemail.app.sync.SyncWorker
import org.junit.Assert.assertEquals
import org.junit.Test

class PerAccountSettingsTest {
    @Test fun accountFollowsTheGeneralIntervalUnlessItHasItsOwn() {
        val s = AppSettings(syncMinutes = 30, accountSync = mapOf("a" to 60, "b" to SyncWorker.MANUAL))
        assertEquals(60, s.syncMinutesFor("a"))
        assertEquals(SyncWorker.MANUAL, s.syncMinutesFor("b"))
        assertEquals(30, s.syncMinutesFor("c"))
    }

    @Test fun backgroundCheckWakesAtTheShortestOwnInterval() {
        val s = AppSettings(syncMinutes = 120, accountSync = mapOf("a" to 30, "b" to SyncWorker.MANUAL))
        assertEquals(30, SyncWorker.periodFor(s, listOf("a", "b", "c")))
        // Only manual accounts: the background check is off.
        assertEquals(SyncWorker.MANUAL, SyncWorker.periodFor(s, listOf("b")))
        // Without accounts the general interval applies.
        assertEquals(120, SyncWorker.periodFor(s, emptyList()))
    }

    @Test fun swipeDistancesGrow() {
        assertEquals(true, SwipeDistance.SHORT.fraction < SwipeDistance.NORMAL.fraction)
        assertEquals(true, SwipeDistance.NORMAL.fraction < SwipeDistance.LONG.fraction)
        assertEquals(SwipeAction.ARCHIVE, SwipeConfig().right)
        assertEquals(SwipeAction.TRASH, SwipeConfig().left)
    }
}
