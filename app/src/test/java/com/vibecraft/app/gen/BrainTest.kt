package com.vibecraft.app.gen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrainTest {
    private val failedRun = RunReport("add history", "I will add history.", listOf("A.kt: saved", "preview.html: failed"), 3, "stopped at preview.html: cut off", 5L)

    @Test fun emptyOrBrokenTextGivesAnEmptyBrain() {
        assertEquals(Brain(), BrainOps.parse(null))
        assertEquals(Brain(), BrainOps.parse(""))
        assertEquals(Brain(), BrainOps.parse("{ not json"))
    }

    @Test fun roundTripKeepsEverything() {
        val b = Brain(
            memory = "GOAL: calculator\nUSER: Bengali; এপিকে = APK", brief = "GOAL: x",
            roadmap = listOf(Task("PHASE 1: basics", TaskState.DONE)),
            tasks = listOf(Task("Write A.kt: x", TaskState.FAILED, "cut off")),
            last = failedRun, active = 0, sinceMemory = 2,
        )
        assertEquals(b, BrainOps.parse(BrainOps.toJson(b)))
    }

    @Test fun unknownFieldsAreIgnored() {
        assertEquals("m", BrainOps.parse("""{"memory":"m","future":1}""").memory)
    }

    @Test fun nextPrefersFileTasksThenTheRoadmap() {
        val b = Brain(
            roadmap = listOf(Task("PHASE 1", TaskState.DONE), Task("PHASE 2")),
            tasks = listOf(Task("Write A.kt", TaskState.DONE), Task("Write B.kt", TaskState.FAILED)),
        )
        assertEquals(NextTask("Write B.kt", null), BrainOps.next(b))
        assertEquals(NextTask("PHASE 2", 1), BrainOps.next(b.copy(tasks = emptyList())))
        assertNull(BrainOps.next(b.copy(tasks = emptyList(), roadmap = listOf(Task("PHASE 1", TaskState.DONE)))))
    }

    @Test fun renderShowsMemoryTasksAndTheLastRun() {
        val text = BrainOps.render(Brain(memory = "GOAL: calc", tasks = listOf(Task("Write B.kt", TaskState.FAILED, "cut off")), last = failedRun))
        assertTrue(text.contains("GOAL: calc"))
        assertTrue(text.contains("[failed] Write B.kt"))
        assertTrue(text.contains("cut off"))
        assertTrue(text.contains("saved v3"))
        assertTrue(text.contains("preview.html: failed"))
    }

    @Test fun statusTextIsBuiltFromTheRecordInBothLanguages() {
        val b = Brain(tasks = listOf(Task("Write preview.html", TaskState.FAILED)), last = failedRun)
        val en = BrainOps.statusText(b, false)
        assertTrue(en.contains("add history"))
        assertTrue(en.contains("saved v3"))
        assertTrue(en.contains("stopped at preview.html"))
        assertTrue(en.contains("Write preview.html"))
        val bn = BrainOps.statusText(b, true)
        assertTrue(bn.contains("v3 সেভ হয়েছে"))
        assertTrue(bn.contains("বাকি কাজ: Write preview.html"))
        assertTrue(BrainOps.statusText(Brain(), false).contains("Nothing has run"))
    }

    @Test fun nothingSavedIsSaidPlainly() {
        val b = Brain(last = RunReport("x", "s", emptyList(), null, null, 1L))
        assertTrue(BrainOps.statusText(b, false).contains("nothing saved"))
        assertTrue(BrainOps.statusText(b, true).contains("কিছু সেভ হয়নি"))
    }

    @Test fun closingNoteSplitsIntoItsParts() {
        val s = BrainOps.sections("COMMIT: Add history\nSUMMARY: Did it.\nMore text.\nMEMORY: GOAL: a\nUSER: b")
        assertEquals("Add history", s["COMMIT"])
        assertEquals("Did it.\nMore text.", s["SUMMARY"])
        assertEquals("GOAL: a\nUSER: b", s["MEMORY"])
    }

    // ---- intent rules ----

    @Test fun intentLineIsRead() {
        assertEquals(Intent.ACT, Intents.parseLine("INTENT: ACT\nSUMMARY: x"))
        assertEquals(Intent.ANSWER, Intents.parseLine("intent: answer\nhello"))
        assertNull(Intents.parseLine("SUMMARY: x\nFILE: a.kt | b"))
    }

    @Test fun controlLinesAreRemovedFromAnAnswer() {
        assertEquals("It is fine.", Intents.stripControl("INTENT: ANSWER\nIt is fine.\nFILE: a.kt | x"))
        assertEquals("I will fix it", Intents.stripControl("INTENT: ACT\nSUMMARY: I will fix it"))
    }

    @Test fun bengaliIsRecognised() {
        assertTrue(Intents.isBengali("কি হইছে"))
        assertFalse(Intents.isBengali("what happened"))
    }

    @Test fun whatHappenedQuestionsAreStatusQuestions() {
        assertTrue(Intents.quickStatus("কি হইছে"))
        assertTrue(Intents.quickStatus("কি হইছে?"))
        assertTrue(Intents.quickStatus("What happened"))
        assertFalse(Intents.quickStatus("এইবার চেক করে দেখো"))
        assertFalse(Intents.quickStatus("কি হইছে এটা ঠিক করো কারণ প্রিভিউ দেখা যাচ্ছে না এবং রং ভুল"))
    }

    @Test fun onlyBareConfirmationsResumeWork() {
        assertTrue(Intents.quickResume("দাও"))
        assertTrue(Intents.quickResume("ওকে!"))
        assertTrue(Intents.quickResume("কখন ঠিক করবে"))
        assertTrue(Intents.quickResume("OK."))
        assertTrue(Intents.quickResume("Continue"))
        assertFalse(Intents.quickResume("ডার্ক মোড করো"))
        assertFalse(Intents.quickResume("ডার্ক মোড দাও"))
    }

    @Test fun aCheckRequestIsNotAChangeRequest() {
        assertTrue(Intents.looksLikeReview("এইবার চেক করে দেখো"))
        assertTrue(Intents.looksLikeReview("প্রিভিউ চেক করো"))
        assertFalse(Intents.looksLikeReview("চেক করো এবং ঠিক করো"))
        assertFalse(Intents.looksLikeReview("check and fix it"))
        assertFalse(Intents.looksLikeReview("add dark mode"))
    }

    @Test fun planCanBeSkippedOnRequest() {
        assertTrue(Intents.skipPlan("সরাসরি বানাও"))
        assertTrue(Intents.skipPlan("build it directly"))
        assertFalse(Intents.skipPlan("একটা ক্যালকুলেটর বানাও"))
    }
}
