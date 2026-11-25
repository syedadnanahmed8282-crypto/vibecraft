package com.vibecraft.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import kotlin.math.cos
import kotlin.math.sin

fun phaseTint(phase: String): Color = when (phase) {
    "read" -> Color(0xFF3B82F6)
    "reason" -> Color(0xFF8B5CF6)
    "plan" -> Color(0xFFF59E0B)
    "work" -> Color(0xFF22A45D)
    "check" -> Color(0xFF06B6D4)
    "save" -> Color(0xFFEC4899)
    else -> Color(0xFF6B7A72)
}

private val phaseLeaf = Path().apply {
    moveTo(60f, 100f); cubicTo(36f, 84f, 30f, 52f, 60f, 18f); cubicTo(90f, 52f, 84f, 84f, 60f, 100f); close()
}
private val phaseSpark = Path().apply {
    moveTo(60f, 12f); lineTo(63.5f, 22.5f); lineTo(74f, 26f); lineTo(63.5f, 29.5f)
    lineTo(60f, 40f); lineTo(56.5f, 29.5f); lineTo(46f, 26f); lineTo(56.5f, 22.5f); close()
}
private fun periodOf(phase: String) = when (phase) {
    "read" -> 1500; "reason" -> 2400; "plan" -> 1500; "work" -> 1400; "check" -> 1000; "save" -> 1400; else -> 1500
}

/**
 * The logo's leaf as a loader. state: 0 waiting, 1 active, 2 done, 3 failed.
 * progress (0..1) fills the ring of the "work" phase; pass -1 when unknown.
 * Only the active one animates, and only transforms and alpha change.
 */
@Composable
fun PhaseLoader(phase: String, state: Int, size: Dp, progress: Float = -1f) {
    val c = VCT.c
    val color = when (state) {
        3 -> Color(0xFFE0564A)
        0 -> c.mu.copy(alpha = 0.5f)
        else -> phaseTint(phase)
    }
    if (state == 1) {
        val inf = rememberInfiniteTransition(label = "phase-$phase")
        val t by inf.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(periodOf(phase), easing = LinearEasing), RepeatMode.Restart),
            label = "t",
        )
        Canvas(Modifier.size(size)) { drawPhase(phase, state, color, t, progress) }
    } else {
        Canvas(Modifier.size(size)) { drawPhase(phase, state, color, 0f, progress) }
    }
}

private fun DrawScope.drawPhase(phase: String, state: Int, color: Color, t: Float, progress: Float) {
    val s = this.size.minDimension / 120f
    val pivot = Offset(60f, 100f)
    val twoPi = (2.0 * Math.PI).toFloat()
    withTransform({ scale(s, s, Offset.Zero) }) {
        var leafScale = 1f
        var leafRot = 0f
        var leafDy = 0f
        var leafAlpha = 1f
        if (state == 1) {
            when (phase) {
                "read" -> leafRot = 10f * sin(twoPi * t)
                "reason" -> leafScale = 1f + 0.07f * (0.5f - 0.5f * cos(2f * twoPi * t))
                "plan", "work" -> leafScale = 0.88f + 0.16f * (0.5f - 0.5f * cos(twoPi * t))
                "check" -> leafScale = 0.92f
                "save" -> {
                    val u = (t / 0.8f).coerceAtMost(1f)
                    leafDy = -30f * (1f - u * u)
                    leafAlpha = when { t < 0.35f -> t / 0.35f; t < 0.8f -> 1f; else -> 1f - (t - 0.8f) / 0.2f }
                }
            }
            if (phase == "work" || phase == "check") {
                drawCircle(color.copy(alpha = 0.18f), radius = 54f, center = Offset(60f, 60f), style = Stroke(7f))
                val sweep = if (phase == "work") 360f * progress.coerceIn(0.04f, 1f) else 95f
                val start = if (phase == "work") -90f else 360f * t - 90f
                drawArc(color, start, sweep, false, Offset(6f, 6f), Size(108f, 108f), style = Stroke(7f, cap = StrokeCap.Round))
            }
            if (phase == "reason") {
                withTransform({ rotate(360f * t, Offset(60f, 60f)) }) {
                    drawCircle(color, 6f, Offset(60f, 6f)); drawCircle(color, 5f, Offset(107f, 88f)); drawCircle(color, 5f, Offset(13f, 88f))
                }
            }
            if (phase == "plan") {
                listOf(30f, 60f, 90f).forEachIndexed { i, y ->
                    val a = 0.2f + 0.8f * (0.5f + 0.5f * sin(twoPi * (t - i / 6f)))
                    drawCircle(color.copy(alpha = a), 6f, Offset(if (i == 1) 112f else 108f, y))
                }
            }
            if (phase == "save") drawRoundRect(color, Offset(28f, 106f), Size(64f, 6f), CornerRadius(3f, 3f))
        }
        withTransform({ translate(0f, leafDy); rotate(leafRot, pivot); scale(leafScale, leafScale, pivot) }) {
            drawPath(phaseLeaf, color, alpha = leafAlpha)
            drawLine(Color.White.copy(alpha = 0.55f * leafAlpha), Offset(60f, 94f), Offset(60f, 46f), strokeWidth = 4f, cap = StrokeCap.Round)
            if (state == 1 && phase == "read") {
                clipPath(phaseLeaf) {
                    drawRoundRect(Color.White.copy(alpha = 0.65f), Offset(10f, -16f + 128f * t), Size(100f, 14f), CornerRadius(7f, 7f))
                }
            }
        }
        if (state == 1 && phase == "reason") {
            val k = 0.5f + 0.65f * (0.5f - 0.5f * cos(2f * twoPi * t))
            withTransform({ scale(k, k, Offset(60f, 26f)) }) {
                drawPath(phaseSpark, Color.White.copy(alpha = (0.4f + 0.6f * k).coerceAtMost(1f)))
            }
        }
        if (state == 2 || state == 3) {
            drawCircle(color, 20f, Offset(98f, 98f))
            val mark = Path().apply {
                if (state == 2) { moveTo(88f, 98f); lineTo(95f, 105f); lineTo(108f, 91f) }
                else { moveTo(90f, 90f); lineTo(106f, 106f); moveTo(106f, 90f); lineTo(90f, 106f) }
            }
            drawPath(mark, Color.White, style = Stroke(6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
