package com.cemail.app.data.gmail

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Base64

/** The Gmail API answered with an error status (401 = the token is no longer valid). */
class GmailHttpException(val code: Int, message: String) : IOException(message)

/** Where the API gets its bearer token; null means the user must sign in again. */
fun interface AccessTokenProvider {
    suspend fun accessToken(): String?
}

/** What the sync and the actions need from Gmail. Tests use a fake of this interface. */
interface GmailApi {
    suspend fun listMessageIds(query: String, max: Int): List<String>

    /** The newest [max] messages that carry one label (by its id), wherever they are. */
    suspend fun listLabelMessageIds(labelId: String, max: Int): List<String> = emptyList()
    suspend fun getMessage(id: String): GmailMessage
    suspend fun getLabels(id: String): List<String>
    suspend fun listUserLabels(): List<GmailLabel>
    suspend fun getAttachment(messageId: String, attachmentId: String): ByteArray
    suspend fun modify(id: String, add: List<String>, remove: List<String>)
    /** The headers a reply needs (Message-ID, References, From, To, Cc...) and the thread id. */
    suspend fun getReplyInfo(id: String): ReplyInfo

    /** The values of some headers of a message (the first of each). */
    suspend fun getHeaders(id: String, names: List<String>): Map<String, String>

    /** The message exactly as RFC 822 text (what an .eml file holds). */
    suspend fun getRaw(id: String): ByteArray
    suspend fun send(raw: ByteArray, threadId: String? = null)

    /** Deletes messages for good (needs Gmail's full-access scope, not just "modify"). */
    suspend fun batchDelete(ids: List<String>)

    /**
     * Asks Gmail to publish a note to the Pub/Sub topic [topicName] whenever the Inbox changes (the push). It lasts about
     * a week, so it is asked again every day. [stopWatching] ends it.
     */
    suspend fun watch(topicName: String) {}
    suspend fun stopWatching() {}

    /** The mailbox's current history id: the starting point for [changesSince]. */
    suspend fun currentHistoryId(): String

    /**
     * What changed since [historyId]. Throws [GmailHttpException] with code 404 when that id is too old
     * (Gmail keeps about a week): the caller then does a full sync.
     */
    suspend fun changesSince(historyId: String): MailboxChanges
}

/** Gmail REST API over HTTPS with OkHttp. Network work always runs on [Dispatchers.IO]. */
class HttpGmailApi(
    private val tokens: AccessTokenProvider,
    private val client: OkHttpClient = OkHttpClient(),
) : GmailApi {
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    override suspend fun listMessageIds(query: String, max: Int): List<String> = list(max) { addQueryParameter("q", query) }

    override suspend fun listLabelMessageIds(labelId: String, max: Int): List<String> = list(max) { addQueryParameter("labelIds", labelId) }

    private suspend fun list(max: Int, filter: okhttp3.HttpUrl.Builder.() -> Unit): List<String> {
        val ids = mutableListOf<String>()
        var page: String? = null
        do {
            val url = BASE.newBuilder()
                .addPathSegment("messages")
                .apply(filter)
                .addQueryParameter("maxResults", minOf(100, max - ids.size).toString())
                .apply { page?.let { addQueryParameter("pageToken", it) } }
                .build()
            val list = json.decodeFromString<MessageList>(call(Request.Builder().url(url).build()))
            ids += list.messages.map { it.id }
            page = list.nextPageToken
        } while (page != null && ids.size < max)
        return ids
    }

    override suspend fun getMessage(id: String): GmailMessage {
        val url = BASE.newBuilder().addPathSegment("messages").addPathSegment(id).addQueryParameter("format", "full").build()
        return json.decodeFromString(call(Request.Builder().url(url).build()))
    }

    override suspend fun getLabels(id: String): List<String> {
        val url = BASE.newBuilder().addPathSegment("messages").addPathSegment(id)
            .addQueryParameter("format", "minimal").build()
        return json.decodeFromString<GmailMessage>(call(Request.Builder().url(url).build())).labelIds
    }

    override suspend fun listUserLabels(): List<GmailLabel> {
        val url = BASE.newBuilder().addPathSegment("labels").build()
        return json.decodeFromString<LabelList>(call(Request.Builder().url(url).build())).labels.filter { it.type == "user" }
    }

    override suspend fun getAttachment(messageId: String, attachmentId: String): ByteArray {
        val url = BASE.newBuilder().addPathSegment("messages").addPathSegment(messageId)
            .addPathSegment("attachments").addPathSegment(attachmentId).build()
        val body = json.decodeFromString<AttachmentBody>(call(Request.Builder().url(url).build()))
        return Base64.getUrlDecoder().decode(body.data)
    }

    override suspend fun modify(id: String, add: List<String>, remove: List<String>) {
        val url = BASE.newBuilder().addPathSegment("messages").addPathSegment(id).addPathSegment("modify").build()
        val body = json.encodeToString(ModifyRequest(add, remove)).toRequestBody(jsonType)
        call(Request.Builder().url(url).post(body).build())
    }

    override suspend fun getHeaders(id: String, names: List<String>): Map<String, String> {
        val url = BASE.newBuilder().addPathSegment("messages").addPathSegment(id)
            .addQueryParameter("format", "metadata")
            .apply { names.forEach { addQueryParameter("metadataHeaders", it) } }.build()
        val headers = json.decodeFromString<GmailMessage>(call(Request.Builder().url(url).build())).payload?.headers.orEmpty()
        return names.mapNotNull { n -> headers.firstOrNull { it.name.equals(n, true) }?.let { n to it.value } }.toMap()
    }

    override suspend fun getRaw(id: String): ByteArray {
        val url = BASE.newBuilder().addPathSegment("messages").addPathSegment(id).addQueryParameter("format", "raw").build()
        val raw = json.decodeFromString<GmailMessage>(call(Request.Builder().url(url).build())).raw ?: throw GmailHttpException(500, "no raw message")
        return Base64.getUrlDecoder().decode(raw)
    }

    override suspend fun getReplyInfo(id: String): ReplyInfo {
        val url = BASE.newBuilder().addPathSegment("messages").addPathSegment(id)
            .addQueryParameter("format", "metadata")
            .apply { ReplyInfoParser.HEADERS.forEach { addQueryParameter("metadataHeaders", it) } }.build()
        return ReplyInfoParser.parse(json.decodeFromString<GmailMessage>(call(Request.Builder().url(url).build())))
    }

    override suspend fun send(raw: ByteArray, threadId: String?) {
        val url = BASE.newBuilder().addPathSegment("messages").addPathSegment("send").build()
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
        val body = json.encodeToString(SendRequest(encoded, threadId)).toRequestBody(jsonType)
        call(Request.Builder().url(url).post(body).build())
    }

    override suspend fun batchDelete(ids: List<String>) {
        if (ids.isEmpty()) return
        val url = BASE.newBuilder().addPathSegment("messages").addPathSegment("batchDelete").build()
        call(Request.Builder().url(url).post(json.encodeToString(BatchDeleteRequest(ids)).toRequestBody(jsonType)).build())
    }

    override suspend fun watch(topicName: String) {
        val url = BASE.newBuilder().addPathSegment("watch").build()
        val body = """{"topicName":"$topicName","labelIds":["INBOX"],"labelFilterBehavior":"INCLUDE"}""".toRequestBody(jsonType)
        call(Request.Builder().url(url).post(body).build())
    }

    override suspend fun stopWatching() {
        val url = BASE.newBuilder().addPathSegment("stop").build()
        call(Request.Builder().url(url).post("{}".toRequestBody(jsonType)).build())
    }

    override suspend fun currentHistoryId(): String {
        val url = BASE.newBuilder().addPathSegment("profile").build()
        return json.decodeFromString<Profile>(call(Request.Builder().url(url).build())).historyId
    }

    override suspend fun changesSince(historyId: String): MailboxChanges {
        val added = linkedSetOf<String>()
        val relabelled = linkedSetOf<String>()
        val deleted = linkedSetOf<String>()
        var latest = historyId
        var page: String? = null
        do {
            val url = BASE.newBuilder().addPathSegment("history")
                .addQueryParameter("startHistoryId", historyId)
                .addQueryParameter("maxResults", "500")
                .apply {
                    listOf("messageAdded", "messageDeleted", "labelAdded", "labelRemoved").forEach { addQueryParameter("historyTypes", it) }
                    page?.let { addQueryParameter("pageToken", it) }
                }.build()
            val list = json.decodeFromString<HistoryList>(call(Request.Builder().url(url).build()))
            for (record in list.history) {
                record.messagesAdded.forEach { added += it.message.id }
                record.messagesDeleted.forEach { deleted += it.message.id }
                (record.labelsAdded + record.labelsRemoved).forEach { relabelled += it.message.id }
            }
            if (list.historyId.isNotEmpty()) latest = list.historyId
            page = list.nextPageToken
        } while (page != null)
        // A message deleted after it was added or relabelled needs neither download nor label check.
        added -= deleted
        relabelled -= deleted
        relabelled -= added
        return MailboxChanges(added, relabelled, deleted, latest)
    }

    private suspend fun call(request: Request): String {
        val token = tokens.accessToken() ?: throw GmailHttpException(401, "not signed in")
        return withContext(Dispatchers.IO) {
            client.newCall(request.newBuilder().header("Authorization", "Bearer $token").build()).execute().use { response ->
                val text = response.body.string()
                if (!response.isSuccessful) throw GmailHttpException(response.code, "Gmail API ${response.code}: ${text.replace(Regex("\\s+"), " ").take(240)}")
                text
            }
        }
    }

    private companion object {
        val BASE = "https://gmail.googleapis.com/gmail/v1/users/me".toHttpUrl()
    }
}
