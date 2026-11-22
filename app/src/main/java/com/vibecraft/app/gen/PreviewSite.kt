package com.vibecraft.app.gen

/** An Android screen to simulate. Sizes are in dp; layout in dp does not change with density. */
data class DeviceProfile(
    val id: String, val label: String, val widthDp: Int, val heightDp: Int, val density: Float,
    val statusDp: Int, val gestureNavDp: Int, val buttonNavDp: Int,
) {
    val bucket: String get() = when {
        density >= 3.5f -> "xxxhdpi"
        density >= 2.5f -> "xxhdpi"
        density >= 1.75f -> "xhdpi"
        density >= 1.25f -> "hdpi"
        else -> "mdpi"
    }
    val short: String get() = "${widthDp}×${heightDp}"
}

object Devices {
    val all = listOf(
        DeviceProfile("small", "Small phone", 360, 640, 2.0f, 24, 16, 48),
        DeviceProfile("standard", "Standard phone", 360, 800, 3.0f, 28, 20, 48),
        DeviceProfile("large", "Large phone", 412, 915, 2.6f, 32, 24, 48),
        DeviceProfile("tablet", "Tablet", 600, 960, 1.5f, 24, 20, 48),
    )
}

data class PreviewConfig(val device: DeviceProfile, val dark: Boolean, val buttonNav: Boolean) {
    val safeTop: Int get() = device.statusDp
    val safeBottom: Int get() = if (buttonNav) device.buttonNavDp else device.gestureNavDp
    fun key() = "${device.id}|$dark|$buttonNav"
}

/**
 * Serves the project's preview files from a private address, like a small web server.
 * Relative links, styles, scripts and text assets work; anything else is answered with "not found",
 * so the preview never reaches the network or the phone's files.
 */
class PreviewSite(private val files: Map<String, String>, private val spec: DesignSpec) {
    enum class Mode { NONE, LEGACY, MODERN }
    class Served(val mime: String, val body: ByteArray, val status: Int = 200)

    companion object {
        const val HOST = "https://preview.vibecraft.local"
        const val MARKER = "vc-preview v2"
        private const val TOKENS = "/_vc/tokens.css"

        fun mime(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            "js" -> "application/javascript"
            "json" -> "application/json"
            "svg" -> "image/svg+xml"
            "xml" -> "text/xml"
            "txt", "md" -> "text/plain"
            else -> "application/octet-stream"
        }

        /** "/a/./b/../c" style paths are refused; the result always starts with "/". */
        fun normalize(raw: String): String? {
            val dec = try { java.net.URLDecoder.decode(raw, "UTF-8") } catch (e: Exception) { return null }
            val segs = dec.split('/').filter { it.isNotEmpty() && it != "." }
            if (segs.any { it == ".." }) return null
            return "/" + segs.joinToString("/")
        }
    }

    val mode: Mode = when {
        files.containsKey("preview/index.html") -> Mode.MODERN
        files["preview.html"]?.contains(MARKER) == true -> Mode.MODERN
        files.containsKey("preview.html") -> Mode.LEGACY
        else -> Mode.NONE
    }

    val entry: String? = when {
        mode != Mode.MODERN -> null
        files.containsKey("preview/index.html") -> "/preview/index.html"
        else -> "/preview.html"
    }

    /** Screens the user can jump to: the entry page first, then preview/screens/<name>.html. */
    fun screens(): List<String> {
        val e = entry ?: return emptyList()
        val rest = files.keys.filter { it.startsWith("preview/screens/") && it.endsWith(".html") }.sorted().map { "/$it" }
        return listOf(e) + rest.filter { it != e }
    }

    /** Changes whenever any file changes, so the preview knows when to reload. */
    val version: Int = files.entries.sortedBy { it.key }.fold(7) { acc, e -> 31 * acc + e.key.hashCode() * 17 + e.value.hashCode() }

    private fun notFound() = Served("text/plain", "Not found".toByteArray(), 404)

    fun serve(url: String, cfg: PreviewConfig): Served {
        if (!url.startsWith(HOST)) return notFound()
        val raw = url.removePrefix(HOST).substringBefore('#').substringBefore('?')
        val path = normalize(raw) ?: return notFound()
        if (path == TOKENS) return Served("text/css", tokensCss(cfg).toByteArray(Charsets.UTF_8))
        val target = if (path == "/") entry ?: return notFound() else path
        val key = target.removePrefix("/")
        val body = files[key] ?: return notFound()
        return if (key.endsWith(".html")) Served("text/html", inject(body, cfg).toByteArray(Charsets.UTF_8))
        else Served(mime(key), body.toByteArray(Charsets.UTF_8))
    }

    fun tokensCss(cfg: PreviewConfig): String =
        Design.toCss(spec) +
            ":root{--safe-top:${cfg.safeTop}px;--safe-bottom:${cfg.safeBottom}px;--screen-w:${cfg.device.widthDp}px;" +
            "--screen-h:${cfg.device.heightDp}px;--density:${cfg.device.density};}\n" +
            "html,body{background:var(--color-background);color:var(--color-on-surface);font-size:var(--font-body)}\n" +
            "body{padding-top:var(--safe-top);padding-bottom:var(--safe-bottom)}\n" +
            ".vc-status{position:fixed;top:0;left:0;right:0;height:var(--safe-top);display:flex;align-items:center;justify-content:space-between;" +
            "padding:0 14px;font:600 12px sans-serif;color:var(--color-on-surface);z-index:2147483000;pointer-events:none}\n" +
            ".vc-nav{position:fixed;bottom:0;left:0;right:0;height:var(--safe-bottom);display:flex;align-items:center;justify-content:center;" +
            "z-index:2147483000;pointer-events:none}\n" +
            ".vc-nav.gesture i{width:96px;height:4px;border-radius:2px;background:var(--color-on-surface);opacity:.35}\n" +
            ".vc-nav.btns{justify-content:space-around;padding:0 18%}\n" +
            ".vc-nav.btns i{width:14px;height:14px;border:2px solid var(--color-on-surface);opacity:.45;border-radius:3px}\n" +
            ".vc-nav.btns i:nth-child(2){border-radius:50%}\n"

    private fun insertAfterOpen(html: String, tag: String, add: String): String? {
        val i = html.indexOf(tag, ignoreCase = true)
        if (i < 0) return null
        val end = html.indexOf('>', i)
        if (end < 0) return null
        return html.substring(0, end + 1) + add + html.substring(end + 1)
    }

    private fun insertBeforeClose(html: String, tag: String, add: String): String? {
        val i = html.lastIndexOf(tag, ignoreCase = true)
        return if (i < 0) null else html.substring(0, i) + add + html.substring(i)
    }

    /** Adds the viewport, the design tokens, the fixed screen size and the status and navigation bars. */
    fun inject(html: String, cfg: PreviewConfig): String {
        val w = cfg.device.widthDp
        val h = cfg.device.heightDp
        val headStart = "<meta name=\"viewport\" content=\"width=$w,initial-scale=1\"><link rel=\"stylesheet\" href=\"/_vc/tokens.css\">" +
            if (cfg.dark) "<script>document.documentElement.classList.add('dark')</script>" else ""
        val headEnd = "<style>html{width:${w}px!important;height:${h}px!important;margin:0!important;overflow:hidden!important}" +
            "body{width:${w}px!important;height:${h}px!important;margin:0!important;overflow-y:auto!important;overflow-x:hidden!important;box-sizing:border-box}</style>"
        val chrome = "<div class=\"vc-status\"><span>9:41</span><span>●●▮</span></div>" +
            "<div class=\"vc-nav ${if (cfg.buttonNav) "btns" else "gesture"}\"><i></i>${if (cfg.buttonNav) "<i></i><i></i>" else ""}</div>"
        var out = html
        out = insertAfterOpen(out, "<head", headStart) ?: (headStart + out)
        out = insertBeforeClose(out, "</head>", headEnd) ?: (out + headEnd)
        out = insertBeforeClose(out, "</body>", chrome) ?: (out + chrome)
        return out
    }
}
