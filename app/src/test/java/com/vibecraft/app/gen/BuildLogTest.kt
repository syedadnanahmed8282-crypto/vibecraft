package com.vibecraft.app.gen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BuildLogTest {
    private val ts = "2026-11-19T10:00:00.1234567Z "
    private val home = "app/src/main/java/com/vibecraft/generated/Home.kt"

    private fun log(vararg lines: String) = lines.joinToString("\n") { ts + it }

    @Test fun kotlinErrorsHaveFileLineAndMessageAndAreDeduplicated() {
        val raw = log(
            "e: file:///home/runner/work/r/r/$home:12:5 Unresolved reference: foo",
            "e: file:///home/runner/work/r/r/$home:12:5 Unresolved reference: foo",
            "e: /home/runner/work/r/r/app/src/main/java/com/vibecraft/generated/Other.kt: (3, 7): Type mismatch",
            "* What went wrong:",
            "Execution failed for task ':app:compileDebugKotlin'.",
        )
        val errors = BuildLog.parse(raw)
        assertEquals(2, errors.size)
        assertEquals(ErrKind.KOTLIN, errors[0].kind)
        assertEquals(home, errors[0].file)
        assertEquals(12, errors[0].line)
        assertEquals("Unresolved reference: foo", errors[0].message)
        assertEquals(3, errors[1].line)
    }

    @Test fun genericGradleLineIsKeptWhenNothingMoreSpecificExists() {
        val errors = BuildLog.parse(log("* What went wrong:", "Execution failed for task ':app:compileDebugKotlin'."))
        assertEquals(1, errors.size)
        assertEquals(ErrKind.GRADLE, errors[0].kind)
    }

    @Test fun dependencyErrorsComeBeforeKotlinErrors() {
        val raw = log(
            "e: file:///x/$home:1:1 Boom",
            "Could not resolve com.example:lib:1.0.",
        )
        val errors = BuildLog.parse(raw)
        assertEquals(ErrKind.DEPENDENCY, errors[0].kind)
        assertEquals(ErrKind.KOTLIN, errors[1].kind)
    }

    @Test fun resourceAndManifestErrorsAreRecognised() {
        val res = BuildLog.parse(log("ERROR: /h/r/app/src/main/res/drawable/ic.xml:5: AAPT: error: attribute android:foo not found."))
        assertEquals(ErrKind.RESOURCE, res[0].kind)
        assertEquals("app/src/main/res/drawable/ic.xml", res[0].file)
        assertEquals(5, res[0].line)
        val man = BuildLog.parse(log("Manifest merger failed : Attribute application@icon value=(@mipmap/x) is bad"))
        assertEquals(ErrKind.MANIFEST, man[0].kind)
    }

    @Test fun renderKeepsOnlyTheMostImportantErrors() {
        val raw = (1..20).joinToString("\n") { "e: file:///x/$home:$it:1 Error number $it" }
        val text = BuildLog.render(BuildLog.parse(raw), maxErrors = 12)
        assertEquals(12, Regex("\\[KOTLIN]").findAll(text).count())
    }

    @Test fun noErrorsInAnOrdinaryLog() {
        assertTrue(BuildLog.parse(log("BUILD SUCCESSFUL in 1m", "w: just a warning")).isEmpty())
    }

    @Test fun zipLogsAreUnpackedAndParsed() {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            z.putNextEntry(ZipEntry("build/5_Build.txt"))
            z.write(("e: file:///x/$home:1:1 Boom\n").toByteArray())
            z.closeEntry()
        }
        val text = LogZip.extract(bos.toByteArray())
        assertNotNull(text)
        assertTrue(text!!.contains("Boom"))
        assertEquals(1, BuildLog.parse(text).size)
    }

    @Test fun corruptOrEmptyArchivesGiveNull() {
        assertNull(LogZip.extract("this is not a zip".toByteArray()))
        assertNull(LogZip.extract(ByteArray(0)))
    }
}
