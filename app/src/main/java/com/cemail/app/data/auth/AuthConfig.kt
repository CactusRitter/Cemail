package com.cemail.app.data.auth

import com.cemail.app.BuildConfig

/** Cemail's values for the shared sign-in code in com.ce.kit.auth. Do not change alias/prefs: it would sign everybody out. */
object AuthConfig {
    const val GOOGLE_SCOPES = "openid email profile https://www.googleapis.com/auth/gmail.modify"
    const val MICROSOFT_SCOPES = "openid profile email offline_access " +
        "https://graph.microsoft.com/Mail.ReadWrite https://graph.microsoft.com/Mail.Send https://graph.microsoft.com/User.Read"
    const val MICROSOFT_REDIRECT = "com.cemail.app://oauth2redirect"
    const val TOKEN_ALIAS = "cemail_tokens"
    const val TOKEN_PREFS = "tokens"

    fun googleClientId(): String = BuildConfig.GOOGLE_OAUTH_CLIENT_ID
    fun microsoftClientId(): String = BuildConfig.MICROSOFT_OAUTH_CLIENT_ID
}
