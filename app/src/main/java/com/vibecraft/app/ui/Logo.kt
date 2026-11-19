package com.vibecraft.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp

private fun leafPath(mirror: Boolean) = Path().apply {
    fun x(v: Float) = if (mirror) 120f - v else v
    moveTo(60f, 92f)
    cubicTo(x(34f), 82f, x(22f), 56f, x(26f), 28f)
    cubicTo(x(52f), 34f, x(66f), 60f, 60f, 92f)
    close()
}

private val sparkPath = Path().apply {
    moveTo(60f, 12f); lineTo(63.5f, 22.5f); lineTo(74f, 26f); lineTo(63.5f, 29.5f)
    lineTo(60f, 40f); lineTo(56.5f, 29.5f); lineTo(46f, 26f); lineTo(56.5f, 22.5f)
    close()
}

/**
 * Two leaves forming a V, plus a spark.
 * animated: leaves grow in once, then sway. breathe: whole mark slowly scales (login screen).
 */
@Composable
fun Logo(size: Dp, animated: Boolean = true, onGreen: Boolean = false, breathe: Boolean = false) {
    val c = VCT.c
    val grow = remember { Animatable(if (animated) 0f else 1f) }
    LaunchedEffect(animated) {
        if (animated) grow.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
    }
    val inf = rememberInfiniteTransition(label = "logo")
    val swayRaw by inf.animateFloat(
        initialValue = -3f, targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Reverse),
        label = "sway",
    )
    val breath by inf.animateFloat(
        initialValue = 1f, targetValue = 1.07f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath",
    )
    val sway = if (animated) swayRaw else 0f
    val b = if (breathe) breath else 1f
    Canvas(Modifier.size(size).graphicsLayer { scaleX = b; scaleY = b }) {
        val s = this.size.minDimension / 120f
        val pivot = Offset(60f, 92f)
        val g = grow.value
        fun leaf(mirror: Boolean, color: Color) {
            withTransform({
                scale(s, s, Offset.Zero)
                rotate(if (mirror) -sway else sway, pivot)
                scale(g, g, pivot)
            }) { drawPath(leafPath(mirror), color) }
        }
        leaf(false, if (onGreen) Color.White else c.ac)
        leaf(true, if (onGreen) Color(0xFFB7E4C7) else c.ok)
        withTransform({
            scale(s, s, Offset.Zero)
            scale(g, g, Offset(60f, 26f))
        }) { drawPath(sparkPath, if (onGreen) Color.White else c.ok) }
    }
}
