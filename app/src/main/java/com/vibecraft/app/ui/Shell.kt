package com.vibecraft.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibecraft.app.AppVm
import com.vibecraft.app.ai.Profile
import com.vibecraft.app.data.ProjectEntity
import com.vibecraft.app.gen.Devices
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun AppRoot(vm: AppVm) {
    val c = VCT.c
    Box(Modifier.fillMaxSize().background(c.bg)) {
        when {
            !vm.ready -> Box(Modifier.fillMaxSize().systemBarsPadding(), Alignment.Center) { Logo(96.dp) }
            !vm.signedIn && !vm.guest -> AuthScreen(vm)
            else -> MainShell(vm)
        }
    }
}

/**
 * One screen, no bottom bar: the chat fills it, two floating glass pills sit on top,
 * Preview / Code / Publish open as pages from the right pill, and the left menu holds chats and Settings.
 */
@Composable
fun MainShell(vm: AppVm) {
    val c = VCT.c
    val ctx = LocalContext.current
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var page by rememberSaveable { mutableStateOf<String?>(null) }   // "preview" | "code" | "publish"
    var codeTab by rememberSaveable { mutableStateOf(0) }
    var drawer by remember { mutableStateOf(false) }
    var section by remember { mutableStateOf<String?>(null) }
    var pillOpen by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf<String?>(null) }
    var lastMenu by remember { mutableStateOf<String?>(null) }
    SideEffect { if (menu != null) lastMenu = menu }
    val projects by vm.projects.collectAsState()
    val projectName = projects.firstOrNull { it.id == openId }?.name ?: "New chat"
    val model = vm.profiles.firstOrNull { it.id == vm.activeId }

    BackHandler(enabled = menu != null || pillOpen || page != null || drawer) {
        when {
            menu != null -> menu = null
            pillOpen -> pillOpen = false
            page != null -> page = null
            else -> drawer = false
        }
    }
    fun go(p: String, tab: Int = 0) { menu = null; pillOpen = false; codeTab = tab; page = p }
    fun openSettings(sec: String) { menu = null; pillOpen = false; page = null; section = sec; drawer = true }
    fun openUrl(u: String) { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        ChatTab(vm, openId, onSetup = { openSettings("ai") }, onOpenCode = { go("code", 0) }, onOpenAi = { openSettings("ai") }) { openId = it }

        // Tap outside closes the dropdown first, then folds the pill back.
        if (menu != null || pillOpen) {
            Box(
                Modifier.fillMaxSize().clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                    if (menu != null) menu = null else pillOpen = false
                },
            )
        }

        Row(
            Modifier.align(Alignment.TopStart).fillMaxWidth().statusBarsPadding().padding(start = 12.dp, end = 12.dp, top = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top,
        ) {
            LeftPill(projectName) { menu = null; pillOpen = false; drawer = true }
            RightPill(pillOpen, menu, model) { k ->
                if (!pillOpen) pillOpen = true   // the first tap only unfolds the pill
                else menu = if (menu == k) null else k
            }
        }

        val origin = TransformOrigin(1f, 0f)
        AnimatedVisibility(
            visible = menu != null,
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 66.dp, end = 12.dp),
            enter = fadeIn(tween(160)) + scaleIn(tween(220), initialScale = 0.9f, transformOrigin = origin),
            exit = fadeOut(tween(120)) + scaleOut(tween(140), targetScale = 0.95f, transformOrigin = origin),
        ) {
            Column(Modifier.width(300.dp).glass(RoundedCornerShape(22.dp), strong = true).padding(8.dp)) {
                MenuContent(
                    kind = menu ?: lastMenu, vm = vm, go = ::go,
                    closeAll = { menu = null; pillOpen = false },
                    addModel = { openSettings("ai") }, openUrl = ::openUrl,
                )
            }
        }

        // Left menu: chats, then every setting.
        AnimatedVisibility(drawer, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
            Box(
                Modifier.fillMaxSize().background(Color(0x73071A10))
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { drawer = false },
            )
        }
        AnimatedVisibility(
            drawer,
            enter = slideInHorizontally(tween(300)) { -it }, exit = slideOutHorizontally(tween(300)) { -it },
        ) {
            Drawer(
                vm, projects, openId, section, { section = it }, onClose = { drawer = false },
                onNew = { openId = null; drawer = false }, onPick = { openId = it; drawer = false },
            )
        }

        // Pages: the same glass language as the chat.
        AnimatedVisibility(
            page != null,
            enter = slideInVertically(tween(340)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(260)) { it } + fadeOut(tween(160)),
        ) {
            Box(Modifier.fillMaxSize().background(c.bg)) {
                AmbientBg()
                when (page) {
                    "preview" -> PreviewTab(vm, openId)
                    "code" -> CodeTab(vm, openId, codeTab)
                    "publish" -> PublishTab(vm, openId, onFix = { page = null }, onSettings = { openSettings("github") })
                }
                PageHeader(
                    when (page) { "preview" -> "Preview"; "code" -> "Code"; else -> "Publish" },
                    Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 12.dp, top = 10.dp),
                ) { page = null }
            }
        }
    }
}

@Composable
private fun LeftPill(name: String, onClick: () -> Unit) {
    val c = VCT.c
    Row(
        Modifier.height(48.dp).widthIn(max = 190.dp).glass(RoundedCornerShape(24.dp), strong = true)
            .clickable(onClick = onClick).padding(start = 5.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(c.ac2), Alignment.Center) {
            Icon(Ic.Menu, "Menu", Modifier.size(20.dp), tint = c.ac)
        }
        Spacer(Modifier.width(9.dp))
        Column {
            Text("Vibecraft", color = c.tx, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(name, color = c.mu, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun RightPill(open: Boolean, menu: String?, model: Profile?, onIcon: (String) -> Unit) {
    val c = VCT.c
    Row(
        Modifier.height(48.dp).glass(RoundedCornerShape(24.dp), strong = true).padding(horizontal = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PillIcon(Ic.Phone, "Preview", menu == "preview") { onIcon("preview") }
        AnimatedVisibility(
            open,
            enter = expandHorizontally(tween(300)) + fadeIn(tween(250)),
            exit = shrinkHorizontally(tween(220)) + fadeOut(tween(150)),
        ) {
            Row {
                PillIcon(Ic.Code, "Code", menu == "code") { onIcon("code") }
                PillIcon(Ic.Up, "Publish", menu == "publish") { onIcon("publish") }
            }
        }
        Box(Modifier.padding(horizontal = 3.dp).width(1.dp).height(20.dp).background(c.bd))
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(if (menu == "model") c.ac2 else Color.Transparent).clickable { onIcon("model") },
            Alignment.Center,
        ) {
            ProviderBadge(model?.provider ?: "Custom", 30.dp)
            Box(
                Modifier.align(Alignment.BottomEnd).padding(2.dp).size(10.dp).clip(CircleShape)
                    .background(if (model != null) c.ok else c.mu).border(2.dp, c.sf, CircleShape),
            )
        }
    }
}

@Composable
private fun PillIcon(icon: ImageVector, desc: String, active: Boolean, onClick: () -> Unit) {
    val c = VCT.c
    Box(
        Modifier.padding(horizontal = 1.dp).size(38.dp).clip(CircleShape)
            .background(if (active) c.ac2 else Color.Transparent).clickable(onClick = onClick),
        Alignment.Center,
    ) { Icon(icon, desc, Modifier.size(20.dp), tint = if (active) c.ac else c.tx) }
}

@Composable
private fun PageHeader(title: String, modifier: Modifier, onBack: () -> Unit) {
    val c = VCT.c
    Row(
        modifier.height(48.dp).glass(RoundedCornerShape(24.dp), strong = true).padding(start = 5.dp, end = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(c.ac2).clickable(onClick = onBack), Alignment.Center) {
            Icon(Ic.Back, "Back", Modifier.size(20.dp), tint = c.ac)
        }
        Spacer(Modifier.width(10.dp))
        Text(title, color = c.tx, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ---------------------------------------------------------------- dropdown menus

@Composable
private fun MenuHeader(t: String) {
    Text(
        t.uppercase(), color = VCT.c.mu, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 10.dp, top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun MenuRow(
    icon: ImageVector?, title: String, sub: String? = null,
    trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit,
) {
    val c = VCT.c
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(c.ac2), Alignment.Center) {
                Icon(icon, null, Modifier.size(17.dp), tint = c.ac)
            }
            Spacer(Modifier.width(11.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = c.tx, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
            if (sub != null) Text(sub, color = c.mu, fontSize = 11.5.sp)
        }
        if (trailing != null) { Spacer(Modifier.width(8.dp)); trailing() }
    }
}

@Composable
private fun MenuContent(
    kind: String?, vm: AppVm, go: (String, Int) -> Unit, closeAll: () -> Unit,
    addModel: () -> Unit, openUrl: (String) -> Unit,
) {
    val c = VCT.c
    when (kind) {
        "preview" -> {
            MenuHeader("Preview")
            MenuRow(Ic.Phone, "Open preview", "Design simulation") { go("preview", 0) }
            val dev = Devices.all[vm.previewDevice.coerceIn(0, Devices.all.lastIndex)]
            MenuRow(Ic.Size, "Device size", "${dev.label} · ${dev.short}") { go("preview", 0) }
            MenuRow(
                Ic.Moon, "Dark preview", "Check the dark palette",
                trailing = { VcSwitch(vm.previewDark) { vm.setPreview(dark = it) } },
            ) { vm.setPreview(dark = !vm.previewDark) }
        }
        "code" -> {
            MenuHeader("Code")
            MenuRow(Ic.File, "Files", "Everything the AI wrote") { go("code", 0) }
            MenuRow(Ic.Hist, "History", "Restore any version") { go("code", 1) }
            MenuRow(Ic.Brain, "Notes", "What the AI remembers") { go("code", 2) }
        }
        "publish" -> {
            MenuHeader("Publish")
            MenuRow(Ic.Up, "Commit & push", "Send the project to GitHub") { go("publish", 0) }
            val st = when (vm.pubStage) { 1, 2 -> "running"; 3 -> "ok"; 4 -> "failed"; else -> null }
            if (st == null) MenuRow(Ic.Build, "Build status", "No build yet") { go("publish", 0) }
            else MenuRow(Ic.Build, "Build status", "Open the build page", trailing = { Chip(st, active = st == "ok") }) { go("publish", 0) }
            MenuRow(Ic.Wand, "Auto-fix rounds", "Set on the Publish page") { go("publish", 0) }
            MenuRow(Ic.Key, "Get GitHub token", "Opens GitHub") { closeAll(); openUrl(CLASSIC_TOKEN_URL) }
        }
        "model" -> {
            MenuHeader("Choose model")
            if (vm.profiles.isEmpty()) {
                Text("No AI profiles yet.", color = c.mu, fontSize = 12.5.sp, modifier = Modifier.padding(start = 10.dp, top = 4.dp, bottom = 8.dp))
            }
            vm.profiles.forEach { p ->
                val sel = p.id == vm.activeId
                val used = vm.usedToday(p.id)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (sel) c.ac2 else Color.Transparent)
                        .clickable { vm.pickModel(p.id); closeAll() }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProviderBadge(p.provider, 34.dp)
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, color = c.tx, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                        Text("${p.provider} · ${p.model}", color = c.mu, fontSize = 11.5.sp)
                        if (p.dailyLimit > 0) Meter(used.toFloat() / p.dailyLimit, Modifier.padding(top = 6.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.size(22.dp).clip(CircleShape).background(if (sel) c.ac else Color.Transparent)
                            .border(2.dp, if (sel) c.ac else c.bd, CircleShape),
                        Alignment.Center,
                    ) { if (sel) Icon(Ic.Check, null, Modifier.size(12.dp), tint = Color.White) }
                }
            }
            Box(Modifier.padding(horizontal = 10.dp, vertical = 6.dp).fillMaxWidth().height(1.dp).background(c.bd))
            MenuRow(
                null, "Auto-switch when limit is reached",
                trailing = { VcSwitch(vm.autoSwitch) { vm.toggleAuto(it) } },
            ) { vm.toggleAuto(!vm.autoSwitch) }
            MenuRow(Ic.Plus, "Add model", "Open AI profiles") { addModel() }
        }
        else -> {}
    }
}

// ---------------------------------------------------------------- left menu: chats + settings

private fun startOfToday(): Long {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

@Composable
private fun Drawer(
    vm: AppVm, projects: List<ProjectEntity>, openId: String?,
    section: String?, onSection: (String?) -> Unit,
    onClose: () -> Unit, onNew: () -> Unit, onPick: (String) -> Unit,
) {
    val c = VCT.c
    val fmt = remember { SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()) }
    val today = remember { startOfToday() }
    Box(Modifier.fillMaxHeight().widthIn(max = 340.dp).fillMaxWidth(0.88f).background(c.bg).border(1.dp, c.bd)) {
        AmbientBg()
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(c.g), Alignment.Center) { Logo(22.dp, animated = false, onGreen = true) }
                Spacer(Modifier.width(10.dp))
                Text("Vibecraft", color = c.tx, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)).clickable(onClick = onClose), Alignment.Center) {
                    Icon(Ic.Close, "Close", Modifier.size(20.dp), tint = c.tx)
                }
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 18.dp)) {
                item(key = "new") { VcButton("New chat", onNew, Modifier.fillMaxWidth(), icon = Ic.Plus) }
                val todayList = projects.filter { it.updatedAt >= today }
                val earlier = projects.filter { it.updatedAt < today }
                fun group(title: String, list: List<ProjectEntity>) {
                    if (list.isEmpty()) return
                    item(key = "h$title") {
                        Text(title.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = c.mu, modifier = Modifier.padding(start = 6.dp, top = 16.dp, bottom = 6.dp))
                    }
                    items(list, key = { it.id }) { p ->
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 2.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (p.id == openId) c.ac2 else Color.Transparent)
                                .clickable { onPick(p.id) }.padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(p.name, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = c.tx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(fmt.format(p.updatedAt), fontSize = 11.5.sp, color = c.mu)
                            }
                            Box(Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).clickable { vm.deleteProject(p.id) }, Alignment.Center) {
                                Icon(Ic.Trash, "Delete", Modifier.size(17.dp), tint = c.mu)
                            }
                        }
                    }
                }
                group("Today", todayList)
                group("Earlier", earlier)
                if (projects.isEmpty()) item(key = "none") {
                    Text("No chats yet.", fontSize = 13.sp, color = c.mu, modifier = Modifier.padding(top = 16.dp, start = 6.dp))
                }
                item(key = "settings-h") {
                    Text("SETTINGS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = c.mu, modifier = Modifier.padding(start = 6.dp, top = 20.dp, bottom = 8.dp))
                }
                item(key = "settings") { SettingsPanel(vm, section, onSection) }
            }
        }
    }
}
