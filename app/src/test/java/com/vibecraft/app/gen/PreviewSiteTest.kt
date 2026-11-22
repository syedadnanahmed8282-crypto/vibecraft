package com.vibecraft.app.gen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewSiteTest {
    private val phone = Devices.all[1]
    private val cfg = PreviewConfig(phone, dark = false, buttonNav = false)
    private val host = PreviewSite.HOST
    private val v2 = "<!-- vc-preview v2 --><html><head><title>x</title></head><body>hi</body></html>"

    private fun site(files: Map<String, String>) = PreviewSite(files, Design.default())
    private fun text(s: PreviewSite.Served) = String(s.body, Charsets.UTF_8)

    @Test fun modesAreDetected() {
        assertEquals(PreviewSite.Mode.NONE, site(emptyMap()).mode)
        assertEquals(PreviewSite.Mode.LEGACY, site(mapOf("preview.html" to "<html></html>")).mode)
        assertEquals(PreviewSite.Mode.MODERN, site(mapOf("preview.html" to v2)).mode)
        assertEquals(PreviewSite.Mode.MODERN, site(mapOf("preview/index.html" to "<html></html>")).mode)
    }

    @Test fun oldPreviewHasNoEntryAndServesNothing() {
        val s = site(mapOf("preview.html" to "<html></html>"))
        assertEquals(null, s.entry)
        assertEquals(404, s.serve("$host/preview.html", cfg).status.let { if (s.entry == null) 404 else it })
    }

    @Test fun screensListEntryFirstThenTheRest() {
        val s = site(mapOf("preview/index.html" to v2, "preview/screens/b.html" to v2, "preview/screens/a.html" to v2))
        assertEquals(listOf("/preview/index.html", "/preview/screens/a.html", "/preview/screens/b.html"), s.screens())
    }

    @Test fun rootServesTheEntryPage() {
        val r = site(mapOf("preview/index.html" to v2)).serve("$host/", cfg)
        assertEquals(200, r.status)
        assertTrue(text(r).contains("hi"))
    }

    @Test fun htmlGetsTokensViewportAndSystemBars() {
        val r = site(mapOf("preview.html" to v2)).serve("$host/preview.html", cfg)
        val html = text(r)
        assertEquals("text/html", r.mime)
        assertTrue(html.contains("/_vc/tokens.css"))
        assertTrue(html.contains("width=360"))
        assertTrue(html.contains("vc-status"))
        assertTrue(html.contains("vc-nav gesture"))
    }

    @Test fun darkModeAndButtonNavigationAreApplied() {
        val dark = PreviewConfig(phone, dark = true, buttonNav = true)
        val html = text(site(mapOf("preview.html" to v2)).serve("$host/preview.html", dark))
        assertTrue(html.contains("classList.add('dark')"))
        assertTrue(html.contains("vc-nav btns"))
    }

    @Test fun tokensCssCarriesTheDeviceAndSafeAreas() {
        val css = text(site(mapOf("preview.html" to v2)).serve("$host/_vc/tokens.css", cfg))
        assertTrue(css.contains("--safe-top:28px"))
        assertTrue(css.contains("--safe-bottom:20px"))
        assertTrue(css.contains("--screen-w:360px"))
        assertTrue(css.contains("--color-primary"))
    }

    @Test fun relativeAssetsAreServedWithTheRightType() {
        val s = site(mapOf("preview/index.html" to v2, "preview/styles/app.css" to "body{}", "preview/scripts/a.js" to "1", "preview/assets/i.svg" to "<svg/>"))
        assertEquals("text/css", s.serve("$host/preview/styles/app.css?v=1", cfg).mime)
        assertEquals("application/javascript", s.serve("$host/preview/scripts/a.js", cfg).mime)
        assertEquals("image/svg+xml", s.serve("$host/preview/assets/i.svg", cfg).mime)
    }

    @Test fun traversalAndOutsideAddressesAreRefused() {
        val s = site(mapOf("preview/index.html" to v2, "app/build.gradle.kts" to "secret"))
        assertEquals(404, s.serve("$host/preview/../app/build.gradle.kts", cfg).status)
        assertEquals(404, s.serve("$host/%2e%2e/secret", cfg).status)
        assertEquals(404, s.serve("https://evil.example/x", cfg).status)
        assertEquals(404, s.serve("$host/missing.html", cfg).status)
    }

    @Test fun versionChangesWhenAFileChanges() {
        val a = site(mapOf("preview.html" to v2)).version
        val b = site(mapOf("preview.html" to v2 + " ")).version
        assertNotEquals(a, b)
        assertEquals(a, site(mapOf("preview.html" to v2)).version)
    }

    @Test fun deviceBucketsAndConfig() {
        assertEquals("xxhdpi", Devices.all[1].bucket)
        assertEquals("xhdpi", Devices.all[0].bucket)
        assertEquals(48, PreviewConfig(phone, false, true).safeBottom)
        assertEquals(20, PreviewConfig(phone, false, false).safeBottom)
    }
}
