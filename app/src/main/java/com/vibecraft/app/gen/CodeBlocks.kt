package com.vibecraft.app.gen

import java.util.UUID

/** Finds fenced code blocks whose first line is a comment holding a relative file path. */
object CodeBlocks {
    private val fence = Regex("```[\\w+.-]*[ \\t]*\\r?\\n(.*?)```", RegexOption.DOT_MATCHES_ALL)
    private val marker = Regex(
        "^(?://|#|--|/\\*|<!--)\\s*(?:file:|path:)?\\s*([A-Za-z0-9_./-]+\\.[A-Za-z0-9]+)\\s*(?:\\*/|-->)?$",
        RegexOption.IGNORE_CASE,
    )

    fun files(text: String): List<Pair<String, String>> {
        val out = linkedMapOf<String, String>()
        fence.findAll(text).forEach { m ->
            val body = m.groupValues[1]
            val first = body.lineSequence().firstOrNull()?.trim() ?: return@forEach
            val path = marker.find(first)?.groupValues?.get(1) ?: return@forEach
            if (path.startsWith("/") || path.contains("..") || path.startsWith(".github/")) return@forEach
            val content = body.substringAfter('\n', "").trimEnd('\n') + "\n"
            if (content.length > 200_000) return@forEach
            out[path] = content
        }
        return out.toList().take(40)
    }

    fun fileId(projectId: String, path: String): String =
        UUID.nameUUIDFromBytes("$projectId:$path".toByteArray()).toString()

    fun checkpointId(projectId: String, version: Int): String =
        UUID.nameUUIDFromBytes("$projectId:cp$version".toByteArray()).toString()
}
