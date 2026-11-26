package com.omnicontrol.feature.accessibility

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import com.omnicontrol.core.common.OmniLog
import com.omnicontrol.core.common.TextMatch

data class AppEntry(val label: String, val packageName: String)

sealed interface LaunchResult {
    data class Launched(val label: String, val packageName: String?) : LaunchResult
    data class NotFound(val query: String, val suggestions: List<String>) : LaunchResult
}

/** Finds and opens apps by spoken name ("you tube", "chat gpt", "my settings"...). */
class AppLauncher(private val context: Context) {

    private var cache: List<AppEntry> = emptyList()
    private var cacheTime = 0L

    fun installedApps(): List<AppEntry> {
        val now = System.currentTimeMillis()
        if (cache.isEmpty() || now - cacheTime > 60_000) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            cache = try {
                pm.queryIntentActivities(intent, 0)
                    .map { AppEntry(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
                    .filter { it.packageName != context.packageName }
                    .distinctBy { it.packageName }
            } catch (e: Exception) {
                OmniLog.w(TAG, "Could not list apps", e); emptyList()
            }
            cacheTime = now
        }
        return cache
    }

    fun find(name: String): AppEntry? {
        val query = cleanName(name)
        if (query.isBlank()) return null
        val apps = installedApps()
        KNOWN_PACKAGES[TextMatch.compact(query)]?.forEach { pkg ->
            apps.firstOrNull { it.packageName == pkg }?.let { return it }
        }
        var best: AppEntry? = null
        var bestScore = 0.0
        for (app in apps) {
            val s = maxOf(
                TextMatch.labelScore(query, app.label),
                TextMatch.similarity(query, app.label),
                if (TextMatch.compact(app.packageName).contains(TextMatch.compact(query)) && query.length >= 4) 0.72 else 0.0
            )
            if (s > bestScore) { bestScore = s; best = app }
        }
        OmniLog.d(TAG, "app lookup score=$bestScore")
        return if (bestScore >= 0.72) best else null
    }

    fun launch(name: String): LaunchResult {
        val query = cleanName(name)
        val compact = TextMatch.compact(query)

        // System screens that have dedicated intents (work on every OEM).
        SYSTEM_INTENTS[compact]?.let { action ->
            if (start(Intent(action))) return LaunchResult.Launched(query.replaceFirstChar { it.uppercase() }, null)
        }

        val app = find(query)
        if (app != null) {
            val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
            if (intent != null && start(intent)) return LaunchResult.Launched(app.label, app.packageName)
        }

        // Not installed but has a website: open it in the browser instead of failing.
        WEB_FALLBACKS[compact]?.let { url ->
            if (start(Intent(Intent.ACTION_VIEW, Uri.parse(url)))) return LaunchResult.Launched("$query website", null)
        }

        val suggestions = installedApps()
            .map { it.label to TextMatch.similarity(query, it.label) }
            .sortedByDescending { it.second }.take(3).map { it.first }
        return LaunchResult.NotFound(query, suggestions)
    }

    fun openUrl(url: String, preferredPackage: String? = null): Boolean {
        val uri = Uri.parse(url)
        if (uri.scheme !in setOf("http", "https")) return false
        val intent = Intent(Intent.ACTION_VIEW, uri)
        if (preferredPackage != null && installedApps().any { it.packageName == preferredPackage }) {
            intent.setPackage(preferredPackage)
            if (start(intent)) return true
            intent.setPackage(null)
        }
        return start(intent)
    }

    private fun start(intent: Intent): Boolean = try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        OmniLog.w(TAG, "startActivity failed for $intent", e)
        false
    }

    private fun cleanName(name: String): String =
        TextMatch.normalize(name)
            .replace(Regex("^(the|my)\\s+"), "")
            .replace(Regex("\\s+(app|application)$"), "")
            .trim()

    companion object {
        private const val TAG = "OmniLauncher"

        /** compact spoken name -> likely packages (first installed one wins). */
        val KNOWN_PACKAGES: Map<String, List<String>> = mapOf(
            "youtube" to listOf("com.google.android.youtube"),
            "youtubemusic" to listOf("com.google.android.apps.youtube.music"),
            "chatgpt" to listOf("com.openai.chatgpt"),
            "chrome" to listOf("com.android.chrome"),
            "googlechrome" to listOf("com.android.chrome"),
            "browser" to listOf("com.android.chrome", "com.sec.android.app.sbrowser", "org.mozilla.firefox"),
            "whatsapp" to listOf("com.whatsapp", "com.whatsapp.w4b"),
            "instagram" to listOf("com.instagram.android"),
            "facebook" to listOf("com.facebook.katana"),
            "gmail" to listOf("com.google.android.gm"),
            "mail" to listOf("com.google.android.gm", "com.samsung.android.email.provider"),
            "maps" to listOf("com.google.android.apps.maps"),
            "googlemaps" to listOf("com.google.android.apps.maps"),
            "playstore" to listOf("com.android.vending"),
            "googleplay" to listOf("com.android.vending"),
            "photos" to listOf("com.google.android.apps.photos"),
            "gallery" to listOf("com.google.android.apps.photos", "com.sec.android.gallery3d", "com.miui.gallery"),
            "files" to listOf("com.google.android.apps.nbu.files", "com.google.android.documentsui", "com.sec.android.app.myfiles"),
            "filemanager" to listOf("com.google.android.apps.nbu.files", "com.sec.android.app.myfiles", "com.mi.android.globalFileexplorer"),
            "messages" to listOf("com.google.android.apps.messaging", "com.samsung.android.messaging"),
            "phone" to listOf("com.google.android.dialer", "com.samsung.android.dialer"),
            "dialer" to listOf("com.google.android.dialer", "com.samsung.android.dialer"),
            "contacts" to listOf("com.google.android.contacts", "com.samsung.android.app.contacts"),
            "calendar" to listOf("com.google.android.calendar", "com.samsung.android.calendar"),
            "clock" to listOf("com.google.android.deskclock", "com.sec.android.app.clockpackage"),
            "calculator" to listOf("com.google.android.calculator", "com.sec.android.app.popupcalculator"),
            "spotify" to listOf("com.spotify.music"),
            "telegram" to listOf("org.telegram.messenger"),
            "x" to listOf("com.twitter.android"),
            "twitter" to listOf("com.twitter.android"),
            "linkedin" to listOf("com.linkedin.android"),
            "amazon" to listOf("in.amazon.mShop.android.shopping", "com.amazon.mShop.android.shopping"),
            "flipkart" to listOf("com.flipkart.android"),
            "netflix" to listOf("com.netflix.mediaclient"),
            "gemini" to listOf("com.google.android.apps.bard"),
            "claude" to listOf("com.anthropic.claude"),
            "drive" to listOf("com.google.android.apps.docs"),
            "googledrive" to listOf("com.google.android.apps.docs"),
            "keep" to listOf("com.google.android.keep"),
            "notes" to listOf("com.google.android.keep", "com.samsung.android.app.notes")
        )

        private val SYSTEM_INTENTS: Map<String, String> = mapOf(
            "settings" to Settings.ACTION_SETTINGS,
            "wifi" to Settings.ACTION_WIFI_SETTINGS,
            "wifisettings" to Settings.ACTION_WIFI_SETTINGS,
            "bluetooth" to Settings.ACTION_BLUETOOTH_SETTINGS,
            "bluetoothsettings" to Settings.ACTION_BLUETOOTH_SETTINGS,
            "displaysettings" to Settings.ACTION_DISPLAY_SETTINGS,
            "soundsettings" to Settings.ACTION_SOUND_SETTINGS,
            "batterysettings" to Intent.ACTION_POWER_USAGE_SUMMARY,
            "camera" to MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA
        )

        private val WEB_FALLBACKS: Map<String, String> = mapOf(
            "chatgpt" to "https://chatgpt.com",
            "youtube" to "https://m.youtube.com",
            "gemini" to "https://gemini.google.com",
            "claude" to "https://claude.ai",
            "google" to "https://www.google.com",
            "gmail" to "https://mail.google.com",
            "instagram" to "https://www.instagram.com",
            "facebook" to "https://m.facebook.com",
            "x" to "https://x.com",
            "twitter" to "https://x.com",
            "amazon" to "https://www.amazon.in",
            "flipkart" to "https://www.flipkart.com"
        )
    }
}
