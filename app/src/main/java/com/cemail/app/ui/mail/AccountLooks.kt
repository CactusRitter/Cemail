package com.cemail.app.ui.mail

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cemail.app.data.AccountLook
import com.cemail.app.data.auth.AccountPhotos
import com.cemail.app.data.db.AccountEntity
import com.cemail.app.ui.theme.Accent
import com.cemail.app.ui.theme.LocalGlass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** How every account looks (Settings → Accounts → the account), available to any screen. */
val LocalAccountLooks = compositionLocalOf { emptyMap<String, AccountLook>() }

/** The name an account goes by in the lists: the one the user gave it, its owner's name, or its address. */
@Composable
fun AccountEntity.shownName(): String = LocalAccountLooks.current[id]?.label ?: displayName ?: email

/** A colour of the design system for the light or dark theme. */
@Composable
fun Accent.shade(): Color = if (LocalGlass.current.dark) dark else light

/** The account's colour: the one chosen in Settings, or one of the theme's by its place in the list. */
@Composable
fun accountColor(account: AccountEntity, accounts: List<AccountEntity>): Color {
    LocalAccountLooks.current[account.id]?.color?.let { return it.shade() }
    val palette = accountPalette()
    return palette[accounts.indexOfFirst { it.id == account.id }.coerceAtLeast(0) % palette.size]
}

/**
 * The account's picture (from Google or Microsoft, or one picked from the gallery), or its initial on its colour.
 * [ring] draws a thin circle of the account's colour around it, which tells accounts apart at a glance.
 */
@Composable
fun AccountAvatar(account: AccountEntity, accounts: List<AccountEntity>, size: Int = 44, ring: Boolean = false) {
    val context = LocalContext.current
    val version by AccountPhotos.version.collectAsState()
    val photo by produceState<ImageBitmap?>(null, account.id, version) {
        value = withContext(Dispatchers.IO) {
            val file = AccountPhotos.file(context, account.id)
            if (file.exists()) BitmapFactory.decodeFile(file.path)?.asImageBitmap() else null
        }
    }
    val color = accountColor(account, accounts)
    val chosen = LocalAccountLooks.current[account.id]?.color
    val border = if (ring) Modifier.border(2.dp, color, CircleShape) else Modifier
    Box(Modifier.size(size.dp).then(border), contentAlignment = Alignment.Center) {
        val inner = if (ring) (size - 6).dp else size.dp
        val image = photo
        if (image != null) {
            Image(image, null, Modifier.size(inner).clip(CircleShape), contentScale = ContentScale.Crop)
        } else {
            val dark = LocalGlass.current.dark
            val background = chosen?.let { if (dark) it.darkContainer else it.lightContainer } ?: color.copy(alpha = 0.18f)
            Box(Modifier.size(inner).background(background, CircleShape), contentAlignment = Alignment.Center) {
                Text(
                    account.shownName().trim().firstOrNull()?.uppercase() ?: "?",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = if (size >= 40) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
