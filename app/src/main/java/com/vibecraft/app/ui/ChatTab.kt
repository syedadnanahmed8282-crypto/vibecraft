package com.vibecraft.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibecraft.app.AppVm
import com.vibecraft.app.Step
import kotlinx.coroutines.flow.flowOf
import com.vibecraft.app.data.MessageEntity

private val templates: List<Pair<String, ImageVector>> = listOf(
    "To-do list" to Ic.Todo, "Chat app" to Ic.Chat, "Notes" to Ic.Notes,
    "Habit tracker" to Ic.Heart, "Calculator" to Ic.Calc, "Simple game" to Ic.Game,
)
private val quick = listOf("Add dark mode", "Fix errors", "Add a screen", "Make it faster")

@Composable
fun ChatTab(vm: AppVm, openId: String?, onSetup: () -> Unit, onOpened: (String) -> Unit) {
    val c = VCT.c
    val id = openId
    val msgs by remember(id) { if (id != null) vm.messages(id) else flowOf(emptyList<MessageEntity>()) }
        .collectAsState(initial = emptyList())
    var text by remember { mutableStateOf("") }
    val state = rememberLazyListState()
    val thinking = id != null && vm.thinkingId == id
    LaunchedEffect(msgs.size, thinking) { if (msgs.isNotEmpty()) state.animateScrollToItem(msgs.size - 1 + if (thinking) 1 else 0) }

    fun send(t: String) {
        if (vm.thinkingId != null) return
        val newId = vm.send(id, t)
        if (newId != null && newId != id) onOpened(newId)
        text = ""
    }

    Column(Modifier.fillMaxSize()) {
        if (msgs.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)) { Hero(::send) }
        } else {
            LazyColumn(
                state = state, modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(msgs, key = { it.id }) { m ->
                    when (m.role) {
                        "assistant" -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                            val shape = RoundedCornerShape(14.dp, 14.dp, 14.dp, 4.dp)
                            Box(
                                Modifier.fillMaxWidth(0.95f).clip(shape).background(c.sf).border(1.dp, c.bd, shape)
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                            ) { MarkdownText(m.content) }
                        }
                        "note" -> if (m.content.startsWith("FILE|")) FileCard(m.content) else Box(Modifier.fillMaxWidth(), Alignment.Center) {
                            Text(
                                m.content, color = c.ac, fontSize = 12.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
                                modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(c.ac2).padding(horizontal = 14.dp, vertical = 6.dp),
                            )
                        }
                        else -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Box(
                                Modifier.widthIn(max = 300.dp)
                                    .clip(RoundedCornerShape(14.dp, 14.dp, 4.dp, 14.dp)).background(c.g)
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                            ) { UserText(m.content) }
                        }
                    }
                }
                if (thinking) item(key = "typing") { if (vm.steps.isEmpty()) TypingDots() else StepsCard(vm.steps) }
            }
        }
        if (vm.profiles.isEmpty()) {
            Text(
                "Add an AI profile in Settings to get replies.", color = c.ac, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().background(c.ac2).clickable(onClick = onSetup).padding(vertical = 9.dp),
            )
        }
        Row(
            Modifier.fillMaxWidth().background(c.sf).horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, top = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            quick.forEach { q ->
                Text(
                    q, color = c.ac, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(c.ac2).clickable { send(q) }.padding(horizontal = 13.dp, vertical = 6.dp),
                )
            }
        }
        Row(Modifier.fillMaxWidth().background(c.sf).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            val shape = RoundedCornerShape(10.dp)
            Box(
                Modifier.weight(1f).clip(shape).background(c.sf2).border(1.dp, c.bd, shape).padding(horizontal = 13.dp, vertical = 12.dp),
            ) {
                BasicTextField(
                    value = text, onValueChange = { text = it }, maxLines = 4,
                    textStyle = TextStyle(color = c.tx, fontSize = 14.sp), cursorBrush = SolidColor(c.ac),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        Box {
                            if (text.isEmpty()) Text("Describe your app…", color = c.mu.copy(alpha = 0.7f), fontSize = 14.sp)
                            inner()
                        }
                    },
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(44.dp).clip(shape).background(c.g).clickable { send(text) },
                Alignment.Center,
            ) { Icon(Ic.Send, "Send", Modifier.size(19.dp), tint = Color.White) }
        }
    }
}

@Composable
private fun Hero(onPick: (String) -> Unit) {
    val c = VCT.c
    val inf = rememberInfiniteTransition(label = "hero")
    val dy by inf.animateFloat(
        initialValue = 0f, targetValue = -12f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "float",
    )
    Column(Modifier.fillMaxWidth().padding(top = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(68.dp).graphicsLayer { translationY = dy }.clip(RoundedCornerShape(22.dp)).background(c.g),
            Alignment.Center,
        ) { Logo(44.dp, animated = false, onGreen = true) }
        Text("What will you build today?", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = c.tx, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
        Text(
            "Describe the app you want to build and I will design it for you.",
            color = c.mu, fontSize = 13.5.sp, textAlign = TextAlign.Center,
        )
        Text(
            "START FROM A TEMPLATE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = c.mu,
            modifier = Modifier.align(Alignment.Start).padding(top = 22.dp, bottom = 10.dp, start = 2.dp),
        )
    }
    TemplateMarquee(onPick)
}

/** Template chips that slide by themselves, can be dragged by hand, and resume sliding after 1.6s. */
@Composable
private fun TemplateMarquee(onPick: (String) -> Unit) {
    val c = VCT.c
    val st = rememberScrollState()
    var viewport by remember { mutableIntStateOf(0) }
    val dragged by st.interactionSource.collectIsDraggedAsState()
    var resumeAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(dragged) {
        if (dragged) resumeAt = Long.MAX_VALUE
        else if (resumeAt == Long.MAX_VALUE) resumeAt = System.currentTimeMillis() + 1600
    }
    LaunchedEffect(viewport) {
        while (true) {
            androidx.compose.runtime.withFrameNanos { _ ->
                if (viewport > 0 && st.maxValue > 0 && System.currentTimeMillis() >= resumeAt) {
                    val half = (st.maxValue + viewport) / 2f
                    st.dispatchRawDelta(1.2f)
                    if (st.value >= half) st.dispatchRawDelta(-half)
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth().onSizeChanged { viewport = it.width }.horizontalScroll(st)) {
        repeat(2) {
            templates.forEach { (name, icon) ->
                val shape = RoundedCornerShape(999.dp)
                Row(
                    Modifier.padding(end = 8.dp).clip(shape).background(c.sf).border(1.dp, c.bd, shape)
                        .clickable { onPick(name) }.padding(start = 8.dp, end = 14.dp, top = 7.dp, bottom = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(24.dp).clip(RoundedCornerShape(8.dp)).background(c.ac2), Alignment.Center) {
                        Icon(icon, null, Modifier.size(14.dp), tint = c.ac)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(name, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = c.tx)
                }
            }
        }
    }
}

@Composable
private fun TypingDots() {
    val c = VCT.c
    val inf = rememberInfiniteTransition(label = "typing")
    val shape = RoundedCornerShape(14.dp, 14.dp, 14.dp, 4.dp)
    Row(
        Modifier.clip(shape).background(c.sf).border(1.dp, c.bd, shape).padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(3) { i ->
            val y by inf.animateFloat(
                initialValue = 0f, targetValue = -5f,
                animationSpec = infiniteRepeatable(tween(500, easing = FastOutSlowInEasing), RepeatMode.Reverse, StartOffset(i * 150)),
                label = "dot$i",
            )
            Box(Modifier.size(7.dp).graphicsLayer { translationY = y }.clip(androidx.compose.foundation.shape.CircleShape).background(c.ac))
        }
    }
}

/** Live progress while the AI works: planning, then one line per file. */
@Composable
private fun StepsCard(steps: List<Step>) {
    val c = VCT.c
    val shape = RoundedCornerShape(14.dp, 14.dp, 14.dp, 4.dp)
    Column(Modifier.fillMaxWidth(0.95f).clip(shape).background(c.sf).border(1.dp, c.bd, shape).padding(horizontal = 14.dp, vertical = 8.dp)) {
        steps.forEach { st ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(18.dp).clip(androidx.compose.foundation.shape.CircleShape)
                        .background(when (st.state) { 2 -> c.ok; 3 -> Color(0xFFE0564A); else -> Color.Transparent })
                        .border(2.dp, when (st.state) { 2 -> c.ok; 3 -> Color(0xFFE0564A); 1 -> c.ac; else -> c.bd }, androidx.compose.foundation.shape.CircleShape),
                    Alignment.Center,
                ) {
                    when (st.state) {
                        2 -> Icon(Ic.Check, null, Modifier.size(11.dp), tint = Color.White)
                        3 -> Icon(Ic.Close, null, Modifier.size(11.dp), tint = Color.White)
                        1 -> CircularProgressIndicator(Modifier.size(11.dp), color = c.ac, strokeWidth = 1.5.dp)
                        else -> {}
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(st.label, color = if (st.state == 0) c.mu else c.tx, fontSize = 13.sp, fontWeight = if (st.state == 1) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

/** "FILE|created|path" notes show as a small card instead of code. */
@Composable
private fun FileCard(content: String) {
    val c = VCT.c
    val parts = content.split("|", limit = 3)
    val status = parts.getOrElse(1) { "updated" }
    val path = parts.getOrElse(2) { "" }
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier.fillMaxWidth(0.95f).clip(shape).background(c.sf).border(1.dp, c.bd, shape).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Ic.File, null, Modifier.size(17.dp), tint = c.ac)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(path.substringAfterLast('/'), color = c.tx, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(path.substringBeforeLast('/', ""), color = c.mu, fontSize = 10.5.sp, maxLines = 1)
        }
        Chip(status, active = status == "created")
    }
}

/** Long messages, such as build errors sent to the AI, are shortened until the user opens them. */
@Composable
private fun UserText(t: String) {
    var open by remember { mutableStateOf(false) }
    val long = t.length > 360
    Column {
        Text(if (long && !open) t.take(300).trimEnd() + "…" else t, color = Color.White, fontSize = 14.5.sp)
        if (long) Text(
            if (open) "Show less" else "Show more", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable { open = !open }.padding(top = 6.dp),
        )
    }
}
