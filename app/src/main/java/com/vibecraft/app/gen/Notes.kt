package com.vibecraft.app.gen

/** A chat message reduced to what grouping needs, so this file has no Android or Room dependency. */
data class RawMsg(val id: String, val role: String, val content: String)

data class FileChange(val status: String, val path: String, val desc: String = "")

/** An error or a paused run. done/total are set only when files were being written. */
data class Problem(
    val kind: String, val title: String, val sub: String = "", val raw: String = "",
    val done: Int = -1, val total: Int = -1, val stoppedAt: String = "",
)

sealed interface ChatItem { val key: String }
data class Plain(val msg: RawMsg) : ChatItem { override val key: String get() = msg.id }
data class Changes(val files: List<FileChange>, val version: Int?, override val key: String) : ChatItem
data class Trouble(val problem: Problem, override val key: String) : ChatItem

/**
 * Notes the app writes into the chat are plain strings; some carry structure:
 *   FILE|status|path|desc
 *   ERR|kind|title|sub|raw
 *   PAUSE|kind|done|total|stoppedAt|title|sub|raw
 * The chat shows them as cards, never as paths or raw error text.
 */
object Notes {
    private fun clean(s: String) = s.replace('|', '/').replace('\r', ' ').replace('\n', ' ').trim()

    fun file(status: String, path: String, desc: String) = "FILE|${clean(status)}|${clean(path)}|${clean(desc).take(140)}"
    fun err(kind: String, title: String, sub: String, raw: String) =
        "ERR|${clean(kind)}|${clean(title)}|${clean(sub)}|${clean(raw).take(400)}"
    fun pause(kind: String, title: String, sub: String, raw: String, done: Int, total: Int, stoppedAt: String) =
        "PAUSE|${clean(kind)}|$done|$total|${clean(stoppedAt)}|${clean(title)}|${clean(sub)}|${clean(raw).take(400)}"

    fun parseFile(s: String): FileChange? {
        val p = s.split("|", limit = 4)
        if (p.size < 3 || p[0] != "FILE") return null
        return FileChange(p[1], p[2], p.getOrElse(3) { "" })
    }

    fun parseProblem(s: String): Problem? {
        if (s.startsWith("ERR|")) {
            val q = s.split("|", limit = 5)
            return if (q.size == 5) Problem(q[1], q[2], q[3], q[4]) else null
        }
        if (s.startsWith("PAUSE|")) {
            val q = s.split("|", limit = 8)
            return if (q.size == 8) Problem(q[1], q[5], q[6], q[7], q[2].toIntOrNull() ?: -1, q[3].toIntOrNull() ?: -1, q[4]) else null
        }
        return null
    }

    /** A readable one-liner for places that cannot show cards (what the AI is told, the status record). */
    fun plain(s: String): String {
        parseProblem(s)?.let { return problemText(it) }
        parseFile(s)?.let { return "${it.status} ${it.path.substringAfterLast('/')}" }
        return s
    }

    fun problemText(p: Problem): String =
        if (p.done >= 0) "Paused at ${p.stoppedAt} (${p.done} of ${p.total} files saved): ${p.title}"
        else p.title + if (p.sub.isBlank()) "" else ". " + p.sub

    /** The card in words: what changed, and in which version. */
    fun changesText(ch: Changes): String =
        headline(ch.files) + (ch.version?.let { " (saved as version $it)" } ?: "") + ": " +
            ch.files.joinToString(", ") { "${it.path.substringAfterLast('/')} (${it.status})" }

    /** What a file is for, in a word. Paths are never shown in the chat. */
    fun roleOf(path: String): String {
        val n = path.substringAfterLast('/')
        return when {
            path.startsWith("preview") -> "Preview"
            n == "design.json" -> "Design"
            n.startsWith("ic_launcher") -> "Icon"
            n.endsWith("Screen.kt") -> "Screen"
            n.endsWith("ViewModel.kt") -> "Logic"
            n.endsWith("Store.kt") || n.endsWith("Repository.kt") || n.endsWith("Dao.kt") -> "Data"
            n.endsWith("Theme.kt") || n.endsWith("Color.kt") || n.endsWith("Type.kt") -> "Style"
            n == "MainActivity.kt" -> "Entry"
            n == "AppRoot.kt" -> "App root"
            n.endsWith(".kt") -> "Code"
            n.endsWith(".xml") -> "Resource"
            else -> "File"
        }
    }

    fun kindOf(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
        "kt" -> "kt"; "json" -> "json"; "html", "htm" -> "html"; "xml" -> "xml"; else -> "other"
    }

    fun badge(path: String): String = when (kindOf(path)) {
        "kt" -> "Kt"; "json" -> "{ }"; "html" -> "</>"; "xml" -> "XML"; else -> "·"
    }

    fun headline(files: List<FileChange>): String {
        val n = files.size
        val kinds = files.map { it.status }.toSet()
        val verb = if (kinds.size == 1) kinds.first() else "changed"
        return "$n ${if (n == 1) "file" else "files"} $verb"
    }

    private val savedRe = Regex("^Saved \\d+ files? · v(\\d+)$")

    /** Turns raw messages into what the chat draws: file notes become one card, error notes become problem cards. */
    fun group(msgs: List<RawMsg>): List<ChatItem> {
        val out = mutableListOf<ChatItem>()
        var i = 0
        while (i < msgs.size) {
            val m = msgs[i]
            val isNote = m.role == "note"
            if (isNote && m.content.startsWith("FILE|")) {
                val files = mutableListOf<FileChange>()
                var j = i
                while (j < msgs.size && msgs[j].role == "note" && msgs[j].content.startsWith("FILE|")) {
                    parseFile(msgs[j].content)?.let { files.add(it) }
                    j++
                }
                var version: Int? = null
                if (j < msgs.size && msgs[j].role == "note") {
                    savedRe.find(msgs[j].content)?.let { version = it.groupValues[1].toInt(); j++ }
                }
                if (files.isNotEmpty()) out.add(Changes(files.associateBy { it.path }.values.toList(), version, "chg-${m.id}"))
                i = j
                continue
            }
            if (isNote) {
                val p = parseProblem(m.content)
                if (p != null) { out.add(Trouble(p, "pb-${m.id}")); i++; continue }
            }
            out.add(Plain(m))
            i++
        }
        return out
    }
}
