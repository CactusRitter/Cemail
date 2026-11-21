package com.cemail.app.data.auth

import com.cemail.app.data.gmail.AccessTokenProvider

/**
 * Gives an API client a valid access token for one account, refreshing and re-saving it when needed.
 * [refresh] is the provider's own refresh (Google's or Microsoft's); null means the user must sign in again.
 */
class AccountTokenProvider(
    private val accountId: String,
    private val store: TokenStore,
    private val refresh: suspend (Tokens) -> Tokens?,
) : AccessTokenProvider {
    override suspend fun accessToken(): String? {
        val current = store.load(accountId) ?: return null
        val fresh = refresh(current) ?: return null
        if (fresh !== current) store.save(accountId, fresh)
        return fresh.accessToken
    }
}
