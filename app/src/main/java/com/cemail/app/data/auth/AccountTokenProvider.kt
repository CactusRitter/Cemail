package com.cemail.app.data.auth

import com.cemail.app.data.gmail.AccessTokenProvider

/** Gives the Gmail client a valid access token for one account, refreshing and re-saving it when needed. */
class AccountTokenProvider(
    private val accountId: String,
    private val store: TokenStore,
    private val auth: GoogleAuth,
) : AccessTokenProvider {
    override suspend fun accessToken(): String? {
        val current = store.load(accountId) ?: return null
        val fresh = auth.freshAccessToken(current) ?: return null
        if (fresh !== current) store.save(accountId, fresh)
        return fresh.accessToken
    }
}
