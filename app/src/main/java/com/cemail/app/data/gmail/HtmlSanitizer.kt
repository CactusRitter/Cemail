package com.cemail.app.data.gmail

import org.jsoup.Jsoup
import org.jsoup.safety.Safelist

/**
 * Makes an email's HTML safe to show in a WebView with JavaScript off: scripts, forms, iframes, objects,
 * event handlers and external stylesheets are removed, and CSS `url(...)` is neutralised so nothing is
 * fetched unless the user chooses to show remote images.
 */
object HtmlSanitizer {
    private val safelist = Safelist.relaxed()
        .addTags("center", "font", "hr", "section", "article", "header", "footer", "main", "figure", "figcaption", "abbr", "s", "del", "ins")
        .addAttributes(":all", "style", "class", "align", "bgcolor", "width", "height", "valign", "dir", "lang", "title")
        .addAttributes("font", "color", "size", "face")
        .addAttributes("table", "cellpadding", "cellspacing", "border")
        .addAttributes("td", "colspan", "rowspan", "nowrap")
        .addAttributes("th", "colspan", "rowspan", "nowrap")
        .addProtocols("img", "src", "http", "https", "data")
        .addProtocols("a", "href", "http", "https", "mailto", "tel")

    private val CSS_URL = Regex("""url\s*\([^)]*\)""", RegexOption.IGNORE_CASE)
    private val IMPORT = Regex("""@import[^;]*;?""", RegexOption.IGNORE_CASE)

    fun clean(html: String): String {
        val doc = Jsoup.parseBodyFragment(Jsoup.clean(html, "", safelist, org.jsoup.nodes.Document.OutputSettings().prettyPrint(false)))
        doc.select("[style]").forEach { el ->
            el.attr("style", el.attr("style").replace(IMPORT, "").replace(CSS_URL, "none"))
        }
        doc.select("a[href]").forEach { it.attr("rel", "noopener noreferrer") }
        return doc.body().html()
    }

    /** true if showing the message would load pictures from the internet (tracking pixels, banners...). */
    fun hasRemoteImages(html: String): Boolean =
        Jsoup.parseBodyFragment(html).select("img[src]").any { it.attr("src").startsWith("http", ignoreCase = true) }

    /** The page the WebView loads: viewport, readable defaults and the sanitized message. */
    fun page(cleanHtml: String): String =
        "<!doctype html><html><head><meta charset=\"utf-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
            "<style>body{margin:12px;font-family:sans-serif;word-wrap:break-word;overflow-wrap:anywhere}" +
            "img{max-width:100%;height:auto}</style></head><body>$cleanHtml</body></html>"
}
