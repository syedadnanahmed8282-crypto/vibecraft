package com.vibecraft.app.gen

import kotlin.math.roundToInt

enum class NoticeKind { DONE, REPLY, ASK, PROBLEM }

data class Notice(val kind: NoticeKind, val title: String, val text: String, val big: String)

/** One progress phase as the notification needs it (state: 0 waiting, 1 active, 2 done, 3 failed). */
data class PhaseView(val label: String, val state: Int, val phase: String, val sub: String, val progress: Float)

data class ProgressLine(val title: String, val text: String, val percent: Int?)

object NoticeText {
    fun okLabel(bn: Boolean) = if (bn) "ঠিক আছে, শুরু করো" else "OK, go ahead"
    fun okText(bn: Boolean) = if (bn) "ঠিক আছে, তোমার প্রস্তাব অনুযায়ী এগিয়ে যাও" else "OK, go ahead with your suggestions"

    /**
     * detail is the AI's own words (a summary, a question, an error); extra is a short fact line such as
     * "3 changes saved · v5". The title always carries the project name, so several chats stay apart.
     */
    fun build(kind: NoticeKind, project: String, bn: Boolean, detail: String, extra: String = ""): Notice {
        val head = when (kind) {
            NoticeKind.DONE -> if (bn) "কাজ শেষ" else "Done"
            NoticeKind.REPLY -> if (bn) "উত্তর এসেছে" else "New reply"
            NoticeKind.ASK -> if (bn) "আপনার উত্তর দরকার" else "Your answer is needed"
            NoticeKind.PROBLEM -> if (bn) "মনোযোগ দরকার" else "Needs attention"
        }
        val long = Quoting.preview(detail, 450)
        val fact = extra.trim()
        val text = if (fact.isNotEmpty()) fact else Quoting.preview(detail, 140)
        val big = if (fact.isNotEmpty() && long.isNotEmpty()) fact + "\n" + long else long.ifBlank { text }
        return Notice(kind, "$head · ${project.ifBlank { "Vibecraft" }}", text.ifBlank { head }, big.ifBlank { head })
    }

    fun phaseName(phase: String, bn: Boolean): String = if (bn) {
        when (phase) {
            "read" -> "পড়ছি"; "reason" -> "ভাবছি"; "plan" -> "পরিকল্পনা করছি"
            "work" -> "কাজ করছি"; "check" -> "যাচাই করছি"; "save" -> "সেভ করছি"; else -> "কাজ চলছে"
        }
    } else {
        when (phase) {
            "read" -> "Reading"; "reason" -> "Reasoning"; "plan" -> "Planning"
            "work" -> "Working"; "check" -> "Checking"; "save" -> "Saving"; else -> "Working"
        }
    }

    /** The ongoing notification while the app works: the active phase, what it is doing, and a bar for Working. */
    fun progress(phases: List<PhaseView>, project: String, bn: Boolean): ProgressLine {
        val title = project.ifBlank { "Vibecraft" }
        val a = phases.firstOrNull { it.state == 1 } ?: return ProgressLine(title, if (bn) "কাজ চলছে" else "Working", null)
        val sub = Quoting.preview(a.sub, 70)
        val text = phaseName(a.phase, bn) + if (sub.isBlank()) "" else " · $sub"
        val pct = if (a.phase == "work" && a.progress >= 0f) (a.progress * 100).roundToInt().coerceIn(0, 100) else null
        return ProgressLine(title, text, pct)
    }
}
