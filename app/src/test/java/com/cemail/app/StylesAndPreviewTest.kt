package com.cemail.app

import com.cemail.app.data.gmail.GmailMessage
import com.cemail.app.data.gmail.Header
import com.cemail.app.data.gmail.HtmlSanitizer
import com.cemail.app.data.gmail.InlineImages
import com.cemail.app.data.gmail.MessagePart
import com.cemail.app.data.gmail.PartBody
import com.cemail.app.data.mail.Mail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StylesAndPreviewTest {
    @Test fun previewHasNoImagePlaceholders() {
        val mail = Mail(1, "Google", "g@x.com", "s", "[image: Google]\nHas permitido que Cemail acceda [cid:abc] a tus datos", 0)
        assertEquals("Has permitido que Cemail acceda a tus datos", mail.preview)
    }

    @Test fun styleSheetsAreKeptButMadeSafe() {
        val css = HtmlSanitizer.styles(
            """<html><head><style>@import url("https://x.example/a.css"); .btn{background:#00838f;padding:12px}
               @font-face{font-family:X;src:url(https://x.example/f.woff)} .bg{background:url(javascript:x)}
               .hero{background:url('https://x.example/h.png')} </style><b>x</style></head><body></body></html>""",
        )
        assertTrue(css.contains(".btn{background:#00838f;padding:12px}"))
        assertFalse(css.contains("@import"))
        assertFalse(css.contains("@font-face"))
        assertFalse(css.contains("javascript"))
        assertTrue(css.contains("https://x.example/h.png"))
        assertFalse(css.contains("<"))
    }

    @Test fun pageCarriesTheStylesAndTheBottomRoom() {
        val page = HtmlSanitizer.page("<p>hi</p>", 40, ".a{color:red}")
        assertTrue(page.contains("<style>.a{color:red}</style>"))
        assertTrue(page.contains("height:40px"))
    }

    @Test fun picturesWithoutSourceDoNotShowABrokenIcon() {
        val out = HtmlSanitizer.clean("""<p><img src="images/spacer.gif"><img alt="Post.at"><img src="https://x.example/a.png"></p>""")
        assertEquals(1, Regex("<img").findAll(out).count())
        assertTrue(out.contains("Post.at"))
    }

    @Test fun picturesSentAsGenericFilesAreRecognisedByTheirName() {
        val message = GmailMessage(
            id = "1",
            payload = MessagePart(
                mimeType = "multipart/related",
                parts = listOf(
                    MessagePart(
                        mimeType = "application/octet-stream", filename = "logo.PNG",
                        headers = listOf(Header("X-Attachment-Id", "logo1")), body = PartBody(attachmentId = "att"),
                    ),
                ),
            ),
        )
        val part = InlineImages.find(message).single()
        assertEquals("logo1", part.cid)
        assertEquals("image/png", part.mimeType)
    }
}
