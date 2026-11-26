package com.vibecraft.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import com.vibecraft.app.gen.Quote
import com.vibecraft.app.gen.Quoting
import kotlinx.coroutines.launch
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibecraft.app.AppVm
import com.vibecraft.app.Step
import com.vibecraft.app.data.MessageEntity
import com.vibecraft.app.gen.Brain
import com.vibecraft.app.gen.ChatItem
import com.vibecraft.app.gen.Intents
import com.vibecraft.app.gen.Changes
import com.vibecraft.app.gen.Notes
import com.vibecraft.app.gen.Plain
import com.vibecraft.app.gen.Problem
import com.vibecraft.app.gen.RawMsg
import com.vibecraft.app.gen.Trouble
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf

/** The chat: messages scroll under the floating top pills and the floating composer. Nothing else on screen. */
@Composable
fun ChatTab(vm: AppVm, openId: String?, onSetup: () -> Unit, onOpenCode: () -> Unit, onOpenAi: () -> Unit, onOpened: (String) -> Unit) {
    val c = VCT.c
    val id = openId
    val msgs by remember(id) { if (id != null) vm.messages(id) else flowOf(emptyList<MessageEntity>()) }
        .collectAsState(initial = emptyList())
    var text by remember { mutableStateOf("") }
    var replyTo by remember(id) { mutableStateOf<Quote?>(null) }
    var lastReply by remember { mutableStateOf<Quote?>(null) }
    SideEffect { if (replyTo != null) lastReply = replyTo }
    val focus = remember { FocusRequester() }
    LaunchedEffect(replyTo) { if (replyTo != null) runCatching { focus.requestFocus() } }
    val state = rememberLazyListState()
    val thinking = id != null && vm.thinkingId == id
    val rows = remember(msgs) { Notes.group(msgs.map { RawMsg(it.id, it.role, it.content) }) }
    val brain by remember(id) { if (id != null) vm.brainFlow(id) else flowOf(Brain()) }.collectAsState(initial = Brain())
    val last = rows.lastOrNull()
    val waiting = !thinking && last is Plain && last.msg.role == "assistant" && (brain.planState == "draft" || brain.pending != null)
    val lastUser = msgs.lastOrNull { it.role == "user" }?.content ?: ""
    val bn = Intents.isBengali(Quoting.decode(lastUser)?.text ?: lastUser)
    LaunchedEffect(rows.size, thinking, waiting) { if (rows.isNotEmpty()) state.animateScrollToItem(rows.size - 1 + if (thinking || waiting) 1 else 0) }

    fun send(t: String) {
        if (vm.thinkingId != null) return
        val q = replyTo
        val body = if (q != null && t.isNotBlank()) Quoting.encode(q.role, q.text, t) else t
        val newId = vm.send(id, body)
        if (newId != null && newId != id) onOpened(newId)
        text = ""
        replyTo = null
    }

    Box(Modifier.fillMaxSize()) {
        if (msgs.isEmpty()) {
            Hero(Modifier.align(Alignment.Center).padding(horizontal = 24.dp))
        } else {
            val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val bottomInset = WindowInsets.navigationBars.union(WindowInsets.ime).asPaddingValues().calculateBottomPadding()
            LazyColumn(
                state = state, modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topInset + 78.dp, bottom = bottomInset + (if (replyTo != null) 164.dp else 100.dp)),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(rows, key = { it.key }) { item ->
                    when (item) {
                        is Changes -> SwipeReply(true, { replyTo = Quote("assistant", Notes.changesText(item)) }) { ChangesCard(item, onOpenCode) }
                        is Trouble -> SwipeReply(true, { replyTo = Quote("assistant", Notes.problemText(item.problem)) }) {
                            ProblemCard(item.problem, vm, id, onOpenAi)
                        }
                        is Plain -> {
                            val m = item.msg
                            when (m.role) {
                                "assistant" -> SwipeReply(true, { replyTo = Quote("assistant", m.content) }) {
                                    Row(Modifier.fillMaxWidth(), Arrangement.Start) {
                                        val shape = RoundedCornerShape(14.dp, 14.dp, 14.dp, 4.dp)
                                        Column(
                                            Modifier.fillMaxWidth(0.95f).clip(shape).background(c.sf).border(1.dp, c.bd, shape)
                                                .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 2.dp),
                                        ) {
                                            MarkdownText(m.content)
                                            Row(Modifier.fillMaxWidth(), Arrangement.End) { CopyBtn(m.content, c.mu) }
                                        }
                                    }
                                }
                                "note" -> Box(Modifier.fillMaxWidth(), Alignment.Center) {
                                    Text(
                                        m.content, color = c.ac, fontSize = 12.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
                                        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(c.ac2).padding(horizontal = 14.dp, vertical = 6.dp),
                                    )
                                }
                                else -> {
                                    val parts = Quoting.decode(m.content)
                                    val mine = parts?.text ?: m.content
                                    SwipeReply(false, { replyTo = Quote("user", mine) }) {
                                        Row(Modifier.fillMaxWidth(), Arrangement.End) {
                                            Column(
                                                Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(14.dp, 14.dp, 4.dp, 14.dp)).background(c.g)
                                                    .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 2.dp),
                                            ) {
                                                if (parts != null) QuoteBlock(parts.role, parts.quote, Modifier.padding(bottom = 8.dp))
                                                UserText(mine)
                                                Row(Modifier.fillMaxWidth(), Arrangement.End) { CopyBtn(mine, Color.White.copy(alpha = 0.8f)) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (waiting) item(key = "quick") {
                    QuickReply(
                        bn,
                        onOk = { send(if (bn) "ঠিক আছে, তোমার প্রস্তাব অনুযায়ী এগিয়ে যাও" else "OK, go ahead with your suggestions") },
                        onChange = { runCatching { focus.requestFocus() } },
                    )
                }
                if (thinking) item(key = "typing") { if (vm.steps.isEmpty()) TypingDots() else StepsCard(vm.steps) }
            }
        }

        if (vm.profiles.isEmpty()) {
            Text(
                "Add an AI profile in Settings to get replies", color = c.ac, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding().padding(bottom = 80.dp)
                    .glass(RoundedCornerShape(999.dp), strong = true, lift = 8.dp).clickable(onClick = onSetup)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        // The message being replied to: shown just above the composer, cancelled with the cross.
        AnimatedVisibility(
            replyTo != null,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding().padding(start = 12.dp, end = 12.dp, bottom = 74.dp),
            enter = fadeIn(tween(160)) + slideInVertically(tween(200)) { it / 2 },
            exit = fadeOut(tween(120)) + slideOutVertically(tween(160)) { it / 2 },
        ) {
            val q = replyTo ?: lastReply
            if (q != null) {
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min).glass(RoundedCornerShape(18.dp), strong = true, lift = 8.dp)
                        .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(3.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(c.ac))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (q.role == "user") "Replying to you" else "Replying to Vibecraft", color = c.ac, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(Quoting.preview(q.text), color = c.mu, fontSize = 12.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Box(Modifier.size(36.dp).clip(CircleShape).clickable { replyTo = null }, Alignment.Center) {
                        Icon(Ic.Close, "Cancel reply", Modifier.size(18.dp), tint = c.mu)
                    }
                }
            }
        }

        // Floating glass composer: the input and the send button only.
        Row(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding().fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp).height(56.dp)
                .glass(RoundedCornerShape(28.dp), strong = true).padding(start = 18.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = text, onValueChange = { text = it }, maxLines = 4,
                textStyle = TextStyle(color = c.tx, fontSize = 15.sp), cursorBrush = SolidColor(c.ac),
                modifier = Modifier.weight(1f).focusRequester(focus),
                decorationBox = { inner ->
                    Box {
                        if (text.isEmpty()) Text("Describe your app…", color = c.mu, fontSize = 15.sp)
                        inner()
                    }
                },
            )
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(44.dp).clip(CircleShape).background(c.g).clickable { send(text) }, Alignment.Center) {
                Icon(Ic.Send, "Send", Modifier.size(19.dp), tint = Color.White)
            }
        }
    }
}

@Composable
private fun Hero(modifier: Modifier) {
    val c = VCT.c
    val inf = rememberInfiniteTransition(label = "hero")
    val dy by inf.animateFloat(
        initialValue = 0f, targetValue = -12f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "float",
    )
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(72.dp).graphicsLayer { translationY = dy }.clip(RoundedCornerShape(24.dp)).background(c.g),
            Alignment.Center,
        ) { Logo(46.dp, animated = false, onGreen = true) }
        Text("What will you build today?", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.tx, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
        Text("Describe the app you want to build and I will design it for you.", color = c.mu, fontSize = 13.5.sp, textAlign = TextAlign.Center)
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
            Box(Modifier.size(7.dp).graphicsLayer { translationY = y }.clip(CircleShape).background(c.ac))
        }
    }
}

private fun phaseHint(phase: String) = when (phase) {
    "read" -> "your request and the project"
    "reason" -> "waiting for the model"
    "plan" -> "what to change"
    "work" -> "writing the files"
    "check" -> "looking for code problems"
    else -> "your changes"
}

/** Live progress while the AI works: one row per phase, each with its own color and motion. */
@Composable
private fun StepsCard(steps: List<Step>) {
    val c = VCT.c
    val shape = RoundedCornerShape(16.dp)
    Column(Modifier.fillMaxWidth(0.94f).clip(shape).background(c.sf).border(1.dp, c.bd, shape).padding(horizontal = 12.dp, vertical = 5.dp)) {
        steps.forEach { st ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp).alpha(if (st.state == 0) 0.6f else 1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PhaseLoader(st.phase, st.state, 34.dp, st.progress)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(st.label, color = if (st.state == 1) phaseTint(st.phase) else c.tx, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                    Text(st.sub.ifBlank { phaseHint(st.phase) }, color = c.mu, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (st.state == 1 && st.startedAt > 0L) Elapsed(st.startedAt)
            }
        }
    }
}

/** Seconds spent in the active phase: slow models are visible, not mysterious. */
@Composable
private fun Elapsed(since: Long) {
    var now by remember(since) { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(since) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val sec = ((now - since) / 1000).coerceAtLeast(0)
    if (sec >= 3) Text("$sec s", color = VCT.c.mu, fontSize = 11.sp)
}

@Composable
private fun FileBadge(path: String) {
    val c = VCT.c
    val base = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp))
    val m = when (Notes.kindOf(path)) {
        "kt" -> base.background(c.g)
        "json" -> base.background(Color(0xFFF59E0B))
        "html" -> base.background(Color(0xFF3B82F6))
        "xml" -> base.background(Color(0xFF14B8A6))
        else -> base.background(c.mu)
    }
    Box(m, Alignment.Center) { Text(Notes.badge(path), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
}

/** Everything one run changed, as a single card: names and roles, never paths. */
@Composable
private fun ChangesCard(ch: Changes, onOpenCode: () -> Unit) {
    val c = VCT.c
    var open by remember(ch.key) { mutableStateOf(true) }
    var expanded by remember(ch.key) { mutableStateOf<String?>(null) }
    val shape = RoundedCornerShape(16.dp)
    Column(Modifier.fillMaxWidth(0.94f).clip(shape).background(c.sf).border(1.dp, c.bd, shape)) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(c.g), Alignment.Center) {
                Text("${ch.files.size}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(Notes.headline(ch.files), color = c.tx, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                Text(if (ch.version != null) "Saved as version ${ch.version}" else "Not saved", color = c.mu, fontSize = 11.5.sp)
            }
            CopyBtn(Notes.changesText(ch), c.mu)
            Icon(Ic.ChevDown, null, Modifier.size(18.dp).graphicsLayer { rotationZ = if (open) 0f else -90f }, tint = c.mu)
        }
        if (open) ch.files.forEach { f ->
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.bd))
            Row(
                Modifier.fillMaxWidth().clickable { expanded = if (expanded == f.path) null else f.path }.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FileBadge(f.path)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(f.path.substringAfterLast('/'), color = c.tx, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(Notes.roleOf(f.path), color = c.mu, fontSize = 11.5.sp)
                }
                Chip(f.status, active = f.status == "created")
            }
            if (expanded == f.path) {
                Column(Modifier.padding(start = 57.dp, end = 12.dp, bottom = 10.dp)) {
                    if (f.desc.isNotBlank()) Text(f.desc, color = c.mu, fontSize = 12.sp)
                    Text("Open in Code ›", color = c.ac, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onOpenCode).padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun RowScope.SmallBtn(text: String, primary: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val c = VCT.c
    val shape = RoundedCornerShape(11.dp)
    Box(
        Modifier.weight(1f).height(38.dp).alpha(if (enabled) 1f else 0.5f).clip(shape)
            .then(if (primary) Modifier.background(c.g) else Modifier.background(c.sf2).border(1.dp, c.bd, shape))
            .clickable(enabled = enabled, onClick = onClick),
        Alignment.Center,
    ) { Text(text, color = if (primary) Color.White else c.tx, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
}

/** A failed or paused run as one calm card: what happened, how far it got, and what to do next. */
@Composable
private fun ProblemCard(p: Problem, vm: AppVm, projectId: String?, onOpenAi: () -> Unit) {
    val c = VCT.c
    val tint = when (p.kind) {
        "timeout" -> Color(0xFFF59E0B); "limit" -> Color(0xFF8B5CF6); "setup" -> Color(0xFF3B82F6); else -> Color(0xFFE0564A)
    }
    val icon = when (p.kind) {
        "timeout" -> Ic.Clock; "limit" -> Ic.Gauge; "key" -> Ic.Key; "setup" -> Ic.Gear; else -> Ic.Alert
    }
    val paused = p.done >= 0
    val busy = vm.thinkingId != null
    var details by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf(false) }
    fun resume() {
        if (projectId == null || busy) return
        if (paused) vm.send(projectId, "Continue") else vm.retry(projectId)
    }
    val needsSettings = p.kind == "key" || p.kind == "setup"
    val label = when { p.kind == "key" -> "Update key"; p.kind == "setup" -> "Open AI profiles"; paused -> "Continue"; else -> "Try again" }
    val shape = RoundedCornerShape(16.dp)
    Row(Modifier.fillMaxWidth(0.94f).height(IntrinsicSize.Min).clip(shape).background(c.sf).border(1.dp, c.bd, shape)) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(tint))
        Column(Modifier.weight(1f).padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(c.sf2), Alignment.Center) { Icon(icon, null, Modifier.size(19.dp), tint = tint) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.title, color = c.tx, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                    if (p.sub.isNotBlank()) Text(p.sub, color = c.mu, fontSize = 12.sp)
                }
                CopyBtn(Notes.problemText(p) + if (p.raw.isNotBlank()) "\n" + p.raw else "", c.mu)
            }
            if (paused && p.total > 0) {
                Row(Modifier.fillMaxWidth().padding(top = 11.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(p.total) { i ->
                        Box(
                            Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp))
                                .background(when { i < p.done -> c.ok; i == p.done -> Color(0xFFE0564A); else -> c.bd }),
                        )
                    }
                }
                Text("${p.done} of ${p.total} files saved" + if (p.stoppedAt.isNotBlank()) ". Stopped at ${p.stoppedAt}" else "", color = c.mu, fontSize = 11.5.sp)
            }
            Row(Modifier.fillMaxWidth().padding(top = 11.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallBtn(label, true, enabled = !busy) { if (needsSettings) onOpenAi() else resume() }
                if (vm.profiles.size > 1) SmallBtn("Switch model", false, enabled = !busy) { pick = !pick }
            }
            if (pick) {
                vm.profiles.forEach { m ->
                    Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(10.dp)).clickable { vm.pickModel(m.id); pick = false; resume() }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProviderBadge(m.provider, 24.dp)
                        Spacer(Modifier.width(9.dp))
                        Text(m.name, color = c.tx, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (p.raw.isNotBlank()) {
                Text(
                    if (details) "Details ▴" else "Details ▾", color = c.mu, fontSize = 12.sp,
                    modifier = Modifier.clickable { details = !details }.padding(top = 9.dp),
                )
                if (details) {
                    Text(
                        p.raw, color = c.mu, fontSize = 11.sp, lineHeight = 16.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        modifier = Modifier.padding(top = 6.dp).fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(c.sf2).padding(8.dp),
                    )
                }
            }
        }
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

/** One tap copies the whole message; the icon turns into a tick for a moment. */
@Composable
private fun CopyBtn(text: String, tint: Color) {
    val c = VCT.c
    val clip = LocalClipboardManager.current
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(done) { if (done) { delay(1400); done = false } }
    Box(
        Modifier.size(30.dp).clip(CircleShape).clickable { clip.setText(AnnotatedString(text)); done = true },
        Alignment.Center,
    ) { Icon(if (done) Ic.Check else Ic.Copy, "Copy", Modifier.size(15.dp), tint = if (done) c.ok else tint) }
}

/** The quoted message inside a sent reply. */
@Composable
private fun QuoteBlock(role: String, quote: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.18f)).padding(8.dp),
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(Color.White))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(if (role == "user") "You" else "Vibecraft", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(Quoting.preview(quote, 160), color = Color.White.copy(alpha = 0.88f), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Drag a message sideways to reply to it: the message follows the finger with some resistance, a reply icon grows,
 * and letting go past the mark starts the reply. AI messages drag to the right, the user's own to the left.
 * The pull is read only while drawing, so nothing recomposes during the drag.
 */
@Composable
private fun SwipeReply(fromStart: Boolean, onReply: () -> Unit, content: @Composable () -> Unit) {
    val c = VCT.c
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val trigger = with(density) { 64.dp.toPx() }
    val maxPull = with(density) { 90.dp.toPx() }
    val pull = remember { Animatable(0f) }
    var fired by remember { mutableStateOf(false) }
    val dir = if (fromStart) 1f else -1f
    Box(
        Modifier.fillMaxWidth().pointerInput(fromStart) {
            var acc = 0f
            detectHorizontalDragGestures(
                onDragStart = { fired = false; acc = pull.value },
                onDragEnd = {
                    if (acc >= trigger) onReply()
                    scope.launch { pull.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
                },
                onDragCancel = { scope.launch { pull.animateTo(0f) } },
            ) { change, dragAmount ->
                change.consume()
                acc = (acc + dragAmount * dir * 0.6f).coerceIn(0f, maxPull)
                scope.launch { pull.snapTo(acc) }
                if (!fired && acc >= trigger) { fired = true; haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
            }
        },
    ) {
        Box(
            Modifier.align(if (fromStart) Alignment.CenterStart else Alignment.CenterEnd).padding(horizontal = 10.dp).size(34.dp)
                .graphicsLayer {
                    val pr = (pull.value / trigger).coerceIn(0f, 1f)
                    alpha = pr; scaleX = 0.6f + 0.4f * pr; scaleY = 0.6f + 0.4f * pr
                }
                .clip(CircleShape).background(c.ac2),
            Alignment.Center,
        ) { Icon(Ic.Reply, "Reply", Modifier.size(18.dp), tint = c.ac) }
        Box(Modifier.graphicsLayer { translationX = pull.value * dir }) { content() }
    }
}

/** Shown only while the AI waits for an answer (the plan questions or a decision): one tap to accept its suggestions. */
@Composable
private fun QuickReply(bn: Boolean, onOk: () -> Unit, onChange: () -> Unit) {
    val c = VCT.c
    val pill = RoundedCornerShape(999.dp)
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.clip(pill).background(c.g).clickable(onClick = onOk).padding(horizontal = 16.dp, vertical = 9.dp)) {
            Text(if (bn) "ঠিক আছে, শুরু করো" else "OK, go ahead", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Box(Modifier.clip(pill).background(c.sf).border(1.dp, c.bd, pill).clickable(onClick = onChange).padding(horizontal = 16.dp, vertical = 9.dp)) {
            Text(if (bn) "কিছু বদলাতে চাই" else "Change something", color = c.tx, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
