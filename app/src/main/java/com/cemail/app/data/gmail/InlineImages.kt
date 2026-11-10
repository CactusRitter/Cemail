package com.cemail.app.data.gmail

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.Base64

/**
 * Decodes the bytes of a text part. Gmail usually hands text over already converted to UTF-8, whatever
 * charset the part's header declares, so valid UTF-8 wins and the declared charset is only the fallback.
 */
fun decodeText(bytes: ByteArray, declared: Charset): String = try {
    Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()
} catch (_: CharacterCodingException) {
    String(bytes, declared)
}

/** A picture that travels inside the message and is referenced from its HTML as `cid:<cid>`. */
class InlinePart(val cid: String, val mimeType: String, val data: ByteArray?, val attachmentId: String?)

object InlineImages {
    /** The image parts of [message] that have a Content-ID, i.e. the ones its HTML can point to. */
    fun find(message: GmailMessage): List<InlinePart> {
        val found = mutableListOf<InlinePart>()
        fun walk(part: MessagePart) {
            val cid = part.headers.firstOrNull { it.name.equals("Content-ID", true) }?.value
                ?.trim()?.removePrefix("<")?.removeSuffix(">")
            if (!cid.isNullOrEmpty() && part.mimeType.startsWith("image/", true) && MIME.matches(part.mimeType)) {
                val data = part.body?.data?.let { Base64.getUrlDecoder().decode(it) }
                if (data != null || part.body?.attachmentId != null) {
                    found += InlinePart(cid, part.mimeType.lowercase(), data, part.body?.attachmentId)
                }
            }
            part.parts.forEach(::walk)
        }
        message.payload?.let(::walk)
        return found
    }

    private val MIME = Regex("""image/[A-Za-z0-9.+-]+""")
}
