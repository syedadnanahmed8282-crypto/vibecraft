package com.vibecraft.app

import android.app.Application
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vibecraft.app.ai.AiClient
import com.vibecraft.app.ai.Candidate
import com.vibecraft.app.ai.Fallback
import com.vibecraft.app.ai.ProviderOrder
import com.vibecraft.app.ai.Selection
import com.vibecraft.app.ai.Reply
import com.vibecraft.app.ai.TimedOut
import com.vibecraft.app.ai.Profile
import com.vibecraft.app.ai.ProfileValidation
import com.vibecraft.app.ai.ProfileStore
import com.vibecraft.app.data.CheckpointEntity
import com.vibecraft.app.data.FileEntity
import com.vibecraft.app.data.MessageEntity
import com.vibecraft.app.data.ProjectEntity
import com.vibecraft.app.data.SyncRepo
import com.vibecraft.app.ui.ThemeMode
import com.vibecraft.app.gen.Brain
import com.vibecraft.app.gen.BrainOps
import com.vibecraft.app.gen.BuildError
import com.vibecraft.app.gen.BuildLog
import com.vibecraft.app.gen.BuildReport
import com.vibecraft.app.gen.BuildStatus
import com.vibecraft.app.gen.CodeBlocks
import com.vibecraft.app.gen.ContextSelector
import com.vibecraft.app.gen.PreviewSite
import com.vibecraft.app.gen.EditEngine
import com.vibecraft.app.gen.EditResult
import com.vibecraft.app.gen.History
import com.vibecraft.app.gen.Intent
import com.vibecraft.app.gen.Notes
import com.vibecraft.app.gen.NoticeKind
import com.vibecraft.app.gen.NoticeText
import com.vibecraft.app.gen.PhaseView
import com.vibecraft.app.notify.Chime
import com.vibecraft.app.notify.Notifier
import com.vibecraft.app.gen.Pending
import com.vibecraft.app.gen.SummaryOut
import com.vibecraft.app.gen.Problem
import com.vibecraft.app.gen.Quoting
import com.vibecraft.app.gen.Intents
import com.vibecraft.app.gen.Manifest
import com.vibecraft.app.gen.Protect
import com.vibecraft.app.gen.Reader
import com.vibecraft.app.gen.RunReport
import com.vibecraft.app.gen.Splitter
import com.vibecraft.app.gen.Task
import com.vibecraft.app.gen.TaskState
import com.vibecraft.app.gen.Edits
import com.vibecraft.app.gen.Lint
import com.vibecraft.app.gen.Plan
import com.vibecraft.app.gen.PlanFile
import com.vibecraft.app.gen.PlanParser
import com.vibecraft.app.gen.Template
import com.vibecraft.app.gh.GhError
import com.vibecraft.app.gh.GitHub
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.UUID

/** Longest file that is shown and rewritten whole; longer files are edited in parts only. */
private const val FULL_FILE_LIMIT = 24_000

/** Most AI requests one build-and-repair task may use. */
private const val REPAIR_BUDGET = 10

/** One line in the live progress card. state: 0 waiting, 1 running, 2 done, 3 failed. */
/** One progress row. phase: read, reason, plan, work, check or save. state: 0 waiting, 1 active, 2 done, 3 failed. */
data class Step(
    val label: String, val state: Int, val phase: String = "", val sub: String = "",
    val progress: Float = -1f, val startedAt: Long = 0L,
)

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
        ProfileValidation.checkUrl(p.baseUrl)?.let { return it }
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

    /** Runs the last user message again (the Try again button). */
    fun retry(projectId: String) {
        if (thinkingId != null) return
        viewModelScope.launch { reply(projectId) }
    }

    private suspend fun addMsg(projectId: String, role: String, text: String) {
        if (role == "note" && !text.startsWith("FILE|") && !text.startsWith("ERR|") && !text.startsWith("PAUSE|")) lastNote = text
        if (role == "assistant") lastAssistant = text
        val now = System.currentTimeMillis()
        dao.putMessage(MessageEntity(UUID.randomUUID().toString(), projectId, role, text, now))
        dao.project(projectId)?.let { dao.putProject(it.copy(updatedAt = now, dirty = true)) }
        backgroundSync()
    }

    // ---- AI work: plan, read, write one file at a time, check, save, summarize ----
    var steps by mutableStateOf<List<Step>>(emptyList()); private set
    private var aiCalls = 0
    private var lastNote: String? = null
    private var lastErr: Problem? = null
    private var planText = ""
    private var deferErr = false
    private var fileDescs: Map<String, String> = emptyMap()

    var planFirst by mutableStateOf(prefs.getBoolean("plan_first", true)); private set
    fun changePlanFirst(on: Boolean) { planFirst = on; prefs.edit().putBoolean("plan_first", on).apply() }

    // ---- background work, notifications and sounds ----
    private val appCtx: Application get() = getApplication<Application>()
    var notifyOn by mutableStateOf(prefs.getBoolean("notify_on", true)); private set
    var soundOn by mutableStateOf(prefs.getBoolean("sound_on", true)); private set
    var notifyAllowed by mutableStateOf(Notifier.canPost(app)); private set
    var askNotifyPermission by mutableStateOf(false)
    var openRequest by mutableStateOf<String?>(null)

    fun changeNotify(on: Boolean) { notifyOn = on; prefs.edit().putBoolean("notify_on", on).apply() }
    fun changeSound(on: Boolean) { soundOn = on; prefs.edit().putBoolean("sound_on", on).apply() }
    fun refreshNotifyState() { notifyAllowed = Notifier.canPost(appCtx) }
    fun onNotifyPermissionResult(granted: Boolean) {
        askNotifyPermission = false
        prefs.edit().putBoolean("notify_asked", true).apply()
        refreshNotifyState()
    }
    fun declineNotifyPrompt() { askNotifyPermission = false; prefs.edit().putBoolean("notify_asked", true).apply() }

    private fun maybeAskNotifyPermission() {
        if (Build.VERSION.SDK_INT >= 33 && !Notifier.canPost(appCtx) && !prefs.getBoolean("notify_asked", false)) askNotifyPermission = true
    }

    /** The settings button: shows exactly how a notification will look and sound. */
    fun testNotification() {
        if (!Notifier.canPost(appCtx)) {
            if (!prefs.getBoolean("notify_asked", false)) askNotifyPermission = true else Notifier.openSettings(appCtx)
            return
        }
        Notifier.post(
            appCtx, null,
            NoticeText.build(NoticeKind.DONE, "Vibecraft", false, "This is how Vibecraft will tell you when a task is done.", "Test notification"),
            silent = !soundOn,
        )
    }

    private var runName = "Vibecraft"
    private var runBn = false
    private var serviceOn = false
    private var lastAssistant: String? = null

    /** Starts the background service the first time the AI is really called in a run, so quick answers never flash a notification. */
    private fun ensureRunService() {
        if (serviceOn) return
        serviceOn = true
        Notifier.runStarted(appCtx, NoticeText.progress(emptyList(), runName, runBn))
    }

    /** Tells the user: a system notification with its own sound when the app is away, the same sound inside the app otherwise. */
    private suspend fun alert(kind: NoticeKind, projectId: String, detail: String, extra: String = "") {
        if (Notifier.foreground) {
            if (soundOn && kind != NoticeKind.REPLY) Chime.play(kind)
            return
        }
        if (!notifyOn) return
        val name = dao.project(projectId)?.name ?: "Vibecraft"
        Notifier.post(
            appCtx, projectId, NoticeText.build(kind, name, runBn, detail, extra), silent = !soundOn,
            okLabel = NoticeText.okLabel(runBn), okText = NoticeText.okText(runBn),
        )
    }

    /** What the app remembers about a project: memory, brief, roadmap, tasks, last run. Stored in the project itself. */
    fun brainFlow(id: String): Flow<Brain> = dao.files(id).map { rows -> BrainOps.parse(rows.firstOrNull { it.path == BrainOps.PATH }?.content) }

    private suspend fun loadBrain(projectId: String): Brain =
        BrainOps.parse(dao.filesOnce(projectId).firstOrNull { it.path == BrainOps.PATH && it.deletedAt == null }?.content)

    private suspend fun saveBrain(projectId: String, b: Brain) {
        dao.putFile(FileEntity(CodeBlocks.fileId(projectId, BrainOps.PATH), projectId, BrainOps.PATH, BrainOps.toJson(b), System.currentTimeMillis()))
    }

    // ---- progress phases: Reading, Reasoning, Planning, Working, Checking, Saving ----
    private val phaseKeys = listOf("read", "reason", "plan", "work", "check", "save")
    private val phaseNames = listOf("Reading", "Reasoning", "Planning", "Working", "Checking", "Saving")
    private var phState = IntArray(6)
    private var phSub = Array(6) { "" }
    private var phStart = LongArray(6)
    private var phProgress = -1f

    private fun pushPhases() {
        steps = List(6) { i -> Step(phaseNames[i], phState[i], phaseKeys[i], phSub[i], if (i == 3) phProgress else -1f, phStart[i]) }
        if (serviceOn) {
            Notifier.runProgress(appCtx, NoticeText.progress(steps.map { PhaseView(it.label, it.state, it.phase, it.sub, it.progress) }, runName, runBn))
        }
    }

    private fun resetPhases() {
        phState = IntArray(6); phSub = Array(6) { "" }; phStart = LongArray(6); phProgress = -1f
        pushPhases()
    }

    /** state: 0 waiting, 1 active, 2 done, 3 failed. The timer starts when a phase first becomes active. */
    private fun phase(i: Int, state: Int, sub: String? = null) {
        val was = phState[i]
        phState[i] = state
        if (sub != null) phSub[i] = sub
        if (state == 1 && was != 1) phStart[i] = System.currentTimeMillis()
        pushPhases()
    }

    /** What the AI is told about the project: a manifest plus only the files that matter for this request. */
    private fun contextOf(files: Map<String, String>, request: String, recent: List<String>): String =
        if (files.isEmpty()) "The project has no files yet." else ContextSelector.select(request, recent, files).render()

    /** Asks the active model and moves to the next profile when one is out of quota. Null means it could not answer. */
    private suspend fun askModel(projectId: String, system: String, msgs: List<Pair<String, String>>, quiet: Boolean = false): Reply? {
        ensureRunService()
        suspend fun say(t: String) { if (!quiet) addMsg(projectId, "note", t) }
        // A terminal error is kept for the closing card; while files are being written it is not shown twice.
        suspend fun fail(kind: String, title: String, sub: String, raw: String) {
            lastErr = Problem(kind, title, sub, raw)
            lastNote = title
            if (!quiet && !deferErr) {
                addMsg(projectId, "note", Notes.err(kind, title, sub, raw))
                alert(NoticeKind.PROBLEM, projectId, title + if (sub.isBlank()) "" else ". $sub")
            }
        }
        val sel = ProviderOrder.choose(
            profiles.map { Candidate(it.id, it.name, store.key(it.id) != null, it.dailyLimit, store.used(it.id)) },
            activeId, autoSwitch,
        )
        val order: List<Profile> = when (sel) {
            is Selection.Blocked -> { fail("setup", sel.message, "", ""); return null }
            is Selection.Order -> {
                sel.notice?.let { say(it) }
                sel.ids.mapNotNull { id -> profiles.firstOrNull { it.id == id } }
            }
        }
        var i = 0
        var retried = false
        while (i < order.size) {
            val p = order[i]
            try {
                aiCalls++
                val r = AiClient.chatFull(p, store.key(p.id)!!, msgs, system)
                store.addUsage(p.id)
                activeId = p.id; store.setActive(p.id)
                return r
            } catch (e: CancellationException) {
                throw e
            } catch (e: TimedOut) {
                val next = order.getOrNull(i + 1)
                if (!retried) {
                    retried = true
                    say("${p.name} is slow. Trying once more…")
                } else if (autoSwitch && next != null) {
                    retried = false
                    say("${p.name} took too long. Switched to ${next.name} automatically.")
                    i++
                } else {
                    fail("timeout", "${p.name} took too long", "No answer in time. Free models can be slow: try again or pick a faster one.", e.message ?: "timeout")
                    return null
                }
            } catch (e: Fallback) {
                val next = order.getOrNull(i + 1)
                if (autoSwitch && next != null) {
                    retried = false
                    say("${p.name} limit reached. Switched to ${next.name} automatically.")
                    i++
                } else {
                    fail("limit", "${p.name} reached its daily limit", if (!autoSwitch) "Auto-switch is off. Turn it on or pick another model." else "No other model is ready.", e.message ?: "limit")
                    return null
                }
            } catch (e: Exception) {
                val m = e.message ?: "request failed"
                val low = m.lowercase()
                if ("401" in low || "403" in low || "unauthorized" in low || "api key" in low || "api_key" in low) {
                    fail("key", "The API key for ${p.name} was rejected", "Check the key in AI profiles.", m)
                } else {
                    fail("other", "${p.name} could not answer", m.take(140), m)
                }
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

    /**
     * Asks for one file (new, or changed parts only) and returns its final text. Null means it could not.
     * mustEdit: the AI saw only part of the file, so a whole-file rewrite is refused.
     */
    private suspend fun produce(projectId: String, path: String, cur: String?, ctx: String, mustEdit: Boolean = false): String? {
        val name = path.substringAfterLast('/')
        val base = cur?.let { Manifest.hash(it) }
        val r = askModel(projectId, AiClient.SYS_FILE, listOf("user" to ctx)) ?: return null
        var body = codeOf(r)
        if (body == null) {
            addMsg(projectId, "note", "The reply for $name was cut off or had no code. Try again, or use a model with a longer limit.")
            return null
        }
        if (EditEngine.isEdit(body)) {
            val res = if (cur != null) EditEngine.apply(cur, path, body, base) else EditResult(null, "The file does not exist yet, so edits cannot be applied.")
            if (res.text != null) return CodeBlocks.normalize(path, res.text).second
            if (mustEdit) {
                addMsg(projectId, "note", "Could not apply the change to $name (${res.error}). The file is long, so it was not rewritten.")
                return null
            }
            val r2 = askModel(projectId, AiClient.SYS_FILE, listOf("user" to (ctx + "\n\nYour edit could not be applied (${res.error}). Send the complete new file in one code block instead."))) ?: return null
            body = codeOf(r2)
            if (body == null || EditEngine.isEdit(body)) {
                addMsg(projectId, "note", "Could not apply the change to $name.")
                return null
            }
        } else if (mustEdit && cur != null) {
            addMsg(projectId, "note", "$name is too long to rewrite in one go and could not be split automatically. Ask for a smaller change, or ask to split it into smaller files.")
            return null
        }
        return CodeBlocks.normalize(path, body).second
    }

    /** The visible start of a long file, cut at a line boundary. */
    private fun visibleHead(text: String): Pair<String, Int> {
        var used = 0
        var n = 0
        for (l in text.lines()) { if (used + l.length + 1 > FULL_FILE_LIMIT) break; used += l.length + 1; n++ }
        return text.lines().take(n).joinToString("\n") to n
    }

    private suspend fun writeFile(
        projectId: String, pf: PlanFile, plan: Plan, request: String,
        existing: Map<String, String>, written: Map<String, String>,
    ): String? {
        val isPreviewFile = pf.path == "preview.html" || pf.path.startsWith("preview/")
        val oldFormat = pf.path == "preview.html" && existing["preview.html"]?.contains(PreviewSite.MARKER) != true
        val cur = if (oldFormat) null else (written[pf.path] ?: existing[pf.path])
        val tooBig = cur != null && cur.length > FULL_FILE_LIMIT
        val others = (existing.keys + written.keys).filter { it != pf.path }
        val ctx = buildString {
            append("What the user asked:\n").append(request).append("\n\nPlan: ").append(plan.summary).append("\nFiles in this change:\n")
            plan.files.forEach { append("- ").append(it.path).append(": ").append(it.desc).append('\n') }
            if (planText.isNotBlank()) append("\nApproved plan (follow it, including the name, the icon idea and the design):\n").append(planText).append('\n')
            written.forEach { (p, c) ->
                if (p != pf.path) append("\nAlready written in this change, ").append(p).append(":\n```\n").append(c.take(4000)).append("\n```\n")
            }
            if (others.isNotEmpty()) append("\nOther files in the project: ").append(others.joinToString(", ")).append('\n')
            append("\nWrite this file now: ").append(pf.path).append(" (").append(pf.desc).append(").")
            if (isPreviewFile && cur == null) {
                val ui = existing.filter { (p, c) -> p.endsWith(".kt") && p !in written && c.contains("@Composable") }.entries.take(3)
                ui.forEach { (p, c) -> append("\nExisting screen ").append(p).append(":\n```\n").append(c.take(3000)).append("\n```\n") }
                (written["design.json"] ?: existing["design.json"])?.let { append("\nDesign tokens (design.json):\n```\n").append(it.take(2500)).append("\n```\n") }
                append("\nWrite a compact preview of the app as it looks after this change, with its features visible, following the preview rules. ")
                append("It replaces an older preview if there is one.")
            } else if (cur != null && tooBig) {
                val (head, n) = visibleHead(cur)
                append("\n\nIts current content is INCOMPLETE (lines 1-$n of ${cur.lines().size}; the rest is not shown). Send edit blocks only, for the visible part:\n```\n")
                append(head).append("\n```")
            } else if (cur != null) append("\n\nIts current content:\n```\n").append(cur).append("\n```") else append("\n\nIt is a new file.")
        }
        return produce(projectId, pf.path, cur, ctx, mustEdit = tooBig)
    }

    /** Repairs a file the checker found problems in, changing only what is needed. */
    private suspend fun fixFile(projectId: String, path: String, content: String, problems: List<String>, request: String): String? {
        val big = content.length > FULL_FILE_LIMIT
        val shown = if (big) visibleHead(content).first else content
        val ctx = "What the user asked:\n$request\n\nThis file has problems that would break the build:\n" +
            problems.joinToString("\n") { "- $it" } +
            "\n\nFix only these problems. Current content of $path" + (if (big) " (INCOMPLETE, edit blocks only)" else "") +
            ":\n```\n$shown\n```\nSend edit blocks (preferred) or the complete file."
        return produce(projectId, path, content, ctx, mustEdit = big)
    }

    /**
     * A Kotlin file longer than the limit cannot be rewritten safely. The AI picks a grouping and the app moves the
     * declarations into new files (no code is retyped); the shorter original is then written as usual.
     */
    private suspend fun splitIfBig(projectId: String, path: String, current: MutableMap<String, String>, written: MutableMap<String, String>) {
        val text = written[path] ?: current[path] ?: return
        if (text.length <= FULL_FILE_LIMIT) return
        val name = path.substringAfterLast('/')
        val outline = Splitter.outline(text)
        if (outline.size < 2) {
            addMsg(projectId, "note", "$name is very long and could not be split automatically.")
            return
        }
        val list = outline.joinToString("\n") { "${it.name} | ${it.kind} | lines ${it.firstLine}-${it.lastLine}" }
        val r = askModel(projectId, AiClient.SYS_SPLIT, listOf("user" to "File: $name (${text.lines().size} lines)\nDeclarations:\n$list")) ?: return
        val taken = (current.keys + written.keys).map { it.substringAfterLast('/') }.toSet()
        val groups = Splitter.parseGroups(r.text, outline.map { it.name }.toSet(), taken, name)
        if (groups.isEmpty()) {
            addMsg(projectId, "note", "$name is very long and the AI gave no usable way to split it.")
            return
        }
        val res = Splitter.split(text, path, groups)
        if (res.error != null) {
            addMsg(projectId, "note", "Could not split $name: ${res.error}")
            return
        }
        current[path] = res.original
        written[path] = res.original
        res.created.forEach { (p, c) -> current[p] = c; written[p] = c }
        addMsg(projectId, "note", "Split $name: moved ${res.moved.joinToString()} into ${res.created.keys.joinToString { it.substringAfterLast('/') }}.")
    }

    /** Saves the files as one version, adds a card per changed file to the chat, and returns the version. */
    private suspend fun commit(projectId: String, files: List<Pair<String, String>>, deletes: List<String>, request: String): Int? {
        val old = dao.filesOnce(projectId).filter { it.deletedAt == null }.associate { it.path to it.content }
        val changed = files.filter { old[it.first] != it.second }
        val saved = saveFiles(projectId, files, request, activeName, deletes)
        if (saved == null) {
            addMsg(projectId, "note", "Nothing changed: the files are already up to date.")
            return null
        }
        changed.forEach { (path, _) -> addMsg(projectId, "note", Notes.file(if (path in old) "updated" else "created", path, fileDescs[path].orEmpty())) }
        deletes.forEach { addMsg(projectId, "note", Notes.file("deleted", it, "")) }
        addMsg(projectId, "note", "Saved ${saved.first} file${if (saved.first == 1) "" else "s"} · v${saved.second}")
        return saved.second
    }

    /** Closing note: what was done, the commit message for GitHub, and the updated project memory (returned). */
    private suspend fun summarize(
        projectId: String, request: String, plan: Plan, changed: List<String>, deleted: List<String>,
        fixRounds: Int, warnings: List<String>, version: Int, memory: String, brief: String, recent: List<String>,
    ): SummaryOut? {
        val prompt = buildString {
            append("User request:\n").append(request).append("\n\nPlan: ").append(plan.summary)
            append("\nFiles changed: ").append(changed.joinToString(", ").ifBlank { "none" })
            append("\nFiles deleted: ").append(deleted.joinToString(", ").ifBlank { "none" })
            append("\nCode problems found and fixed automatically before saving: ")
            append(if (fixRounds > 0) "yes, in $fixRounds round(s)" else "none")
            append("\nRemaining warnings: ").append(if (warnings.isEmpty()) "none" else warnings.take(3).joinToString("; "))
            append("\n\nCurrent memory:\n").append(memory.ifBlank { "(empty)" })
            if (brief.isNotBlank()) append("\n\nApproved plan:\n").append(brief.take(1800))
            append("\n\nRecent user messages:\n").append(recent.joinToString("\n") { "- " + it.take(200) })
        }
        val r = askModel(projectId, AiClient.SYS_SUMMARY, listOf("user" to prompt), quiet = true) ?: return null
        val sec = BrainOps.sections(r.text)
        val commitLine = sec["COMMIT"]?.lineSequence()?.firstOrNull()?.trim()?.trim('"', '`')?.take(72)
        val summary = sec["SUMMARY"]
        if (!summary.isNullOrBlank()) addMsg(projectId, "assistant", summary)
        if (!commitLine.isNullOrBlank()) {
            dao.checkpointsOnce(projectId).firstOrNull { it.version == version }?.let { dao.putCheckpoint(it.copy(title = commitLine, dirty = true)) }
        }
        return SummaryOut(sec["MEMORY"]?.takeIf { it.isNotBlank() }?.take(1500), sec["PLAN"]?.takeIf { BrainOps.parsePhases(it).isNotEmpty() })
    }

    /** A memory refresh after a few answers that changed no code. */
    private suspend fun refreshMemory(projectId: String, brain: Brain, recent: List<Pair<String, String>>): Brain {
        val prompt = "Current memory:\n" + brain.memory.ifBlank { "(empty)" } + "\n\nRecent conversation:\n" +
            recent.joinToString("\n") { "${it.first}: ${it.second.take(300)}" }
        val r = askModel(projectId, AiClient.SYS_MEMORY, listOf("user" to prompt), quiet = true) ?: return brain.copy(sinceMemory = 0)
        val t = r.text.trim().removePrefix("MEMORY:").trim()
        return brain.copy(memory = if (t.isBlank()) brain.memory else t.take(1500), sinceMemory = 0)
    }

    /** A new app starts with a plan and a few questions. Nothing is built until the user has answered. */
    private suspend fun briefFlow(projectId: String, history: List<Pair<String, String>>, brain: Brain) {
        phase(0, 2); phase(2, 1, "drafting the plan")
        val r = askModel(projectId, AiClient.SYS_BRIEF, history) ?: return
        phase(2, 2)
        val text = r.text.trim()
        val phases = BrainOps.parsePhases(text)
        if (phases.isEmpty()) {
            addMsg(projectId, "assistant", text.ifBlank { "(empty reply)" })
            alert(NoticeKind.REPLY, projectId, text)
            return
        }
        saveBrain(projectId, brain.copy(planState = "draft", planRounds = 0, brief = text.take(2500), roadmap = phases.map { Task(it) }))
        addMsg(projectId, "assistant", text)
        alert(NoticeKind.ASK, projectId, text.substringAfter("QUESTIONS:", text))
    }

    /** The user answered the questions: fix the final plan, save it as the project's memory, and let the work start. */
    private suspend fun finalizeFlow(projectId: String, brain: Brain, userText: String, bn: Boolean): Brain? {
        phase(0, 2); phase(2, 1, "fixing the plan with your answers")
        val prompt = buildString {
            append("The plan that was proposed to the user:\n").append(brain.brief)
            append("\n\nThe user's answer:\n").append(userText)
            if (brain.planRounds >= 2) append("\n\nYou have already asked twice: STATUS must be READY now. Use your suggestions for anything unanswered.")
        }
        val r = askModel(projectId, AiClient.SYS_FINALIZE, listOf("user" to prompt)) ?: return null
        phase(2, 2)
        val status = BrainOps.status(r.text)
        val body = r.text.lines().dropWhile { it.isBlank() || it.trim().startsWith("STATUS:", ignoreCase = true) }.joinToString("\n").trim()
        val phases = BrainOps.parsePhases(body)
        if (status == "CANCEL") {
            addMsg(projectId, "assistant", body.ifBlank { if (bn) "ঠিক আছে, বাদ দিলাম।" else "Okay, I dropped it." })
            saveBrain(projectId, brain.copy(planState = "", planRounds = 0, brief = "", roadmap = emptyList()))
            return null
        }
        if (status == "ASK" || (status == null && phases.isEmpty())) {
            addMsg(projectId, "assistant", body.ifBlank { r.text.trim().ifBlank { "(empty reply)" } })
            saveBrain(projectId, brain.copy(planRounds = brain.planRounds + 1))
            alert(NoticeKind.ASK, projectId, body.ifBlank { r.text })
            return null
        }
        val lines = BrainOps.planLines(body).ifBlank { body }
        BrainOps.field(lines, "NAME")?.let { BrainOps.cleanName(it) }?.takeIf { it.isNotBlank() }?.let { name ->
            dao.project(projectId)?.let { dao.putProject(it.copy(name = name, updatedAt = System.currentTimeMillis(), dirty = true)) }
        }
        val ready = brain.copy(
            planState = "ready", planRounds = 0, brief = lines.take(2500), roadmap = phases.map { Task(it) },
            memory = BrainOps.memoryFromPlan(lines, bn), active = if (phases.isNotEmpty()) 0 else -1,
        )
        saveBrain(projectId, ready)
        addMsg(projectId, "assistant", body)
        return ready
    }

    /** Words that suggest the user is reporting something broken rather than asking a question. */
    private fun looksLikeProblem(t: String): Boolean {
        val l = t.lowercase()
        return listOf(
            "সমস্যা", "দেখা যায় না", "দেখায় না", "দেখাচ্ছে না", "হচ্ছে না", "কাজ করছে না", "কাজ করে না", "ভুল", "ঠিক করো", "ঠিক কর",
            "not working", "doesn't", "does not", "isn't", "problem", "issue", "bug", "error", "broken", "fix",
        ).any { it in l }
    }

    /** A short "ok / do it / দাও" answer to something the AI proposed. */
    private fun looksLikeConfirmation(t: String): Boolean {
        val l = t.trim().lowercase()
        return l.length <= 24 && listOf("দাও", "করো", "কর", "মুছো", "মুছে", "ঠিক আছে", "ওকে", "হ্যাঁ", "ok", "okay", "yes", "do it", "go ahead").any { it in l }
    }

    /** Notes the app wrote between the user's previous message and the latest one: what really happened. */
    private fun eventsBefore(all: List<MessageEntity>): List<String> {
        val last = all.indexOfLast { it.role == "user" }
        if (last <= 0) return emptyList()
        val prev = all.subList(0, last).indexOfLast { it.role == "user" }
        return all.subList(prev + 1, last).filter { it.role == "note" && !it.content.startsWith("FILE|") }.map { Notes.plain(it.content).take(200) }.takeLast(8)
    }

    private suspend fun reply(projectId: String) {
        thinkingId = projectId
        lastNote = null
        resetPhases(); phase(0, 1, "your request and project files")
        lastErr = null; deferErr = false; fileDescs = emptyMap(); lastAssistant = null
        try {
            val all = dao.messagesOnce(projectId)
            val rawTyped = all.lastOrNull { it.role == "user" }?.content ?: ""
            val replied = Quoting.decode(rawTyped)           // the user swiped to reply to an earlier message
            val typed = replied?.text ?: rawTyped
            var request = if (replied != null) Quoting.forAi(replied) else typed
            var origRequest = typed
            var brain = loadBrain(projectId)
            val bn = Intents.isBengali(typed)
            runBn = bn
            runName = dao.project(projectId)?.name ?: "Vibecraft"
            maybeAskNotifyPermission()

            // 1. "What happened?" is answered from what the app really recorded: instant, exact, no AI call.
            if (Intents.quickStatus(typed) && brain.last != null && replied == null) {
                addMsg(projectId, "assistant", BrainOps.statusText(brain, bn))
                return
            }
            // 2a. The plan was shown with questions: this message is the user's answer. Fix the plan, save it, then build.
            var finalized = false
            if (brain.planState == "draft") {
                val ready = finalizeFlow(projectId, brain, typed, bn) ?: return
                brain = ready
                finalized = true
                request = "Build the app now, following the approved plan. Start with phase 1."
                origRequest = ready.roadmap.firstOrNull()?.title ?: typed
            }
            // 2b. The AI asked something before acting (a plan change or an important decision): this message is the answer.
            var askRounds = 0
            val pend = brain.pending
            if (!finalized && pend != null) {
                askRounds = pend.rounds
                request = "Earlier request: ${pend.request}\nYou asked the user: ${pend.question}\nThe user answered: $typed\n" +
                    "Now carry out the request according to the answer. " +
                    if (pend.rounds >= 2) "Do not ask again: decide yourself." else "Do not ask the same thing again."
                origRequest = pend.request
                brain = brain.copy(pending = null)
            }
            // 2c. A bare "ok / continue / fix it" picks up the unfinished work instead of starting over.
            val next = BrainOps.next(brain)
            val resuming = !finalized && pend == null && next != null && replied == null && Intents.quickResume(typed)
            if (resuming && next != null) {
                origRequest = brain.last?.request ?: typed
                request = "Continue the unfinished task: ${next.title}" + (brain.last?.request?.let { ". Original request: $it" } ?: "")
                if (next.roadmapIndex != null) brain = brain.copy(active = next.roadmapIndex)
                addMsg(projectId, "assistant", if (bn) "চালিয়ে যাচ্ছি: ${next.title}" else "Continuing: ${next.title}")
            }
            val history = all.filter { it.role == "user" || it.role == "assistant" }.takeLast(8).map { it.role to Quoting.forAiText(it.content) }
            val hist = if (request != typed && history.isNotEmpty()) history.dropLast(1) + ("user" to request) else history
            val recentUser = all.filter { it.role == "user" }.takeLast(3).map { Quoting.decode(it.content)?.text ?: it.content }
            val current = dao.filesOnce(projectId).filter { it.deletedAt == null && !it.path.startsWith(".vibecraft/") }
                .associate { it.path to it.content }.toMutableMap()
            val baseHash = current.mapValues { Manifest.hash(it.value) }
            val isNewApp = current.keys.none { it.endsWith(".kt") }

            // 3. A new app starts with a short plan the user approves, so later steps stay consistent with it.
            if (planFirst && isNewApp && brain.planState.isEmpty() && brain.brief.isBlank() && next == null && request.length >= 15 && !Intents.skipPlan(request)) {
                briefFlow(projectId, history, brain)
                return
            }

            planText = if (brain.planState != "draft") BrainOps.planLines(brain.brief).lines().filter { !it.trim().startsWith("PHASE", ignoreCase = true) }.joinToString("\n").take(1000) else ""
            val events = eventsBefore(all)
            val sys = AiClient.SYS_PLAN + "\n\n" + BrainOps.render(brain) + "\n\n" +
                (if (events.isEmpty()) "" else "App events since your last answer (what really happened):\n" + events.joinToString("\n") { "- $it" } + "\n\n") +
                contextOf(current, request, recentUser)
            phase(0, 2); phase(1, 1, "waiting for ${activeName ?: "the model"}")
            val planReply = askModel(projectId, sys, hist) ?: return
            var text = planReply.text
            var plan = PlanParser.parse(text)
            var intent = Intents.parseLine(text)

            // The AI asked to look at files (or parts of long files) first: give it exactly that and let it plan again.
            if (plan.files.isEmpty() && plan.deletes.isEmpty() && plan.reads.isNotEmpty()) {
                phase(1, 2); phase(0, 1, "${plan.reads.size} file${if (plan.reads.size == 1) "" else "s"} you asked for")
                val chunks = plan.reads.mapNotNull { r -> current[r.path]?.let { Reader.chunk(r.path, it, r.from, r.to) } }.joinToString("\n\n")
                phase(0, 2); phase(1, 1, "waiting for ${activeName ?: "the model"}")
                val second = askModel(projectId, sys + "\n\nFiles you asked to read:\n" + chunks.ifBlank { "(none of those files exist)" }, hist) ?: return
                text = second.text
                plan = PlanParser.parse(text)
                intent = Intents.parseLine(text)
            }
            // "Check it" never changes files: if the AI planned changes anyway, ask for the check result instead.
            val reviewOnly = Intents.looksLikeReview(typed) && !resuming && !finalized && pend == null
            if (reviewOnly && (plan.files.isNotEmpty() || plan.deletes.isNotEmpty())) {
                val again = askModel(
                    projectId, sys,
                    hist + ("assistant" to text) +
                        ("user" to "The user only asked to check or review. Start with INTENT: ANSWER, tell in a few sentences what you found, and change nothing."),
                )
                text = again?.text ?: ""
                plan = PlanParser.parse(text)
                intent = Intent.ANSWER
            }
            // A problem report or a confirmation answered with advice only: ask once more for the work itself.
            if (!reviewOnly && intent != Intent.ASK && plan.files.isEmpty() && plan.deletes.isEmpty() && plan.reads.isEmpty() &&
                CodeBlocks.files(text).isEmpty() && (looksLikeProblem(typed) || looksLikeConfirmation(typed))
            ) {
                val again = askModel(
                    projectId, sys,
                    hist + ("assistant" to text) +
                        ("user" to if (looksLikeProblem(typed)) "This is a problem report. Find the cause in the project files and fix it now: start with INTENT: ACT and reply in the FILE format, or with READ lines if you need a file."
                        else "The user confirmed. Do now what you proposed: start with INTENT: ACT and reply in the FILE / DELETE format. If nothing needs to change, say so in one sentence."),
                )
                if (again != null) { text = again.text; plan = PlanParser.parse(text); intent = Intents.parseLine(text) }
            }

            // The AI wants the user to decide or know something before it acts: it asks, and waits for the answer.
            if (intent == Intent.ASK && plan.files.isEmpty() && plan.deletes.isEmpty() && askRounds < 2) {
                val ask = Intents.stripControl(text).ifBlank {
                    if (bn) "কাজ শুরুর আগে একটা সিদ্ধান্ত দরকার। কীভাবে এগোব?" else "I need your decision before I start. How should I go on?"
                }
                addMsg(projectId, "assistant", ask)
                brain = brain.copy(pending = Pending(origRequest, ask.take(600), askRounds + 1), sinceMemory = brain.sinceMemory + 1)
                saveBrain(projectId, brain)
                alert(NoticeKind.ASK, projectId, ask)
                return
            }
            if (intent == Intent.ANSWER || (plan.files.isEmpty() && plan.deletes.isEmpty())) {
                // An answer, or a model that ignored the format and sent code blocks anyway.
                val found = if (intent == Intent.ANSWER) emptyList() else CodeBlocks.files(text).filter { Protect.canWrite(it.first) }
                val fallback = if (bn) "দেখেছি, কিছু বদলাইনি।" else "Checked; nothing was changed."
                val shown = if (found.isNotEmpty()) CodeBlocks.stripBlocks(text).ifBlank { "Done." } else Intents.stripControl(text).ifBlank { fallback }
                addMsg(projectId, "assistant", shown)
                if (found.isNotEmpty()) commit(projectId, found, emptyList(), origRequest)
                alert(if (found.isNotEmpty()) NoticeKind.DONE else NoticeKind.REPLY, projectId, shown, if (found.isNotEmpty()) "Code updated" else "")
                brain = brain.copy(sinceMemory = brain.sinceMemory + 1)
                if (brain.sinceMemory >= 4) brain = refreshMemory(projectId, brain, history + ("assistant" to shown))
                saveBrain(projectId, brain)
                return
            }

            // App name: the AI picks one for a new app (or when asked to rename).
            if (plan.appName.isNotBlank()) {
                dao.project(projectId)?.let {
                    dao.putProject(it.copy(name = plan.appName, updatedAt = System.currentTimeMillis(), dirty = true))
                }
            }
            // A new app always gets its own icon; the design tokens come first and the preview goes last.
            val iconHint = BrainOps.field(brain.memory, "ICON")?.let { ": $it" } ?: ""
            val designHint = BrainOps.field(brain.memory, "DESIGN")?.let { " (" + it.take(120) + ")" } ?: ""
            val icons = if (isNewApp) {
                listOf(Template.ICON_FG to "app icon artwork$iconHint", Template.ICON_COLORS to "icon background color$iconHint")
                    .filter { (p, _) -> plan.files.none { it.path == p } }.map { PlanFile(it.first, it.second) }
            } else emptyList()
            val designPlan = plan.files.firstOrNull { it.path == "design.json" }
                ?: if (isNewApp) PlanFile("design.json", "design tokens: colors, text sizes, spacing, corners$designHint") else null
            val previewPlan = plan.files.firstOrNull { it.path == "preview.html" }
            var files = listOfNotNull(designPlan) + plan.files.filter { it.path != "preview.html" && it.path != "design.json" } + icons
            val hasPreview = current.containsKey("preview.html") || current.containsKey("preview/index.html") || files.any { it.path.startsWith("preview/") }
            if (previewPlan != null || (!hasPreview && files.any { it.path.endsWith(".kt") })) {
                files = files + (previewPlan ?: PlanFile("preview.html", "design preview of the main screen"))
            }
            plan = plan.copy(files = files)
            val wantedDeletes = plan.deletes.filter { it in current }
            plan.deletes.filter { it !in current }.forEach {
                addMsg(projectId, "note", "${it.substringAfterLast('/')} is not in this project. If it only exists in your GitHub repository, it is removed automatically on the next publish.")
            }

            val total = files.size + wantedDeletes.size
            phase(1, 2); phase(2, 1, "$total change${if (total == 1) "" else "s"} to make")
            addMsg(projectId, "assistant", plan.summary.ifBlank { "I will change $total files." })
            delay(450)
            phase(2, 2)
            fileDescs = files.associate { it.path to it.desc }

            // The work is recorded as tasks, so a failure leaves something the user can simply continue.
            brain = brain.copy(
                tasks = files.map { Task(("Write ${it.path.substringAfterLast('/')}: ${it.desc}").trim().trimEnd(':', ' ')) } +
                    wantedDeletes.map { Task("Delete ${it.substringAfterLast('/')}") },
            )
            saveBrain(projectId, brain)

            val written = linkedMapOf<String, String>()
            val results = mutableListOf<String>()
            var failedAt: String? = null
            deferErr = true
            for ((i, pf) in files.withIndex()) {
                phProgress = i.toFloat() / files.size
                phase(3, 1, "${pf.path.substringAfterLast('/')} · ${i + 1} of ${files.size}")
                if (pf.path.endsWith(".kt")) splitIfBig(projectId, pf.path, current, written)
                val out = writeFile(projectId, pf, plan, request, current, written)
                val name = pf.path.substringAfterLast('/')
                if (out == null) {
                    phase(3, 3, "stopped at $name")
                    brain = brain.copy(tasks = brain.tasks.mapIndexed { j, t -> if (j == i) t.copy(state = TaskState.FAILED, note = lastNote ?: "") else t })
                    results.add("$name: failed")
                    if (pf.path == "preview.html") continue   // only the sketch failed; the app files are still saved
                    failedAt = name
                    break
                }
                written[pf.path] = out
                brain = brain.copy(tasks = brain.tasks.mapIndexed { j, t -> if (j == i) t.copy(state = TaskState.DONE) else t })
                results.add("$name: saved")
                phProgress = (i + 1f) / files.size
                pushPhases()
            }
            if (failedAt == null) phase(3, 2)

            // Deleting is destructive: keep a file when something that stays still uses it.
            val after = current + written
            val deletes = mutableListOf<String>()
            for ((k, d) in wantedDeletes.withIndex()) {
                val blockers = Protect.deleteBlockers(d, after).filter { it !in wantedDeletes }
                val ti = files.size + k
                if (blockers.isEmpty()) {
                    deletes.add(d)
                    brain = brain.copy(tasks = brain.tasks.mapIndexed { j, t -> if (j == ti) t.copy(state = TaskState.DONE) else t })
                    results.add("${d.substringAfterLast('/')}: deleted")
                } else {
                    val why = "still used by ${blockers.take(3).joinToString { it.substringAfterLast('/') }}"
                    addMsg(projectId, "note", "Did not delete ${d.substringAfterLast('/')}: $why.")
                    brain = brain.copy(tasks = brain.tasks.mapIndexed { j, t -> if (j == ti) t.copy(state = TaskState.FAILED, note = why) else t })
                    results.add("${d.substringAfterLast('/')}: kept ($why)")
                }
            }

            // Check before saving: fix what the checker finds, at most two rounds.
            var fixRounds = 0
            var warnings = emptyList<String>()
            if (written.isNotEmpty()) {
                phase(4, 1, "looking for code problems")
                fun state() = current.filterKeys { it !in deletes } + written
                var issues = Lint.check(state())
                while (issues.isNotEmpty() && fixRounds < 2) {
                    val byPath = issues.filter { it.path in written }.groupBy { it.path }
                    if (byPath.isEmpty()) break
                    fixRounds++
                    for ((p, list) in byPath.entries.take(3)) {
                        val fixed = fixFile(projectId, p, written.getValue(p), list.map { it.text }, request) ?: continue
                        written[p] = fixed
                    }
                    issues = Lint.check(state())
                }
                warnings = issues.map { "${it.path.substringAfterLast('/')}: ${it.text}" }
                phase(4, if (warnings.isEmpty()) 2 else 3, if (warnings.isEmpty()) null else "${warnings.size} warning${if (warnings.size == 1) "" else "s"}")
            }

            // Stale check: a file that changed while the AI worked on it must not be overwritten.
            val latest = dao.filesOnce(projectId).filter { it.deletedAt == null }.associate { it.path to it.content }
            val stale = written.keys.filter { k -> baseHash[k] != null && latest[k] != null && Manifest.hash(latest.getValue(k)) != baseHash.getValue(k) }
            if (stale.isNotEmpty()) {
                stale.forEach { written.remove(it) }
                addMsg(projectId, "note", "Skipped ${stale.joinToString { it.substringAfterLast('/') }}: it changed while the AI was working.")
            }

            var version: Int? = null
            var newMemory: String? = null
            var newPlan: String? = null
            if (written.isNotEmpty() || deletes.isNotEmpty()) {
                phase(5, 1, "your changes")
                version = commit(projectId, written.toList(), deletes, origRequest)
                phase(5, 2)
                if (warnings.isNotEmpty()) {
                    addMsg(projectId, "note", "Saved with warnings: " + warnings.take(3).joinToString("; "))
                }
                if (version != null) {
                    val so = summarize(projectId, request, plan, written.keys.toList(), deletes, fixRounds, warnings, version, brain.memory, brain.brief, recentUser)
                    newMemory = so?.memory
                    newPlan = so?.plan
                }
            }
            val e = lastErr
            if (failedAt != null) {
                val pp = Problem(e?.kind ?: "other", e?.title ?: "Writing stopped at $failedAt", e?.sub ?: "", e?.raw ?: "", written.size, files.size, failedAt)
                addMsg(projectId, "note", Notes.pause(pp.kind, pp.title, pp.sub, pp.raw, pp.done, pp.total, pp.stoppedAt))
                alert(NoticeKind.PROBLEM, projectId, Notes.problemText(pp))
            } else if (e != null && results.any { it.endsWith("failed") }) {
                addMsg(projectId, "note", Notes.err(e.kind, e.title, e.sub, e.raw))
                alert(NoticeKind.PROBLEM, projectId, e.title + if (e.sub.isBlank()) "" else ". " + e.sub)
            }

            // What the app records here is what "what happened?" is answered from, and what "continue" resumes.
            val problem = failedAt?.let { "stopped at $it: ${lastNote ?: "unknown reason"}" }
                ?: if (results.any { it.endsWith("failed") }) lastNote else null
            val left = brain.tasks.filter { it.state != TaskState.DONE }
            var roadmap = brain.roadmap
            var active = brain.active
            if (left.isEmpty() && active >= 0 && active < roadmap.size && failedAt == null) {
                roadmap = roadmap.mapIndexed { j, t -> if (j == active) t.copy(state = TaskState.DONE) else t }
                active = -1
            }
            // A plan that changed during the task replaces the old one; phases already done stay done.
            var briefText = brain.brief
            newPlan?.let { np ->
                val lines = BrainOps.planLines(np)
                if (lines.isNotBlank()) {
                    briefText = lines.take(2500)
                    roadmap = BrainOps.mergeRoadmap(roadmap, BrainOps.parsePhases(np))
                    if (active >= roadmap.size) active = -1
                }
            }
            brain = brain.copy(
                tasks = left, roadmap = roadmap, active = active, brief = briefText,
                memory = newMemory ?: brain.memory,
                sinceMemory = if (newMemory != null) 0 else brain.sinceMemory + 1,
                last = RunReport(origRequest.take(200), plan.summary.take(200), results, version, problem, System.currentTimeMillis()),
            )
            saveBrain(projectId, brain)
            if (failedAt == null) {
                val n = written.size + deletes.size
                if (version != null) {
                    alert(NoticeKind.DONE, projectId, lastAssistant ?: plan.summary, "$n change${if (n == 1) "" else "s"} saved · v$version")
                } else {
                    alert(NoticeKind.REPLY, projectId, lastAssistant ?: plan.summary)
                }
            }
        } finally {
            thinkingId = null
            steps = emptyList()
            deferErr = false
            fileDescs = emptyMap()
            planText = ""
            if (serviceOn) { serviceOn = false; Notifier.runEnded(appCtx) }
        }
    }

    // ---- project files, history, preview ----
    fun files(id: String) = dao.files(id)
    fun checkpoints(id: String) = dao.checkpoints(id)

    /** Writes changed files, soft-deletes removed ones, and records one checkpoint. Null when nothing changed. */
    private suspend fun saveFiles(
        projectId: String, found: List<Pair<String, String>>, title: String, model: String?, deletes: List<String> = emptyList(),
    ): Pair<Int, Int>? {
        if (found.isEmpty() && deletes.isEmpty()) return null
        val now = System.currentTimeMillis()
        val rows = dao.filesOnce(projectId)
        val existing = rows.associateBy { it.path }
        val changed = found.filter { (path, content) ->
            val old = existing[path]
            old == null || old.deletedAt != null || old.content != content
        }
        val gone = deletes.filter { existing[it]?.deletedAt == null && existing.containsKey(it) }
        if (changed.isEmpty() && gone.isEmpty()) return null
        changed.forEach { (path, content) ->
            dao.putFile(FileEntity(CodeBlocks.fileId(projectId, path), projectId, path, content, now))
        }
        gone.forEach { path -> existing[path]?.let { dao.putFile(it.copy(deletedAt = now, updatedAt = now, dirty = true)) } }
        val v = (dao.maxVersion(projectId) ?: 0) + 1
        val changes = History.changes(changed.toMap(), gone)
        dao.putCheckpoint(
            CheckpointEntity(CodeBlocks.checkpointId(projectId, v), projectId, v, title.take(60), model, changes, now)
        )
        return (changed.size + gone.size) to v
    }

    /** Puts the project back to how it was at [version]; the restore itself becomes a new version. */
    fun restore(projectId: String, version: Int) {
        viewModelScope.launch {
            val state = History.replay(dao.checkpointsOnce(projectId).map { it.version to it.changes }, version)
            val now = System.currentTimeMillis()
            dao.filesOnce(projectId).forEach { f ->
                if (f.deletedAt == null && f.path !in state && !f.path.startsWith(".vibecraft/")) dao.putFile(f.copy(deletedAt = now, updatedAt = now, dirty = true))
            }
            state.forEach { (path, content) ->
                dao.putFile(FileEntity(CodeBlocks.fileId(projectId, path), projectId, path, content, now))
            }
            val v = (dao.maxVersion(projectId) ?: 0) + 1
            val full = History.changes(state, full = true)
            dao.putCheckpoint(
                CheckpointEntity(CodeBlocks.checkpointId(projectId, v), projectId, v, "Restored v$version", null, full, now)
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

    /** True when a GitHub token is saved (it is entered in Settings, not on the Publish page). */
    var ghSaved by mutableStateOf(store.ghToken() != null); private set

    /** Returns an error text, or null when the token was saved. */
    fun saveGhToken(t: String): String? {
        if (t.isBlank()) return "Paste the token first."
        if (!store.setGhToken(t.trim())) return "Secure storage is not available on this phone, so the token cannot be saved."
        ghSaved = true
        return null
    }

    fun clearGhToken() { store.clearGhToken(); ghSaved = false }
    fun ghRepo(id: String) = prefs.getString("gh_repo_$id", "") ?: ""
    fun ghBranch(id: String) = prefs.getString("gh_branch_$id", "main") ?: "main"

    fun lastPublished(id: String) = prefs.getInt("gh_pub_ver_$id", 0)

    // ---- preview simulation settings ----
    var previewDevice by mutableStateOf(prefs.getInt("pv_dev", 1)); private set
    var previewDark by mutableStateOf(prefs.getBoolean("pv_dark", false)); private set
    var previewButtons by mutableStateOf(prefs.getBoolean("pv_btn", false)); private set

    fun setPreview(device: Int? = null, dark: Boolean? = null, buttons: Boolean? = null) {
        device?.let { previewDevice = it }
        dark?.let { previewDark = it }
        buttons?.let { previewButtons = it }
        prefs.edit().putInt("pv_dev", previewDevice).putBoolean("pv_dark", previewDark).putBoolean("pv_btn", previewButtons).apply()
    }

    var buildReport by mutableStateOf<BuildReport?>(null); private set
    var repairInfo by mutableStateOf<String?>(null); private set
    var repairMax by mutableStateOf(prefs.getInt("repair_max", 2)); private set

    @JvmName("updateRepairMax")
    fun setRepairMax(n: Int) {
        repairMax = n.coerceIn(0, 3)
        prefs.edit().putInt("repair_max", repairMax).apply()
    }

    /** Sends the project (plus Vibecraft's build files) to GitHub and returns the commit. */
    private suspend fun pushNow(projectId: String, token: String, repo: String, branch: String, message: String): String {
        val proj = dao.project(projectId) ?: error("Open a project first.")
        val rows = dao.filesOnce(projectId)
        val own = rows.filter { it.deletedAt == null && !it.path.startsWith(".vibecraft/") }.associate { it.path to it.content }
        val gone = rows.filter { it.deletedAt != null && it.path !in own }.map { it.path }.toSet()
        val sha = GitHub.push(
            token, repo.trim(), branch.trim(), message.ifBlank { "Update from Vibecraft" },
            Template.files(proj.name, projectId, own["design.json"]) + own, gone, listOf("${CodeBlocks.GEN}/", "app/src/main/res/"),
        )
        if (GitHub.lastPruned.isNotEmpty()) {
            addMsg(projectId, "note", "Removed leftover files from the repository: " + GitHub.lastPruned.take(5).joinToString { it.substringAfterLast('/') })
        }
        prefs.edit().putString("gh_repo_$projectId", repo.trim()).putString("gh_branch_$projectId", branch.trim())
            .putInt("gh_pub_ver_$projectId", dao.maxVersion(projectId) ?: 0).apply()
        return sha
    }

    private fun window(text: String, line: Int?, radius: Int): String {
        val ls = text.lines()
        val c = (line ?: 1).coerceIn(1, ls.size)
        val from = maxOf(1, c - radius)
        val to = minOf(ls.size, c + radius)
        return (from..to).joinToString("\n") { "%4d| %s".format(it, ls[it - 1]) }
    }

    /** One repair round: fix the files named in the errors, check them, and save a version. False when nothing safe came out. */
    private suspend fun repairOnce(projectId: String, errors: List<BuildError>, round: Int): Boolean {
        val files = dao.filesOnce(projectId).filter { it.deletedAt == null }.associate { it.path to it.content }
        val byFile = errors.filter { it.file != null && files.containsKey(it.file) && Protect.canWrite(it.file) }.groupBy { it.file!! }
        if (byFile.isEmpty()) return false
        val fixed = linkedMapOf<String, String>()
        for ((path, errs) in byFile.entries.take(3)) {
            val cur = files.getValue(path)
            val big = cur.length > FULL_FILE_LIMIT
            val ctx = buildString {
                append("The GitHub build failed. Fix these compiler errors in ").append(path).append(". Change only what is needed.\n\n")
                append("What every project file declares (use these exact names):\n").append(Manifest.render(Manifest.build(files))).append("\n\nErrors:\n")
                errs.forEach { e ->
                    append("- line ").append(e.line ?: "?").append(": ").append(e.message).append('\n')
                    e.context.forEach { append("    ").append(it).append('\n') }
                }
                append("\nCode around the first error (line numbers are for reference only and are not part of the code):\n")
                append(window(cur, errs.first().line, 8))
                append("\n\nCurrent content of ").append(path).append(if (big) " (INCOMPLETE, edit blocks only)" else "").append(":\n```\n")
                append(if (big) visibleHead(cur).first else cur).append("\n```")
            }
            produce(projectId, path, cur, ctx, mustEdit = big)?.let { fixed[path] = it }
        }
        if (fixed.isEmpty()) return false
        // Check before anything is pushed.
        var issues = Lint.check(files + fixed).filter { it.path in fixed }
        if (issues.isNotEmpty()) {
            for ((p, list) in issues.groupBy { it.path }.entries.take(3)) {
                fixFile(projectId, p, fixed.getValue(p), list.map { it.text }, "Fix the build errors")?.let { fixed[p] = it }
            }
            issues = Lint.check(files + fixed).filter { it.path in fixed }
            if (issues.isNotEmpty()) return false
        }
        val saved = saveFiles(projectId, fixed.toList(), "Fix build errors (round $round)", activeName) ?: return false
        fixed.keys.forEach { addMsg(projectId, "note", Notes.file("updated", it, "")) }
        addMsg(projectId, "note", "Auto-fixed build errors · v${saved.second}")
        return true
    }

    fun publish(projectId: String, repo: String, branch: String, commitMsg: String, tokenInput: String) {
        viewModelScope.launch {
            pubStage = 1; pubInfo = null; runUrl = null; buildErrors = null; buildReport = null; repairInfo = null
            try {
                if (tokenInput.isNotBlank() && !store.setGhToken(tokenInput.trim())) {
                    error("Secure storage is not available on this phone, so the token cannot be saved.")
                }
                val token = store.ghToken() ?: error("Enter your GitHub token.")
                var sha = pushNow(projectId, token, repo, branch, commitMsg)
                val startCalls = aiCalls
                var round = 0
                while (true) {
                    pubStage = 2
                    val run = GitHub.waitForRun(token, repo.trim(), sha)
                    runUrl = run.url
                    val report = GitHub.analyze(token, repo.trim(), run)
                    buildReport = report
                    if (report.status == BuildStatus.SUCCESS) { pubStage = 3; repairInfo = null; break }
                    buildErrors = BuildLog.render(report.errors).ifBlank { null }
                    val fixable = report.status == BuildStatus.FAILED && report.errors.any { it.file != null && Protect.canWrite(it.file) }
                    if (!fixable || round >= repairMax) {
                        pubInfo = when {
                            report.status != BuildStatus.FAILED -> report.note
                            round > 0 -> "Auto-fix stopped after $round round${if (round == 1) "" else "s"}; ${report.errors.size} error${if (report.errors.size == 1) "" else "s"} remain."
                            !fixable -> "The errors are in files Vibecraft manages, so they cannot be fixed automatically."
                            else -> "The build failed."
                        }
                        pubStage = 4; repairInfo = null; break
                    }
                    if (aiCalls - startCalls >= REPAIR_BUDGET) {
                        pubInfo = "Auto-fix stopped: the AI request budget for this task is used up."
                        pubStage = 4; repairInfo = null; break
                    }
                    round++
                    repairInfo = "Fixing build errors (round $round of $repairMax)…"
                    if (!repairOnce(projectId, report.errors, round)) {
                        pubInfo = "Auto-fix could not produce a safe change."
                        pubStage = 4; repairInfo = null; break
                    }
                    repairInfo = "Publishing the fix (round $round)…"
                    pubStage = 1
                    sha = try {
                        pushNow(projectId, token, repo, branch, "Fix build errors (round $round)")
                    } catch (e: GhError) {
                        if (e.message?.startsWith("Nothing new") == true) {
                            pubInfo = "The fix produced no new changes."
                            pubStage = 4; repairInfo = null
                            break
                        } else throw e
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val m = e.message ?: "Publishing failed."
                pubInfo = m
                pubStage = if (m.startsWith("Nothing new")) 0 else 4
                repairInfo = null
            }
        }
    }

    /** Sends the build errors back to the AI so it can fix the code. */
    fun fixBuild(projectId: String) {
        val errs = buildErrors ?: return
        send(projectId, "The GitHub build failed. Please fix these errors.\n$errs")
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
