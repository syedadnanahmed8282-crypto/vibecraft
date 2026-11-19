package com.vibecraft.app.gen

import java.util.UUID

/**
 * Finds code blocks that name a file and returns (path, content).
 * The path may be: a comment on the first line, part of the fence line (```kotlin:Path.kt),
 * the line just above the block, or inferred from the Kotlin declaration.
 */
object CodeBlocks {
    const val GEN = "app/src/main/java/com/vibecraft/generated"

    private val fence = Regex("(?m)^[ \\t]*```([^\\n]*)\\r?\\n(.*?)^[ \\t]*```[ \\t]*$", RegexOption.DOT_MATCHES_ALL)
    private val pathToken = Regex("([A-Za-z0-9_./-]+\\.[A-Za-z0-9]{1,6})")
    private val comment = Regex(
        "^(?://|#|--|/\\*|<!--)\\s*(?:file:|path:|filename:)?\\s*([A-Za-z0-9_./-]+\\.[A-Za-z0-9]+)\\s*(?:\\*/|-->)?$",
        RegexOption.IGNORE_CASE,
    )
    private val kotlinName = Regex("(?m)^(?:@Composable\\s+)?(?:fun|class|object|interface)\\s+([A-Za-z_][A-Za-z0-9_]*)")

    fun files(text: String): List<Pair<String, String>> {
        val out = linkedMapOf<String, String>()
        fence.findAll(text).forEach { m ->
            val header = m.groupValues[1].trim()
            val body = m.groupValues[2]
            val lang = header.substringBefore(' ').substringBefore(':').substringBefore('{').lowercase()
            val lines = body.lines()
            var path: String? = null
            var content = body

            // 1. first line comment
            val first = lines.firstOrNull()?.trim() ?: ""
            comment.find(first)?.let { path = it.groupValues[1]; content = lines.drop(1).joinToString("\n") }
            // 2. fence line: ```kotlin:path or ```kotlin title="path"
            if (path == null) path = pathToken.findAll(header).map { it.value }.firstOrNull { it.contains('.') && !it.startsWith(".") }
            // 3. the line right above the block
            if (path == null) {
                val before = text.substring(0, m.range.first).trimEnd().lines().lastOrNull()?.trim().orEmpty()
                val cleaned = before.trim('*', '`', '#', ':', ' ', '-').removePrefix("File").removePrefix("file").trim(':', ' ', '*', '`')
                if (cleaned.isNotEmpty() && pathToken.matchEntire(cleaned) != null) path = cleaned
            }
            // 4. Kotlin: infer from the declared name
            if (path == null && (lang == "kotlin" || lang == "kt")) {
                kotlinName.find(body)?.let { path = "$GEN/${it.groupValues[1]}.kt" }
            }
            val p = path ?: return@forEach
            val (np, nc) = normalize(p, content.trimEnd('\n') + "\n")
            if (np.startsWith("/") || np.contains("..") || np.startsWith(".github/")) return@forEach
            if (nc.length > 200_000) return@forEach
            out[np] = nc
        }
        return out.toList().take(40)
    }

    /** Keeps every Kotlin file inside the generated package so the template's MainActivity can find it. */
    fun normalize(path: String, content: String): Pair<String, String> {
        if (!path.endsWith(".kt")) return path to content
        val p = if (path.startsWith("$GEN/")) path else "$GEN/${path.substringAfterLast('/')}"
        var c = content.replace(Regex("(?m)^package\\s+[\\w.]+")) { r ->
            if (r.value.contains("com.vibecraft.generated")) r.value else "package com.vibecraft.generated"
        }
        if (!Regex("(?m)^package\\s").containsMatchIn(c)) {
            // No package line at all: add one (after any leading @file: annotations).
            val lines = c.lines()
            val k = lines.takeWhile { it.startsWith("@file:") || it.isBlank() }.size
            c = (lines.take(k) + "package com.vibecraft.generated" + "" + lines.drop(k)).joinToString("\n")
        }
        return p to c
    }

    /** A safe relative path, or null. Kotlin files are kept inside the generated package folder. */
    fun cleanPath(raw: String): String? {
        val p = raw.trim().trim('`', '*', '"', '\'', ' ')
        if (!Regex("^[A-Za-z0-9_./-]+\\.[A-Za-z0-9]{1,6}$").matches(p)) return null
        if (p.startsWith("/") || p.contains("..") || p.startsWith(".github/")) return null
        return if (p.endsWith(".kt")) (if (p.startsWith("$GEN/")) p else "$GEN/${p.substringAfterLast('/')}") else p
    }

    /** Drops a first line like "// path/File.kt" that some models add. */
    fun dropPathLine(body: String): String {
        val first = body.lineSequence().firstOrNull()?.trim() ?: return body
        return if (comment.matches(first)) body.lines().drop(1).joinToString("\n") else body
    }

    /** The reply text without fenced code blocks, so the chat stays short. */
    fun stripBlocks(text: String): String = fence.replace(text, "").trim()

    fun fileId(projectId: String, path: String): String =
        UUID.nameUUIDFromBytes("$projectId:$path".toByteArray()).toString()

    fun checkpointId(projectId: String, version: Int): String =
        UUID.nameUUIDFromBytes("$projectId:cp$version".toByteArray()).toString()
}
