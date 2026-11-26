package com.omnicontrol.feature.voice

import com.omnicontrol.core.common.TextMatch
import com.omnicontrol.core.common.safety.ContentFilter

/**
 * Decides whether what the recogniser heard is safe and clear enough to act on.
 *
 * Rules:
 *  - If the best guess contains an explicit/offensive word, it's treated as a mishearing and NOT run.
 *    Exception: another guess is clean and contains a number ("six video" vs "sex video") - use that.
 *  - If the recogniser reports low confidence, ask the user to repeat instead of guessing.
 *  - Very short noise ("uh", single letters) is ignored.
 */
object VoiceGate {

    sealed interface Result {
        data class Accept(val text: String) : Result
        data class Repeat(val message: String) : Result
        data object Ignore : Result
    }

    const val REPEAT_MESSAGE = "I couldn't understand that command clearly. Please repeat it."
    private const val LOW_CONFIDENCE = 0.35f
    private val NOISE = Regex("^(uh+|um+|hm+|ah+|oh+|eh+|mm+|[a-z])$", RegexOption.IGNORE_CASE)

    fun evaluate(alternatives: List<String>, confidence: FloatArray?, vocabulary: Collection<String>): Result {
        val cleaned = alternatives.map { SpeechFixer.clean(it) }.filter { it.isNotBlank() }
        if (cleaned.isEmpty()) return Result.Ignore
        val best = cleaned.first()
        if (NOISE.matches(best.trim())) return Result.Ignore

        if (ContentFilter.containsExplicit(best)) {
            val numbered = cleaned.drop(1).firstOrNull { !ContentFilter.containsExplicit(it) && TextMatch.ordinal(it) != null }
            return if (numbered != null) Result.Accept(numbered) else Result.Repeat(REPEAT_MESSAGE)
        }

        // Android only sometimes reports confidence; 0 means "unknown" on many devices.
        val top = confidence?.firstOrNull() ?: -1f
        if (top > 0f && top < LOW_CONFIDENCE) return Result.Repeat(REPEAT_MESSAGE)

        val clean = alternatives.filter { !ContentFilter.containsExplicit(it) }
        return Result.Accept(SpeechFixer.pickBest(clean.ifEmpty { listOf(best) }, vocabulary))
    }
}
