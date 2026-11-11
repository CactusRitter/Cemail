package com.cemail.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
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
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Launches.handle(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as CemailApplication
        // Opened from the launcher menu or a widget: MailApp carries the request out once its account is on screen.
        if (savedInstanceState == null) Launches.handle(intent)
        // Android 13+ asks once, the first time the app opens; Settings can ask again.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !app.settings.askedNotificationPermission) {
            app.settings.askedNotificationPermission = true
            if (!app.notifier.allowed()) {
                registerForActivityResult(ActivityResultContracts.RequestPermission()) {}.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        setContent {
            val settings by app.settings.state.collectAsStateWithLifecycle()
            CemailTheme(settings) {
                val accountVm: AccountViewModel = viewModel(factory = AccountViewModel.Factory(app.accountRepository, app.googleAuth, app.microsoftAuth))
                val accounts by accountVm.state.collectAsStateWithLifecycle()
                // What is on screen: the demo store (no account), one account, or all of them together.
                val ids = accounts.accounts.map { it.id }
                val selected = accounts.selectedId
                val scopeKey = when {
                    ids.isEmpty() -> "demo"
                    selected != null -> selected
                    ids.size == 1 -> ids.first()
                    else -> "all:" + ids.joinToString(" ")
                }
                val repo = remember(scopeKey) {
                    when {
                        ids.isEmpty() -> app.demoRepository
                        selected != null -> app.mailRepositoryFor(selected)
                        ids.size == 1 -> app.mailRepositoryFor(ids.first())
                        else -> app.unifiedRepository(ids)
                    }
                }
                val vm: MailViewModel = viewModel(key = scopeKey, factory = MailViewModel.Factory(repo) { id -> if (id in ids) app.mailRepositoryFor(id) else null })
                androidx.compose.runtime.CompositionLocalProvider(com.cemail.app.ui.mail.LocalAccountLooks provides settings.accountLooks) {
                    MailApp(vm, accountVm, settings, app.settings)
                }
            }
        }
    }
}
