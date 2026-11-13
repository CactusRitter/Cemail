package com.cemail.app.data.gmail

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

    /** The newest [max] messages that match [query] (Gmail search words; may be empty) and, if given, carry [labelId]. */
    suspend fun listMessageIds(query: String, max: Int, labelId: String?): List<String> =
        if (labelId == null) listMessageIds(query, max) else listLabelMessageIds(labelId, max)
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

    /**
     * Saves a draft on the server and returns the id of its message there. With [replacingMessageId] (the message of a
     * draft saved before) that same draft is updated in place, so the server never holds more than one copy of it.
     */
    suspend fun saveDraft(raw: ByteArray, threadId: String?, replacingMessageId: String?): String? = null

    /** Deletes for good the draft whose message is [messageId] (after sending it, or when it is thrown away). */
    suspend fun deleteDraft(messageId: String) {}

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

    override suspend fun listMessageIds(query: String, max: Int, labelId: String?): List<String> = list(max) {
        if (query.isNotBlank()) addQueryParameter("q", query)
        labelId?.let { addQueryParameter("labelIds", it) }
    }

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

    override suspend fun saveDraft(raw: ByteArray, threadId: String?, replacingMessageId: String?): String? {
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
        val body = json.encodeToString(DraftRequest(SendRequest(encoded, threadId))).toRequestBody(jsonType)
        val draftId = replacingMessageId?.let { draftIdOf(it) }
        val drafts = BASE.newBuilder().addPathSegment("drafts")
        val response = if (draftId != null) {
            call(Request.Builder().url(drafts.addPathSegment(draftId).build()).put(body).build())
        } else {
            call(Request.Builder().url(drafts.build()).post(body).build())
        }
        return json.decodeFromString<GmailDraft>(response).message.id
    }

    override suspend fun deleteDraft(messageId: String) {
        val draftId = draftIdOf(messageId) ?: return
        call(Request.Builder().url(BASE.newBuilder().addPathSegment("drafts").addPathSegment(draftId).build()).delete().build())
    }

    /** Gmail addresses drafts by their own id, not by their message's: it is looked up in the list of drafts. */
    private suspend fun draftIdOf(messageId: String): String? {
        var page: String? = null
        do {
            val url = BASE.newBuilder().addPathSegment("drafts").addQueryParameter("maxResults", "500")
                .apply { page?.let { addQueryParameter("pageToken", it) } }.build()
            val list = json.decodeFromString<GmailDraftList>(call(Request.Builder().url(url).build()))
            list.drafts.firstOrNull { it.message.id == messageId }?.let { return it.id }
            page = list.nextPageToken
        } while (page != null)
        return null
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
        var attempt = 0
        while (true) {
            val token = tokens.accessToken() ?: throw GmailHttpException(401, "not signed in")
            val reply = withContext(Dispatchers.IO) {
                client.newCall(request.newBuilder().header("Authorization", "Bearer $token").build()).execute().use { response ->
                    Triple(response.code, response.body.string(), response.header("Retry-After")?.toLongOrNull())
                }
            }
            val (code, text, retryAfter) = reply
            if (code in 200..299) return text
            // Google's per-minute quota (a new account's first sync asks for many messages at once) clears by itself:
            // wait a little, with a random spread so the parallel requests do not all come back together, and ask again.
            if (attempt < MAX_RETRIES && isRateLimited(code, text)) {
                val wait = retryAfter?.times(1000) ?: minOf(30_000L, 4_000L shl attempt)
                delay(wait + (0..1500).random())
                attempt++
                continue
            }
            throw GmailHttpException(code, "Gmail API $code: ${text.lines().joinToString(" ") { it.trim() }.take(240)}")
        }
    }

    private fun isRateLimited(code: Int, body: String): Boolean =
        code == 429 || (code == 403 && (body.contains("rateLimitExceeded", true) || body.contains("Quota exceeded", true)))

    private companion object {
        val BASE = "https://gmail.googleapis.com/gmail/v1/users/me".toHttpUrl()
        const val MAX_RETRIES = 4
    }
}
