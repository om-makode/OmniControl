package com.omnicontrol.feature.voice

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.KeyEvent
import com.omnicontrol.core.common.OmniLog

/**
 * Continuous listening built on Android's SpeechRecognizer.
 *
 * Android only recognises one phrase per request, so this class runs a loop: listen -> result ->
 * listen again, and recovers from every error code. It prefers the on-device recogniser (offline,
 * no beep) and falls back to the normal one when the language pack is missing.
 *
 * All methods must be called on the main thread.
 */
class SpeechInput(private val context: Context) {

    interface Listener {
        fun onReady() {}
        fun onSpeechStart() {}
        fun onPartial(text: String) {}
        /** [alternatives] best first. [confidence] may be empty. */
        fun onFinal(alternatives: List<String>, confidence: FloatArray?)
        fun onLevel(level: Float) {}
        fun onProblem(message: String, fatal: Boolean) {}
    }

    var listener: Listener? = null
    var preferOffline = true
    var languageTag: String = ""
    var foregroundPackage: (() -> String)? = null
    var isMediaActionExpected: (() -> Boolean)? = null
    var onMediaPlaybackResumed: (() -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private var recognizer: SpeechRecognizer? = null
    private var usingOnDevice = false
    private var onDeviceBroken = false

    /** The session wants the mic on. */
    private var active = false
    /** Temporarily muted (e.g. while we speak), but the session continues. */
    private var paused = false
    private var listening = false
    private var consecutiveErrors = 0
    private var generation = 0

    val isActive: Boolean get() = active

    fun start() {
        if (active) return
        active = true
        paused = false
        consecutiveErrors = 0
        listenSoon(0)
    }

    fun stop() {
        active = false
        paused = false
        generation++
        main.removeCallbacksAndMessages(null)
        destroyRecognizer()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
            focusRequest = null
        }
    }

    /** Stop hearing (so our own voice is not transcribed) but keep the session alive. */
    fun pause() {
        if (!active || paused) return
        paused = true
        generation++
        main.removeCallbacksAndMessages(null)
        try { recognizer?.cancel() } catch (_: Exception) {}
        listening = false
    }

    fun resume(delayMs: Long = 250) {
        if (!active || !paused) return
        paused = false
        listenSoon(delayMs)
    }

    fun release() = stop()

    // ------------------------------------------------------------------------------------

    private val focusListener = AudioManager.OnAudioFocusChangeListener { /* audio manager handles ducking */ }

    private fun requestTransientFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val am = audioManager ?: return
            try {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener(focusListener, main)
                    .setAcceptsDelayedFocusGain(false)
                    .setWillPauseWhenDucked(false)
                    .build()
                focusRequest = req
                am.requestAudioFocus(req)
            } catch (e: Exception) {
                OmniLog.w(TAG, "requestTransientFocus failed", e)
            }
        }
    }

    private fun isMediaActive(): Boolean {
        if (isMediaActionExpected?.invoke() == true) return true
        if (audioManager?.isMusicActive == true) return true
        val pkg = foregroundPackage?.invoke().orEmpty().lowercase()
        return pkg.isNotEmpty() && (
            pkg.contains("youtube") || pkg.contains("video") || pkg.contains("music") ||
            pkg.contains("spotify") || pkg.contains("vlc") || pkg.contains("netflix") ||
            pkg.contains("player") || pkg.contains("hotstar") || pkg.contains("reels")
        )
    }

    private fun dispatchMediaPlay() {
        try {
            val am = audioManager ?: return
            val now = SystemClock.uptimeMillis()
            val down = KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY, 0)
            val up = KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY, 0)
            am.dispatchMediaKeyEvent(down)
            am.dispatchMediaKeyEvent(up)
        } catch (e: Exception) {
            OmniLog.w(TAG, "dispatchMediaPlay failed", e)
        }
    }

    private fun ensureMediaPlayback(wasActive: Boolean) {
        if (!wasActive && !isMediaActive()) return
        main.postDelayed({ if (active && !paused) dispatchMediaPlay() }, 80)
        main.postDelayed({ if (active && !paused) dispatchMediaPlay() }, 250)
    }

    private fun listenSoon(delayMs: Long) {
        val gen = ++generation
        main.postDelayed({ if (gen == generation) listenNow() }, delayMs)
    }

    private fun listenNow() {
        if (!active || paused) return
        val wasMediaActive = isMediaActive()
        val r = recognizer ?: createRecognizer() ?: run {
            listener?.onProblem("Speech recognition is not available on this phone. Install/enable the Google app.", true)
            active = false
            return
        }
        try {
            requestTransientFocus()
            r.startListening(buildIntent())
            listening = true
            if (wasMediaActive) {
                ensureMediaPlayback(true)
                onMediaPlaybackResumed?.invoke()
            }
        } catch (e: Exception) {
            OmniLog.w(TAG, "startListening failed", e)
            destroyRecognizer()
            listenSoon(600)
        }
    }

    private fun createRecognizer(): SpeechRecognizer? {
        val r = try {
            if (preferOffline && !onDeviceBroken && android.os.Build.VERSION.SDK_INT >= 31 &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            ) {
                usingOnDevice = true
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else if (SpeechRecognizer.isRecognitionAvailable(context)) {
                usingOnDevice = false
                SpeechRecognizer.createSpeechRecognizer(context)
            } else null
        } catch (e: Exception) {
            OmniLog.w(TAG, "createRecognizer failed", e); null
        } ?: return null
        r.setRecognitionListener(callbacks)
        recognizer = r
        OmniLog.i(TAG, "Recognizer created (onDevice=$usingOnDevice)")
        return r
    }

    private fun destroyRecognizer() {
        listening = false
        try { recognizer?.cancel() } catch (_: Exception) {}
        try { recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
    }

    private fun buildIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
        // Give people time to finish a long command before the recogniser cuts them off.
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L)
        if (languageTag.isNotBlank()) putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
    }

    private val callbacks = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            if (isMediaActive()) {
                ensureMediaPlayback(true)
            }
            listener?.onReady()
        }
        override fun onBeginningOfSpeech() { listener?.onSpeechStart() }
        override fun onRmsChanged(rmsdB: Float) { listener?.onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f)) }
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            if (paused) return
            val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()?.trim().orEmpty()
            if (text.isNotEmpty()) listener?.onPartial(text)
        }

        override fun onResults(results: Bundle?) {
            listening = false
            consecutiveErrors = 0
            if (paused) return
            val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
            val conf = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
            if (list.isNotEmpty()) listener?.onFinal(list, conf)
            if (active && !paused) listenSoon(80)
        }

        override fun onError(error: Int) {
            listening = false
            if (!active || paused) return
            OmniLog.d(TAG, "recognizer error $error (${errorName(error)})")
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    // Normal silence: just listen again.
                    consecutiveErrors = 0
                    listenSoon(60)
                }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    listener?.onProblem("Microphone permission is missing.", true)
                    stop()
                }
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> {
                    if (usingOnDevice) {
                        OmniLog.i(TAG, "On-device language pack missing, switching to standard recognizer")
                        onDeviceBroken = true
                        destroyRecognizer()
                        listenSoon(200)
                    } else {
                        listener?.onProblem("Speech language not available. Pick another language in Settings.", true)
                        stop()
                    }
                }
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                SpeechRecognizer.ERROR_SERVER,
                SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> {
                    consecutiveErrors++
                    if (consecutiveErrors == 3) {
                        listener?.onProblem("Speech needs internet or an offline language pack (Settings > Google > Offline speech).", false)
                    }
                    destroyRecognizer()
                    listenSoon(minOf(4000L, 500L * consecutiveErrors))
                }
                else -> {
                    // BUSY, CLIENT, TOO_MANY_REQUESTS, AUDIO, CANNOT_CHECK_SUPPORT...: rebuild the recogniser.
                    consecutiveErrors++
                    if (usingOnDevice && consecutiveErrors >= 2) {
                        OmniLog.i(TAG, "On-device recognizer failing ($error), switching to standard recognizer")
                        onDeviceBroken = true
                        destroyRecognizer()
                        listenSoon(200)
                        return
                    }
                    destroyRecognizer()
                    if (consecutiveErrors >= 8) {
                        listener?.onProblem("The microphone keeps failing. Is another app using it?", false)
                        consecutiveErrors = 0
                        listenSoon(3000)
                    } else listenSoon(300L + 150L * consecutiveErrors)
                }
            }
        }
    }

    private fun errorName(e: Int) = when (e) {
        SpeechRecognizer.ERROR_AUDIO -> "audio"
        SpeechRecognizer.ERROR_CLIENT -> "client"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "permission"
        SpeechRecognizer.ERROR_NETWORK -> "network"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "network timeout"
        SpeechRecognizer.ERROR_NO_MATCH -> "no match"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "busy"
        SpeechRecognizer.ERROR_SERVER -> "server"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "silence"
        else -> "code $e"
    }

    companion object {
        private const val TAG = "OmniSpeech"
    }
}
