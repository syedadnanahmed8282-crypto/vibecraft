package com.vibecraft.app.gen

import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryTest {
    private val v1 = History.changes(mapOf("a.kt" to "1", "b.kt" to "1"))
    private val v2 = History.changes(mapOf("a.kt" to "2", "c.kt" to "new"), listOf("b.kt"))

    @Test fun replayAppliesChangesAndDeletionsInOrder() {
        val cps = listOf(2 to v2, 1 to v1)
        assertEquals(mapOf("a.kt" to "1", "b.kt" to "1"), History.replay(cps, 1))
        assertEquals(mapOf("a.kt" to "2", "c.kt" to "new"), History.replay(cps, 2))
    }

    @Test fun aFullSnapshotRestoresAnEarlierVersionExactly() {
        val restored = History.changes(History.replay(listOf(1 to v1, 2 to v2), 1), full = true)
        val state = History.replay(listOf(1 to v1, 2 to v2, 3 to restored), 3)
        assertEquals(mapOf("a.kt" to "1", "b.kt" to "1"), state)
    }

    @Test fun filesAddedLaterAreGoneAfterAFullSnapshot() {
        val full = History.changes(mapOf("a.kt" to "1"), full = true)
        assertEquals(mapOf("a.kt" to "1"), History.replay(listOf(1 to v1, 2 to v2, 3 to full), 3))
    }

    @Test fun contentWithQuotesNewlinesAndBengaliSurvives() {
        val text = "হ্যালো\n\"quoted\" \\ backslash"
        val state = History.replay(listOf(1 to History.changes(mapOf("x.txt" to text))), 1)
        assertEquals(text, state["x.txt"])
    }

    @Test fun nothingBeforeTheFirstVersion() {
        assertEquals(emptyMap<String, String>(), History.replay(listOf(1 to v1), 0))
    }
}
