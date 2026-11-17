package com.cemail.app.data.backup

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import com.cemail.app.AppLog
import com.cemail.app.BuildConfig
import com.cemail.app.data.SettingsRepository
import com.cemail.app.data.auth.AccountRepository
import com.cemail.app.data.db.LocalMarks
import com.cemail.app.data.db.MessageDao
import com.cemail.app.data.db.MutedThread
import com.cemail.app.data.imap.ImapConfig
import com.cemail.app.data.imap.Security
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File

/** The server settings of an IMAP account, as a backup keeps them: everything but the password. */
data class ImapServers(
    val username: String,
    val imapHost: String,
    val imapPort: Int,
    val imapSecurity: Security,
    val smtpHost: String,
    val smtpPort: Int,
    val smtpSecurity: Security,
)

/** An account listed in a backup: what is needed to add it again on another device. Never its tokens or password. */
data class BackupAccount(
    val id: String,
    /** GMAIL, MICROSOFT or IMAP, as in the accounts table. */
    val provider: String,
    val email: String,
    val displayName: String?,
    val imap: ImapServers? = null,
)

enum class RestoreResult { RESTORED, INVALID, TOO_NEW }

/**
 * The backup file of Cemail (Settings → Backup): one JSON file the user keeps where they like (Drive, Downloads…) and
 * brings back on this or another device, so that only signing in to the accounts is left to do.
 *
 * Format 2, read forward-compatibly: unknown fields are ignored and every section may be missing. Cemail versions that
 * only knew format 1 still take its "settings" and "signatures". If a future change cannot be read by older versions,
 * the file says so with "minReader" and those versions refuse it instead of restoring half of it.
 * ```
 * { "app": "cemail", "format": 2, "appVersion": "2.3.1", "createdAt": "2026-11-17T10:00:00Z",
 *   "settings": { key: {"b"|"i"|"l"|"f"|"s"|"set": value} },   // SharedPreferences "settings" as stored
 *   "signatures": { file name: html },                          // the imported HTML signatures
 *   "locale": "es-ES",                                          // the app's own language (Android 13+), if one is set
 *   "accounts": [ {"id", "provider", "email", "displayName", "imap": {username, hosts, ports, security}} ],
 *   "muted": [ {"accountId", "threadId"} ],
 *   "messages": [ {"accountId", "remoteId", "pinned", "snoozedUntil"} ] }
 * ```
 * Never in it: tokens, IMAP passwords, push keys and registrations, the cached mail and the Outbox.
 *
 * Restoring applies the settings at once (the app follows them by itself). Accounts that are not on the device are
 * offered as "Accounts to add"; muted conversations are stored at once (they are keyed by the server's ids); the
 * pinned and snoozed marks wait in [pendingFile] until the sync brings those messages, for up to [PENDING_DAYS] days.
 */
class Backup(
    context: Context,
    private val settings: SettingsRepository,
    private val accounts: AccountRepository,
    private val messages: MessageDao,
    private val scope: CoroutineScope,
) {
    private val app = context.applicationContext
    private val pendingFile = File(app.filesDir, "restore.json")
    private val lock = Mutex()
    private var watcher: Job? = null

    private val waiting = MutableStateFlow(readPending().accounts)

    /** Accounts of the restored backup that are not on this device yet. */
    val accountsToAdd: Flow<List<BackupAccount>> = combine(waiting, accounts.accounts) { wanted, present ->
        wanted.filter { a -> present.none { it.id == a.id } }
    }

    /** The whole backup, as the text of the file. */
    suspend fun export(): String = withContext(Dispatchers.IO) {
        val list = accounts.accounts.first()
        buildJsonObject {
            put("app", APP)
            put("format", FORMAT)
            put("appVersion", BuildConfig.VERSION_NAME)
            put("createdAt", java.time.Instant.now().toString())
            put("settings", settings.backupValues())
            put("signatures", settings.backupSignatures())
            appLocales().takeIf { it.isNotEmpty() }?.let { put("locale", it) }
            put("accounts", buildJsonArray {
                list.forEach { a ->
                    val imap = if (a.provider == "IMAP") accounts.imapConfig(a.id)?.servers() else null
                    add(BackupAccount(a.id, a.provider, a.email, a.displayName, imap).toJson())
                }
            })
            put("muted", buildJsonArray {
                messages.allMuted().forEach { m -> add(buildJsonObject { put("accountId", m.accountId); put("threadId", m.threadId) }) }
            })
            put("messages", buildJsonArray { messages.localMarks().forEach { add(it.toJson()) } })
        }.toString()
    }

    /** Applies a backup file; nothing changes when it is not one (or comes from a Cemail this one cannot read). */
    suspend fun restore(text: String): RestoreResult {
        val root = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return RestoreResult.INVALID
        if (root["app"].string() != APP) return RestoreResult.INVALID
        if ((root["minReader"].int() ?: 1) > FORMAT) return RestoreResult.TOO_NEW
        withContext(Dispatchers.IO) {
            (root["settings"] as? JsonObject)?.let { settings.restoreBackup(it, root["signatures"] as? JsonObject) }
            val restoredAccounts = root["accounts"].objects().mapNotNull { it.toAccount() }
            val muted = root["muted"].objects().mapNotNull { o ->
                val account = o["accountId"].string() ?: return@mapNotNull null
                val thread = o["threadId"].string() ?: return@mapNotNull null
                MutedThread(account, thread)
            }
            if (muted.isNotEmpty()) messages.muteAll(muted)
            val marks = root["messages"].objects().mapNotNull { it.toMarks() }
            lock.withLock {
                val old = readPending()
                val pending = Pending(
                    restoredAt = System.currentTimeMillis(),
                    accounts = (old.accounts.filter { o -> restoredAccounts.none { it.id == o.id } } + restoredAccounts),
                    marks = (old.marks + marks).associateBy { it.accountId to it.remoteId }.values.toList(),
                )
                writePending(pending)
                waiting.value = pending.accounts
            }
            AppLog.i("backup", "restored: ${restoredAccounts.size} account(s), ${muted.size} muted, ${marks.size} marked message(s)")
            applyPending()
        }
        watch()
        // Last: a new language recreates the screen.
        root["locale"].string()?.takeIf { it.isNotBlank() }?.let { tags ->
            withContext(Dispatchers.Main) { setAppLocales(tags) }
        }
        return RestoreResult.RESTORED
    }

    /** The user does not want to add the accounts of the backup that are still missing. */
    fun forgetAccountsToAdd() {
        scope.launch(Dispatchers.IO) {
            lock.withLock {
                writePending(readPending().copy(accounts = emptyList()))
                waiting.value = emptyList()
            }
        }
    }

    /**
     * Puts the pinned and snoozed marks back on the messages the sync has brought since, until none is left (or they
     * are too old). Started when the app starts and after a restore; it does nothing when no mark is waiting.
     */
    @OptIn(FlowPreview::class)
    fun watch() {
        synchronized(this) {
            if (watcher?.isActive == true || readPending().marks.isEmpty()) return
            watcher = scope.launch(Dispatchers.IO) {
                messages.changes().debounce(2_000).first { applyPending() == 0 }
                AppLog.i("backup", "every restored mark is back in place")
            }
        }
    }

    /** Applies the waiting marks to the messages that are already here; returns how many still wait. */
    private suspend fun applyPending(): Int = lock.withLock {
        val pending = readPending()
        if (pending.marks.isEmpty()) return@withLock 0
        val expired = System.currentTimeMillis() - pending.restoredAt > PENDING_DAYS * 24 * 3_600_000L
        val left = if (expired) emptyList() else pending.marks.filter { m ->
            // A snooze that ended meanwhile is put back as it was: the snooze wake-up brings it to the Inbox at once.
            messages.restoreMarks(m.accountId, m.remoteId, m.pinned, m.snoozedUntil) == 0
        }
        if (left.size != pending.marks.size) {
            AppLog.i("backup", "${pending.marks.size - left.size} restored mark(s) applied, ${left.size} waiting${if (expired) " (expired)" else ""}")
            writePending(pending.copy(marks = left))
        }
        left.size
    }

    private data class Pending(val restoredAt: Long = 0, val accounts: List<BackupAccount> = emptyList(), val marks: List<LocalMarks> = emptyList())

    private fun readPending(): Pending = try {
        if (!pendingFile.exists()) {
            Pending()
        } else {
            val o = Json.parseToJsonElement(pendingFile.readText()).jsonObject
            Pending(o["restoredAt"].long() ?: 0, o["accounts"].objects().mapNotNull { it.toAccount() }, o["marks"].objects().mapNotNull { it.toMarks() })
        }
    } catch (e: Exception) {
        AppLog.e("backup", "the waiting restore could not be read", e)
        Pending()
    }

    private fun writePending(p: Pending) {
        if (p.accounts.isEmpty() && p.marks.isEmpty()) {
            pendingFile.delete()
            return
        }
        pendingFile.writeText(
            buildJsonObject {
                put("restoredAt", p.restoredAt)
                put("accounts", JsonArray(p.accounts.map { it.toJson() }))
                put("marks", JsonArray(p.marks.map { it.toJson() }))
            }.toString(),
        )
    }

    /** The language chosen for Cemail alone (Android 13+ → Settings → Apps → Language), as tags; empty = the phone's. */
    private fun appLocales(): String =
        if (Build.VERSION.SDK_INT >= 33) app.getSystemService(LocaleManager::class.java)?.applicationLocales?.toLanguageTags().orEmpty() else ""

    private fun setAppLocales(tags: String) {
        if (Build.VERSION.SDK_INT < 33) return
        try {
            app.getSystemService(LocaleManager::class.java)?.applicationLocales = LocaleList.forLanguageTags(tags)
        } catch (e: Exception) {
            AppLog.e("backup", "the language could not be set", e)
        }
    }

    private companion object {
        const val APP = "cemail"
        /** The format this version writes and the newest one it can read. */
        const val FORMAT = 2
        const val PENDING_DAYS = 30
    }
}

private fun ImapConfig.servers() = ImapServers(username, imapHost, imapPort, imapSecurity, smtpHost, smtpPort, smtpSecurity)

private fun BackupAccount.toJson(): JsonObject = buildJsonObject {
    put("id", id)
    put("provider", provider)
    put("email", email)
    displayName?.let { put("displayName", it) }
    imap?.let { s ->
        put("imap", buildJsonObject {
            put("username", s.username)
            put("imapHost", s.imapHost); put("imapPort", s.imapPort); put("imapSecurity", s.imapSecurity.name)
            put("smtpHost", s.smtpHost); put("smtpPort", s.smtpPort); put("smtpSecurity", s.smtpSecurity.name)
        })
    }
}

private fun JsonObject.toAccount(): BackupAccount? {
    val id = this["id"].string() ?: return null
    val email = this["email"].string() ?: return null
    val imap = (this["imap"] as? JsonObject)?.let { s ->
        fun security(key: String) = Security.entries.firstOrNull { it.name == s[key].string() } ?: Security.SSL
        ImapServers(
            username = s["username"].string() ?: email,
            imapHost = s["imapHost"].string() ?: return@let null,
            imapPort = s["imapPort"].int() ?: 993,
            imapSecurity = security("imapSecurity"),
            smtpHost = s["smtpHost"].string() ?: return@let null,
            smtpPort = s["smtpPort"].int() ?: 465,
            smtpSecurity = security("smtpSecurity"),
        )
    }
    return BackupAccount(id, this["provider"].string() ?: "GMAIL", email, this["displayName"].string(), imap)
}

private fun LocalMarks.toJson(): JsonObject = buildJsonObject {
    put("accountId", accountId)
    put("remoteId", remoteId)
    put("pinned", pinned)
    snoozedUntil?.let { put("snoozedUntil", it) }
}

private fun JsonObject.toMarks(): LocalMarks? {
    val account = this["accountId"].string() ?: return null
    val remote = this["remoteId"].string() ?: return null
    val pinned = (this["pinned"] as? JsonPrimitive)?.booleanOrNull ?: false
    val until = this["snoozedUntil"].long()
    return if (!pinned && until == null) null else LocalMarks(account, remote, pinned, until)
}

private fun JsonElement?.string(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
private fun JsonElement?.int(): Int? = (this as? JsonPrimitive)?.intOrNull
private fun JsonElement?.long(): Long? = (this as? JsonPrimitive)?.longOrNull
private fun JsonElement?.objects(): List<JsonObject> = (this as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
