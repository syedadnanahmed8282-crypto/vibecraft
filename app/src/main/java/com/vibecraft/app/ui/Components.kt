package com.vibecraft.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun VcCard(modifier: Modifier = Modifier, pad: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    val c = VCT.c
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier.fillMaxWidth().clip(shape).background(c.sf).border(1.dp, c.bd, shape).padding(pad),
        content = content,
    )
}

@Composable
fun VcButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, loading: Boolean = false, done: Boolean = false,
    secondary: Boolean = false, icon: ImageVector? = null,
) {
    val c = VCT.c
    val shape = RoundedCornerShape(10.dp)
    val fg = if (secondary) c.tx else Color.White
    val bgMod = when {
        secondary -> Modifier.background(c.sf, shape).border(1.dp, c.bd, shape)
        done -> Modifier.background(c.ok, shape)
        else -> Modifier.background(c.g, shape)
    }
    Box(
        modifier.height(46.dp).clip(shape).then(bgMod)
            .alpha(if (enabled) 1f else 0.5f)
            .clickable(enabled = enabled && !loading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            loading -> CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
            done -> Icon(Ic.Check, null, tint = Color.White)
            else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                if (icon != null) Icon(icon, null, Modifier.size(17.dp), tint = fg)
                Text(text, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun VcField(
    value: String, onChange: (String) -> Unit, label: String, placeholder: String = "",
    modifier: Modifier = Modifier, password: Boolean = false, keyboard: KeyboardType = KeyboardType.Text,
    mono: Boolean = false,
) {
    val c = VCT.c
    var show by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(9.dp)
    Column(modifier) {
        Text(label, color = c.mu, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 12.dp, bottom = 5.dp))
        Box(Modifier.fillMaxWidth().clip(shape).background(c.sf2).border(1.dp, if (focused) c.ac else c.bd, shape)) {
            BasicTextField(
                value = value, onValueChange = onChange, singleLine = true,
                textStyle = TextStyle(color = c.tx, fontSize = if (mono) 12.5.sp else 14.sp),
                cursorBrush = SolidColor(c.ac),
                visualTransformation = if (password && !show) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard),
                modifier = Modifier.fillMaxWidth()
                    .padding(start = 12.dp, end = if (password) 44.dp else 12.dp, top = 12.dp, bottom = 12.dp)
                    .onFocusChanged { focused = it.isFocused },
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) Text(placeholder, color = c.mu.copy(alpha = 0.6f), fontSize = 14.sp)
                        inner()
                    }
                },
            )
            if (password) {
                Box(Modifier.align(Alignment.CenterEnd).size(40.dp).clickable { show = !show }, Alignment.Center) {
                    Icon(Ic.Eye, "Toggle password visibility", Modifier.size(20.dp), tint = if (show) c.ac else c.mu)
                }
            }
        }
    }
}

@Composable
fun StrengthBar(score: Int, modifier: Modifier = Modifier) {
    val c = VCT.c
    val frac by animateFloatAsState(score / 4f, tween(300), label = "strength")
    val col by animateColorAsState(
        when { score < 2 -> Color(0xFFE0564A); score < 3 -> Color(0xFFE0A030); else -> c.ok }, tween(300), label = "strengthColor",
    )
    Box(modifier.padding(top = 8.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.bd)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(frac).background(col))
    }
}

/** Segmented control whose white pill slides to the selected option. */
@Composable
fun SlidingTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = VCT.c
    val frac by animateFloatAsState(selected.toFloat(), tween(250), label = "tabs")
    BoxWithConstraints(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.sf2).padding(3.dp)) {
        val w = maxWidth / options.size
        Box(
            Modifier.offset(x = w * frac).width(w).height(36.dp)
                .shadow(1.dp, RoundedCornerShape(9.dp)).background(c.sf, RoundedCornerShape(9.dp)),
        )
        Row {
            options.forEachIndexed { i, t ->
                Box(
                    Modifier.weight(1f).height(36.dp).clickable(
                        indication = null, interactionSource = remember { MutableInteractionSource() },
                    ) { onSelect(i) },
                    Alignment.Center,
                ) {
                    Text(t, color = if (i == selected) c.tx else c.mu, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }
    }
}

/** Fades and slides its content up after a delay (the staggered entrance in the design). */
@Composable
fun Entrance(delayMs: Int, content: @Composable () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(delayMs.toLong()); a.animateTo(1f, tween(500)) }
    Box(Modifier.graphicsLayer { alpha = a.value; translationY = (1f - a.value) * 24f }) { content() }
}

@Composable
fun VcSwitch(on: Boolean, onChange: (Boolean) -> Unit) {
    val c = VCT.c
    val x by animateDpAsState(if (on) 18.dp else 0.dp, tween(200), label = "sw")
    val bg by animateColorAsState(if (on) c.ac else c.bd, tween(200), label = "swColor")
    Box(Modifier.width(42.dp).height(24.dp).clip(RoundedCornerShape(12.dp)).background(bg).clickable { onChange(!on) }.padding(3.dp)) {
        Box(Modifier.offset(x = x).size(18.dp).clip(CircleShape).background(Color.White))
    }
}

@Composable
fun Meter(frac: Float, modifier: Modifier = Modifier) {
    val c = VCT.c
    val f by animateFloatAsState(frac.coerceIn(0f, 1f), tween(600), label = "meter")
    Box(modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.bd)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(f).background(if (frac > 0.9f) Color(0xFFE0564A) else c.ac))
    }
}

@Composable
fun Chip(text: String, active: Boolean = false, modifier: Modifier = Modifier) {
    val c = VCT.c
    Text(
        text, fontSize = 10.5.sp, fontWeight = FontWeight.Bold,
        color = if (active) c.ac else c.mu,
        modifier = modifier.clip(RoundedCornerShape(999.dp)).background(if (active) c.ac2 else c.bd).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
fun CardTitle(icon: ImageVector, text: String) {
    val c = VCT.c
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 4.dp)) {
        Icon(icon, null, Modifier.size(18.dp), tint = c.ac)
        Text(text, color = c.tx, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
    }
}
