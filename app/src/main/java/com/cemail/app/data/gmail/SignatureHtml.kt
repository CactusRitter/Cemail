package com.cemail.app.data.gmail

import java.util.Base64
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/** A picture shown inside the HTML of an outgoing message (`<img src="cid:...">`). */
class InlineImage(val cid: String, val mimeType: String, val bytes: ByteArray)

/** Everything about HTML signatures: finding one in a message, making it safe, and putting it into a new message. */
object SignatureHtml {
    // Where mail apps put the signature when they write a message.
    private val SELECTORS = listOf(
        "div.gmail_signature", "[data-smartmail=gmail_signature]", "div#Signature", "div#signature",
        "div[id^=Signature]", "div.moz-signature", "div.Signature", "table.signature",
    )

    /**
     * The signature in an imported message or HTML file: the block marked as signature if there is one, else the
     * whole message. Scripts, forms and the like are removed; pictures that travel inside the message become
     * `data:` pictures so they show without any download.
     */
    fun extract(parsed: ParsedEml): String? {
        val source = parsed.html ?: parsed.text?.let { plainToHtml(it) } ?: return null
        val inline = parsed.images.mapValues { (_, image) ->
            "data:${image.mimeType};base64," + Base64.getEncoder().encodeToString(image.bytes)
        }
        val doc = Jsoup.parse(source)
        val marked = SELECTORS.firstNotNullOfOrNull { doc.selectFirst(it) }
        val fragment = marked?.outerHtml() ?: doc.body().html()
        val clean = HtmlSanitizer.clean(fragment, inline).trim()
        return clean.takeIf { it.isNotEmpty() }
    }

    fun plainToHtml(text: String): String =
        text.lines().joinToString("") { "<div>" + escape(it).ifEmpty { "<br>" } + "</div>" }

    private fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    /** The signature as plain text, for the plain-text half of a message. */
    fun toText(html: String): String {
        val doc: Document = Jsoup.parse(html)
        doc.outputSettings().prettyPrint(false)
        doc.select("p, div, tr, li, h1, h2, h3").append("\\n")
        // A span shown as a block starts its own line.
        doc.select("span[style*='display:block'], span[style*='display: block']").prepend("\\n")
        return doc.wholeText().replace("\\n", "\n").lines().joinToString("\n") { it.trimEnd() }
            .replace(Regex("\n{3,}"), "\n\n").trim()
    }

    /**
     * [userHtml] followed by the signature. Signature pictures embedded as `data:` become attachments referenced
     * by `cid:` (mail apps do not show `data:` pictures), returned with the final HTML.
     */
    fun merge(userHtml: String, signatureHtml: String): Pair<String, List<InlineImage>> {
        val doc = Jsoup.parseBodyFragment(signatureHtml)
        doc.outputSettings().prettyPrint(false)
        val images = mutableListOf<InlineImage>()
        cidify(doc, "sig", images)
        return (userHtml + "<br><div>" + doc.body().html() + "</div>") to images
    }

    /** Turns the `data:` pictures of [doc] into `cid:` references (mail apps do not show `data:` pictures), adding their bytes to [images]. */
    private fun cidify(doc: Document, prefix: String, images: MutableList<InlineImage>) {
        doc.select("img[src^=data:]").forEach { img ->
            val match = Regex("""^data:(image/[a-zA-Z0-9.+-]+);base64,(.*)$""", RegexOption.DOT_MATCHES_ALL).find(img.attr("src"))
            val bytes = match?.let { runCatching { Base64.getMimeDecoder().decode(it.groupValues[2]) }.getOrNull() }
            if (match != null && bytes != null) {
                val cid = "$prefix${images.size + 1}@cemail"
                images += InlineImage(cid, match.groupValues[1], bytes)
                img.attr("src", "cid:$cid")
            }
        }
    }

    /**
     * [html] followed by the quoted original message ([quoteHtml], already sanitized) under a [header] line, as mail
     * apps do when answering; its embedded pictures become inline parts, the remote ones stay links.
     */
    fun appendQuote(html: String, header: String, quoteHtml: String): Pair<String, List<InlineImage>> {
        val doc = Jsoup.parseBodyFragment(quoteHtml)
        doc.outputSettings().prettyPrint(false)
        val images = mutableListOf<InlineImage>()
        cidify(doc, "quote", images)
        val quote = "<br><div>" + escape(header) + "</div><blockquote style=\"margin:0 0 0 0.8ex;border-left:1px solid #ccc;padding-left:1ex\">" + doc.body().html() + "</blockquote>"
        return (html + quote) to images
    }
}
