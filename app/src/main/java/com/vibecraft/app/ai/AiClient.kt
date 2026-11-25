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
        "The user often writes English words in Bengali letters. Read them as: এপিকে = APK, কটলিন = Kotlin, প্রিভিউ = preview, " +
        "বিল্ড = build, পাবলিশ = publish, কমিট = commit, গিটহাব = GitHub, ইরর = error, ফাইল = file, ডিজাইন = design, থিম = theme, " +
        "প্ল্যান = plan. Never read them as other words. Always answer in the language the user wrote in. " +
        "Use the project memory, the unfinished tasks and the app events below: stay consistent with earlier decisions, continue " +
        "unfinished work, and say plainly what was done or could not be done. The app events tell what really happened; never claim " +
        "anything they do not confirm. " +
        "Project rules: the code package is com.vibecraft.generated; the entry point is a composable function fun AppRoot() in " +
        "app/src/main/java/com/vibecraft/generated/AppRoot.kt; all Kotlin files live in app/src/main/java/com/vibecraft/generated/; " +
        "only these libraries exist: Compose BOM with ui, foundation, material3, material-icons-core, activity-compose, " +
        "lifecycle viewmodel and runtime compose, and kotlinx coroutines. Do not use other libraries and do not edit Gradle files. " +
        "Never ask the user for passwords or API keys. " +
        "About the app you work in (Vibecraft): the user builds an Android app from a phone. There is no Android Studio, no Gradle access " +
        "and no @Preview. Your files are saved automatically in the project. The Preview tab shows preview.html, a separate HTML sketch " +
        "of the main screen that you must keep in sync with the Kotlin screens; it is not generated from Kotlin. The user publishes the " +
        "project to GitHub, which builds the APK. Your only tools are the plan lines FILE (create or change a file), DELETE (remove a file), " +
        "READ (look at a file) and APP_NAME. You cannot run or compile code. Never tell the user to use Android Studio or to do manual " +
        "steps: do the work yourself with FILE lines. "

    /** Step 1: decide between a plain answer, a read, or a short plan of files. */
    const val SYS_PLAN = RULES +
        "Start your reply with exactly one line: 'INTENT: ACT' when the user wants something built, changed, fixed, deleted or continued now, " +
        "or 'INTENT: ANSWER' when they ask a question, ask you to check, review, explain or read something, ask what happened, or just chat " +
        "(a check or review never changes files unless they also ask for a change; দেখো, চেক, যাচাই, কেন, কখন, কি হইছে are questions). " +
        "When the user's message starts with 'The user is replying to this earlier message', that quoted message is exactly what words like this, that, it, ok, do it, দাও, ঠিক করো refer to: " +
        "read the quote first, then answer or act on it directly (a quoted problem means find the cause and fix it, a quoted proposal the user accepts means do it now, " +
        "a quoted answer they question means explain or correct it). Never ask which message they mean. " +
        "With INTENT: ANSWER write the answer right after that line, in a few clear sentences, using the memory, the tasks, the events and the files, and no FILE lines. " +
        "With INTENT: ACT, after that line reply in EXACTLY this format and nothing else: " +
        "a first line 'SUMMARY: ' with one or two friendly sentences saying what you are about to do (future tense, for example 'I will add...'); " +
        "for a new app a line 'APP_NAME: ' with a short, catchy, original name of one or two words (use the user's own name if they gave one); " +
        "then one line per file: 'FILE: <path> | <what it does in under 12 words>'; " +
        "and one line 'DELETE: <path>' for every file that is no longer needed. " +
        "List only files that must be created or changed, at most 8, each short (under 200 lines). " +
        "Order them so files used by other files come first, then AppRoot.kt. " +
        "Add the preview last when the look of the app changes or the project has no preview: preview.html, or for a bigger app " +
        "preview/index.html with preview/screens/<name>.html, preview/styles/<name>.css and preview/scripts/<name>.js. " +
        "A new app also needs design.json (the design tokens) listed first. For new apps and redesigns, Kotlin code must take every " +
        "color, text size, spacing and corner from the generated object DesignTokens (DesignTokens.Light.primary and DesignTokens.Dark.primary " +
        "with the same color names as design.json, plus titleSize, bodySize, captionSize, titleWeight, spaceXs, spaceS, spaceM, spaceL, " +
        "radiusS, radiusM, radiusL, elevationCard, elevationSheet), and Theme.kt must build its color schemes from it. Never write DesignTokens.kt yourself. " +
        "Add APP_NAME later only if the user asks to rename the app. " +
        "A new app always needs its own icon: include app/src/main/res/drawable/ic_launcher_foreground.xml and " +
        "app/src/main/res/values/ic_launcher_colors.xml, and change them when the user asks for a new icon or logo. " +
        "When the user reports a problem or something that does not work (the preview, the look, a missing feature, an error), " +
        "find the cause in the project files and FIX it with FILE lines in the same reply, saying the cause and the fix in SUMMARY; " +
        "do not only explain and do not give general advice. If you need a file you cannot see, reply with READ lines first. " +
        "A short confirmation from the user (such as ok, do it, দাও, মুছো) means: do now what you proposed, with FILE or DELETE lines. " +
        "Never say that you are changing or deleting something unless the same reply has the matching FILE or DELETE lines. " +
        "A build error that names a file missing from the manifest comes from a leftover file in the GitHub repository; " +
        "Vibecraft removes such files on the next publish, so do not try to delete them. " +
        "The project manifest below lists every file with its purpose, the files it uses, its size and its version. Only some files are " +
        "shown in full; files marked INCOMPLETE are shown in part. Never rewrite an INCOMPLETE file as a whole. " +
        "If the user asks to read, explain, review or find something in the code, do NOT write files: answer directly when the file " +
        "contents are shown below, otherwise reply with only lines 'READ: <path>' or 'READ: <path>:<first line>-<last line>' (at most 3). " +
        "If the user only asks a question or chats, answer in a few sentences and write no FILE lines."

    /** Step 2: one request per file. */
    const val SYS_FILE = RULES +
        "You write exactly one file now. Reply with ONLY one fenced code block and no other text. " +
        "Keep every Kotlin file under 300 lines: put separate parts of a screen into separate files. " +
        "For a new file send the complete file. For an existing file you may instead send edit blocks inside the code block, " +
        "each in this form:\n<<<<<<< SEARCH\n(exact existing lines)\n=======\n(replacement lines)\n>>>>>>> REPLACE\n" +
        "Send only the edits that are needed; each SEARCH part must match exactly one place in the file; to remove code send an edit with an empty replacement. " +
        "If the file is marked INCOMPLETE you must send edit blocks only. " +
        "For a long file you may instead replace one whole definition by name with a target block: a line '<<<<<<< REPLACE fun Name' " +
        "(or class, object, interface for Kotlin; css <selector> for CSS; id <elementId> for HTML; function <name> for JavaScript), " +
        "then the complete new definition, then a line '>>>>>>> END'. The name must match exactly one place, and a function with an " +
        "expression body (fun x() = ...) needs a SEARCH block instead. " +
        "design.json holds the design tokens as JSON: {\"name\":\"App\",\"colors\":{\"light\":{...},\"dark\":{...}}," +
        "\"typography\":{\"title\":22,\"body\":15,\"caption\":12,\"titleWeight\":700},\"spacing\":{\"xs\":4,\"s\":8,\"m\":16,\"l\":24}," +
        "\"radius\":{\"s\":8,\"m\":16,\"l\":24},\"elevation\":{\"card\":2,\"sheet\":8}} where each color object has primary, onPrimary, " +
        "background, surface, onSurface, muted, accent and error as #RRGGBB, with a dark palette that suits the light one. " +
        "Preview files are preview.html (one compact page, under 150 lines) or, for a bigger app, preview/index.html with small files " +
        "preview/screens/<name>.html, preview/styles/<name>.css and preview/scripts/<name>.js linked by relative paths such as screens/detail.html. " +
        "Start every HTML file with <!-- vc-preview v2 -->. They are shown on a simulated phone of any size, so do not draw a phone frame, " +
        "status bar or navigation bar and fill the whole screen. Keep content clear of the system bars with padding-top: var(--safe-top) and " +
        "padding-bottom: var(--safe-bottom). Use only these CSS variables for colors, text, spacing and corners: --color-primary, --color-on-primary, " +
        "--color-background, --color-surface, --color-on-surface, --color-muted, --color-accent, --color-error, --font-title, --font-body, --font-caption, " +
        "--font-title-weight, --space-xs, --space-s, --space-m, --space-l, --radius-s, --radius-m, --radius-l, --elev-card, --elev-sheet. " +
        "No external resources. " +
        "ic_launcher_foreground.xml must be an Android vector drawable: <vector> with android:width and android:height of 108dp, " +
        "android:viewportWidth and android:viewportHeight of 108, containing only <path> and <group> elements with android:fillColor " +
        "as a hex color and android:pathData; keep all artwork inside the central square from 21 to 87; use simple bold shapes that " +
        "suit the app, at most three colors, no gradients and no text. ic_launcher_colors.xml must be exactly: " +
        "<resources><color name=\"ic_launcher_background\">#RRGGBB</color></resources> with a color that suits the icon."

    /** A file is too long to rewrite: only the grouping is chosen here, the app moves the code. */
    const val SYS_SPLIT = "You split a long Kotlin file into smaller files. You only decide the grouping; the app moves the code. " +
        "Reply with ONLY lines of the form 'GROUP: <NewFileName>.kt | Name1, Name2' (at most 6 groups, each declaration in at most one group). " +
        "Group declarations by purpose, for example the display, the keypad, the history panel or helpers. " +
        "Leave the entry function AppRoot and any shared theme or state declarations out, so they stay in the original file."

    /** Reading questions: answer from the files, never rewrite them. */
    const val SYS_ANSWER = RULES +
        "Answer the user's question about the project code below in a few short sentences, in the user's language. " +
        "Do not write or rewrite files and do not paste long code; quote only the few lines that matter."

    /** Closing note after a change, plus the updated project memory. */
    const val SYS_SUMMARY = "You write the closing note of a coding task. Reply in exactly this form. " +
        "COMMIT: a git commit message in English, imperative mood, under 60 characters. " +
        "SUMMARY: two or three short friendly sentences in the language the user wrote, saying what you did, which problem there was (if any) " +
        "and that it was fixed, or what is still open. No code and no markdown. " +
        "MEMORY: the updated project memory, at most 1200 characters, as short lines that start with GOAL:, DECISIONS:, DESIGN:, USER: or OPEN:. " +
        "The USER line records the user's language, how they like to work, and the words they use for things (for example এপিকে = APK). " +
        "Keep earlier facts that are still true, add what was said or done in this task, remove what is outdated. Facts only, no guesses."

    /** A memory refresh without any code change. */
    const val SYS_MEMORY = "You keep the memory of a coding project for an assistant. You get the current memory and the recent conversation. " +
        "Reply with ONLY the updated memory, at most 1200 characters, as short lines that start with GOAL:, DECISIONS:, DESIGN:, USER: or OPEN:. " +
        "The USER line records the user's language, how they like to work, and the words they use for things. " +
        "Keep earlier facts that are still true, add new facts the user stated or the assistant did, remove what is outdated. Facts only, no guesses."

    /** The plan a user approves before a new app is built. */
    const val SYS_BRIEF = "You plan a new Android app with Kotlin and Jetpack Compose together with the user, before any code is written. " +
        "Reply in the language the user wrote, with these lines: GOAL: one sentence. SCREENS: the screens, separated by commas. " +
        "FEATURES: the main features, separated by commas. DESIGN: the look in one sentence (style, colors, mood). " +
        "Then 3 to 5 lines 'PHASE 1: ...', 'PHASE 2: ...' where each phase is one small buildable step and phase 1 already gives a working app. " +
        "Add one line 'QUESTION: ...' only if something essential is missing. No code."

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
            val secrets = headers.values.map { it.removePrefix("Bearer ").trim() }.filter { it.length >= 8 }
            fun hide(t: String) = secrets.fold(t) { acc, k -> acc.replace(k, "***") }
            val slow = e::class.simpleName?.contains("Timeout", true) == true ||
                e.message?.contains("timeout", true) == true || e.message?.contains("timed out", true) == true
            if (slow) throw TimedOut("The model took too long to answer.")
            if (e is kotlinx.coroutines.CancellationException) throw e
            throw AiError(hide("Network problem: ${e.message ?: "could not reach the provider"}"))
        }
        if (!r.status.isSuccess()) {
            val secrets = headers.values.map { it.removePrefix("Bearer ").trim() }.filter { it.length >= 8 }
            val msg = secrets.fold((errorMessage(text) ?: r.status.description).take(160)) { acc, k -> acc.replace(k, "***") }
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
