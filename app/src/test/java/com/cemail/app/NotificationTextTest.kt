package com.cemail.app

import com.cemail.app.notify.NotificationText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationTextTest {
    @Test fun newsletterBoilerplateAndAddressesAreGone() {
        val out = NotificationText.clean(
            "Nicht verpassen: Black Friday bei Jacques Lemans!\n" +
                "Webansicht / Webview <https://tdc512382.emailsys2a.net/123/4567/abcdef/view.html>\n" +
                "----------\n" +
                "Bis zu 50% auf alle Uhren.\n" +
                "==========\n" +
                "Abmelden <https://tdc512382.emailsys2a.net/unsub>",
        )
        assertEquals("Nicht verpassen: Black Friday bei Jacques Lemans! Bis zu 50% auf alle Uhren.", out)
    }

    @Test fun markdownLinksKeepOnlyTheirText() {
        val out = NotificationText.clean(
            "[RENPHO](https://renpho.eu) [Rise and shop](https://renpho.eu/rise?utm=1)\n" +
                "![logo](https://img.example/a.png)\n" +
                "**Black Friday** deals are live: save up to 40% on scales.",
        )
        assertEquals("RENPHO Rise and shop Black Friday deals are live: save up to 40% on scales.", out)
        assertFalse(out.contains("http"))
        assertFalse(out.contains("]("))
    }

    @Test fun plainTextKeepsItsSentencesAndBareUrlsGo() {
        val out = NotificationText.clean("Hola Ana,\r\n\r\nmira esto https://x.example/a?b=1 y dime\r\nsi te parece bien.\r\n\r\n> \r\nUn abrazo")
        assertEquals("Hola Ana, mira esto y dime si te parece bien. Un abrazo", out)
    }

    @Test fun onlyBoilerplateLeavesNothingSoTheCallerFallsBackToTheSubject() {
        assertEquals("", NotificationText.clean("View in browser <https://x.example/v>\n-----\n[image: logo]"))
    }

    @Test fun longTextIsCut() {
        val out = NotificationText.clean("palabra ".repeat(200), max = 50)
        assertTrue(out.length <= 51 && out.endsWith("…"))
    }
}
