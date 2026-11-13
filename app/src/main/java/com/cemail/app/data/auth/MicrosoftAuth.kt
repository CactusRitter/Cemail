package com.cemail.app.data.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * "Sign in with Microsoft" (Outlook.com, Hotmail, Microsoft 365) with AppAuth: system browser, authorization code +
 * PKCE, no client secret. The app is registered in Azure as a public client for personal and work accounts.
 */
class MicrosoftAuth(context: Context) {
    private val service = AuthorizationService(context.applicationContext)
    private val config = AuthorizationServiceConfiguration(
        Uri.parse("https://login.microsoftonline.com/common/oauth2/v2.0/authorize"),
        Uri.parse("https://login.microsoftonline.com/common/oauth2/v2.0/token"),
    )
    private val clientId = BuildConfig.MICROSOFT_OAUTH_CLIENT_ID
    private val redirectUri = Uri.parse(REDIRECT)

    val isConfigured: Boolean get() = clientId.isNotBlank()

    fun signInIntent(): Intent? {
        if (!isConfigured) return null
        val request = AuthorizationRequest.Builder(config, clientId, ResponseTypeValues.CODE, redirectUri)
            .setScope(SCOPES)
            .setPrompt(AuthorizationRequest.Prompt.SELECT_ACCOUNT)
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
        val token = (try { exchange(response.createTokenExchangeRequest()) } catch (e: java.io.IOException) { null })
            ?: return@withContext SignInOutcome.Failed("token")
        val claims = token.idToken?.let(GoogleAuth::parseIdToken)
        // "oid" identifies the user in the directory; personal accounts only carry "sub".
        val id = claims?.optString("oid").orEmpty().ifEmpty { claims?.optString("sub").orEmpty() }
        val email = claims?.optString("email").orEmpty().ifEmpty { claims?.optString("preferred_username").orEmpty() }
        if (id.isEmpty() || email.isEmpty()) return@withContext SignInOutcome.Failed("id_token")
        SignInOutcome.Success(
            SignInResult(
                accountId = "ms:$id",
                email = email,
                name = claims?.optString("name")?.takeIf { it.isNotEmpty() },
                tokens = Tokens(token.refreshToken, token.accessToken, token.accessTokenExpirationTime ?: 0L),
            ),
        )
    }

    /** A valid access token, refreshed when it is about to expire; null if the user must sign in again. */
    suspend fun freshAccessToken(tokens: Tokens): Tokens? = withContext(Dispatchers.IO) {
        if (tokens.accessToken != null && tokens.expiresAtMillis - System.currentTimeMillis() > 60_000) return@withContext tokens
        val refresh = tokens.refreshToken ?: return@withContext null
        val request = TokenRequest.Builder(config, clientId)
            .setGrantType(GrantTypeValues.REFRESH_TOKEN)
            .setRefreshToken(refresh)
            .setScope(SCOPES)
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
        const val REDIRECT = "com.cemail.app://oauth2redirect"
        const val SCOPES = "openid profile email offline_access " +
            "https://graph.microsoft.com/Mail.ReadWrite https://graph.microsoft.com/Mail.Send https://graph.microsoft.com/User.Read"
    }
}
