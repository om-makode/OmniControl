package com.omnicontrol.feature.overlay

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Insets
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.omnicontrol.core.common.OmniLog
import com.omnicontrol.core.model.AssistantState
import com.omnicontrol.core.model.ChatItem
import com.omnicontrol.core.model.ChatRole
import com.omnicontrol.core.model.UiState
import com.omnicontrol.feature.overlay.state.AssistantEvent
import com.omnicontrol.feature.overlay.state.AssistantStateStore
import com.omnicontrol.feature.overlay.ui.BubbleContent
import com.omnicontrol.feature.overlay.ui.HighlightOverlayContent
import com.omnicontrol.feature.overlay.ui.PanelContent
import com.omnicontrol.feature.overlay.ui.StatusPillContent
import com.omnicontrol.feature.overlay.window.OverlayWindowManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.abs
import kotlin.math.sqrt

class AssistantService : LifecycleService() {

    private lateinit var overlayWindowManager: OverlayWindowManager
    private lateinit var notificationManager: NotificationManager

    private var bubbleView: ComposeView? = null
    private var panelView: ComposeView? = null
    private var statusPillView: ComposeView? = null
    private var highlightView: ComposeView? = null

    private var bubbleParams: WindowManager.LayoutParams? = null
    private var isDragging = false
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var touchSlop = 0

    override fun onCreate() {
        super.onCreate()
        OmniLog.i(TAG, "AssistantService onCreate")
        activeServiceInstance = this
        overlayWindowManager = OverlayWindowManager(this)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        touchSlop = ViewConfiguration.get(this).scaledTouchSlop

        createNotificationChannel()
        startForegroundSpecialUse()

        initBubbleWindow()
        initPanelWindow()
        initStatusPillWindow()
        initHighlightWindow()

        observeState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val action = intent?.action ?: ACTION_SHOW
        OmniLog.i(TAG, "onStartCommand action: $action")

        when (action) {
            ACTION_SHOW -> {
                // Ensure bubble is visible
                bubbleView?.let { v ->
                    bubbleParams?.let { p ->
                        if (!overlayWindowManager.hasWindow(KEY_BUBBLE)) {
                            overlayWindowManager.addWindow(KEY_BUBBLE, v, p)
                        }
                    }
                }
            }
            ACTION_STOP -> {
                stateStore.dispatch(AssistantEvent.StopSession)
            }
            ACTION_TOGGLE -> {
                stateStore.dispatch(AssistantEvent.BubbleTap)
            }
            ACTION_HIDE -> {
                overlayWindowManager.removeAll()
            }
            ACTION_QUIT -> {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        OmniLog.i(TAG, "AssistantService onDestroy")
        overlayWindowManager.removeAll()
        activeServiceInstance = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "OmniControl Assistant",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "OmniControl floating assistant and voice sessions"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundSpecialUse() {
        val notification = buildNotification(stateStore.state.value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
            startForeground(NOTIFICATION_ID, notification, type)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateForegroundType(isListening: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                val notification = buildNotification(stateStore.state.value)
                val type = if (isListening) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                }
                startForeground(NOTIFICATION_ID, notification, type)
            } catch (e: Exception) {
                OmniLog.w(TAG, "Could not update foreground service type", e)
            }
        }
    }

    private fun buildNotification(state: UiState): Notification {
        val stopPending = PendingIntent.getService(
            this,
            101,
            Intent(this, AssistantService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val togglePending = PendingIntent.getService(
            this,
            102,
            Intent(this, AssistantService::class.java).apply { action = ACTION_TOGGLE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val quitPending = PendingIntent.getService(
            this,
            103,
            Intent(this, AssistantService::class.java).apply { action = ACTION_QUIT },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusText = when (val a = state.assistant) {
            is AssistantState.Listening -> "Listening..."
            is AssistantState.Processing -> "Thinking..."
            is AssistantState.Observing -> "Observing screen..."
            is AssistantState.Executing -> "Executing: ${a.step}"
            is AssistantState.Confirming -> "Confirmation required: ${a.question}"
            is AssistantState.Paused -> "Paused: ${a.reason}"
            is AssistantState.Error -> "Error: ${a.message}"
            is AssistantState.Waiting -> "Action complete"
            is AssistantState.Stopped -> "Session ended"
            is AssistantState.Idle -> "Ready"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("OmniControl Assistant")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPending)
            .addAction(android.R.drawable.ic_menu_sort_by_size, "Panel", togglePending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Quit", quitPending)
            .build()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initBubbleWindow() {
        val bubbleSizePx = (56 * resources.displayMetrics.density).toInt()
        val insets = getSystemInsets()
        val screenBounds = getScreenBounds()

        // Start snapped to right edge, vertically centered
        val initialBubbleX = screenBounds.width() - bubbleSizePx - insets.right - 12
        val initialBubbleY = (screenBounds.height() / 2) - (bubbleSizePx / 2)

        val params = overlayWindowManager.createDefaultParams(
            width = bubbleSizePx,
            height = bubbleSizePx,
            gravity = Gravity.TOP or Gravity.START,
            x = initialBubbleX,
            y = initialBubbleY,
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )
        bubbleParams = params

        val composeView = ComposeView(this).apply {
            setContent {
                val state by stateStore.state.collectAsState()
                BubbleContent(
                    state = state.assistant,
                    audioLevel = state.audioLevel,
                    onClick = {
                        stateStore.dispatch(AssistantEvent.BubbleTap)
                    }
                )
            }
        }

        composeView.setOnTouchListener { _, event ->
            val p = bubbleParams ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = p.x
                    initialY = p.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (!isDragging && sqrt(dx * dx + dy * dy) > touchSlop) {
                        isDragging = true
                    }
                    if (isDragging) {
                        p.x = (initialX + dx).toInt()
                        p.y = (initialY + dy).toInt()
                        overlayWindowManager.updateWindow(KEY_BUBBLE, p)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!isDragging) {
                        stateStore.dispatch(AssistantEvent.BubbleTap)
                    } else {
                        snapBubbleToEdge(p)
                    }
                    true
                }
                else -> false
            }
        }

        bubbleView = composeView
        overlayWindowManager.addWindow(KEY_BUBBLE, composeView, params)
    }

    private fun snapBubbleToEdge(params: WindowManager.LayoutParams) {
        val bubbleSizePx = (56 * resources.displayMetrics.density).toInt()
        val insets = getSystemInsets()
        val screenBounds = getScreenBounds()

        val minX = insets.left + 12
        val maxX = screenBounds.width() - bubbleSizePx - insets.right - 12
        val minY = insets.top + 24
        val maxY = screenBounds.height() - bubbleSizePx - insets.bottom - 24

        // Clamp Y
        params.y = params.y.coerceIn(minY, maxY)

        // Choose closer horizontal edge
        val currentCenterX = params.x + (bubbleSizePx / 2)
        val screenCenterX = screenBounds.width() / 2
        val targetX = if (currentCenterX < screenCenterX) minX else maxX

        val startX = params.x
        val animator = ValueAnimator.ofInt(startX, targetX).apply {
            duration = 200
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                params.x = animation.animatedValue as Int
                overlayWindowManager.updateWindow(KEY_BUBBLE, params)
            }
        }
        animator.start()
    }

    private fun initPanelWindow() {
        val screenBounds = getScreenBounds()
        val insets = getSystemInsets()
        val panelWidth = (screenBounds.width() * 0.92f).toInt()

        val params = overlayWindowManager.createDefaultParams(
            width = panelWidth,
            height = WRAP_CONTENT,
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
            x = 0,
            y = insets.bottom + 32,
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )

        val composeView = ComposeView(this).apply {
            setContent {
                val state by stateStore.state.collectAsState()
                PanelContent(
                    uiState = state,
                    onMinimize = {
                        stateStore.dispatch(AssistantEvent.MinimizePanel)
                    },
                    onStopSession = {
                        stateStore.dispatch(AssistantEvent.StopSession)
                    },
                    onConfirm = { accepted ->
                        if (accepted) {
                            stateStore.dispatch(AssistantEvent.SetExecuting("User confirmed action", 1, 1))
                        } else {
                            stateStore.dispatch(AssistantEvent.SetWaiting)
                        }
                    },
                    onSendText = { text ->
                        stateStore.dispatch(
                            AssistantEvent.AppendMessage(
                                ChatItem(
                                    id = UUID.randomUUID().toString(),
                                    role = ChatRole.USER,
                                    content = text
                                )
                            )
                        )
                        stateStore.dispatch(AssistantEvent.SetProcessing)
                    },
                    onFocusChange = { isFocused ->
                        overlayWindowManager.setFocusable(KEY_PANEL, isFocused)
                    }
                )
            }
        }
        panelView = composeView
        // Added dynamically based on state.panelExpanded
    }

    private fun initStatusPillWindow() {
        val insets = getSystemInsets()
        val params = overlayWindowManager.createDefaultParams(
            width = WRAP_CONTENT,
            height = WRAP_CONTENT,
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL,
            x = 0,
            y = insets.top + 16,
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )

        val composeView = ComposeView(this).apply {
            setContent {
                val state by stateStore.state.collectAsState()
                StatusPillContent(
                    state = state.assistant,
                    onStop = {
                        stateStore.dispatch(AssistantEvent.StopSession)
                    }
                )
            }
        }
        statusPillView = composeView
        // Added dynamically based on state
    }

    private fun initHighlightWindow() {
        val params = overlayWindowManager.createDefaultParams(
            width = MATCH_PARENT,
            height = MATCH_PARENT,
            gravity = Gravity.TOP or Gravity.START,
            x = 0,
            y = 0,
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )

        val composeView = ComposeView(this).apply {
            setContent {
                val state by stateStore.state.collectAsState()
                HighlightOverlayContent(
                    targetBounds = state.targetBounds,
                    debugElements = state.debugElements
                )
            }
        }
        highlightView = composeView
        // Added dynamically when highlights exist
    }

    private fun observeState() {
        lifecycleScope.launch {
            stateStore.state.collectLatest { state ->
                // Update notification
                notificationManager.notify(NOTIFICATION_ID, buildNotification(state))

                // Update foreground type
                updateForegroundType(state.assistant is AssistantState.Listening)

                // Manage Panel window
                if (state.panelExpanded) {
                    if (!overlayWindowManager.hasWindow(KEY_PANEL)) {
                        panelView?.let { pv ->
                            val screenBounds = getScreenBounds()
                            val insets = getSystemInsets()
                            val panelWidth = (screenBounds.width() * 0.92f).toInt()
                            val params = overlayWindowManager.createDefaultParams(
                                width = panelWidth,
                                height = WRAP_CONTENT,
                                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
                                x = 0,
                                y = insets.bottom + 32,
                                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                            )
                            overlayWindowManager.addWindow(KEY_PANEL, pv, params)
                        }
                    }
                } else {
                    if (overlayWindowManager.hasWindow(KEY_PANEL)) {
                        overlayWindowManager.removeWindow(KEY_PANEL)
                    }
                }

                // Manage Status Pill window (shown when panel minimized and actively executing/observing/confirming)
                val showPill = !state.panelExpanded && (
                    state.assistant is AssistantState.Executing ||
                        state.assistant is AssistantState.Observing ||
                        state.assistant is AssistantState.Processing ||
                        state.assistant is AssistantState.Confirming
                    )

                if (showPill) {
                    if (!overlayWindowManager.hasWindow(KEY_STATUS_PILL)) {
                        statusPillView?.let { spv ->
                            val insets = getSystemInsets()
                            val params = overlayWindowManager.createDefaultParams(
                                width = WRAP_CONTENT,
                                height = WRAP_CONTENT,
                                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL,
                                x = 0,
                                y = insets.top + 16,
                                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                            )
                            overlayWindowManager.addWindow(KEY_STATUS_PILL, spv, params)
                        }
                    }
                } else {
                    if (overlayWindowManager.hasWindow(KEY_STATUS_PILL)) {
                        overlayWindowManager.removeWindow(KEY_STATUS_PILL)
                    }
                }

                // Manage Highlight window
                val showHighlight = state.targetBounds != null || state.debugElements.isNotEmpty()
                if (showHighlight) {
                    if (!overlayWindowManager.hasWindow(KEY_HIGHLIGHT)) {
                        highlightView?.let { hv ->
                            val params = overlayWindowManager.createDefaultParams(
                                width = MATCH_PARENT,
                                height = MATCH_PARENT,
                                gravity = Gravity.TOP or Gravity.START,
                                x = 0,
                                y = 0,
                                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                            )
                            overlayWindowManager.addWindow(KEY_HIGHLIGHT, hv, params)
                        }
                    }
                } else {
                    if (overlayWindowManager.hasWindow(KEY_HIGHLIGHT)) {
                        overlayWindowManager.removeWindow(KEY_HIGHLIGHT)
                    }
                }
            }
        }
    }

    private fun getScreenBounds(): android.graphics.Rect {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            wm.currentWindowMetrics.bounds
        } else {
            @Suppress("DEPRECATION")
            android.graphics.Rect(0, 0, resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
        }
    }

    private fun getSystemInsets(): Insets {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val insets = wm.currentWindowMetrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            )
            Insets.of(insets.left, insets.top, insets.right, insets.bottom)
        } else {
            Insets.of(0, (24 * resources.displayMetrics.density).toInt(), 0, (48 * resources.displayMetrics.density).toInt())
        }
    }

    companion object {
        private const val TAG = "AssistantService"
        const val CHANNEL_ID = "omnicontrol_overlay_channel"
        const val NOTIFICATION_ID = 2001

        const val ACTION_SHOW = "com.omnicontrol.action.SHOW_OVERLAY"
        const val ACTION_STOP = "com.omnicontrol.action.STOP_SESSION"
        const val ACTION_TOGGLE = "com.omnicontrol.action.TOGGLE_PANEL"
        const val ACTION_HIDE = "com.omnicontrol.action.HIDE_OVERLAY"
        const val ACTION_QUIT = "com.omnicontrol.action.QUIT_ASSISTANT"

        const val KEY_BUBBLE = "bubble"
        const val KEY_PANEL = "panel"
        const val KEY_STATUS_PILL = "status_pill"
        const val KEY_HIGHLIGHT = "highlight"

        val stateStore = AssistantStateStore()

        var activeServiceInstance: AssistantService? = null
            private set

        fun start(context: Context) {
            val intent = Intent(context, AssistantService::class.java).apply {
                action = ACTION_SHOW
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AssistantService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun toggle(context: Context) {
            val intent = Intent(context, AssistantService::class.java).apply {
                action = ACTION_TOGGLE
            }
            context.startService(intent)
        }

        fun quit(context: Context) {
            val intent = Intent(context, AssistantService::class.java).apply {
                action = ACTION_QUIT
            }
            context.startService(intent)
        }
    }
}
