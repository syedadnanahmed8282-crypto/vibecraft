package com.vibecraft.app.gen

import java.security.MessageDigest

/** One row of the project manifest. hash = version of the file's content. */
data class FileInfo(
    val path: String, val type: String, val purpose: String,
    val deps: List<String>, val lines: Int, val chars: Int, val hash: String,
)

object Manifest {
    fun hash(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .take(6).joinToString("") { "%02x".format(it) }

    private val declRe = Regex(
        "(?m)^\\s*(?:public\\s+|private\\s+|internal\\s+)?(?:@Composable\\s+)?" +
            "(?:data\\s+|sealed\\s+|enum\\s+|abstract\\s+|open\\s+)?(?:fun|class|object|interface|typealias)\\s+(?:<[^>]*>\\s*)?([A-Za-z_][A-Za-z0-9_]*)",
    )
    private val wordRe = Regex("[A-Za-z_][A-Za-z0-9_]*")

    fun type(path: String) = when {
        path.endsWith(".kt") -> "kotlin"
        path.endsWith(".html") -> "html-preview"
        path.endsWith(".xml") -> "android-xml"
        else -> "other"
    }

    /** Names of the functions, classes and objects a Kotlin file declares. */
    fun declared(text: String): List<String> = declRe.findAll(text).map { it.groupValues[1] }.distinct().toList()

    private fun purpose(path: String, text: String) = when (type(path)) {
        "kotlin" -> declared(text).take(5).let { if (it.isEmpty()) "Kotlin file" else "declares " + it.joinToString(", ") }
        "html-preview" -> "design sketch shown in the Preview tab"
        "android-xml" -> path.substringAfterLast('/')
        else -> "file"
    }

    /** files: path -> content. Dependencies are the other project files whose declarations this file uses. */
    fun build(files: Map<String, String>): List<FileInfo> {
        val owner = HashMap<String, String>()
        files.filterKeys { it.endsWith(".kt") }.toSortedMap().forEach { (p, t) -> declared(t).forEach { owner.putIfAbsent(it, p) } }
        return files.map { (path, text) ->
            val deps = if (path.endsWith(".kt")) {
                wordRe.findAll(text).map { it.value }.toHashSet().mapNotNull { owner[it] }.filter { it != path }.distinct().sorted().take(8)
            } else emptyList()
            FileInfo(path, type(path), purpose(path, text), deps, text.count { it == '\n' } + 1, text.length, hash(text))
        }.sortedBy { it.path }
    }

    fun render(list: List<FileInfo>): String = list.joinToString("\n") { i ->
        "${i.path} | ${i.type} | ${i.purpose} | uses: " +
            (if (i.deps.isEmpty()) "-" else i.deps.joinToString(",") { it.substringAfterLast('/') }) +
            " | ${i.lines} lines | #${i.hash}"
    }
}

/** A file (or the start of it) that is shown to the AI. complete = the whole file is included. */
data class Shown(val path: String, val text: String, val complete: Boolean, val from: Int, val to: Int, val totalLines: Int)

data class ContextPack(val manifest: String, val shown: List<Shown>, val notShown: List<String>) {
    fun isComplete(path: String) = shown.firstOrNull { it.path == path }?.complete == true

    fun render(): String = buildString {
        append("Project manifest (path | type | purpose | uses | size | version):\n").append(manifest).append("\n")
        shown.forEach { s ->
            append("\n--- ").append(s.path)
            if (s.complete) append(" [complete]\n")
            else append(" [INCOMPLETE: lines ${s.from}-${s.to} of ${s.totalLines}; the rest is not shown. Never rewrite this whole file; ")
                .append("change only visible parts with edit blocks, or ask READ: ${s.path}:${s.to + 1}-${s.to + 200}]\n")
            append(s.text).append('\n')
        }
        if (notShown.isNotEmpty()) append("\nNot shown (use READ if you need them): ").append(notShown.joinToString(", ")).append('\n')
    }
}

object ContextSelector {
    const val DEFAULT_BUDGET = 14_000

    /** Picks the Kotlin files that matter for this request, within a character budget. */
    fun select(request: String, recent: List<String>, files: Map<String, String>, budget: Int = DEFAULT_BUDGET): ContextPack {
        val infos = Manifest.build(files)
        val manifest = Manifest.render(infos)
        val kt = files.filterKeys { it.endsWith(".kt") }
        val order: List<String> = if (kt.values.sumOf { it.length } <= budget) kt.keys.sorted() else rank(request, recent, kt, infos)

        val shown = mutableListOf<Shown>()
        var left = budget
        for (p in order) {
            val t = kt.getValue(p)
            val lines = t.lines()
            if (t.length <= left) {
                shown.add(Shown(p, t, true, 1, lines.size, lines.size)); left -= t.length
            } else if (left > 600) {
                var used = 0
                var n = 0
                for (l in lines) { if (used + l.length + 1 > left) break; used += l.length + 1; n++ }
                shown.add(Shown(p, lines.take(n).joinToString("\n"), false, 1, n, lines.size)); left = 0
            } else break
        }
        val shownPaths = shown.map { it.path }.toSet()
        return ContextPack(manifest, shown, files.keys.filter { it !in shownPaths }.sorted())
    }

    private fun rank(request: String, recent: List<String>, kt: Map<String, String>, infos: List<FileInfo>): List<String> {
        val text = (request + " " + recent.joinToString(" ")).lowercase()
        val score = HashMap<String, Int>()
        kt.forEach { (p, t) ->
            var s = 0
            val base = p.substringAfterLast('/').removeSuffix(".kt").lowercase()
            if (base.length >= 3 && base in text) s += 3
            s += Manifest.declared(t).count { it.length >= 4 && it.lowercase() in text } * 2
            if (p.endsWith("/AppRoot.kt")) s += 1
            if (s > 0) score[p] = s
        }
        val top = score.entries.sortedByDescending { it.value }.map { it.key }.toMutableList()
        val deps = infos.filter { it.path in top }.flatMap { it.deps }.filter { it !in top }.distinct()
        return top + deps
    }
}

/** A READ request: a file and an optional line range. */
data class ReadReq(val path: String, val from: Int?, val to: Int?)

object Reader {
    const val MAX_LINES = 300

    /** Numbered lines of a range, with the file version and an explicit marker when the file continues. */
    fun chunk(path: String, text: String, from: Int?, to: Int?): String {
        val lines = text.lines()
        val total = lines.size
        val s = (from ?: 1).coerceIn(1, total)
        val e = minOf(to ?: (s + MAX_LINES - 1), s + MAX_LINES - 1, total).coerceAtLeast(s)
        val body = (s..e).joinToString("\n") { i -> "%4d| %s".format(i, lines[i - 1]) }
        val tail = if (e < total) "[INCOMPLETE: lines $s-$e of $total. Next: READ: $path:${e + 1}-${minOf(total, e + MAX_LINES)}]"
        else "[end of file: lines $s-$e of $total]"
        return "--- $path (version #${Manifest.hash(text)}; line numbers are for reference only and are not part of the code)\n$body\n$tail"
    }
}
