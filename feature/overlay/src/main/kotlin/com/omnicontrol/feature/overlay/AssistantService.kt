package com.omnicontrol.feature.overlay

import android.Manifest
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowInsets
import android.view.animation.DecelerateInterpolator
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.omnicontrol.core.common.OmniLog
import com.omnicontrol.core.common.TextMatch
import com.omnicontrol.core.common.settings.OmniSettings
import com.omnicontrol.core.common.settings.SettingsState
import com.omnicontrol.core.model.ChatItem
import com.omnicontrol.core.model.ChatRole
import com.omnicontrol.core.model.NumberTag
import com.omnicontrol.core.model.Phase
import com.omnicontrol.feature.accessibility.OmniAccessibilityService
import com.omnicontrol.feature.accessibility.TouchFeedback
import com.omnicontrol.feature.agent.AgentController
import com.omnicontrol.feature.agent.AgentUi
import com.omnicontrol.feature.agent.quick.QuickCommands
import com.omnicontrol.feature.agent.quick.ScreenQueries
import com.omnicontrol.feature.overlay.ui.BallState
import com.omnicontrol.feature.overlay.ui.Caption
import com.omnicontrol.feature.overlay.ui.NumberTagsOverlay
import com.omnicontrol.feature.overlay.ui.Orb
import com.omnicontrol.feature.overlay.ui.Panel
import com.omnicontrol.feature.overlay.ui.TouchBall
import com.omnicontrol.feature.overlay.window.OverlayWindowManager
import com.omnicontrol.feature.voice.SpeechFixer
import com.omnicontrol.feature.voice.SpeechInput
import com.omnicontrol.feature.voice.Speaker
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Owns everything that floats over other apps: the orb, the live caption, the chat panel and the
 * touching ball. Also owns the voice session and the agent.
 */
class AssistantService : LifecycleService(), TouchFeedback {

    private lateinit var windows: OverlayWindowManager
    private lateinit var speech: SpeechInput
    private lateinit var speaker: Speaker
    private lateinit var agent: AgentController
    private lateinit var omniSettings: OmniSettings
    @Volatile private var settings = SettingsState()

    private val main = Handler(Looper.getMainLooper())
    private val ball = MutableStateFlow(BallState())
    private var ballHideJob: Job? = null
    private var captionHideJob: Job? = null
    private var inactivityJob: Job? = null
    private var tagsRefreshJob: Job? = null
    private var confirm: CompletableDeferred<Boolean>? = null
    private var lastSpokeAt = 0L
    private val density get() = resources.displayMetrics.density

    // ============================================================================ lifecycle

    override fun onCreate() {
        super.onCreate()
        AssistantHub.serviceRunning = true
        windows = OverlayWindowManager(this)
        omniSettings = OmniSettings(this)
        createChannel()
        setForeground(mic = false)

        speaker = Speaker(this).apply {
            onSpeakingChanged = { talking ->
                main.post {
                    if (talking) speech.pause() else { lastSpokeAt = System.currentTimeMillis(); speech.resume(350) }
                }
            }
        }
        speech = SpeechInput(this).apply {
            listener = speechListener
            foregroundPackage = { OmniAccessibilityService.foregroundPackage() }
            isMediaActionExpected = { OmniAccessibilityService.mediaActionExpected }
            onMediaPlaybackResumed = { OmniAccessibilityService.mediaActionExpected = false }
        }
        agent = AgentController(this, lifecycleScope, agentUi) { settings }

        OmniAccessibilityService.touchFeedback = this
        OmniAccessibilityService.panicListener = { main.post { stopTask("Stopped (volume keys)") } }
        OmniAccessibilityService.screenChangeListener = { main.post { scheduleNumberTagsRefresh(300L) } }

        lifecycleScope.launch {
            omniSettings.settingsFlow.collect { s ->
                settings = s
                speaker.enabled = s.speakReplies
                speech.preferOffline = s.preferOfflineSpeech
                speech.languageTag = s.speechLanguage
                AssistantHub.update { it.copy(brainName = agent.brainLabel()) }
            }
        }
        lifecycleScope.launch {
            AssistantHub.state.map { Triple(it.phase, it.sessionActive, it.status) }.distinctUntilChanged().collect { (_, _, _) ->
                updateNotification()
                syncCaption()
                syncNumberTags()
            }
        }
        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        showOrb()
        scheduleNumberTagsRefresh(500L)
        OmniLog.i(TAG, "Assistant service started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_TOGGLE_LISTEN -> if (AssistantHub.state.value.sessionActive) stopSession() else startSession(false)
            ACTION_START_LISTENING -> startSession(false)
            ACTION_START_LISTENING_FG -> startSession(true)
            ACTION_STOP -> stopSession()
            ACTION_OPEN_PANEL -> openPanel()
            ACTION_COMMAND -> intent.getStringExtra(EXTRA_TEXT)?.let { runCommand(it) }
            ACTION_QUIT -> { stopSelf(); return START_NOT_STICKY }
            else -> if (!windows.has(KEY_ORB)) showOrb()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenOffReceiver) }
        agent.release()
        speech.release()
        speaker.shutdown()
        tagsRefreshJob?.cancel()
        windows.removeAll()
        if (OmniAccessibilityService.touchFeedback === this) OmniAccessibilityService.touchFeedback = null
        OmniAccessibilityService.panicListener = null
        OmniAccessibilityService.screenChangeListener = null
        AssistantHub.serviceRunning = false
        AssistantHub.update { it.copy(sessionActive = false, busy = false, phase = Phase.IDLE, panelOpen = false, transcript = "", status = "", numberTags = emptyList()) }
        super.onDestroy()
    }

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) stopSession()
        }
    }

    // ============================================================================ voice session

    private fun hasMic() = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun startSession(fromActivity: Boolean) {
        if (AssistantHub.state.value.sessionActive && speech.isActive) return
        if (!hasMic()) {
            AssistantHub.post(ChatItem(role = ChatRole.SYSTEM, content = "Microphone permission needed. Opening OmniControl…"))
            packageManager.getLaunchIntentForPackage(packageName)?.let {
                startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("request_mic", true))
            }
            return
        }
        if (!setForeground(mic = true) && !fromActivity) {
            // Android said no from the background: bounce through an invisible activity.
            startActivity(
                Intent(this, MicTrampolineActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            )
            return
        }
        speech.start()
        AssistantHub.update {
            it.copy(sessionActive = true, phase = if (it.busy) it.phase else Phase.LISTENING, transcript = "",
                status = "Listening — say a command")
        }
        resetInactivity()
        scheduleNumberTagsRefresh(100L)
    }

    private fun stopSession() {
        speech.stop()
        agent.cancel()
        speaker.stop()
        confirm?.complete(false)
        setForeground(mic = false)
        inactivityJob?.cancel()
        tagsRefreshJob?.cancel()
        windows.remove(KEY_TAGS)
        AssistantHub.update {
            it.copy(sessionActive = false, busy = false, phase = Phase.IDLE, transcript = "", status = "", audioLevel = 0f, stepIndex = 0, numberTags = emptyList())
        }
    }

    /** Stop the current task but keep listening. */
    private fun stopTask(message: String = "Stopped") {
        agent.cancel()
        speaker.stop()
        confirm?.complete(false)
        AssistantHub.update {
            it.copy(busy = false, stepIndex = 0, phase = if (it.sessionActive) Phase.LISTENING else Phase.IDLE, status = message)
        }
        AssistantHub.post(ChatItem(role = ChatRole.SYSTEM, content = message))
    }

    private fun resetInactivity() {
        inactivityJob?.cancel()
        inactivityJob = lifecycleScope.launch {
            delay(INACTIVITY_MS)
            if (!agent.isBusy) {
                AssistantHub.post(ChatItem(role = ChatRole.SYSTEM, content = "Stopped listening after a few quiet minutes."))
                stopSession()
            } else resetInactivity()
        }
    }

    private val speechListener = object : SpeechInput.Listener {
        override fun onReady() {
            val s = AssistantHub.state.value
            if (!s.busy && s.phase != Phase.CONFIRM && s.phase != Phase.SPEAKING) {
                AssistantHub.update { it.copy(phase = Phase.LISTENING) }
            }
        }

        override fun onPartial(text: String) {
            // Instant stop while something is running, without waiting for the final result.
            if (AssistantHub.state.value.busy && QuickCommands.isStop(SpeechFixer.clean(text))) {
                stopTask(); return
            }
            AssistantHub.update {
                it.copy(transcript = text, phase = if (it.busy || it.phase == Phase.CONFIRM) it.phase else Phase.HEARING)
            }
        }

        override fun onFinal(alternatives: List<String>, confidence: FloatArray?) {
            resetInactivity()
            val vocab = OmniAccessibilityService.launcher()?.installedApps()?.map { it.label }.orEmpty()
            handleUtterance(SpeechFixer.pickBest(alternatives, vocab))
        }

        override fun onLevel(level: Float) {
            val p = AssistantHub.state.value.phase
            if (p == Phase.LISTENING || p == Phase.HEARING) AssistantHub.update { it.copy(audioLevel = level) }
        }

        override fun onProblem(message: String, fatal: Boolean) {
            AssistantHub.post(ChatItem(role = ChatRole.SYSTEM, content = message))
            AssistantHub.update { it.copy(status = message, phase = Phase.ERROR) }
            if (fatal) stopSession()
        }
    }

    private fun handleUtterance(text: String) {
        if (text.isBlank()) return
        // Ignore our own reply if the mic caught the tail of it.
        val last = speaker.lastSpoken
        if (last.isNotBlank() && System.currentTimeMillis() - lastSpokeAt < 3000 && TextMatch.labelScore(text, last) > 0.8) return

        AssistantHub.update { it.copy(transcript = text) }
        confirm?.let { d ->
            when {
                QuickCommands.isYes(text) -> d.complete(true)
                QuickCommands.isNo(text) -> d.complete(false)
                else -> { d.complete(false); agent.submit(text) }
            }
            return
        }
        when {
            QuickCommands.isEnd(text) -> {
                lifecycleScope.launch { speaker.say("Okay") }
                stopSession()
            }
            QuickCommands.isStop(text) -> if (agent.isBusy) stopTask() else stopSession()
            else -> runCommand(text)
        }
    }

    private fun runCommand(text: String) {
        if (!windows.has(KEY_ORB)) showOrb()
        AssistantHub.update { it.copy(transcript = text, busy = true, phase = Phase.THINKING, status = "Working on it") }
        agent.submit(text)
    }

    // ============================================================================ agent ui

    private val agentUi = object : AgentUi {
        override fun setPhase(phase: Phase, status: String) {
            if ((phase == Phase.THINKING || phase == Phase.ACTING) && AssistantHub.state.value.panelOpen &&
                confirm == null
            ) main.post { closePanel() }
            AssistantHub.update {
                it.copy(
                    phase = phase,
                    status = status.ifBlank { it.status },
                    busy = when (phase) { Phase.THINKING, Phase.ACTING, Phase.CONFIRM -> true; else -> it.busy }
                )
            }
        }

        override fun setStep(index: Int) = AssistantHub.update { it.copy(stepIndex = index) }
        override fun setApp(label: String) = AssistantHub.update { it.copy(currentApp = label) }
        override fun post(item: ChatItem) = AssistantHub.post(item)

        override suspend fun speak(text: String) {
            if (settings.speakReplies) speaker.say(text.take(300))
        }

        override suspend fun confirm(question: String): Boolean {
            val d = CompletableDeferred<Boolean>()
            confirm = d
            AssistantHub.update { it.copy(confirmQuestion = question, phase = Phase.CONFIRM, status = question) }
            if (!AssistantHub.state.value.sessionActive) withContext(Dispatchers.Main) { openPanel() }
            speak("$question Say yes or no.")
            val answer = withTimeoutOrNull(30_000) { d.await() } ?: false
            confirm = null
            AssistantHub.update { it.copy(confirmQuestion = null) }
            return answer
        }

        override fun taskFinished() {
            AssistantHub.update {
                it.copy(busy = false, stepIndex = 0, phase = if (it.sessionActive) Phase.LISTENING else Phase.IDLE)
            }
            main.post {
                syncCaption()
                scheduleNumberTagsRefresh(150L)
                val pkg = OmniAccessibilityService.foregroundPackage().lowercase()
                if (pkg.contains("youtube") || pkg.contains("video") || pkg.contains("player")) {
                    OmniAccessibilityService.notifyMediaActionExpected()
                }
            }
        }
    }

    // ============================================================================ windows

    private fun screenSize(): Pair<Int, Int> {
        val b = windowManagerBounds()
        return b.width() to b.height()
    }

    private fun windowManagerBounds() =
        (getSystemService(WINDOW_SERVICE) as android.view.WindowManager).currentWindowMetrics.bounds

    private fun topInset(): Int =
        (getSystemService(WINDOW_SERVICE) as android.view.WindowManager).currentWindowMetrics.windowInsets
            .getInsetsIgnoringVisibility(WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout()).top

    @SuppressLint("ClickableViewAccessibility")
    private fun showOrb() {
        if (!Settings.canDrawOverlays(this)) {
            AssistantHub.post(ChatItem(role = ChatRole.SYSTEM, content = "Allow \"Display over other apps\" for OmniControl."))
            return
        }
        val size = (68 * density).toInt()
        val prefs = getSharedPreferences("omni_overlay", MODE_PRIVATE)
        val (w, h) = screenSize()
        val params = windows.params(size, size, x = prefs.getInt("orb_x", w - size - (8 * density).toInt()), y = prefs.getInt("orb_y", (h * 0.55f).toInt()))
        val view = windows.show(KEY_ORB, params) {
            val s by AssistantHub.state.collectAsState()
            Orb(s.phase, s.sessionActive, s.audioLevel, s.busy)
        } ?: return

        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0
        var dragging = false; var longPressed = false
        val longPress = Runnable { if (!dragging) { longPressed = true; openPanel() } }
        view.setOnTouchListener { _, e ->
            val p = windows.paramsOf(KEY_ORB) ?: return@setOnTouchListener false
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = p.x; startY = p.y
                    dragging = false; longPressed = false
                    main.postDelayed(longPress, 450)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (!dragging && hypot(dx, dy) > slop) { dragging = true; main.removeCallbacks(longPress) }
                    if (dragging) { p.x = (startX + dx).toInt(); p.y = (startY + dy).toInt(); windows.update(KEY_ORB) }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    main.removeCallbacks(longPress)
                    when {
                        dragging -> snapOrb()
                        longPressed -> Unit
                        e.actionMasked == MotionEvent.ACTION_UP -> onOrbTap()
                    }
                }
            }
            true
        }
    }

    private fun onOrbTap() {
        val s = AssistantHub.state.value
        when {
            s.busy -> stopTask()
            s.sessionActive -> stopSession()
            else -> startSession(false)
        }
    }

    private fun snapOrb() {
        val p = windows.paramsOf(KEY_ORB) ?: return
        val (w, h) = screenSize()
        val size = p.width
        val margin = (6 * density).toInt()
        val targetX = if (p.x + size / 2 < w / 2) margin else w - size - margin
        p.y = p.y.coerceIn(topInset() + margin, h - size - (48 * density).toInt())
        ValueAnimator.ofInt(p.x, targetX).apply {
            duration = 220
            interpolator = DecelerateInterpolator()
            addUpdateListener { p.x = it.animatedValue as Int; windows.update(KEY_ORB) }
            start()
        }
        getSharedPreferences("omni_overlay", MODE_PRIVATE).edit().putInt("orb_x", targetX).putInt("orb_y", p.y).apply()
    }

    private fun syncCaption() {
        val s = AssistantHub.state.value
        val wanted = !s.panelOpen && (s.sessionActive || s.busy || s.phase == Phase.ERROR || s.phase == Phase.SPEAKING)
        if (wanted && !windows.has(KEY_CAPTION) && Settings.canDrawOverlays(this)) {
            val params = windows.params(MATCH_PARENT, WRAP_CONTENT, gravity = Gravity.TOP or Gravity.START,
                y = topInset() + (6 * density).toInt(), touchable = false)
            windows.show(KEY_CAPTION, params) {
                val st by AssistantHub.state.collectAsState()
                Caption(st)
            }
        }
        captionHideJob?.cancel()
        if (!wanted && windows.has(KEY_CAPTION)) windows.remove(KEY_CAPTION)
        else if (!s.sessionActive && !s.busy && windows.has(KEY_CAPTION)) {
            captionHideJob = lifecycleScope.launch {
                delay(5000)
                val now = AssistantHub.state.value
                if (!now.sessionActive && !now.busy) windows.remove(KEY_CAPTION)
            }
        }
    }

    private fun openPanel() {
        if (!Settings.canDrawOverlays(this)) return
        val (_, h) = screenSize()
        val params = windows.params(MATCH_PARENT, (h * 0.62f).toInt(), gravity = Gravity.BOTTOM, focusable = false)
        AssistantHub.update { it.copy(panelOpen = true, brainName = agent.brainLabel()) }
        windows.remove(KEY_CAPTION)
        windows.remove(KEY_TAGS)
        windows.show(KEY_PANEL, params) {
            val s by AssistantHub.state.collectAsState()
            Panel(
                state = s,
                onSend = { text -> closePanel(); runCommand(text) },
                onMic = { if (AssistantHub.state.value.sessionActive) stopSession() else { closePanel(); startSession(false) } },
                onStop = { stopTask() },
                onClose = { closePanel() },
                onConfirm = { yes -> confirm?.complete(yes) },
                onInputFocus = { focused -> windows.setFocusable(KEY_PANEL, focused) }
            )
        }
    }

    private fun closePanel() {
        windows.remove(KEY_PANEL)
        AssistantHub.update { it.copy(panelOpen = false) }
        syncCaption()
        syncNumberTags()
    }

    private fun scheduleNumberTagsRefresh(delayMs: Long = 300L) {
        tagsRefreshJob?.cancel()
        tagsRefreshJob = lifecycleScope.launch {
            if (delayMs > 0) delay(delayMs)
            val snap = OmniAccessibilityService.snapshot() ?: return@launch
            val pkg = snap.state.packageName
            val isLauncher = pkg.contains("launcher") || pkg.endsWith(".home") || pkg == "com.miui.home"
            val items = if (isLauncher) emptyList() else ScreenQueries.contentItems(snap.state)
            val tags = items.take(10).mapIndexed { idx, el ->
                val badgeX = (el.bounds.left + 16).coerceIn(8, (snap.state.screenWidth - 72).coerceAtLeast(8))
                val badgeY = maxOf(el.bounds.top + 16, 24).coerceIn(8, (snap.state.screenHeight - 72).coerceAtLeast(8))
                NumberTag(
                    number = idx + 1,
                    elementId = el.id,
                    x = badgeX,
                    y = badgeY,
                    label = el.primaryLabel
                )
            }
            AssistantHub.update { it.copy(numberTags = tags) }
            withContext(Dispatchers.Main) {
                syncNumberTags()
            }
        }
    }

    private fun syncNumberTags() {
        val s = AssistantHub.state.value
        val wanted = (s.sessionActive || s.busy) && !s.panelOpen && s.numberTags.isNotEmpty() && Settings.canDrawOverlays(this)
        if (wanted) {
            if (!windows.has(KEY_TAGS)) {
                val params = windows.params(
                    MATCH_PARENT, MATCH_PARENT,
                    touchable = false,
                    focusable = false,
                    layoutInScreen = true
                )
                windows.show(KEY_TAGS, params) {
                    val st by AssistantHub.state.collectAsState()
                    NumberTagsOverlay(st.numberTags)
                }
            }
        } else {
            if (windows.has(KEY_TAGS)) windows.remove(KEY_TAGS)
        }
    }

    // ============================================================================ touch feedback

    private fun ballSize() = (72 * density).toInt()

    private fun ensureBall() {
        if (windows.has(KEY_BALL)) return
        val size = ballSize()
        val params = windows.params(size, size, touchable = false, layoutInScreen = true).apply { x = -size; y = -size }
        windows.show(KEY_BALL, params) {
            val b by ball.collectAsState()
            TouchBall(b, (72).toDp())
        }
    }

    private fun Int.toDp() = androidx.compose.ui.unit.Dp(this.toFloat())

    private suspend fun moveBall(x: Int, y: Int, durationMs: Long) {
        val p = windows.paramsOf(KEY_BALL) ?: return
        val half = ballSize() / 2
        val tx = x - half; val ty = y - half
        if (p.x < -half || abs(p.x - tx) + abs(p.y - ty) < 4) {
            p.x = tx; p.y = ty; windows.update(KEY_BALL); return
        }
        val sx = p.x; val sy = p.y
        suspendCancellableCoroutine { cont ->
            val anim = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = durationMs
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    val f = it.animatedValue as Float
                    p.x = (sx + (tx - sx) * f).toInt(); p.y = (sy + (ty - sy) * f).toInt()
                    windows.update(KEY_BALL)
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) { if (cont.isActive) cont.resume(Unit) }
                })
            }
            anim.start()
            cont.invokeOnCancellation { main.post { anim.cancel() } }
        }
    }

    private fun scheduleBallHide() {
        ballHideJob?.cancel()
        ballHideJob = lifecycleScope.launch {
            delay(1600)
            windows.remove(KEY_BALL)
        }
    }

    override suspend fun showTouch(x: Int, y: Int, longPress: Boolean) {
        if (!settings.showTouchBall) return
        withContext(Dispatchers.Main) {
            ballHideJob?.cancel()
            ensureBall()
            moveBall(x, y, 260)
            ball.value = BallState(ball.value.tick + 1, pressing = true, longPress = longPress)
        }
        delay(120)
        withContext(Dispatchers.Main) { scheduleBallHide() }
    }

    override suspend fun showSwipe(fromX: Int, fromY: Int, toX: Int, toY: Int) {
        if (!settings.showTouchBall) return
        withContext(Dispatchers.Main) {
            ballHideJob?.cancel()
            ensureBall()
            moveBall(fromX, fromY, 200)
            ball.value = BallState(ball.value.tick + 1, pressing = true)
            moveBall(toX, toY, 300)
            scheduleBallHide()
        }
    }

    override fun setPassthrough(enabled: Boolean) {
        val apply = {
            windows.setTouchable(KEY_ORB, !enabled)
            windows.setTouchable(KEY_PANEL, !enabled)
        }
        if (Looper.myLooper() == Looper.getMainLooper()) apply() else main.post(apply)
    }

    override suspend fun <T> withOverlaysHidden(block: suspend () -> T): T {
        val keys = withContext(Dispatchers.Main) { windows.keys().also { ks -> ks.forEach { windows.setAlpha(it, 0f) } } }
        delay(90)
        try {
            return block()
        } finally {
            withContext(Dispatchers.Main) { keys.forEach { windows.setAlpha(it, 1f) } }
        }
    }

    // ============================================================================ notification

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "OmniControl assistant", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows while the floating assistant is running"
                setShowBadge(false)
            }
        )
    }

    private fun buildNotification(): Notification {
        val s = AssistantHub.state.value
        fun pi(code: Int, action: String) = PendingIntent.getService(
            this, code, Intent(this, AssistantService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 10, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val text = when {
            s.sessionActive && s.status.isNotBlank() -> s.status
            s.sessionActive -> "Listening"
            else -> "Tap the orb to talk"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(if (s.sessionActive) "OmniControl is listening" else "OmniControl is ready")
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(open)
            .addAction(0, if (s.sessionActive) "Stop listening" else "Listen", pi(1, ACTION_TOGGLE_LISTEN))
            .addAction(0, "Chat", pi(2, ACTION_OPEN_PANEL))
            .addAction(0, "Quit", pi(3, ACTION_QUIT))
            .build()
    }

    private fun updateNotification() {
        runCatching { getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification()) }
    }

    /** @return false if Android refused (microphone from background). */
    private fun setForeground(mic: Boolean): Boolean {
        val special = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        val type = if (mic) special or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else special
        return try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
            true
        } catch (e: Exception) {
            OmniLog.w(TAG, "startForeground(mic=$mic) refused", e)
            if (mic) runCatching { ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), special) }
            false
        }
    }

    companion object {
        private const val TAG = "OmniService"
        private const val CHANNEL_ID = "omnicontrol_assistant"
        private const val NOTIFICATION_ID = 2001
        private const val INACTIVITY_MS = 4 * 60_000L

        const val ACTION_SHOW = "com.omnicontrol.SHOW"
        const val ACTION_TOGGLE_LISTEN = "com.omnicontrol.TOGGLE_LISTEN"
        const val ACTION_START_LISTENING = "com.omnicontrol.START_LISTENING"
        const val ACTION_START_LISTENING_FG = "com.omnicontrol.START_LISTENING_FG"
        const val ACTION_STOP = "com.omnicontrol.STOP"
        const val ACTION_OPEN_PANEL = "com.omnicontrol.OPEN_PANEL"
        const val ACTION_COMMAND = "com.omnicontrol.COMMAND"
        const val ACTION_QUIT = "com.omnicontrol.QUIT"
        const val EXTRA_TEXT = "text"

        private const val KEY_ORB = "orb"
        private const val KEY_CAPTION = "caption"
        private const val KEY_PANEL = "panel"
        private const val KEY_BALL = "ball"
        private const val KEY_TAGS = "tags"

        fun start(context: Context, action: String = ACTION_SHOW, text: String? = null) {
            val i = Intent(context, AssistantService::class.java).setAction(action)
            if (text != null) i.putExtra(EXTRA_TEXT, text)
            ContextCompat.startForegroundService(context, i)
        }

        fun send(context: Context, action: String) {
            context.startService(Intent(context, AssistantService::class.java).setAction(action))
        }
    }
}
