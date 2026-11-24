package com.omnicontrol.app.spike

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.omnicontrol.app.MainActivity
import com.omnicontrol.app.OmniApp
import com.omnicontrol.app.R
import com.omnicontrol.core.common.OmniLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class SpikeOverlayService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var windowManager: WindowManager? = null
    private var overlayBubble: View? = null
    private var recordingJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        OmniLog.i(TAG, "SpikeOverlayService created")
        startForegroundServiceSpecialUse()
        createFloatingBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        OmniLog.i(TAG, "onStartCommand action: $action")
        when (action) {
            ACTION_START_MIC_FROM_TRAMPOLINE -> {
                OmniLog.i(TAG, "Triggered mic start via TrampolineActivity fallback")
                startRecordingWithFgsUpgrade(useTrampoline = false)
            }
            ACTION_STOP_SERVICE -> {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundServiceSpecialUse() {
        val notification = buildNotification("Assistant Overlay Active")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
            startForeground(NOTIFICATION_ID, notification, fgsType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, OmniApp.CHANNEL_ID_OVERLAY)
            .setContentTitle("OmniControl Spike D")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createFloatingBubble() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val sizePx = (56 * resources.displayMetrics.density).toInt()
        val layoutParams = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 200
        }

        val bubble = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.RED)
            }
            setOnClickListener {
                OmniLog.i(TAG, "Overlay bubble tapped! Testing mic start...")
                startRecordingWithFgsUpgrade(useTrampoline = false)
            }
        }

        try {
            windowManager?.addView(bubble, layoutParams)
            overlayBubble = bubble
            OmniLog.i(TAG, "Overlay bubble view attached successfully")
        } catch (e: Exception) {
            OmniLog.e(TAG, "Error adding overlay bubble view", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startRecordingWithFgsUpgrade(useTrampoline: Boolean) {
        if (recordingJob?.isActive == true) {
            OmniLog.i(TAG, "Recording already active, ignoring tap")
            return
        }

        try {
            // Upgrade FGS to microphone
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val types = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                startForeground(NOTIFICATION_ID, buildNotification("Listening (Mic Active)..."), types)
            }
            OmniLog.i(TAG, "FGS upgrade to MICROPHONE succeeded directly!")
        } catch (e: Exception) {
            OmniLog.w(TAG, "Direct FGS microphone start failed: ${e.javaClass.simpleName}: ${e.message}")
            if (!useTrampoline) {
                OmniLog.i(TAG, "Attempting TrampolineActivity fallback...")
                val trampolineIntent = Intent(this, TrampolineActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(trampolineIntent)
                return
            }
        }

        // Run 10s audio record test
        recordingJob = serviceScope.launch(Dispatchers.IO) {
            val sampleRate = 16000
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat).coerceAtLeast(3200)

            var recorder: AudioRecord? = null
            try {
                recorder = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )

                if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                    OmniLog.e(TAG, "AudioRecord initialization failed!")
                    return@launch
                }

                recorder.startRecording()
                OmniLog.i(TAG, "AudioRecord started! Measuring RMS for 10 seconds...")

                val buffer = ShortArray(1600)
                var seconds = 0
                val endTime = System.currentTimeMillis() + 10_000

                while (isActive && System.currentTimeMillis() < endTime) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        var sum = 0.0
                        for (i in 0 until read) {
                            sum += buffer[i] * buffer[i]
                        }
                        val rms = sqrt(sum / read)
                        OmniLog.d(TAG, "[Mic Spike] second=${seconds++}, RMS=${"%.2f".format(rms)}")
                    }
                    delay(1000)
                }
                OmniLog.i(TAG, "[Mic Spike] 10s audio recording completed successfully!")
            } catch (e: Exception) {
                OmniLog.e(TAG, "Exception during AudioRecord", e)
            } finally {
                try {
                    recorder?.stop()
                    recorder?.release()
                } catch (e: Exception) {
                    OmniLog.w(TAG, "Error stopping recorder", e)
                }
                // Downgrade back to specialUse
                startForegroundServiceSpecialUse()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        recordingJob?.cancel()
        serviceScope.cancel()
        overlayBubble?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                OmniLog.w(TAG, "Error removing bubble view", e)
            }
        }
        OmniLog.i(TAG, "SpikeOverlayService destroyed")
    }

    companion object {
        const val TAG = "OmniVoice"
        private const val NOTIFICATION_ID = 2001
        const val ACTION_START_MIC_FROM_TRAMPOLINE = "com.omnicontrol.action.START_MIC_FROM_TRAMPOLINE"
        const val ACTION_STOP_SERVICE = "com.omnicontrol.action.STOP_SERVICE"
    }
}
