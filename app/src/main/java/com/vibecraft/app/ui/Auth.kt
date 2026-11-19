package com.vibecraft.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibecraft.app.AppVm
import kotlinx.coroutines.delay

private fun score(v: String): Int {
    var s = 0
    if (v.length >= 6) s++
    if (v.length >= 10) s++
    if (v.any { it.isDigit() } && v.any { it.isLetter() }) s++
    if (v.any { !it.isLetterOrDigit() } || (v.any { it.isUpperCase() } && v.any { it.isLowerCase() })) s++
    return s
}

@Composable
private fun Blobs() {
    val inf = rememberInfiniteTransition(label = "blobs")
    val d by inf.animateFloat(
        initialValue = -24f, targetValue = 24f,
        animationSpec = infiniteRepeatable(tween(4500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "drift",
    )
    Canvas(Modifier.fillMaxSize()) {
        val r = 170.dp.toPx()
        val glow = listOf(Color(0x332F8F5B), Color.Transparent)
        val c1 = Offset(size.width - 10.dp.toPx() + d, 30.dp.toPx() - d)
        val c2 = Offset(10.dp.toPx() - d, size.height - 30.dp.toPx() + d)
        drawCircle(Brush.radialGradient(glow, c1, r), r, c1)
        drawCircle(Brush.radialGradient(glow, c2, r), r, c2)
    }
}

@Composable
private fun Letter(t: String, delayMs: Int) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(delayMs.toLong()); a.animateTo(1f, tween(500)) }
    Text(
        t, fontSize = 27.sp, fontWeight = FontWeight.Bold, color = VCT.c.tx,
        modifier = Modifier.graphicsLayer { alpha = a.value; translationY = (1f - a.value) * 30f },
    )
}

@Composable
fun AuthScreen(vm: AppVm) {
    val c = VCT.c
    var signUp by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var mail by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }

    Box(Modifier.fillMaxSize()) {
        Blobs()
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.padding(top = 8.dp, bottom = 4.dp)) { Logo(104.dp, breathe = true) }
            Row(Modifier.padding(top = 6.dp)) { "Vibecraft".forEachIndexed { i, ch -> Letter(ch.toString(), 450 + i * 45) } }
            Text(
                "Turn ideas into apps, just by chatting.", color = c.mu, fontSize = 13.5.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp).widthIn(max = 280.dp),
            )
            Column(Modifier.fillMaxWidth().widthIn(max = 380.dp)) {
                Entrance(300) { SlidingTabs(listOf("Sign in", "Create account"), if (signUp) 1 else 0, { signUp = it == 1 }, Modifier.testTag("auth_mode_tabs")) }
                AnimatedVisibility(signUp) { VcField(name, { name = it }, "Full name", "Your name", Modifier.testTag("name_input")) }
                Entrance(350) { VcField(mail, { mail = it }, "Email", "you@example.com", Modifier.testTag("email_input"), keyboard = KeyboardType.Email) }
                Entrance(400) {
                    Column {
                        VcField(pass, { pass = it }, "Password", "Password", Modifier.testTag("password_input"), password = true)
                        if (signUp) StrengthBar(score(pass))
                    }
                }
                vm.message?.let { Text(it, color = Color(0xFFE0564A), fontSize = 12.5.sp, modifier = Modifier.padding(top = 10.dp)) }
                Entrance(450) {
                    VcButton(
                        if (signUp) "Create account" else "Sign in",
                        onClick = { vm.submit(signUp, name, mail, pass) },
                        modifier = Modifier.padding(top = 16.dp).fillMaxWidth().testTag("auth_submit_button"),
                        loading = vm.busy && !vm.authOk, done = vm.authOk,
                    )
                }
                Entrance(500) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f).height(1.dp).background(c.bd))
                        Text("or", color = c.mu, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp))
                        Box(Modifier.weight(1f).height(1.dp).background(c.bd))
                    }
                }
                Entrance(550) {
                    VcButton(
                        "Continue with Google", onClick = { vm.message = "Google sign-in arrives in a later build." },
                        modifier = Modifier.fillMaxWidth().testTag("google_sign_in_button"), secondary = true,
                    )
                }
                Entrance(600) {
                    Text(
                        "Continue as guest", color = c.ac, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().clickable { vm.continueAsGuest() }.padding(vertical = 14.dp).testTag("guest_button"),
                    )
                }
                Entrance(650) {
                    Text(
                        "By continuing you agree to the Terms and Privacy Policy.", color = c.mu, fontSize = 11.5.sp,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
