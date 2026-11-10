package com.cemail.app.data.mail

import java.io.File
import java.util.Base64

/** A picture that travels inside a message and is referenced from its HTML as `cid:<cid>`. */
class FetchedImage(val cid: String, val mimeType: String, val bytes: ByteArray)

/**
 * Keeps the embedded pictures of a message in a folder (fetched once, then read from the phone) and hands them to
 * the page as `data:` URIs. Used by the IMAP and Microsoft accounts; Gmail has its own copy of this logic.
 */
object InlineCache {
    private const val INDEX = "index.v1"
    private const val MAX_BYTES = 4 * 1024 * 1024

    /** Cid → `data:` URI for the message whose pictures live in [dir]; [fetch] downloads them the first time. */
    fun load(dir: File, fetch: () -> List<FetchedImage>): Map<String, String> {
        val index = File(dir, INDEX)
        if (!index.exists()) {
            dir.mkdirs()
            val lines = fetch().filter { it.bytes.size <= MAX_BYTES }.mapIndexed { n, image ->
                File(dir, "$n").writeBytes(image.bytes)
                "${image.cid}\t${image.mimeType}\t$n"
            }
            index.writeText(lines.joinToString("\n"))
        }
        return index.readLines().filter { it.isNotBlank() }.mapNotNull { line ->
            val parts = line.split("\t")
            val file = parts.getOrNull(2)?.let { File(dir, it) }?.takeIf { it.exists() } ?: return@mapNotNull null
            parts[0] to "data:${parts[1]};base64," + Base64.getEncoder().encodeToString(file.readBytes())
        }.toMap()
    }

    /** `<abc@host>` → `abc@host` */
    fun cleanCid(raw: String?): String? = raw?.trim()?.removePrefix("<")?.removeSuffix(">")?.takeIf { it.isNotEmpty() }
}
