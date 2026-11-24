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

        val maxSteps = 10
        var currentStep = 1

        try {
            while (currentStep <= maxSteps && isRunning) {
                // 1. OBSERVE
                listener?.onStateChanged(AssistantState.Observing)
                delay(100)

                val obs = OmniAccessibilityService.currentObservation()
                if (obs == null) {
                    OmniLog.w(TAG, "No accessibility observation available, waiting 1s...")
                    delay(1000)
                    currentStep++
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
                val actionType = plan.action.type.lowercase().trim()
                val stepDesc = plan.thought.ifEmpty { "Executing $actionType" }
                listener?.onStateChanged(AssistantState.Executing(stepDesc, currentStep, maxSteps))

                val actionResult = OmniAccessibilityService.executeAction(plan.action)

                if (actionResult != null) {
                    listener?.onMessage(
                        ChatItem(
                            id = UUID.randomUUID().toString(),
                            role = ChatRole.ACTION,
                            content = actionResult.reason,
                            actionDetail = ActionCardDetail(
                                actionType = actionType,
                                targetDescription = plan.action.app ?: plan.action.url ?: plan.action.direction ?: "#${plan.action.target}",
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
