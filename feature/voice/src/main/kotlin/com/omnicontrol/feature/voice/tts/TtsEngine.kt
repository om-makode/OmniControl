package com.omnicontrol.feature.voice.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.omnicontrol.core.common.OmniLog
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

class TtsEngine(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private val isReady = AtomicBoolean(false)
    val isSpeaking = AtomicBoolean(false)

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            OmniLog.e(TAG, "Failed to instantiate TextToSpeech", e)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val engine = tts ?: return
            val result = engine.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                OmniLog.w(TAG, "Locale.US not supported by TTS, falling back to default")
                engine.setLanguage(Locale.getDefault())
            }

            // Prefer offline voices if available
            try {
                val voices = engine.voices
                if (voices != null) {
                    val offlineVoice = voices.find { voice ->
                        voice.locale.language == Locale.US.language && !voice.isNetworkConnectionRequired
                    }
                    if (offlineVoice != null) {
                        engine.voice = offlineVoice
                        OmniLog.i(TAG, "Selected offline TTS voice: ${offlineVoice.name}")
                    }
                }
            } catch (e: Exception) {
                OmniLog.w(TAG, "Error selecting offline voice", e)
            }

            isReady.set(true)
            OmniLog.i(TAG, "TtsEngine initialized successfully")
        } else {
            OmniLog.e(TAG, "Failed to initialize TextToSpeech: status=$status")
        }
    }

    suspend fun speak(text: String, silentMode: Boolean = false): Boolean {
        if (silentMode || text.isBlank() || !isReady.get()) {
            return false
        }
        val engine = tts ?: return false

        return suspendCancellableCoroutine { continuation ->
            val utteranceId = UUID.randomUUID().toString()
            isSpeaking.set(true)

            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {
                    isSpeaking.set(true)
                }

                override fun onDone(id: String?) {
                    isSpeaking.set(false)
                    if (continuation.isActive) {
                        continuation.resume(true)
                    }
                }

                override fun onError(id: String?) {
                    isSpeaking.set(false)
                    if (continuation.isActive) {
                        continuation.resume(false)
                    }
                }
            })

            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            }

            val queueResult = engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            if (queueResult != TextToSpeech.SUCCESS) {
                isSpeaking.set(false)
                if (continuation.isActive) {
                    continuation.resume(false)
                }
            }

            continuation.invokeOnCancellation {
                stop()
            }
        }
    }

    fun stop() {
        try {
            tts?.stop()
            isSpeaking.set(false)
        } catch (e: Exception) {
            OmniLog.w(TAG, "Error stopping TTS", e)
        }
    }

    fun shutdown() {
        try {
            stop()
            tts?.shutdown()
            tts = null
            isReady.set(false)
        } catch (e: Exception) {
            OmniLog.w(TAG, "Error shutting down TTS", e)
        }
    }

    companion object {
        private const val TAG = "TtsEngine"
    }
}
