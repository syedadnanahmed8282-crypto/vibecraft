package com.vibecraft.app.gen

data class PlanFile(val path: String, val desc: String)

data class Plan(
    val summary: String,
    val files: List<PlanFile>,
    val appName: String = "",
    val reads: List<ReadReq> = emptyList(),
    val deletes: List<String> = emptyList(),
)

/** Reads the AI's short plan: SUMMARY, APP_NAME, FILE, DELETE and READ lines. */
object PlanParser {
    private val summaryRe = Regex("(?im)^\\s*SUMMARY:\\s*(.+)$")
    private val nameRe = Regex("(?im)^\\s*APP_NAME:\\s*(.+)$")
    private val fileRe = Regex("(?im)^\\s*[-*]?\\s*FILE:\\s*([^|\\n]+?)\\s*(?:\\|\\s*(.*))?$")
    private val deleteRe = Regex("(?im)^\\s*[-*]?\\s*DELETE:\\s*(\\S+)")
    private val readRe = Regex("(?im)^\\s*[-*]?\\s*READ:\\s*(\\S+?)(?::(\\d+)-(\\d+)|\\s+(\\d+)-(\\d+))?\\s*$")

    fun parse(text: String): Plan {
        val summary = summaryRe.find(text)?.groupValues?.get(1)?.trim().orEmpty()
        val appName = nameRe.find(text)?.groupValues?.get(1)?.trim()?.trim('"', '\'', '`', '*')?.take(30).orEmpty()
        val files = fileRe.findAll(text).mapNotNull { m ->
            val p = CodeBlocks.cleanPath(m.groupValues[1])?.takeIf { Protect.canWrite(it) } ?: return@mapNotNull null
            PlanFile(p, m.groupValues[2].trim())
        }.distinctBy { it.path }.take(8).toList()
        val deletes = deleteRe.findAll(text).mapNotNull { CodeBlocks.cleanPath(it.groupValues[1])?.takeIf { p -> Protect.canDelete(p) } }
            .distinct().take(8).toList()
        val reads = readRe.findAll(text).mapNotNull { m ->
            val p = CodeBlocks.cleanPath(m.groupValues[1]) ?: return@mapNotNull null
            val a = m.groupValues[2].ifEmpty { m.groupValues[4] }.toIntOrNull()
            val b = m.groupValues[3].ifEmpty { m.groupValues[5] }.toIntOrNull()
            ReadReq(p, a, b)
        }.distinctBy { Triple(it.path, it.from, it.to) }.take(3).toList()
        return Plan(summary, files, appName, reads, deletes)
    }
}
