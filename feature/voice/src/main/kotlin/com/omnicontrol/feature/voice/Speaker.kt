package com.omnicontrol.feature.voice

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.omnicontrol.core.common.OmniLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Text-to-speech with proper "started / finished" tracking so the microphone can be muted while
 * the assistant talks (otherwise it hears itself and runs its own reply as a command).
 */
class Speaker(context: Context) {

    var onSpeakingChanged: ((Boolean) -> Unit)? = null
    var enabled = true

    private val ready = AtomicBoolean(false)
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    private val counter = AtomicInteger()
    @Volatile private var speaking = false

    /** Text of the last thing we said, used to ignore echoes picked up by the mic. */
    @Volatile var lastSpoken: String = ""
        private set

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            setupVoice()
            ready.set(true)
        } else OmniLog.w(TAG, "TTS init failed: $status")
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = setSpeaking(true)
            override fun onDone(utteranceId: String?) = finish(utteranceId)
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finish(utteranceId)
            override fun onError(utteranceId: String?, errorCode: Int) = finish(utteranceId)
            override fun onStop(utteranceId: String?, interrupted: Boolean) = finish(utteranceId)
        })
    }

    private fun setupVoice() {
        val locale = Locale.getDefault()
        if (tts.isLanguageAvailable(locale) < TextToSpeech.LANG_AVAILABLE) tts.language = Locale.US else tts.language = locale
        runCatching {
            tts.voices?.filter { !it.isNetworkConnectionRequired && it.locale.language == tts.voice?.locale?.language }
                ?.maxByOrNull { it.quality }?.let { tts.voice = it }
        }
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        tts.setAudioAttributes(attrs)
        tts.setSpeechRate(1.05f)
    }

    private fun finish(id: String?) {
        id?.let { pending.remove(it)?.complete(Unit) }
        if (pending.isEmpty()) setSpeaking(false)
    }

    private fun setSpeaking(value: Boolean) {
        if (speaking == value) return
        speaking = value
        onSpeakingChanged?.invoke(value)
    }

    val isSpeaking: Boolean get() = speaking

    /** Speak and suspend until finished (max ~12 s). Returns immediately if speech is off. */
    suspend fun say(text: String) {
        if (!enabled || text.isBlank() || !ready.get()) return
        val id = "omni-${counter.incrementAndGet()}"
        val done = CompletableDeferred<Unit>()
        pending[id] = done
        lastSpoken = text
        setSpeaking(true)
        val res = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        if (res != TextToSpeech.SUCCESS) { finish(id); return }
        withTimeoutOrNull(12_000) { done.await() } ?: finish(id)
    }

    fun stop() {
        runCatching { tts.stop() }
        pending.values.forEach { it.complete(Unit) }
        pending.clear()
        setSpeaking(false)
    }

    fun shutdown() {
        stop()
        runCatching { tts.shutdown() }
    }

    companion object {
        private const val TAG = "OmniTTS"
    }
}
