package com.vibecraft.app.gen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoticeTextTest {
    @Test fun doneCarriesTheProjectAndTheFacts() {
        val n = NoticeText.build(NoticeKind.DONE, "SkyCalc 3D", false, "The keypad is ready. Colors come from design.json.", "3 changes saved · v5")
        assertEquals("Done · SkyCalc 3D", n.title)
        assertEquals("3 changes saved · v5", n.text)
        assertTrue(n.big.startsWith("3 changes saved · v5\n"))
        assertTrue(n.big.contains("The keypad is ready."))
    }

    @Test fun anAnswerIsNeededInBengaliToo() {
        assertEquals("আপনার উত্তর দরকার · X", NoticeText.build(NoticeKind.ASK, "X", true, "কোন রং?").title)
        assertEquals("Your answer is needed · X", NoticeText.build(NoticeKind.ASK, "X", false, "Which colors?").title)
    }

    @Test fun problemAndReplyHaveTheirOwnWords() {
        assertEquals("Needs attention · X", NoticeText.build(NoticeKind.PROBLEM, "X", false, "slow").title)
        assertEquals("New reply · X", NoticeText.build(NoticeKind.REPLY, "X", false, "hi").title)
        assertEquals("মনোযোগ দরকার · X", NoticeText.build(NoticeKind.PROBLEM, "X", true, "slow").title)
    }

    @Test fun withoutFactsTheDetailIsTheText() {
        val n = NoticeText.build(NoticeKind.ASK, "X", false, "## Plan\n**Which** colors?\n\n1. blue")
        assertEquals("Plan Which colors? 1. blue", n.text)
        assertEquals(n.text, n.big)
    }

    @Test fun longDetailIsCutForTheNotification() {
        val n = NoticeText.build(NoticeKind.REPLY, "X", false, "word ".repeat(300))
        assertTrue(n.text.length <= 141)
        assertTrue(n.text.endsWith("…"))
        assertTrue(n.big.length <= 451)
    }

    @Test fun emptyDetailStillGivesReadableText() {
        val n = NoticeText.build(NoticeKind.DONE, "", false, "")
        assertEquals("Done · Vibecraft", n.title)
        assertEquals("Done", n.text)
        assertFalse(n.big.isBlank())
    }

    @Test fun workingShowsTheFileAndAPercentage() {
        val p = NoticeText.progress(
            listOf(PhaseView("Reading", 2, "read", "", -1f), PhaseView("Working", 1, "work", "CalculatorViewModel.kt · 2 of 3", 0.5f)),
            "SkyCalc 3D", false,
        )
        assertEquals("SkyCalc 3D", p.title)
        assertEquals("Working · CalculatorViewModel.kt · 2 of 3", p.text)
        assertEquals(50, p.percent)
    }

    @Test fun otherPhasesHaveNoPercentage() {
        val p = NoticeText.progress(listOf(PhaseView("Reasoning", 1, "reason", "waiting for the model", -1f)), "X", false)
        assertEquals("Reasoning · waiting for the model", p.text)
        assertNull(p.percent)
    }

    @Test fun progressIsReadableInBengali() {
        assertEquals("কাজ করছি · A.kt", NoticeText.progress(listOf(PhaseView("Working", 1, "work", "A.kt", 0f)), "X", true).text)
        assertEquals("ভাবছি", NoticeText.progress(listOf(PhaseView("Reasoning", 1, "reason", "", -1f)), "X", true).text)
    }

    @Test fun percentStaysInRange() {
        assertEquals(100, NoticeText.progress(listOf(PhaseView("Working", 1, "work", "", 1.7f)), "X", false).percent)
        assertEquals(0, NoticeText.progress(listOf(PhaseView("Working", 1, "work", "", 0f)), "X", false).percent)
    }

    @Test fun nothingActiveStillSaysSomething() {
        assertEquals("Working", NoticeText.progress(emptyList(), "X", false).text)
        assertEquals("কাজ চলছে", NoticeText.progress(emptyList(), "X", true).text)
    }

    @Test fun theOkButtonSaysWhatItSends() {
        assertEquals("OK, go ahead", NoticeText.okLabel(false))
        assertTrue(NoticeText.okText(false).startsWith("OK, go ahead"))
        assertTrue(NoticeText.okText(true).startsWith("ঠিক আছে"))
    }
}
