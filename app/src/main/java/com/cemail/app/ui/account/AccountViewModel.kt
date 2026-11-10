package com.cemail.app.ui.account

import android.content.Intent
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cemail.app.R
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
)

private data class Local(val signingIn: Boolean = false, val error: Int? = null, val demo: Boolean = false)

class AccountViewModel(private val repo: AccountRepository, private val auth: GoogleAuth) : ViewModel() {
    private val local = MutableStateFlow(Local())

    val state: StateFlow<AccountUiState> = combine(repo.accounts, local) { accounts, l ->
        AccountUiState(true, accounts, l.signingIn, l.error, l.demo)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AccountUiState())

    /** The intent to start Google's sign-in, or null (and an error shown) if the app has no client id. */
    fun startSignIn(): Intent? {
        val intent = auth.signInIntent()
        local.update { if (intent == null) it.copy(error = R.string.sign_in_not_configured) else it.copy(signingIn = true, error = null) }
        return intent
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

    fun useDemo() = local.update { it.copy(demo = true, error = null) }
    fun dismissError() = local.update { it.copy(error = null) }

    class Factory(private val repo: AccountRepository, private val auth: GoogleAuth) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AccountViewModel(repo, auth) as T
    }
}
