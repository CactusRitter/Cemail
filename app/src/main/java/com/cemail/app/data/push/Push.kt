package com.cemail.app.data.push

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cemail.app.AppLog
import com.cemail.app.BuildConfig
import com.cemail.app.CemailApplication
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Instant notifications ("push"), see docs/PUSH.md. Gmail publishes a note to a Google Pub/Sub topic whenever the Inbox
 * changes; a small relay (a Cloudflare Worker) turns that into a Firebase Cloud Messaging wake-up for this phone, and
 * [PushService] then syncs the account. The relay only learns the address, a history number and this phone's FCM token.
 */
object Push {
    /** All the keys are in local.properties; without them the app simply works without push (15-minute checks). */
    val available: Boolean
        get() = BuildConfig.PUSH_URL.isNotBlank() && BuildConfig.PUSH_APP_KEY.isNotBlank() &&
            BuildConfig.FIREBASE_APP_ID.isNotBlank() && BuildConfig.FIREBASE_API_KEY.isNotBlank()

    private val client = OkHttpClient()
    private val json = "application/json; charset=utf-8".toMediaType()
    private val lock = Mutex()

    /** Starts Firebase from BuildConfig (no google-services file, so no keys in git). */
    fun initFirebase(context: Context) {
        if (!available || FirebaseApp.getApps(context).isNotEmpty()) return
        try {
            FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                    .setApiKey(BuildConfig.FIREBASE_API_KEY)
                    .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                    .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
                    .build(),
            )
        } catch (e: Exception) {
            AppLog.e("push", "could not start Firebase", e)
        }
    }

    private suspend fun token(): String? = suspendCancellableCoroutine { continuation ->
        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (!task.isSuccessful) AppLog.e("push", "no FCM token", task.exception)
                continuation.resume(if (task.isSuccessful) task.result else null)
            }
        } catch (e: Exception) {
            AppLog.e("push", "no FCM token", e)
            continuation.resume(null)
        }
    }

    /**
     * Brings both ends in line: this phone's token is registered at the relay for the Gmail accounts (none when
     * [enabled] is false), and every Gmail account is told to publish (or to stop). Safe to call as often as wanted;
     * the daily [PushRenewWorker] calls it because Gmail's watch lasts about a week.
     */
    suspend fun sync(app: CemailApplication, enabled: Boolean): Boolean = lock.withLock {
        withContext(Dispatchers.IO) {
            if (!available) return@withContext true
            initFirebase(app)
            val accounts = app.accountRepository.accounts.first().filter { it.provider == "GMAIL" || it.provider == "MICROSOFT" }
            val prefs = app.getSharedPreferences("push", Context.MODE_PRIVATE)
            val previous = prefs.getStringSet("emails", emptySet()).orEmpty()
            val emails = if (enabled) accounts.map { it.email.lowercase() } else emptyList()
            val token = token() ?: return@withContext false
            var ok = register(token, emails, previous.toList())
            if (ok) prefs.edit().putStringSet("emails", emails.toSet()).apply()
            for (account in accounts) {
                try {
                    app.mailRepositoryFor(account.id).setPush(if (enabled) BuildConfig.PUBSUB_TOPIC else null)
                    AppLog.i("push", "...${account.id.takeLast(4)} ${account.provider} ${if (enabled) "watching" else "stopped"}")
                } catch (e: Exception) {
                    ok = false
                    AppLog.e("push", "...${account.id.takeLast(4)} ${account.provider} watch failed", e)
                }
            }
            ok
        }
    }

    /** The address Microsoft Graph calls with change notifications (the relay's /graph). */
    fun graphUrl(): String = BuildConfig.PUSH_URL.trimEnd('/') + "/graph"

    /** Tells the relay which address a Graph subscription belongs to, and the secret Microsoft will send with it. */
    fun registerSubscription(subscriptionId: String, email: String, clientState: String) {
        val body = JSONObject()
            .put("subscriptionId", subscriptionId).put("email", email).put("clientState", clientState)
            .toString().toRequestBody(json)
        val request = Request.Builder()
            .url(BuildConfig.PUSH_URL.trimEnd('/') + "/register-ms")
            .header("X-Cemail-Key", BuildConfig.PUSH_APP_KEY)
            .post(body).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw java.io.IOException("relay answered ${response.code}")
        }
    }

    private fun register(token: String, emails: List<String>, previous: List<String>): Boolean = try {
        val body = JSONObject()
            .put("token", token)
            .put("emails", JSONArray(emails))
            .put("previous", JSONArray(previous))
            .toString().toRequestBody(json)
        val request = Request.Builder()
            .url(BuildConfig.PUSH_URL.trimEnd('/') + "/register")
            .header("X-Cemail-Key", BuildConfig.PUSH_APP_KEY)
            .post(body).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) AppLog.i("push", "relay answered ${response.code}")
            response.isSuccessful
        }
    } catch (e: Exception) {
        AppLog.e("push", "could not reach the relay", e)
        false
    }
}

/** Receives the wake-up and syncs the account it names. */
class PushService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        val app = application as CemailApplication
        app.appScope.launch { Push.sync(app, app.settings.state.value.pushEnabled) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        if (message.data["type"] != "gmail" && message.data["type"] != "microsoft") return
        val email = message.data["email"].orEmpty()
        val app = application as CemailApplication
        AppLog.i("push", "wake-up received")
        // A high-priority message gives the app a few seconds: the sync runs right here, with a limit.
        runBlocking(Dispatchers.IO) {
            withTimeoutOrNull(20_000) {
                val account = app.accountRepository.accounts.first().firstOrNull { it.email.equals(email, ignoreCase = true) }
                account?.let { app.mailRepositoryFor(it.id).refresh(quick = true) }
            }
        }
    }
}

/** Once a day: tells Gmail again to publish (its watch expires after about a week) and refreshes the registration. */
class PushRenewWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as CemailApplication
        return if (Push.sync(app, app.settings.state.value.pushEnabled)) Result.success() else Result.retry()
    }

    companion object {
        private const val NAME = "push-renew"

        fun schedule(context: Context, on: Boolean) {
            val manager = WorkManager.getInstance(context)
            if (!on) {
                manager.cancelUniqueWork(NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<PushRenewWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            manager.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
