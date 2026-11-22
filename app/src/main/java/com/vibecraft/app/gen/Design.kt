package com.vibecraft.app.gen

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/** Colors per mode (#RRGGBB) and sizes. The same spec feeds the Kotlin DesignTokens object and the preview's CSS variables. */
data class DesignSpec(
    val name: String,
    val light: Map<String, String>,
    val dark: Map<String, String>,
    val sizes: Map<String, Int>,
    val issues: List<String> = emptyList(),
)

object Design {
    val COLOR_KEYS = listOf("primary", "onPrimary", "background", "surface", "onSurface", "muted", "accent", "error")

    private val LIGHT = mapOf(
        "primary" to "#1F6B43", "onPrimary" to "#FFFFFF", "background" to "#FFFFFF", "surface" to "#F4F9F5",
        "onSurface" to "#0F1A13", "muted" to "#6E7B71", "accent" to "#2E9E5B", "error" to "#B3261E",
    )
    private val DARK = mapOf(
        "primary" to "#6FDD93", "onPrimary" to "#04170C", "background" to "#08180E", "surface" to "#0E1F16",
        "onSurface" to "#EAF5EC", "muted" to "#94A89A", "accent" to "#4CC380", "error" to "#FF8A80",
    )
    private val SIZES = linkedMapOf(
        "title" to 22, "body" to 15, "caption" to 12, "titleWeight" to 700,
        "spaceXs" to 4, "spaceS" to 8, "spaceM" to 16, "spaceL" to 24,
        "radiusS" to 8, "radiusM" to 16, "radiusL" to 24, "elevCard" to 2, "elevSheet" to 8,
    )
    private val hex = Regex("^#[0-9A-Fa-f]{6}$")

    fun default() = DesignSpec("App", LIGHT, DARK, SIZES)

    /** Tolerant: anything missing or invalid falls back to the default and is listed in issues. */
    fun parse(text: String?): DesignSpec {
        if (text.isNullOrBlank()) return default()
        val root = try {
            Json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            return default().copy(issues = listOf("design.json is not valid JSON."))
        }
        val issues = mutableListOf<String>()
        fun obj(o: JsonObject?, k: String): JsonObject? = o?.get(k) as? JsonObject

        val colors = obj(root, "colors")
        fun palette(mode: String, def: Map<String, String>): Map<String, String> {
            val o = obj(colors, mode)
            return COLOR_KEYS.associateWith { k ->
                val v = (o?.get(k) as? JsonPrimitive)?.contentOrNull
                when {
                    v == null -> def.getValue(k)
                    hex.matches(v) -> v.uppercase()
                    else -> { issues.add("colors.$mode.$k must be a #RRGGBB color."); def.getValue(k) }
                }
            }
        }

        fun num(o: JsonObject?, k: String, def: Int, lo: Int, hi: Int, path: String): Int {
            val p = o?.get(k) as? JsonPrimitive ?: return def
            val v = p.intOrNull
            if (v == null) { issues.add("$path.$k must be a number."); return def }
            return v.coerceIn(lo, hi)
        }

        val t = obj(root, "typography"); val sp = obj(root, "spacing"); val rd = obj(root, "radius"); val el = obj(root, "elevation")
        val sizes = linkedMapOf(
            "title" to num(t, "title", 22, 8, 64, "typography"),
            "body" to num(t, "body", 15, 8, 40, "typography"),
            "caption" to num(t, "caption", 12, 8, 32, "typography"),
            "titleWeight" to num(t, "titleWeight", 700, 100, 900, "typography"),
            "spaceXs" to num(sp, "xs", 4, 0, 128, "spacing"), "spaceS" to num(sp, "s", 8, 0, 128, "spacing"),
            "spaceM" to num(sp, "m", 16, 0, 128, "spacing"), "spaceL" to num(sp, "l", 24, 0, 128, "spacing"),
            "radiusS" to num(rd, "s", 8, 0, 64, "radius"), "radiusM" to num(rd, "m", 16, 0, 64, "radius"),
            "radiusL" to num(rd, "l", 24, 0, 64, "radius"),
            "elevCard" to num(el, "card", 2, 0, 24, "elevation"), "elevSheet" to num(el, "sheet", 8, 0, 24, "elevation"),
        )
        val name = ((root["name"] as? JsonPrimitive)?.contentOrNull ?: "App").take(40)
        return DesignSpec(name, palette("light", LIGHT), palette("dark", DARK), sizes, issues)
    }

    private fun kebab(k: String) = k.replace(Regex("([A-Z])")) { "-" + it.value.lowercase() }

    /** CSS variables for the preview; html.dark switches to the dark palette. */
    fun toCss(s: DesignSpec): String {
        fun colors(m: Map<String, String>) = COLOR_KEYS.joinToString("") { "--color-${kebab(it)}:${m.getValue(it)};" }
        val z = s.sizes
        fun px(k: String) = "${z.getValue(k)}px"
        fun shadow(k: String) = "0 ${z.getValue(k)}px ${z.getValue(k) * 3}px rgba(0,0,0,.16)"
        return ":root{" + colors(s.light) +
            "--font-title:${px("title")};--font-body:${px("body")};--font-caption:${px("caption")};--font-title-weight:${z.getValue("titleWeight")};" +
            "--space-xs:${px("spaceXs")};--space-s:${px("spaceS")};--space-m:${px("spaceM")};--space-l:${px("spaceL")};" +
            "--radius-s:${px("radiusS")};--radius-m:${px("radiusM")};--radius-l:${px("radiusL")};" +
            "--elev-card:${shadow("elevCard")};--elev-sheet:${shadow("elevSheet")};}\n" +
            "html.dark{" + colors(s.dark) + "}\n"
    }

    private fun argb(hex: String) = "0xFF" + hex.removePrefix("#").uppercase()

    /** The Kotlin object the generated app uses; written by Vibecraft, never by the AI. */
    fun toKotlin(s: DesignSpec): String {
        fun colors(m: Map<String, String>) = COLOR_KEYS.joinToString("\n") { "        val $it = Color(${argb(m.getValue(it))})" }
        val z = s.sizes
        return """
package com.vibecraft.generated

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Generated by Vibecraft from design.json. Do not edit: change design.json instead. */
object DesignTokens {
    object Light {
${colors(s.light)}
    }

    object Dark {
${colors(s.dark)}
    }

    val titleSize = ${z.getValue("title")}.sp
    val bodySize = ${z.getValue("body")}.sp
    val captionSize = ${z.getValue("caption")}.sp
    const val titleWeight = ${z.getValue("titleWeight")}
    val spaceXs = ${z.getValue("spaceXs")}.dp
    val spaceS = ${z.getValue("spaceS")}.dp
    val spaceM = ${z.getValue("spaceM")}.dp
    val spaceL = ${z.getValue("spaceL")}.dp
    val radiusS = ${z.getValue("radiusS")}.dp
    val radiusM = ${z.getValue("radiusM")}.dp
    val radiusL = ${z.getValue("radiusL")}.dp
    val elevationCard = ${z.getValue("elevCard")}.dp
    val elevationSheet = ${z.getValue("elevSheet")}.dp
}
""".trimStart()
    }
}
