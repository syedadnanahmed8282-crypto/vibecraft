package com.vibecraft.app.gen

data class Decl(val name: String, val kind: String, val firstLine: Int, val lastLine: Int)

data class SplitResult(val original: String, val created: Map<String, String>, val moved: List<String>, val error: String? = null)

/**
 * Splits a file that is too long to rewrite: the AI only chooses the grouping, and the app moves the exact source of
 * each declaration into new files in the same package. Nothing is retyped, so nothing can be lost or changed.
 */
object Splitter {
    private val topRe = Regex(
        "(?m)^(?:(?:public|private|internal|data|sealed|enum|abstract|open|inline|suspend|operator|annotation|fun)\\s+)*" +
            "(fun|class|object|interface)\\s+(?:<[^>]*>\\s*)?(?:[A-Za-z0-9_.<>?]+\\.)?([A-Za-z_][A-Za-z0-9_]*)",
    )
    private val groupRe = Regex("(?im)^\\s*[-*]?\\s*GROUP:\\s*([A-Za-z0-9_]+\\.kt)\\s*\\|\\s*(.+)$")
    private val kinds = listOf("fun", "class", "object", "interface")
    private val widenRe = Regex(
        "(?m)^private\\s+(?=(?:fun|val|var|const|class|object|interface|data|enum|sealed|abstract|open|inline|suspend|typealias|lateinit)\\b)",
    )

    private fun find(text: String, name: String): Triple<String, Int, Int>? {
        for (k in kinds) Targets.findKotlin(text, k, name)?.let { return Triple(k, it.first, it.second) }
        return null
    }

    /** Top-level declarations that can be moved: a unique name and a block or one-line body. */
    fun outline(text: String): List<Decl> {
        val names = LinkedHashSet<String>()
        topRe.findAll(text).forEach { names.add(it.groupValues[2]) }
        return names.mapNotNull { n ->
            val f = find(text, n) ?: return@mapNotNull null
            Decl(n, f.first, text.substring(0, f.second).count { it == '\n' } + 1, text.substring(0, f.third).count { it == '\n' } + 1)
        }.sortedBy { it.firstLine }
    }

    /** GROUP lines from the AI: new file name and the declarations to move. Unknown names and taken file names are ignored. */
    fun parseGroups(reply: String, allowed: Set<String>, takenFiles: Set<String>, originalFile: String): Map<String, List<String>> {
        val used = HashSet<String>()
        val out = LinkedHashMap<String, List<String>>()
        for (m in groupRe.findAll(reply)) {
            val file = m.groupValues[1]
            if (file == originalFile || file in takenFiles) continue
            val names = m.groupValues[2].split(',').map { it.trim().trim('`', '*') }.filter { it in allowed && used.add(it) }
            if (names.isNotEmpty() && (out.size < 6 || out.containsKey(file))) out[file] = (out[file] ?: emptyList()) + names
        }
        return out
    }

    private fun header(text: String): String {
        val lines = text.lines()
        val pkg = lines.firstOrNull { it.startsWith("package ") }
        val imports = lines.filter { it.startsWith("import ") }
        return (listOfNotNull(pkg) + (if (imports.isNotEmpty()) listOf("") + imports else emptyList())).joinToString("\n")
    }

    /** Moves the named declarations into new files next to [path]. Top-level `private` is dropped so the pieces can see each other. */
    fun split(text: String, path: String, groups: Map<String, List<String>>): SplitResult {
        val dir = path.substringBeforeLast('/', "")
        val head = header(text)
        var rest = text
        val created = linkedMapOf<String, String>()
        val moved = mutableListOf<String>()
        for ((file, names) in groups) {
            val parts = mutableListOf<String>()
            for (n in names) {
                val f = find(rest, n) ?: continue
                parts.add(rest.substring(f.second, f.third).trimEnd())
                rest = rest.removeRange(f.second, f.third)
                moved.add(n)
            }
            if (parts.isNotEmpty()) created[(if (dir.isEmpty()) "" else "$dir/") + file] = head + "\n\n" + parts.joinToString("\n\n") + "\n"
        }
        if (moved.isEmpty()) return SplitResult(text, emptyMap(), emptyList(), "Nothing could be moved.")
        val original = widenRe.replace(rest, "").replace(Regex("\\n{3,}"), "\n\n").trimEnd() + "\n"
        return SplitResult(original, created.mapValues { widenRe.replace(it.value, "") }, moved)
    }
}
