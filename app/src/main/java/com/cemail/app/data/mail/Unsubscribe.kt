package com.cemail.app.data.mail

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

/** How a mailing list lets the user leave (from the `List-Unsubscribe` header of its messages). */
data class UnsubscribeInfo(
    /** A web address (always https), or null. */
    val https: String?,
    /** An address to write to ("mailto:..." split in the address and the subject), or null. */
    val mailto: String?,
    val mailtoSubject: String?,
    /** The list accepts a plain POST (`List-Unsubscribe-Post: List-Unsubscribe=One-Click`), no page to open. */
    val oneClick: Boolean,
)

object Unsubscribe {
    /** Reads the two headers; null if the message gives no usable way to unsubscribe. */
    fun parse(listUnsubscribe: String?, listUnsubscribePost: String?): UnsubscribeInfo? {
        val links = Regex("<([^>]+)>").findAll(listUnsubscribe.orEmpty()).map { it.groupValues[1].trim() }.toList()
        val https = links.firstOrNull { it.startsWith("https://", ignoreCase = true) }
        val mailtoRaw = links.firstOrNull { it.startsWith("mailto:", ignoreCase = true) }
        val address = mailtoRaw?.removePrefix("mailto:")?.removePrefix("MAILTO:")?.substringBefore('?')?.takeIf { it.contains('@') }
        val subject = mailtoRaw?.substringAfter('?', "")?.split('&')
            ?.firstOrNull { it.startsWith("subject=", ignoreCase = true) }
            ?.substringAfter('=')?.let { java.net.URLDecoder.decode(it, "UTF-8") }
        if (https == null && address == null) return null
        val oneClick = https != null && listUnsubscribePost.orEmpty().contains("List-Unsubscribe=One-Click", ignoreCase = true)
        return UnsubscribeInfo(https, address, subject, oneClick)
    }

    /** The "one click" way: a POST to the list's address. true if the server accepted it. */
    suspend fun postOneClick(url: String, client: OkHttpClient = OkHttpClient()): Boolean = withContext(Dispatchers.IO) {
        if (!url.startsWith("https://", ignoreCase = true)) return@withContext false
        try {
            val body = FormBody.Builder().add("List-Unsubscribe", "One-Click").build()
            client.newCall(Request.Builder().url(url).post(body).build()).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    val HEADERS = listOf("List-Unsubscribe", "List-Unsubscribe-Post")
}
