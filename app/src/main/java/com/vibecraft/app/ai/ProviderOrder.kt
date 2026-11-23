package com.vibecraft.app.ai

data class Candidate(val id: String, val name: String, val hasKey: Boolean, val dailyLimit: Int, val used: Int) {
    /** Has a key and has not used up its daily requests (a limit of 0 means no limit). */
    val ready: Boolean get() = hasKey && (dailyLimit <= 0 || used < dailyLimit)
}

sealed class Selection {
    data class Order(val ids: List<String>, val notice: String? = null) : Selection()
    data class Blocked(val message: String) : Selection()
}

/** Which profiles to try, in which order, before the first request is sent. */
object ProviderOrder {
    fun choose(all: List<Candidate>, activeId: String?, autoSwitch: Boolean): Selection {
        val usable = all.filter { it.hasKey }
        if (usable.isEmpty()) return Selection.Blocked("Add an AI profile with an API key in Settings to get replies.")
        val ready = usable.filter { it.ready }
        if (ready.isEmpty()) {
            return Selection.Blocked("Every AI profile has reached its daily request limit. Raise the limit in Settings or try tomorrow.")
        }
        val active = usable.firstOrNull { it.id == activeId }
        var notice: String? = null
        if (active != null && !active.ready) {
            if (!autoSwitch) return Selection.Blocked("${active.name} reached its daily limit (${active.dailyLimit}). Auto-switch is off.")
            notice = "${active.name} reached its daily limit. Switched to ${ready.first().name} automatically."
        }
        val first = ready.firstOrNull { it.id == activeId }
        return Selection.Order((listOfNotNull(first) + ready.filter { it !== first }).map { it.id }, notice)
    }
}
