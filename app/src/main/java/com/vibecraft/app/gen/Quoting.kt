package com.vibecraft.app.gen

/** What the user chose to reply to. role is "user" or "assistant". */
data class Quote(val role: String, val text: String)
data class QuoteParts(val role: String, val quote: String, val text: String)

/**
 * A reply is stored inside the message text itself, so it syncs like any message and needs no database change:
 *   [[reply:assistant]]<quoted text>[[/reply]]
 *   <what the user wrote>
 * The chat shows the quote as a small block; the AI is told exactly which earlier message the words refer to.
 */
object Quoting {
    const val MAX_QUOTE = 1500
    private const val CLOSE = "[[/reply]]"
    private val re = Regex("^\\[\\[reply:(user|assistant)\\]\\]([\\s\\S]*?)\\[\\[/reply\\]\\]\\n?([\\s\\S]*)$")

    fun encode(role: String, quote: String, text: String): String {
        val r = if (role == "user") "user" else "assistant"
        val q = quote.trim().replace(CLOSE, "[[/ reply]]").take(MAX_QUOTE)
        return "[[reply:$r]]$q$CLOSE\n${text.trim()}"
    }

    fun decode(content: String): QuoteParts? {
        val m = re.find(content) ?: return null
        return QuoteParts(m.groupValues[1], m.groupValues[2], m.groupValues[3].trim())
    }

    /** The message as the AI should read it. It starts with the sentence the planning rules look for. */
    fun forAi(p: QuoteParts): String {
        val who = if (p.role == "user") "the user (an earlier message of their own)" else "you, the assistant"
        return "The user is replying to this earlier message from $who:\n\"\"\"\n${p.quote}\n\"\"\"\nThe user's reply: ${p.text}"
    }

    fun forAiText(content: String): String = decode(content)?.let { forAi(it) } ?: content

    /** A short, readable line for the reply bar and the quote block: no markdown marks, one line of text. */
    fun preview(text: String, max: Int = 200): String {
        val t = text.replace("**", "").replace("`", "").replace(Regex("(?m)^#+\\s*"), "").replace(Regex("\\s+"), " ").trim()
        return if (t.length <= max) t else t.take(max).trimEnd() + "…"
    }
}
