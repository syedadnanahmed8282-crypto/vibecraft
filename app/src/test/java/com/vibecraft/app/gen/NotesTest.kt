package com.vibecraft.app.gen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotesTest {
    private fun note(id: String, t: String) = RawMsg(id, "note", t)

    @Test fun fileNotesBecomeOneCardWithTheVersion() {
        val items = Notes.group(listOf(
            RawMsg("u", "user", "make it"),
            note("1", Notes.file("created", "app/src/main/java/x/CalculatorScreen.kt", "Keypad")),
            note("2", Notes.file("created", "design.json", "")),
            note("3", "Saved 2 files · v5"),
            RawMsg("a", "assistant", "Done"),
        ))
        assertEquals(3, items.size)
        val c = items[1] as Changes
        assertEquals(2, c.files.size)
        assertEquals(5, c.version)
        assertEquals("Keypad", c.files[0].desc)
        assertEquals("2 files created", Notes.headline(c.files))
    }

    @Test fun aFileNoteWithoutSavedLineStillGroups() {
        val items = Notes.group(listOf(note("1", Notes.file("updated", "a/B.kt", ""))))
        assertEquals(null, (items.single() as Changes).version)
    }

    @Test fun samePathTwiceKeepsOneRow() {
        val c = Notes.group(listOf(note("1", Notes.file("created", "A.kt", "")), note("2", Notes.file("updated", "A.kt", "")))).single() as Changes
        assertEquals(1, c.files.size)
        assertEquals("updated", c.files[0].status)
    }

    @Test fun mixedStatusesSayChanged() {
        assertEquals("2 files changed", Notes.headline(listOf(FileChange("created", "A.kt"), FileChange("deleted", "B.kt"))))
        assertEquals("1 file deleted", Notes.headline(listOf(FileChange("deleted", "B.kt"))))
    }

    @Test fun errorNoteRoundTripsAndKeepsPipesOutOfTheFields() {
        val s = Notes.err("timeout", "Model | slow", "Try again", "Network problem: Request timeout [url=https://x/v1, t=120000 ms]")
        val p = Notes.parseProblem(s)!!
        assertEquals("timeout", p.kind)
        assertEquals("Model / slow", p.title)
        assertEquals(-1, p.done)
        assertTrue(p.raw.startsWith("Network problem"))
    }

    @Test fun errorWithEmptyFieldsParses() {
        val p = Notes.parseProblem(Notes.err("setup", "Add an AI profile", "", ""))!!
        assertEquals("", p.sub)
        assertEquals("", p.raw)
    }

    @Test fun pauseNoteCarriesTheProgress() {
        val p = Notes.parseProblem(Notes.pause("timeout", "X took too long", "No answer", "raw", 1, 3, "CalculatorViewModel.kt"))!!
        assertEquals(1, p.done)
        assertEquals(3, p.total)
        assertEquals("CalculatorViewModel.kt", p.stoppedAt)
        assertEquals("X took too long", p.title)
    }

    @Test fun brokenNotesAreIgnored() {
        assertNull(Notes.parseProblem("ERR|only|three"))
        assertNull(Notes.parseProblem("PAUSE|a|b"))
        assertNull(Notes.parseProblem("Saved 1 file · v2"))
        assertNull(Notes.parseFile("FILE|only"))
    }

    @Test fun problemNotesBecomeCards() {
        val items = Notes.group(listOf(note("1", Notes.err("key", "Key rejected", "Check it", "401")), note("2", "Just a note")))
        assertTrue(items[0] is Trouble)
        assertTrue(items[1] is Plain)
    }

    @Test fun plainTextForThePlanner() {
        assertEquals("Paused at A.kt (1 of 3 files saved): Slow", Notes.plain(Notes.pause("timeout", "Slow", "", "", 1, 3, "A.kt")))
        assertEquals("Slow. Try later", Notes.plain(Notes.err("timeout", "Slow", "Try later", "")))
        assertEquals("created CalculatorScreen.kt", Notes.plain(Notes.file("created", "x/y/CalculatorScreen.kt", "")))
        assertEquals("hello", Notes.plain("hello"))
    }

    @Test fun rolesHideThePath() {
        assertEquals("Screen", Notes.roleOf("app/src/main/java/c/CalculatorScreen.kt"))
        assertEquals("Logic", Notes.roleOf("a/CalculatorViewModel.kt"))
        assertEquals("Data", Notes.roleOf("a/HistoryStore.kt"))
        assertEquals("Design", Notes.roleOf("design.json"))
        assertEquals("Preview", Notes.roleOf("preview.html"))
        assertEquals("Icon", Notes.roleOf("app/src/main/res/drawable/ic_launcher_foreground.xml"))
        assertEquals("Code", Notes.roleOf("a/Util.kt"))
    }

    @Test fun badgesFollowTheExtension() {
        assertEquals("Kt", Notes.badge("A.kt"))
        assertEquals("{ }", Notes.badge("design.json"))
        assertEquals("</>", Notes.badge("preview.html"))
        assertEquals("XML", Notes.badge("a.xml"))
        assertEquals("·", Notes.badge("README"))
    }

    @Test fun aChangesCardReadsAsOneLine() {
        val ch = Changes(listOf(FileChange("created", "a/B.kt"), FileChange("updated", "design.json")), 5, "k")
        assertEquals("2 files changed (saved as version 5): B.kt (created), design.json (updated)", Notes.changesText(ch))
    }
}
