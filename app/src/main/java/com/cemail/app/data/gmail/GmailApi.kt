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
    suspend fun getMessage(id: String): GmailMessage
    suspend fun getLabels(id: String): List<String>
    suspend fun listUserLabels(): List<GmailLabel>
    suspend fun getAttachment(messageId: String, attachmentId: String): ByteArray
    suspend fun modify(id: String, add: List<String>, remove: List<String>)
    /** The headers a reply needs (Message-ID, References, From, To, Cc...) and the thread id. */
    suspend fun getReplyInfo(id: String): ReplyInfo

    /** The message exactly as RFC 822 text (what an .eml file holds). */
    suspend fun getRaw(id: String): ByteArray
    suspend fun send(raw: ByteArray, threadId: String? = null)
}

/** Gmail REST API over HTTPS with OkHttp. Network work always runs on [Dispatchers.IO]. */
class HttpGmailApi(
    private val tokens: AccessTokenProvider,
    private val client: OkHttpClient = OkHttpClient(),
) : GmailApi {
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    override suspend fun listMessageIds(query: String, max: Int): List<String> {
        val ids = mutableListOf<String>()
        var page: String? = null
        do {
            val url = BASE.newBuilder()
                .addPathSegment("messages")
                .addQueryParameter("q", query)
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

    private suspend fun call(request: Request): String {
        val token = tokens.accessToken() ?: throw GmailHttpException(401, "not signed in")
        return withContext(Dispatchers.IO) {
            client.newCall(request.newBuilder().header("Authorization", "Bearer $token").build()).execute().use { response ->
                val text = response.body.string()
                if (!response.isSuccessful) throw GmailHttpException(response.code, "Gmail API ${response.code}")
                text
            }
        }
    }

    private companion object {
        val BASE = "https://gmail.googleapis.com/gmail/v1/users/me".toHttpUrl()
    }
}
