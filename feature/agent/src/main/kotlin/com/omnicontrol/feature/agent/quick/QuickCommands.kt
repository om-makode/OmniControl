package com.omnicontrol.feature.agent.quick

import com.omnicontrol.core.common.TextMatch

/** A step simple enough to do without AI. */
sealed interface QuickStep {
    val spoken: String
    data class OpenOrTap(val name: String, override val spoken: String) : QuickStep
    data class OpenUrl(val url: String, override val spoken: String) : QuickStep
    data class Search(val query: String, val app: String?, override val spoken: String) : QuickStep
    data class Nth(val n: Int, override val spoken: String) : QuickStep
    /** "play despacito (on youtube)" = search + open the first result. */
    data class Play(val query: String, val app: String?, override val spoken: String) : QuickStep
    data class TapLabel(val label: String, val long: Boolean, override val spoken: String) : QuickStep
    data class TypeText(val text: String, override val spoken: String) : QuickStep
    data class Enter(override val spoken: String) : QuickStep
    data class Scroll(val direction: String, val times: Int, override val spoken: String) : QuickStep
    data class Swipe(val direction: String, override val spoken: String) : QuickStep
    data class Global(val type: String, override val spoken: String) : QuickStep
    data class MediaControl(val play: Boolean, override val spoken: String) : QuickStep
}

/**
 * Turns "open YouTube, search swift tutorials and play the second video" into
 * [OpenOrTap(YouTube), Search(swift tutorials), Nth(2)].
 * Segments it does not understand are returned as `null` so the AI brain can take over from there.
 */
object QuickCommands {

    private val VERBS = setOf(
        "open", "launch", "start", "go", "search", "look", "google", "play", "watch", "tap", "click", "press",
        "hit", "select", "choose", "scroll", "swipe", "type", "write", "enter", "show", "pull", "visit", "back",
        "home", "next", "previous", "turn", "switch", "enable", "disable", "send", "ask", "find", "read",
        "close", "subscribe", "like", "check", "set", "change", "call", "message", "reply", "copy", "paste", "long",
        "more", "down", "up", "again"
    )

    private val SPLIT = Regex("\\s*(?:,|;|\\band then\\b|\\bthen\\b|\\bafter that\\b|\\band\\b)\\s*", RegexOption.IGNORE_CASE)

    /** Splits a command into clauses, only where the next clause starts with a verb. */
    fun splitClauses(command: String): List<String> {
        val raw = command.split(SPLIT).map { it.trim() }.filter { it.isNotEmpty() }
        val out = ArrayList<String>()
        for (part in raw) {
            val first = TextMatch.tokens(part).firstOrNull()
            if (out.isEmpty() || (first != null && first in VERBS)) out.add(part)
            else out[out.lastIndex] = out.last() + " and " + part
        }
        return out
    }

    /** Parse each clause; null entries are clauses that need the AI. */
    fun parse(command: String): List<Pair<String, QuickStep?>> = splitClauses(command).map { it to parseClause(it) }

    private val SITE_APPS = "youtube|google|chrome|play store|maps|google maps|amazon|flipkart|spotify|instagram|whatsapp|settings|gmail|chatgpt"

    private val R_BACK = Regex("^(go |navigate )?back( please)?$|^(go to |open )?(the )?previous (screen|page)$|^back$")
    private val R_HOME = Regex("^(go |take me )?(to )?(the )?home( screen)?$|^(show|open) (the )?home screen$")
    private val R_RECENTS = Regex("^(show |open )?(the )?(recent apps|recents|app switcher|recent)$")
    private val R_NOTIF = Regex("^(open |show |pull down )?(the )?(notifications?|notification (shade|panel))$")
    private val R_QS = Regex("^(open |show )?(the )?quick settings$")
    private val R_SCROLL = Regex(
        "^(please )?(scroll|page)( the page| the screen| it)?( up| down| left| right)?( more| again| a bit| a little| little)?( please)?$|" +
        "^(go|move|page) (up|down)( please)?$|" +
        "^(more|more down|more up|one more|once more|again|down|up|keep scrolling|scroll more|show more)( please)?$"
    )
    private val R_SWIPE = Regex("^swipe( the screen)? (up|down|left|right)$")
    private val R_SCROLL_END = Regex("^(scroll|go) (to the )?(bottom|end)$")
    private val R_SCROLL_TOP = Regex("^(scroll|go) (back )?(to the )?top$")
    private val R_NEXT_SHORT = Regex("^(next|skip)( short| reel| story| video)?$")
    private val R_PREV_SHORT = Regex("^previous( short| reel| story)?$")
    private val R_MEDIA_PLAY = Regex("^(resume|resume video|resume the video|resume playback|continue video|continue playing)$")
    private val R_MEDIA_PAUSE = Regex("^(pause video|pause the video|pause playback|pause music|pause the music)$")
    private val R_URL = Regex("^(open|go to|visit|browse)( to)? ((https?://)?[a-z0-9.-]+\\.(com|in|org|net|io|ai|dev|co|app|me|tv)(/\\S*)?)$")
    private val R_SEARCH_IN = Regex("^(search|look up|google|find)( for)? (.+?) (on|in|using) ($SITE_APPS)$")
    private val R_SEARCH = Regex("^(search|look up|google)( for| about)? (.+)$")
    private val R_NTH = Regex(
        "^(play|open|tap|click|select|watch|show|choose|press)?( on)? ?(the )?" +
            "(first|second|third|fourth|fifth|sixth|seventh|eighth|ninth|tenth|1st|2nd|3rd|4th|5th|6th|7th|8th|9th|10th|last|top|number \\d{1,2}|video \\d{1,2}|\\d{1,2})" +
            "( one| video| result| item| post| song| chat| link| option| short| reel| product| email| message| entry| row)?( from (the )?(top|list))?$"
    )
    private val R_PLAY = Regex("^(play|watch|put on)( the)? (.+?)( on (youtube|spotify|youtube music))?$")
    private val R_TYPE = Regex("^(type|write|enter|input)( in| the text)? (.+)$")
    private val R_ENTER = Regex("^(press |hit )?(enter|submit|search|go)( key| button)?$")
    private val R_LONG = Regex("^(long press|long tap|press and hold|hold)( on)? (.+)$")
    private val R_TAP = Regex("^(tap|click|press|hit|select|choose)( on)? (.+)$")
    private val R_OPEN = Regex("^(open|launch|start|run|go to|switch to)( the| my)? (.+?)( app| application)?$")

    /** Lowercase, keep the punctuation that matters (apostrophes, dots in URLs), drop the rest. */
    fun light(s: String): String =
        s.lowercase().replace('’', '\'').replace(Regex("[^a-z0-9 './:#-]"), " ")
            .replace(Regex("\\s+"), " ").trim().trimEnd('.', ' ')

    fun parseClause(clause: String): QuickStep? {
        val c = light(clause)
        if (c.isBlank()) return null
        R_BACK.find(c)?.let { return QuickStep.Global("back", "Going back") }
        R_HOME.find(c)?.let { return QuickStep.Global("home", "Going home") }
        R_RECENTS.find(c)?.let { return QuickStep.Global("recents", "Opening recent apps") }
        R_NOTIF.find(c)?.let { return QuickStep.Global("notifications", "Opening notifications") }
        R_QS.find(c)?.let { return QuickStep.Global("quick_settings", "Opening quick settings") }
        R_SCROLL_END.find(c)?.let { return QuickStep.Scroll("down", 5, "Scrolling to the bottom") }
        R_SCROLL_TOP.find(c)?.let { return QuickStep.Scroll("up", 5, "Scrolling to the top") }
        R_SWIPE.find(c)?.let { m -> return QuickStep.Swipe(m.groupValues[2], "Swiping ${m.groupValues[2]}") }
        R_SCROLL.find(c)?.let {
            val dir = when {
                c.contains("up") -> "up"
                c.contains("left") -> "left"
                c.contains("right") -> "right"
                else -> "down"
            }
            return QuickStep.Scroll(dir, 1, "Scrolling $dir")
        }
        R_NEXT_SHORT.find(c)?.let { return QuickStep.Swipe("up", "Next") }
        R_PREV_SHORT.find(c)?.let { return QuickStep.Swipe("down", "Previous") }
        R_MEDIA_PLAY.find(c)?.let { return QuickStep.MediaControl(true, "Resuming video") }
        R_MEDIA_PAUSE.find(c)?.let { return QuickStep.MediaControl(false, "Pausing video") }
        // Use the original text for URLs and typed text (case matters).
        R_URL.find(c)?.let { m -> return QuickStep.OpenUrl(m.groupValues[3], "Opening ${m.groupValues[3]}") }
        R_SEARCH_IN.find(c)?.let { m ->
            val q = originalTail(clause, m.groupValues[3])
            return QuickStep.Search(q, m.groupValues[5], "Searching ${m.groupValues[5]} for $q")
        }
        R_NTH.find(c)?.let { m ->
            val n = TextMatch.ordinal(m.groupValues[4]) ?: 1
            val what = m.groupValues[5].trim().ifBlank { "item" }
            return QuickStep.Nth(n, "Opening the ${m.groupValues[4]} $what")
        }
        R_SEARCH.find(c)?.let { m ->
            val q = originalTail(clause, m.groupValues[3])
            return QuickStep.Search(q, null, "Searching for $q")
        }
        R_ENTER.find(c)?.let { return QuickStep.Enter("Pressing enter") }
        R_PLAY.find(c)?.let { m ->
            val q = m.groupValues[3].trim()
            val generic = setOf("it", "this", "that", "video", "the video", "song", "music", "again")
            if (q !in generic && q.split(' ').size <= 8) {
                val app = m.groupValues[5].ifBlank { null }
                return QuickStep.Play(originalTail(clause, q), app, "Playing $q")
            }
        }
        R_TYPE.find(c)?.let { m -> return QuickStep.TypeText(originalTail(clause, m.groupValues[3]), "Typing") }
        R_LONG.find(c)?.let { m -> return QuickStep.TapLabel(m.groupValues[3], true, "Long-pressing ${m.groupValues[3]}") }
        R_TAP.find(c)?.let { m -> return QuickStep.TapLabel(m.groupValues[3], false, "Tapping ${m.groupValues[3]}") }
        R_OPEN.find(c)?.let { m ->
            val name = m.groupValues[3].trim()
            // "open the video with a car", "open my order history" -> needs understanding
            if (name.split(' ').size > 4 || Regex("\\b(with|having|about|where|which|that|from)\\b").containsMatchIn(name)) return null
            return QuickStep.OpenOrTap(name, "Opening $name")
        }
        return null
    }

    /** Returns [normalizedTail] in its original casing from the clause, when possible. */
    private fun originalTail(clause: String, normalizedTail: String): String {
        val idx = clause.lowercase().indexOf(normalizedTail.lowercase())
        return if (idx >= 0) clause.substring(idx, idx + normalizedTail.length).trim() else normalizedTail
    }

    val STOP = Regex("^(stop|cancel|wait|hold on|pause|freeze|abort|stop it|stop that|never mind|nevermind)$")
    val END = Regex("^(done|stop listening|that's all|that is all|thats all|bye|goodbye|exit|finish|close assistant|go to sleep|thank you that's all)$")
    val YES = Regex("^(yes|yeah|yep|yup|sure|ok|okay|do it|go ahead|confirm|correct|right|haan|ha)$")
    val NO = Regex("^(no|nope|don't|do not|cancel|stop|nah|nahi|wrong)$")

    fun isStop(text: String) = STOP.matches(light(text))
    fun isEnd(text: String) = END.matches(light(text))
    fun isYes(text: String) = YES.matches(light(text))
    fun isNo(text: String) = NO.matches(light(text))
}
