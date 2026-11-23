package com.vibecraft.app.gen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitTest {
    private val path = "${CodeBlocks.GEN}/AppRoot.kt"
    private val src = """
        package com.vibecraft.generated

        import androidx.compose.runtime.Composable
        import androidx.compose.material3.Text

        private val Gap = 8

        @Composable
        fun AppRoot() {
            Display()
            Keypad()
        }

        @Composable
        private fun Display() {
            Text("d" + Gap)
        }

        @Composable
        private fun Keypad() {
            Text("k")
        }

        fun helper() = 1
    """.trimIndent()

    @Test fun outlineListsMovableDeclarationsOnly() {
        val names = Splitter.outline(src).map { it.name }
        assertEquals(listOf("AppRoot", "Display", "Keypad"), names)
    }

    @Test fun movesTheExactSourceIntoANewFile() {
        val r = Splitter.split(src, path, mapOf("Panels.kt" to listOf("Display", "Keypad")))
        assertEquals(null, r.error)
        assertEquals(listOf("Display", "Keypad"), r.moved)
        val moved = r.created.getValue("${CodeBlocks.GEN}/Panels.kt")
        assertTrue(moved.contains("package com.vibecraft.generated"))
        assertTrue(moved.contains("import androidx.compose.material3.Text"))
        assertTrue(moved.contains("@Composable\nfun Display()"))
        assertTrue(moved.contains("fun Keypad()"))
        assertFalse(moved.contains("private fun"))
        assertTrue(r.original.contains("fun AppRoot()"))
        assertFalse(r.original.contains("fun Display"))
        assertFalse(r.original.contains("fun Keypad"))
        assertTrue(r.original.contains("val Gap = 8"))
        assertFalse(r.original.contains("private val"))
    }

    @Test fun unknownNamesMoveNothing() {
        val r = Splitter.split(src, path, mapOf("X.kt" to listOf("Nope")))
        assertNotNull(r.error)
        assertEquals(src, r.original)
        assertTrue(r.created.isEmpty())
    }

    @Test fun groupsFromTheAiAreFiltered() {
        val reply = """
            GROUP: Panels.kt | Display, Nope
            GROUP: AppRoot.kt | Keypad
            GROUP: Taken.kt | Keypad
            GROUP: More.kt | Display, Keypad
        """.trimIndent()
        val g = Splitter.parseGroups(reply, setOf("Display", "Keypad"), setOf("Taken.kt"), "AppRoot.kt")
        assertEquals(mapOf("Panels.kt" to listOf("Display"), "More.kt" to listOf("Keypad")), g)
    }

    @Test fun atMostSixGroups() {
        val reply = (1..9).joinToString("\n") { "GROUP: F$it.kt | N$it" }
        val g = Splitter.parseGroups(reply, (1..9).map { "N$it" }.toSet(), emptySet(), "AppRoot.kt")
        assertEquals(6, g.size)
    }
}
