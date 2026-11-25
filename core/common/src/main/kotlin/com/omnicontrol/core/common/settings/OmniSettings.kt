package com.omnicontrol.core.common.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "omnicontrol_settings")

/** Where the "thinking" happens. Everything except NONE / LOCAL talks HTTP in the OpenAI chat format. */
enum class BrainProvider(
    val title: String,
    val subtitle: String,
    val defaultUrl: String,
    val defaultModel: String,
    val needsKey: Boolean,
    val leavesPhone: Boolean
) {
    NONE(
        "Quick commands only", "No AI. Open apps, search, play Nth item, tap by name, scroll.",
        "", "", needsKey = false, leavesPhone = false
    ),
    LOCAL(
        "On-device model", "A .gguf model on the phone (llama.cpp). Private and offline, but slow and less smart.",
        "", "", needsKey = false, leavesPhone = false
    ),
    MY_COMPUTER(
        "My computer (Ollama / LM Studio)", "Free model running on your Mac/PC over Wi-Fi. Private: stays on your network.",
        "http://192.168.1.10:11434/v1", "qwen2.5vl:7b", needsKey = false, leavesPhone = false
    ),
    GEMINI(
        "Google Gemini (free key)", "Free API key from aistudio.google.com. Smart + sees the screen. Screen data goes to Google.",
        "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.5-flash", needsKey = true, leavesPhone = true
    ),
    GROQ(
        "Groq (free key)", "Free API key from console.groq.com. Very fast. Screen data goes to Groq.",
        "https://api.groq.com/openai/v1", "meta-llama/llama-4-scout-17b-16e-instruct", needsKey = true, leavesPhone = true
    ),
    OPENROUTER(
        "OpenRouter (free models)", "Free API key from openrouter.ai, pick any \":free\" model.",
        "https://openrouter.ai/api/v1", "google/gemma-3-27b-it:free", needsKey = true, leavesPhone = true
    ),
    CUSTOM(
        "Custom (OpenAI-compatible)", "Any server that speaks the OpenAI /chat/completions format.",
        "http://", "", needsKey = false, leavesPhone = true
    );
}

data class SettingsState(
    val onboardingDone: Boolean = false,
    val brainProvider: BrainProvider = BrainProvider.NONE,
    val brainUrl: String = "",
    val brainModel: String = "",
    val brainApiKey: String = "",
    /** Attach a screenshot (with numbered boxes) to every AI step. Needed for "the video with a car". */
    val brainVision: Boolean = true,
    val speakReplies: Boolean = true,
    val preferOfflineSpeech: Boolean = true,
    val speechLanguage: String = "",
    val confirmRiskyActions: Boolean = true,
    val maxSteps: Int = 25,
    val showTouchBall: Boolean = true
) {
    val effectiveUrl: String get() = brainUrl.ifBlank { brainProvider.defaultUrl }.trimEnd('/')
    val effectiveModel: String get() = brainModel.ifBlank { brainProvider.defaultModel }
}

class OmniSettings(private val context: Context) {

    private object Keys {
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val BRAIN_PROVIDER = stringPreferencesKey("brain_provider")
        val BRAIN_URL = stringPreferencesKey("brain_url")
        val BRAIN_MODEL = stringPreferencesKey("brain_model")
        val BRAIN_KEY = stringPreferencesKey("brain_api_key")
        val BRAIN_VISION = booleanPreferencesKey("brain_vision")
        val SPEAK_REPLIES = booleanPreferencesKey("speak_replies")
        val PREFER_OFFLINE_SPEECH = booleanPreferencesKey("prefer_offline_speech")
        val SPEECH_LANGUAGE = stringPreferencesKey("speech_language")
        val CONFIRM_RISKY = booleanPreferencesKey("confirm_risky")
        val MAX_STEPS = intPreferencesKey("max_steps")
        val SHOW_TOUCH_BALL = booleanPreferencesKey("show_touch_ball")
    }

    val settingsFlow: Flow<SettingsState> = context.dataStore.data.map { p ->
        SettingsState(
            onboardingDone = p[Keys.ONBOARDING_DONE] ?: false,
            brainProvider = p[Keys.BRAIN_PROVIDER]?.let { runCatching { BrainProvider.valueOf(it) }.getOrNull() }
                ?: BrainProvider.NONE,
            brainUrl = p[Keys.BRAIN_URL] ?: "",
            brainModel = p[Keys.BRAIN_MODEL] ?: "",
            brainApiKey = p[Keys.BRAIN_KEY] ?: "",
            brainVision = p[Keys.BRAIN_VISION] ?: true,
            speakReplies = p[Keys.SPEAK_REPLIES] ?: true,
            preferOfflineSpeech = p[Keys.PREFER_OFFLINE_SPEECH] ?: true,
            speechLanguage = p[Keys.SPEECH_LANGUAGE] ?: "",
            confirmRiskyActions = p[Keys.CONFIRM_RISKY] ?: true,
            maxSteps = p[Keys.MAX_STEPS] ?: 25,
            showTouchBall = p[Keys.SHOW_TOUCH_BALL] ?: true
        )
    }

    suspend fun current(): SettingsState = settingsFlow.first()

    suspend fun setOnboardingDone(done: Boolean) = edit { it[Keys.ONBOARDING_DONE] = done }

    /** Switching provider clears URL/model so the provider defaults apply. The key is kept per provider. */
    suspend fun setBrainProvider(provider: BrainProvider) = edit {
        it[Keys.BRAIN_PROVIDER] = provider.name
        it[Keys.BRAIN_URL] = ""
        it[Keys.BRAIN_MODEL] = ""
        it[Keys.BRAIN_KEY] = it[stringPreferencesKey("brain_key_${provider.name}")] ?: ""
    }

    suspend fun setBrainUrl(url: String) = edit { it[Keys.BRAIN_URL] = url.trim() }
    suspend fun setBrainModel(model: String) = edit { it[Keys.BRAIN_MODEL] = model.trim() }
    suspend fun setBrainApiKey(key: String) = edit {
        it[Keys.BRAIN_KEY] = key.trim()
        val provider = it[Keys.BRAIN_PROVIDER] ?: BrainProvider.NONE.name
        it[stringPreferencesKey("brain_key_$provider")] = key.trim()
    }
    suspend fun setBrainVision(enabled: Boolean) = edit { it[Keys.BRAIN_VISION] = enabled }
    suspend fun setSpeakReplies(enabled: Boolean) = edit { it[Keys.SPEAK_REPLIES] = enabled }
    suspend fun setPreferOfflineSpeech(enabled: Boolean) = edit { it[Keys.PREFER_OFFLINE_SPEECH] = enabled }
    suspend fun setSpeechLanguage(tag: String) = edit { it[Keys.SPEECH_LANGUAGE] = tag.trim() }
    suspend fun setConfirmRiskyActions(enabled: Boolean) = edit { it[Keys.CONFIRM_RISKY] = enabled }
    suspend fun setMaxSteps(steps: Int) = edit { it[Keys.MAX_STEPS] = steps.coerceIn(5, 60) }
    suspend fun setShowTouchBall(enabled: Boolean) = edit { it[Keys.SHOW_TOUCH_BALL] = enabled }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }
}
