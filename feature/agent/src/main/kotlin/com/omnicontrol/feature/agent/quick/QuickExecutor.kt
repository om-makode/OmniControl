package com.omnicontrol.feature.agent.quick

import android.net.Uri
import com.omnicontrol.core.common.TextMatch
import com.omnicontrol.core.model.ActionResult
import com.omnicontrol.core.model.AgentAction
import com.omnicontrol.feature.accessibility.OmniAccessibilityService
import com.omnicontrol.feature.accessibility.ScreenSnapshot
import kotlinx.coroutines.delay

data class StepOutcome(val ok: Boolean, val message: String, val needsBrain: Boolean = false)

/**
 * Executes [QuickStep]s. [act] performs an action (it goes through the safety check and shows
 * progress in the UI). When something can't be done reliably without understanding the screen,
 * the outcome says `needsBrain = true` so the AI can continue.
 */
class QuickExecutor(
    private val act: suspend (AgentAction, ScreenSnapshot?) -> ActionResult,
    private val brainAvailable: () -> Boolean
) {

    suspend fun run(step: QuickStep): StepOutcome = when (step) {
        is QuickStep.Global -> fromResult(act(AgentAction(step.type), null))
        is QuickStep.Scroll -> {
            var last = act(AgentAction("scroll", direction = step.direction), null)
            for (i in 1 until step.times) {
                if (!last.success || !last.screenChanged) break
                last = act(AgentAction("scroll", direction = step.direction), null)
            }
            fromResult(last)
        }
        is QuickStep.Swipe -> fromResult(act(AgentAction("swipe", direction = step.direction), null))
        is QuickStep.OpenUrl -> fromResult(act(AgentAction("open_url", url = step.url), null))
        is QuickStep.TypeText -> fromResult(act(AgentAction("type", text = step.text), null))
        is QuickStep.Enter -> fromResult(act(AgentAction("enter"), null))
        is QuickStep.OpenOrTap -> openOrTap(step.name)
        is QuickStep.Search -> search(step.query, step.app)
        is QuickStep.Play -> {
            OmniAccessibilityService.notifyMediaActionExpected()
            val s = search(step.query, step.app)
            if (!s.ok) s else nth(1)
        }
        is QuickStep.Nth -> {
            OmniAccessibilityService.notifyMediaActionExpected()
            nth(step.n)
        }
        is QuickStep.TapLabel -> tapLabel(step.label, step.long)
        is QuickStep.MediaControl -> fromResult(act(AgentAction(if (step.play) "media_play" else "media_pause"), null))
    }

    private fun fromResult(r: ActionResult) = StepOutcome(r.success, r.reason)

    private suspend fun snap(): ScreenSnapshot? = OmniAccessibilityService.snapshot()

    // ----------------------------------------------------------------------------------- open

    private suspend fun openOrTap(name: String): StepOutcome {
        val launcher = OmniAccessibilityService.launcher()
        if (launcher?.find(name) != null) return fromResult(act(AgentAction("open_app", app = name), null))

        // Not an app: maybe it's something on screen ("open settings" inside an app, "open profile").
        val s = snap()
        if (s != null) {
            val m = ScreenQueries.findByLabel(s.state, name)
            if (m != null && m.score >= 0.8 && m.score - m.runnerUp >= 0.04) {
                return fromResult(act(AgentAction("click", target = m.element.id), s))
            }
        }
        val r = act(AgentAction("open_app", app = name), null) // also tries system screens + websites
        if (r.success) return fromResult(r)
        return StepOutcome(false, r.reason, needsBrain = true)
    }

    // --------------------------------------------------------------------------------- search

    private suspend fun search(query: String, app: String?): StepOutcome {
        val key = app?.let { TextMatch.compact(it) }
        if (key != null) {
            deepLink(key, query)?.let { (url, pkg) ->
                return fromResult(act(AgentAction("open_url", url = url, app = pkg), null))
            }
            val opened = act(AgentAction("open_app", app = app), null)
            if (!opened.success) return StepOutcome(false, opened.reason)
            OmniAccessibilityService.waitForSettle(500, 2500)
        } else {
            val fg = OmniAccessibilityService.foregroundPackage()
            PACKAGE_TO_KEY[fg]?.let { k ->
                deepLink(k, query)?.let { (url, pkg) -> return fromResult(act(AgentAction("open_url", url = url, app = pkg), null)) }
            }
        }
        return searchInCurrentApp(query)
    }

    private suspend fun searchInCurrentApp(query: String): StepOutcome {
        var s = snap() ?: return StepOutcome(false, "Can't read the screen")
        var field = ScreenQueries.searchField(s.state)
        if (field == null) {
            val btn = ScreenQueries.searchButton(s.state)
            if (btn == null) {
                if (isLauncher(s.state.packageName) || s.state.elements.isEmpty()) {
                    val (url, pkg) = deepLink("google", query)!!
                    return fromResult(act(AgentAction("open_url", url = url, app = pkg), null))
                }
                return StepOutcome(false, "I can't find a search box in ${s.state.appLabel}", needsBrain = true)
            }
            val tapped = act(AgentAction("click", target = btn.id), s)
            if (!tapped.success) return fromResult(tapped)
            for (attempt in 0 until 4) {
                s = snap() ?: return StepOutcome(false, "Can't read the screen")
                field = ScreenQueries.searchField(s.state)
                if (field != null) break
                delay(400)
            }
        }
        val f = field ?: return StepOutcome(false, "The search box did not open", needsBrain = true)
        return fromResult(act(AgentAction("type", target = f.id, text = query, submit = true), s))
    }

    private fun isLauncher(pkg: String) =
        pkg.contains("launcher") || pkg.endsWith(".home") || pkg == "com.miui.home" || pkg.isBlank()

    // ------------------------------------------------------------------------------------ nth

    private suspend fun nth(n: Int): StepOutcome {
        val s = snap()
        var item = s?.let { ScreenQueries.nth(it.state, n) }
        var currentSnap = s
        if (item == null) {
            // Results may still be loading or settling.
            OmniAccessibilityService.waitForSettle(400, 2500)
            currentSnap = snap()
            item = currentSnap?.let { ScreenQueries.nth(it.state, n) }
        }
        if (item == null) {
            val total = currentSnap?.let { ScreenQueries.contentItems(it.state).size } ?: 0
            val msg = if (total > 0) "I can only see $total videos on screen right now. Say 'scroll down' to see more."
                      else "I can't see video number $n on this screen."
            return StepOutcome(false, msg, needsBrain = false)
        }
        return fromResult(act(AgentAction("click", target = item.id), currentSnap ?: snap()))
    }

    // ------------------------------------------------------------------------------------ tap

    private suspend fun tapLabel(label: String, long: Boolean): StepOutcome {
        val type = if (long) "long_press" else "click"
        val maxScrolls = if (brainAvailable()) 0 else 3
        for (attempt in 0..maxScrolls) {
            val s = snap() ?: return StepOutcome(false, "Can't read the screen")
            val m = ScreenQueries.findByLabel(s.state, label)
            if (m != null && m.score >= 0.75 && (m.score - m.runnerUp >= 0.04 || m.score >= 0.95)) {
                return fromResult(act(AgentAction(type, target = m.element.id), s))
            }
            if (attempt < maxScrolls) {
                val r = act(AgentAction("scroll", direction = "down"), s)
                if (!r.screenChanged) break
            }
        }
        return StepOutcome(false, "I can't find \"$label\" on the screen", needsBrain = true)
    }

    companion object {
        private val PACKAGE_TO_KEY = mapOf(
            "com.google.android.youtube" to "youtube",
            "com.android.vending" to "playstore",
            "com.google.android.apps.maps" to "maps",
            "com.android.chrome" to "google",
            "in.amazon.mShop.android.shopping" to "amazon",
            "com.flipkart.android" to "flipkart"
        )

        /** Reliable search links: returns (url, package to open it in). */
        fun deepLink(key: String, q: String): Pair<String, String?>? {
            val e = Uri.encode(q)
            return when (key) {
                "youtube" -> "https://www.youtube.com/results?search_query=$e" to "com.google.android.youtube"
                "google", "chrome" -> "https://www.google.com/search?q=$e" to null
                "playstore" -> "https://play.google.com/store/search?q=$e&c=apps" to "com.android.vending"
                "maps", "googlemaps" -> "https://www.google.com/maps/search/$e" to "com.google.android.apps.maps"
                "amazon" -> "https://www.amazon.in/s?k=$e" to "in.amazon.mShop.android.shopping"
                "flipkart" -> "https://www.flipkart.com/search?q=$e" to "com.flipkart.android"
                else -> null
            }
        }
    }
}
