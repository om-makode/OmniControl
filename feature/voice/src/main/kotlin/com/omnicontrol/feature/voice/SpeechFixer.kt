package com.omnicontrol.feature.voice

import com.omnicontrol.core.common.TextMatch

/**
 * Cleans up what the recogniser heard:
 *  - fixes common mis-hearings of app names ("you tube", "chat gpt", "what's app"),
 *  - drops filler ("hey omni", "please", "can you"),
 *  - picks the best of the recogniser's alternatives using the words we expect right now
 *    (installed app names and labels on the current screen).
 */
object SpeechFixer {

    private val REPLACEMENTS: List<Pair<Regex, String>> = listOf(
        "\\byou ?tube\\b" to "YouTube",
        "\\b(chat|chad|chaat|jet|chart) ?(gpt|gbt|jpt|gp t|g p t|bt|pt)\\b" to "ChatGPT",
        "\\bchat ?gpt\\b" to "ChatGPT",
        "\\b(what'?s ?app|whats ?up|what's ?up)\\b" to "WhatsApp",
        "\\binsta ?gram\\b" to "Instagram",
        "\\bface ?book\\b" to "Facebook",
        "\\bg ?mail\\b" to "Gmail",
        "\\bplay ?store\\b" to "Play Store",
        "\\bspot ?ify\\b" to "Spotify",
        "\\bwi[ -]?fi\\b" to "Wi-Fi",
        "\\bblue ?tooth\\b" to "Bluetooth",
        "\\bflip ?kart\\b" to "Flipkart",
        "\\blinked ?in\\b" to "LinkedIn",
        "\\btele ?gram\\b" to "Telegram",
        "\\bnet ?flix\\b" to "Netflix",
        "\\bscroll (town|done|don)\\b" to "scroll down",
        "\\bscrolling down\\b" to "scroll down",
        "\\bscrolling up\\b" to "scroll up",
        "\\bgo bag\\b" to "go back",
        "\\bopen up\\b" to "open",
        // "six" misheard as harmless words. (A misheard explicit word is never auto-corrected:
        // the voice gate asks the user to repeat instead, see VoiceGate.)
        "\\b(secs|sechs) (video|result|item|one|short|reel|song)\\b" to "6th $2",
        "\\b(open|play|tap|click|watch|choose|select) (secs|sechs)\\b" to "$1 6"
    ).map { Regex(it.first, RegexOption.IGNORE_CASE) to it.second }

    private val FILLER_PREFIX = Regex(
        "^(?:(?:hey|hi|ok|okay)\\b[,!.]?\\s*)?(?:(?:omni|omnicontrol|assistant)\\b[,!.]?\\s*)?" +
            "(?:(?:can you|could you|would you|will you|please|kindly|i want you to|i want to|i'd like to|just)\\b\\s*)?(?:please\\b\\s*)?",
        RegexOption.IGNORE_CASE
    )

    fun clean(text: String): String {
        var t = text.trim()
        for ((re, rep) in REPLACEMENTS) t = re.replace(t, rep)
        t = FILLER_PREFIX.replaceFirst(t, "")
        t = t.replace(Regex("\\s+please[.!?]*$", RegexOption.IGNORE_CASE), "")
        t = t.trim().trimEnd('.', '!', '?', ',')
        return t.ifBlank { text.trim() }
    }

    private val COMMAND_START = Regex(
        "^(open|launch|start|go|search|find|play|tap|click|press|scroll|swipe|type|write|send|turn|switch|show|stop|done|back|home|select|call|message|enable|disable|watch|read|what|how|close|more|down|up|again|next)\\b",
        RegexOption.IGNORE_CASE
    )

    /**
     * Choose the most likely alternative. The recogniser's first guess wins unless another guess
     * clearly matches an app name or an on-screen label much better.
     */
    fun pickBest(alternatives: List<String>, vocabulary: Collection<String>): String {
        if (alternatives.isEmpty()) return ""
        if (alternatives.size == 1) return clean(alternatives[0])
        val vocab = vocabulary.map { TextMatch.normalize(it) }.filter { it.length >= 3 }.toSet()
        val scored = alternatives.mapIndexed { i, raw ->
            val c = clean(raw)
            val n = TextMatch.normalize(c)
            var s = (alternatives.size - i) * 0.6          // recogniser order matters most
            if (COMMAND_START.containsMatchIn(c)) s += 1.0
            s += vocab.count { v -> n.contains(v) } * 1.2  // mentions something we know exists
            c to s
        }
        return scored.maxByOrNull { it.second }!!.first
    }
}
