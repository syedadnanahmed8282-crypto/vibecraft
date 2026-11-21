package com.vibecraft.app.ai

import java.net.URI

object ProfileValidation {
    private val localHosts = setOf("localhost", "127.0.0.1", "10.0.2.2")

    /** Returns an error text for an unsafe or malformed API base URL, or null when it is fine. */
    fun checkUrl(raw: String): String? {
        val url = raw.trim()
        if (url.isEmpty()) return "Enter the API base URL."
        val u = try { URI(url) } catch (e: Exception) { return "The API base URL is not valid." }
        val host = u.host?.lowercase() ?: return "The API base URL needs a host name."
        if (u.userInfo != null) return "Do not put a user name or password inside the URL."
        if (u.query != null || u.fragment != null) return "The base URL must not contain ? or #."
        return when (u.scheme?.lowercase()) {
            "https" -> null
            "http" -> if (host in localHosts) null else "Use https. Plain http is only allowed for localhost."
            else -> "The URL must start with https://"
        }
    }
}
