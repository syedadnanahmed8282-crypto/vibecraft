package com.vibecraft.app.gen

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** The format of a checkpoint and how a version is rebuilt from the checkpoints before it. */
object History {
    const val FULL = "__full"
    const val DEL = "__del:"

    /** changed: path -> new content; deleted: paths removed; full = this checkpoint replaces everything. */
    fun changes(changed: Map<String, String>, deleted: List<String> = emptyList(), full: Boolean = false): String =
        buildJsonObject {
            if (full) put(FULL, "1")
            changed.forEach { (k, v) -> put(k, v) }
            deleted.forEach { put("$DEL$it", "1") }
        }.toString()

    /** Project files as they were after checkpoint [upTo]. checkpoints: (version, changes JSON). */
    fun replay(checkpoints: List<Pair<Int, String>>, upTo: Int): Map<String, String> {
        val state = linkedMapOf<String, String>()
        checkpoints.filter { it.first <= upTo }.sortedBy { it.first }.forEach { (_, json) ->
            val o = Json.parseToJsonElement(json).jsonObject
            if (o.containsKey(FULL)) state.clear()
            o.forEach { (k, v) ->
                when {
                    k == FULL -> {}
                    k.startsWith(DEL) -> state.remove(k.removePrefix(DEL))
                    else -> state[k] = v.jsonPrimitive.content
                }
            }
        }
        return state
    }
}
