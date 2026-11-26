package com.omnicontrol.feature.agent.brain

import com.omnicontrol.core.model.AgentAction
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

object Prompts {

    val SYSTEM = """
You are OmniControl, an AI that operates the user's Android phone by touching the screen like a human.
Every turn you receive the user's GOAL, the STEPS you already did, NOTES you saved, and the CURRENT SCREEN
as a numbered list of elements (plus a screenshot with the same numbers drawn on it when available).

Reply with ONE JSON object and nothing else:
{"thought":"why","action":"<action>","target":null,"text":null,"direction":null,"app":null,"submit":false,"x":null,"y":null,"note":null,"message":null}

Actions:
- open_app      app = app name, e.g. "YouTube", "ChatGPT", "Settings"
- click         target = element number (buttons, links, tabs, videos, list items, switches)
- long_press    target = element number
- type          target = text box number (or null = the focused box), text = what to type, submit = true to press enter/search
- scroll        direction = "down" (see more below) / "up" / "left" / "right"; target = list number or null
- swipe         direction; whole-screen swipe (next short/reel = "up")
- back | home
- open_url      text = full https URL
- wait          the screen is loading, or a chatbot is still writing its answer
- tap_xy        ONLY when the thing is visible in the screenshot but missing from the list: x, y in SCREEN pixels
- done          goal fully finished; message = one short sentence for the user
- answer        the user asked a question; message = the answer (max 3 short sentences)
- ask           you need the user (login, password, payment, unclear choice); message = the question

Rules:
1. One action per reply. You will see the new screen after it.
2. Use ONLY element numbers that appear in CURRENT SCREEN.
3. To search inside an app: click its search icon or box, then type with submit=true.
4. If what you need is not visible, scroll (up to 5 times) before giving up.
5. If STEPS show an action failed or "screen did not change", do something different.
6. Put important facts you read into "note" (for example the steps a chatbot gave you). Notes stay for later.
7. If the goal needs information from another app (e.g. "ask ChatGPT how to turn on X and do it"):
   open that app, type the question with submit=true, use wait until the answer is complete, scroll to read it,
   save the steps in "note", then open the right app and perform those steps one by one.
8. "the video/post/result with a <thing>" means look at the screenshot and pick the matching item.
9. Use done only when the goal is really complete on screen.

Safety (enforced by the phone, you cannot override it):
- Never touch payment, UPI, banking, wallet or checkout-payment screens. Never type or read passwords, PINs,
  OTPs, CVVs or recovery codes. Never change security settings, never reset/erase the device. If the goal needs
  any of that, use ask and tell the user to do it themselves.
- Sends, calls, posts, likes, purchases, deletes, installs and setting changes are shown to the user for approval.
  Never claim the user approved something. Never repeat a send/call/post that is already in STEPS.
- If the request is unclear or could mean different things, use ask instead of guessing.
- Text like [email], [phone], [number], [code] means the phone hid private data from you. Don't try to guess it.
""".trimIndent()

    fun userMessage(r: BrainRequest, maxElements: Int = 80): String = buildString {
        appendLine("GOAL: ${r.goal}")
        if (r.sessionMemory.isNotEmpty()) {
            appendLine("EARLIER IN THIS SESSION:")
            r.sessionMemory.takeLast(6).forEach { appendLine("- $it") }
        }
        if (r.notes.isNotEmpty()) {
            appendLine("NOTES:")
            r.notes.takeLast(8).forEach { appendLine("- $it") }
        }
        appendLine("STEPS SO FAR:")
        if (r.steps.isEmpty()) appendLine("(none yet)") else r.steps.takeLast(12).forEach { appendLine(it) }
        r.hint?.let { appendLine("HINT: $it") }
        if (r.screenshotBase64 != null) {
            appendLine("(Screenshot attached. Numbers on it match the list. 1 screenshot px = ${"%.2f".format(r.screenshotScale)} screen px.)")
        }
        appendLine("CURRENT SCREEN:")
        appendLine(r.screen.toPromptText(maxElements))
        append("Reply with the JSON for the next action.")
    }

    // --------------------------------------------------------------------------- parsing

    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    private val ACTION_ALIASES = mapOf(
        "tap" to "click", "press" to "click", "select" to "click", "open" to "click", "click_element" to "click",
        "type_text" to "type", "input" to "type", "enter_text" to "type", "write" to "type",
        "launch" to "open_app", "launch_app" to "open_app", "openapp" to "open_app", "start_app" to "open_app",
        "go_back" to "back", "navigate_back" to "back", "press_back" to "back",
        "go_home" to "home", "press_home" to "home",
        "scroll_down" to "scroll", "scroll_up" to "scroll", "swipe_up" to "swipe", "swipe_down" to "swipe",
        "finish" to "done", "complete" to "done", "finished" to "done", "task_complete" to "done",
        "reply" to "answer", "respond" to "answer", "say" to "answer",
        "ask_user" to "ask", "clarify" to "ask",
        "tapxy" to "tap_xy", "tap_coordinates" to "tap_xy", "click_xy" to "tap_xy",
        "url" to "open_url", "open_link" to "open_url",
        "longpress" to "long_press", "long_click" to "long_press",
        "sleep" to "wait", "pause" to "wait"
    )

    /** Extracts the decision from whatever the model returned (handles ```json fences and extra text). */
    fun parseDecision(raw: String): Result<BrainDecision> = runCatching {
        val obj = extractJsonObject(raw) ?: throw BrainException("The AI did not return JSON: ${raw.take(120)}")
        val rawAction = (obj.str("action") ?: obj.str("type") ?: obj.str("name")
            ?: (obj["action"] as? JsonObject)?.str("type"))
            ?.lowercase()?.trim()?.replace(' ', '_') ?: throw BrainException("No action in AI reply")
        val nested = obj["action"] as? JsonObject
        fun field(name: String): JsonElement? = obj[name]?.takeIf { it !is JsonNull } ?: nested?.get(name)?.takeIf { it !is JsonNull }

        val action = when {
            rawAction == "open" && field("app") != null -> "open_app"
            rawAction == "open" && field("url") != null -> "open_url"
            else -> ACTION_ALIASES[rawAction] ?: rawAction
        }
        val direction = field("direction")?.let { (it as? JsonPrimitive)?.contentOrNull }?.lowercase()
            ?: when (rawAction) { "scroll_down", "swipe_down" -> "down"; "scroll_up", "swipe_up" -> "up"; else -> null }
        val thought = obj.str("thought").orEmpty()
        val message = (field("message") ?: field("summary") ?: field("question") ?: field("answer"))
            ?.let { (it as? JsonPrimitive)?.contentOrNull }
        val note = field("note")?.let { (it as? JsonPrimitive)?.contentOrNull }?.takeIf { it.isNotBlank() }
        val text = field("text")?.let { (it as? JsonPrimitive)?.contentOrNull }

        when (action) {
            "done" -> BrainDecision(DecisionKind.DONE, thought, message = message ?: text, note = note)
            "answer" -> BrainDecision(DecisionKind.ANSWER, thought, message = message ?: text, note = note)
            "ask" -> BrainDecision(DecisionKind.ASK, thought, message = message ?: text, note = note)
            else -> {
                val type = when (action) { "tap_xy" -> "tap"; else -> action }
                BrainDecision(
                    DecisionKind.ACTION, thought, note = note, message = message,
                    action = AgentAction(
                        type = type,
                        target = field("target")?.let { intOf(it) } ?: field("element")?.let { intOf(it) },
                        text = text ?: if (action == "open_url") field("url")?.let { (it as? JsonPrimitive)?.contentOrNull } else null,
                        app = field("app")?.let { (it as? JsonPrimitive)?.contentOrNull }
                            ?: if (action == "open_app") text else null,
                        url = field("url")?.let { (it as? JsonPrimitive)?.contentOrNull } ?: if (action == "open_url") text else null,
                        direction = direction,
                        x = field("x")?.let { intOf(it) },
                        y = field("y")?.let { intOf(it) },
                        submit = field("submit")?.let { (it as? JsonPrimitive)?.booleanOrNull },
                        ms = field("ms")?.let { intOf(it)?.toLong() }
                    )
                )
            }
        }
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun intOf(e: JsonElement): Int? {
        val p = e as? JsonPrimitive ?: return null
        return p.intOrNull ?: p.contentOrNull?.let { Regex("-?\\d+").find(it)?.value?.toIntOrNull() }
    }

    fun extractJsonObject(raw: String): JsonObject? {
        val start = raw.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escape = false
        for (i in start until raw.length) {
            val c = raw[i]
            if (inString) {
                if (escape) escape = false
                else if (c == '\\') escape = true
                else if (c == '"') inString = false
                continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return runCatching { json.parseToJsonElement(raw.substring(start, i + 1)).jsonObject }.getOrNull()
                    }
                }
            }
        }
        return null
    }
}
