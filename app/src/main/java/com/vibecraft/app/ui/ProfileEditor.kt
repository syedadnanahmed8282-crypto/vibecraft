package com.vibecraft.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vibecraft.app.AppVm
import com.vibecraft.app.ai.Preset
import com.vibecraft.app.ai.Presets
import com.vibecraft.app.ai.Profile
import kotlinx.coroutines.launch
import java.util.UUID

/** Add or edit an AI profile: provider, URL, key, model, optional daily limit, and a connection test. */
@Composable
fun ProfileEditor(vm: AppVm, profile: Profile?, onClose: () -> Unit) {
    val c = VCT.c
    val scope = rememberCoroutineScope()
    val start = profile ?: Profile(UUID.randomUUID().toString(), "", Presets[0].provider, Presets[0].type, Presets[0].baseUrl, Presets[0].model)
    var provider by remember { mutableStateOf(start.provider) }
    var type by remember { mutableStateOf(start.type) }
    var name by remember { mutableStateOf(start.name) }
    var url by remember { mutableStateOf(start.baseUrl) }
    var model by remember { mutableStateOf(start.model) }
    var limit by remember { mutableStateOf(if (start.dailyLimit > 0) start.dailyLimit.toString() else "") }
    var key by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

    fun draft() = Profile(
        start.id, name.trim().ifBlank { "$provider ${model.trim()}" }, provider, type,
        url.trim(), model.trim(), limit.toIntOrNull()?.coerceAtLeast(0) ?: 0,
    )

    fun pick(p: Preset) {
        provider = p.provider; type = p.type; url = p.baseUrl; model = p.model
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val shape = RoundedCornerShape(16.dp)
        Column(
            Modifier.padding(16.dp).fillMaxWidth().heightIn(max = 620.dp).clip(shape).background(c.sf).border(1.dp, c.bd, shape)
                .verticalScroll(rememberScrollState()).padding(16.dp).testTag("profile_editor_dialog"),
        ) {
            CardTitle(Ic.Code, if (profile == null) "Add AI profile" else "Edit AI profile")
            Text("Use any provider with your own API key. The key stays on this phone.", color = c.mu, fontSize = 12.5.sp)
            Text("Provider", color = c.mu, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Presets.forEach { p ->
                    val sel = p.provider == provider
                    Text(
                        p.provider, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = if (sel) c.ac else c.tx,
                        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(if (sel) c.ac2 else c.sf2)
                            .border(1.dp, if (sel) c.ac else c.bd, RoundedCornerShape(999.dp))
                            .clickable { pick(p) }.padding(horizontal = 13.dp, vertical = 7.dp),
                    )
                }
            }
            VcField(name, { name = it }, "Name", "$provider $model")
            VcField(url, { url = it }, "API base URL", "https://…", mono = true, keyboard = KeyboardType.Uri)
            VcField(key, { key = it }, "API key", if (profile != null) "Leave blank to keep the saved key" else "sk-…", password = true, mono = true)
            VcField(model, { model = it }, "Model name", "model-name", mono = true)
            VcField(limit, { limit = it.filter(Char::isDigit) }, "Daily request limit (optional)", "e.g. 50", keyboard = KeyboardType.Number)
            result?.let {
                Text(it, color = if (it == "Connected.") c.ok else Color(0xFFE0564A), fontSize = 12.5.sp, modifier = Modifier.padding(top = 10.dp))
            }
            VcButton(
                "Test connection", {
                    scope.launch { testing = true; result = vm.testProfile(draft(), key); testing = false }
                },
                Modifier.padding(top = 14.dp).fillMaxWidth().testTag("profile_test_button"), loading = testing, secondary = true, icon = Ic.Check,
            )
            VcButton(
                "Save", {
                    val d = draft()
                    if (d.baseUrl.isBlank() || d.model.isBlank()) result = "Enter the base URL and the model name."
                    else {
                        val err = vm.saveProfile(d, key)
                        if (err == null) onClose() else result = err
                    }
                },
                Modifier.padding(top = 10.dp).fillMaxWidth().testTag("profile_save_button"),
            )
            if (profile != null) {
                Text(
                    "Delete profile", color = Color(0xFFE0564A), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clickable { vm.deleteProfile(profile.id); onClose() }.padding(top = 14.dp, bottom = 4.dp).testTag("profile_delete_button"),
                )
            }
        }
    }
}
