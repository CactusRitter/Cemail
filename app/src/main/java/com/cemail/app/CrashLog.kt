package com.cemail.app

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Keeps the last unexpected error in a private file so it can be read in Settings and passed on.
 * It never leaves the phone by itself (no crash-reporting service).
 */
object CrashLog {
    private fun file(context: Context) = File(context.filesDir, "last_crash.txt")

    /** Records crashes of any thread, then lets Android handle them as usual. */
    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            write(app, error)
            previous?.uncaughtException(thread, error)
        }
    }

    /** Records an error that was survived (background work), so it is not lost silently. */
    fun write(context: Context, error: Throwable) {
        try {
            val header = "Cemail ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · Android ${android.os.Build.VERSION.RELEASE} · " +
                java.time.Instant.now().toString()
            file(context).writeText(header + "\n" + Log.getStackTraceString(error))
            AppLog.e("crash", "unexpected error", error)
        } catch (_: Exception) {
            // nothing else can be done here
        }
    }

    fun read(context: Context): String? = file(context).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }

    fun clear(context: Context) {
        file(context).delete()
    }
}
