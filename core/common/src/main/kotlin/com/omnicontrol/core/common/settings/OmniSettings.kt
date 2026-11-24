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
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "omnicontrol_settings")

data class SettingsState(
    val onboardingDone: Boolean = false,
    val offlineMode: Boolean = true,
    val speakReplies: Boolean = true,
    val silenceTimeoutMin: Int = 3,
    val endOfSpeechMs: Int = 700,
    val maxStepsPerCommand: Int = 15,
    val recordTraces: Boolean = false,
    val wakeWordEnabled: Boolean = false,
    val homeBrainEnabled: Boolean = false,
    val homeBrainUrl: String = "",
    val selectedLlmModel: String = "",
    val selectedAsrModel: String = "",
    val confirmSubscribeAndLike: Boolean = true,
    val panicKeysEnabled: Boolean = true
)

class OmniSettings(private val context: Context) {

    private object Keys {
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val OFFLINE_MODE = booleanPreferencesKey("offline_mode")
        val SPEAK_REPLIES = booleanPreferencesKey("speak_replies")
        val SILENCE_TIMEOUT_MIN = intPreferencesKey("silence_timeout_min")
        val END_OF_SPEECH_MS = intPreferencesKey("end_of_speech_ms")
        val MAX_STEPS_PER_COMMAND = intPreferencesKey("max_steps_per_command")
        val RECORD_TRACES = booleanPreferencesKey("record_traces")
        val WAKE_WORD_ENABLED = booleanPreferencesKey("wake_word_enabled")
        val HOME_BRAIN_ENABLED = booleanPreferencesKey("home_brain_enabled")
        val HOME_BRAIN_URL = stringPreferencesKey("home_brain_url")
        val SELECTED_LLM_MODEL = stringPreferencesKey("selected_llm_model")
        val SELECTED_ASR_MODEL = stringPreferencesKey("selected_asr_model")
        val CONFIRM_SUBSCRIBE_AND_LIKE = booleanPreferencesKey("confirm_subscribe_and_like")
        val PANIC_KEYS_ENABLED = booleanPreferencesKey("panic_keys_enabled")
    }

    val settingsFlow: Flow<SettingsState> = context.dataStore.data.map { prefs ->
        SettingsState(
            onboardingDone = prefs[Keys.ONBOARDING_DONE] ?: false,
            offlineMode = prefs[Keys.OFFLINE_MODE] ?: true,
            speakReplies = prefs[Keys.SPEAK_REPLIES] ?: true,
            silenceTimeoutMin = prefs[Keys.SILENCE_TIMEOUT_MIN] ?: 3,
            endOfSpeechMs = prefs[Keys.END_OF_SPEECH_MS] ?: 700,
            maxStepsPerCommand = prefs[Keys.MAX_STEPS_PER_COMMAND] ?: 15,
            recordTraces = prefs[Keys.RECORD_TRACES] ?: false,
            wakeWordEnabled = prefs[Keys.WAKE_WORD_ENABLED] ?: false,
            homeBrainEnabled = prefs[Keys.HOME_BRAIN_ENABLED] ?: false,
            homeBrainUrl = prefs[Keys.HOME_BRAIN_URL] ?: "",
            selectedLlmModel = prefs[Keys.SELECTED_LLM_MODEL] ?: "",
            selectedAsrModel = prefs[Keys.SELECTED_ASR_MODEL] ?: "",
            confirmSubscribeAndLike = prefs[Keys.CONFIRM_SUBSCRIBE_AND_LIKE] ?: true,
            panicKeysEnabled = prefs[Keys.PANIC_KEYS_ENABLED] ?: true
        )
    }

    suspend fun setOnboardingDone(done: Boolean) = setBoolean(Keys.ONBOARDING_DONE, done)
    suspend fun setOfflineMode(enabled: Boolean) = setBoolean(Keys.OFFLINE_MODE, enabled)
    suspend fun setSpeakReplies(enabled: Boolean) = setBoolean(Keys.SPEAK_REPLIES, enabled)
    suspend fun setSilenceTimeoutMin(minutes: Int) = setInt(Keys.SILENCE_TIMEOUT_MIN, minutes)
    suspend fun setEndOfSpeechMs(ms: Int) = setInt(Keys.END_OF_SPEECH_MS, ms)
    suspend fun setMaxStepsPerCommand(max: Int) = setInt(Keys.MAX_STEPS_PER_COMMAND, max)
    suspend fun setRecordTraces(enabled: Boolean) = setBoolean(Keys.RECORD_TRACES, enabled)
    suspend fun setWakeWordEnabled(enabled: Boolean) = setBoolean(Keys.WAKE_WORD_ENABLED, enabled)
    suspend fun setHomeBrainEnabled(enabled: Boolean) = setBoolean(Keys.HOME_BRAIN_ENABLED, enabled)
    suspend fun setHomeBrainUrl(url: String) = setString(Keys.HOME_BRAIN_URL, url)
    suspend fun setSelectedLlmModel(model: String) = setString(Keys.SELECTED_LLM_MODEL, model)
    suspend fun setSelectedAsrModel(model: String) = setString(Keys.SELECTED_ASR_MODEL, model)
    suspend fun setConfirmSubscribeAndLike(confirm: Boolean) = setBoolean(Keys.CONFIRM_SUBSCRIBE_AND_LIKE, confirm)
    suspend fun setPanicKeysEnabled(enabled: Boolean) = setBoolean(Keys.PANIC_KEYS_ENABLED, enabled)

    private suspend fun setBoolean(key: Preferences.Key<Boolean>, value: Boolean) {
        context.dataStore.edit { it[key] = value }
    }

    private suspend fun setInt(key: Preferences.Key<Int>, value: Int) {
        context.dataStore.edit { it[key] = value }
    }

    private suspend fun setString(key: Preferences.Key<String>, value: String) {
        context.dataStore.edit { it[key] = value }
    }
}
