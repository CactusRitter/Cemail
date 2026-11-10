package com.cemail.app

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * A small activity log kept in a private file, so a problem on the phone can be understood later without a
 * cable: sync results, notifications decisions, account changes and errors. It records counts and error
 * types, never message text, subjects, addresses or tokens. It leaves the phone only when the user shares it.
 */
object AppLog {
    private const val MAX_BYTES = 200_000
    private val time = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")
    private var dir: File? = null
    private val lock = Any()

    fun init(context: Context) {
        dir = context.applicationContext.filesDir
        i("app", "start ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · Android ${android.os.Build.VERSION.RELEASE} · ${android.os.Build.MODEL}")
    }

    private fun file() = dir?.let { File(it, "activity.log") }

    fun i(tag: String, message: String) = append("I", tag, message)

    fun e(tag: String, message: String, error: Throwable? = null) {
        append("E", tag, message + (error?.let { " → ${it.javaClass.simpleName}: ${it.message?.take(300)}" } ?: ""))
        if (error != null) Log.w("Cemail", "$tag: $message", error)
    }

    private fun append(level: String, tag: String, message: String) {
        val f = file() ?: return
        synchronized(lock) {
            try {
                if (f.exists() && f.length() > MAX_BYTES) {
                    // Keep the newer half of the file.
                    val text = f.readText()
                    f.writeText(text.substring(text.length / 2).substringAfter('\n'))
                }
                f.appendText("${LocalDateTime.now().format(time)} $level/$tag $message\n")
            } catch (_: Exception) {
                // logging must never break the app
            }
        }
    }

    /** The whole log, followed by the last crash if there is one. */
    fun text(context: Context): String = synchronized(lock) {
        val log = file()?.takeIf { it.exists() }?.readText().orEmpty()
        val crash = CrashLog.read(context)
        if (crash == null) log else "$log\n--- last crash ---\n$crash\n"
    }

    fun clear(context: Context) = synchronized(lock) {
        file()?.delete()
        CrashLog.clear(context)
        i("app", "log cleared")
    }

    /** Opens Android's share sheet with the log as a .txt file (Drive, Telegram, e-mail...). */
    fun share(context: Context, title: String) {
        val out = File(context.cacheDir, "shared").apply { mkdirs() }.let { File(it, "cemail-log.txt") }
        out.writeText(text(context))
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", out)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "Cemail ${BuildConfig.VERSION_NAME} log")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
