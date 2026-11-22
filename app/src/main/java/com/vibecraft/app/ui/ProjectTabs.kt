package com.vibecraft.app.ui

import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vibecraft.app.AppVm
import com.vibecraft.app.data.FileEntity
import com.vibecraft.app.gen.BuildStatus
import com.vibecraft.app.gen.Template
import kotlinx.coroutines.flow.flowOf

@Composable
internal fun Empty(title: String, text: String) {
    val c = VCT.c
    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = c.tx, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(text, color = c.mu, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp, start = 24.dp, end = 24.dp))
    }
}

@Composable
internal fun filesOf(vm: AppVm, id: String?): List<FileEntity> {
    val list by remember(id) { if (id != null) vm.files(id) else flowOf(emptyList<FileEntity>()) }.collectAsState(initial = emptyList())
    return list
}

@Composable
fun CodeTab(vm: AppVm, projectId: String?) {
    val c = VCT.c
    var history by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<FileEntity?>(null) }
    val files = filesOf(vm, projectId)
    val cps by remember(projectId) { if (projectId != null) vm.checkpoints(projectId) else flowOf(emptyList<com.vibecraft.app.data.CheckpointEntity>()) }
        .collectAsState(initial = emptyList())
    TabPage {
        SlidingTabs(listOf("Files", "History"), if (history) 1 else 0, { history = it == 1 })
        Spacer(Modifier.height(14.dp))
        if (!history) {
            if (files.isEmpty()) Empty("No files yet", "Files the AI writes appear here.")
            else {
                VcCard(pad = 4.dp) {
                    files.forEach { f ->
                        Row(
                            Modifier.fillMaxWidth().clickable { viewing = f }.padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Ic.File, null, Modifier.size(17.dp), tint = c.mu)
                            Spacer(Modifier.width(10.dp))
                            Text(f.path.substringAfterLast('/'), color = c.tx, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Text(f.path.substringAfterLast('.', ""), color = c.mu, fontSize = 11.5.sp)
                        }
                    }
                }
                Note("Gradle and build files are added automatically when you publish.")
            }
        } else {
            if (cps.isEmpty()) Empty("No history yet", "Every AI change is saved as a version you can go back to.")
            else VcCard(pad = 14.dp) {
                val latest = cps.first().version
                cps.forEach { cp ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(if (cp.version == latest) c.ac else c.sf2),
                            Alignment.Center,
                        ) { Text("v${cp.version}", color = if (cp.version == latest) Color.White else c.mu, fontSize = 11.5.sp, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(cp.title, color = c.tx, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            if (cp.model != null) Text(cp.model, color = c.mu, fontSize = 11.5.sp)
                        }
                        if (cp.version == latest) Chip("Current", active = true)
                        else Text(
                            "Restore", color = c.tx, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, c.bd, RoundedCornerShape(10.dp))
                                .clickable { projectId?.let { vm.restore(it, cp.version) } }.padding(horizontal = 11.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
    viewing?.let { f ->
        Dialog(onDismissRequest = { viewing = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            val shape = RoundedCornerShape(16.dp)
            Column(Modifier.padding(16.dp).fillMaxWidth().heightIn(max = 600.dp).clip(shape).background(c.sf).border(1.dp, c.bd, shape).padding(16.dp)) {
                Text(f.path, color = c.tx, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(
                    f.content.take(20_000), color = c.tx, fontSize = 11.5.sp, fontFamily = FontFamily.Monospace, lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 10.dp).weight(1f, fill = false).clip(RoundedCornerShape(10.dp)).background(c.sf2)
                        .verticalScroll(rememberScrollState()).padding(12.dp),
                )
                val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    VcButton("Copy", { clipboard.setText(androidx.compose.ui.text.AnnotatedString(f.content)) }, Modifier.weight(1f), secondary = true)
                    VcButton("Close", { viewing = null }, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun PublishTab(vm: AppVm, projectId: String?, onFix: () -> Unit) {
    val c = VCT.c
    val ctx = LocalContext.current
    var repo by remember(projectId) { mutableStateOf(projectId?.let { vm.ghRepo(it) } ?: "") }
    var branch by remember(projectId) { mutableStateOf(projectId?.let { vm.ghBranch(it) } ?: "main") }
    val cps by remember(projectId) { if (projectId != null) vm.checkpoints(projectId) else flowOf(emptyList<com.vibecraft.app.data.CheckpointEntity>()) }
        .collectAsState(initial = emptyList())
    val projects by vm.projects.collectAsState()
    val slug = Template.slug(projects.firstOrNull { it.id == projectId }?.name ?: "app")
    val suggestion = projectId?.let { id ->
        val last = vm.lastPublished(id)
        cps.filter { it.version > last }.reversed().joinToString("; ") { it.title }.take(72)
    }.orEmpty().ifBlank { "Update from Vibecraft" }
    var msg by remember { mutableStateOf(suggestion) }
    var edited by remember { mutableStateOf(false) }
    LaunchedEffect(suggestion) { if (!edited) msg = suggestion }
    var token by remember { mutableStateOf("") }
    val stage = vm.pubStage
    val running = stage == 1 || stage == 2
    fun st(i: Int) = when (stage) {
        1 -> if (i == 0) 1 else 0
        2 -> if (i == 0) 2 else if (i == 1) 1 else 0
        3 -> 2
        else -> 0
    }
    TabPage {
        if (projectId == null) {
            Empty("Nothing to publish yet", "Start a chat and let the AI write your app first.")
            return@TabPage
        }
        VcCard {
            CardTitle(Ic.Up, "Commit & push")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                VcField(repo, { repo = it }, "Repository", "owner/name", mono = true, modifier = Modifier.weight(1f))
                VcField(branch, { branch = it }, "Branch", "main", mono = true, modifier = Modifier.weight(1f))
            }
            VcField(msg, { msg = it; edited = true }, "Commit message")
            VcField(
                token, { token = it }, "GitHub token",
                if (vm.hasGhToken()) "Saved. Leave blank to keep it" else "ghp_… or github_pat_…", password = true, mono = true,
            )
            Text(
                "No token yet? Tap Get token. GitHub opens with the right options already ticked (repo and workflow). " +
                    "Scroll down, press Generate token, copy it and paste it above. It stays encrypted on this phone.",
                color = c.mu, fontSize = 11.5.sp, modifier = Modifier.padding(top = 8.dp),
            )
            VcButton(
                "Get token on GitHub",
                { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/settings/tokens/new?scopes=repo,workflow&description=Vibecraft"))) },
                Modifier.padding(top = 10.dp).fillMaxWidth(), secondary = true, icon = Ic.Up,
            )
            Text(
                "Prefer limited access? Create a fine-grained token (Contents and Workflows: read and write, Actions: read)",
                color = c.ac, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
                    .clickable { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/settings/personal-access-tokens/new"))) }
                    .padding(top = 12.dp),
            )
            VcButton(
                "Commit & push", { vm.publish(projectId, repo, branch, msg, token); token = "" },
                Modifier.padding(top = 14.dp).fillMaxWidth(), enabled = !running, loading = running, icon = Ic.Up,
            )
            if (stage == 0) vm.pubInfo?.let {
                Text(it, color = c.mu, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
            }
            Text("Auto-fix build errors (rounds)", color = c.mu, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
            SlidingTabs(listOf("Off", "1", "2", "3"), vm.repairMax, { vm.setRepairMax(it) })
        }
        Spacer(Modifier.height(14.dp))
        VcCard {
            CardTitle(Ic.Code, "APK build")
            StepRow(1, "Uploading code", st(0))
            StepRow(2, "Building with Gradle", st(1))
            StepRow(3, "APK ready", st(2))
            vm.repairInfo?.let {
                Text(it, color = c.ac, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
            }
            if (stage == 3) {
                Text(
                    "The build succeeded. This only means the code compiled, not that the app works correctly: test it on your phone.\n" +
                        "Open the build on GitHub, download $slug-apk at the bottom, unzip it and install $slug.apk.",
                    color = c.mu, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
                )
                VcButton(
                    "Open build on GitHub", { vm.runUrl?.let { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) } },
                    Modifier.fillMaxWidth(), secondary = true, icon = Ic.Down,
                )
            }
            if (stage == 4) {
                val status = vm.buildReport?.status
                val title = when (status) {
                    BuildStatus.FAILED -> "Build failed"
                    BuildStatus.EXTRACTION_FAILED -> "Build failed, but its log could not be read"
                    BuildStatus.INCONCLUSIVE -> "Build result unclear"
                    else -> "Publishing stopped"
                }
                Text(title, color = Color(0xFFE0564A), fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                Text(vm.pubInfo ?: "Something went wrong.", color = c.mu, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                vm.buildErrors?.let {
                    Text(
                        it, color = c.tx, fontSize = 11.sp, fontFamily = FontFamily.Monospace, lineHeight = 15.sp,
                        modifier = Modifier.padding(top = 8.dp).fillMaxWidth().heightIn(max = 220.dp).clip(RoundedCornerShape(10.dp))
                            .background(c.sf2).verticalScroll(rememberScrollState()).padding(10.dp),
                    )
                    VcButton("Fix with AI", { vm.fixBuild(projectId); onFix() }, Modifier.padding(top = 12.dp).fillMaxWidth(), icon = Ic.Check)
                }
                vm.runUrl?.let { url ->
                    Text(
                        "Open the run on GitHub", color = c.ac, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().clickable { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.padding(top = 14.dp),
                    )
                }
            }
        }
    }
}
