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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** The provider says "limit reached" or is overloaded: safe to try the next profile. */
class Fallback(message: String) : Exception(message)

/** Anything else (bad key, bad request, no network): show it, do not switch. */
class AiError(message: String) : Exception(message)

object AiClient {
    /** Hidden project rules, sent with every request so every model follows the same ones. */
    const val SYSTEM = "You are Vibecraft, an assistant that designs and writes Android apps with Kotlin and Jetpack Compose. " +
        "Use Material 3 and a clean white and forest green look. Keep answers short and practical for beginners. " +
        "When you create or change files, put each file in its own fenced code block whose FIRST line is a comment with the file path, " +
        "for example // app/src/main/java/com/vibecraft/generated/HomeScreen.kt . Always send the complete file, never a fragment. " +
        "Rules for the project: the code package is com.vibecraft.generated; the entry point is a composable function " +
        "fun AppRoot() in app/src/main/java/com/vibecraft/generated/AppRoot.kt; only these libraries are available: " +
        "Compose BOM with ui, foundation, material3, material-icons-core, activity-compose, lifecycle viewmodel and runtime compose, " +
        "and kotlinx coroutines. Do not use other libraries and do not edit Gradle files. " +
        "For every new app or screen change also send preview.html: one self-contained HTML file, with inline CSS and JS and no external " +
        "resources, that shows the main screen like the app looks, using the same colors. Start it with <!-- preview.html -->. " +
        "Never ask the user for passwords or API keys."

    private val http by lazy {
        HttpClient(OkHttp) {
            install(HttpTimeout) {
                requestTimeoutMillis = 120_000
                connectTimeoutMillis = 20_000
                socketTimeoutMillis = 120_000
            }
        }
    }
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun chat(p: Profile, key: String, history: List<Pair<String, String>>, system: String = SYSTEM): String {
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
            ) { root -> root["content"]!!.jsonArray.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.content ?: "" } }

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
                root["candidates"]!!.jsonArray[0].jsonObject["content"]!!.jsonObject["parts"]!!.jsonArray
                    .joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.content ?: "" }
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
            ) { root -> root["choices"]!!.jsonArray[0].jsonObject["message"]!!.jsonObject["content"]!!.jsonPrimitive.content }
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

    private suspend fun call(url: String, headers: Map<String, String>, body: JsonObject, parse: (JsonObject) -> String): String {
        val r = try {
            http.post(url) {
                headers.forEach { (k, v) -> header(k, v) }
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }
        } catch (e: Exception) {
            throw AiError("Network problem: ${e.message ?: "could not reach the provider"}")
        }
        val text = r.bodyAsText()
        if (!r.status.isSuccess()) {
            val msg = (errorMessage(text) ?: r.status.description).take(160)
            val low = (msg + text.take(400)).lowercase()
            val limited = r.status.value in listOf(402, 408, 429, 500, 502, 503, 504, 529) ||
                "quota" in low || "rate limit" in low || "overloaded" in low || "insufficient" in low
            if (limited) throw Fallback(msg) else throw AiError(msg)
        }
        return try {
            parse(json.parseToJsonElement(text).jsonObject).trim()
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
