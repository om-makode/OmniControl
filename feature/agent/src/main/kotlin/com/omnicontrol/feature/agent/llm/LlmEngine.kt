package com.omnicontrol.feature.agent.llm

import android.content.Context
import com.omnicontrol.core.common.OmniLog
import com.omnicontrol.native_llama.LlamaNative
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

interface LlmEngine {
    val modelName: String
    val isReady: Boolean
    suspend fun generate(prompt: String, grammar: String? = null, maxTokens: Int = 256): Result<String>
    fun release()
}

class LlamaCppEngine(
    private val modelFile: File,
    private val nCtx: Int = 2048,
    private val nThreads: Int = 4
) : LlmEngine {

    private var nativeHandle: Long = 0L
    private val ready = AtomicBoolean(false)

    override val modelName: String get() = modelFile.name
    override val isReady: Boolean get() = ready.get() && nativeHandle != 0L

    suspend fun load(): Boolean = withContext(LlamaNative.llamaDispatcher) {
        if (!modelFile.exists()) {
            OmniLog.w(TAG, "Model file not found: ${modelFile.absolutePath}")
            return@withContext false
        }
        OmniLog.i(TAG, "Loading GGUF model: ${modelFile.name} (ctx: $nCtx, threads: $nThreads)")
        nativeHandle = LlamaNative.loadModel(modelFile.absolutePath, nCtx, nThreads)
        val loaded = nativeHandle != 0L
        ready.set(loaded)
        if (loaded) {
            OmniLog.i(TAG, "GGUF model loaded successfully (handle: $nativeHandle)")
        } else {
            OmniLog.e(TAG, "Failed to load GGUF model")
        }
        loaded
    }

    override suspend fun generate(prompt: String, grammar: String?, maxTokens: Int): Result<String> = withContext(LlamaNative.llamaDispatcher) {
        val handle = nativeHandle
        if (handle == 0L || !ready.get()) {
            return@withContext Result.failure(IllegalStateException("Model not loaded"))
        }
        try {
            val output = LlamaNative.generate(handle, prompt, grammar, maxTokens)
            Result.success(output)
        } catch (e: Exception) {
            OmniLog.e(TAG, "Error generating LLM output", e)
            Result.failure(e)
        }
    }

    override fun release() {
        val handle = nativeHandle
        if (handle != 0L) {
            nativeHandle = 0L
            ready.set(false)
            LlamaNative.free(handle)
            OmniLog.i(TAG, "Released GGUF model handle")
        }
    }

    companion object {
        private const val TAG = "LlamaCppEngine"

        fun findModelFile(context: Context): File? {
            val llmDir = File(context.filesDir, "models/llm")
            if (!llmDir.exists()) return null
            return llmDir.listFiles { f -> f.extension.equals("gguf", ignoreCase = true) }?.firstOrNull()
        }
    }
}

class FallbackLlmEngine : LlmEngine {
    override val modelName: String = "RuleBasedFallbackEngine"
    override val isReady: Boolean = true

    override suspend fun generate(prompt: String, grammar: String?, maxTokens: Int): Result<String> {
        val planJson = planFromPrompt(prompt)
        return Result.success(planJson)
    }

    override fun release() {}

    companion object {
        private const val TAG = "FallbackLlmEngine"

        private data class ParsedUiElement(
            val id: Int,
            val role: String,
            val label: String,
            val isScrollable: Boolean
        )

        fun planFromPrompt(prompt: String): String {
            try {
                // 1. Extract goal from <|user|>
                val goal = extractGoal(prompt)
                val cleanGoal = goal.lowercase().trim()

                // 2. Extract observation elements
                val elements = extractElements(prompt)

                // 3. Extract history
                val history = extractHistory(prompt)

                // 4. If previous assistant action was clicking the target, mark task as done
                val lastAssistantAction = history.lastOrNull { it.startsWith("assistant:", ignoreCase = true) }
                if (lastAssistantAction != null &&
                    (lastAssistantAction.contains("click", ignoreCase = true) || lastAssistantAction.contains("select", ignoreCase = true))) {
                    return """{"thought":"Target item clicked, concluding task","action":{"type":"done","summary":"Opened target item"},"say":"Task completed."}"""
                }

                // 5. Handle explicit navigation & scroll commands
                if (cleanGoal.contains("scroll down") || cleanGoal.contains("swipe down")) {
                    return """{"thought":"Scrolling down as requested","action":{"type":"scroll","direction":"down"},"say":"Scrolling down"}"""
                }
                if (cleanGoal.contains("scroll up") || cleanGoal.contains("swipe up")) {
                    return """{"thought":"Scrolling up as requested","action":{"type":"scroll","direction":"up"},"say":"Scrolling up"}"""
                }
                if (cleanGoal == "back" || cleanGoal.contains("go back")) {
                    return """{"thought":"Navigating back","action":{"type":"back"},"say":"Going back"}"""
                }
                if (cleanGoal == "home" || cleanGoal.contains("go home")) {
                    return """{"thought":"Navigating to home screen","action":{"type":"home"},"say":"Going home"}"""
                }

                // 6. Handle search commands: e.g. "search for X"
                val searchMatch = Regex("^(?:search for|search|find)\\s+(.+)$", RegexOption.IGNORE_CASE).find(cleanGoal)
                if (searchMatch != null) {
                    val searchTopic = searchMatch.groupValues[1].trim()
                    val editField = elements.find { it.role.equals("edittext", ignoreCase = true) || it.role.equals("textfield", ignoreCase = true) }
                    if (editField != null) {
                        return """{"thought":"Typing search query into search bar","action":{"type":"type_text","target":${editField.id},"text":"${escapeJson(searchTopic)}","submit":true},"say":"Searching for ${escapeJson(searchTopic)}"}"""
                    }
                    val searchButton = elements.find { it.label.contains("search", ignoreCase = true) }
                    if (searchButton != null) {
                        return """{"thought":"Tapping search button","action":{"type":"click","target":${searchButton.id}},"say":"Opening search"}"""
                    }
                }

                // 7. Handle Ordinal selection: e.g. "open first video", "play second video", "click the third one", "first video open first video"
                val isOrdinal = cleanGoal.contains("first") || cleanGoal.contains("1st") ||
                    cleanGoal.contains("second") || cleanGoal.contains("2nd") ||
                    cleanGoal.contains("third") || cleanGoal.contains("3rd") ||
                    cleanGoal.contains("fourth") || cleanGoal.contains("4th") ||
                    cleanGoal.contains("last") || cleanGoal.contains("top")

                if (isOrdinal) {
                    val targetIndex = when {
                        cleanGoal.contains("first") || cleanGoal.contains("1st") || cleanGoal.contains("top") -> 0
                        cleanGoal.contains("second") || cleanGoal.contains("2nd") -> 1
                        cleanGoal.contains("third") || cleanGoal.contains("3rd") -> 2
                        cleanGoal.contains("fourth") || cleanGoal.contains("4th") -> 3
                        else -> 0
                    }
                    val isLast = cleanGoal.contains("last")

                    val nonContentKeywords = setOf(
                        "home", "subs", "subscriptions", "music", "live", "more", "search",
                        "notifications", "create", "you", "explore", "trending", "settings",
                        "menu", "cast", "back", "navigate up", "voice search", "close", "dismiss"
                    )

                    val contentCandidates = elements.filter { el ->
                        val lbl = el.label.lowercase().trim()
                        val meaningful = lbl.length >= 4 && !nonContentKeywords.contains(lbl)
                        val isCardRole = el.role in listOf("view", "container", "clickable", "button", "text")
                        meaningful && isCardRole
                    }

                    val selectedElement = if (isLast) {
                        contentCandidates.lastOrNull()
                    } else {
                        contentCandidates.getOrNull(targetIndex) ?: contentCandidates.firstOrNull()
                    }

                    if (selectedElement != null) {
                        val safeLabel = escapeJson(selectedElement.label.take(60))
                        val speechLabel = escapeJson(selectedElement.label.take(40))
                        val ordinalName = when (targetIndex) {
                            0 -> "first"
                            1 -> "second"
                            2 -> "third"
                            else -> "selected"
                        }
                        return """{"thought":"Opening $ordinalName content item [${selectedElement.id}] \"$safeLabel\"","action":{"type":"click","target":${selectedElement.id}},"say":"Opening $ordinalName video"}"""
                    }
                }

                // 8. Keyword matching for content selection / clicking
                val stopWords = setOf(
                    "open", "play", "watch", "click", "tap", "show", "me", "the", "a", "an",
                    "having", "with", "of", "in", "by", "from", "on", "to", "please", "video",
                    "post", "link", "chat", "button", "item", "view", "page", "screen", "go",
                    "for", "and", "about", "called", "named", "titled", "find", "can", "you",
                    "i", "want", "just", "see", "at"
                )
                val cleanTokens = cleanGoal.replace(Regex("[^a-z0-9\\s]"), " ")
                    .split(Regex("\\s+"))
                    .filter { it.isNotBlank() && it !in stopWords }

                // Score elements
                var bestElement: ParsedUiElement? = null
                var bestScore = 0

                val fullPhrase = cleanTokens.joinToString(" ")

                for (el in elements) {
                    val labelLower = el.label.lowercase().trim()
                    if (labelLower.isBlank()) continue

                    var score = 0

                    // Full phrase match
                    if (fullPhrase.isNotBlank() && labelLower.contains(fullPhrase)) {
                        score += 80
                    }

                    // Token match
                    val matchedTokens = cleanTokens.count { token -> labelLower.contains(token) }
                    if (matchedTokens > 0) {
                        score += matchedTokens * 25
                        if (cleanTokens.isNotEmpty() && matchedTokens == cleanTokens.size) {
                            score += 40
                        }
                    }

                    // Role bonus
                    if (el.role in listOf("button", "imagebutton", "clickable", "container", "view", "text")) {
                        score += 10
                    }

                    if (score > bestScore) {
                        bestScore = score
                        bestElement = el
                    }
                }

                if (bestElement != null && bestScore >= 25) {
                    val safeLabel = escapeJson(bestElement.label.take(60))
                    val speechLabel = escapeJson(bestElement.label.take(60))
                    return """{"thought":"Found matching element [${bestElement.id}] \"$safeLabel\"","action":{"type":"click","target":${bestElement.id}},"say":"Opening $speechLabel"}"""
                }

                // 8. If target item not found on current screen, scroll down
                val scrollCount = history.count { it.contains("scroll", ignoreCase = true) }
                if (scrollCount < 4) {
                    val targetName = if (cleanTokens.isNotEmpty()) cleanTokens.joinToString(" ") else "item"
                    return """{"thought":"Target '$targetName' not visible on current screen, scrolling down to locate it","action":{"type":"scroll","direction":"down"},"say":"Looking down for $targetName"}"""
                }

                // 9. If scrolled multiple times and still not found, ask user
                val queryDesc = if (cleanTokens.isNotEmpty()) cleanTokens.joinToString(" ") else cleanGoal
                return """{"thought":"Target not found after multiple scrolls","action":{"type":"ask_user","question":"I couldn't find '${escapeJson(queryDesc)}' on the screen. Would you like me to search for it?"},"say":"I could not find that on screen."}"""

            } catch (e: Exception) {
                OmniLog.e(TAG, "Error in rule-based fallback planning", e)
                return """{"thought":"Executing default fallback plan","action":{"type":"done","summary":"Plan executed"},"say":"Ready."}"""
            }
        }

        private fun extractGoal(prompt: String): String {
            val userIdx = prompt.indexOf("<|user|>")
            if (userIdx == -1) return ""
            val userBlock = prompt.substring(userIdx)
            val match = Regex("(?m)^Goal:\\s*(.+)$").find(userBlock)
            return match?.groupValues?.get(1)?.trim() ?: ""
        }

        private fun extractElements(prompt: String): List<ParsedUiElement> {
            val startIdx = prompt.indexOf("<|observation|>")
            val endIdx = prompt.indexOf("<|end|>", if (startIdx != -1) startIdx else 0)
            if (startIdx == -1 || endIdx == -1 || endIdx <= startIdx) return emptyList()

            val obsBlock = prompt.substring(startIdx, endIdx)
            val regex = Regex("(?m)^\\[(\\d+)\\]\\s+(\\w+)\\s+\"([^\"]*)\"(.*)$")
            return regex.findAll(obsBlock).map { m ->
                val id = m.groupValues[1].toIntOrNull() ?: 0
                val role = m.groupValues[2].lowercase()
                val label = m.groupValues[3]
                val extra = m.groupValues[4]
                val isScrollable = extra.contains("(scrollable)")
                ParsedUiElement(id, role, label, isScrollable)
            }.toList()
        }

        private fun extractHistory(prompt: String): List<String> {
            val startIdx = prompt.indexOf("<|history|>")
            val endIdx = prompt.indexOf("<|end|>", if (startIdx != -1) startIdx else 0)
            if (startIdx == -1 || endIdx == -1 || endIdx <= startIdx) return emptyList()

            return prompt.substring(startIdx, endIdx)
                .lines()
                .map { it.trim() }
                .filter { it.isNotBlank() && (it.startsWith("user:") || it.startsWith("assistant:")) }
        }

        private fun escapeJson(str: String): String {
            return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", "")
        }
    }
}
