package com.cemail.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.cemail.app.AppLog
import com.cemail.app.BuildConfig
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/** A newer version of Cemail published on GitHub; [size] is the APK's size in bytes (0 if GitHub did not say). */
data class AppUpdate(val version: String, val apkUrl: String, val pageUrl: String, val notes: String = "", val size: Long = 0)

/** Where the download of a newer version stands. */
sealed interface DownloadState {
    data object Idle : DownloadState

    /** [total] is -1 while the size is not known. */
    data class Downloading(val version: String, val bytes: Long, val total: Long) : DownloadState

    /** Fully downloaded: the installer can be opened. */
    data class Ready(val version: String) : DownloadState

    /** It stopped: [incomplete] when the file came but not whole, otherwise the connection or the server failed. */
    data class Failed(val version: String, val incomplete: Boolean) : DownloadState
}

@Serializable
private data class GitHubAsset(
    val name: String = "",
    @SerialName("browser_download_url") val url: String = "",
    val size: Long = 0,
)

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
            AppUpdate(version, apk.url, release.page, release.body.orEmpty(), apk.size)
        } else {
            null
        }
        AppLog.i("update", "checked: latest ${release.tag}, ${if (found != null) "newer" else "up to date"}")
        prefs.edit().apply {
            if (found == null) remove(KEY_VERSION).remove(KEY_APK).remove(KEY_PAGE).remove(KEY_NOTES).remove(KEY_SIZE)
            else putString(KEY_VERSION, found.version).putString(KEY_APK, found.apkUrl).putString(KEY_PAGE, found.pageUrl)
                .putString(KEY_NOTES, found.notes).putLong(KEY_SIZE, found.size)
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

    // The download lives here, in the app (not in a screen): it goes on when the screen turns or Settings is left, as
    // long as the app's process lives. No download manager and no notification: the progress is shown in the app.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val dir = File(context.filesDir, "updates")
    private val _download = MutableStateFlow(initialDownload())
    /** How the download of the newer version is going. */
    val download: StateFlow<DownloadState> = _download.asStateFlow()

    init {
        scope.launch { cleanUp() }
    }

    /**
     * Downloads the APK of [update] into the app's own storage, showing the progress in [download], and opens the
     * installer when it is complete. A download that was cut off goes on from where it stopped (if the server allows
     * it). Does nothing while a download is already running.
     */
    fun startDownload(update: AppUpdate) {
        if (job?.isActive == true) return
        job = scope.launch {
            val done = apkFile(update.version)
            if (done.exists()) {
                _download.value = DownloadState.Ready(update.version)
                withContext(Dispatchers.Main) { install(update) }
                return@launch
            }
            dir.mkdirs()
            val part = File(dir, done.name + ".part")
            try {
                AppLog.i("update", "downloading ${update.version}")
                fetch(update, part)
                if (!part.renameTo(done)) throw java.io.IOException("could not keep the file")
                _download.value = DownloadState.Ready(update.version)
                AppLog.i("update", "downloaded ${update.version}")
                withContext(Dispatchers.Main) { install(update) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IncompleteException) {
                part.delete() // something is wrong with it: the next try starts from the beginning
                AppLog.e("update", "download of ${update.version} incomplete", e)
                _download.value = DownloadState.Failed(update.version, incomplete = true)
            } catch (e: Exception) {
                AppLog.e("update", "download of ${update.version} failed", e)
                _download.value = DownloadState.Failed(update.version, incomplete = false)
            }
        }
    }

    /** Stops the download and throws away what came so far. */
    fun cancelDownload() {
        job?.cancel()
        job = null
        scope.launch {
            dir.listFiles { f -> f.name.endsWith(".part") }?.forEach { it.delete() }
            _download.value = DownloadState.Idle
        }
    }

    /** Copies the APK into [part], going on from what [part] already has. Throws when it cannot be fully fetched. */
    private suspend fun fetch(update: AppUpdate, part: File) {
        var have = if (part.exists()) part.length() else 0L
        val request = Request.Builder().url(update.apkUrl)
            .header("User-Agent", "Cemail")
            .apply { if (have > 0) header("Range", "bytes=$have-") }
            .build()
        downloadClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw java.io.IOException("download ${response.code}")
            val resumed = response.code == 206
            if (resumed) AppLog.i("update", "going on from $have bytes") else have = 0 // the server sent it all again
            val length = response.body.contentLength()
            val total = when {
                length < 0 -> update.size.takeIf { it > 0 } ?: -1L
                else -> have + length
            }
            _download.value = DownloadState.Downloading(update.version, have, total)
            java.io.FileOutputStream(part, resumed).use { out ->
                response.body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var shownAt = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        have += n
                        val now = System.currentTimeMillis()
                        if (now - shownAt > 100) { // about ten times a second is enough for the bar
                            shownAt = now
                            _download.value = DownloadState.Downloading(update.version, have, total)
                        }
                    }
                }
            }
            _download.value = DownloadState.Downloading(update.version, have, total)
            // Complete: as long as the server said, and as large as GitHub says the APK is.
            if ((total > 0 && have != total) || (update.size > 0 && have != update.size)) {
                throw IncompleteException("got $have of ${if (update.size > 0) update.size else total} bytes")
            }
        }
    }

    private class IncompleteException(message: String) : java.io.IOException(message)

    /** The APK of [version] once it is fully downloaded. */
    private fun apkFile(version: String) = File(dir, "Cemail-$version.apk")

    /** After a restart: an APK of the version on offer that is already here can be installed at once. */
    private fun initialDownload(): DownloadState {
        val version = _available.value?.version ?: return DownloadState.Idle
        return if (apkFile(version).exists()) DownloadState.Ready(version) else DownloadState.Idle
    }

    /**
     * Throws away downloaded APKs that are no use any more (this version or older, so already installed, or an older
     * offer than the current one) and those that Android's download manager kept for earlier versions.
     */
    private fun cleanUp() {
        val offered = _available.value?.version
        dir.listFiles()?.forEach { file ->
            val version = file.name.removePrefix("Cemail-").substringBefore(".apk")
            if (version != offered) file.delete()
        }
        context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
            ?.listFiles { f -> f.name.startsWith("Cemail-") && f.name.endsWith(".apk") }
            ?.forEach { it.delete() }
    }

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

    /**
     * Opens Android's installer with the downloaded APK of [update] (also again, after the installer was cancelled).
     * false while it is not fully downloaded. Android shows what is being installed and asks first.
     */
    fun install(update: AppUpdate): Boolean {
        val apk = apkFile(update.version)
        if (!apk.exists()) return false
        val file = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
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
        return AppUpdate(
            version, prefs.getString(KEY_APK, null) ?: return null, prefs.getString(KEY_PAGE, "").orEmpty(),
            prefs.getString(KEY_NOTES, "").orEmpty(), prefs.getLong(KEY_SIZE, 0),
        )
    }

    /** For the APK: a long read timeout (a slow connection), and redirects followed (GitHub sends it from elsewhere). */
    private val downloadClient by lazy { client.newBuilder().readTimeout(60, TimeUnit.SECONDS).build() }

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
        const val KEY_SIZE = "size"
    }
}
