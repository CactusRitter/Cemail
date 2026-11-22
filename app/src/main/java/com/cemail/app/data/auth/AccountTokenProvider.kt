package com.cemail.app.data.auth

import com.ce.kit.auth.TokenProvider
import com.ce.kit.auth.TokenStore
import com.ce.kit.auth.Tokens
import com.cemail.app.data.gmail.AccessTokenProvider

/**
 * Gives an API client a valid access token for one account, refreshing and re-saving it when needed (the work is
 * the kit's [TokenProvider]). [refresh] is the provider's own refresh (Google's or Microsoft's); null means the user
 * must sign in again.
 */
class AccountTokenProvider(
    private val accountId: String,
    private val store: TokenStore,
    private val refresh: suspend (Tokens) -> Tokens?,
) : AccessTokenProvider {
    override suspend fun accessToken(): String? = TokenProvider(store).accessToken(accountId, refresh = refresh)
}

/** Where the picture of this account comes from (null for IMAP accounts: they have none). */
fun com.cemail.app.data.db.AccountEntity.photoProvider(): com.ce.kit.auth.PhotoProvider? = when (provider) {
    "GMAIL" -> com.ce.kit.auth.PhotoProvider.GOOGLE
    "MICROSOFT" -> com.ce.kit.auth.PhotoProvider.MICROSOFT
    else -> null
}
