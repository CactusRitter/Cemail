package com.cemail.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
                val vm: MailViewModel = viewModel(factory = MailViewModel.Factory(app.mailRepository))
                MailApp(vm)
            }
        }
    }
}
