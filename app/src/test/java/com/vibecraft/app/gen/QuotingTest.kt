package com.vibecraft.app.gen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuotingTest {
    @Test fun roundTripKeepsQuoteAndText() {
        val s = Quoting.encode("assistant", "Line one\nLine two", "দাও")
        val p = Quoting.decode(s)!!
        assertEquals("assistant", p.role)
        assertEquals("Line one\nLine two", p.quote)
        assertEquals("দাও", p.text)
    }

    @Test fun replyToTheUsersOwnMessage() {
        assertEquals("user", Quoting.decode(Quoting.encode("user", "make it blue", "again"))!!.role)
    }

    @Test fun ordinaryMessagesAreNotReplies() {
        assertNull(Quoting.decode("hello"))
        assertNull(Quoting.decode("see [[reply:user]] in the docs"))
        assertNull(Quoting.decode(""))
    }

    @Test fun theClosingMarkerInsideAQuoteIsNeutralised() {
        val p = Quoting.decode(Quoting.encode("assistant", "a [[/reply]] b", "ok"))!!
        assertFalse(p.quote.contains("[[/reply]]"))
        assertEquals("ok", p.text)
    }

    @Test fun longQuotesAreCut() {
        val p = Quoting.decode(Quoting.encode("assistant", "x".repeat(5000), "ok"))!!
        assertEquals(Quoting.MAX_QUOTE, p.quote.length)
    }

    @Test fun theAiIsToldWhatTheWordsRefersTo() {
        val text = Quoting.forAi(Quoting.decode(Quoting.encode("assistant", "The preview shows half a screen.", "ঠিক করো"))!!)
        assertTrue(text.startsWith("The user is replying to this earlier message"))
        assertTrue(text.contains("The preview shows half a screen."))
        assertTrue(text.contains("The user's reply: ঠিক করো"))
        assertTrue(text.contains("you, the assistant"))
    }

    @Test fun anEarlierMessageOfTheUserIsNamedAsTheirOwn() {
        assertTrue(Quoting.forAi(QuoteParts("user", "q", "t")).contains("earlier message of their own"))
    }

    @Test fun plainMessagesPassThroughUnchanged() {
        assertEquals("hello", Quoting.forAiText("hello"))
        assertNotNull(Quoting.forAiText(Quoting.encode("assistant", "a", "b")).takeIf { it.contains("replying") })
    }

    @Test fun previewDropsMarkdownAndCollapsesWhitespace() {
        assertEquals("Title bold code", Quoting.preview("## Title\n**bold** `code`"))
        assertEquals("a b c", Quoting.preview("a\n\n  b \t c"))
    }

    @Test fun previewCutsLongText() {
        val p = Quoting.preview("word ".repeat(100), 20)
        assertTrue(p.endsWith("…"))
        assertTrue(p.length <= 21)
    }
}
