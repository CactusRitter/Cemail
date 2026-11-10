package com.cemail.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cemail.app.ui.account.AccountViewModel
import com.cemail.app.ui.mail.MailApp
import com.cemail.app.ui.mail.MailViewModel
import com.cemail.app.ui.theme.CemailTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as CemailApplication
        setContent {
            val settings by app.settings.state.collectAsStateWithLifecycle()
            CemailTheme(settings) {
                val accountVm: AccountViewModel = viewModel(factory = AccountViewModel.Factory(app.accountRepository, app.googleAuth))
                val accounts by accountVm.state.collectAsStateWithLifecycle()
                // One account is shown at a time (no unified inbox); no account means the demo store.
                val active = accounts.accounts.firstOrNull()?.id
                val repo = remember(active) { if (active != null) app.mailRepositoryFor(active) else app.demoRepository }
                val vm: MailViewModel = viewModel(key = active ?: "demo", factory = MailViewModel.Factory(repo))
                MailApp(vm, accountVm)
            }
        }
    }
}
