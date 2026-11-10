package com.cemail.app.data.mail

/**
 * Turns what the user typed into a full-text query (FTS4): every word must appear, and the last letters of a word are
 * optional ("fact" finds "factura"). Words are cleaned of anything that is not a letter or digit, because the symbols
 * of the query language ( " * - : ) would break the search. Returns null when there is nothing to search for or the
 * text uses Gmail-style operators (`from:`, `is:unread`...), which the list filters by themselves.
 */
fun ftsQuery(raw: String): String? {
    if (raw.contains(':')) return null
    val words = raw.trim().split(Regex("""\s+"""))
        .map { word -> word.filter { it.isLetterOrDigit() } }
        .filter { it.isNotEmpty() }
    if (words.isEmpty()) return null
    return words.joinToString(" ") { "$it*" }
}
