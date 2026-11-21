package com.vibecraft.app.gen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditsTest {
    private fun edit(search: String, replace: String) =
        "<<<<<<< SEARCH\n$search\n=======\n$replace\n>>>>>>> REPLACE"

    @Test fun appliesAnExactEdit() {
        val r = Edits.apply("a\nb\nc\n", edit("b", "B"))
        assertEquals("a\nB\nc\n", r.text)
        assertNull(r.error)
    }

    @Test fun ambiguousTargetIsRejected() {
        val r = Edits.apply("x\nx\n", edit("x", "y"))
        assertNull(r.text)
        assertTrue(r.error!!.contains("more than once"))
    }

    @Test fun missingTargetIsRejected() {
        val r = Edits.apply("a\nb\n", edit("zzz", "y"))
        assertNull(r.text)
        assertTrue(r.error!!.contains("not found"))
    }

    @Test fun emptyReplacementRemovesCode() {
        val r = Edits.apply("a\nb\nc\n", "<<<<<<< SEARCH\nb\n=======\n>>>>>>> REPLACE")
        assertNotNull(r.text)
        assertFalse(r.text!!.contains("b"))
    }

    @Test fun staleVersionIsRejected() {
        val r = Edits.apply("a\nb\n", edit("b", "B"), expectedHash = "000000000000")
        assertNull(r.text)
        assertTrue(r.error!!.contains("changed"))
    }

    @Test fun matchingVersionIsAccepted() {
        val original = "a\nb\n"
        val r = Edits.apply(original, edit("b", "B"), expectedHash = Manifest.hash(original))
        assertEquals("a\nB\n", r.text)
    }

    @Test fun trailingSpacesDoNotBreakTheMatch() {
        val r = Edits.apply("fun x() {  \n  return 1\n}\n", edit("fun x() {\n  return 1\n}", "fun x() { }"))
        assertTrue(r.text!!.contains("fun x() { }"))
    }

    @Test fun textWithoutBlocksIsNotAnEdit() {
        assertFalse(Edits.isEdit("fun a() {}"))
        assertNull(Edits.apply("a", "fun a() {}").text)
    }
}
