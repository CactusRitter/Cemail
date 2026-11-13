package com.cemail.app.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import androidx.core.content.ContextCompat
import com.cemail.app.AppLog
import com.cemail.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/** A newer version of Cemail published on GitHub. */
data class AppUpdate(val version: String, val apkUrl: String, val pageUrl: String, val notes: String = "")

@Serializable
private data class GitHubAsset(val name: String = "", @SerialName("browser_download_url") val url: String = "")

@Serializable
private data class GitHubRelease(
    @SerialName("tag_name") val tag: String = "",
    @SerialName("html_url") val page: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val body: String? = null,
    val assets: List<GitHubAsset> = emptyList(),
)

/** Version numbers as "1.10.2" (a leading "v" is fine), compared number by number. */
object Versions {
    fun parse(text: String): List<Int>? {
        val parts = text.trim().removePrefix("v").removePrefix("V").split('.')
        if (parts.isEmpty()) return null
        return parts.map { it.toIntOrNull() ?: return null }
    }

    /** true if [candidate] is a later version than [current]. */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = parse(candidate) ?: return false
        val b = parse(current) ?: return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}

/**
 * Looks on GitHub for a newer version of Cemail (the latest release of the public repository, with its APK), at most
 * twice a day, and downloads it for Android to install. GitHub sees the address of the connection and nothing else:
 * no account, no mail. Nothing is installed without the user: Android always asks.
 */
class Updates(private val context: Context, private val client: OkHttpClient = OkHttpClient()) {
    private val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _available = MutableStateFlow(stored())
    /** The newer version there is (and the user has not set aside), or null. */
    val available: StateFlow<AppUpdate?> = _available.asStateFlow()

    /** What a check found: the newer version, null when this one is the latest. Throws when GitHub could not be asked. */
    suspend fun check(force: Boolean = false): AppUpdate? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong(KEY_CHECKED, 0) < EVERY) return@withContext _available.value
        val request = Request.Builder().url(LATEST)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Cemail") // GitHub wants one; nothing about the user goes in it
            .build()
        val release = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw java.io.IOException("GitHub ${response.code}")
            json.decodeFromString(GitHubRelease.serializer(), response.body.string())
        }
        prefs.edit().putLong(KEY_CHECKED, now).apply()
        val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
        val version = release.tag.removePrefix("v")
        val found = if (!release.draft && !release.prerelease && apk != null && Versions.isNewer(version, BuildConfig.VERSION_NAME)) {
            AppUpdate(version, apk.url, release.page, release.body.orEmpty())
        } else {
            null
        }
        AppLog.i("update", "checked: latest ${release.tag}, ${if (found != null) "newer" else "up to date"}")
        prefs.edit().apply {
            if (found == null) remove(KEY_VERSION).remove(KEY_APK).remove(KEY_PAGE).remove(KEY_NOTES)
            else putString(KEY_VERSION, found.version).putString(KEY_APK, found.apkUrl).putString(KEY_PAGE, found.pageUrl).putString(KEY_NOTES, found.notes)
        }.apply()
        // Asking by hand (Settings) offers it again even if it was set aside.
        if (force) prefs.edit().remove(KEY_IGNORED).apply()
        _available.value = found?.takeIf { it.version != prefs.getString(KEY_IGNORED, null) }
        found
    }

    /** "Not now": this version is not offered again (a later one will be). */
    fun setAside(update: AppUpdate) {
        prefs.edit().putString(KEY_IGNORED, update.version).apply()
        _available.value = null
    }

    /**
     * Downloads the APK with Android's download manager (its notification shows the progress) and opens the installer
     * when it is done. Returns false if the download could not even start.
     */
    fun download(update: AppUpdate, title: String): Boolean = try {
        val manager = context.getSystemService(DownloadManager::class.java)
        val name = "Cemail-${update.version}.apk"
        java.io.File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), name).delete() // an earlier, unfinished try
        val id = manager.enqueue(
            DownloadManager.Request(Uri.parse(update.apkUrl))
                .setTitle(title)
                .setMimeType(APK)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, name),
        )
        prefs.edit().putLong(KEY_DOWNLOAD, id).putString(KEY_DOWNLOADED, update.version).apply()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != id) return
                context.unregisterReceiver(this)
                if (!install(update)) AppLog.e("update", "download of ${update.version} failed")
            }
        }
        // The download manager is another app: its broadcast has to be let in.
        ContextCompat.registerReceiver(context, receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), ContextCompat.RECEIVER_EXPORTED)
        AppLog.i("update", "downloading ${update.version}")
        true
    } catch (e: Exception) {
        AppLog.e("update", "could not start the download", e)
        false
    }

    /**
     * Opens Android's installer with the downloaded APK of [update] (also again, after the installer was cancelled).
     * false while it is not fully downloaded. Android shows what is being installed and asks first (and, the first
     * time, for leave to install apps that come from Cemail).
     */
    /** Whether Android already lets Cemail install apps. Granting it can restart the app, so it is asked before downloading. */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Opens the Android screen where the user allows installing apps from Cemail. */
    fun askToAllowInstall(): Boolean = try {
        context.startActivity(
            Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (e: Exception) {
        AppLog.e("update", "could not open the install permission", e)
        false
    }

    fun install(update: AppUpdate): Boolean {
        if (prefs.getString(KEY_DOWNLOADED, null) != update.version) return false
        val manager = context.getSystemService(DownloadManager::class.java)
        val file = manager.getUriForDownloadedFile(prefs.getLong(KEY_DOWNLOAD, -1)) ?: return false
        return try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW).setDataAndType(file, APK)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        } catch (e: Exception) {
            AppLog.e("update", "could not open the installer", e)
            false
        }
    }

    private fun stored(): AppUpdate? {
        val version = prefs.getString(KEY_VERSION, null) ?: return null
        if (!Versions.isNewer(version, BuildConfig.VERSION_NAME) || version == prefs.getString(KEY_IGNORED, null)) return null
        return AppUpdate(version, prefs.getString(KEY_APK, null) ?: return null, prefs.getString(KEY_PAGE, "").orEmpty(), prefs.getString(KEY_NOTES, "").orEmpty())
    }

    private companion object {
        const val LATEST = "https://api.github.com/repos/CactusRitter/Cemail/releases/latest"
        const val APK = "application/vnd.android.package-archive"
        const val EVERY = 12L * 60 * 60 * 1000
        const val KEY_CHECKED = "checked_at"
        const val KEY_VERSION = "version"
        const val KEY_APK = "apk"
        const val KEY_PAGE = "page"
        const val KEY_NOTES = "notes"
        const val KEY_IGNORED = "ignored"
        const val KEY_DOWNLOAD = "download_id"
        const val KEY_DOWNLOADED = "download_version"
    }
}
