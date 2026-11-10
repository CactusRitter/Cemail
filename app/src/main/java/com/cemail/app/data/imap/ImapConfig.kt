package com.cemail.app.data.imap

import org.json.JSONObject

/** How the connection to a server is protected. */
enum class Security { SSL, STARTTLS }

/** Everything needed to read and send mail for one IMAP account. The password is stored encrypted. */
data class ImapConfig(
    val email: String,
    val username: String,
    val password: String,
    val imapHost: String,
    val imapPort: Int = 993,
    val imapSecurity: Security = Security.SSL,
    val smtpHost: String,
    val smtpPort: Int = 465,
    val smtpSecurity: Security = Security.SSL,
    val displayName: String? = null,
) {
    fun toJson(): String = JSONObject()
        .put("email", email).put("username", username).put("password", password)
        .put("imapHost", imapHost).put("imapPort", imapPort).put("imapSecurity", imapSecurity.name)
        .put("smtpHost", smtpHost).put("smtpPort", smtpPort).put("smtpSecurity", smtpSecurity.name)
        .put("displayName", displayName ?: JSONObject.NULL)
        .toString()

    companion object {
        fun fromJson(text: String): ImapConfig? = try {
            val o = JSONObject(text)
            ImapConfig(
                email = o.getString("email"), username = o.getString("username"), password = o.getString("password"),
                imapHost = o.getString("imapHost"), imapPort = o.getInt("imapPort"),
                imapSecurity = Security.valueOf(o.getString("imapSecurity")),
                smtpHost = o.getString("smtpHost"), smtpPort = o.getInt("smtpPort"),
                smtpSecurity = Security.valueOf(o.getString("smtpSecurity")),
                displayName = o.optString("displayName").takeIf { it.isNotEmpty() && it != "null" },
            )
        } catch (_: Exception) {
            null
        }
    }
}

/** Server settings of well-known providers, found from the part of the address after the @. */
object ImapPresets {
    private class Preset(val imap: String, val smtp: String, val smtpPort: Int = 465, val smtpSecurity: Security = Security.SSL)

    private val byDomain: Map<String, Preset> = buildMap {
        fun add(preset: Preset, vararg domains: String) = domains.forEach { put(it, preset) }
        add(Preset("imap.mail.yahoo.com", "smtp.mail.yahoo.com"), "yahoo.com", "yahoo.es", "yahoo.de", "yahoo.fr", "yahoo.co.uk", "ymail.com", "rocketmail.com")
        add(Preset("imap.mail.me.com", "smtp.mail.me.com", 587, Security.STARTTLS), "icloud.com", "me.com", "mac.com")
        add(Preset("imap.gmx.net", "mail.gmx.net"), "gmx.net", "gmx.de", "gmx.at", "gmx.com", "gmx.es")
        add(Preset("imap.web.de", "smtp.web.de", 587, Security.STARTTLS), "web.de")
        add(Preset("imap.aol.com", "smtp.aol.com"), "aol.com")
        add(Preset("imap.zoho.com", "smtp.zoho.com"), "zoho.com", "zohomail.com")
        add(Preset("imap.fastmail.com", "smtp.fastmail.com"), "fastmail.com", "fastmail.fm")
        add(Preset("imap.mail.ovh.net", "ssl0.ovh.net"), "ovh.net")
        add(Preset("imap.1und1.de", "smtp.1und1.de", 587, Security.STARTTLS), "online.de", "1und1.de")
        add(Preset("imap.t-online.de", "securesmtp.t-online.de", 587, Security.STARTTLS), "t-online.de")
        add(Preset("imap.a1.net", "smtp.a1.net", 587, Security.STARTTLS), "a1.net", "aon.at")
    }

    /** A starting configuration for [email], or null when the provider is not known (the user fills it in). */
    fun forEmail(email: String, password: String): ImapConfig? {
        val domain = email.substringAfter('@', "").lowercase()
        val preset = byDomain[domain] ?: return null
        return ImapConfig(
            email = email, username = email, password = password,
            imapHost = preset.imap, smtpHost = preset.smtp, smtpPort = preset.smtpPort, smtpSecurity = preset.smtpSecurity,
        )
    }

    /** The usual guess for a custom domain: imap.<domain> and smtp.<domain>, both over SSL. */
    fun guess(email: String, password: String): ImapConfig {
        val domain = email.substringAfter('@', "").lowercase()
        return ImapConfig(email = email, username = email, password = password, imapHost = "imap.$domain", smtpHost = "smtp.$domain")
    }
}
