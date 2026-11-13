package com.cemail.app

import com.cemail.app.data.auth.TokenFailure
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenFailureTest {
    @Test
    fun aRejectedSignInMeansSigningInAgain() {
        assertTrue(TokenFailure.mustSignInAgain("invalid_grant"))
        assertTrue(TokenFailure.mustSignInAgain("INVALID_GRANT"))
        assertTrue(TokenFailure.mustSignInAgain("interaction_required"))
    }

    @Test
    fun connectionProblemsAndServerHiccupsDoNot() {
        assertFalse(TokenFailure.mustSignInAgain(null)) // no answer at all: no network
        assertFalse(TokenFailure.mustSignInAgain("temporarily_unavailable"))
        assertFalse(TokenFailure.mustSignInAgain("server_error"))
        assertFalse(TokenFailure.mustSignInAgain(""))
    }
}
