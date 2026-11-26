package com.omnicontrol.core.common.safety

import java.net.URI

/** Flags links that deserve a warning before opening. */
object LinkSafety {

    private val SHORTENERS = setOf(
        "bit.ly", "tinyurl.com", "t.co", "goo.gl", "ow.ly", "is.gd", "buff.ly", "cutt.ly", "rb.gy", "shorturl.at",
        "tiny.cc", "rebrand.ly", "s.id", "t.ly", "lnkd.in"
    )
    private val TRUSTED = setOf(
        "google.com", "youtube.com", "youtu.be", "wikipedia.org", "github.com", "android.com", "developer.android.com",
        "apple.com", "microsoft.com", "amazon.in", "amazon.com", "flipkart.com", "openai.com", "chatgpt.com",
        "stackoverflow.com", "reddit.com", "linkedin.com", "instagram.com", "facebook.com", "x.com", "twitter.com",
        "whatsapp.com", "gmail.com", "maps.google.com", "play.google.com", "medium.com", "swift.org", "kotlinlang.org"
    )
    private val RISKY_EXTENSIONS = listOf(".apk", ".xapk", ".apks", ".exe", ".bat", ".msi", ".dmg", ".jar", ".scr", ".zip", ".rar")

    /** Empty list = looks fine. Otherwise human readable reasons. */
    fun warnings(url: String): List<String> {
        val reasons = ArrayList<String>()
        val uri = runCatching { URI(if (url.contains("://")) url else "https://$url") }.getOrNull()
            ?: return listOf("the link looks broken")
        val host = uri.host?.lowercase().orEmpty()
        val path = uri.path?.lowercase().orEmpty()
        if (uri.scheme == "http") reasons += "it is not encrypted (http)"
        if (host.isEmpty()) reasons += "it has no website name"
        if (Regex("^\\d{1,3}(\\.\\d{1,3}){3}$").matches(host)) reasons += "it points to a raw IP address"
        if (host.contains("xn--")) reasons += "the website name uses look-alike characters"
        if (SHORTENERS.any { host == it || host.endsWith(".$it") }) reasons += "it is a shortened link that hides the real site"
        if (host.count { it == '.' } >= 4) reasons += "the website name is unusually long"
        if (url.contains('@')) reasons += "it contains an @ which can hide the real site"
        if (RISKY_EXTENSIONS.any { path.endsWith(it) }) reasons += "it downloads a file (${path.substringAfterLast('.')})"
        if (ContentFilter.isExplicitUrl(url)) reasons += "it looks like adult content"
        return reasons
    }

    fun isTrusted(url: String): Boolean {
        val host = runCatching { URI(if (url.contains("://")) url else "https://$url").host?.lowercase() }.getOrNull() ?: return false
        return TRUSTED.any { host == it || host.endsWith(".$it") }
    }

    fun isDownload(url: String): Boolean {
        val path = runCatching { URI(if (url.contains("://")) url else "https://$url").path?.lowercase() }.getOrNull() ?: return false
        return RISKY_EXTENSIONS.any { path.endsWith(it) }
    }
}
