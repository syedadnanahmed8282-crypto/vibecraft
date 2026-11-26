package com.vibecraft.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/** Asked once, in plain words, before Android's own permission question. */
@Composable
fun NotifyPrompt(onAllow: () -> Unit, onLater: () -> Unit) {
    val c = VCT.c
    val shape = RoundedCornerShape(24.dp)
    Dialog(onDismissRequest = onLater) {
        Column(
            Modifier.fillMaxWidth().clip(shape).background(c.sf).border(1.dp, c.bd, shape).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(c.g), Alignment.Center) {
                Icon(Ic.Bell, null, Modifier.size(30.dp), tint = Color.White)
            }
            Spacer(Modifier.height(16.dp))
            Text("Stay in the loop", color = c.tx, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Vibecraft keeps working while you use other apps. Allow notifications and it will tell you, with a soft sound, " +
                    "when a task is done, when it needs your answer, or when something goes wrong.",
                color = c.mu, fontSize = 13.5.sp, textAlign = TextAlign.Center, lineHeight = 19.sp,
            )
            Spacer(Modifier.height(20.dp))
            VcButton("Allow notifications", onAllow, Modifier.fillMaxWidth(), icon = Ic.Bell)
            Spacer(Modifier.height(8.dp))
            VcButton("Not now", onLater, Modifier.fillMaxWidth(), secondary = true)
        }
    }
}
