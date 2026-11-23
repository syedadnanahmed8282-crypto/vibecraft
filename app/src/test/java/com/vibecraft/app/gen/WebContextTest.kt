package com.vibecraft.app.gen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebContextTest {
    private val gen = CodeBlocks.GEN
    private val index = "<html><head><link rel=\"stylesheet\" href=\"styles/app.css\"><script src=\"scripts/a.js\"></script>" +
        "<link href=\"https://x.example/y.css\"></head><body><a href=\"screens/detail.html\">d</a></body></html>"
    private val files = mapOf(
        "$gen/AppRoot.kt" to "package p\nfun AppRoot() {}\n",
        "preview/index.html" to index,
        "preview/styles/app.css" to "@import \"base.css\";\nbody{background:url(../assets/i.svg)}",
        "preview/styles/base.css" to "a{}",
        "preview/scripts/a.js" to "x",
        "preview/screens/detail.html" to "<p>d</p>",
        "preview/assets/i.svg" to "<svg/>",
        "design.json" to "{}",
    )

    private fun deps(path: String) = Manifest.build(files).first { it.path == path }.deps

    @Test fun htmlLinksAreResolvedToProjectFiles() {
        assertEquals(listOf("preview/screens/detail.html", "preview/scripts/a.js", "preview/styles/app.css"), deps("preview/index.html"))
    }

    @Test fun cssImportAndUrlAreResolved() {
        assertEquals(listOf("preview/assets/i.svg", "preview/styles/base.css"), deps("preview/styles/app.css"))
    }

    @Test fun externalAndMissingReferencesAreIgnored() {
        val d = Manifest.webDeps("preview/index.html", "<link href=\"https://a/b.css\"><img src=\"nope.png\"><img src=\"data:x\">", files.keys)
        assertTrue(d.isEmpty())
    }

    @Test fun previewFilesAreShownWhenTheRequestIsAboutTheLook() {
        val paths = ContextSelector.select("change the preview colors", emptyList(), files).shown.map { it.path }
        assertTrue(paths.contains("preview/index.html"))
        assertTrue(paths.contains("design.json"))
        assertTrue(paths.contains("preview/styles/app.css"))
    }

    @Test fun aNamedScreenIsShown() {
        val paths = ContextSelector.select("fix the detail page", emptyList(), files).shown.map { it.path }
        assertTrue(paths.contains("preview/screens/detail.html"))
    }

    @Test fun previewFilesAreLeftOutOtherwise() {
        val paths = ContextSelector.select("rename the title", emptyList(), files).shown.map { it.path }
        assertFalse(paths.any { it.startsWith("preview/") })
        assertTrue(paths.contains("$gen/AppRoot.kt"))
    }

    @Test fun aLinkedStyleSheetCannotBeDeleted() {
        assertTrue(Protect.deleteBlockers("preview/styles/app.css", files).contains("preview/index.html"))
        assertTrue(Protect.deleteBlockers("preview/screens/detail.html", files).contains("preview/index.html"))
    }

    @Test fun manifestKnowsTheNewFileKinds() {
        assertEquals("preview-style", Manifest.type("preview/styles/app.css"))
        assertEquals("preview-script", Manifest.type("preview/scripts/a.js"))
        assertEquals("design-tokens", Manifest.type("design.json"))
    }
}
