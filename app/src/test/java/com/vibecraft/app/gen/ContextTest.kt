package com.vibecraft.app.gen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextTest {
    private val gen = CodeBlocks.GEN
    private val root = "$gen/AppRoot.kt"
    private val calc = "$gen/CalculatorScreen.kt"
    private val unused = "$gen/Unused.kt"

    private val small = mapOf(
        root to "package p\nfun AppRoot() { CalculatorScreen() }\n",
        calc to "package p\nfun CalculatorScreen() {}\nclass History\n",
        unused to "package p\nfun Unused() {}\n",
    )

    @Test fun declaredNames() {
        assertEquals(listOf("CalculatorScreen", "History"), Manifest.declared(small.getValue(calc)))
    }

    @Test fun dependenciesAreFound() {
        val info = Manifest.build(small).first { it.path == root }
        assertEquals(listOf(calc), info.deps)
        assertTrue(Manifest.build(small).first { it.path == unused }.deps.isEmpty())
    }

    @Test fun hashChangesWithContent() {
        assertEquals(Manifest.hash("a"), Manifest.hash("a"))
        assertNotEquals(Manifest.hash("a"), Manifest.hash("b"))
    }

    @Test fun smallProjectIsShownCompletely() {
        val pack = ContextSelector.select("anything", emptyList(), small)
        assertEquals(3, pack.shown.size)
        assertTrue(pack.shown.all { it.complete })
        assertTrue(pack.render().contains("Project manifest"))
    }

    @Test fun largeProjectShowsPartOfALongFileAndSaysSo() {
        val big = (1..500).joinToString("\n") { "val a$it = $it" }
        val files = mapOf(
            root to "package p\nfun AppRoot() { BigScreen() }\n",
            "$gen/BigScreen.kt" to "package p\nfun BigScreen() {}\n$big\n",
        )
        val pack = ContextSelector.select("change BigScreen colors", emptyList(), files, budget = 1000)
        val shown = pack.shown.first { it.path.endsWith("BigScreen.kt") }
        assertFalse(shown.complete)
        assertFalse(pack.isComplete("$gen/BigScreen.kt"))
        assertTrue(pack.render().contains("INCOMPLETE"))
        assertTrue(pack.render().contains("READ: $gen/BigScreen.kt:"))
    }

    @Test fun relatedFilesAreChosenFromTheRequest() {
        val filler = (1..400).joinToString("\n") { "val z$it = $it" }
        val files = mapOf(
            root to "package p\nfun AppRoot() {}\n",
            calc to "package p\nfun CalculatorScreen() {}\n$filler\n",
            unused to "package p\nfun Unused() {}\n$filler\n",
        )
        val pack = ContextSelector.select("fix the calculatorscreen layout", emptyList(), files, budget = 6000)
        assertTrue(pack.shown.any { it.path == calc })
    }

    @Test fun readChunkIsNumberedAndMarksTheRest() {
        val text = (1..10).joinToString("\n") { "line$it" }
        val c = Reader.chunk("f.kt", text, 3, 5)
        assertTrue(c.contains("   3| line3"))
        assertTrue(c.contains("INCOMPLETE"))
        assertTrue(c.contains("READ: f.kt:6-10"))
        val all = Reader.chunk("f.kt", text, null, null)
        assertTrue(all.contains("[end of file: lines 1-10 of 10]"))
    }

    @Test fun deletingAFileThatIsStillUsedIsBlocked() {
        assertEquals(listOf(root), Protect.deleteBlockers(calc, small))
        assertTrue(Protect.deleteBlockers(unused, small).isEmpty())
    }
}
