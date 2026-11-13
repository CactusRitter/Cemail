package com.cemail.app.update

/**
 * Notes of a version. Bundled ones (res/raw/changelog) are "## 1.2.3" sections; the notes of a release on GitHub
 * may carry one section per language ("## en", "## es", "## de") or just plain text.
 */
object ReleaseNotes {
    private fun sections(text: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        var key: String? = null
        val lines = StringBuilder()
        fun flush() { key?.let { result[it] = lines.toString().trim() }; lines.clear() }
        for (line in text.lines()) {
            if (line.startsWith("## ")) { flush(); key = line.removePrefix("## ").trim() } else lines.appendLine(line)
        }
        flush()
        return result
    }

    /** The notes of [version] in a bundled changelog, or null. */
    fun forVersion(changelog: String, version: String): String? = sections(changelog)[version]?.takeIf { it.isNotEmpty() }

    /** The part of a GitHub release body written in [language] (else English, else the whole body). */
    fun forLanguage(body: String, language: String): String {
        val parts = sections(body).filterKeys { it.length == 2 }
        if (parts.isEmpty()) return body.trim()
        return (parts[language] ?: parts["en"] ?: parts.values.first()).trim()
    }

    /** Every version in a bundled changelog, newest first, with its notes. */
    fun all(changelog: String): List<Pair<String, String>> = sections(changelog).map { it.key to it.value }
}
