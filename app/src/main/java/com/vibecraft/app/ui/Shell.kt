package com.vibecraft.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibecraft.app.AppVm
import com.vibecraft.app.data.ProjectEntity
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

@Composable
fun MainShell(vm: AppVm) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var drawer by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf(false) }
    val projects by vm.projects.collectAsState()
    BackHandler(enabled = drawer || sheet) { drawer = false; sheet = false }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Header(
                sub = projects.firstOrNull { it.id == openId }?.name ?: "New project",
                model = vm.activeName, onMenu = { drawer = true }, onPill = { sheet = true },
            )
            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> ChatTab(vm, openId, { tab = 4 }) { openId = it }
                    1 -> PreviewTab(vm, openId)
                    2 -> CodeTab(vm, openId)
                    3 -> PublishTab(vm, openId) { tab = 0 }
                    else -> SettingsTab(vm)
                }
            }
            BottomNav(tab) { tab = it }
        }
        Scrim(drawer || sheet) { drawer = false; sheet = false }
        Drawer(drawer, vm, projects, openId,
            onClose = { drawer = false },
            onNew = { openId = null; tab = 0; drawer = false },
            onPick = { openId = it; tab = 0; drawer = false })
        ModelSheet(sheet, vm) { sheet = false }
    }
}

@Composable
private fun Header(sub: String, model: String?, onMenu: () -> Unit, onPill: () -> Unit) {
    val c = VCT.c
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .background(c.sf)
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(9.dp)).clickable(onClick = onMenu).testTag("menu_button"), Alignment.Center) {
                Icon(Ic.Menu, "Menu", Modifier.size(22.dp), tint = c.tx)
            }
            Spacer(Modifier.width(4.dp))
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(c.g), Alignment.Center) {
                Logo(24.dp, animated = false, onGreen = true)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Vibecraft", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.tx)
                Text(sub, fontSize = 11.5.sp, color = c.mu, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(
                Modifier.clip(RoundedCornerShape(999.dp)).background(c.sf2).clickable(onClick = onPill).padding(horizontal = 10.dp, vertical = 5.dp).testTag("model_pill"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(if (model != null) c.ok else c.mu))
                Spacer(Modifier.width(6.dp))
                Text(model ?: "No model", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = c.tx)
                Spacer(Modifier.width(4.dp))
                Icon(Ic.ChevDown, null, Modifier.size(13.dp), tint = c.mu)
            }
            Spacer(Modifier.width(8.dp))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.bd))
    }
}

@Composable
private fun BottomNav(selected: Int, onSelect: (Int) -> Unit) {
    val c = VCT.c
    val items: List<Pair<String, ImageVector>> = listOf(
        "Chat" to Ic.Chat, "Preview" to Ic.Phone, "Code" to Ic.Code, "Publish" to Ic.Up, "Settings" to Ic.Gear,
    )
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.bd))
        Row(Modifier.fillMaxWidth().background(c.sf).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))) {
            items.forEachIndexed { i, (label, icon) ->
                val sel = i == selected
                val pop by animateFloatAsState(if (sel) 1.15f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "pop")
                val ind by animateFloatAsState(if (sel) 1f else 0f, tween(250), label = "ind")
                Box(
                    Modifier.weight(1f).clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onSelect(i) }.testTag("nav_${label.lowercase()}"),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Box(
                        Modifier.fillMaxWidth(0.4f).height(3.dp).graphicsLayer { scaleX = ind }
                            .background(c.ac, RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp)),
                    )
                    Column(Modifier.padding(top = 9.dp, bottom = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(icon, label, Modifier.size(22.dp).graphicsLayer { scaleX = pop; scaleY = pop }, tint = if (sel) c.ac else c.mu)
                        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = if (sel) c.ac else c.mu, modifier = Modifier.padding(top = 3.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Scrim(open: Boolean, onClose: () -> Unit) {
    AnimatedVisibility(open, enter = fadeIn(tween(250)), exit = fadeOut(tween(250))) {
        Box(
            Modifier.fillMaxSize().background(Color(0x73071A10))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClose),
        )
    }
}

private fun startOfToday(): Long {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

@Composable
private fun Drawer(
    open: Boolean, vm: AppVm, projects: List<ProjectEntity>, openId: String?,
    onClose: () -> Unit, onNew: () -> Unit, onPick: (String) -> Unit,
) {
    val c = VCT.c
    val fmt = remember { SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()) }
    val today = remember(open) { startOfToday() }
    AnimatedVisibility(open, enter = slideInHorizontally(tween(280)) { -it }, exit = slideOutHorizontally(tween(280)) { -it }) {
        Column(
            Modifier.fillMaxHeight().widthIn(max = 300.dp).fillMaxWidth(0.84f)
                .background(c.sf).border(1.dp, c.bd).systemBarsPadding().padding(14.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Chats", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = c.tx, modifier = Modifier.weight(1f))
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)).clickable(onClick = onClose), Alignment.Center) {
                    Icon(Ic.Close, "Close", Modifier.size(20.dp), tint = c.tx)
                }
            }
            VcButton("New chat", onNew, Modifier.fillMaxWidth().testTag("new_chat_button"), icon = Ic.Plus)
            LazyColumn(Modifier.weight(1f).padding(top = 14.dp)) {
                val todayList = projects.filter { it.updatedAt >= today }
                val earlier = projects.filter { it.updatedAt < today }
                fun group(title: String, list: List<ProjectEntity>) {
                    if (list.isEmpty()) return
                    item(key = "h$title") {
                        Text(title.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = c.mu, modifier = Modifier.padding(start = 6.dp, top = 12.dp, bottom = 6.dp))
                    }
                    items(list, key = { it.id }) { p ->
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 2.dp).clip(RoundedCornerShape(10.dp))
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
                if (projects.isEmpty()) item {
                    Text("No chats yet.", fontSize = 13.sp, color = c.mu, modifier = Modifier.padding(top = 16.dp, start = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun ModelSheet(open: Boolean, vm: AppVm, onClose: () -> Unit) {
    val c = VCT.c
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(open, enter = slideInVertically(tween(280)) { it }, exit = slideOutVertically(tween(280)) { it }) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                    .background(c.sf).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 20.dp),
            ) {
                Box(Modifier.align(Alignment.CenterHorizontally).width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.bd))
                Text("Choose model", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.tx, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
                if (vm.profiles.isEmpty()) {
                    Text("No AI profiles yet. Add one in Settings.", fontSize = 13.sp, color = c.mu, modifier = Modifier.padding(vertical = 14.dp))
                }
                vm.profiles.forEach { p ->
                    val sel = p.id == vm.activeId
                    val used = vm.usedToday(p.id)
                    val shape = RoundedCornerShape(12.dp)
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp).clip(shape)
                            .background(if (sel) c.ac2 else Color.Transparent)
                            .border(1.dp, if (sel) c.ac else c.bd, shape)
                            .clickable { vm.pickModel(p.id); onClose() }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = c.tx)
                            Text("${p.provider} · $used today", fontSize = 11.5.sp, color = c.mu)
                            if (p.dailyLimit > 0) Meter(used.toFloat() / p.dailyLimit, Modifier.padding(top = 6.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Box(
                            Modifier.size(22.dp).clip(CircleShape).background(if (sel) c.ac else Color.Transparent)
                                .border(2.dp, if (sel) c.ac else c.bd, CircleShape),
                            Alignment.Center,
                        ) { if (sel) Icon(Ic.Check, null, Modifier.size(13.dp), tint = Color.White) }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Auto-switch when limit is reached", fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = c.tx, modifier = Modifier.weight(1f))
                    VcSwitch(vm.autoSwitch) { vm.toggleAuto(it) }
                }
            }
        }
    }
}
