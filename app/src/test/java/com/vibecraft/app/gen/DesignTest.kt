package com.vibecraft.app.gen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignTest {
    @Test fun defaultsWhenEmpty() {
        val s = Design.parse(null)
        assertEquals("#1F6B43", s.light["primary"])
        assertTrue(s.issues.isEmpty())
        assertEquals(s, Design.parse("  "))
    }

    @Test fun validJsonOverridesOnlyWhatItSets() {
        val s = Design.parse("""{"name":"Hisab","colors":{"light":{"primary":"#112233"}},"typography":{"title":30},"spacing":{"m":20}}""")
        assertEquals("Hisab", s.name)
        assertEquals("#112233", s.light["primary"])
        assertEquals("#FFFFFF", s.light["onPrimary"])
        assertEquals(30, s.sizes["title"])
        assertEquals(20, s.sizes["spaceM"])
        assertTrue(s.issues.isEmpty())
    }

    @Test fun invalidColorFallsBackAndIsReported() {
        val s = Design.parse("""{"colors":{"light":{"primary":"green"}}}""")
        assertEquals("#1F6B43", s.light["primary"])
        assertTrue(s.issues.any { it.contains("colors.light.primary") })
    }

    @Test fun notJsonGivesDefaultsAndAnIssue() {
        val s = Design.parse("{ this is not json")
        assertEquals("#1F6B43", s.light["primary"])
        assertTrue(s.issues.any { it.contains("not valid JSON") })
    }

    @Test fun numbersAreKeptInRange() {
        assertEquals(128, Design.parse("""{"spacing":{"m":9999}}""").sizes["spaceM"])
        assertEquals(8, Design.parse("""{"typography":{"title":1}}""").sizes["title"])
    }

    @Test fun cssHasVariablesForBothModes() {
        val css = Design.toCss(Design.default())
        assertTrue(css.contains("--color-primary:#1F6B43;"))
        assertTrue(css.contains("--color-on-primary:#FFFFFF;"))
        assertTrue(css.contains("--space-m:16px"))
        assertTrue(css.contains("html.dark{"))
        assertTrue(css.contains("--color-primary:#6FDD93;"))
    }

    @Test fun kotlinOutputIsAnObjectWithColorsAndSizes() {
        val k = Design.toKotlin(Design.default())
        assertTrue(k.contains("package com.vibecraft.generated"))
        assertTrue(k.contains("object DesignTokens"))
        assertTrue(k.contains("val primary = Color(0xFF1F6B43)"))
        assertTrue(k.contains("val spaceM = 16.dp"))
        assertTrue(k.contains("const val titleWeight = 700"))
    }

    @Test fun designFileIsWritableButGeneratedTokensAreNot() {
        assertTrue(Protect.canWrite("design.json"))
        assertFalse(Protect.canWrite("${CodeBlocks.GEN}/DesignTokens.kt"))
    }

    @Test fun lintReportsDesignProblems() {
        val issues = Lint.check(mapOf("design.json" to """{"colors":{"dark":{"surface":"nope"}}}"""))
        assertTrue(issues.any { it.text.contains("colors.dark.surface") })
    }

    @Test fun templateAlwaysIncludesTheGeneratedTokens() {
        val f = Template.files("App", "abcdef12", """{"colors":{"light":{"primary":"#010203"}}}""")
        assertTrue(f.getValue("${CodeBlocks.GEN}/DesignTokens.kt").contains("Color(0xFF010203)"))
        assertTrue(Template.files("App", "abcdef12").containsKey("${CodeBlocks.GEN}/DesignTokens.kt"))
    }
}
