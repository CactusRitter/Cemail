package com.cemail.app.data.gmail

/** A file attached to a received message, as listed by Gmail (the bytes are downloaded when it is opened). */
data class AttachmentInfo(val attachmentId: String, val filename: String, val mimeType: String, val size: Int)

/** A file the user is attaching to a message being written. */
class OutAttachment(val name: String, val mimeType: String, val bytes: ByteArray) {
    val size: Int get() = bytes.size
}

object AttachmentFinder {
    /**
     * The real attachments of [message]: parts with a file name that Gmail keeps as a separate download.
     * Pictures that belong to the message body (they have a Content-ID) are shown inside it instead.
     */
    fun find(message: GmailMessage): List<AttachmentInfo> {
        val found = mutableListOf<AttachmentInfo>()
        fun walk(part: MessagePart) {
            val id = part.body?.attachmentId
            if (part.filename.isNotBlank() && id != null) {
                val inlinePicture = part.mimeType.startsWith("image/", ignoreCase = true) &&
                    part.headers.any { it.name.equals("Content-ID", true) }
                if (!inlinePicture) {
                    found += AttachmentInfo(id, part.filename, part.mimeType.ifBlank { "application/octet-stream" }, part.body.size)
                }
            }
            part.parts.forEach(::walk)
        }
        message.payload?.let(::walk)
        return found
    }

    /** A name that is safe to use as a file name on the phone. */
    fun safeFileName(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|\u0000-\u001f]"""), "_").trim().trim('.').take(120).ifEmpty { "attachment" }
}
