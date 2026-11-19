package com.vibecraft.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private class Block(val code: Boolean, val label: String, val text: String)

private fun split(text: String): List<Block> {
    val out = mutableListOf<Block>()
    val buf = StringBuilder()
    var inCode = false
    var label = ""
    fun flush() {
        if (buf.isNotEmpty() || inCode) out.add(Block(inCode, label, buf.toString().trimEnd('\n')))
        buf.clear()
    }
    text.lines().forEach { line ->
        if (line.trimStart().startsWith("```")) {
            flush()
            inCode = !inCode
            label = if (inCode) line.trim().removePrefix("```").trim() else ""
        } else {
            buf.append(line).append('\n')
        }
    }
    if (buf.isNotEmpty()) out.add(Block(inCode, label, buf.toString().trimEnd('\n')))
    return out.filter { it.text.isNotBlank() }
}

private fun styled(s: String, codeColor: androidx.compose.ui.graphics.Color, codeBg: androidx.compose.ui.graphics.Color): AnnotatedString =
    buildAnnotatedString {
        var i = 0
        while (i < s.length) {
            if (s.startsWith("**", i)) {
                val e = s.indexOf("**", i + 2)
                if (e > 0) { withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(s.substring(i + 2, e)) }; i = e + 2; continue }
            }
            if (s[i] == '`') {
                val e = s.indexOf('`', i + 1)
                if (e > 0) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = codeColor, background = codeBg)) { append(s.substring(i + 1, e)) }
                    i = e + 1; continue
                }
            }
            append(s[i]); i++
        }
    }

/** Small markdown view for AI replies: headings, bullets, **bold**, `code` and fenced code blocks with a Copy button. */
@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    val c = VCT.c
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        split(text).forEach { b ->
            if (b.code) CodeCard(b.label, b.text)
            else b.text.lines().forEach { raw ->
                val line = raw.trimEnd()
                when {
                    line.isBlank() -> Spacer(Modifier.height(2.dp))
                    line.startsWith("#") -> Text(
                        styled(line.trimStart('#').trim(), c.ac, c.sf2), color = c.tx, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    )
                    line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") -> Row {
                        Text("•  ", color = c.ac, fontSize = 14.5.sp)
                        Text(styled(line.trimStart().drop(2), c.ac, c.sf2), color = c.tx, fontSize = 14.5.sp, lineHeight = 21.sp)
                    }
                    else -> Text(styled(line, c.ac, c.sf2), color = c.tx, fontSize = 14.5.sp, lineHeight = 21.sp)
                }
            }
        }
    }
}

@Composable
private fun CodeCard(label: String, code: String) {
    val c = VCT.c
    val clipboard = LocalClipboardManager.current
    var all by remember { mutableStateOf(false) }
    val lines = code.lines()
    val shown = if (all || lines.size <= 14) code else lines.take(14).joinToString("\n")
    val shape = RoundedCornerShape(10.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(c.sf2).border(1.dp, c.bd, shape)) {
        Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label.ifBlank { "code" }, color = c.mu, fontSize = 11.sp, modifier = Modifier.weight(1f), maxLines = 1)
            Text(
                "Copy", color = c.ac, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { clipboard.setText(AnnotatedString(code)) }.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        Text(
            shown, color = c.tx, fontSize = 11.5.sp, fontFamily = FontFamily.Monospace, lineHeight = 17.sp,
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 6.dp),
        )
        if (lines.size > 14) Text(
            if (all) "Show less" else "Show all ${lines.size} lines", color = c.ac, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth().clickable { all = !all }.padding(10.dp),
        )
    }
}
