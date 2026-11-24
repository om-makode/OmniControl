package com.omnicontrol.feature.agent.loop

import com.omnicontrol.core.common.OmniLog
import com.omnicontrol.core.model.ActionCardDetail
import com.omnicontrol.core.model.AssistantState
import com.omnicontrol.core.model.ChatItem
import com.omnicontrol.core.model.ChatRole
import com.omnicontrol.feature.accessibility.OmniAccessibilityService
import com.omnicontrol.feature.agent.llm.ActionGrammar
import com.omnicontrol.feature.agent.llm.LlmEngine
import com.omnicontrol.feature.agent.memory.ConversationManager
import com.omnicontrol.feature.agent.prompt.PromptBuilder
import com.omnicontrol.feature.agent.safety.RiskLevel
import com.omnicontrol.feature.agent.safety.SafetyGuard
import com.omnicontrol.feature.navigator.clipboard.SessionClipboard
import com.omnicontrol.feature.navigator.popup.PopupHandler
import com.omnicontrol.feature.vision.ocr.OcrEngine
import com.omnicontrol.feature.vision.resolver.VisualResolver
import com.omnicontrol.feature.vision.resolver.VisualResult
import com.omnicontrol.feature.vision.screenshot.ScreenshotManager
import com.omnicontrol.feature.vision.som.SetOfMarksRenderer
import com.omnicontrol.feature.voice.router.FastRouteResult
import com.omnicontrol.feature.voice.router.FastRouter
import com.omnicontrol.feature.voice.tts.TtsEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

class AgentLoop(
    private val llmEngine: LlmEngine,
    private val conversationManager: ConversationManager,
    private val listener: AgentEventListener? = null,
    private val tts: TtsEngine? = null
) {
    private var activeJob: Job? = null
    var isRunning: Boolean = false
        private set

    fun startTask(goal: String, scope: CoroutineScope): Job {
        cancel()
        val job = scope.launch {
            runLoop(goal)
        }
        activeJob = job
        return job
    }

    private suspend fun runLoop(goal: String) {
        isRunning = true
        OmniLog.i(TAG, "Starting ReAct agent loop for goal: '$goal'")
        conversationManager.startNewSession(goal)
        conversationManager.recordTurn("user", goal)

        val fastResult = FastRouter.route(goal)
        if (fastResult is FastRouteResult.Action) {
            val action = fastResult.action
            val actionType = action.type.lowercase().trim()
            fastResult.spokenReply?.let { tts?.speak(it) }
            listener?.onStateChanged(AssistantState.Executing(fastResult.spokenReply ?: "Executing $actionType", 1, 1))
            val actionResult = OmniAccessibilityService.executeAction(action)
            if (actionResult != null) {
                listener?.onMessage(
                    ChatItem(
                        id = UUID.randomUUID().toString(),
                        role = ChatRole.ACTION,
                        content = actionResult.reason,
                        actionDetail = ActionCardDetail(
                            actionType = actionType,
                            targetDescription = action.direction ?: action.app ?: action.url ?: "",
                            isSuccess = actionResult.success,
                            durationMs = actionResult.durationMs
                        )
                    )
                )
                conversationManager.recordTurn("assistant", "$actionType -> ${actionResult.reason}")
            }
            listener?.onStateChanged(AssistantState.Waiting)
            conversationManager.endSession("COMPLETED")
            isRunning = false
            return
        }

        val maxSteps = 10
        var currentStep = 1

        try {
            while (currentStep <= maxSteps && isRunning) {
                // 1. OBSERVE
                listener?.onStateChanged(AssistantState.Observing)
                delay(100)

                var obs = OmniAccessibilityService.currentObservation()
                if (obs == null) {
                    OmniLog.w(TAG, "No accessibility observation available, waiting 1s...")
                    delay(1000)
                    currentStep++
                    continue
                }

                // If observation has few text elements, enrich with OCR
                if (obs.elements.count { !it.text.isNullOrBlank() } < 5) {
                    val bitmap = ScreenshotManager.capture(obs.screenFingerprint)
                    if (bitmap != null) {
                        val ocrItems = OcrEngine.recognizeText(bitmap)
                        obs = OcrEngine.mergeWithObservation(obs, ocrItems)
                    }
                }

                // Check and auto-dismiss annoying promo/skip popups
                val popup = PopupHandler.checkPopup(obs)
                if (popup != null) {
                    OmniLog.i(TAG, "Auto-dismissing popup: '${popup.dismissText}'")
                    OmniAccessibilityService.executeAction(com.omnicontrol.core.model.AgentAction(type = "click", target = popup.element.id))
                    listener?.onMessage(
                        ChatItem(
                            id = UUID.randomUUID().toString(),
                            role = ChatRole.ACTION,
                            content = "Closed a popup: ${popup.dismissText}"
                        )
                    )
                    delay(300)
                    continue
                }

                conversationManager.recordApp(obs.packageName)
                listener?.onCurrentApp(obs.appLabel.ifEmpty { obs.packageName })

                // 2. THINK / PLAN
                listener?.onStateChanged(AssistantState.Processing)
                val prompt = PromptBuilder.buildPrompt(
                    goal = goal,
                    observation = obs,
                    historyTurns = conversationManager.turns
                )

                OmniLog.d(TAG, "Querying LLM with prompt (${prompt.length} chars)...")
                val llmOutputResult = llmEngine.generate(prompt, ActionGrammar.GBNF_GRAMMAR, maxTokens = 256)

                val plan = if (llmOutputResult.isSuccess) {
                    val rawJson = llmOutputResult.getOrThrow()
                    OmniLog.i(TAG, "LLM JSON: $rawJson")
                    ActionGrammar.parsePlan(rawJson).getOrNull()
                } else {
                    OmniLog.w(TAG, "LLM generation failed: ${llmOutputResult.exceptionOrNull()?.message}")
                    null
                }

                if (plan == null) {
                    OmniLog.w(TAG, "Failed to parse action plan from LLM, finishing loop")
                    listener?.onStateChanged(AssistantState.Error("Could not plan next action"))
                    tts?.speak("I could not determine the next step.")
                    break
                }

                // Speak voice update if provided
                plan.say?.let { speech ->
                    if (speech.isNotBlank()) {
                        tts?.speak(speech)
                    }
                }

                // 3. SAFETY CHECK
                val safety = SafetyGuard.evaluate(plan.action, obs)
                when (safety.level) {
                    RiskLevel.BLOCKED -> {
                        OmniLog.w(TAG, "SafetyGuard BLOCKED action: ${safety.reason}")
                        listener?.onStateChanged(AssistantState.Error(safety.reason))
                        tts?.speak("That action is not permitted.")
                        break
                    }
                    RiskLevel.HANDOFF -> {
                        OmniLog.i(TAG, "SafetyGuard HANDOFF: ${safety.reason}")
                        listener?.onStateChanged(AssistantState.Paused(safety.reason))
                        tts?.speak(safety.reason)
                        break
                    }
                    RiskLevel.CONFIRM -> {
                        OmniLog.i(TAG, "SafetyGuard CONFIRM required: ${safety.confirmQuestion}")
                        listener?.onStateChanged(AssistantState.Confirming(safety.confirmQuestion ?: "Confirm action?"))
                        tts?.speak(safety.confirmQuestion ?: "Confirm action?")
                        break
                    }
                    RiskLevel.SAFE -> {
                        // Safe to proceed
                    }
                }

                // 4. ACT
                var actionToExecute = plan.action
                if (actionToExecute.type == "click_visual" || (actionToExecute.type == "click" && actionToExecute.target == null && !actionToExecute.text.isNullOrBlank())) {
                    val query = actionToExecute.text ?: ""
                    when (val vRes = VisualResolver.resolve(query, obs)) {
                        is VisualResult.Found -> {
                            actionToExecute = actionToExecute.copy(type = "click", target = vRes.element.id)
                        }
                        is VisualResult.Ambiguous -> {
                            val count = vRes.candidates.size
                            val question = "I found $count possible matches for '$query'. Which one (1 to $count) would you like?"
                            listener?.onMessage(
                                ChatItem(
                                    id = UUID.randomUUID().toString(),
                                    role = ChatRole.ASSISTANT,
                                    content = question
                                )
                            )
                            tts?.speak(question)
                            listener?.onStateChanged(AssistantState.Listening)
                            break
                        }
                        is VisualResult.SecureScreen -> {
                            listener?.onStateChanged(AssistantState.Paused("Screen is secure/protected. Please proceed manually."))
                            tts?.speak("Screen is secure. Please proceed manually.")
                            break
                        }
                        is VisualResult.NotFound -> {
                            OmniLog.w(TAG, "Visual target not found: ${vRes.reason}")
                        }
                    }
                }

                if (actionToExecute.type == "copy_text") {
                    val extracted = if (!actionToExecute.text.isNullOrBlank()) {
                        actionToExecute.text!!
                    } else {
                        SessionClipboard.extractUrlFromObservation(obs) ?: ""
                    }
                    SessionClipboard.copyText(extracted)
                    val desc = if (extracted.isNotBlank()) "Copied: $extracted" else "Copied text to session clipboard"
                    listener?.onMessage(
                        ChatItem(
                            id = UUID.randomUUID().toString(),
                            role = ChatRole.ACTION,
                            content = desc,
                            actionDetail = ActionCardDetail(
                                actionType = "copy_text",
                                targetDescription = extracted,
                                isSuccess = true,
                                durationMs = 15
                            )
                        )
                    )
                    tts?.speak("Copied.")
                    currentStep++
                    delay(300)
                    continue
                }

                val actionType = actionToExecute.type.lowercase().trim()
                val stepDesc = plan.thought.ifEmpty { "Executing $actionType" }
                listener?.onStateChanged(AssistantState.Executing(stepDesc, currentStep, maxSteps))

                val actionResult = OmniAccessibilityService.executeAction(actionToExecute)

                if (actionResult != null) {
                    listener?.onMessage(
                        ChatItem(
                            id = UUID.randomUUID().toString(),
                            role = ChatRole.ACTION,
                            content = actionResult.reason,
                            actionDetail = ActionCardDetail(
                                actionType = actionType,
                                targetDescription = actionToExecute.app ?: actionToExecute.url ?: actionToExecute.direction ?: "#${actionToExecute.target}",
                                isSuccess = actionResult.success,
                                durationMs = actionResult.durationMs
                            )
                        )
                    )
                    conversationManager.recordTurn("assistant", "[Step $currentStep] $actionType -> ${actionResult.reason}")
                }

                // 5. TERMINAL ACTIONS
                if (actionType == "done") {
                    OmniLog.i(TAG, "Agent reached done: ${plan.action.summary}")
                    val summary = plan.action.summary ?: "Task completed."
                    listener?.onMessage(
                        ChatItem(
                            id = UUID.randomUUID().toString(),
                            role = ChatRole.ASSISTANT,
                            content = summary
                        )
                    )
                    tts?.speak(summary)
                    listener?.onStateChanged(AssistantState.Waiting)
                    break
                }

                if (actionType == "ask_user") {
                    OmniLog.i(TAG, "Agent asking user: ${plan.action.question}")
                    val question = plan.action.question ?: "Can you clarify?"
                    listener?.onMessage(
                        ChatItem(
                            id = UUID.randomUUID().toString(),
                            role = ChatRole.ASSISTANT,
                            content = question
                        )
                    )
                    tts?.speak(question)
                    listener?.onStateChanged(AssistantState.Listening)
                    break
                }

                currentStep++
                delay(300)
            }

            if (currentStep > maxSteps) {
                OmniLog.w(TAG, "ReAct loop reached maximum step budget ($maxSteps)")
                listener?.onStateChanged(AssistantState.Waiting)
                tts?.speak("Reached maximum step budget.")
            }
            conversationManager.endSession("COMPLETED")
        } catch (e: CancellationException) {
            OmniLog.i(TAG, "Agent loop was cancelled")
            conversationManager.endSession("CANCELLED")
        } catch (e: Exception) {
            OmniLog.e(TAG, "Unhandled exception in Agent loop", e)
            listener?.onStateChanged(AssistantState.Error(e.message ?: "Agent error"))
            conversationManager.endSession("ERROR")
        } finally {
            isRunning = false
        }
    }

    fun cancel() {
        isRunning = false
        activeJob?.cancel()
        activeJob = null
    }

    companion object {
        private const val TAG = "AgentLoop"
    }
}
