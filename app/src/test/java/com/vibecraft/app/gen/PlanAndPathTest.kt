package com.vibecraft.app.gen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanAndPathTest {
    private val gen = CodeBlocks.GEN

    @Test fun parsesSummaryNameFilesDeletesAndReads() {
        val t = """
            SUMMARY: I will add history.
            APP_NAME: Hisab
            FILE: $gen/Home.kt | home screen
            FILE: preview.html | sketch
            DELETE: $gen/Old.kt
            READ: $gen/Home.kt:10-40
        """.trimIndent()
        val p = PlanParser.parse(t)
        assertEquals("I will add history.", p.summary)
        assertEquals("Hisab", p.appName)
        assertEquals(listOf("$gen/Home.kt", "preview.html"), p.files.map { it.path })
        assertEquals(listOf("$gen/Old.kt"), p.deletes)
        assertEquals(ReadReq("$gen/Home.kt", 10, 40), p.reads.single())
    }

    @Test fun readWithoutRangeHasNoLimits() {
        val p = PlanParser.parse("READ: $gen/A.kt")
        assertEquals(ReadReq("$gen/A.kt", null, null), p.reads.single())
    }

    @Test fun kotlinPathsAreKeptInsideTheGeneratedPackage() {
        val p = PlanParser.parse("FILE: src/other/Foo.kt | x")
        assertEquals("$gen/Foo.kt", p.files.single().path)
    }

    @Test fun protectedAndUnsafePathsAreDropped() {
        val t = """
            FILE: app/build.gradle.kts | x
            FILE: ../evil.kt | x
            FILE: /abs/x.kt | x
            FILE: .github/workflows/build.yml | x
            FILE: app/src/main/AndroidManifest.xml | x
            DELETE: $gen/AppRoot.kt
            DELETE: $gen/MainActivity.kt
        """.trimIndent()
        val p = PlanParser.parse(t)
        assertTrue(p.files.isEmpty())
        assertTrue(p.deletes.isEmpty())
    }

    @Test fun cleanPathRejectsTraversal() {
        assertNull(CodeBlocks.cleanPath("../x.kt"))
        assertNull(CodeBlocks.cleanPath("a/../b.kt"))
        assertEquals("preview.html", CodeBlocks.cleanPath("`preview.html`"))
    }

    @Test fun protectRules() {
        assertTrue(Protect.isProtected("app/build.gradle.kts"))
        assertTrue(Protect.isProtected(".github/workflows/build.yml"))
        assertTrue(Protect.canWrite("preview.html"))
        assertTrue(Protect.canWrite("$gen/Home.kt"))
        assertFalse(Protect.canWrite("README.md"))
        assertFalse(Protect.canDelete("$gen/AppRoot.kt"))
    }
}
