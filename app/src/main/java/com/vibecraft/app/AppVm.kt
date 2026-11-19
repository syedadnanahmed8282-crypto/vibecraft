package com.vibecraft.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vibecraft.app.ai.AiClient
import com.vibecraft.app.ai.Fallback
import com.vibecraft.app.ai.Reply
import com.vibecraft.app.ai.Profile
import com.vibecraft.app.ai.ProfileStore
import com.vibecraft.app.data.CheckpointEntity
import com.vibecraft.app.data.FileEntity
import com.vibecraft.app.data.MessageEntity
import com.vibecraft.app.data.ProjectEntity
import com.vibecraft.app.data.SyncRepo
import com.vibecraft.app.ui.ThemeMode
import com.vibecraft.app.gen.CodeBlocks
import com.vibecraft.app.gen.Edits
import com.vibecraft.app.gen.Plan
import com.vibecraft.app.gen.PlanFile
import com.vibecraft.app.gen.PlanParser
import com.vibecraft.app.gen.Template
import com.vibecraft.app.gh.GitHub
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.UUID

/** One line in the live progress card. state: 0 waiting, 1 running, 2 done, 3 failed. */
data class Step(val label: String, val state: Int)

class AppVm(app: Application) : AndroidViewModel(app) {
    private val vc = app as VibecraftApp
    private val dao = vc.db.dao()
    private val prefs = app.getSharedPreferences("vc", 0)
    private val sync = SyncRepo(vc.supabase, dao)
    private val store = ProfileStore(app)

    val projects = dao.projects().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var ready by mutableStateOf(false); private set
    var signedIn by mutableStateOf(false); private set
    var guest by mutableStateOf(prefs.getBoolean("guest", false)); private set
    var busy by mutableStateOf(false); private set
    var authOk by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null)

    var theme by mutableStateOf(runCatching { ThemeMode.valueOf(prefs.getString("theme", "System")!!) }.getOrDefault(ThemeMode.System))
        private set

    // ---- AI profiles (keys stay on this phone, encrypted) ----
    val profiles = mutableStateListOf<Profile>().also { it.addAll(store.list()) }
    var activeId by mutableStateOf(store.activeId()); private set
    var autoSwitch by mutableStateOf(store.autoSwitch()); private set
    var thinkingId by mutableStateOf<String?>(null); private set

    val activeName: String? get() = profiles.firstOrNull { it.id == activeId }?.name
    fun hasKey(id: String) = store.key(id) != null
    fun usedToday(id: String) = store.used(id)

    fun toggleAuto(on: Boolean) { autoSwitch = on; store.setAuto(on) }

    fun moveProfile(i: Int, d: Int) {
        val j = i + d
        if (i in profiles.indices && j in profiles.indices) {
            val t = profiles[i]; profiles[i] = profiles[j]; profiles[j] = t
            store.saveOrder(profiles.toList())
        }
    }

    fun pickModel(id: String) {
        activeId = id; store.setActive(id)
        val i = profiles.indexOfFirst { it.id == id }
        if (i > 0) { profiles.add(0, profiles.removeAt(i)); store.saveOrder(profiles.toList()) }
    }

    /** Returns an error text, or null when saved. A blank key keeps the stored one. */
    fun saveProfile(p: Profile, key: String): String? {
        if (key.isNotBlank()) {
            if (!store.secure()) return "Secure storage is not available on this phone, so the key cannot be saved."
            store.putKey(p.id, key.trim())
        } else if (store.key(p.id) == null) {
            return "Enter an API key."
        }
        val i = profiles.indexOfFirst { it.id == p.id }
        if (i >= 0) profiles[i] = p else profiles.add(p)
        store.saveOrder(profiles.toList())
        if (activeId == null || profiles.none { it.id == activeId }) { activeId = p.id; store.setActive(p.id) }
        return null
    }

    fun deleteProfile(id: String) {
        store.removeKey(id)
        profiles.removeAll { it.id == id }
        store.saveOrder(profiles.toList())
        if (activeId == id) { activeId = profiles.firstOrNull()?.id; store.setActive(activeId) }
    }

    suspend fun testProfile(p: Profile, key: String): String {
        val k = key.ifBlank { store.key(p.id) ?: "" }
        if (k.isBlank()) return "Enter an API key first."
        return try {
            AiClient.chat(p, k, listOf("user" to "Reply with the single word OK."), "You are a connection test.")
            "Connected."
        } catch (e: Exception) {
            e.message ?: "Connection failed."
        }
    }

    val email: String get() = vc.supabase.auth.currentUserOrNull()?.email ?: ""

    init {
        viewModelScope.launch {
            vc.supabase.auth.awaitInitialization()
            signedIn = vc.supabase.auth.currentUserOrNull() != null
            ready = true
            if (signedIn) sync.run()
        }
    }

    fun messages(id: String) = dao.messages(id)

    fun changeTheme(mode: ThemeMode) {
        theme = mode
        prefs.edit().putString("theme", mode.name).apply()
    }

    fun submit(signUp: Boolean, name: String, mail: String, pass: String) {
        if (mail.isBlank() || pass.length < 6) {
            message = "Enter a valid email and a password with 6 or more characters."
            return
        }
        if (signUp && name.isBlank()) {
            message = "Enter your name."
            return
        }
        viewModelScope.launch {
            busy = true
            message = null
            try {
                if (signUp) {
                    vc.supabase.auth.signUpWith(Email) {
                        email = mail.trim()
                        password = pass
                        data = buildJsonObject { put("name", name.trim()) }
                    }
                } else {
                    vc.supabase.auth.signInWith(Email) { email = mail.trim(); password = pass }
                }
                if (vc.supabase.auth.currentUserOrNull() != null) {
                    authOk = true
                    delay(650)
                    prefs.edit().putBoolean("guest", false).apply()
                    guest = false
                    signedIn = true
                    authOk = false
                    sync.run()
                } else {
                    message = "Check your email to confirm your account, then sign in."
                }
            } catch (e: Exception) {
                message = e.message ?: "Something went wrong. Try again."
            }
            busy = false
        }
    }

    fun continueAsGuest() {
        prefs.edit().putBoolean("guest", true).apply()
        guest = true
    }

    /** clearDevice = false keeps every project and chat on this phone. */
    fun signOut(clearDevice: Boolean) {
        viewModelScope.launch {
            runCatching { vc.supabase.auth.signOut() }
            if (clearDevice) withContext(Dispatchers.IO) { vc.db.clearAllTables() }
            signedIn = false
            guest = false
            prefs.edit().putBoolean("guest", false).apply()
            message = null
        }
    }

    fun deleteProject(id: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            dao.project(id)?.let { dao.putProject(it.copy(deletedAt = now, updatedAt = now, dirty = true)) }
            backgroundSync()
        }
    }

    /** Returns the id of the project the message went to (a new one is created when projectId is null). */
    fun send(projectId: String?, text: String): String? {
        val body = text.trim()
        if (body.isEmpty()) return projectId
        val id = projectId ?: UUID.randomUUID().toString()
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            if (projectId == null) dao.putProject(ProjectEntity(id, body.take(28), now, now))
            else dao.project(id)?.let { dao.putProject(it.copy(updatedAt = now, dirty = true)) }
            dao.putMessage(MessageEntity(UUID.randomUUID().toString(), id, "user", body, now))
            backgroundSync()
            reply(id)
        }
        return id
    }

    private suspend fun addMsg(projectId: String, role: String, text: String) {
        val now = System.currentTimeMillis()
        dao.putMessage(MessageEntity(UUID.randomUUID().toString(), projectId, role, text, now))
        dao.project(projectId)?.let { dao.putProject(it.copy(updatedAt = now, dirty = true)) }
        backgroundSync()
    }

    // ---- AI work: a short plan first, then one request per file ----
    var steps by mutableStateOf<List<Step>>(emptyList()); private set

    private fun setStep(i: Int, state: Int) {
        steps = steps.mapIndexed { j, st -> if (j == i) Step(st.label, state) else st }
    }

    /** What the AI is told about the project: all files when small, otherwise just the list. */
    private suspend fun snapshotOf(projectId: String): String {
        val fs = dao.filesOnce(projectId).filter { it.deletedAt == null }
        if (fs.isEmpty()) return "The project has no files yet."
        return if (fs.sumOf { it.content.length } <= 12_000) fs.joinToString("\n\n") { "--- ${it.path}\n${it.content}" }
        else fs.joinToString("\n") { "${it.path} (${it.content.length} characters)" }
    }

    /** Asks the active model and moves to the next profile when one is out of quota. Null means a note was added to the chat. */
    private suspend fun askModel(projectId: String, system: String, msgs: List<Pair<String, String>>): Reply? {
        val usable = profiles.filter { store.key(it.id) != null }
        val first = usable.firstOrNull { it.id == activeId }
        val order = listOfNotNull(first) + usable.filter { it !== first }
        if (order.isEmpty()) {
            addMsg(projectId, "note", "Add an AI profile with an API key in Settings to get replies.")
            return null
        }
        var i = 0
        while (i < order.size) {
            val p = order[i]
            try {
                val r = AiClient.chatFull(p, store.key(p.id)!!, msgs, system)
                store.addUsage(p.id)
                activeId = p.id; store.setActive(p.id)
                return r
            } catch (e: CancellationException) {
                throw e
            } catch (e: Fallback) {
                val next = order.getOrNull(i + 1)
                if (autoSwitch && next != null) {
                    addMsg(projectId, "note", "${p.name} limit reached. Switched to ${next.name} automatically.")
                    i++
                } else {
                    addMsg(projectId, "note", "${p.name} limit reached." + if (!autoSwitch) " Auto-switch is off." else "")
                    return null
                }
            } catch (e: Exception) {
                addMsg(projectId, "note", "${p.name}: ${e.message ?: "request failed"}")
                return null
            }
        }
        return null
    }

    private fun codeOf(r: Reply): String? {
        if (r.cut) return null
        val m = Regex("(?s)```[^\\n]*\\r?\\n(.*?)```").find(r.text)
        val body = m?.groupValues?.get(1) ?: r.text.takeIf { !it.contains("```") && it.isNotBlank() }
        return body?.let { CodeBlocks.dropPathLine(it).trimEnd('\n') + "\n" }
    }

    /** Writes one file. For existing files the AI may send only the changed parts. Null means it could not. */
    private suspend fun writeFile(
        projectId: String, pf: PlanFile, plan: Plan, request: String,
        existing: Map<String, String>, written: Map<String, String>,
    ): String? {
        val cur = written[pf.path] ?: existing[pf.path]
        val others = (existing.keys + written.keys).filter { it != pf.path }
        val ctx = buildString {
            append("What the user asked:\n").append(request).append("\n\nPlan: ").append(plan.summary).append("\nFiles in this change:\n")
            plan.files.forEach { append("- ").append(it.path).append(": ").append(it.desc).append('\n') }
            written.forEach { (p, c) ->
                if (p != pf.path) append("\nAlready written in this change, ").append(p).append(":\n```\n").append(c.take(4000)).append("\n```\n")
            }
            if (others.isNotEmpty()) append("\nOther files in the project: ").append(others.joinToString(", ")).append('\n')
            append("\nWrite this file now: ").append(pf.path).append(" (").append(pf.desc).append(").")
            if (cur != null) append("\n\nIts current content:\n```\n").append(cur).append("\n```") else append("\n\nIt is a new file.")
        }
        val name = pf.path.substringAfterLast('/')
        val r = askModel(projectId, AiClient.SYS_FILE, listOf("user" to ctx)) ?: return null
        var body = codeOf(r)
        if (body == null) {
            addMsg(projectId, "note", "The reply for $name was cut off or had no code. Try again, or use a model with a longer limit.")
            return null
        }
        if (Edits.isEdit(body)) {
            val applied = if (cur != null) Edits.apply(cur, body) else null
            if (applied != null) return CodeBlocks.normalize(pf.path, applied).second
            val r2 = askModel(projectId, AiClient.SYS_FILE, listOf("user" to (ctx + "\n\nYour edit did not match the file. Send the complete new file in one code block instead."))) ?: return null
            body = codeOf(r2)
            if (body == null || Edits.isEdit(body)) {
                addMsg(projectId, "note", "Could not apply the change to $name.")
                return null
            }
        }
        return CodeBlocks.normalize(pf.path, body).second
    }

    /** Saves the files as one version and adds a card per changed file to the chat. */
    private suspend fun commit(projectId: String, files: List<Pair<String, String>>, request: String): Boolean {
        val old = dao.filesOnce(projectId).filter { it.deletedAt == null }.associate { it.path to it.content }
        val changed = files.filter { old[it.first] != it.second }
        val saved = saveFiles(projectId, files, request, activeName)
        if (saved == null) {
            addMsg(projectId, "note", "Nothing changed: the files are already up to date.")
            return false
        }
        changed.forEach { (path, _) -> addMsg(projectId, "note", "FILE|${if (path in old) "updated" else "created"}|$path") }
        addMsg(projectId, "note", "Saved ${saved.first} file${if (saved.first == 1) "" else "s"} · v${saved.second}")
        return true
    }

    private suspend fun reply(projectId: String) {
        thinkingId = projectId
        steps = listOf(Step("Reading your request", 1))
        try {
            val all = dao.messagesOnce(projectId)
            val request = all.lastOrNull { it.role == "user" }?.content ?: ""
            val history = all.filter { it.role == "user" || it.role == "assistant" }.takeLast(12).map { it.role to it.content }
            val sys = AiClient.SYS_PLAN + "\n\nCurrent project files:\n" + snapshotOf(projectId)
            val planReply = askModel(projectId, sys, history) ?: return
            val text = planReply.text
            var plan = PlanParser.parse(text)

            if (plan.files.isEmpty()) {
                // A plain answer, or a model that ignored the format and sent code blocks anyway.
                val found = CodeBlocks.files(text)
                val shown = if (found.isNotEmpty()) CodeBlocks.stripBlocks(text).ifBlank { "Done." } else text.ifBlank { "(empty reply)" }
                addMsg(projectId, "assistant", shown)
                if (found.isNotEmpty()) commit(projectId, found, request)
                return
            }

            val hasPreview = dao.filesOnce(projectId).any { it.deletedAt == null && it.path == "preview.html" }
            if (!hasPreview && plan.files.none { it.path == "preview.html" } && plan.files.any { it.path.endsWith(".kt") }) {
                plan = plan.copy(files = plan.files + PlanFile("preview.html", "design preview of the main screen"))
            }
            addMsg(projectId, "assistant", plan.summary.ifBlank { "I will change ${plan.files.size} files." })

            steps = listOf(Step("Planned ${plan.files.size} file${if (plan.files.size == 1) "" else "s"}", 2)) +
                plan.files.map { Step("Writing ${it.path.substringAfterLast('/')}", 0) } + Step("Saving version", 0)

            val existing = dao.filesOnce(projectId).filter { it.deletedAt == null }.associate { it.path to it.content }
            val written = linkedMapOf<String, String>()
            var failedAt: String? = null
            for ((i, pf) in plan.files.withIndex()) {
                setStep(i + 1, 1)
                val out = writeFile(projectId, pf, plan, request, existing, written)
                if (out == null) { setStep(i + 1, 3); failedAt = pf.path.substringAfterLast('/'); break }
                written[pf.path] = out
                setStep(i + 1, 2)
            }
            if (written.isNotEmpty()) {
                val last = steps.lastIndex
                setStep(last, 1)
                commit(projectId, written.toList(), request)
                setStep(last, 2)
            }
            if (failedAt != null) {
                addMsg(projectId, "note", "Stopped at $failedAt." + if (written.isNotEmpty()) " The files before it were saved." else "")
            }
        } finally {
            thinkingId = null
            steps = emptyList()
        }
    }

    // ---- project files, history, preview ----
    fun files(id: String) = dao.files(id)
    fun checkpoints(id: String) = dao.checkpoints(id)

    /** Writes changed files and records one checkpoint. Returns (changed files, version) or null when nothing changed. */
    private suspend fun saveFiles(projectId: String, found: List<Pair<String, String>>, title: String, model: String?): Pair<Int, Int>? {
        if (found.isEmpty()) return null
        val now = System.currentTimeMillis()
        val existing = dao.filesOnce(projectId).associateBy { it.path }
        val changed = found.filter { (path, content) ->
            val old = existing[path]
            old == null || old.deletedAt != null || old.content != content
        }
        if (changed.isEmpty()) return null
        changed.forEach { (path, content) ->
            dao.putFile(FileEntity(CodeBlocks.fileId(projectId, path), projectId, path, content, now))
        }
        val v = (dao.maxVersion(projectId) ?: 0) + 1
        val changes = buildJsonObject { changed.forEach { (path, content) -> put(path, content) } }
        dao.putCheckpoint(
            CheckpointEntity(CodeBlocks.checkpointId(projectId, v), projectId, v, title.take(60), model, changes.toString(), now)
        )
        return changed.size to v
    }

    /** Puts the project back to how it was at [version]; the restore itself becomes a new version. */
    fun restore(projectId: String, version: Int) {
        viewModelScope.launch {
            val state = linkedMapOf<String, String>()
            dao.checkpointsOnce(projectId).filter { it.version <= version }.forEach { cp ->
                val o = Json.parseToJsonElement(cp.changes).jsonObject
                if (o.containsKey("__full")) state.clear()
                o.forEach { (k, v) -> if (k != "__full") state[k] = v.jsonPrimitive.content }
            }
            val now = System.currentTimeMillis()
            dao.filesOnce(projectId).forEach { f ->
                if (f.deletedAt == null && f.path !in state) dao.putFile(f.copy(deletedAt = now, updatedAt = now, dirty = true))
            }
            state.forEach { (path, content) ->
                dao.putFile(FileEntity(CodeBlocks.fileId(projectId, path), projectId, path, content, now))
            }
            val v = (dao.maxVersion(projectId) ?: 0) + 1
            val full = buildJsonObject { put("__full", "1"); state.forEach { (k, c) -> put(k, c) } }
            dao.putCheckpoint(
                CheckpointEntity(CodeBlocks.checkpointId(projectId, v), projectId, v, "Restored v$version", null, full.toString(), now)
            )
            backgroundSync()
        }
    }

    // ---- publish to GitHub ----
    var pubStage by mutableStateOf(0); private set      // 0 idle, 1 uploading, 2 building, 3 done, 4 failed
    var pubInfo by mutableStateOf<String?>(null); private set
    var runUrl by mutableStateOf<String?>(null); private set
    var buildErrors by mutableStateOf<String?>(null); private set

    fun hasGhToken() = store.ghToken() != null
    fun ghRepo(id: String) = prefs.getString("gh_repo_$id", "") ?: ""
    fun ghBranch(id: String) = prefs.getString("gh_branch_$id", "main") ?: "main"

    fun publish(projectId: String, repo: String, branch: String, commitMsg: String, tokenInput: String) {
        viewModelScope.launch {
            pubStage = 1; pubInfo = null; runUrl = null; buildErrors = null
            try {
                if (tokenInput.isNotBlank() && !store.setGhToken(tokenInput.trim())) {
                    error("Secure storage is not available on this phone, so the token cannot be saved.")
                }
                val token = store.ghToken() ?: error("Enter your GitHub token.")
                val proj = dao.project(projectId) ?: error("Open a project first.")
                val own = dao.filesOnce(projectId).filter { it.deletedAt == null }.associate { it.path to it.content }
                val sha = GitHub.push(token, repo.trim(), branch.trim(), commitMsg.ifBlank { "Update from Vibecraft" }, Template.files(proj.name, projectId) + own)
                prefs.edit().putString("gh_repo_$projectId", repo.trim()).putString("gh_branch_$projectId", branch.trim()).apply()
                pubStage = 2
                val run = GitHub.waitForRun(token, repo.trim(), sha)
                runUrl = run.url
                if (run.conclusion == "success") {
                    pubStage = 3
                } else {
                    buildErrors = GitHub.failureSummary(token, repo.trim(), run.id)
                    pubInfo = "The build failed."
                    pubStage = 4
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                pubInfo = e.message ?: "Publishing failed."
                pubStage = 4
            }
        }
    }

    /** Sends the build errors back to the AI so it can fix the code. */
    fun fixBuild(projectId: String) {
        val errs = buildErrors ?: return
        send(projectId, "The GitHub build failed. Please fix the code and send the complete changed files. Errors:\n$errs")
        pubStage = 0
    }

    fun syncNow() {
        viewModelScope.launch {
            busy = true
            message = sync.run().fold({ it }, { it.message ?: "Sync failed." })
            busy = false
        }
    }

    private fun backgroundSync() {
        if (signedIn) viewModelScope.launch { sync.run() }
    }
}
