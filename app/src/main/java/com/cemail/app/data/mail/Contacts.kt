package com.cemail.app.data.mail

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** An address offered while typing a recipient. */
data class Suggestion(val name: String, val email: String)

/**
 * Addresses to offer while typing a recipient: the phone's contacts (if the user allowed it) and the people who
 * wrote to the user. Nothing leaves the phone.
 */
object ContactSuggestions {
    fun hasPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** Up to [limit] matches for [query] (in the name or the address), contacts first, without repeats. */
    suspend fun search(context: Context, query: String, senders: List<Suggestion>, limit: Int = 5): List<Suggestion> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        val fromContacts = if (hasPermission(context)) withContext(Dispatchers.IO) { contacts(context, q, limit * 2) } else emptyList()
        val fromSenders = senders.filter { it.matches(q) }
        return (fromContacts + fromSenders).distinctBy { it.email.lowercase() }.take(limit)
    }

    private fun Suggestion.matches(q: String) = name.contains(q, ignoreCase = true) || email.contains(q, ignoreCase = true)

    private fun contacts(context: Context, q: String, max: Int): List<Suggestion> = try {
        val found = mutableListOf<Suggestion>()
        val like = "%${q.replace("%", "").replace("_", "")}%"
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Email.DISPLAY_NAME_PRIMARY, ContactsContract.CommonDataKinds.Email.ADDRESS),
            "${ContactsContract.CommonDataKinds.Email.DISPLAY_NAME_PRIMARY} LIKE ? OR ${ContactsContract.CommonDataKinds.Email.ADDRESS} LIKE ?",
            arrayOf(like, like),
            // (Android stopped counting how often each contact is used, so the list simply goes by name.)
            ContactsContract.CommonDataKinds.Email.DISPLAY_NAME_PRIMARY,
        )?.use { cursor ->
            while (cursor.moveToNext() && found.size < max) {
                val address = cursor.getString(1)?.trim().orEmpty()
                if (address.isNotEmpty()) found += Suggestion(cursor.getString(0).orEmpty(), address)
            }
        }
        found
    } catch (_: Exception) {
        emptyList() // permission taken away meanwhile, or no contacts provider
    }
}

/** The people who sent messages to the user, most frequent first (one entry per address). */
fun sendersOf(mails: List<Mail>): List<Suggestion> = mails
    .filter { it.folder != Folder.SENT && it.folder != Folder.DRAFTS && it.senderEmail.isNotBlank() }
    .groupBy { it.senderEmail.lowercase() }
    .values
    .sortedByDescending { it.size }
    .map { group ->
        val named = group.firstOrNull { it.senderName.isNotBlank() && !it.senderName.equals(it.senderEmail, ignoreCase = true) }
        Suggestion(named?.senderName.orEmpty(), group.first().senderEmail)
    }
