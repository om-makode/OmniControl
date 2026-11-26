package com.omnicontrol.feature.voice

/**
 * Spoken editing while dictating a command.
 *
 * Saying "cut" removes the word before it, so each "cut" deletes one more word:
 *   "open YouTube and search for cut cut go to my profile" -> "open YouTube and go to my profile"
 *
 * If a phrase ends with "cut" the user is still correcting, so the text is kept as a draft
 * and the next phrase continues it (see [endsWithCut]).
 */
object VoiceEdit {

    data class Result(val text: String, val endsWithCut: Boolean, val hadCut: Boolean)

    private val CUT = Regex("^cut[.,!?]*$", RegexOption.IGNORE_CASE)
    // Recognisers sometimes glue repeats together: "cut-cut", "cut,cut".
    private val GLUED_CUTS = Regex("^cut(?:[-,.]+cut)+[.,!?]*$", RegexOption.IGNORE_CASE)

    fun apply(text: String): Result {
        val words = ArrayList<String>()
        var endsWithCut = false
        var hadCut = false
        for (token in tokens(text)) {
            if (CUT.matches(token)) {
                if (words.isNotEmpty()) words.removeAt(words.lastIndex)
                endsWithCut = true
                hadCut = true
            } else {
                words.add(token)
                endsWithCut = false
            }
        }
        return Result(words.joinToString(" "), endsWithCut, hadCut)
    }

    /** Joins a held draft with the newly heard phrase. */
    fun join(draft: String, heard: String): String =
        if (draft.isBlank()) heard.trim() else "${draft.trim()} ${heard.trim()}".trim()

    private fun tokens(text: String): List<String> =
        text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.flatMap { t ->
            if (GLUED_CUTS.matches(t)) t.split(Regex("[-,.!?]+")).filter { it.isNotEmpty() } else listOf(t)
        }
}
