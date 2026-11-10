package com.cemail.app

import com.cemail.app.data.mail.Unsubscribe
import com.cemail.app.data.mail.ftsQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchAndUnsubscribeTest {
    @Test
    fun everyWordIsRequiredAndMatchedByItsStart() {
        assertEquals("factura* mayo*", ftsQuery("factura mayo"))
    }

    @Test
    fun symbolsOfTheQueryLanguageAreRemoved() {
        assertEquals("hola* mundo*", ftsQuery("  \"hola\"  mundo* - "))
        assertEquals("ana* 2026*", ftsQuery("ana, 2026!"))
        assertNull(ftsQuery("   "))
        assertNull(ftsQuery("\"*-"))
    }

    @Test
    fun gmailStyleOperatorsAreLeftToTheListFilter() {
        assertNull(ftsQuery("from:ana factura"))
        assertNull(ftsQuery("is:unread"))
    }

    @Test
    fun accentedWordsKeepTheirLetters() {
        assertEquals("canción*", ftsQuery("canción"))
    }

    @Test
    fun oneClickListsAreRecognised() {
        val info = Unsubscribe.parse(
            "<https://lista.example/u?id=1>, <mailto:baja@lista.example?subject=Baja>",
            "List-Unsubscribe=One-Click",
        )
        assertNotNull(info)
        assertTrue(info!!.oneClick)
        assertEquals("https://lista.example/u?id=1", info.https)
        assertEquals("baja@lista.example", info.mailto)
        assertEquals("Baja", info.mailtoSubject)
    }

    @Test
    fun withoutThePostHeaderItIsNotOneClick() {
        val info = Unsubscribe.parse("<https://lista.example/u>", null)!!
        assertFalse(info.oneClick)
        assertEquals("https://lista.example/u", info.https)
        assertNull(info.mailto)
    }

    @Test
    fun onlyHttpsAndMailtoAreAccepted() {
        assertNull(Unsubscribe.parse("<http://insecure.example/u>", null))
        assertNull(Unsubscribe.parse("<javascript:alert(1)>", null))
        assertNull(Unsubscribe.parse(null, null))
        val mailOnly = Unsubscribe.parse("<mailto:baja@lista.example>", null)!!
        assertEquals("baja@lista.example", mailOnly.mailto)
        assertNull(mailOnly.https)
    }
}
