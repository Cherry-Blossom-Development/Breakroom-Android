package com.cherryblossomdev.breakroom.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RichTextTest {

    @Test
    fun plainTextPassesThrough() {
        assertEquals("Line one\nLine two", RichText.toPlainText("Line one\r\nLine two\n"))
        // Angle brackets alone don't make it HTML
        assertEquals("Jane <jane@example.com>", RichText.toPlainText("Jane <jane@example.com>"))
        assertEquals("", RichText.toPlainText(null))
    }

    @Test
    fun paragraphsAndBreaksBecomeLines() {
        assertEquals(
            "First & foremost\nSecond line\nThird",
            RichText.toPlainText("<p>First &amp; <strong>foremost</strong></p>\n<p>Second line<br>Third</p><p><br></p>")
        )
    }

    @Test
    fun listsGetBulletsAndNumbers() {
        val html = "<p>Steps:</p><ol><li>Open</li><li>Click <em>Save</em><ul><li>twice</li></ul></li></ol><ul><li>Done</li></ul>"
        assertEquals("Steps:\n1. Open\n2. Click Save\n  • twice\n• Done", RichText.toPlainText(html))
    }

    @Test
    fun plainTextConvertsToParagraphsAndLists() {
        assertEquals(
            "<p>Steps:</p><ol><li>Open</li><li>Save &amp; close</li></ol><ul><li>a</li><li>b</li></ul><p>x &lt;y&gt;</p>",
            RichText.fromPlainText("Steps:\n1. Open\n2. Save & close\n\n• a\n- b\nx <y>")
        )
        assertEquals("", RichText.fromPlainText("  \n "))
    }

    @Test
    fun roundTripKeepsStructure() {
        val html = "<p>Intro</p><ul><li>one</li><li>two</li></ul><ol><li>first</li></ol>"
        assertEquals(html, RichText.fromPlainText(RichText.toPlainText(html)))
    }

    @Test
    fun detectsFormattingAnEditWouldLose() {
        assertTrue(RichText.hasInlineFormatting("<p>Some <strong>bold</strong></p>"))
        assertTrue(RichText.hasInlineFormatting("<p><a href=\"x\">link</a></p>"))
        assertFalse(RichText.hasInlineFormatting("<p>Plain</p><ul><li>list</li></ul>"))
        assertFalse(RichText.hasInlineFormatting("Jane <jane@example.com>"))
    }
}
