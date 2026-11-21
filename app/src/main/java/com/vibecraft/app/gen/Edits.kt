package com.vibecraft.app.gen

data class EditResult(val text: String?, val error: String? = null)

/** Applies "<<<<<<< SEARCH ... ======= ... >>>>>>> REPLACE" blocks. Every target must match exactly one place. */
object Edits {
    private val re = Regex(
        "<<<<<<<\\s*SEARCH\\r?\\n(.*?)\\r?\\n=======\\r?\\n(.*?)\\r?\\n?>>>>>>>\\s*REPLACE",
        RegexOption.DOT_MATCHES_ALL,
    )

    fun isEdit(s: String) = re.containsMatchIn(s)

    /** expectedHash: the version the AI was shown; a different file now means the edit is stale. */
    fun apply(original: String, text: String, expectedHash: String? = null): EditResult {
        if (expectedHash != null && Manifest.hash(original) != expectedHash) {
            return EditResult(null, "The file changed after the AI looked at it.")
        }
        var out = original
        var n = 0
        for (m in re.findAll(text)) {
            val search = m.groupValues[1]
            val replace = m.groupValues[2]
            if (search.isBlank()) return EditResult(null, "An edit block has an empty search part.")
            val idx = out.indexOf(search)
            if (idx >= 0) {
                if (out.indexOf(search, idx + 1) >= 0) return EditResult(null, "An edit target appears more than once; it must be unique.")
                out = out.substring(0, idx) + replace + out.substring(idx + search.length)
            } else {
                out = when (val r = replaceLoose(out, search, replace)) {
                    is Loose.Done -> r.text
                    Loose.Many -> return EditResult(null, "An edit target appears more than once; it must be unique.")
                    Loose.None -> return EditResult(null, "An edit target was not found in the file.")
                }
            }
            n++
        }
        return if (n > 0) EditResult(out) else EditResult(null, "No edit block found.")
    }

    private sealed class Loose {
        class Done(val text: String) : Loose()
        object Many : Loose()
        object None : Loose()
    }

    /** Same match, ignoring spaces at the end of each line. */
    private fun replaceLoose(orig: String, search: String, replace: String): Loose {
        val ol = orig.lines()
        val sl = search.lines().map { it.trimEnd() }
        if (sl.isEmpty() || sl.size > ol.size) return Loose.None
        val hits = (0..ol.size - sl.size).filter { i -> sl.indices.all { ol[i + it].trimEnd() == sl[it] } }
        return when {
            hits.isEmpty() -> Loose.None
            hits.size > 1 -> Loose.Many
            else -> Loose.Done((ol.take(hits[0]) + replace.lines() + ol.drop(hits[0] + sl.size)).joinToString("\n"))
        }
    }
}
