package com.cemail.app.data.gmail

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.safety.Safelist

/**
 * Makes an email's HTML safe to show in a WebView with JavaScript off: scripts, forms, iframes, objects,
 * event handlers and stylesheets are removed. The only things left that can reach the internet are pictures
 * (`<img>` and CSS backgrounds over http/https), which the WebView keeps blocked until the user allows them.
 */
object HtmlSanitizer {
    private val safelist = Safelist.relaxed()
        .addTags("center", "font", "hr", "section", "article", "header", "footer", "main", "figure", "figcaption", "abbr", "s", "del", "ins")
        .addAttributes(":all", "style", "class", "align", "bgcolor", "width", "height", "valign", "dir", "lang", "title")
        .addAttributes("font", "color", "size", "face")
        .addAttributes("table", "cellpadding", "cellspacing", "border")
        .addAttributes("td", "colspan", "rowspan", "nowrap")
        .addAttributes("th", "colspan", "rowspan", "nowrap")
        .addProtocols("img", "src", "http", "https", "data", "cid")
        .addProtocols("a", "href", "http", "https", "mailto", "tel")

    private val CSS_URL = Regex("""url\s*\([^)]*\)""", RegexOption.IGNORE_CASE)
    private val REMOTE_CSS_URL = Regex("""url\s*\(\s*['"]?https?://""", RegexOption.IGNORE_CASE)
    private val IMPORT = Regex("""@import[^;]*;?""", RegexOption.IGNORE_CASE)

    /**
     * @param inline pictures that travel inside the message: Content-ID → `data:` URI. An image that points
     * to a Content-ID that is not in the map is left without source (it shows its alternative text).
     */
    fun clean(html: String, inline: Map<String, String> = emptyMap()): String {
        // The page title and style sheets are not part of the message text.
        val source = Jsoup.parse(html)
        source.select("head, title, style, script").remove()
        val doc = Jsoup.parseBodyFragment(Jsoup.clean(source.body().html(), "", safelist, Document.OutputSettings().prettyPrint(false)))
        doc.select("[style]").forEach { el ->
            val style = el.attr("style").replace(IMPORT, "").replace(CSS_URL) { m ->
                if (REMOTE_CSS_URL.containsMatchIn(m.value)) m.value else "none"
            }
            el.attr("style", style)
        }
        doc.select("img[src]").forEach { img ->
            val src = img.attr("src")
            if (src.startsWith("cid:", ignoreCase = true)) {
                val data = inline[src.substring(4)]
                if (data != null) img.attr("src", data) else img.removeAttr("src")
            }
        }
        doc.select("a[href]").forEach { it.attr("rel", "noopener noreferrer") }
        return doc.body().html()
    }

    /** true if showing the message would load pictures from the internet (tracking pixels, banners...). */
    fun hasRemoteImages(html: String): Boolean {
        val doc = Jsoup.parseBodyFragment(html)
        return doc.select("img[src]").any { it.attr("src").startsWith("http", ignoreCase = true) } ||
            doc.select("[style]").any { REMOTE_CSS_URL.containsMatchIn(it.attr("style")) }
    }

    /** true if the message has pictures of its own (`cid:`) that must be fetched from Gmail to be shown. */
    fun hasInlineImages(html: String): Boolean = html.contains("cid:", ignoreCase = true)

    /** The page the WebView loads: viewport, readable defaults and the sanitized message. */
    fun page(cleanHtml: String, bottomSpacePx: Int = 0): String =
        "<!doctype html><html><head><meta charset=\"utf-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width, minimum-scale=0.1\">" +
            "<style>body{margin:12px 12px ${12 + bottomSpacePx}px;font-family:sans-serif;word-wrap:break-word;overflow-wrap:anywhere}" +
            "img{max-width:100%;height:auto}</style></head><body>$cleanHtml</body></html>"
}
