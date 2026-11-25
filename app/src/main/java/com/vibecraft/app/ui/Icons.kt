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
    val Back = ic("back", "M15 6l-6 6 6 6")
    val Size = ic("size", "M4 9V4h5", "M20 15v5h-5", "M4 4l6 6", "M20 20l-6 -6")
    val Moon = ic("moon", "M20 14.5A8 8 0 0 1 9.5 4 8 8 0 1 0 20 14.5z")
    val Hist = ic("hist", "M3 12a9 9 0 1 0 9 -9 9 9 0 0 0 -6.4 2.6L3 8", "M3 3v5h5", "M12 8v5l3 2")
    val Brain = ic("brain", "M9 4a3 3 0 0 0 -3 3 3 3 0 0 0 -2 5 3 3 0 0 0 2 5 3 3 0 0 0 6 0V7a3 3 0 0 0 -3 -3z", "M15 4a3 3 0 0 1 3 3 3 3 0 0 1 2 5 3 3 0 0 1 -2 5 3 3 0 0 1 -6 0")
    val Wand = ic("wand", "M5 19L19 5", "M14 4l1 2 2 1 -2 1 -1 2 -1 -2 -2 -1 2 -1z")
    val Key = ic("key", "M12 15a4 4 0 1 1 -8 0 4 4 0 0 1 8 0z", "M11 12l9 -9", "M16 7l3 3")
    val Build = ic("build", "M21 16V8l-9 -5 -9 5v8l9 5z", "M3 8l9 5 9 -5", "M12 13v8")
    val Sync = ic("sync", "M21 12a9 9 0 0 1 -15 6.7L3 16", "M3 12a9 9 0 0 1 15 -6.7L21 8", "M21 3v5h-5", "M3 21v-5h5")
    val User = ic("user", "M16 8a4 4 0 1 1 -8 0 4 4 0 0 1 8 0z", "M4 21a8 8 0 0 1 16 0")
    val ProvO = ic("provo", "M12 3l7.8 4.5v9L12 21l-7.8 -4.5v-9z", "M15 12a3 3 0 1 1 -6 0 3 3 0 0 1 6 0z")
    val ProvA = ic("prova", "M12 3v18", "M3 12h18", "M5.6 5.6l12.8 12.8", "M18.4 5.6L5.6 18.4")
    val ProvG = ic("provg", "M12 3c.8 5 4 8.2 9 9 -5 .8 -8.2 4 -9 9 -.8 -5 -4 -8.2 -9 -9 5 -.8 8.2 -4 9 -9z")
    val ProvR = ic("provr", "M4 8h12l-3 -3", "M20 16H8l3 3")
    val ProvC = ic("provc", "M9 3v5", "M15 3v5", "M7 8h10v3a5 5 0 0 1 -10 0z", "M12 16v5")
    val Clock = ic("clock", "M20 13a8 8 0 1 1 -16 0 8 8 0 0 1 16 0z", "M12 9v4l3 2", "M9 3h6")
    val Gauge = ic("gauge", "M4 18a8 8 0 1 1 16 0", "M12 18l4 -6")
    val Alert = ic("alert", "M12 8v5", "M12 17h.01", "M10.3 3.9L2.4 18a2 2 0 0 0 1.7 3h15.8a2 2 0 0 0 1.7 -3L13.7 3.9a2 2 0 0 0 -3.4 0z")
    val Copy = ic("copy", "M8 8h11v12H8z", "M5 16V4h11")
    val Reply = ic("reply", "M9 14L4 9l5 -5", "M4 9h10a6 6 0 0 1 6 6v2")
}
