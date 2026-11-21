package com.vibecraft.app.gen

import org.junit.Assert.assertTrue
import org.junit.Test

class LintTest {
    private val gen = CodeBlocks.GEN
    private fun issues(code: String, path: String = "$gen/Foo.kt") = Lint.check(mapOf(path to code))

    @Test fun unbalancedBracesAreReported() {
        assertTrue(issues("package p\nfun a() {\n").any { it.text.contains("Unbalanced") })
    }

    @Test fun bracketsInStringsAndCommentsAreIgnored() {
        assertTrue(issues("package p\nval s = \"{\" // }\n").isEmpty())
    }

    @Test fun missingIconIsReported() {
        assertTrue(issues("import androidx.compose.material.icons.filled.Backspace\n").any { it.text.contains("Backspace") })
    }

    @Test fun basicIconIsFine() {
        assertTrue(issues("import androidx.compose.material.icons.filled.Add\n").isEmpty())
    }

    @Test fun unavailableLibraryIsReported() {
        assertTrue(issues("import com.google.gson.Gson\n").any { it.text.contains("not available") })
    }

    @Test fun appRootMustExistAsAComposable() {
        val r = Lint.check(mapOf("$gen/AppRoot.kt" to "package p\nfun Other() {}\n"))
        assertTrue(r.any { it.text.contains("AppRoot") })
    }
}
