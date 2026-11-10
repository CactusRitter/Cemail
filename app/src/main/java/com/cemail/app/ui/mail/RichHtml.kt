package com.cemail.app.ui.mail

import androidx.compose.ui.text.font.FontFamily
import com.mohamedrejeb.richeditor.model.RichTextState
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/** Tags that only formatted text produces (the editor wraps plain text in a paragraph and nothing else). */
private val FORMATTED = Regex("<(b|i|u|s|em|strong|span|ul|ol)[ >]")

/**
 * The editor's text as HTML for the e-mail, or null when it has no formatting (it then goes out as plain text).
 * The editor's own HTML export covers paragraphs, lists, bold, italic and underline but leaves the font out, so
 * the font family is added here: each text piece in a font gets wrapped in a `<span style="font-family:...">`.
 */
fun RichTextState.toMailHtml(): String? {
    val html = toHtml()
    val text = annotatedString.text
    val fontAt = arrayOfNulls<FontFamily>(text.length)
    for (range in annotatedString.spanStyles) {
        val family = range.item.fontFamily ?: continue
        for (i in range.start until minOf(range.end, text.length)) fontAt[i] = family
    }
    val hasFont = fontAt.any { it != null }
    if (!hasFont) return if (FORMATTED.containsMatchIn(html)) html else null

    val doc = Jsoup.parseBodyFragment(html)
    doc.outputSettings().prettyPrint(false)
    var cursor = 0
    for (paragraph in doc.body().select("p, li")) {
        // Find where this paragraph's text sits in the editor's text (which may carry list markers).
        val paragraphText = paragraph.wholeText()
        val start = text.indexOf(paragraphText, cursor).takeIf { it >= 0 } ?: continue
        cursor = start + paragraphText.length
        var offset = 0
        for (node in textNodes(paragraph)) {
            val piece = node.wholeText
            wrapFonts(node, piece, fontAt, start + offset)
            offset += piece.length
        }
    }
    return doc.body().html()
}

private fun textNodes(root: Element): List<TextNode> {
    val found = mutableListOf<TextNode>()
    fun walk(node: Node) {
        if (node is TextNode) found += node else node.childNodes().toList().forEach(::walk)
    }
    walk(root)
    return found
}

/** Replaces [node] by the same text split wherever the font changes; pieces in a font go inside a span. */
private fun wrapFonts(node: TextNode, piece: String, fontAt: Array<FontFamily?>, base: Int) {
    if (piece.isEmpty()) return
    var from = 0
    while (from < piece.length) {
        val family = fontAt.getOrNull(base + from)
        var to = from + 1
        while (to < piece.length && fontAt.getOrNull(base + to) == family) to++
        val part = piece.substring(from, to)
        val css = family?.let(::cssFont)
        if (css != null) {
            node.before(Element("span").attr("style", "font-family:$css").appendText(part))
        } else {
            node.before(TextNode(part))
        }
        from = to
    }
    node.remove()
}

private fun cssFont(family: FontFamily): String? = when (family) {
    FontFamily.Serif -> "Georgia,'Times New Roman',serif"
    FontFamily.Monospace -> "'Courier New',Courier,monospace"
    FontFamily.Cursive -> "'Comic Sans MS','Brush Script MT',cursive"
    FontFamily.SansSerif -> "Arial,Helvetica,sans-serif"
    else -> null
}
