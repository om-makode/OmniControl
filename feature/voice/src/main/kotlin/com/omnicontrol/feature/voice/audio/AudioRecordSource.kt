package com.omnicontrol.feature.voice.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.omnicontrol.core.common.OmniLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

class AudioRecordSource(
    private val sampleRate: Int = 16000,
    private val audioSource: Int = MediaRecorder.AudioSource.VOICE_COMMUNICATION
) {
    private var audioRecord: AudioRecord? = null
    private val isRecording = AtomicBoolean(false)
    private var recordingJob: Job? = null

    val active: Boolean get() = isRecording.get()

    @SuppressLint("MissingPermission")
    fun start(
        scope: CoroutineScope,
        onAudioData: (FloatArray) -> Unit,
        onAudioLevel: (Float) -> Unit
    ): Boolean {
        if (isRecording.get()) {
            OmniLog.w(TAG, "AudioRecordSource already active")
            return true
        }

        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = maxOf(minBufferSize, sampleRate / 10 * 2) // At least 100ms buffer

        try {
            audioRecord = AudioRecord(
                audioSource,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                OmniLog.e(TAG, "AudioRecord failed to initialize")
                audioRecord?.release()
                audioRecord = null
                return false
            }

            audioRecord?.startRecording()
            isRecording.set(true)
            OmniLog.i(TAG, "Continuous AudioRecord started at $sampleRate Hz")

            recordingJob = scope.launch(Dispatchers.IO) {
                val chunkSize = sampleRate / 10 // 100ms -> 1600 samples
                val shortBuffer = ShortArray(chunkSize)
                val floatBuffer = FloatArray(chunkSize)

                while (isActive && isRecording.get()) {
                    val record = audioRecord ?: break
                    val read = record.read(shortBuffer, 0, chunkSize)
                    if (read > 0) {
                        var sumSquares = 0.0
                        for (i in 0 until read) {
                            val sample = shortBuffer[i]
                            floatBuffer[i] = sample / 32768.0f
                            sumSquares += sample.toDouble() * sample.toDouble()
                        }

                        // RMS amplitude
                        val rms = sqrt(sumSquares / read)
                        val normalizedLevel = (rms / 32768.0f).coerceIn(0.0, 1.0).toFloat() * 3.0f

                        onAudioLevel(normalizedLevel.coerceIn(0f, 1f))
                        onAudioData(floatBuffer.copyOf(read))
                    }
                }
            }
            return true
        } catch (e: Exception) {
            OmniLog.e(TAG, "Error starting AudioRecord", e)
            stop()
            return false
        }
    }

    fun stop() {
        isRecording.set(false)
        recordingJob?.cancel()
        recordingJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            OmniLog.i(TAG, "AudioRecord stopped")
        } catch (e: Exception) {
            OmniLog.w(TAG, "Error stopping AudioRecord", e)
        }
    }

    companion object {
        private const val TAG = "AudioRecordSource"
    }
}
