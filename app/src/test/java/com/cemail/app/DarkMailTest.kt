package com.cemail.app

import com.cemail.app.data.gmail.HtmlSanitizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DarkMailTest {
    @Test
    fun lightOnlyCssIsRemoved() {
        val page = ":root { color-scheme: light !important; supported-color-schemes: light only; }"
        val out = HtmlSanitizer.allowDark(page)
        assertFalse(out.contains("light"))
        assertEquals(":root { color-scheme: normal; color-scheme: normal; }", out)
    }

    @Test
    fun lightOnlyMetaIsRemoved() {
        val page = "<meta name=\"color-scheme\" content=\"light only\"><meta name=\"supported-color-schemes\" content=\"light\"><p>Hi</p>"
        assertEquals("<p>Hi</p>", HtmlSanitizer.allowDark(page))
    }

    @Test
    fun lightDarkWithoutDarkRulesIsRemoved() {
        val page = "<meta name=\"color-scheme\" content=\"light dark\"><style>:root{color-scheme: light dark}</style><p>Hi</p>"
        assertEquals("<style>:root{color-scheme: normal}</style><p>Hi</p>", HtmlSanitizer.allowDark(page))
    }

    @Test
    fun ownDarkRulesAreKept() {
        val rules = "@media (prefers-color-scheme: light) { p{color:#000} } @media (prefers-color-scheme: dark) { p{color:#fff} }"
        val page = "<meta name=\"color-scheme\" content=\"light dark\"><style>:root{color-scheme: light dark}$rules</style>"
        val out = HtmlSanitizer.allowDark(page)
        assertEquals("<style>:root{color-scheme: normal}$rules</style>", out)
        assertTrue(out.contains("prefers-color-scheme: dark"))
    }
}
