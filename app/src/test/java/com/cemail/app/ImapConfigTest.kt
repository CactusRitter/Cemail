package com.cemail.app

import com.cemail.app.data.imap.ImapConfig
import com.cemail.app.data.imap.ImapPresets
import com.cemail.app.data.imap.Security
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ImapConfigTest {
    @Test
    fun knownProvidersGetTheirServers() {
        val yahoo = ImapPresets.forEmail("ana@yahoo.es", "pw")!!
        assertEquals("imap.mail.yahoo.com", yahoo.imapHost)
        assertEquals(Security.SSL, yahoo.smtpSecurity)
        val icloud = ImapPresets.forEmail("ana@ICLOUD.com", "pw")!!
        assertEquals(587, icloud.smtpPort)
        assertEquals(Security.STARTTLS, icloud.smtpSecurity)
        assertEquals("ana@icloud.com".lowercase(), icloud.username.lowercase())
    }

    @Test
    fun unknownProviderIsNotInTheTableButCanBeGuessed() {
        assertNull(ImapPresets.forEmail("ana@miempresa.org", "pw"))
        val guess = ImapPresets.guess("ana@miempresa.org", "pw")
        assertEquals("imap.miempresa.org", guess.imapHost)
        assertEquals("smtp.miempresa.org", guess.smtpHost)
    }

    @Test
    fun settingsSurviveTheEncryptedJsonRoundTrip() {
        val config = ImapConfig(
            email = "a@b.c", username = "user", password = "p\"w\\ñ", imapHost = "i.b.c", imapPort = 143,
            imapSecurity = Security.STARTTLS, smtpHost = "s.b.c", smtpPort = 587, smtpSecurity = Security.STARTTLS,
            displayName = "Ana",
        )
        assertEquals(config, ImapConfig.fromJson(config.toJson()))
        assertNull(ImapConfig.fromJson("not json"))
        assertNotNull(ImapConfig.fromJson(config.copy(displayName = null).toJson()))
    }
}
