package com.omnicontrol.feature.agent

import android.content.Context
import com.omnicontrol.core.common.OmniLog
import com.omnicontrol.core.common.safety.PrivacyFilter
import com.omnicontrol.core.common.settings.SettingsState
import com.omnicontrol.core.model.ActionResult
import com.omnicontrol.core.model.AgentAction
import com.omnicontrol.core.model.ChatItem
import com.omnicontrol.core.model.ChatRole
import com.omnicontrol.core.model.ConfirmRequest
import com.omnicontrol.core.model.Phase
import com.omnicontrol.core.model.ScreenState
import com.omnicontrol.feature.accessibility.OmniAccessibilityService
import com.omnicontrol.feature.accessibility.ScreenSnapshot
import com.omnicontrol.feature.agent.brain.Brain
import com.omnicontrol.feature.agent.brain.BrainFactory
import com.omnicontrol.feature.agent.brain.BrainRequest
import com.omnicontrol.feature.agent.brain.DecisionKind
import com.omnicontrol.feature.agent.brain.ScreenshotEncoder
import com.omnicontrol.feature.agent.quick.QuickCommands
import com.omnicontrol.feature.agent.quick.QuickExecutor
import com.omnicontrol.feature.agent.safety.BrainPrivacy
import com.omnicontrol.feature.agent.safety.Risk
import com.omnicontrol.feature.agent.safety.SafetyPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the controller needs from the UI layer (overlay service). */
interface AgentUi {
    fun setPhase(phase: Phase, status: String = "")
    fun setStep(index: Int)
    fun setApp(label: String)
    fun post(item: ChatItem)
    suspend fun speak(text: String)
    /**
     * Shows the "Proposed action" card and suspends until the USER says/taps confirm or cancel.
     * Returns false on cancel or timeout. Only real user input can complete this.
     */
    suspend fun confirm(request: ConfirmRequest): Boolean
    /** A command finished (successfully or not). */
    fun taskFinished()
}

/** Stops the current task with a message for the user. */
private class StopTask(val userMessage: String) : Exception(userMessage)

/**
 * The assistant's mind:
 *  1. Simple clauses ("open YouTube", "search X", "play the second video", "tap Subscribe") run
 *     instantly through [QuickCommands] / [QuickExecutor].
 *  2. Anything that needs understanding goes to the AI [Brain] in an observe -> decide -> act loop
 *     that checks the screen after every touch.
 *  3. Session memory + notes let follow-up commands refer to earlier ones.
 */
class AgentController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val ui: AgentUi,
    private val settings: () -> SettingsState
) {
    private var job: Job? = null
    private var brain: Brain? = null
    private var brainKey: String = ""

    private val memory = ArrayList<String>()
    private val notes = ArrayList<String>()
    private var pendingGoal: String? = null
    private val queue = ArrayDeque<String>()
    /** Recent sends/calls/posts/purchases (key -> time) so they are never silently repeated. */
    private val recentSensitive = HashMap<String, Long>()

    private val quick = QuickExecutor(act = { a, s -> act(a, s) }, brainAvailable = { currentBrain() != null })

    val isBusy: Boolean get() = job?.isActive == true

    fun currentBrain(): Brain? {
        val s = settings()
        val key = listOf(s.brainProvider, s.effectiveUrl, s.effectiveModel, s.brainApiKey, s.brainVision).joinToString("|")
        if (key != brainKey) {
            brain?.close()
            brain = BrainFactory.create(context, s)
            brainKey = key
        }
        return brain
    }

    fun brainLabel(): String = currentBrain()?.name ?: "Quick commands"

    /** New command from voice or keyboard. */
    fun submit(command: String) {
        val text = command.trim()
        if (text.isEmpty()) return
        if (isBusy) {
            if (queue.size < 2) queue.addLast(text)
            ui.post(ChatItem(role = ChatRole.SYSTEM, content = "Next: $text"))
            return
        }
        start(text)
    }

    private fun start(text: String) {
        var cancelled = false
        job = scope.launch {
            try {
                handle(text)
            } catch (e: CancellationException) {
                cancelled = true
                ui.setPhase(Phase.IDLE, "Stopped")
                throw e
            } catch (e: StopTask) {
                finish(text, e.userMessage, ok = false)
            } catch (e: Exception) {
                OmniLog.e(TAG, "task failed", e)
                finish(text, "Something went wrong: ${e.message}", ok = false)
            } finally {
                val next = if (cancelled) null else queue.removeFirstOrNull()
                if (next != null) start(next) else ui.taskFinished()
            }
        }
    }

    fun cancel() {
        queue.clear()
        job?.cancel()
        job = null
    }

    fun resetSession() {
        cancel()
        memory.clear()
        notes.clear()
        pendingGoal = null
    }

    fun release() {
        cancel()
        brain?.close()
        brain = null
    }

    // ------------------------------------------------------------------------------------------

    private suspend fun handle(command: String) {
        if (!OmniAccessibilityService.isConnected) {
            throw StopTask("Screen control is off. Open OmniControl and turn on the accessibility service.")
        }
        ui.post(ChatItem(role = ChatRole.USER, content = command))
        SafetyPolicy.checkCommand(command).let { if (it.risk == Risk.BLOCK) throw StopTask(it.reason) }
        val brain = currentBrain()

        // Answer to a question the brain asked last time: continue that task.
        pendingGoal?.let { previous ->
            pendingGoal = null
            if (brain != null) {
                runBrain(brain, "$previous\nThe user answered: $command", mutableListOf())
                return
            }
        }

        val clauses = QuickCommands.parse(command)
        val steps = mutableListOf<String>()
        var index = 0
        var lastMessage = ""
        while (index < clauses.size) {
            val (_, step) = clauses[index]
            if (step == null) break
            ui.setPhase(Phase.ACTING, step.spoken)
            val out = quick.run(step)
            steps += "${steps.size + 1}. ${step.spoken} -> ${if (out.ok) "OK" else "FAILED"}: ${out.message}"
            lastMessage = out.message
            if (!out.ok) {
                if (out.needsBrain && brain != null) break
                throw StopTask(out.message)
            }
            index++
        }

        if (index >= clauses.size) {
            finish(command, friendly(lastMessage, clauses.size), ok = true)
            return
        }
        if (brain == null) {
            val rest = clauses.drop(index).joinToString(", then ") { it.first }
            throw StopTask(
                if (steps.isEmpty()) "I need an AI brain for \"$rest\". You can set one up for free in the OmniControl app."
                else "I did the first part, but \"$rest\" needs an AI brain. Set one up in the OmniControl app."
            )
        }
        runBrain(brain, command, steps)
    }

    private suspend fun runBrain(brain: Brain, goal: String, steps: MutableList<String>) {
        val max = settings().maxSteps
        var lastSig = ""
        var repeats = 0
        var unchanged = 0
        var invalid = 0
        for (n in 1..max) {
            currentCoroutineContext().ensureActive()
            ui.setStep(n)
            ui.setPhase(Phase.THINKING, "Looking at the screen")
            OmniAccessibilityService.waitForSettle(300, 1500)
            val snap = OmniAccessibilityService.snapshot() ?: throw StopTask("I can't read the screen.")
            val screen = snap.state
            ui.setApp(screen.appLabel)
            SafetyPolicy.screenHandBack(screen)?.let { throw StopTask(it) }

            val cloud = !brain.isPrivate
            val prepared = when (val d = BrainPrivacy.prepare(screen, cloud, settings().allowPersonalCloud, brain.seesImages, brain.name.substringBefore(" ·"))) {
                is BrainPrivacy.Decision.Refuse -> throw StopTask(d.reason)
                is BrainPrivacy.Decision.Allow -> d
            }
            val shot = if (prepared.screenshotAllowed) {
                OmniAccessibilityService.screenshot()?.let { bmp ->
                    withContext(Dispatchers.Default) { ScreenshotEncoder.encode(bmp, screen).also { bmp.recycle() } }
                }
            } else null

            val hint = when {
                repeats >= 2 -> "You are repeating the same action and it does not work. Try a different element, scroll, or go back."
                unchanged >= 2 -> "Your last actions did not change the screen. Choose something else."
                invalid > 0 -> "Your last reply used an element number that does not exist. Use only numbers from CURRENT SCREEN."
                else -> null
            }
            ui.setPhase(Phase.THINKING, "Thinking (step $n)")
            val decision = brain.decide(
                BrainRequest(
                    goal = BrainPrivacy.clean(goal, cloud),
                    sessionMemory = memory.map { BrainPrivacy.clean(it, cloud) },
                    notes = notes.map { BrainPrivacy.clean(it, cloud) },
                    steps = steps.map { BrainPrivacy.clean(it, cloud) },
                    screen = prepared.screen,
                    screenshotBase64 = shot?.base64,
                    screenshotScale = shot?.scale ?: 1f,
                    hint = hint
                )
            ).getOrElse { e -> throw StopTask("The AI brain had a problem: ${e.message}") }

            decision.note?.let { note ->
                if (!PrivacyFilter.isSecretLabel(note)) notes.add(note.take(600))
                if (notes.size > 12) notes.removeAt(0)
            }

            when (decision.kind) {
                DecisionKind.DONE -> { finish(goal, decision.message ?: "Done.", ok = true); return }
                DecisionKind.ANSWER -> {
                    val answer = decision.message ?: "I couldn't find an answer."
                    if (PrivacyFilter.containsPersonalInfo(answer)) {
                        // Show it, but don't read personal details out loud.
                        ui.post(ChatItem(role = ChatRole.ASSISTANT, content = answer, success = true))
                        finish(goal, "This has personal details, so I've shown it in the chat instead of reading it aloud.", ok = true)
                    } else finish(goal, answer, ok = true)
                    return
                }
                DecisionKind.ASK -> {
                    pendingGoal = goal + if (steps.isNotEmpty()) "\n(Already done: ${steps.takeLast(5).joinToString("; ")})" else ""
                    val q = decision.message ?: "What should I do next?"
                    ui.post(ChatItem(role = ChatRole.ASSISTANT, content = q))
                    ui.speak(q)
                    return
                }
                DecisionKind.ACTION -> Unit
            }

            val action = decision.action ?: continue
            val problem = validate(action, screen)
            if (problem != null) {
                invalid++
                steps += "$n. INVALID ${action.type}: $problem"
                if (invalid >= 3) throw StopTask("The AI keeps choosing things that aren't on screen. Try saying it differently.")
                continue
            }
            invalid = 0

            val sig = "${screen.fingerprint}|${action.type}|${action.target}|${action.text}|${action.direction}|${action.x}"
            repeats = if (sig == lastSig) repeats + 1 else 0
            lastSig = sig
            if (repeats >= 4) throw StopTask("I'm stuck on this screen. Tell me what to tap, or say it another way.")

            val res = act(action, snap)
            val changeNote = if (!res.screenChanged && action.type != "wait") " (screen did not change)" else ""
            steps += "$n. ${action.describe(screen)} -> ${if (res.success) "OK" else "FAILED"}: ${res.reason}$changeNote"
            unchanged = if (res.screenChanged) 0 else unchanged + 1
        }
        finish(goal, "I stopped after $max steps. Tell me what to do next.", ok = false)
    }

    private fun validate(a: AgentAction, screen: ScreenState): String? {
        val needsTarget = a.type in setOf("click", "long_press", "clear")
        return when {
            a.type == "tap" && (a.x == null || a.y == null) -> "tap_xy needs x and y"
            a.type == "tap" && (a.x!! !in 0..screen.screenWidth || a.y!! !in 0..screen.screenHeight) -> "x,y outside the screen"
            needsTarget && a.target == null && a.x == null -> "${a.type} needs a target number"
            a.target != null && screen.element(a.target) == null -> "element ${a.target} is not on the screen"
            a.type == "open_app" && (a.app ?: a.text).isNullOrBlank() -> "open_app needs an app name"
            a.type == "open_url" && (a.url ?: a.text).isNullOrBlank() -> "open_url needs a URL"
            a.type == "type" && a.text == null -> "type needs text"
            a.type !in KNOWN_TYPES -> "unknown action ${a.type}"
            else -> null
        }
    }

    /** Safety check + UI feedback + the actual touch. Used by quick commands and the brain. */
    private suspend fun act(action: AgentAction, snap: ScreenSnapshot?): ActionResult {
        currentCoroutineContext().ensureActive()
        var current = snap ?: OmniAccessibilityService.snapshot()
        val openPkg = if (action.type == "open_app") OmniAccessibilityService.launcher()?.find(action.app ?: action.text.orEmpty())?.packageName else null
        val check = SafetyPolicy.checkAction(action, current?.state, openPkg)
        when (check.risk) {
            Risk.BLOCK -> throw StopTask(check.reason)
            Risk.CONFIRM -> {
                var request = check.request!!
                check.repeatKey?.let { key ->
                    val last = recentSensitive[key]
                    val ago = last?.let { (System.currentTimeMillis() - it) / 1000 }
                    if (ago != null && ago < REPEAT_WINDOW_S) {
                        request = request.copy(warning = listOfNotNull("I already did exactly this ${ago}s ago. Doing it again will repeat it.", request.warning).joinToString(" "))
                    }
                }
                ui.setPhase(Phase.CONFIRM, request.title)
                if (!ui.confirm(request)) throw StopTask("Okay, cancelled. I didn't do it.")
                currentCoroutineContext().ensureActive()
                // The screen may have changed while waiting: re-check the same action on a fresh read.
                val fresh = OmniAccessibilityService.snapshot()
                if (fresh != null && fresh.state.fingerprint != current?.state?.fingerprint) {
                    val again = SafetyPolicy.checkAction(action, fresh.state, openPkg)
                    if (again.risk == Risk.BLOCK) throw StopTask(again.reason)
                    // Element ids belong to the old read; the performer re-finds the same element by its label.
                    if (action.target == null) current = fresh
                }
            }
            Risk.SAFE -> Unit
        }
        currentCoroutineContext().ensureActive()
        ui.setPhase(Phase.ACTING, action.describe(current?.state))
        val res = OmniAccessibilityService.perform(action, current)
        if (res.success) check.repeatKey?.let { recentSensitive[it] = System.currentTimeMillis() }
        ui.post(ChatItem(role = ChatRole.ACTION, content = res.reason, success = res.success))
        return res
    }

    private suspend fun finish(goal: String, message: String, ok: Boolean) {
        memory.add("\"${goal.lineSequence().first().take(80)}\" -> $message")
        if (memory.size > 10) memory.removeAt(0)
        ui.post(ChatItem(role = ChatRole.ASSISTANT, content = message, success = ok))
        ui.setPhase(if (ok) Phase.SPEAKING else Phase.ERROR, message)
        ui.speak(message)
    }

    private fun friendly(last: String, clauses: Int): String =
        if (clauses == 1 && last.isNotBlank()) last.replace(Regex("^Tapped \"(.{30}).*\"$"), "Tapped \"$1…\"") else "Done."

    companion object {
        private const val TAG = "OmniAgent"
        private const val REPEAT_WINDOW_S = 180L
        private val KNOWN_TYPES = setOf(
            "open_app", "open_url", "click", "tap", "long_press", "type", "clear", "enter", "scroll", "swipe",
            "back", "home", "recents", "notifications", "quick_settings", "wait"
        )
    }
}
