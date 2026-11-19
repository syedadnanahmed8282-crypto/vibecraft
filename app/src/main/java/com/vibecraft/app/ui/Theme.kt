package com.vibecraft.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val Forest = Color(0xFF1F6B43)
val Fern = Color(0xFF2F9E5B)

enum class ThemeMode { System, Light, Dark }

/** Design tokens, identical to the HTML design. */
@Immutable
class VC(
    val dark: Boolean, val bg: Color, val sf: Color, val sf2: Color, val tx: Color, val mu: Color,
    val ac: Color, val ac2: Color, val ok: Color, val bd: Color, val g: Brush,
)

private val LightVC = VC(
    false, Color(0xFFF3F8F4), Color.White, Color(0xFFEDF4EF), Color(0xFF10261B), Color(0xFF5F7568),
    Color(0xFF1F6B43), Color(0xFFE4F1E9), Color(0xFF2E9E5B), Color(0xFFDCE8E0),
    Brush.linearGradient(listOf(Color(0xFF2F8F5B), Color(0xFF175235))),
)
private val DarkVC = VC(
    true, Color(0xFF07140D), Color(0xFF0E1F16), Color(0xFF15291D), Color(0xFFE6F2EA), Color(0xFF8AA596),
    Color(0xFF4CC380), Color(0xFF12301F), Color(0xFF4CC380), Color(0xFF1D3527),
    Brush.linearGradient(listOf(Color(0xFF34A368), Color(0xFF1B6B43))),
)

val LocalVC = staticCompositionLocalOf { LightVC }

object VCT {
    val c: VC
        @Composable @ReadOnlyComposable get() = LocalVC.current
}

@Composable
fun VibecraftTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val c = if (dark) DarkVC else LightVC
    val scheme = if (dark) {
        darkColorScheme(primary = c.ac, onPrimary = Color(0xFF07140D), background = c.bg, surface = c.sf, onBackground = c.tx, onSurface = c.tx)
    } else {
        lightColorScheme(primary = c.ac, onPrimary = Color.White, background = c.bg, surface = c.sf, onBackground = c.tx, onSurface = c.tx)
    }
    CompositionLocalProvider(LocalVC provides c) { MaterialTheme(colorScheme = scheme, content = content) }
}
