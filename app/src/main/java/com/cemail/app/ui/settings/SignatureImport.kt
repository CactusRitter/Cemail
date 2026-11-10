package com.cemail.app.ui.settings

import android.content.Context
import android.net.Uri
import com.cemail.app.AppLog
import com.cemail.app.data.gmail.EmlParser
import com.cemail.app.data.gmail.ParsedEml
import com.cemail.app.data.gmail.SignatureHtml

/**
 * Reads a signature from a file the user picked: an .eml message (the signature block is found inside it) or a
 * plain .html file. Returns the safe HTML, or null if nothing usable is in the file.
 */
fun readSignatureFile(context: Context, uri: Uri): String? = try {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    if (bytes.size > 8 * 1024 * 1024) return null
    val head = String(bytes, 0, minOf(bytes.size, 2000), Charsets.ISO_8859_1).trimStart('ï', '»', '¿', ' ', '\n', '\r', '\t')
    val parsed = if (head.startsWith("<")) {
        ParsedEml(null, String(bytes, Charsets.UTF_8), null, emptyMap())
    } else {
        EmlParser.parse(bytes)
    }
    SignatureHtml.extract(parsed)
} catch (e: Exception) {
    AppLog.e("signature", "could not read the signature file", e)
    null
}
