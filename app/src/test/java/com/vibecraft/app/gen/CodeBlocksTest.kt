package com.vibecraft.app.gen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeBlocksTest {
    private val gen = CodeBlocks.GEN

    @Test fun firstLineCommentGivesThePath() {
        val text = "Here:\n```kotlin\n// $gen/A.kt\npackage com.vibecraft.generated\nfun A() {}\n```\n"
        val f = CodeBlocks.files(text)
        assertEquals(1, f.size)
        assertEquals("$gen/A.kt", f[0].first)
        assertTrue(f[0].second.contains("fun A()"))
        assertFalse(f[0].second.contains("// $gen"))
    }

    @Test fun fenceHeaderPathIsUsedAndPackageIsAdded() {
        val f = CodeBlocks.files("```kotlin:B.kt\nfun B() {}\n```")
        assertEquals("$gen/B.kt", f.single().first)
        assertTrue(f.single().second.contains("package com.vibecraft.generated"))
    }

    @Test fun foreignPackageIsRewritten() {
        val (path, text) = CodeBlocks.normalize("x/Y.kt", "package com.foo\n\nfun a() {}\n")
        assertEquals("$gen/Y.kt", path)
        assertTrue(text.contains("package com.vibecraft.generated"))
        assertFalse(text.contains("com.foo"))
    }

    @Test fun unclosedBlockIsIgnored() {
        assertTrue(CodeBlocks.files("```kotlin\n// $gen/A.kt\nfun A() {").isEmpty())
    }

    @Test fun blocksWithoutAnyNameAreIgnored() {
        assertTrue(CodeBlocks.files("```\nhello\n```").isEmpty())
    }

    @Test fun stripBlocksKeepsOnlyProse() {
        val t = CodeBlocks.stripBlocks("Done.\n```kotlin\n// $gen/A.kt\nfun A() {}\n```\nBye")
        assertTrue(t.contains("Done."))
        assertFalse(t.contains("fun A"))
    }
}
