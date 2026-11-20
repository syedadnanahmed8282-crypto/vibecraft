package com.vibecraft.app.gen

import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

/** Fast local checks that catch the most common build breakers before a version is saved. */
object Lint {
    data class Issue(val path: String, val text: String)

    private val coreIcons = setOf(
        "AccountBox", "AccountCircle", "Add", "AddCircle", "ArrowBack", "ArrowDropDown", "ArrowForward", "Build", "Call",
        "Check", "CheckCircle", "Clear", "Close", "Create", "DateRange", "Delete", "Done", "Edit", "Email", "ExitToApp",
        "Face", "Favorite", "FavoriteBorder", "Home", "Info", "KeyboardArrowDown", "KeyboardArrowLeft", "KeyboardArrowRight",
        "KeyboardArrowUp", "List", "LocationOn", "Lock", "MailOutline", "Menu", "MoreVert", "Notifications", "Person",
        "Phone", "Place", "PlayArrow", "Refresh", "Search", "Send", "Settings", "Share", "ShoppingCart", "Star", "ThumbUp", "Warning",
    )
    private val okImports = listOf(
        "androidx.compose.", "androidx.activity.", "androidx.lifecycle.", "kotlinx.coroutines.",
        "kotlin.", "java.", "javax.", "android.", "com.vibecraft.generated",
    )
    private val importRe = Regex("(?m)^import\\s+([\\w.]+)")
    private val importIconRe = Regex("androidx\\.compose\\.material\\.icons\\.(?:automirrored\\.)?(?:filled|outlined|rounded|sharp|twotone)\\.([A-Za-z0-9]+)")
    private val useIconRe = Regex("Icons\\.(?:Default|Filled|Outlined|Rounded|Sharp|TwoTone|AutoMirrored\\.(?:Filled|Outlined|Rounded|Sharp|TwoTone))\\.([A-Za-z0-9]+)")

    /** files: the whole project as it would be saved. */
    fun check(files: Map<String, String>): List<Issue> {
        val out = mutableListOf<Issue>()
        files.forEach { (path, text) ->
            when {
                path.endsWith(".kt") -> kotlin(path, text, out)
                path.endsWith(".xml") -> xml(path, text, out)
            }
        }
        files.keys.firstOrNull { it.endsWith("/AppRoot.kt") }?.let { root ->
            if (!Regex("fun\\s+AppRoot\\s*\\(\\s*\\)").containsMatchIn(files.getValue(root))) {
                out.add(Issue(root, "AppRoot.kt must define a composable function named AppRoot with no parameters."))
            }
        }
        return out.take(12)
    }

    private fun kotlin(path: String, text: String, out: MutableList<Issue>) {
        val clean = strip(text)
        listOf('{' to '}', '(' to ')', '[' to ']').forEach { (o, c) ->
            val a = clean.count { it == o }
            val b = clean.count { it == c }
            if (a != b) out.add(Issue(path, "Unbalanced $o $c: $a opening and $b closing. The file may be cut off or has a typo."))
        }
        importRe.findAll(text).forEach { m ->
            val imp = m.groupValues[1]
            if (okImports.none { imp.startsWith(it) } || imp.contains("icons.extended")) {
                out.add(Issue(path, "Import $imp is not available. Only Compose, AndroidX activity and lifecycle, coroutines and the Kotlin standard library exist."))
            }
        }
        val icons = LinkedHashSet<String>()
        importIconRe.findAll(text).forEach { icons.add(it.groupValues[1]) }
        useIconRe.findAll(text).forEach { icons.add(it.groupValues[1]) }
        icons.filter { it !in coreIcons }.forEach {
            out.add(Issue(path, "Icon $it does not exist here (only basic icons such as Add, Delete, Close, Check, Menu, Settings, Send, Search). Use a basic icon or Text instead."))
        }
    }

    private fun xml(path: String, text: String, out: MutableList<Issue>) {
        try {
            val p = android.util.Xml.newPullParser()
            p.setInput(StringReader(text))
            while (p.next() != XmlPullParser.END_DOCUMENT) { /* just parse */ }
        } catch (e: Exception) {
            out.add(Issue(path, "XML is not well-formed: ${e.message?.take(120) ?: "parse error"}"))
            return
        }
        if (path == Template.ICON_FG && !text.contains("<vector")) out.add(Issue(path, "The icon must be an Android <vector> drawable."))
        if (path == Template.ICON_COLORS && !text.contains("ic_launcher_background")) {
            out.add(Issue(path, "The file must define a color named ic_launcher_background."))
        }
    }

    /** Removes comments, strings and char literals so only code brackets are counted. */
    private fun strip(s: String): String {
        val sb = StringBuilder()
        var i = 0
        val n = s.length
        while (i < n) {
            val c = s[i]
            when {
                s.startsWith("//", i) -> while (i < n && s[i] != '\n') i++
                s.startsWith("/*", i) -> { val e = s.indexOf("*/", i + 2); i = if (e < 0) n else e + 2 }
                s.startsWith("\"\"\"", i) -> { val e = s.indexOf("\"\"\"", i + 3); i = if (e < 0) n else e + 3 }
                c == '"' -> { i++; while (i < n && s[i] != '"' && s[i] != '\n') { if (s[i] == '\\') i++; i++ }; i++ }
                c == '\'' -> { i++; while (i < n && s[i] != '\'' && s[i] != '\n') { if (s[i] == '\\') i++; i++ }; i++ }
                else -> { sb.append(c); i++ }
            }
        }
        return sb.toString()
    }
}
