package com.vibecraft.app.gen

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class TaskState { TODO, DOING, DONE, FAILED }

@Serializable
data class Task(val title: String, val state: TaskState = TaskState.TODO, val note: String = "")

/** What the app really did on the last request, recorded by the app and not by the AI. */
@Serializable
data class RunReport(
    val request: String, val summary: String, val results: List<String>,
    val version: Int?, val problem: String?, val at: Long,
)

/**
 * Everything the app remembers about a project besides its files: the AI-kept memory, the approved brief,
 * the roadmap, unfinished tasks and the last run. Stored in the project (so it syncs and survives a reinstall).
 */
@Serializable
data class Brain(
    val memory: String = "",
    val brief: String = "",
    val roadmap: List<Task> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val last: RunReport? = null,
    val active: Int = -1,
    val sinceMemory: Int = 0,
)

data class NextTask(val title: String, val roadmapIndex: Int?)

object BrainOps {
    const val PATH = ".vibecraft/brain.json"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun parse(text: String?): Brain {
        if (text.isNullOrBlank()) return Brain()
        return try { json.decodeFromString<Brain>(text) } catch (e: Exception) { Brain() }
    }

    fun toJson(b: Brain): String = json.encodeToString(b)

    private fun open(t: Task) = t.state == TaskState.TODO || t.state == TaskState.FAILED

    /** Unfinished file-level tasks come first, then the next roadmap step. */
    fun next(b: Brain): NextTask? {
        b.tasks.firstOrNull { open(it) }?.let { return NextTask(it.title, null) }
        val i = b.roadmap.indexOfFirst { open(it) }
        return if (i >= 0) NextTask(b.roadmap[i].title, i) else null
    }

    private fun mark(t: Task) = when (t.state) {
        TaskState.DONE -> "done"; TaskState.FAILED -> "failed"; TaskState.DOING -> "doing"; TaskState.TODO -> "todo"
    }

    /** The memory block that goes into every request. */
    fun render(b: Brain): String = buildString {
        append("Project memory:\n").append(b.memory.ifBlank { "(nothing yet)" }).append('\n')
        if (b.brief.isNotBlank()) append("\nApproved brief:\n").append(b.brief.take(1800)).append('\n')
        if (b.roadmap.isNotEmpty()) {
            append("\nRoadmap:\n")
            b.roadmap.forEach { append("- [").append(mark(it)).append("] ").append(it.title).append('\n') }
        }
        val open = b.tasks.filter { open(it) }
        if (open.isNotEmpty()) {
            append("\nUnfinished tasks:\n")
            open.forEach { append("- [").append(mark(it)).append("] ").append(it.title).append(if (it.note.isNotBlank()) " (${it.note.take(120)})" else "").append('\n') }
        }
        b.last?.let {
            append("\nLast run: \"").append(it.request.take(160)).append("\" -> ")
            append(if (it.version != null) "saved v${it.version}" else "nothing saved")
            if (it.results.isNotEmpty()) append("; ").append(it.results.joinToString(", "))
            if (!it.problem.isNullOrBlank()) append("; problem: ").append(it.problem.take(200))
            append('\n')
        }
    }.trimEnd()

    /** A plain answer to "what happened?", built only from what the app recorded. */
    fun statusText(b: Brain, bengali: Boolean): String {
        val l = b.last ?: return if (bengali) "এখনো কোনো কাজ চলেনি।" else "Nothing has run yet."
        val unfinished = b.tasks.filter { open(it) }.map { it.title } + b.roadmap.filter { open(it) }.map { it.title }
        return buildString {
            if (bengali) {
                append("শেষ অনুরোধ: ").append(l.request.take(120)).append('\n')
                append("ফল: ").append(if (l.version != null) "v${l.version} সেভ হয়েছে" else "কিছু সেভ হয়নি").append('\n')
                l.results.forEach { append("• ").append(it).append('\n') }
                append("সমস্যা: ").append(l.problem?.takeIf { it.isNotBlank() } ?: "নেই").append('\n')
                append("বাকি কাজ: ").append(if (unfinished.isEmpty()) "নেই" else unfinished.take(4).joinToString("; "))
                if (unfinished.isNotEmpty()) append("\nচালিয়ে যেতে \"ঠিক করো\" লিখুন।")
            } else {
                append("Last request: ").append(l.request.take(120)).append('\n')
                append("Result: ").append(if (l.version != null) "saved v${l.version}" else "nothing saved").append('\n')
                l.results.forEach { append("- ").append(it).append('\n') }
                append("Problem: ").append(l.problem?.takeIf { it.isNotBlank() } ?: "none").append('\n')
                append("Unfinished: ").append(if (unfinished.isEmpty()) "none" else unfinished.take(4).joinToString("; "))
                if (unfinished.isNotEmpty()) append("\nSay \"continue\" to pick it up.")
            }
        }
    }

    private val sectionRe = Regex("(?im)^\\s*(COMMIT|SUMMARY|MEMORY):\\s*")

    /** Splits "COMMIT: ... SUMMARY: ... MEMORY: ..." into its parts (keys in capitals). */
    fun sections(text: String): Map<String, String> {
        val ms = sectionRe.findAll(text).toList()
        val out = LinkedHashMap<String, String>()
        ms.forEachIndexed { i, m ->
            val end = if (i + 1 < ms.size) ms[i + 1].range.first else text.length
            out[m.groupValues[1].uppercase()] = text.substring(m.range.last + 1, end).trim()
        }
        return out
    }
}

enum class Intent { ACT, ANSWER }

/** Rules that decide, before and after the AI answers, whether the user wants work done or just an answer. */
object Intents {
    private val intentRe = Regex("(?im)^\\s*INTENT:\\s*(ACT|ANSWER)\\b")
    private val controlRe = Regex("(?im)^\\s*(?:INTENT|FILE|DELETE|READ|APP_NAME)\\s*:.*(?:\\r?\\n|$)")
    private val summaryLabel = Regex("(?im)^\\s*SUMMARY:\\s*")

    fun parseLine(text: String): Intent? = intentRe.find(text)?.let { if (it.groupValues[1].uppercase() == "ACT") Intent.ACT else Intent.ANSWER }

    /** The answer without the machine lines. */
    fun stripControl(text: String): String = summaryLabel.replace(controlRe.replace(text, ""), "").trim()

    fun isBengali(t: String) = t.count { it in '\u0980'..'\u09FF' } >= 3

    private fun norm(t: String) = t.lowercase().trim().trim('.', '!', '?', '।', ' ', '\n')

    private val statusPhrases = listOf(
        "কি হইছে", "কী হইছে", "কি হয়েছে", "কী হয়েছে", "কি হলো", "কী হলো", "কি অবস্থা", "কী অবস্থা", "কাজ কতদূর",
        "what happened", "what's happening", "whats happening", "status",
    )

    /** "What happened?" style questions: answered from the recorded last run. */
    fun quickStatus(t: String): Boolean {
        val n = norm(t)
        return n.length <= 40 && statusPhrases.any { it in n }
    }

    private val resumeWords = setOf(
        "দাও", "দিন", "ওকে", "ঠিক আছে", "হ্যাঁ", "হ্যা", "আবার", "আবার করো", "চালিয়ে যাও", "চালিয়ে যান", "কন্টিনিউ", "শুরু করো",
        "ঠিক করো", "ঠিক কর", "মুছো", "মুছে দাও", "কখন ঠিক করবে", "কখন করবে", "এখন করো", "করো", "করে দাও", "পরেরটা", "পরের ধাপ",
        "ok", "okay", "yes", "do it", "go ahead", "continue", "retry", "try again", "next", "start",
    )

    /** A bare "ok / continue / fix it": resume unfinished work. Anything with real content is a new request. */
    fun quickResume(t: String): Boolean = norm(t) in resumeWords

    private val reviewWords = listOf("চেক", "দেখো", "দেখ ", "যাচাই", "check", "review", "রিভিউ")
    private val actionWords = listOf(
        "ঠিক করো", "ঠিক কর", "বানাও", "যোগ করো", "যোগ কর", "বদলাও", "বদলে", "মুছো", "মুছে", "তৈরি করো", "আপডেট করো",
        "fix", "add ", "change", "make ", "create", "remove", "delete", "update", "redesign",
    )

    /** "Check it" or "look at it" without asking for a change: nothing may be modified. */
    fun looksLikeReview(t: String): Boolean {
        val l = t.lowercase()
        return reviewWords.any { it in l } && actionWords.none { it in l }
    }

    fun skipPlan(t: String): Boolean {
        val l = t.lowercase()
        return listOf("সরাসরি", "directly", "skip plan", "no plan", "প্ল্যান ছাড়া", "পরিকল্পনা ছাড়া").any { it in l }
    }
}
