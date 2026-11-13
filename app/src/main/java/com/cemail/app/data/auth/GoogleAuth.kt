package com.cemail.app.data.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import com.cemail.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.GrantTypeValues
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenRequest
import net.openid.appauth.TokenResponse
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Who just signed in, plus the tokens Google gave us. */
data class SignInResult(val accountId: String, val email: String, val name: String?, val tokens: Tokens)

sealed interface SignInOutcome {
    data class Success(val result: SignInResult) : SignInOutcome
    data object Cancelled : SignInOutcome
    data object NotConfigured : SignInOutcome
    data class Failed(val reason: String) : SignInOutcome
}

/**
 * "Sign in with Google" with AppAuth (system browser, authorization code + PKCE, no client secret).
 * The Android OAuth client is tied to the package name and the signing certificate (docs/OAUTH_SETUP.md).
 */
class GoogleAuth(context: Context) {
    private val service = AuthorizationService(context.applicationContext)
    private val config = AuthorizationServiceConfiguration(
        Uri.parse("https://accounts.google.com/o/oauth2/v2/auth"),
        Uri.parse("https://oauth2.googleapis.com/token"),
    )
    private val clientId = BuildConfig.GOOGLE_OAUTH_CLIENT_ID
    private val redirectUri = Uri.parse("${redirectScheme(clientId)}:/oauth2redirect")

    val isConfigured: Boolean get() = clientId.isNotBlank()

    /** Intent that opens Google's sign-in page; deliver the result to [handleResult]. */
    fun signInIntent(): Intent? {
        if (!isConfigured) return null
        val request = AuthorizationRequest.Builder(config, clientId, ResponseTypeValues.CODE, redirectUri)
            .setScope(SCOPES)
            .build()
        return service.getAuthorizationRequestIntent(request)
    }

    suspend fun handleResult(data: Intent?): SignInOutcome = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext SignInOutcome.NotConfigured
        val intent = data ?: return@withContext SignInOutcome.Cancelled
        val error = AuthorizationException.fromIntent(intent)
        val response = AuthorizationResponse.fromIntent(intent)
        if (response == null) {
            return@withContext if (error == null || error == AuthorizationException.GeneralErrors.USER_CANCELED_AUTH_FLOW) {
                SignInOutcome.Cancelled
            } else {
                SignInOutcome.Failed(error.errorDescription ?: error.error ?: "error ${error.code}")
            }
        }
        val token = try { exchange(response.createTokenExchangeRequest()) } catch (e: java.io.IOException) { null }
            ?: return@withContext SignInOutcome.Failed("token")
        val claims = token.idToken?.let(::parseIdToken)
        val sub = claims?.optString("sub").orEmpty()
        val email = claims?.optString("email").orEmpty()
        if (sub.isEmpty() || email.isEmpty()) return@withContext SignInOutcome.Failed("id_token")
        SignInOutcome.Success(
            SignInResult(
                accountId = sub,
                email = email,
                name = claims?.optString("name")?.takeIf { it.isNotEmpty() },
                tokens = Tokens(token.refreshToken, token.accessToken, token.accessTokenExpirationTime ?: 0L),
            ),
        )
    }

    /** Returns a valid access token, refreshing it when it is about to expire; null if the user must sign in again. */
    suspend fun freshAccessToken(tokens: Tokens): Tokens? = withContext(Dispatchers.IO) {
        if (tokens.accessToken != null && tokens.expiresAtMillis - System.currentTimeMillis() > 60_000) return@withContext tokens
        val refresh = tokens.refreshToken ?: return@withContext null
        val request = TokenRequest.Builder(config, clientId)
            .setGrantType(GrantTypeValues.REFRESH_TOKEN)
            .setRefreshToken(refresh)
            .build()
        val response = exchange(request) ?: return@withContext null
        Tokens(response.refreshToken ?: refresh, response.accessToken, response.accessTokenExpirationTime ?: 0L)
    }

    /** The answer to a token request: null when the server says the saved sign-in is no longer valid; a connection problem throws ([TokenFailure]). */
    private suspend fun exchange(request: TokenRequest): TokenResponse? = suspendCancellableCoroutine { cont ->
        service.performTokenRequest(request) { response, error ->
            if (response != null) {
                cont.resume(response)
            } else {
                val failure = TokenFailure.of(error)
                if (failure == null) cont.resume(null) else cont.resumeWithException(failure)
            }
        }
    }

    companion object {
        const val SCOPES = "openid email profile https://www.googleapis.com/auth/gmail.modify"

        /** Google's rule for Android clients: the redirect scheme is the client id reversed. */
        fun redirectScheme(clientId: String) =
            "com.googleusercontent.apps." + clientId.removeSuffix(".apps.googleusercontent.com")

        /** Reads the claims of a JWT (we only need who the user is; Google signed it over TLS just now). */
        fun parseIdToken(jwt: String): JSONObject? = try {
            val payload = jwt.split(".")[1]
            JSONObject(String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP), Charsets.UTF_8))
        } catch (_: Exception) {
            null
        }
    }
}
