package com.vibecraft.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box

/**
 * Liquid-glass look: a translucent surface, a bright edge and a soft highlight, lifted by a shadow.
 * Content that scrolls underneath stays visible through it. (It is translucent, not a true backdrop blur.)
 */
fun Modifier.glass(shape: Shape, strong: Boolean = false, lift: Dp = 14.dp): Modifier = composed {
    val c = VCT.c
    val base = if (c.dark) Color(0xFF10261B).copy(alpha = if (strong) 0.80f else 0.62f)
    else Color.White.copy(alpha = if (strong) 0.84f else 0.66f)
    val edge = if (c.dark) Color(0xFF96E6B9).copy(alpha = 0.22f) else Color.White.copy(alpha = 0.85f)
    val hi = Color.White.copy(alpha = if (c.dark) 0.12f else 0.55f)
    this
        .shadow(lift, shape, clip = false, ambientColor = Color(0x33081E12), spotColor = Color(0x44081E12))
        .clip(shape)
        .background(base)
        .background(Brush.linearGradient(listOf(hi, Color.Transparent, Color.White.copy(alpha = 0.06f))))
        .border(1.dp, edge, shape)
}

/** Soft green glow behind a page, so the glass cards on it are visible. */
@Composable
fun AmbientBg(modifier: Modifier = Modifier) {
    val c = VCT.c
    Canvas(modifier.fillMaxSize()) {
        val r = 260.dp.toPx()
        val glow = listOf(c.ok.copy(alpha = if (c.dark) 0.20f else 0.22f), Color.Transparent)
        val a = Offset(size.width * 0.95f, size.height * 0.08f)
        val b = Offset(size.width * 0.02f, size.height * 0.62f)
        drawCircle(Brush.radialGradient(glow, a, r), r, a)
        drawCircle(Brush.radialGradient(glow, b, r), r, b)
    }
}

fun providerIcon(provider: String): ImageVector = when (provider.lowercase()) {
    "openai" -> Ic.ProvO
    "anthropic" -> Ic.ProvA
    "gemini", "google" -> Ic.ProvG
    "openrouter" -> Ic.ProvR
    else -> Ic.ProvC
}

fun providerColor(provider: String): Color = when (provider.lowercase()) {
    "openai" -> Color(0xFF1D8A6E)
    "anthropic" -> Color(0xFFC0673F)
    "gemini", "google" -> Color(0xFF3D6FE0)
    "openrouter" -> Color(0xFF7A56D6)
    else -> Color(0xFF6B7A72)
}

/** A round badge with the provider's glyph. */
@Composable
fun ProviderBadge(provider: String, size: Dp) {
    Box(Modifier.size(size).clip(CircleShape).background(providerColor(provider)), Alignment.Center) {
        Icon(providerIcon(provider), null, Modifier.size(size * 0.58f), tint = Color.White)
    }
}
