package com.cemail.app.notify

/**
 * Clean text for a notification: the message as plain prose, without pictures, addresses, separator lines,
 * Markdown syntax or the "view in browser / unsubscribe" boilerplate of newsletters.
 */
object NotificationText {
    private val MARKDOWN_IMAGE = Regex("""!\[[^\]]*]\([^)]*\)""")
    private val MARKDOWN_LINK = Regex("""\[([^\]]*)]\([^)]*\)""")
    private val ANGLE_URL = Regex("""<\s*(?:https?://|www\.|mailto:)[^>\s]*\s*>""", RegexOption.IGNORE_CASE)
    private val BARE_URL = Regex("""(?:https?://|www\.)\S+""", RegexOption.IGNORE_CASE)
    private val IMAGE_PLACEHOLDER = Regex("""\[(?:image|cid)[^\]]*]""", RegexOption.IGNORE_CASE)
    private val SEPARATOR_LINE = Regex("""^[\s\-=_*~#.•·+|]{3,}$""")
    private val EMPTY_BRACKETS = Regex("""[<\[(]\s*[>\])]""")
    private val MARKUP = Regex("""[*_`]{1,3}(\S(?:.*?\S)?)[*_`]{1,3}""")
    private val HEADING = Regex("""^\s{0,3}#{1,6}\s+""")
    private val WHITESPACE = Regex("""[ \t ]+""")

    /** Lines that only say "see this online", "unsubscribe" and the like (several languages). */
    private val BOILERPLATE = Regex(
        """(?i)\b(webansicht|webview|web[- ]?version|view (?:this )?(?:email |e-mail |message )?(?:in|online|in your)|view in (?:your )?browser|""" +
            """open in (?:your )?browser|click here to view|ver (?:este )?(?:correo|mensaje|email) (?:en|online)|ver en (?:el )?navegador|""" +
            """im browser (?:ansehen|öffnen|anzeigen)|newsletter nicht (?:lesbar|korrekt)|unsubscribe|abmelden|abbestellen|""" +
            """austragen|darse de baja|cancelar suscripci|desuscri|darstellungsprobleme|display problems|having trouble viewing|""" +
            """no longer wish|datenschutz)\b""",
    )

    /** @param text the plain-text body, or the HTML already turned into text. */
    fun clean(text: String, max: Int = 400): String {
        val lines = text.replace("\r", "").lines().mapNotNull { raw ->
            var line = raw
                .replace(MARKDOWN_IMAGE, " ")
                .replace(MARKDOWN_LINK, "$1")
                .replace(ANGLE_URL, " ")
                .replace(BARE_URL, " ")
                .replace(IMAGE_PLACEHOLDER, " ")
                .replace(EMPTY_BRACKETS, " ")
                .replace(HEADING, "")
                .replace(MARKUP, "$1")
                .replace(WHITESPACE, " ")
                .trim()
            // A leftover quote marker ("> ") or list bullet with nothing after it.
            line = line.trimStart('>', ' ').trim()
            when {
                line.isEmpty() -> null
                SEPARATOR_LINE.matches(line) -> null
                // Boilerplate is short ("Webansicht / Webview"); a long line that merely mentions it is prose.
                line.length <= 90 && BOILERPLATE.containsMatchIn(line) -> null
                else -> line
            }
        }
        // Plain-text mail is hard-wrapped mid-sentence, so lines are joined with a space.
        val joined = lines.joinToString(" ").replace(WHITESPACE, " ").trim()
        return if (joined.length > max) joined.take(max).trimEnd() + "…" else joined
    }
}
