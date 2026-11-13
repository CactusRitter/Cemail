package com.cemail.app.data.auth

import net.openid.appauth.AuthorizationException
import java.io.IOException

/**
 * Why a token refresh did not give a token. Only an answer from the server that rejects the saved sign-in means the
 * user has to sign in again; a failed connection (no network for a moment, no DNS, a timeout) is not that, and used to
 * be reported as "not signed in" all the same, which was wrong and alarming.
 */
object TokenFailure {
    private val SIGN_IN_AGAIN = setOf("invalid_grant", "interaction_required", "consent_required", "invalid_client", "unauthorized_client")

    /** true when the provider's [error] code says the saved sign-in is no longer valid. */
    fun mustSignInAgain(error: String?): Boolean = error?.lowercase() in SIGN_IN_AGAIN

    /** null: the user must sign in again. Otherwise an [IOException] to throw: it is a connection problem. */
    fun of(exception: AuthorizationException?): IOException? =
        if (exception != null && exception.type == AuthorizationException.TYPE_OAUTH_TOKEN_ERROR && mustSignInAgain(exception.error)) {
            null
        } else {
            IOException("token refresh failed: ${exception?.error ?: exception?.errorDescription ?: "no answer"}", exception)
        }
}
