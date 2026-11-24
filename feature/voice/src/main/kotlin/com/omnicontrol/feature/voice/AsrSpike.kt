package com.omnicontrol.feature.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

object AsrSpike {
    private const val TAG = "OmniVoice"
    private const val SAMPLE_RATE = 16000
    private const val CHUNK_SIZE = 1600 // 100 ms at 16 kHz

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var recognizer: OnlineRecognizer? = null
    private var stream: OnlineStream? = null
    private var vad: Vad? = null

    val isRunning: Boolean get() = job?.isActive == true

    fun initEngines(context: Context): Boolean {
        try {
            val asrDir = File(context.filesDir, "models/asr")
            val vadDir = File(context.filesDir, "models/vad")

            val encoder = File(asrDir, "encoder.onnx")
            val decoder = File(asrDir, "decoder.onnx")
            val joiner = File(asrDir, "joiner.onnx")
            val tokens = File(asrDir, "tokens.txt")
            val sileroModel = File(vadDir, "silero_vad.onnx")

            OmniLog.i(TAG, "Checking ASR models in ${asrDir.absolutePath} and VAD in ${vadDir.absolutePath}")
            val asrReady = encoder.exists() && decoder.exists() && joiner.exists() && tokens.exists()
            val vadReady = sileroModel.exists()

            if (!asrReady) {
                OmniLog.w(TAG, "ASR models missing: encoder=${encoder.exists()}, decoder=${decoder.exists()}, joiner=${joiner.exists()}, tokens=${tokens.exists()}")
            } else {
                OmniLog.i(TAG, "Initializing Sherpa-ONNX OnlineRecognizer...")
                val modelConfig = OnlineModelConfig(
                    transducer = OnlineTransducerModelConfig(
                        encoder = encoder.absolutePath,
                        decoder = decoder.absolutePath,
                        joiner = joiner.absolutePath
                    ),
                    tokens = tokens.absolutePath,
                    numThreads = 4,
                    debug = false,
                    provider = "cpu",
                    modelType = "zipformer"
                )

                val featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80)
                val endpointConfig = EndpointConfig(
                    rule1 = EndpointRule(mustContainNonSilence = false, minTrailingSilence = 2.4f, minUtteranceLength = 0.0f),
                    rule2 = EndpointRule(mustContainNonSilence = true, minTrailingSilence = 1.0f, minUtteranceLength = 0.0f),
                    rule3 = EndpointRule(mustContainNonSilence = false, minTrailingSilence = 0.0f, minUtteranceLength = 20.0f)
                )

                val config = OnlineRecognizerConfig(
                    featConfig = featConfig,
                    modelConfig = modelConfig,
                    endpointConfig = endpointConfig,
                    enableEndpoint = true,
                    decodingMethod = "greedy_search"
                )

                recognizer = OnlineRecognizer(null, config)
                stream = recognizer?.createStream()
                OmniLog.i(TAG, "OnlineRecognizer initialized successfully!")
            }

            if (!vadReady) {
                OmniLog.w(TAG, "Silero VAD model missing: ${sileroModel.absolutePath}")
            } else {
                OmniLog.i(TAG, "Initializing Silero VAD...")
                val sileroConfig = SileroVadModelConfig(
                    model = sileroModel.absolutePath,
                    threshold = 0.5f,
                    minSilenceDuration = 0.25f,
                    minSpeechDuration = 0.25f,
                    windowSize = 512,
                    maxSpeechDuration = 10.0f
                )
                val vadModelConfig = VadModelConfig(
                    sileroVadModelConfig = sileroConfig,
                    sampleRate = SAMPLE_RATE,
                    numThreads = 1,
                    provider = "cpu",
                    debug = false
                )
                vad = Vad(null, vadModelConfig)
                OmniLog.i(TAG, "Silero VAD initialized successfully!")
            }

            return asrReady || vadReady
        } catch (e: Exception) {
            OmniLog.e(TAG, "Error initializing sherpa-onnx engines", e)
            return false
        }
    }

    @SuppressLint("MissingPermission")
    fun startSpike(
        context: Context,
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onVadStatus: (Boolean) -> Unit
    ) {
        if (isRunning) {
            OmniLog.w(TAG, "AsrSpike already running")
            return
        }

        initEngines(context)

        job = scope.launch {
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_FLOAT
            val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, channelConfig, AudioFormat.ENCODING_PCM_16BIT)
            val bufferSize = (minBuf * 2).coerceAtLeast(CHUNK_SIZE * 4)

            var recorder: AudioRecord? = null
            try {
                recorder = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )

                if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                    OmniLog.e(TAG, "AudioRecord failed to initialize")
                    return@launch
                }

                recorder.startRecording()
                OmniLog.i(TAG, "AudioRecord started for ASR + VAD spike")

                val chunk = FloatArray(CHUNK_SIZE)
                var lastText = ""

                while (isActive) {
                    val read = recorder.read(chunk, 0, chunk.size, AudioRecord.READ_BLOCKING)
                    if (read <= 0) continue

                    // Process VAD
                    vad?.let { v ->
                        v.acceptWaveform(chunk)
                        val isSpeech = v.isSpeechDetected()
                        withContext(Dispatchers.Main) {
                            onVadStatus(isSpeech)
                        }
                    }

                    // Process Streaming ASR
                    val r = recognizer
                    val s = stream
                    if (r != null && s != null) {
                        s.acceptWaveform(chunk, SAMPLE_RATE)
                        while (r.isReady(s)) {
                            r.decode(s)
                        }

                        val result = r.getResult(s)
                        val currentText = result.text.trim()
                        if (currentText.isNotEmpty() && currentText != lastText) {
                            lastText = currentText
                            OmniLog.d(TAG, "[Partial ASR] $currentText")
                            withContext(Dispatchers.Main) {
                                onPartial(currentText)
                            }
                        }

                        if (r.isEndpoint(s)) {
                            if (currentText.isNotEmpty()) {
                                OmniLog.i(TAG, "[FINAL ASR] $currentText")
                                withContext(Dispatchers.Main) {
                                    onFinal(currentText)
                                }
                            }
                            r.reset(s)
                            lastText = ""
                        }
                    }
                }
            } catch (e: Exception) {
                OmniLog.e(TAG, "Exception during AsrSpike loop", e)
            } finally {
                try {
                    recorder?.stop()
                    recorder?.release()
                } catch (e: Exception) {
                    OmniLog.w(TAG, "Error releasing AudioRecord", e)
                }
                OmniLog.i(TAG, "AsrSpike recording loop stopped")
            }
        }
    }

    fun stopSpike() {
        job?.cancel()
        job = null
        try {
            stream?.release()
            stream = null
            recognizer?.release()
            recognizer = null
            vad?.release()
            vad = null
        } catch (e: Exception) {
            OmniLog.w(TAG, "Error releasing sherpa-onnx objects", e)
        }
        OmniLog.i(TAG, "AsrSpike stopped and resources released")
    }
}
