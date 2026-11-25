package com.vibecraft.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibecraft.app.AppVm
import kotlinx.coroutines.delay

@Composable
internal fun Note(text: String) {
    Text(text, color = VCT.c.mu, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
}

@Composable
internal fun TabPage(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 76.dp, bottom = 28.dp), content = content)
}

@Composable
internal fun StepRow(n: Int, label: String, state: Int) {
    val c = VCT.c
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(22.dp).clip(CircleShape).background(if (state == 2) c.ok else Color.Transparent)
                .border(2.dp, if (state == 2) c.ok else if (state == 1) c.ac else c.bd, CircleShape),
            Alignment.Center,
        ) {
            when (state) {
                2 -> Icon(Ic.Check, null, Modifier.size(13.dp), tint = Color.White)
                1 -> CircularProgressIndicator(Modifier.size(14.dp), color = c.ac, strokeWidth = 2.dp)
                else -> Text("$n", color = c.mu, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(label, color = if (state == 0) c.mu else c.tx, fontSize = 13.5.sp)
    }
}

@Composable
fun SettingsTab(vm: AppVm) {
    val c = VCT.c
    var confirm by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<com.vibecraft.app.ai.Profile?>(null) }
    TabPage {
        VcCard {
            CardTitle(Ic.Code, "AI profiles")
            Text("Add any model with your own API key. The order sets the fallback priority.", color = c.mu, fontSize = 12.5.sp, modifier = Modifier.padding(bottom = 6.dp))
            if (vm.profiles.isEmpty()) {
                Text("No profiles yet. Add your first one to start getting replies.", color = c.tx, fontSize = 13.sp, modifier = Modifier.padding(vertical = 10.dp))
            }
            vm.profiles.forEachIndexed { i, p ->
                val shape = RoundedCornerShape(10.dp)
                val used = vm.usedToday(p.id)
                val keyed = vm.hasKey(p.id)
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp).clip(shape).background(c.sf2).border(1.dp, c.bd, shape)
                        .clickable { editing = p }.padding(start = 10.dp, top = 9.dp, bottom = 9.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(c.g), Alignment.Center) {
                        Text("${i + 1}", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, color = c.tx, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                        Text(
                            "${p.provider} · ${p.model}" + if (p.dailyLimit > 0) " · $used/${p.dailyLimit} today" else " · $used today",
                            color = c.mu, fontSize = 11.5.sp, maxLines = 1,
                        )
                        if (p.dailyLimit > 0) Meter(used.toFloat() / p.dailyLimit, Modifier.padding(top = 6.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Chip(
                        when { !keyed -> "No key"; p.id == vm.activeId -> "Active"; else -> "Standby" },
                        active = keyed && p.id == vm.activeId,
                    )
                    Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).clickable { vm.moveProfile(i, -1) }, Alignment.Center) { Icon(Ic.ChevUp, "Up", Modifier.size(15.dp), tint = c.tx) }
                    Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).clickable { vm.moveProfile(i, 1) }, Alignment.Center) { Icon(Ic.ChevDown, "Down", Modifier.size(15.dp), tint = c.tx) }
                }
            }
            VcButton("Add profile", { adding = true }, Modifier.padding(top = 12.dp).fillMaxWidth(), secondary = true, icon = Ic.Plus)
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Auto-switch when limit is reached", color = c.tx, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                VcSwitch(vm.autoSwitch) { vm.toggleAuto(it) }
            }
        }
        Spacer(Modifier.height(14.dp))
        VcCard {
            CardTitle(Ic.Phone, "Preferences")
            Text("Theme", color = c.mu, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
            SlidingTabs(listOf("System", "Light", "Dark"), vm.theme.ordinal, { vm.changeTheme(ThemeMode.values()[it]) })
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Plan first for new apps", color = c.tx, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                VcSwitch(vm.planFirst) { vm.changePlanFirst(it) }
            }
        }
        Spacer(Modifier.height(14.dp))
        VcCard {
            CardTitle(Ic.Gear, "Account")
            Text(
                if (vm.signedIn) "Signed in as ${vm.email}" else "Guest mode: your projects stay on this phone only.",
                color = c.mu, fontSize = 12.5.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            if (vm.signedIn) {
                VcButton(if (vm.busy) "Syncing…" else "Sync now", { vm.syncNow() }, Modifier.fillMaxWidth(), enabled = !vm.busy)
                Spacer(Modifier.height(8.dp))
            }
            VcButton(if (vm.signedIn) "Sign out (keep data on this phone)" else "Leave guest mode", { vm.signOut(false) }, Modifier.fillMaxWidth(), secondary = true)
            Text(
                "Sign out and clear this phone", color = Color(0xFFE0564A), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clickable { confirm = true }.padding(top = 14.dp, bottom = 4.dp),
            )
            vm.message?.let { Text(it, color = c.mu, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
        }
        Spacer(Modifier.height(16.dp))
    }
    if (adding) ProfileEditor(vm, null) { adding = false }
    editing?.let { ProfileEditor(vm, it) { editing = null } }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Clear this phone?") },
            text = {
                Text(
                    if (vm.signedIn) "Projects and chats on this phone will be removed. They stay safe in your cloud backup."
                    else "Guest projects exist only on this phone, so they will be lost for good."
                )
            },
            confirmButton = { TextButton(onClick = { confirm = false; vm.signOut(true) }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}
