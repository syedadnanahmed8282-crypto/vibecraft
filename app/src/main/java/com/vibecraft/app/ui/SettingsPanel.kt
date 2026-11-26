package com.vibecraft.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibecraft.app.AppVm
import com.vibecraft.app.ai.Profile
import com.vibecraft.app.notify.Notifier

internal const val APP_VERSION = "0.15.0"
internal const val CLASSIC_TOKEN_URL = "https://github.com/settings/tokens/new?scopes=repo,workflow&description=Vibecraft"
internal const val FINE_TOKEN_URL = "https://github.com/settings/personal-access-tokens/new"

@Composable
private fun Acc(title: String, icon: ImageVector, open: Boolean, onToggle: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = VCT.c
    val rot by animateFloatAsState(if (open) 180f else 0f, tween(250), label = "chev")
    VcCard(Modifier.padding(bottom = 10.dp), pad = 0.dp) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(c.ac2), Alignment.Center) {
                Icon(icon, null, Modifier.size(16.dp), tint = c.ac)
            }
            Spacer(Modifier.width(10.dp))
            Text(title, color = c.tx, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Icon(Ic.ChevDown, null, Modifier.size(18.dp).graphicsLayer { rotationZ = rot }, tint = c.mu)
        }
        AnimatedVisibility(open) { Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp), content = content) }
    }
}

/** AI profiles (API key, URL, model), preferences, GitHub token and account: one accordion, one section open at a time. */
@Composable
fun SettingsPanel(vm: AppVm, section: String?, onSection: (String?) -> Unit) {
    val c = VCT.c
    val ctx = LocalContext.current
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Profile?>(null) }
    var confirm by remember { mutableStateOf(false) }
    fun toggle(k: String) = onSection(if (section == k) null else k)
    fun openUrl(u: String) = ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)))

    Column {
        // ---- AI profiles: API key, base URL, model name, test ----
        Acc("AI profiles", Ic.Gear, section == "ai", { toggle("ai") }) {
            Text(
                "Add any model with your own API key. Tap a profile to change its API key, URL or model. The order sets the fallback priority.",
                color = c.mu, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp),
            )
            if (vm.profiles.isEmpty()) {
                Text("No profiles yet. Add your first one to start getting replies.", color = c.tx, fontSize = 13.sp, modifier = Modifier.padding(vertical = 10.dp))
            }
            vm.profiles.forEachIndexed { i, p ->
                val shape = RoundedCornerShape(12.dp)
                val used = vm.usedToday(p.id)
                val keyed = vm.hasKey(p.id)
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp).clip(shape).background(c.sf2).border(1.dp, c.bd, shape)
                        .clickable { editing = p }.padding(start = 8.dp, top = 8.dp, bottom = 8.dp, end = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProviderBadge(p.provider, 32.dp)
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, color = c.tx, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${p.provider} · ${p.model}" + if (p.dailyLimit > 0) " · $used/${p.dailyLimit}" else " · $used today",
                            color = c.mu, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        if (p.dailyLimit > 0) Meter(used.toFloat() / p.dailyLimit, Modifier.padding(top = 6.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Chip(
                        when { !keyed -> "No key"; p.id == vm.activeId -> "Active"; else -> "Standby" },
                        active = keyed && p.id == vm.activeId,
                    )
                    Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).clickable { vm.moveProfile(i, -1) }, Alignment.Center) { Icon(Ic.ChevUp, "Up", Modifier.size(15.dp), tint = c.tx) }
                    Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).clickable { vm.moveProfile(i, 1) }, Alignment.Center) { Icon(Ic.ChevDown, "Down", Modifier.size(15.dp), tint = c.tx) }
                }
            }
            VcButton("Add profile", { adding = true }, Modifier.padding(top = 10.dp).fillMaxWidth(), secondary = true, icon = Ic.Plus)
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Auto-switch when limit is reached", color = c.tx, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                VcSwitch(vm.autoSwitch) { vm.toggleAuto(it) }
            }
        }

        // ---- Preferences ----
        Acc("Preferences", Ic.Moon, section == "pref", { toggle("pref") }) {
            Text("Theme", color = c.mu, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 6.dp))
            SlidingTabs(listOf("System", "Light", "Dark"), vm.theme.ordinal, { vm.changeTheme(ThemeMode.values()[it]) })
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Plan first for new apps", color = c.tx, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                VcSwitch(vm.planFirst) { vm.changePlanFirst(it) }
            }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Notifications", color = c.tx, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                VcSwitch(vm.notifyOn) { vm.changeNotify(it) }
            }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Sounds", color = c.tx, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                VcSwitch(vm.soundOn) { vm.changeSound(it) }
            }
            Text(
                if (vm.notifyAllowed) "Vibecraft keeps working in the background and tells you when a task is done, when it needs you, or when something goes wrong."
                else "Android is blocking notifications for Vibecraft. Allow them in the system settings to hear back when you leave the app.",
                color = if (vm.notifyAllowed) c.mu else Color(0xFFE0564A), fontSize = 11.5.sp, modifier = Modifier.padding(top = 8.dp),
            )
            VcButton("Send a test notification", { vm.testNotification() }, Modifier.padding(top = 10.dp).fillMaxWidth(), secondary = true, icon = Ic.Bell)
            VcButton("System notification settings", { Notifier.openSettings(ctx) }, Modifier.padding(top = 8.dp).fillMaxWidth(), secondary = true, icon = Ic.Gear)
        }

        // ---- GitHub token ----
        Acc("GitHub", Ic.Key, section == "github", { toggle("github") }) {
            var tok by remember { mutableStateOf("") }
            var err by remember { mutableStateOf<String?>(null) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Token", color = c.tx, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Chip(if (vm.ghSaved) "Saved" else "Not set", active = vm.ghSaved)
            }
            VcField(
                tok, { tok = it }, "Personal access token",
                if (vm.ghSaved) "Saved. Paste a new one to replace it" else "ghp_… or github_pat_…", password = true, mono = true,
            )
            err?.let { Text(it, color = Color(0xFFE0564A), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
            VcButton("Save token", { err = vm.saveGhToken(tok); if (err == null) tok = "" }, Modifier.padding(top = 12.dp).fillMaxWidth(), enabled = tok.isNotBlank())
            VcButton("Get token on GitHub", { openUrl(CLASSIC_TOKEN_URL) }, Modifier.padding(top = 8.dp).fillMaxWidth(), secondary = true, icon = Ic.Up)
            Text(
                "Prefer limited access? Create a fine-grained token (Contents and Workflows: read and write, Actions: read)",
                color = c.ac, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clickable { openUrl(FINE_TOKEN_URL) }.padding(top = 12.dp),
            )
            if (vm.ghSaved) Text(
                "Remove token", color = Color(0xFFE0564A), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clickable { vm.clearGhToken() }.padding(top = 14.dp),
            )
            Text("The token stays encrypted on this phone.", color = c.mu, fontSize = 11.5.sp, modifier = Modifier.padding(top = 10.dp))
        }

        // ---- Account ----
        Acc("Account", Ic.User, section == "account", { toggle("account") }) {
            Text(
                if (vm.signedIn) "Signed in as ${vm.email}" else "Guest mode: your projects stay on this phone only.",
                color = c.mu, fontSize = 12.5.sp, modifier = Modifier.padding(bottom = 12.dp),
            )
            if (vm.signedIn) {
                VcButton(if (vm.busy) "Syncing…" else "Sync now", { vm.syncNow() }, Modifier.fillMaxWidth(), enabled = !vm.busy, icon = Ic.Sync)
                Spacer(Modifier.height(8.dp))
            }
            VcButton(if (vm.signedIn) "Sign out (keep data on this phone)" else "Leave guest mode", { vm.signOut(false) }, Modifier.fillMaxWidth(), secondary = true)
            Text(
                "Sign out and clear this phone", color = Color(0xFFE0564A), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clickable { confirm = true }.padding(top = 14.dp, bottom = 4.dp),
            )
            vm.message?.let { Text(it, color = c.mu, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
        }

        Text("Vibecraft $APP_VERSION", color = c.mu, fontSize = 11.5.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
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
