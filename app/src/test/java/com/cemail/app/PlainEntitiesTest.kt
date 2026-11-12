package com.cemail.app

import com.cemail.app.data.imap.decodeEntities
import org.junit.Assert.assertEquals
import org.junit.Test

class PlainEntitiesTest {
    @Test
    fun entitiesInPlainTextAreDecoded() {
        assertEquals("  ----- Weitergeleitete Nachricht", decodeEntities("&nbsp; ----- Weitergeleitete Nachricht"))
        assertEquals("die AMS-Bestätigung", decodeEntities("die AMS-Best&auml;tigung"))
        assertEquals("€ 5", decodeEntities("&#8364; 5"))
    }

    @Test
    fun ordinaryTextIsKept() {
        assertEquals("R&D and Tom & Jerry", decodeEntities("R&D and Tom & Jerry"))
    }
}
