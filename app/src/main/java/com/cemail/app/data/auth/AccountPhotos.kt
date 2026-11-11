package com.cemail.app.data.auth

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.cemail.app.AppLog
import com.cemail.app.CemailApplication
import com.cemail.app.data.db.AccountEntity
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * The picture of each account: by default the one the account has at Google or Microsoft (fetched once and checked
 * again every few days), or one the user picked from the gallery, which then stays. Kept as a small square JPEG in the
 * app's files; [version] changes whenever any picture does, so the screens redraw.
 */
object AccountPhotos {
    private const val SIZE = 256
    private const val RECHECK_MILLIS = 7L * 24 * 3600 * 1000

    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    private val client by lazy { OkHttpClient() }

    fun file(context: Context, accountId: String): File =
        File(context.filesDir, "avatars/${accountId.filter { it.isLetterOrDigit() }}.jpg")

    private fun prefs(context: Context) = context.getSharedPreferences("avatars", Context.MODE_PRIVATE)

    /** true when the user chose the picture (the provider's is then never fetched over it). */
    fun isCustom(context: Context, accountId: String) = prefs(context).getBoolean("custom_$accountId", false)

    /** Uses a picture from the gallery. false if it could not be read. */
    suspend fun setFromUri(context: Context, accountId: String, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            AppLog.e("photo", "could not read the picked picture", e)
            null
        } ?: return@withContext false
        save(context, accountId, bytes).also { ok -> if (ok) prefs(context).edit().putBoolean("custom_$accountId", true).apply() }
    }

    /** Goes back to the provider's picture (fetched now; without one, the coloured initial). */
    suspend fun useProvider(app: CemailApplication, account: AccountEntity): Boolean {
        prefs(app).edit().remove("custom_${account.id}").apply()
        file(app, account.id).delete()
        _version.value++
        return fetch(app, account)
    }

    /** Fetches the provider's picture if there is no chosen one and it was not checked lately. */
    suspend fun refreshIfStale(app: CemailApplication, account: AccountEntity) {
        if (isCustom(app, account.id)) return
        val checked = prefs(app).getLong("checked_${account.id}", 0L)
        if (file(app, account.id).exists() && System.currentTimeMillis() - checked < RECHECK_MILLIS) return
        if (System.currentTimeMillis() - checked < RECHECK_MILLIS && checked > 0) return // checked: it has none
        fetch(app, account)
    }

    fun remove(context: Context, accountId: String) {
        file(context, accountId).delete()
        prefs(context).edit().remove("custom_$accountId").remove("checked_$accountId").apply()
        _version.value++
    }

    /** Downloads the account's picture from Google or Microsoft. IMAP accounts have none. */
    private suspend fun fetch(app: CemailApplication, account: AccountEntity): Boolean = withContext(Dispatchers.IO) {
        val bytes = try {
            when (account.provider) {
                "GMAIL" -> {
                    val token = AccountTokenProvider(account.id, app.tokenStore) { app.googleAuth.freshAccessToken(it) }.accessToken()
                    token?.let { googlePicture(it) }
                }
                "MICROSOFT" -> {
                    val token = AccountTokenProvider(account.id, app.tokenStore) { app.microsoftAuth.freshAccessToken(it) }.accessToken()
                    token?.let { microsoftPicture(it) }
                }
                else -> null
            }
        } catch (e: Exception) {
            AppLog.e("photo", "could not fetch the account picture", e)
            return@withContext false // offline, say: tried again next time
        }
        prefs(app).edit().putLong("checked_${account.id}", System.currentTimeMillis()).apply()
        if (bytes == null) {
            AppLog.i("photo", "...${account.id.takeLast(4)} has no picture at the provider")
            return@withContext false
        }
        save(app, account.id, bytes)
    }

    private fun googlePicture(token: String): ByteArray? {
        val info = get("https://openidconnect.googleapis.com/v1/userinfo", token) ?: return null
        val url = JSONObject(String(info)).optString("picture").takeIf { it.isNotBlank() } ?: return null
        // Google's picture addresses end with the size ("=s96-c"): a bigger one looks sharp on large screens.
        return get(url.replace(Regex("=s\\d+(-c)?$"), "=s$SIZE-c"), null)
    }

    private fun microsoftPicture(token: String): ByteArray? =
        get("https://graph.microsoft.com/v1.0/me/photos/240x240/\$value", token)
            ?: get("https://graph.microsoft.com/v1.0/me/photo/\$value", token)

    /** The body, or null when the server says there is nothing (404 and the like). Network failures throw. */
    private fun get(url: String, token: String?): ByteArray? {
        val request = Request.Builder().url(url).apply { token?.let { header("Authorization", "Bearer $it") } }.build()
        client.newCall(request).execute().use { response ->
            return if (response.isSuccessful) response.body.bytes() else null
        }
    }

    /** Crops the picture to a centred square, shrinks it and keeps it as JPEG. */
    private fun save(context: Context, accountId: String, bytes: ByteArray): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= SIZE) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return false
        val side = minOf(bitmap.width, bitmap.height)
        val square = Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side)
        val scaled = if (side > SIZE) Bitmap.createScaledBitmap(square, SIZE, SIZE, true) else square
        val target = file(context, accountId)
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, target.name + ".tmp")
        temp.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        temp.renameTo(target)
        _version.value++
        return true
    }
}
