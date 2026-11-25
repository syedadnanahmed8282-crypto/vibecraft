package com.vibecraft.app.ai

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** type: "openai" (OpenAI-compatible), "anthropic" or "gemini". The API key is never part of this object. */
@Serializable
data class Profile(
    val id: String,
    val name: String,
    val provider: String,
    val type: String,
    val baseUrl: String,
    val model: String,
    val dailyLimit: Int = 0,
)

data class Preset(val provider: String, val type: String, val baseUrl: String, val model: String)

val Presets = listOf(
    Preset("OpenAI", "openai", "https://api.openai.com/v1", "gpt-4o"),
    Preset("Anthropic", "anthropic", "https://api.anthropic.com/v1", "claude-sonnet-4-5"),
    Preset("Gemini", "gemini", "https://generativelanguage.googleapis.com/v1beta", "gemini-2.5-flash"),
    Preset("OpenRouter", "openai", "https://openrouter.ai/api/v1", "provider/model-name"),
    Preset("Custom", "openai", "", "model-name"),
)

/** Profile details live in normal prefs; API keys live in encrypted prefs and stay on this phone. */
class ProfileStore(ctx: Context) {
    private val meta = ctx.getSharedPreferences("vc_ai", 0)
    private val keys: SharedPreferences? = runCatching {
        val mk = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            ctx, "vc_keys", mk,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrNull()
    private val json = Json { ignoreUnknownKeys = true }
    private val ser = ListSerializer(Profile.serializer())

    fun secure() = keys != null
    fun list(): List<Profile> =
        runCatching { json.decodeFromString(ser, meta.getString("profiles", "[]")!!) }.getOrDefault(emptyList())

    fun saveOrder(l: List<Profile>) { meta.edit().putString("profiles", json.encodeToString(ser, l)).apply() }
    fun key(id: String): String? = runCatching { keys?.getString("k_$id", null) }.getOrNull()
    fun putKey(id: String, k: String) { keys?.edit()?.putString("k_$id", k)?.apply() }
    fun removeKey(id: String) { keys?.edit()?.remove("k_$id")?.apply() }
    fun ghToken(): String? = runCatching { keys?.getString("gh_token", null) }.getOrNull()
    fun clearGhToken() { keys?.edit()?.remove("gh_token")?.apply() }
    fun setGhToken(t: String): Boolean { val k = keys ?: return false; k.edit().putString("gh_token", t).apply(); return true }
    fun activeId(): String? = meta.getString("active", null)
    fun setActive(id: String?) { meta.edit().putString("active", id).apply() }
    fun autoSwitch() = meta.getBoolean("auto", true)
    fun setAuto(b: Boolean) { meta.edit().putBoolean("auto", b).apply() }

    private fun day() = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
    fun used(id: String) = meta.getInt("u_${id}_${day()}", 0)
    fun addUsage(id: String) { meta.edit().putInt("u_${id}_${day()}", used(id) + 1).apply() }
}
