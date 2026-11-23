package com.vibecraft.app.gen

/**
 * "<<<<<<< REPLACE fun Name ... >>>>>>> END": replaces one whole definition found by name.
 * Kotlin: fun, class, object, interface. CSS: css <selector>. HTML: id <elementId>. JavaScript: function <name>.
 * The name must match exactly one place.
 */
object Targets {
    private val re = Regex(
        "<<<<<<<\\s*REPLACE\\s+(fun|class|object|interface|css|id|function)\\s+([^\\r\\n]+?)\\s*\\r?\\n(.*?)\\r?\\n?>>>>>>>\\s*END",
        RegexOption.DOT_MATCHES_ALL,
    )
    private val voidTags = setOf("br", "hr", "img", "input", "meta", "link", "source", "area", "base", "col", "embed", "param", "track", "wbr")

    private class Located(val start: Int = 0, val end: Int = 0, val error: String? = null)

    fun isTarget(s: String) = re.containsMatchIn(s)

    fun apply(original: String, path: String, text: String): EditResult {
        val lang = when {
            path.endsWith(".kt") -> "kt"
            path.endsWith(".css") -> "css"
            path.endsWith(".js") -> "js"
            path.endsWith(".html") -> "html"
            else -> ""
        }
        val allowed = when (lang) {
            "kt" -> setOf("fun", "class", "object", "interface")
            "css" -> setOf("css")
            "js" -> setOf("function")
            "html" -> setOf("id")
            else -> emptySet()
        }
        var out = original
        var n = 0
        for (m in re.findAll(text)) {
            val kind = m.groupValues[1]
            val name = m.groupValues[2].trim()
            val body = m.groupValues[3]
            if (kind !in allowed) return EditResult(null, "A '$kind' target cannot be used in this kind of file.")
            val loc = when (lang) {
                "kt" -> locateKotlin(out, kind, name)
                "css" -> locateCss(out, name)
                "js" -> locateJs(out, name)
                else -> locateHtml(out, name)
            }
            if (loc.error != null) return EditResult(null, loc.error)
            out = out.substring(0, loc.start) + body.trimEnd('\n', '\r') + out.substring(loc.end)
            n++
        }
        return if (n > 0) EditResult(out) else EditResult(null, "No target block found.")
    }

    private fun skipString(s: String, start: Int): Int {
        val q = s[start]
        var i = start + 1
        while (i < s.length && s[i] != q && s[i] != '\n') { if (s[i] == '\\') i++; i++ }
        return minOf(i + 1, s.length)
    }

    /** Index just after the closer that matches the opener at [open]; strings and comments are skipped. */
    internal fun closeDelim(s: String, open: Int, o: Char, c: Char, lineComments: Boolean): Int? {
        var depth = 0
        var i = open
        val n = s.length
        while (i < n) {
            val ch = s[i]
            if (lineComments && s.startsWith("//", i)) { while (i < n && s[i] != '\n') i++; continue }
            if (s.startsWith("/*", i)) { val e = s.indexOf("*/", i + 2); if (e < 0) return null; i = e + 2; continue }
            if (lineComments && s.startsWith("\"\"\"", i)) { val e = s.indexOf("\"\"\"", i + 3); if (e < 0) return null; i = e + 3; continue }
            if (ch == '"' || ch == '\'' || (lineComments && ch == '`')) { i = skipString(s, i); continue }
            if (ch == o) depth++
            else if (ch == c) { depth--; if (depth == 0) return i + 1 }
            i++
        }
        return null
    }

    /** Annotation lines right above a declaration belong to it. */
    private fun withAnnotations(s: String, start: Int): Int {
        var st = start
        while (st > 0) {
            val prevEnd = st - 1
            val prevStart = s.lastIndexOf('\n', prevEnd - 1) + 1
            if (prevStart >= prevEnd) break
            if (s.substring(prevStart, prevEnd).trim().startsWith("@")) st = prevStart else break
        }
        return st
    }

    private fun locateKotlin(s: String, kind: String, name: String): Located {
        val q = Regex.escape(name)
        val mods = "(?:(?:public|private|internal|protected|override|open|abstract|sealed|data|enum|annotation|inner|suspend|inline|operator|tailrec|fun)\\s+)*"
        val re = if (kind == "fun") {
            Regex("(?m)^[ \\t]*(?:(?:public|private|internal|protected|override|open|suspend|inline|operator|tailrec)\\s+)*fun\\s+(?:<[^>]*>\\s*)?(?:[A-Za-z0-9_.<>?]+\\.)?$q\\s*\\(")
        } else {
            Regex("(?m)^[ \\t]*$mods$kind\\s+$q\\b")
        }
        val hits = re.findAll(s).toList()
        if (hits.isEmpty()) return Located(error = "No $kind named $name was found.")
        if (hits.size > 1) return Located(error = "$kind $name appears ${hits.size} times; it must be unique.")
        val m = hits[0]
        val start = withAnnotations(s, m.range.first)
        if (kind != "fun") return classEnd(s, m.range.last + 1, start)
        val pclose = closeDelim(s, m.range.last, '(', ')', true) ?: return Located(error = "The parameters of $name are not closed.")
        var i = pclose
        while (i < s.length && s[i] != '{' && s[i] != '=') i++
        if (i >= s.length) return Located(error = "The body of $name was not found.")
        if (s[i] == '=') return Located(error = "$name has an expression body; use a SEARCH/REPLACE block for it.")
        val end = closeDelim(s, i, '{', '}', true) ?: return Located(error = "The body of $name is not closed.")
        return Located(start, end)
    }

    /** A class, object or interface: with a body in braces, or a one-line declaration. */
    private fun classEnd(s: String, from: Int, start: Int): Located {
        var i = from
        var paren = 0
        val n = s.length
        while (i < n) {
            val ch = s[i]
            if (s.startsWith("//", i)) { while (i < n && s[i] != '\n') i++; continue }
            if (s.startsWith("/*", i)) { val e = s.indexOf("*/", i + 2); i = if (e < 0) n else e + 2; continue }
            if (ch == '"' || ch == '\'') { i = skipString(s, i); continue }
            if (ch == '(') {
                paren++
            } else if (ch == ')') {
                paren--
            } else if (ch == '{' && paren == 0) {
                val end = closeDelim(s, i, '{', '}', true) ?: return Located(error = "The body is not closed.")
                return Located(start, end)
            } else if (ch == '\n' && paren == 0) {
                val before = s.substring(s.lastIndexOf('\n', i - 1) + 1, i).trimEnd()
                var j = i + 1
                while (j < n && s[j].isWhitespace()) j++
                if (!(before.endsWith(",") || before.endsWith(":") || (j < n && s[j] == '{'))) return Located(start, i)
            }
            i++
        }
        return Located(start, n)
    }

    private fun locateJs(s: String, name: String): Located {
        val q = Regex.escape(name)
        val hits = Regex("(?m)^[ \\t]*(?:export\\s+)?(?:async\\s+)?function\\s*\\*?\\s*$q\\s*\\(").findAll(s).toList()
        if (hits.isEmpty()) return Located(error = "No function named $name was found.")
        if (hits.size > 1) return Located(error = "function $name appears ${hits.size} times; it must be unique.")
        val m = hits[0]
        val pclose = closeDelim(s, m.range.last, '(', ')', true) ?: return Located(error = "The parameters of $name are not closed.")
        val open = s.indexOf('{', pclose)
        if (open < 0) return Located(error = "The body of $name was not found.")
        val end = closeDelim(s, open, '{', '}', true) ?: return Located(error = "The body of $name is not closed.")
        return Located(m.range.first, end)
    }

    private fun norm(t: String) = t.replace(Regex("\\s+"), " ").replace(Regex("\\s*,\\s*"), ", ").trim()

    private fun locateCss(s: String, selector: String): Located {
        val want = norm(selector)
        val hits = mutableListOf<Pair<Int, Int>>()
        var seg = 0
        var i = 0
        val n = s.length
        while (i < n) {
            val ch = s[i]
            if (s.startsWith("/*", i)) { val e = s.indexOf("*/", i + 2); i = if (e < 0) n else e + 2; continue }
            if (ch == '"' || ch == '\'') { i = skipString(s, i); continue }
            if (ch == '{') {
                val sel = norm(s.substring(seg, i).replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), ""))
                val end = closeDelim(s, i, '{', '}', false)
                if (sel == want && end != null) {
                    var st = seg
                    while (st < i && s[st].isWhitespace()) st++
                    hits.add(st to end)
                }
                seg = i + 1
            } else if (ch == '}' || ch == ';') {
                seg = i + 1
            }
            i++
        }
        if (hits.isEmpty()) return Located(error = "No CSS rule for $selector was found.")
        if (hits.size > 1) return Located(error = "The CSS rule $selector appears ${hits.size} times; it must be unique.")
        return Located(hits[0].first, hits[0].second)
    }

    private fun locateHtml(s: String, id: String): Located {
        val q = Regex.escape(id)
        val hits = Regex("<([a-zA-Z][a-zA-Z0-9]*)\\b[^>]*\\bid\\s*=\\s*[\"']$q[\"'][^>]*>").findAll(s).toList()
        if (hits.isEmpty()) return Located(error = "No element with id $id was found.")
        if (hits.size > 1) return Located(error = "The id $id appears ${hits.size} times; it must be unique.")
        val m = hits[0]
        val tag = m.groupValues[1].lowercase()
        if (tag in voidTags || m.value.endsWith("/>")) return Located(m.range.first, m.range.last + 1)
        var depth = 0
        for (t in Regex("<(/?)$tag\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(s, m.range.first)) {
            if (t.value.endsWith("/>")) continue
            if (t.groupValues[1] == "/") {
                depth--
                if (depth == 0) return Located(m.range.first, t.range.last + 1)
            } else {
                depth++
            }
        }
        return Located(error = "The element with id $id is not closed.")
    }
}

/** One entry point for every kind of edit block: named targets first, then exact SEARCH/REPLACE blocks. */
object EditEngine {
    fun isEdit(s: String) = Edits.isEdit(s) || Targets.isTarget(s)

    fun apply(original: String, path: String, text: String, expectedHash: String?): EditResult {
        if (expectedHash != null && Manifest.hash(original) != expectedHash) {
            return EditResult(null, "The file changed after the AI looked at it.")
        }
        var out = original
        if (Targets.isTarget(text)) {
            val r = Targets.apply(out, path, text)
            if (r.text == null) return r
            out = r.text
        }
        if (Edits.isEdit(text)) {
            val r = Edits.applyBlocks(out, text)
            if (r.text == null) return r
            out = r.text
        }
        return EditResult(out)
    }
}
