package com.vibecraft.app.gen

data class PlanFile(val path: String, val desc: String)
data class Plan(val summary: String, val files: List<PlanFile>)

/** Reads the AI's short plan: a SUMMARY line and one FILE line per file. */
object PlanParser {
    private val summaryRe = Regex("(?im)^\\s*SUMMARY:\\s*(.+)$")
    private val fileRe = Regex("(?im)^\\s*[-*]?\\s*FILE:\\s*([^|\\n]+?)\\s*(?:\\|\\s*(.*))?$")

    fun parse(text: String): Plan {
        val summary = summaryRe.find(text)?.groupValues?.get(1)?.trim().orEmpty()
        val files = fileRe.findAll(text).mapNotNull { m ->
            val p = CodeBlocks.cleanPath(m.groupValues[1]) ?: return@mapNotNull null
            PlanFile(p, m.groupValues[2].trim())
        }.distinctBy { it.path }.take(8).toList()
        return Plan(summary, files)
    }
}
