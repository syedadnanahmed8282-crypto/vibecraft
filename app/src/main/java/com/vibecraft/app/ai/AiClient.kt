package com.vibecraft.app.ai

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** The provider says "limit reached" or is overloaded: safe to try the next profile. */
class Fallback(message: String) : Exception(message)

/** The model did not answer in time (free models can be slow). Worth one retry, then the next profile. */
class TimedOut(message: String) : Exception(message)

/** Anything else (bad key, bad request, no network): show it, do not switch. */
class AiError(message: String) : Exception(message)

/** text, plus cut = the provider stopped because of its length limit. */
data class Reply(val text: String, val cut: Boolean = false)

object AiClient {
    private const val RULES = "You are Vibecraft, an assistant that designs and writes Android apps with Kotlin and Jetpack Compose. " +
        "Use Material 3 and a clean white and forest green look. Reply in the language the user writes. " +
        "Project rules: the code package is com.vibecraft.generated; the entry point is a composable function fun AppRoot() in " +
        "app/src/main/java/com/vibecraft/generated/AppRoot.kt; all Kotlin files live in app/src/main/java/com/vibecraft/generated/; " +
        "only these libraries exist: Compose BOM with ui, foundation, material3, material-icons-core, activity-compose, " +
        "lifecycle viewmodel and runtime compose, and kotlinx coroutines. Do not use other libraries and do not edit Gradle files. " +
        "Never ask the user for passwords or API keys. "

    /** Step 1: decide between a plain answer, a read, or a short plan of files. */
    const val SYS_PLAN = RULES +
        "Decide what the user wants. If they want an app built or changed, reply in EXACTLY this format and nothing else: " +
        "a first line 'SUMMARY: ' with one or two friendly sentences saying what you are about to do (future tense, for example 'I will add...'); " +
        "for a new app a line 'APP_NAME: ' with a short, catchy, original name of one or two words (use the user's own name if they gave one); " +
        "then one line per file: 'FILE: <path> | <what it does in under 12 words>'; " +
        "and one line 'DELETE: <path>' for every file that is no longer needed. " +
        "List only files that must be created or changed, at most 8, each short (under 200 lines). " +
        "Order them so files used by other files come first, then AppRoot.kt. " +
        "Add preview.html last when the look of the app changes or the project has no preview. " +
        "Add APP_NAME later only if the user asks to rename the app. " +
        "A new app always needs its own icon: include app/src/main/res/drawable/ic_launcher_foreground.xml and " +
        "app/src/main/res/values/ic_launcher_colors.xml, and change them when the user asks for a new icon or logo. " +
        "If the user asks to read, explain, review or find something in the code, do NOT write files: answer directly when the file " +
        "contents are shown below, otherwise reply with only lines 'READ: <path>' (at most 3). " +
        "If the user only asks a question or chats, answer in a few sentences and write no FILE lines."

    /** Step 2: one request per file. */
    const val SYS_FILE = RULES +
        "You write exactly one file now. Reply with ONLY one fenced code block and no other text. " +
        "For a new file send the complete file. For an existing file you may instead send edit blocks inside the code block, " +
        "each in this form:\n<<<<<<< SEARCH\n(exact existing lines)\n=======\n(replacement lines)\n>>>>>>> REPLACE\n" +
        "Send only the edits that are needed; to remove code send an edit with an empty replacement. " +
        "preview.html must be one self-contained HTML file with inline CSS and JS and no external resources. It is shown on a " +
        "260 by 500 pixel screen: fill that whole screen and do not draw a phone frame or a fake status bar. " +
        "ic_launcher_foreground.xml must be an Android vector drawable: <vector> with android:width and android:height of 108dp, " +
        "android:viewportWidth and android:viewportHeight of 108, containing only <path> and <group> elements with android:fillColor " +
        "as a hex color and android:pathData; keep all artwork inside the central square from 21 to 87; use simple bold shapes that " +
        "suit the app, at most three colors, no gradients and no text. ic_launcher_colors.xml must be exactly: " +
        "<resources><color name=\"ic_launcher_background\">#RRGGBB</color></resources> with a color that suits the icon."

    /** Reading questions: answer from the files, never rewrite them. */
    const val SYS_ANSWER = RULES +
        "Answer the user's question about the project code below in a few short sentences, in the user's language. " +
        "Do not write or rewrite files and do not paste long code; quote only the few lines that matter."

    /** Closing note after a change. */
    const val SYS_SUMMARY = "You write the closing note of a coding task. Reply in exactly two lines. " +
        "First line: 'COMMIT: ' then a git commit message in English, imperative mood, under 60 characters. " +
        "Second line: 'SUMMARY: ' then two or three short friendly sentences in the language the user wrote, saying what you did, " +
        "which problem there was (if any) and that it was fixed. No code and no markdown."

    const val SYSTEM = SYS_PLAN

    private val http by lazy {
        HttpClient(OkHttp) {
            install(HttpTimeout) {
                requestTimeoutMillis = 240_000
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = 240_000
            }
        }
    }
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun chat(p: Profile, key: String, history: List<Pair<String, String>>, system: String = SYSTEM): String =
        chatFull(p, key, history, system).text

    suspend fun chatFull(p: Profile, key: String, history: List<Pair<String, String>>, system: String = SYSTEM): Reply {
        if (p.baseUrl.isBlank()) throw AiError("The base URL is empty.")
        val base = p.baseUrl.trim().trimEnd('/')
        val h = merge(history)
        return when (p.type) {
            "anthropic" -> call(
                "$base/messages",
                mapOf("x-api-key" to key, "anthropic-version" to "2023-06-01"),
                buildJsonObject {
                    put("model", p.model)
                    put("max_tokens", 4096)
                    put("system", system)
                    putJsonArray("messages") { h.forEach { add(buildJsonObject { put("role", it.first); put("content", it.second) }) } }
                },
            ) { root ->
                Reply(
                    root["content"]!!.jsonArray.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.content ?: "" },
                    root["stop_reason"]?.jsonPrimitive?.contentOrNull == "max_tokens",
                )
            }

            "gemini" -> call(
                "$base/models/${p.model}:generateContent",
                mapOf("x-goog-api-key" to key),
                buildJsonObject {
                    putJsonObject("systemInstruction") { putJsonArray("parts") { add(buildJsonObject { put("text", system) }) } }
                    putJsonArray("contents") {
                        h.forEach {
                            add(buildJsonObject {
                                put("role", if (it.first == "assistant") "model" else "user")
                                putJsonArray("parts") { add(buildJsonObject { put("text", it.second) }) }
                            })
                        }
                    }
                },
            ) { root ->
                val cand = root["candidates"]!!.jsonArray[0].jsonObject
                Reply(
                    cand["content"]!!.jsonObject["parts"]!!.jsonArray.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.content ?: "" },
                    cand["finishReason"]?.jsonPrimitive?.contentOrNull == "MAX_TOKENS",
                )
            }

            else -> call(
                "$base/chat/completions",
                mapOf("Authorization" to "Bearer $key"),
                buildJsonObject {
                    put("model", p.model)
                    putJsonArray("messages") {
                        add(buildJsonObject { put("role", "system"); put("content", system) })
                        h.forEach { add(buildJsonObject { put("role", it.first); put("content", it.second) }) }
                    }
                },
            ) { root ->
                val ch = root["choices"]!!.jsonArray[0].jsonObject
                Reply(
                    ch["message"]!!.jsonObject["content"]!!.jsonPrimitive.content,
                    ch["finish_reason"]?.jsonPrimitive?.contentOrNull == "length",
                )
            }
        }
    }

    /** Providers want alternating turns that start with the user, so merge neighbours of the same role. */
    private fun merge(h: List<Pair<String, String>>): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        h.forEach {
            if (out.isNotEmpty() && out.last().first == it.first) out[out.lastIndex] = it.first to (out.last().second + "\n\n" + it.second)
            else out.add(it)
        }
        while (out.firstOrNull()?.first == "assistant") out.removeAt(0)
        return out
    }

    private suspend fun call(url: String, headers: Map<String, String>, body: JsonObject, parse: (JsonObject) -> Reply): Reply {
        val (r, text) = try {
            val res = http.post(url) {
                headers.forEach { (k, v) -> header(k, v) }
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }
            res to res.bodyAsText()
        } catch (e: Exception) {
            val slow = e::class.simpleName?.contains("Timeout", true) == true ||
                e.message?.contains("timeout", true) == true || e.message?.contains("timed out", true) == true
            if (slow) throw TimedOut("The model took too long to answer.")
            if (e is kotlinx.coroutines.CancellationException) throw e
            throw AiError("Network problem: ${e.message ?: "could not reach the provider"}")
        }
        if (!r.status.isSuccess()) {
            val msg = (errorMessage(text) ?: r.status.description).take(160)
            val low = (msg + text.take(400)).lowercase()
            val limited = r.status.value in listOf(402, 408, 429, 500, 502, 503, 504, 529) ||
                "quota" in low || "rate limit" in low || "overloaded" in low || "insufficient" in low
            if (limited) throw Fallback(msg) else throw AiError(msg)
        }
        return try {
            parse(json.parseToJsonElement(text).jsonObject).let { it.copy(text = it.text.trim()) }
        } catch (e: Exception) {
            throw AiError("Unexpected response from the provider.")
        }
    }

    private fun errorMessage(text: String): String? = runCatching {
        val e = json.parseToJsonElement(text).jsonObject["error"]
        when (e) {
            is JsonObject -> e["message"]?.jsonPrimitive?.content
            is JsonPrimitive -> e.content
            else -> null
        }
    }.getOrNull()
}
