package com.omnicontrol.feature.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.EndpointRule
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import com.omnicontrol.core.common.OmniLog
import com.omnicontrol.core.model.AgentAction
import com.omnicontrol.feature.voice.audio.AudioRecordSource
import com.omnicontrol.feature.voice.router.FastRouteResult
import com.omnicontrol.feature.voice.router.FastRouter
import com.omnicontrol.feature.voice.tts.TtsEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class VoiceEngine(
    private val context: Context,
    val tts: TtsEngine = TtsEngine(context)
) {
    private val audioSource = AudioRecordSource(sampleRate = 16000)
    private var recognizer: OnlineRecognizer? = null
    private var stream: OnlineStream? = null
    private var vad: Vad? = null

    private var androidSpeechRecognizer: SpeechRecognizer? = null
    private var isUsingAndroidFallback = false

    private val isSessionActive = AtomicBoolean(false)
    private var hotwordsString: String = ""

    var onPartialTranscript: ((String) -> Unit)? = null
    var onFinalUtterance: ((String) -> Unit)? = null
    var onAudioLevel: ((Float) -> Unit)? = null
    var onFastRouteAction: ((AgentAction, String?) -> Unit)? = null
    var onLlmPassRequired: ((String) -> Unit)? = null
    var onSessionEnd: (() -> Unit)? = null
    var onTaskInterrupted: (() -> Unit)? = null

    val isRunning: Boolean get() = isSessionActive.get()

    fun initialize(): Boolean {
        val asrDir = File(context.filesDir, "models/asr")
        val vadDir = File(context.filesDir, "models/vad")

        val encoder = File(asrDir, "encoder.onnx")
        val decoder = File(asrDir, "decoder.onnx")
        val joiner = File(asrDir, "joiner.onnx")
        val tokens = File(asrDir, "tokens.txt")
        val silero = File(vadDir, "silero_vad.onnx")

        val sherpaAvailable = encoder.exists() && decoder.exists() && joiner.exists() && tokens.exists()

        if (sherpaAvailable) {
            try {
                OmniLog.i(TAG, "Initializing Sherpa-ONNX Voice Engine...")
                val modelConfig = OnlineModelConfig(
                    transducer = OnlineTransducerModelConfig(
                        encoder = encoder.absolutePath,
                        decoder = decoder.absolutePath,
                        joiner = joiner.absolutePath
                    ),
                    tokens = tokens.absolutePath,
                    numThreads = 4,
                    provider = "cpu",
                    modelType = "zipformer"
                )

                val featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80)
                val endpointConfig = EndpointConfig(
                    rule1 = EndpointRule(mustContainNonSilence = false, minTrailingSilence = 2.4f, minUtteranceLength = 0.0f),
                    rule2 = EndpointRule(mustContainNonSilence = true, minTrailingSilence = 0.7f, minUtteranceLength = 0.0f),
                    rule3 = EndpointRule(mustContainNonSilence = false, minTrailingSilence = 0.0f, minUtteranceLength = 20.0f)
                )

                val recConfig = OnlineRecognizerConfig(
                    featConfig = featConfig,
                    modelConfig = modelConfig,
                    endpointConfig = endpointConfig,
                    enableEndpoint = true,
                    decodingMethod = "greedy_search"
                )

                recognizer = OnlineRecognizer(null, recConfig)
                stream = recognizer?.createStream(hotwordsString)

                if (silero.exists()) {
                    val sileroConfig = SileroVadModelConfig(
                        model = silero.absolutePath,
                        threshold = 0.5f,
                        minSilenceDuration = 0.7f,
                        minSpeechDuration = 0.25f,
                        windowSize = 512,
                        maxSpeechDuration = 10.0f
                    )
                    val vadModelConfig = VadModelConfig(
                        sileroVadModelConfig = sileroConfig,
                        sampleRate = 16000,
                        numThreads = 1,
                        provider = "cpu",
                        debug = false
                    )
                    vad = Vad(null, vadModelConfig)
                }

                isUsingAndroidFallback = false
                OmniLog.i(TAG, "Sherpa-ONNX voice pipeline ready")
                return true
            } catch (e: Exception) {
                OmniLog.e(TAG, "Failed to initialize Sherpa-ONNX, falling back to Android SpeechRecognizer", e)
            }
        }

        OmniLog.i(TAG, "Using Android SpeechRecognizer as offline fallback engine")
        isUsingAndroidFallback = true
        return true
    }

    fun updateHotwords(labels: List<String>) {
        if (labels.isEmpty()) return
        hotwordsString = labels.take(30).joinToString("/")
        try {
            val r = recognizer
            if (r != null) {
                stream = r.createStream(hotwordsString)
                OmniLog.d(TAG, "Updated contextual hotwords: $hotwordsString")
            }
        } catch (e: Exception) {
            OmniLog.w(TAG, "Could not set hotwords", e)
        }
    }

    fun startSession(scope: CoroutineScope): Boolean {
        if (isSessionActive.get()) {
            OmniLog.w(TAG, "Session already active")
            return true
        }
        isSessionActive.set(true)
        OmniLog.i(TAG, "Starting continuous voice session...")

        if (!isUsingAndroidFallback && recognizer != null) {
            return startSherpaSession(scope)
        } else {
            return startAndroidFallbackSession(scope)
        }
    }

    private fun startSherpaSession(scope: CoroutineScope): Boolean {
        val streamInstance = recognizer?.createStream(hotwordsString) ?: return false
        stream = streamInstance

        var speechDetected = false

        val started = audioSource.start(
            scope = scope,
            onAudioLevel = { level ->
                onAudioLevel?.invoke(level)
            },
            onAudioData = { samples ->
                val isTtsSpeaking = tts.isSpeaking.get()

                val s = stream ?: return@start
                s.acceptWaveform(samples, 16000)

                val v = vad
                if (v != null) {
                    v.acceptWaveform(samples)
                    if (v.isSpeechDetected()) {
                        speechDetected = true
                    }
                } else {
                    speechDetected = true
                }

                while (recognizer?.isReady(s) == true) {
                    recognizer?.decode(s)
                }

                val currentText = recognizer?.getResult(s)?.text?.trim() ?: ""

                if (currentText.isNotEmpty()) {
                    // Fast stop / barge-in interrupt check
                    if (isStopWord(currentText)) {
                        OmniLog.i(TAG, "Barge-in keyword 'stop' detected!")
                        tts.stop()
                        onTaskInterrupted?.invoke()
                        resetStream()
                        return@start
                    }

                    if (!isTtsSpeaking) {
                        onPartialTranscript?.invoke(currentText)
                    }
                }

                val isEndpoint = recognizer?.isEndpoint(s) == true
                if (isEndpoint && speechDetected && !isTtsSpeaking) {
                    val finalResult = recognizer?.getResult(s)?.text?.trim() ?: ""
                    OmniLog.i(TAG, "End of speech detected: '$finalResult'")
                    if (finalResult.isNotEmpty()) {
                        scope.launch(Dispatchers.Main) {
                            handleUtterance(finalResult)
                        }
                    }
                    resetStream()
                    speechDetected = false
                }
            }
        )
        return started
    }

    private fun startAndroidFallbackSession(scope: CoroutineScope): Boolean {
        scope.launch(Dispatchers.Main) {
            try {
                if (SpeechRecognizer.isRecognitionAvailable(context)) {
                    androidSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                        setRecognitionListener(object : RecognitionListener {
                            override fun onReadyForSpeech(params: Bundle?) {}
                            override fun onBeginningOfSpeech() {}
                            override fun onRmsChanged(rmsdB: Float) {
                                val normalized = ((rmsdB + 2) / 12f).coerceIn(0f, 1f)
                                onAudioLevel?.invoke(normalized)
                            }
                            override fun onBufferReceived(buffer: ByteArray?) {}
                            override fun onEndOfSpeech() {}
                            override fun onError(error: Int) {
                                OmniLog.w(TAG, "SpeechRecognizer error: $error")
                                if (isSessionActive.get()) {
                                    restartFallbackListening()
                                }
                            }
                            override fun onResults(results: Bundle?) {
                                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                val text = matches?.firstOrNull()?.trim() ?: ""
                                if (text.isNotEmpty()) {
                                    handleUtterance(text)
                                }
                                if (isSessionActive.get()) {
                                    restartFallbackListening()
                                }
                            }
                            override fun onPartialResults(partialResults: Bundle?) {
                                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                val text = matches?.firstOrNull()?.trim() ?: ""
                                if (text.isNotEmpty()) {
                                    if (isStopWord(text)) {
                                        tts.stop()
                                        onTaskInterrupted?.invoke()
                                    } else {
                                        onPartialTranscript?.invoke(text)
                                    }
                                }
                            }
                            override fun onEvent(eventType: Int, params: Bundle?) {}
                        })
                    }
                    restartFallbackListening()
                }
            } catch (e: Exception) {
                OmniLog.e(TAG, "Error starting Android speech recognizer", e)
            }
        }
        return true
    }

    private fun restartFallbackListening() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
            androidSpeechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            OmniLog.w(TAG, "Could not start fallback listening", e)
        }
    }

    private fun handleUtterance(utterance: String) {
        onFinalUtterance?.invoke(utterance)
        OmniLog.i(TAG, "Routing utterance: '$utterance'")

        when (val routeResult = FastRouter.route(utterance)) {
            is FastRouteResult.Stop -> {
                tts.stop()
                onTaskInterrupted?.invoke()
            }
            is FastRouteResult.Done -> {
                endSession()
            }
            is FastRouteResult.Action -> {
                routeResult.spokenReply?.let { reply ->
                    CoroutineScope(Dispatchers.Main).launch {
                        tts.speak(reply)
                    }
                }
                onFastRouteAction?.invoke(routeResult.action, routeResult.spokenReply)
            }
            is FastRouteResult.PassToLlm -> {
                onLlmPassRequired?.invoke(routeResult.utterance)
            }
        }
    }

    private fun isStopWord(text: String): Boolean {
        val lower = text.lowercase().trim()
        return lower == "stop" || lower == "cancel" || lower == "wait" || lower == "freeze"
    }

    private fun resetStream() {
        val r = recognizer
        val s = stream
        if (r != null && s != null) {
            r.reset(s)
        }
    }

    fun endSession() {
        isSessionActive.set(false)
        audioSource.stop()
        tts.stop()
        androidSpeechRecognizer?.stopListening()
        androidSpeechRecognizer?.destroy()
        androidSpeechRecognizer = null
        onSessionEnd?.invoke()
        OmniLog.i(TAG, "Voice session ended")
    }

    fun shutdown() {
        endSession()
        tts.shutdown()
        stream = null
        recognizer = null
        vad = null
    }

    companion object {
        private const val TAG = "VoiceEngine"
    }
}
