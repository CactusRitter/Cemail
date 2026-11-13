package com.cemail.app.data.graph

import com.cemail.app.data.gmail.AccessTokenProvider
import com.cemail.app.data.gmail.AttachmentInfo
import com.cemail.app.data.gmail.GmailHttpException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64

@Serializable
data class GraphAddress(val name: String? = null, val address: String? = null)

@Serializable
data class GraphRecipient(val emailAddress: GraphAddress? = null)

@Serializable
data class GraphFlag(val flagStatus: String = "notFlagged")

@Serializable
data class GraphBody(val contentType: String = "text", val content: String = "")

@Serializable
data class GraphHeader(val name: String, val value: String)

@Serializable
data class GraphMessage(
    val id: String,
    val subject: String? = null,
    val from: GraphRecipient? = null,
    val toRecipients: List<GraphRecipient> = emptyList(),
    val ccRecipients: List<GraphRecipient> = emptyList(),
    val replyTo: List<GraphRecipient> = emptyList(),
    val receivedDateTime: String? = null,
    val isRead: Boolean = false,
    val flag: GraphFlag? = null,
    val hasAttachments: Boolean = false,
    val body: GraphBody? = null,
    val conversationId: String? = null,
    val internetMessageId: String? = null,
    val internetMessageHeaders: List<GraphHeader> = emptyList(),
)

@Serializable
data class GraphPage<T>(val value: List<T> = emptyList(), @SerialName("@odata.nextLink") val next: String? = null)

@Serializable
data class GraphId(val id: String)

@Serializable
data class GraphAttachment(
    val id: String,
    val name: String = "",
    val contentType: String = "application/octet-stream",
    val size: Int = 0,
    val isInline: Boolean = false,
)

@Serializable
data class GraphInline(
    val contentType: String = "",
    val contentId: String? = null,
    val contentBytes: String? = null,
    val isInline: Boolean = false,
)

/** What the Microsoft repository needs from Microsoft Graph. */
class GraphApi(
    private val tokens: AccessTokenProvider,
    // A full sync lists whole folders with their bodies: Microsoft can take well over the default 10 s to answer.
    private val client: OkHttpClient = OkHttpClient.Builder()
        .dns(com.cemail.app.data.net.Ipv4First)
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build(),
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val textType = "text/plain".toMediaType()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    /** The newest [max] messages of a folder (well-known name: inbox, sentitems, drafts, deleteditems, archive). */
    suspend fun messages(folder: String, max: Int): List<GraphMessage> {
        val all = mutableListOf<GraphMessage>()
        var url: String? = "$BASE/mailFolders/$folder/messages".toHttpUrl().newBuilder()
            .addQueryParameter("\$top", minOf(50, max).toString())
            .addQueryParameter("\$orderby", "receivedDateTime desc")
            .addQueryParameter("\$select", SELECT)
            .build().toString()
        while (url != null && all.size < max) {
            val page = json.decodeFromString(PAGE, get(url))
            all += page.value
            url = page.next
        }
        return all.take(max)
    }

    /** The ids of the newest [max] messages of a folder, without anything else (cheap: Load more looks past them). */
    suspend fun ids(folder: String, max: Int): List<String> {
        val all = mutableListOf<String>()
        var url: String? = "$BASE/mailFolders/$folder/messages".toHttpUrl().newBuilder()
            .addQueryParameter("\$top", minOf(1000, max).toString())
            .addQueryParameter("\$orderby", "receivedDateTime desc")
            .addQueryParameter("\$select", "id")
            .build().toString()
        while (url != null && all.size < max) {
            val page = json.decodeFromString(IDS, get(url))
            all += page.value.map { it.id }
            url = page.next
        }
        return all.take(max)
    }

    /** One message with everything a row of the list needs. */
    suspend fun listed(id: String): GraphMessage = message(id, SELECT)

    /**
     * Up to [max] messages of a folder that contain [query] (in the subject, the sender or the text), whatever their
     * date. Microsoft returns them by relevance, without paging.
     */
    suspend fun search(folder: String, query: String, max: Int): List<String> {
        // The words go between double quotes, so they cannot have their own.
        val words = query.replace("\"", " ").trim()
        if (words.isEmpty()) return emptyList()
        // Only the ids (cheap): the messages the phone does not have yet are then read one by one ([listed]).
        val url = "$BASE/mailFolders/$folder/messages".toHttpUrl().newBuilder()
            .addQueryParameter("\$search", "\"$words\"")
            .addQueryParameter("\$top", max.toString())
            .addQueryParameter("\$select", "id")
            .build().toString()
        return json.decodeFromString(IDS, get(url)).value.map { it.id }
    }

    suspend fun message(id: String, select: String): GraphMessage =
        json.decodeFromString(GraphMessage.serializer(), get("$BASE/messages/$id?\$select=$select"))

    suspend fun update(id: String, isRead: Boolean?, flagged: Boolean?) {
        val body = buildJsonObject {
            isRead?.let { put("isRead", it) }
            flagged?.let { put("flag", buildJsonObject { put("flagStatus", if (it) "flagged" else "notFlagged") }) }
        }
        send(Request.Builder().url("$BASE/messages/$id").patch(body.toString().toRequestBody(jsonType)))
    }

    /** Moves a message to a folder (well-known name); it gets a new id there. */
    suspend fun move(id: String, destination: String) {
        val body = buildJsonObject { put("destinationId", destination) }
        send(Request.Builder().url("$BASE/messages/$id/move").post(body.toString().toRequestBody(jsonType)))
    }

    suspend fun delete(id: String) {
        send(Request.Builder().url("$BASE/messages/$id").delete())
    }

    /** Saves a complete MIME message as a new draft in Drafts and returns its id. */
    suspend fun createDraft(raw: ByteArray): String {
        val encoded = Base64.getEncoder().encodeToString(raw)
        val reply = execute(Request.Builder().url("$BASE/messages").post(encoded.toRequestBody(textType))) { it.body.string() }
        return json.decodeFromString(GraphMessage.serializer(), reply).id
    }

    /** Deletes a message for good (not into Deleted Items): an old copy of a draft that was saved again. */
    suspend fun permanentDelete(id: String) {
        send(Request.Builder().url("$BASE/messages/$id/permanentDelete").post(ByteArray(0).toRequestBody(null)))
    }

    /** Sends a complete MIME message; Microsoft keeps the copy in Sent Items by itself. */
    suspend fun sendMime(raw: ByteArray) {
        val encoded = Base64.getEncoder().encodeToString(raw)
        send(Request.Builder().url("$BASE/sendMail").post(encoded.toRequestBody(textType)))
    }

    suspend fun raw(id: String): ByteArray = bytes("$BASE/messages/$id/\$value")

    suspend fun attachments(id: String): List<AttachmentInfo> {
        val url = "$BASE/messages/$id/attachments".toHttpUrl().newBuilder()
            .addQueryParameter("\$select", "id,name,contentType,size,isInline").build().toString()
        return json.decodeFromString(ATTACHMENTS, get(url)).value
            .filter { !it.isInline && it.name.isNotBlank() }
            .map { AttachmentInfo(it.id, it.name, it.contentType, it.size) }
    }

    /** The pictures a message embeds (inline attachments with a Content-ID), with their bytes. */
    suspend fun inlineImages(id: String): List<com.cemail.app.data.mail.FetchedImage> {
        val url = "$BASE/messages/$id/attachments".toHttpUrl().newBuilder()
            .addQueryParameter("${'$'}filter", "isInline eq true").build().toString()
        return json.decodeFromString(GraphPage.serializer(GraphInline.serializer()), get(url)).value.mapNotNull { a ->
            val cid = com.cemail.app.data.mail.InlineCache.cleanCid(a.contentId) ?: return@mapNotNull null
            val bytes = a.contentBytes?.let { runCatching { Base64.getMimeDecoder().decode(it) }.getOrNull() } ?: return@mapNotNull null
            if (!a.contentType.startsWith("image/", ignoreCase = true)) null
            else com.cemail.app.data.mail.FetchedImage(cid, a.contentType.lowercase(), bytes)
        }
    }

    /**
     * Asks Microsoft to call [notificationUrl] whenever a message arrives in the Inbox (change notifications, used for
     * push). Microsoft first validates the address by calling it; [clientState] comes back with every notification.
     * Returns the subscription id. Subscriptions for mail last at most about 3 days, so they are renewed daily.
     */
    suspend fun subscribe(notificationUrl: String, clientState: String, expires: java.time.Instant): String {
        val body = buildJsonObject {
            put("changeType", "created")
            put("notificationUrl", notificationUrl)
            put("resource", "me/mailFolders('inbox')/messages")
            put("expirationDateTime", expires.toString())
            put("clientState", clientState)
        }
        val reply = execute(Request.Builder().url(SUBSCRIPTIONS).post(body.toString().toRequestBody(jsonType))) { it.body.string() }
        return (json.parseToJsonElement(reply) as JsonObject)["id"]?.let { (it as JsonPrimitive).content }
            ?: throw GmailHttpException(500, "Microsoft Graph subscription without id")
    }

    /** Extends a subscription; false when Microsoft no longer knows it (it expired), so a new one is needed. */
    suspend fun renewSubscription(id: String, expires: java.time.Instant): Boolean {
        val body = buildJsonObject { put("expirationDateTime", expires.toString()) }
        return try {
            send(Request.Builder().url("$SUBSCRIPTIONS/$id").patch(body.toString().toRequestBody(jsonType)))
            true
        } catch (e: GmailHttpException) {
            if (e.code == 404) false else throw e
        }
    }

    suspend fun unsubscribe(id: String) {
        try {
            send(Request.Builder().url("$SUBSCRIPTIONS/$id").delete())
        } catch (e: GmailHttpException) {
            if (e.code != 404) throw e
        }
    }

    suspend fun attachmentBytes(messageId: String, attachmentId: String): ByteArray =
        bytes("$BASE/messages/$messageId/attachments/$attachmentId/\$value")

    private suspend fun get(url: String): String = String(bytes(url), Charsets.UTF_8)

    private suspend fun bytes(url: String): ByteArray = execute(Request.Builder().url(url).header("Prefer", "outlook.body-content-type=\"html\"")) { it.body.bytes() }

    private suspend fun send(builder: Request.Builder) {
        execute(builder) { it.body.string() }
    }

    private suspend fun <T> execute(builder: Request.Builder, read: (okhttp3.Response) -> T): T {
        val token = tokens.accessToken() ?: throw GmailHttpException(401, "not signed in")
        val request = builder.header("Authorization", "Bearer $token").build()
        return withContext(Dispatchers.IO) {
            var attempt = 0
            var serverTries = 0
            // Microsoft answering 500/502/503/504/429 (an outage on their side): wait and ask again. Only for requests that
            // are safe to repeat: reads, flag changes, moves and deletes; never a send or a new draft.
            val repeatable = request.method in setOf("GET", "PATCH", "DELETE") || request.url.encodedPath.endsWith("/move")
            val serverPauses = if (request.method == "GET") SERVER_PAUSES_READ else SERVER_PAUSES_WRITE
            while (true) {
                try {
                    return@withContext client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            if (repeatable && response.code in SERVER_ERRORS && serverTries < serverPauses.size) {
                                val asked = response.header("Retry-After")?.toLongOrNull()?.times(1_000)
                                throw ServerBusy(minOf(asked ?: serverPauses[serverTries], 20_000L))
                            }
                            // Only Microsoft's error code (never the body): enough to tell what went wrong in the log.
                            val code = Regex("\"code\"\\s*:\\s*\"(\\w+)\"").find(runCatching { response.body.string() }.getOrDefault(""))?.groupValues?.get(1)
                            throw GmailHttpException(response.code, "Microsoft Graph ${response.code}${code?.let { " $it" }.orEmpty()}")
                        }
                        read(response)
                    }
                } catch (e: ServerBusy) {
                    serverTries++
                    kotlinx.coroutines.delay(e.pauseMillis)
                } catch (e: java.net.ConnectException) {
                    // A first attempt on a dead route (often right after the app starts): one more try before giving up.
                    if (++attempt > 1) throw e
                } catch (e: java.net.UnknownHostException) {
                    // No DNS for a moment (the network is only just coming up): the request never left, ask once more.
                    if (++attempt > 1) throw e
                    kotlinx.coroutines.delay(2_000)
                } catch (e: java.net.SocketTimeoutException) {
                    // Microsoft answering slowly: one more try after a short pause. Only for reads, so a message that
                    // was in fact sent is never sent twice.
                    if (request.method != "GET" || ++attempt > 1) throw e
                    kotlinx.coroutines.delay(1_500)
                }
            }
            @Suppress("UNREACHABLE_CODE") error("unreachable")
        }
    }

    /** Microsoft is busy or down: ask again after [pauseMillis] (never leaves [execute]). */
    private class ServerBusy(val pauseMillis: Long) : java.io.IOException("Microsoft busy")

    private companion object {
        val SERVER_ERRORS = setOf(429, 500, 502, 503, 504)
        val SERVER_PAUSES_READ = longArrayOf(2_000, 6_000)
        val SERVER_PAUSES_WRITE = longArrayOf(2_000, 6_000, 15_000)
        const val BASE = "https://graph.microsoft.com/v1.0/me"
        const val SUBSCRIPTIONS = "https://graph.microsoft.com/v1.0/subscriptions"
        const val SELECT = "id,subject,from,toRecipients,receivedDateTime,isRead,flag,hasAttachments,body,conversationId"
        val PAGE: KSerializer<GraphPage<GraphMessage>> = GraphPage.serializer(GraphMessage.serializer())
        val ATTACHMENTS: KSerializer<GraphPage<GraphAttachment>> = GraphPage.serializer(GraphAttachment.serializer())
        val IDS: KSerializer<GraphPage<GraphId>> = GraphPage.serializer(GraphId.serializer())
    }
}

