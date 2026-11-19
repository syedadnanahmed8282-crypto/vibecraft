package com.vibecraft.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

private fun ic(name: String, vararg d: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        d.forEach {
            addPath(
                PathParser().parsePathString(it).toNodes(),
                fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

/** Same stroke icons as the HTML design. */
object Ic {
    val Menu = ic("menu", "M4 7h16", "M4 12h16", "M4 17h10")
    val Chat = ic("chat", "M21 12a8 8 0 0 1 -11.6 7.1L3 21l1.9 -6.4A8 8 0 1 1 21 12z")
    val Phone = ic("phone", "M9 2h6a2 2 0 0 1 2 2v16a2 2 0 0 1 -2 2H9a2 2 0 0 1 -2 -2V4a2 2 0 0 1 2 -2z", "M11 18h2")
    val Code = ic("code", "M8 7l-5 5 5 5", "M16 7l5 5 -5 5")
    val Up = ic("up", "M12 19V5", "M5 12l7 -7 7 7")
    val Down = ic("down", "M12 5v14", "M5 12l7 7 7 -7")
    val ChevUp = ic("chevup", "M6 15l6 -6 6 6")
    val ChevDown = ic("chevdown", "M6 9l6 6 6 -6")
    val Send = ic("send", "M22 2L11 13", "M22 2l-7 20 -4 -9 -9 -4z")
    val Close = ic("close", "M6 6l12 12", "M18 6L6 18")
    val Plus = ic("plus", "M12 5v14", "M5 12h14")
    val Check = ic("check", "M5 12l5 5L20 7")
    val Gear = ic("gear", "M12 15a3 3 0 1 0 0 -6 3 3 0 0 0 0 6z", "M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1 .1a2 2 0 1 1 -2.8 2.8l-.1 -.1a1.7 1.7 0 0 0 -1.8 -.3 1.7 1.7 0 0 0 -1 1.5V21a2 2 0 1 1 -4 0v-.1a1.7 1.7 0 0 0 -1.1 -1.5 1.7 1.7 0 0 0 -1.8 .3l-.1 .1a2 2 0 1 1 -2.8 -2.8l.1 -.1a1.7 1.7 0 0 0 .3 -1.8 1.7 1.7 0 0 0 -1.5 -1H3a2 2 0 1 1 0 -4h.1a1.7 1.7 0 0 0 1.5 -1.1 1.7 1.7 0 0 0 -.3 -1.8l-.1 -.1a2 2 0 1 1 2.8 -2.8l.1 .1a1.7 1.7 0 0 0 1.8 .3H9a1.7 1.7 0 0 0 1 -1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8 -.3l.1 -.1a2 2 0 1 1 2.8 2.8l-.1 .1a1.7 1.7 0 0 0 -.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0 -1.5 1z")
    val Eye = ic("eye", "M2 12s4 -7 10 -7 10 7 10 7 -4 7 -10 7S2 12 2 12z", "M15 12a3 3 0 1 1 -6 0 3 3 0 0 1 6 0z")
    val Trash = ic("trash", "M4 7h16", "M10 11v6", "M14 11v6", "M6 7l1 13h10l1 -13", "M9 7V4h6v3")
    val File = ic("file", "M14 3H7a2 2 0 0 0 -2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2 -2V8z", "M14 3v5h5")
    val Todo = ic("todo", "M9 11l3 3 8 -8", "M20 12v7a2 2 0 0 1 -2 2H6a2 2 0 0 1 -2 -2V7a2 2 0 0 1 2 -2h9")
    val Notes = ic("notes", "M14 3H7a2 2 0 0 0 -2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2 -2V8z", "M14 3v5h5", "M9 13h6", "M9 17h4")
    val Heart = ic("heart", "M12 21s-7 -4.5 -7 -10a4 4 0 0 1 7 -2.6A4 4 0 0 1 19 11c0 5.5 -7 10 -7 10z")
    val Calc = ic("calc", "M7 3h10a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2H7a2 2 0 0 1 -2 -2V5a2 2 0 0 1 2 -2z", "M9 7h6", "M9 12h.01", "M12 12h.01", "M15 12h.01", "M9 16h.01", "M12 16h.01", "M15 16h.01")
    val Game = ic("game", "M8 8h8a5 5 0 0 1 0 10H8A5 5 0 0 1 8 8z", "M8 11v4", "M6 13h4", "M16 12h.01", "M18 14h.01")
}
