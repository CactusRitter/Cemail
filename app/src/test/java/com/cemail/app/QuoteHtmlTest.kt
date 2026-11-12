package com.cemail.app

import com.cemail.app.data.gmail.SignatureHtml
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuoteHtmlTest {
    private val pixel = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg=="

    @Test
    fun `the original message follows the reply under a header, as a blockquote`() {
        val (html, images) = SignatureHtml.appendQuote("<div>Thanks</div>", "On 12 Nov, A & B wrote:", "<p>Hello</p>")
        assertTrue(html.startsWith("<div>Thanks</div>"))
        assertTrue(html.contains("A &amp; B wrote:"))
        assertTrue(html.contains("<blockquote"))
        assertTrue(html.contains("<p>Hello</p>"))
        assertTrue(images.isEmpty())
    }

    @Test
    fun `embedded pictures of the original become inline parts, remote ones stay links`() {
        val quote = """<img src="$pixel"><img src="https://example.com/banner.png">"""
        val (html, images) = SignatureHtml.appendQuote("", "wrote:", quote)
        assertEquals(1, images.size)
        assertEquals("image/png", images[0].mimeType)
        assertTrue(html.contains("src=\"cid:${images[0].cid}\""))
        assertTrue(html.contains("https://example.com/banner.png"))
        assertFalse(html.contains("data:image"))
    }

    @Test
    fun `quote and signature pictures never share a content id`() {
        val (withSig, sigImages) = SignatureHtml.merge("<div>x</div>", """<img src="$pixel">""")
        val (_, quoteImages) = SignatureHtml.appendQuote(withSig, "wrote:", """<img src="$pixel">""")
        assertEquals(1, sigImages.size)
        assertEquals(1, quoteImages.size)
        assertFalse(sigImages[0].cid == quoteImages[0].cid)
    }
}
