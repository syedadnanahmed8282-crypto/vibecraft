package com.vibecraft.app.gen

/** Applies "<<<<<<< SEARCH ... ======= ... >>>>>>> REPLACE" blocks so the AI only sends the parts that change. */
object Edits {
    private val re = Regex(
        "<<<<<<<\\s*SEARCH\\r?\\n(.*?)\\r?\\n=======\\r?\\n(.*?)\\r?\\n?>>>>>>>\\s*REPLACE",
        RegexOption.DOT_MATCHES_ALL,
    )

    fun isEdit(s: String) = re.containsMatchIn(s)

    /** Returns the new file text, or null when any block does not match the file. */
    fun apply(original: String, text: String): String? {
        var out = original
        var n = 0
        for (m in re.findAll(text)) {
            val search = m.groupValues[1]
            val replace = m.groupValues[2]
            if (search.isBlank()) return null
            val idx = out.indexOf(search)
            out = if (idx >= 0) out.substring(0, idx) + replace + out.substring(idx + search.length)
            else replaceLoose(out, search, replace) ?: return null
            n++
        }
        return if (n > 0) out else null
    }

    /** Same match, but ignoring spaces at the end of each line. */
    private fun replaceLoose(orig: String, search: String, replace: String): String? {
        val ol = orig.lines()
        val sl = search.lines().map { it.trimEnd() }
        if (sl.isEmpty() || sl.size > ol.size) return null
        for (i in 0..ol.size - sl.size) {
            if (sl.indices.all { ol[i + it].trimEnd() == sl[it] }) {
                return (ol.take(i) + replace.lines() + ol.drop(i + sl.size)).joinToString("\n")
            }
        }
        return null
    }
}
