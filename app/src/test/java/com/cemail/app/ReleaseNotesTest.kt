package com.cemail.app

import com.cemail.app.update.ReleaseNotes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseNotesTest {
    private val log = "## 2.0.1\n- B\n\n## 2.0.0\n- A\n- A2\n"
    private val body = "## en\n- English\n\n## es\n- Español\n"

    @Test
    fun findsTheNotesOfAVersion() {
        assertEquals("- A\n- A2", ReleaseNotes.forVersion(log, "2.0.0"))
        assertNull(ReleaseNotes.forVersion(log, "9.9.9"))
        assertEquals(listOf("2.0.1", "2.0.0"), ReleaseNotes.all(log).map { it.first })
    }

    @Test
    fun picksTheLanguageOfAReleaseBody() {
        assertEquals("- Español", ReleaseNotes.forLanguage(body, "es"))
        assertEquals("- English", ReleaseNotes.forLanguage(body, "de")) // not there: English
        assertEquals("plain text", ReleaseNotes.forLanguage("plain text", "es"))
    }
}
