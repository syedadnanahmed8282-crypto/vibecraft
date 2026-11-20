package com.vibecraft.app.gh

import android.util.Base64
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

class GhError(message: String) : Exception(message)

class Res(val code: Int, val body: String)

data class RunInfo(val id: Long, val url: String, val conclusion: String?)

/** Minimal GitHub REST client: one atomic commit per publish, then watches the Actions build. */
object GitHub {
    private val http by lazy {
        HttpClient(OkHttp) {
            install(HttpTimeout) {
                requestTimeoutMillis = 90_000
                connectTimeoutMillis = 20_000
                socketTimeoutMillis = 90_000
            }
        }
    }
    private val json = Json { ignoreUnknownKeys = true }

    private fun obj(body: String): JsonObject? = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()

    private suspend fun call(method: HttpMethod, token: String, path: String, body: JsonObject? = null): Res {
        val r = try {
            http.request("https://api.github.com$path") {
                this.method = method
                header(HttpHeaders.Authorization, "Bearer $token")
                header(HttpHeaders.Accept, "application/vnd.github+json")
                header("X-GitHub-Api-Version", "2022-11-28")
                header(HttpHeaders.UserAgent, "Vibecraft")
                if (body != null) {
                    contentType(ContentType.Application.Json)
                    setBody(body.toString())
                }
            }
        } catch (e: Exception) {
            throw GhError("Network problem: ${e.message ?: "could not reach GitHub"}")
        }
        return Res(r.status.value, r.bodyAsText())
    }

    private fun msg(r: Res): String {
        val m = obj(r.body)?.get("message")?.jsonPrimitive?.contentOrNull
        return when (r.code) {
            401 -> "GitHub rejected the token (401). Check that it is correct and not expired."
            404 -> "Repository or branch not found, or the token cannot access it (404)."
            else -> "GitHub error ${r.code}: ${m ?: "request failed"}"
        }
    }

    private fun shaOf(r: Res): String =
        obj(r.body)!!["object"]!!.jsonObject["sha"]!!.jsonPrimitive.content

    /** Creates one commit holding all files on top of the branch. Returns the new commit sha. */
    suspend fun push(token: String, repo: String, branch: String, message: String, files: Map<String, String>, deletes: Set<String> = emptySet()): String {
        if (!Regex("^[\\w.-]+/[\\w.-]+$").matches(repo)) throw GhError("Enter the repository as owner/name.")
        var br = branch.ifBlank { "main" }
        var ref = call(HttpMethod.Get, token, "/repos/$repo/git/ref/heads/$br")
        // A brand-new repository answers 409 "Git Repository is empty"; a missing branch answers 404.
        if (ref.code == 404 || ref.code == 409) {
            val info = call(HttpMethod.Get, token, "/repos/$repo")
            if (info.code != 200) throw GhError(msg(info))
            val def = obj(info.body)?.get("default_branch")?.jsonPrimitive?.contentOrNull ?: "main"
            val defRef = call(HttpMethod.Get, token, "/repos/$repo/git/ref/heads/$def")
            if (defRef.code == 200) {
                val c = call(HttpMethod.Post, token, "/repos/$repo/git/refs", buildJsonObject {
                    put("ref", "refs/heads/$br"); put("sha", shaOf(defRef))
                })
                if (c.code !in 200..201) throw GhError(msg(c))
            } else {
                // Empty repository: create the first commit so the Git Data API can be used.
                val first = call(HttpMethod.Put, token, "/repos/$repo/contents/README.md", buildJsonObject {
                    put("message", "Initialize repository")
                    put("content", Base64.encodeToString("# Vibecraft app\n".toByteArray(), Base64.NO_WRAP))
                })
                if (first.code !in 200..201) throw GhError(msg(first))
                br = def
            }
            ref = call(HttpMethod.Get, token, "/repos/$repo/git/ref/heads/$br")
        }
        if (ref.code != 200) throw GhError(msg(ref))
        val parent = shaOf(ref)
        val commit = call(HttpMethod.Get, token, "/repos/$repo/git/commits/$parent")
        if (commit.code != 200) throw GhError(msg(commit))
        val baseTree = obj(commit.body)!!["tree"]!!.jsonObject["sha"]!!.jsonPrimitive.content
        // Compare with what the repository already holds, so only real changes are sent and nothing empty is committed.
        val remote = HashMap<String, String>()
        var complete = false
        val listing = call(HttpMethod.Get, token, "/repos/$repo/git/trees/$baseTree?recursive=1")
        if (listing.code == 200) {
            val o = obj(listing.body)
            complete = o?.get("truncated")?.jsonPrimitive?.contentOrNull != "true"
            o?.get("tree")?.jsonArray?.forEach { e ->
                val eo = e.jsonObject
                if (eo["type"]?.jsonPrimitive?.contentOrNull == "blob") {
                    remote[eo["path"]!!.jsonPrimitive.content] = eo["sha"]!!.jsonPrimitive.content
                }
            }
        }
        val send = if (complete) files.filter { (p, c) -> remote[p] != blobSha(c) } else files
        val remove = if (complete) deletes.filter { it in remote } else emptyList()
        if (send.isEmpty() && remove.isEmpty()) throw GhError("Nothing new to publish. Your repository already has these files.")
        val tree = call(HttpMethod.Post, token, "/repos/$repo/git/trees", buildJsonObject {
            put("base_tree", baseTree)
            putJsonArray("tree") {
                send.forEach { (p, c) ->
                    add(buildJsonObject { put("path", p); put("mode", "100644"); put("type", "blob"); put("content", c) })
                }
                remove.forEach { p ->
                    add(buildJsonObject { put("path", p); put("mode", "100644"); put("type", "blob"); put("sha", JsonNull) })
                }
            }
        })
        if (tree.code !in 200..201) throw GhError(msg(tree))
        val treeSha = obj(tree.body)!!["sha"]!!.jsonPrimitive.content
        val nc = call(HttpMethod.Post, token, "/repos/$repo/git/commits", buildJsonObject {
            put("message", message); put("tree", treeSha)
            putJsonArray("parents") { add(parent) }
        })
        if (nc.code !in 200..201) throw GhError(msg(nc))
        val newSha = obj(nc.body)!!["sha"]!!.jsonPrimitive.content
        val up = call(HttpMethod.Patch, token, "/repos/$repo/git/refs/heads/$br", buildJsonObject { put("sha", newSha) })
        if (up.code != 200) throw GhError(msg(up))
        return newSha
    }

    /** The id GitHub gives a file with this content, so unchanged files can be skipped. */
    private fun blobSha(text: String): String {
        val b = text.toByteArray(Charsets.UTF_8)
        val md = java.security.MessageDigest.getInstance("SHA-1")
        md.update("blob ${b.size}\u0000".toByteArray())
        md.update(b)
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** Waits for the Actions run of this commit to appear and finish. */
    suspend fun waitForRun(token: String, repo: String, sha: String): RunInfo {
        var id: Long? = null
        var url = ""
        var tries = 0
        while (id == null && tries < 25) {
            val r = call(HttpMethod.Get, token, "/repos/$repo/actions/runs?head_sha=$sha&per_page=5")
            if (r.code == 200) {
                val first = obj(r.body)?.get("workflow_runs")?.jsonArray?.firstOrNull()?.jsonObject
                if (first != null) {
                    id = first["id"]!!.jsonPrimitive.long
                    url = first["html_url"]!!.jsonPrimitive.content
                }
            }
            if (id == null) { delay(3000); tries++ }
        }
        val rid = id ?: throw GhError("No build started. Check that GitHub Actions is enabled and the workflow file was pushed.")
        var polls = 0
        while (polls < 240) {
            val o = obj(call(HttpMethod.Get, token, "/repos/$repo/actions/runs/$rid").body)
            if (o?.get("status")?.jsonPrimitive?.contentOrNull == "completed") {
                return RunInfo(rid, url, o?.get("conclusion")?.jsonPrimitive?.contentOrNull)
            }
            delay(5000); polls++
        }
        throw GhError("The build is taking too long. Check GitHub Actions.")
    }

    /** Pulls the useful error lines out of the failed job's log. */
    suspend fun failureSummary(token: String, repo: String, runId: Long): String {
        val jobs = call(HttpMethod.Get, token, "/repos/$repo/actions/runs/$runId/jobs")
        val arr = obj(jobs.body)?.get("jobs")?.jsonArray ?: return "The build failed."
        val job = arr.map { it.jsonObject }.firstOrNull { it["conclusion"]?.jsonPrimitive?.contentOrNull == "failure" }
            ?: return "The build failed."
        val log = call(HttpMethod.Get, token, "/repos/$repo/actions/jobs/${job["id"]!!.jsonPrimitive.long}/logs")
        if (log.code != 200) return "The build failed. Open the run on GitHub to read the log."
        val lines = log.body.lines().map { it.replace(Regex("^\\S+Z\\s"), "") }
        val errs = lines.filter {
            it.startsWith("e: ") || it.contains("error:") || it.startsWith("FAILURE") ||
                it.contains("What went wrong") || it.contains("Execution failed")
        }
        return (if (errs.isNotEmpty()) errs else lines.takeLast(25)).take(40).joinToString("\n") { it.take(300) }
    }
}
