package com.vibecraft.app.gen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetsTest {
    private val kt = """
        package p

        @Composable
        fun Home(x: Int) {
            val s = "}{" // braces in a string and a comment }
            Text(s)
        }

        fun Other() = 1

        class Box(val a: Int) {
            fun inner() {}
        }

        data class Pt(val x: Int)
    """.trimIndent()

    private fun target(kind: String, name: String, body: String) = "<<<<<<< REPLACE $kind $name\n$body\n>>>>>>> END"

    @Test fun replacesAFunctionWithItsAnnotationAndIgnoresBracesInStrings() {
        val r = Targets.apply(kt, "A.kt", target("fun", "Home", "@Composable\nfun Home(x: Int) {\n    Text(\"new\")\n}"))
        val t = r.text!!
        assertTrue(t.contains("Text(\"new\")"))
        assertFalse(t.contains("\"}{\""))
        assertTrue(t.contains("fun Other() = 1"))
        assertTrue(t.contains("class Box"))
        assertEquals(1, Regex("@Composable").findAll(t).count())
    }

    @Test fun expressionBodiesAreRefused() {
        val r = Targets.apply(kt, "A.kt", target("fun", "Other", "fun Other() = 2"))
        assertNull(r.text)
        assertTrue(r.error!!.contains("expression body"))
    }

    @Test fun missingNameIsReported() {
        assertTrue(Targets.apply(kt, "A.kt", target("fun", "Nope", "fun Nope() {}")).error!!.contains("No fun named Nope"))
    }

    @Test fun duplicateNamesAreRejected() {
        val r = Targets.apply("fun Dup() {}\nfun Dup() {}\n", "A.kt", target("fun", "Dup", "fun Dup() {}"))
        assertNull(r.text)
        assertTrue(r.error!!.contains("must be unique"))
    }

    @Test fun replacesAClassWithABody() {
        val t = Targets.apply(kt, "A.kt", target("class", "Box", "class Box(val a: Int) {\n    fun added() {}\n}")).text!!
        assertTrue(t.contains("fun added()"))
        assertFalse(t.contains("fun inner()"))
        assertTrue(t.contains("data class Pt"))
    }

    @Test fun replacesAClassWithoutABody() {
        val t = Targets.apply(kt, "A.kt", target("class", "Pt", "data class Pt(val x: Int, val y: Int)")).text!!
        assertTrue(t.trimEnd().endsWith("data class Pt(val x: Int, val y: Int)"))
    }

    private val css = ".a { color: red; }\n@media (max-width: 600px) {\n  .a { color: blue; }\n}\n.b, .c { margin: 0 }\n"

    @Test fun replacesACssRule() {
        val t = Targets.apply(css, "s.css", target("css", ".b, .c", ".b, .c { margin: 4px }")).text!!
        assertTrue(t.contains("margin: 4px"))
        assertFalse(t.contains("margin: 0"))
    }

    @Test fun aCssSelectorUsedTwiceMustBeUnique() {
        assertTrue(Targets.apply(css, "s.css", target("css", ".a", ".a { color: green }")).error!!.contains("must be unique"))
    }

    @Test fun replacesAWholeMediaRule() {
        val t = Targets.apply(css, "s.css", target("css", "@media (max-width: 600px)", "@media (max-width: 600px) {\n  .a { color: pink; }\n}")).text!!
        assertTrue(t.contains("pink"))
        assertFalse(t.contains("blue"))
    }

    @Test fun replacesAnHtmlElementWithNestedTags() {
        val html = "<div id=\"root\"><div><p>x</p></div></div><span id=\"s\">1</span>"
        val t = Targets.apply(html, "i.html", target("id", "root", "<main id=\"root\">new</main>")).text
        assertEquals("<main id=\"root\">new</main><span id=\"s\">1</span>", t)
    }

    @Test fun replacesAJavaScriptFunction() {
        val js = "function a(x) { if (x) { return 1 } return 2 }\nfunction b() {}\n"
        val t = Targets.apply(js, "a.js", target("function", "a", "function a() { return 0 }")).text!!
        assertTrue(t.startsWith("function a() { return 0 }"))
        assertTrue(t.contains("function b()"))
    }

    @Test fun aTargetKindMustFitTheFileType() {
        assertTrue(Targets.apply(css, "s.css", target("fun", "x", "fun x() {}")).error!!.contains("cannot be used"))
    }

    @Test fun engineCombinesTargetsAndSearchBlocksAndChecksTheVersion() {
        val src = "fun A() {\n    val v = 1\n}\n\nfun B() {}\n"
        val text = target("fun", "B", "fun B() { println(2) }") + "\n<<<<<<< SEARCH\nval v = 1\n=======\nval v = 5\n>>>>>>> REPLACE"
        val ok = EditEngine.apply(src, "A.kt", text, Manifest.hash(src))
        assertNotNull(ok.text)
        assertTrue(ok.text!!.contains("println(2)"))
        assertTrue(ok.text!!.contains("val v = 5"))
        assertTrue(EditEngine.apply(src, "A.kt", text, "000000000000").error!!.contains("changed"))
        assertTrue(EditEngine.isEdit(text))
    }
}
