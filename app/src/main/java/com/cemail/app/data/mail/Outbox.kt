package com.cemail.app.data.mail

import com.cemail.app.data.gmail.InlineImage
import com.cemail.app.data.gmail.OutAttachment
import java.io.File

/**
 * The files of messages waiting in the Outbox (scheduled, or in the few seconds of "undo send"): attachments and the
 * pictures of an HTML signature live here, one folder per message, until it is sent or cancelled. The text itself is
 * a row in the database.
 */
object Outbox {
    private var root: File? = null

    fun init(dir: File) {
        root = dir
    }

    private fun dir(id: Long) = File(root ?: error("Outbox not initialised"), id.toString())

    fun save(id: Long, attachments: List<OutAttachment>, images: List<InlineImage>) {
        if (attachments.isEmpty() && images.isEmpty()) return
        val d = dir(id).apply { mkdirs() }
        File(d, "attachments.idx").writeText(attachments.mapIndexed { n, a ->
            File(d, "att_$n").writeBytes(a.bytes)
            "$n\t${a.mimeType}\t${a.name.replace('\n', ' ').replace('\t', ' ')}"
        }.joinToString("\n"))
        File(d, "images.idx").writeText(images.mapIndexed { n, i ->
            File(d, "img_$n").writeBytes(i.bytes)
            "$n\t${i.mimeType}\t${i.cid}"
        }.joinToString("\n"))
    }

    fun load(id: Long): Pair<List<OutAttachment>, List<InlineImage>> {
        val d = dir(id)
        val attachments = File(d, "attachments.idx").takeIf { it.exists() }?.readLines().orEmpty().filter { it.isNotBlank() }.mapNotNull { line ->
            val (n, mime, name) = line.split("\t", limit = 3).takeIf { it.size == 3 } ?: return@mapNotNull null
            File(d, "att_$n").takeIf { it.exists() }?.let { OutAttachment(name, mime, it.readBytes()) }
        }
        val images = File(d, "images.idx").takeIf { it.exists() }?.readLines().orEmpty().filter { it.isNotBlank() }.mapNotNull { line ->
            val (n, mime, cid) = line.split("\t", limit = 3).takeIf { it.size == 3 } ?: return@mapNotNull null
            File(d, "img_$n").takeIf { it.exists() }?.let { InlineImage(cid, mime, it.readBytes()) }
        }
        return attachments to images
    }

    fun delete(id: Long) {
        dir(id).deleteRecursively()
    }

    /** How long a message that cannot be sent (no connection) is retried before it falls back to Drafts. */
    const val GIVE_UP_MILLIS = 30L * 60 * 1000

    /** Time between two attempts. */
    const val RETRY_MILLIS = 2L * 60 * 1000
}
