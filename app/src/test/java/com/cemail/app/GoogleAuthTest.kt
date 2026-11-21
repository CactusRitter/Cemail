package com.cemail.app

import com.ce.kit.auth.GoogleAuth
import org.junit.Assert.assertEquals
import org.junit.Test

class GoogleAuthTest {
    @Test fun redirectSchemeIsTheClientIdReversedTheGoogleWay() {
        assertEquals(
            "com.googleusercontent.apps.123-abc",
            GoogleAuth.redirectScheme("123-abc.apps.googleusercontent.com"),
        )
    }

    @Test fun scopesIncludeGmailModifyAndIdentity() {
        val scopes = com.cemail.app.data.auth.AuthConfig.GOOGLE_SCOPES.split(" ")
        assert("https://www.googleapis.com/auth/gmail.modify" in scopes)
        assert("openid" in scopes && "email" in scopes)
    }
}
