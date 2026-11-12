package com.cemail.app.ui.account

import android.content.Intent
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cemail.app.R
import com.cemail.app.data.orderedBy
import com.cemail.app.data.auth.AccountRepository
import com.cemail.app.data.auth.GoogleAuth
import com.cemail.app.data.auth.SignInOutcome
import com.cemail.app.data.db.AccountEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccountUiState(
    /** false until Room has answered, so the sign-in screen does not flash on start. */
    val loaded: Boolean = false,
    val accounts: List<AccountEntity> = emptyList(),
    val signingIn: Boolean = false,
    @StringRes val error: Int? = null,
    /** The user chose to look around with example messages instead of signing in. */
    val demo: Boolean = false,
    /** The account whose mail is shown, or null for all of them together (the unified inbox). */
    val selectedId: String? = null,
)

private data class Local(
    val signingIn: Boolean = false,
    val error: Int? = null,
    val demo: Boolean = false,
    val selectedId: String? = null,
)

class AccountViewModel(
    private val repo: AccountRepository,
    private val auth: GoogleAuth,
    private val microsoft: com.cemail.app.data.auth.MicrosoftAuth,
    /** The order the user chose for the accounts (Settings → Accounts). */
    private val order: kotlinx.coroutines.flow.Flow<List<String>> = kotlinx.coroutines.flow.flowOf(emptyList()),
) : ViewModel() {
    private val local = MutableStateFlow(Local())

    val state: StateFlow<AccountUiState> = combine(repo.accounts, local, order) { rawAccounts, l, ord ->
        val accounts = rawAccounts.orderedBy(ord) { it.id }
        // A removed account can no longer be the selected one.
        AccountUiState(true, accounts, l.signingIn, l.error, l.demo, l.selectedId?.takeIf { id -> accounts.any { it.id == id } })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AccountUiState())

    /** The intent to start Google's sign-in, or null (and an error shown) if the app has no client id. */
    fun startSignIn(): Intent? {
        val intent = auth.signInIntent()
        local.update { if (intent == null) it.copy(error = R.string.sign_in_not_configured) else it.copy(signingIn = true, error = null) }
        return intent
    }

    /** The intent to start Microsoft's sign-in, or null (and an error shown) if the app has no client id. */
    fun startMicrosoftSignIn(): Intent? {
        val intent = microsoft.signInIntent()
        local.update { if (intent == null) it.copy(error = R.string.sign_in_not_configured) else it.copy(signingIn = true, error = null) }
        return intent
    }

    fun onMicrosoftResult(data: Intent?) {
        viewModelScope.launch {
            when (val outcome = microsoft.handleResult(data)) {
                is SignInOutcome.Success -> {
                    repo.add(outcome.result, "MICROSOFT")
                    local.update { it.copy(signingIn = false, error = null, demo = false) }
                }
                SignInOutcome.Cancelled -> local.update { it.copy(signingIn = false) }
                SignInOutcome.NotConfigured -> local.update { it.copy(signingIn = false, error = R.string.sign_in_not_configured) }
                is SignInOutcome.Failed -> local.update { it.copy(signingIn = false, error = R.string.sign_in_failed) }
            }
        }
    }

    fun onSignInResult(data: Intent?) {
        viewModelScope.launch {
            when (val outcome = auth.handleResult(data)) {
                is SignInOutcome.Success -> {
                    repo.add(outcome.result)
                    local.update { it.copy(signingIn = false, error = null, demo = false) }
                }
                SignInOutcome.Cancelled -> local.update { it.copy(signingIn = false) }
                SignInOutcome.NotConfigured -> local.update { it.copy(signingIn = false, error = R.string.sign_in_not_configured) }
                is SignInOutcome.Failed -> local.update { it.copy(signingIn = false, error = R.string.sign_in_failed) }
            }
        }
    }

    fun remove(accountId: String) {
        viewModelScope.launch { repo.remove(accountId) }
    }

    /** Checks the connection and saves the account; returns the text to show if something is wrong, null if all went well. */
    suspend fun addImap(config: com.cemail.app.data.imap.ImapConfig): Int? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            com.cemail.app.data.imap.ImapClient(config).check()
            repo.addImap(config)
            local.update { it.copy(error = null, demo = false) }
            null
        } catch (e: javax.mail.AuthenticationFailedException) {
            R.string.imap_error_auth
        } catch (e: javax.mail.MessagingException) {
            R.string.imap_error_connect
        } catch (e: Exception) {
            com.cemail.app.AppLog.e("account", "could not check the IMAP account", e)
            R.string.imap_error_other
        }
    }

    /** Shows one account's mail, or all accounts together when [accountId] is null. */
    fun select(accountId: String?) = local.update { it.copy(selectedId = accountId) }

    fun useDemo() = local.update { it.copy(demo = true, error = null) }

    class Factory(
        private val repo: AccountRepository,
        private val auth: GoogleAuth,
        private val microsoft: com.cemail.app.data.auth.MicrosoftAuth,
        private val order: kotlinx.coroutines.flow.Flow<List<String>> = kotlinx.coroutines.flow.flowOf(emptyList()),
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AccountViewModel(repo, auth, microsoft, order) as T
    }
}
