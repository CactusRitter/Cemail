package com.cemail.app.data.gmail

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.TextNode
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
    private val FONT_FACE = Regex("""@font-face\s*\{[^}]*\}""", RegexOption.IGNORE_CASE)
    private val IMPORT =Regex("""@import[^;]*;?""", RegexOption.IGNORE_CASE)

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
                val cid = src.substring(4)
                val data = inline[cid] ?: inline[decode(cid)]
                if (data != null) img.attr("src", data) else img.removeAttr("src")
            }
        }
        // A picture left without a source would show a "broken image" icon: show its description instead.
        doc.select("img").forEach { img ->
            if (img.attr("src").isBlank()) {
                val alt = img.attr("alt").trim()
                if (alt.isEmpty()) img.remove() else img.replaceWith(TextNode(alt))
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

    private fun decode(value: String): String = try {
        java.net.URLDecoder.decode(value, "UTF-8")
    } catch (_: Exception) {
        value
    }

    /**
     * The message's own style sheets (many emails need them to lay out buttons and columns), made safe:
     * no imports, no downloadable fonts, no `url(...)` other than http/https pictures (blocked like any
     * remote image until the user allows them), and nothing that could close the style element.
     */
    fun styles(html: String): String =
        Jsoup.parse(html).select("style").joinToString("\n") { it.data() }
            .replace(IMPORT, "")
            .replace(FONT_FACE, "")
            .replace(CSS_URL) { m -> if (REMOTE_CSS_URL.containsMatchIn(m.value)) m.value else "none" }
            .replace("<", "")

    /**
     * The page the WebView loads: viewport, readable defaults, the message's (safe) [css] and the sanitized
     * message, followed by [bottomSpacePx] of empty room.
     */
    fun page(cleanHtml: String, bottomSpacePx: Int = 0, css: String = ""): String =
        "<!doctype html><html><head><meta charset=\"utf-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width, minimum-scale=0.1\">" +
            "<style>body{margin:12px;font-family:sans-serif;overflow-wrap:break-word}" +
            "img{max-width:100%;height:auto}</style><style>$css</style></head>" +
            "<body>$cleanHtml<div style=\"height:${bottomSpacePx}px\"></div></body></html>"

    // "Light only" declarations: `color-scheme: light [only] [!important]` (also supported-color-schemes, but never the
    // `prefers-color-scheme: light` of a media query) and the matching <meta> tags.
    private val ANY_SCHEME = Regex("""(?<![\w-])(supported-)?color-schemes?\s*:\s*[^;}"'<]*""", RegexOption.IGNORE_CASE)
    private val SCHEME_META = Regex("""<meta[^>]*name\s*=\s*["']?(supported-)?color-schemes?["']?[^>]*>""", RegexOption.IGNORE_CASE)

    /**
     * The [page] ready for the WebView to darken it (Settings → Dark messages): every colour-scheme request is left out,
     * because the WebView never darkens a page that claims to handle dark itself, and many claim it ("light dark", or
     * dark rules that keep the page white) while counting on the mail app to darken them. The page's own
     * `prefers-color-scheme: dark` rules still apply; the WebView leaves what is already dark as it is.
     */
    fun allowDark(page: String): String = page.replace(SCHEME_META, "").replace(ANY_SCHEME, "color-scheme: normal")
}
