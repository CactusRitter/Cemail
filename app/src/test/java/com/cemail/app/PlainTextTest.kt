package com.cemail.app

import com.cemail.app.ui.mail.htmlToPlainText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PlainTextTest {
    @Test
    fun boldAndItalicGoOutWithoutMarks() {
        val text = htmlToPlainText("<p>Hola. Esto: <b>negrita</b> y <i>cursiva</i> y <u>subrayado</u>.</p>")
        assertEquals("Hola. Esto: negrita y cursiva y subrayado.", text)
        assertFalse(text.contains('*'))
        assertFalse(text.contains('_'))
    }

    @Test
    fun eachParagraphIsALineAndAnEmptyOneIsOneBlankLine() {
        assertEquals("uno\n\ndos", htmlToPlainText("<p>uno</p><p><br></p><p>dos</p>"))
        assertEquals("uno\ndos", htmlToPlainText("<p>uno</p><p>dos</p>"))
    }

    @Test
    fun listsGetBulletsAndNumbers() {
        assertEquals("• uno\n• dos", htmlToPlainText("<ul><li>uno</li><li>dos</li></ul>"))
        assertEquals("1. uno\n2. dos", htmlToPlainText("<ol><li>uno</li><li><b>dos</b></li></ol>"))
    }

    @Test
    fun entitiesAreDecoded() {
        assertEquals("a & b < c", htmlToPlainText("<p>a &amp; b &lt; c</p>"))
    }
}
