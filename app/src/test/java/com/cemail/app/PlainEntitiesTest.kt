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

class PlainHtmlTest {
    @Test
    fun htmlInThePlainPartBecomesText() {
        val html = "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0\"><html><head><style>p{color:red}</style></head><body><p>Rechnung für deine Bestellung</p></body></html>"
        org.junit.Assert.assertEquals("Rechnung für deine Bestellung", com.cemail.app.data.imap.readablePlain(html))
        org.junit.Assert.assertEquals("Hola", com.cemail.app.data.imap.readablePlain("<span class=\"preheader\">Hola</span>"))
    }

    @Test
    fun plainTextWithASignIsKept() {
        org.junit.Assert.assertEquals("2 < 3 and 5 > 4", com.cemail.app.data.imap.readablePlain("2 < 3 and 5 > 4"))
    }
}
