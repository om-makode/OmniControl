package com.omnicontrol.feature.navigator.deeplink

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.omnicontrol.core.common.OmniLog
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URLEncoder

@Serializable
data class DeepLinkRule(
    val app: String,
    val pattern: String,
    val uri: String,
    val pkg: String? = null
)

object DeepLinkRegistry {
    private const val TAG = "DeepLinkRegistry"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val rules = mutableListOf<Pair<Regex, DeepLinkRule>>()

    fun init(context: Context) {
        try {
            val content = context.assets.open("deeplinks.json").bufferedReader().use { it.readText() }
            val list = json.decodeFromString<List<DeepLinkRule>>(content)
            rules.clear()
            for (rule in list) {
                rules.add(Regex(rule.pattern, RegexOption.IGNORE_CASE) to rule)
            }
            OmniLog.i(TAG, "Loaded ${rules.size} deep link rules")
        } catch (e: Exception) {
            OmniLog.w(TAG, "Could not load deeplinks.json from assets", e)
        }
    }

    fun match(utterance: String): Intent? {
        val clean = utterance.trim().lowercase()

        // 1. Settings screens
        when {
            clean.contains("wifi") || clean.contains("wi-fi") ->
                return Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            clean.contains("bluetooth") ->
                return Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            clean.contains("display") || clean.contains("brightness") ->
                return Intent(Settings.ACTION_DISPLAY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            clean.contains("sound") || clean.contains("volume") ->
                return Intent(Settings.ACTION_SOUND_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            clean.contains("battery") ->
                return Intent(Intent.ACTION_POWER_USAGE_SUMMARY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            clean.contains("app settings") || clean.contains("manage apps") ->
                return Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        // 2. Rules from json
        for ((regex, rule) in rules) {
            val m = regex.find(utterance)
            if (m != null) {
                val query = m.groups["q"]?.value?.trim() ?: ""
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val targetUri = rule.uri.replace("{q}", encodedQuery)
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUri)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (!rule.pkg.isNullOrBlank()) {
                        setPackage(rule.pkg)
                    }
                }
                OmniLog.i(TAG, "Matched deep link for app ${rule.app}: $targetUri")
                return intent
            }
        }

        return null
    }
}
