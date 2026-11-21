package com.vibecraft.app.gen

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

enum class ErrKind(val priority: Int) { MANIFEST(0), CONFIG(1), DEPENDENCY(2), RESOURCE(3), KOTLIN(4), GRADLE(5), OTHER(6) }

data class BuildError(
    val kind: ErrKind, val file: String?, val line: Int?, val message: String, val context: List<String> = emptyList(),
)

enum class BuildStatus { SUCCESS, FAILED, EXTRACTION_FAILED, INCONCLUSIVE }

data class BuildReport(
    val status: BuildStatus, val errors: List<BuildError> = emptyList(), val note: String = "", val runUrl: String = "",
)

object BuildLog {
    private val stamp = Regex("^\\d{4}-\\d{2}-\\d{2}T[\\d:.]+Z\\s?")
    private val ansi = Regex("\u001B\\[[0-9;]*[A-Za-z]")
    private val ktNew = Regex("^e: (?:file://)?(\\S+?\\.kt):(\\d+):(\\d+)\\s+(.*)$")
    private val ktOld = Regex("^e: (?:file://)?(\\S+?\\.kt): ?\\((\\d+), (\\d+)\\): (.*)$")
    private val ktBare = Regex("^e: (.+)$")
    private val xmlErr = Regex("(\\S+\\.xml):(\\d+)(?::\\d+)?:\\s*(?:AAPT:\\s*)?error:\\s*(.*)")
    private val aapt = Regex("AAPT: error: (.*)")
    private val resMissing = Regex("(?i)error: (resource [\\w/.]+.*not found.*)")
    private val manifestRe = Regex("(?i)(Manifest merger failed.*)")
    private val depRe = Regex("(?i)^(Could not resolve .*|Could not find .*|Failed to resolve: .*|Could not GET .*)")
    private val cfgRe = Regex("(?i)(Plugin .* was not found.*|SDK location not found.*|License for package .* not accepted.*|Unsupported class file major version.*)")
    private val execFailed = Regex("^Execution failed for task '(.+)'\\.?")

    fun lines(raw: String): List<String> = raw.lines().map { ansi.replace(stamp.replace(it.trimEnd(), ""), "") }

    /** Keeps the part of a path that starts at app/src, so it matches the project's own file paths. */
    fun normalizePath(p: String): String {
        val i = p.indexOf("app/src/")
        return if (i >= 0) p.substring(i) else p
    }

    fun parse(raw: String): List<BuildError> {
        val ls = lines(raw)
        val out = mutableListOf<BuildError>()
        var i = 0
        while (i < ls.size) {
            val l = ls[i]
            val kn = ktNew.find(l) ?: ktOld.find(l)
            when {
                kn != null -> {
                    val ctx = mutableListOf<String>()
                    var j = i + 1
                    while (j < ls.size && ctx.size < 2 && ls[j].isNotBlank() && !ls[j].startsWith("e: ") &&
                        !ls[j].startsWith("w: ") && !ls[j].startsWith("> ") && !ls[j].startsWith("FAILURE")
                    ) { ctx.add(ls[j].trim()); j++ }
                    out.add(BuildError(ErrKind.KOTLIN, normalizePath(kn.groupValues[1]), kn.groupValues[2].toIntOrNull(), kn.groupValues[4].trim(), ctx))
                }
                xmlErr.containsMatchIn(l) -> {
                    val m = xmlErr.find(l)!!
                    val f = normalizePath(m.groupValues[1])
                    out.add(BuildError(if (f.endsWith("AndroidManifest.xml")) ErrKind.MANIFEST else ErrKind.RESOURCE, f, m.groupValues[2].toIntOrNull(), m.groupValues[3].trim()))
                }
                aapt.containsMatchIn(l) -> out.add(BuildError(ErrKind.RESOURCE, null, null, aapt.find(l)!!.groupValues[1].trim()))
                resMissing.containsMatchIn(l) -> out.add(BuildError(ErrKind.RESOURCE, null, null, resMissing.find(l)!!.groupValues[1].trim()))
                manifestRe.containsMatchIn(l) -> out.add(BuildError(ErrKind.MANIFEST, "app/src/main/AndroidManifest.xml", null, manifestRe.find(l)!!.groupValues[1].trim()))
                depRe.containsMatchIn(l.trim()) -> out.add(BuildError(ErrKind.DEPENDENCY, null, null, l.trim()))
                cfgRe.containsMatchIn(l) -> out.add(BuildError(ErrKind.CONFIG, null, null, cfgRe.find(l)!!.groupValues[1].trim()))
                l.trim() == "* What went wrong:" -> {
                    var j = i + 1
                    while (j < ls.size && ls[j].isBlank()) j++
                    val first = ls.getOrNull(j)?.trim().orEmpty()
                    val ex = execFailed.find(first)
                    when {
                        ex != null -> out.add(BuildError(ErrKind.GRADLE, null, null, "Task ${ex.groupValues[1]} failed"))
                        first.isNotEmpty() && !depRe.containsMatchIn(first) && !cfgRe.containsMatchIn(first) -> out.add(BuildError(ErrKind.GRADLE, null, null, first))
                    }
                }
                ktBare.matches(l) -> out.add(BuildError(ErrKind.KOTLIN, null, null, ktBare.find(l)!!.groupValues[1].trim()))
            }
            i++
        }
        return prioritize(out)
    }

    /** Removes duplicates, drops the generic Gradle wrapper lines when something specific exists, and sorts by cause. */
    fun prioritize(all: List<BuildError>): List<BuildError> {
        val seen = HashSet<String>()
        val unique = all.filter { seen.add("${it.kind}|${it.file}|${it.line}|${it.message.take(120)}") }
        val specific = unique.any { it.kind != ErrKind.GRADLE }
        return unique.filter { !(specific && it.kind == ErrKind.GRADLE) }
            .sortedWith(compareBy({ it.kind.priority }, { it.file ?: "" }, { it.line ?: 0 }))
    }

    fun render(errors: List<BuildError>, maxErrors: Int = 12, maxChars: Int = 4000): String {
        val sb = StringBuilder()
        for (e in errors.take(maxErrors)) {
            val where = if (e.file != null) "${e.file}${e.line?.let { ":$it" } ?: ""}" else "-"
            val block = "[${e.kind}] $where: ${e.message}" + e.context.joinToString("") { "\n    $it" } + "\n"
            if (sb.length + block.length > maxChars) break
            sb.append(block)
        }
        return sb.toString().trimEnd()
    }

    fun tail(raw: String, n: Int = 25): String = lines(raw).filter { it.isNotBlank() }.takeLast(n).joinToString("\n") { it.take(300) }
}

/** GitHub sends logs for a whole run as a ZIP archive: unpack it instead of reading the bytes as text. */
object LogZip {
    /** Null when the bytes are not a readable, non-empty ZIP. */
    fun extract(bytes: ByteArray, maxChars: Int = 3_000_000): String? {
        return try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zin ->
                val sb = StringBuilder()
                var any = false
                var e = zin.nextEntry
                while (e != null && sb.length < maxChars) {
                    if (!e.isDirectory) {
                        any = true
                        sb.append("##### ").append(e.name).append('\n').append(zin.readBytes().toString(Charsets.UTF_8)).append('\n')
                    }
                    e = zin.nextEntry
                }
                if (any) sb.toString() else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
