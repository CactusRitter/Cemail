package com.cemail.app.data.gmail

import kotlinx.serialization.Serializable

// Subset of the Gmail REST API resources we use (https://developers.google.com/gmail/api/reference/rest).

@Serializable
data class MessageList(val messages: List<MessageRef> = emptyList(), val nextPageToken: String? = null)

@Serializable
data class MessageRef(val id: String, val threadId: String? = null)

@Serializable
data class GmailMessage(
    val id: String,
    val threadId: String? = null,
    val labelIds: List<String> = emptyList(),
    val snippet: String = "",
    /** Milliseconds since the epoch, as a string. */
    val internalDate: String? = null,
    val payload: MessagePart? = null,
)

@Serializable
data class MessagePart(
    val mimeType: String = "",
    val filename: String = "",
    val headers: List<Header> = emptyList(),
    val body: PartBody? = null,
    val parts: List<MessagePart> = emptyList(),
)

@Serializable
data class Header(val name: String, val value: String)

@Serializable
data class PartBody(val size: Int = 0, val data: String? = null, val attachmentId: String? = null)

@Serializable
data class ModifyRequest(val addLabelIds: List<String> = emptyList(), val removeLabelIds: List<String> = emptyList())

@Serializable
data class SendRequest(val raw: String)
