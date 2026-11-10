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
    /** The whole message as RFC 822 text, base64url (only with format=raw). */
    val raw: String? = null,
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
data class SendRequest(val raw: String, val threadId: String? = null)

@Serializable
data class LabelList(val labels: List<GmailLabel> = emptyList())

/** [type] is "system" (INBOX, SENT, ...) or "user". */
@Serializable
data class GmailLabel(val id: String, val name: String, val type: String = "user")

@Serializable
data class AttachmentBody(val size: Int = 0, val data: String = "")

@Serializable
data class Profile(val historyId: String = "")

@Serializable
data class HistoryMessage(val message: MessageRef)

@Serializable
data class HistoryRecord(
    val messagesAdded: List<HistoryMessage> = emptyList(),
    val messagesDeleted: List<HistoryMessage> = emptyList(),
    val labelsAdded: List<HistoryMessage> = emptyList(),
    val labelsRemoved: List<HistoryMessage> = emptyList(),
)

@Serializable
data class HistoryList(
    val history: List<HistoryRecord> = emptyList(),
    val nextPageToken: String? = null,
    val historyId: String = "",
)

/** What changed in a mailbox since a history id: message ids to download, to re-read the labels of, and to drop. */
data class MailboxChanges(
    val added: Set<String>,
    val relabelled: Set<String>,
    val deleted: Set<String>,
    val historyId: String,
)
