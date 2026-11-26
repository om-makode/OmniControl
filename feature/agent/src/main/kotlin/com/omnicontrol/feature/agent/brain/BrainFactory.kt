package com.omnicontrol.feature.agent.brain

import android.content.Context
import com.omnicontrol.core.common.settings.BrainProvider
import com.omnicontrol.core.common.settings.SettingsState

object BrainFactory {

    /** Returns null when no AI is configured (quick commands only). */
    fun create(context: Context, s: SettingsState): Brain? = when (s.brainProvider) {
        BrainProvider.NONE -> null
        BrainProvider.LOCAL -> LocalLlamaBrain.findModel(context)?.let { LocalLlamaBrain(it) }
        else -> {
            val url = s.effectiveUrl
            val model = s.effectiveModel
            if (url.isBlank() || model.isBlank() || (s.brainProvider.needsKey && s.brainApiKey.isBlank())) null
            else OpenAiCompatibleBrain(
                name = "${s.brainProvider.title.substringBefore(" (")} · $model",
                baseUrl = url,
                model = model,
                apiKey = s.brainApiKey,
                vision = s.brainVision,
                isPrivate = !s.brainProvider.leavesPhone || isHomeNetwork(url)
            )
        }
    }

    /** Plain-http servers on the local network (the user's own computer) count as private. */
    fun isHomeNetwork(url: String): Boolean {
        val host = runCatching { java.net.URI(url).host }.getOrNull()?.lowercase() ?: return false
        return host == "localhost" || host.endsWith(".local") || host.startsWith("10.") || host.startsWith("192.168.") ||
            Regex("^172\\.(1[6-9]|2\\d|3[01])\\.").containsMatchIn(host) || host.startsWith("127.")
    }

    /** Human readable reason why [create] returned null. */
    fun whyMissing(context: Context, s: SettingsState): String = when (s.brainProvider) {
        BrainProvider.NONE -> "No AI brain selected"
        BrainProvider.LOCAL -> "No .gguf model in the phone's models/llm folder"
        else -> when {
            s.brainProvider.needsKey && s.brainApiKey.isBlank() -> "API key missing"
            s.effectiveModel.isBlank() -> "Model name missing"
            s.effectiveUrl.isBlank() -> "Server URL missing"
            else -> "Brain not configured"
        }
    }
}
