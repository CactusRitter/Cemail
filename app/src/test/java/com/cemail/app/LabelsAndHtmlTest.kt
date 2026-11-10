package com.cemail.app

import com.cemail.app.data.gmail.HtmlSanitizer
import com.cemail.app.data.mail.Label
import com.cemail.app.data.mail.buildLabelTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelsAndHtmlTest {
    @Test fun labelNamesWithSlashesBecomeNestedFolders() {
        val tree = buildLabelTree(
            listOf(Label("1", "Compras"), Label("2", "Compras/Amazon"), Label("3", "Compras/AliExpress"), Label("4", "Bancos/Ahorro"), Label("5", "Alquiler")),
        )
        assertEquals(listOf("Alquiler", "Bancos", "Compras"), tree.map { it.name })
        val compras = tree.first { it.name == "Compras" }
        assertEquals("1", compras.id)
        assertEquals(listOf("AliExpress", "Amazon"), compras.children.map { it.name })
        // "Bancos" has no label of its own, only a child: it is a folder you can open and close but not select.
        val bancos = tree.first { it.name == "Bancos" }
        assertNull(bancos.id)
        assertEquals("Bancos/Ahorro", bancos.children.single().fullName)
    }

    @Test fun sanitizerRemovesScriptsHandlersAndCssUrls() {
        val out = HtmlSanitizer.clean(
            """<div style="background:url(javascript:evil());color:red" onclick="evil()">Hi<script>alert(1)</script>
               <iframe src="http://x"></iframe><a href="javascript:evil()">x</a><a href="https://ok.example">ok</a></div>""",
        )
        assertFalse(out.contains("script", true))
        assertFalse(out.contains("onclick", true))
        assertFalse(out.contains("iframe", true))
        assertFalse(out.contains("javascript:", true))
        assertFalse(out.contains("url(", true))
        assertTrue(out.contains("color:red"))
        assertTrue(out.contains("https://ok.example"))
    }

    @Test fun detectsRemoteImages() {
        assertTrue(HtmlSanitizer.hasRemoteImages("""<img src="https://t.example/p.gif">"""))
        assertFalse(HtmlSanitizer.hasRemoteImages("""<img src="data:image/png;base64,AAAA"><p>x</p>"""))
    }
}
