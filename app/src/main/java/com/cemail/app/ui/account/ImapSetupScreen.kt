package com.cemail.app.ui.account

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.cemail.app.R
import com.cemail.app.data.imap.ImapConfig
import com.cemail.app.data.imap.ImapPresets
import com.cemail.app.data.imap.Security
import com.cemail.app.ui.glass.GlassBackground
import com.cemail.app.ui.glass.GlassKind
import com.cemail.app.ui.glass.GlassPill
import com.cemail.app.ui.glass.PillIcon
import com.cemail.app.ui.glass.glass
import com.cemail.app.ui.glass.pressScale
import kotlinx.coroutines.launch

/**
 * Adds an account of any provider that speaks IMAP/SMTP. With only the address and password the server settings are
 * guessed (known providers) and checked; "Server settings" lets the user write them by hand.
 */
@Composable
fun ImapSetupScreen(accountVm: AccountViewModel, yahoo: Boolean = false, onClose: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var username by rememberSaveable { mutableStateOf("") }
    var imapHost by rememberSaveable { mutableStateOf("") }
    var imapPort by rememberSaveable { mutableStateOf("993") }
    var imapStartTls by rememberSaveable { mutableStateOf(false) }
    var smtpHost by rememberSaveable { mutableStateOf("") }
    var smtpPort by rememberSaveable { mutableStateOf("465") }
    var smtpStartTls by rememberSaveable { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    var unknownProvider by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val colors = TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
    )

    fun fillFrom(preset: ImapConfig) {
        username = preset.username
        imapHost = preset.imapHost; imapPort = preset.imapPort.toString(); imapStartTls = preset.imapSecurity == Security.STARTTLS
        smtpHost = preset.smtpHost; smtpPort = preset.smtpPort.toString(); smtpStartTls = preset.smtpSecurity == Security.STARTTLS
    }

    fun submit() {
        val address = email.trim()
        if (!address.contains('@') || password.isEmpty()) return
        // Without hand-written settings the provider's known ones are used; unknown providers get a guess to review.
        var manual = advanced && imapHost.isNotBlank() && smtpHost.isNotBlank()
        if (!manual) {
            val known = ImapPresets.forEmail(address, password)
            if (known == null) {
                fillFrom(ImapPresets.guess(address, password))
                advanced = true
                unknownProvider = true
                return
            }
            fillFrom(known)
            manual = true
        }
        val config = ImapConfig(
            email = address,
            username = username.ifBlank { address },
            password = password,
            imapHost = imapHost.trim(), imapPort = imapPort.toIntOrNull() ?: 993,
            imapSecurity = if (imapStartTls) Security.STARTTLS else Security.SSL,
            smtpHost = smtpHost.trim(), smtpPort = smtpPort.toIntOrNull() ?: 465,
            smtpSecurity = if (smtpStartTls) Security.STARTTLS else Security.SSL,
            displayName = name.trim().ifEmpty { null },
        )
        checking = true
        error = null
        scope.launch {
            val problem = accountVm.addImap(config)
            checking = false
            if (problem == null) onClose() else error = problem
        }
    }

    GlassBackground {
        // Centred and no wider than a phone column on tablets and open foldables.
        Box(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .fillMaxHeight()
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 12.dp, end = 12.dp, top = 80.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (yahoo) {
                    YahooSteps()
                } else {
                    Text(
                        stringResource(R.string.imap_body),
                        Modifier.padding(horizontal = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp), GlassKind.Card)) {
                    TextField(
                        value = email, onValueChange = { email = it }, singleLine = true, colors = colors,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(if (yahoo) R.string.yahoo_email else R.string.imap_email)) },
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Email),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    TextField(
                        value = password, onValueChange = { password = it }, singleLine = true, colors = colors,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(if (yahoo) R.string.yahoo_password else R.string.imap_password)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    TextField(
                        value = name, onValueChange = { name = it }, singleLine = true, colors = colors,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.imap_name)) },
                    )
                }
                if (unknownProvider) {
                    Text(
                        stringResource(R.string.imap_server_unknown),
                        Modifier.padding(horizontal = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    stringResource(R.string.imap_advanced),
                    Modifier.clickable(role = Role.Button) { advanced = !advanced }.padding(8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (advanced) {
                    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp), GlassKind.Card)) {
                        TextField(
                            value = username, onValueChange = { username = it }, singleLine = true, colors = colors,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.imap_username)) },
                            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Email),
                        )
                        ServerBlock(R.string.imap_server_in, imapHost, { imapHost = it }, imapPort, { imapPort = it }, imapStartTls, { imapStartTls = it }, colors)
                        ServerBlock(R.string.imap_server_out, smtpHost, { smtpHost = it }, smtpPort, { smtpPort = it }, smtpStartTls, { smtpStartTls = it }, colors)
                    }
                }
                error?.let {
                    Text(stringResource(it), Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Row(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .glass(CircleShape, GlassKind.Pinned, elevation = 8.dp)
                        .clickable(enabled = !checking, role = Role.Button) { submit() }
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (checking) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(
                        stringResource(if (checking) R.string.imap_checking else R.string.imap_add),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
            Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 12.dp, vertical = 8.dp)) {
                GlassPill(Modifier.fillMaxWidth(), height = 64.dp) {
                    PillIcon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), onClose)
                    Text(
                        stringResource(if (yahoo) R.string.account_add_yahoo else R.string.imap_title),
                        Modifier.weight(1f).padding(horizontal = 8.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

/**
 * Yahoo does not take the normal password in other apps: it needs an app password, and only lets an account create
 * one once two-step verification is on and the account has been used in the same browser for a few days. The steps,
 * and a button to Yahoo's security page.
 */
@Composable
private fun YahooSteps() {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp), GlassKind.Card).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            com.cemail.app.ui.icons.YahooLogo(36.dp)
            Text(stringResource(R.string.yahoo_steps_title), style = MaterialTheme.typography.titleSmall)
        }
        listOf(R.string.yahoo_step_1, R.string.yahoo_step_2, R.string.yahoo_step_3, R.string.yahoo_step_4).forEachIndexed { i, step ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.step_number, i + 1),
                    Modifier.widthIn(min = 20.dp), // the texts of all the steps start at the same place
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(stringResource(step), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text(
            stringResource(R.string.yahoo_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.yahoo_open_security),
            Modifier
                .clip(CircleShape)
                .clickable(role = Role.Button) {
                    try {
                        context.startActivity(
                            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(YAHOO_SECURITY))
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    } catch (_: Exception) {
                        // no browser
                    }
                }
                .padding(horizontal = 4.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** Yahoo's account security page, where two-step verification and app passwords are. */
private const val YAHOO_SECURITY = "https://login.yahoo.com/myaccount/security/"

@Composable
private fun ServerBlock(
    title: Int,
    host: String,
    onHost: (String) -> Unit,
    port: String,
    onPort: (String) -> Unit,
    startTls: Boolean,
    onStartTls: (Boolean) -> Unit,
    colors: androidx.compose.material3.TextFieldColors,
) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Text(stringResource(title), Modifier.padding(start = 16.dp, top = 10.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextField(
            value = host, onValueChange = onHost, singleLine = true, colors = colors, modifier = Modifier.weight(1f),
            label = { Text(stringResource(R.string.imap_host)) },
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Uri),
        )
        TextField(
            value = port, onValueChange = { onPort(it.filter(Char::isDigit).take(5)) }, singleLine = true, colors = colors,
            modifier = Modifier.width(96.dp),
            label = { Text(stringResource(R.string.imap_port)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        listOf(false to R.string.imap_security_ssl, true to R.string.imap_security_starttls).forEach { (value, label) ->
            Text(
                stringResource(label),
                Modifier
                    .clickable(role = Role.RadioButton) { onStartTls(value) }
                    .glass(CircleShape, if (startTls == value) GlassKind.Pinned else GlassKind.Card)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
