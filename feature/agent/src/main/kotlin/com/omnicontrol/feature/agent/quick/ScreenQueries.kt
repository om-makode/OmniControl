package com.omnicontrol.feature.agent.quick

import com.omnicontrol.core.common.TextMatch
import com.omnicontrol.core.model.ScreenState
import com.omnicontrol.core.model.UiElement

/** Answers simple questions about a screen without any AI. */
object ScreenQueries {

    /**
     * The main content items (videos, search results, chats, posts, products) in reading order.
     * Filters out toolbars, tabs, chips, tiny icons and ads.
     */
    fun contentItems(screen: ScreenState): List<UiElement> {
        val w = screen.screenWidth.coerceAtLeast(1)
        val h = screen.screenHeight.coerceAtLeast(1)
        var c = screen.elements.filter { e ->
            e.clickable && e.enabled && !e.editable && !e.scrollable &&
                e.bounds.width >= w * 0.4f &&
                e.bounds.height >= h * 0.045f &&
                e.bounds.height <= h * 0.8f &&
                e.bounds.centerY >= h * 0.1f && e.bounds.centerY <= h * 0.93f &&
                !isAd(e)
        }
        if (c.any { it.inList }) c = c.filter { it.inList }
        // Keep the outer card when a card and a child row are both clickable.
        c = c.filter { inner -> c.none { outer -> outer !== inner && outer.bounds.contains(inner.bounds) && outer.bounds.area > inner.bounds.area } }
        return c.sortedWith(compareBy({ it.bounds.top / 12 }, { it.bounds.left }))
            .distinctBy { it.bounds.top / 12 * 10_000 + it.bounds.left / 12 }
    }

    private fun isAd(e: UiElement): Boolean {
        val t = e.searchText
        return t.contains("sponsored") || t.startsWith("ad ") || t.contains(" ad · ") || t.contains("· ad ")
    }

    fun nth(screen: ScreenState, n: Int): UiElement? {
        val items = contentItems(screen)
        if (items.isEmpty()) return null
        return if (n < 0) items.last() else items.getOrNull(n - 1)
    }

    data class LabelMatch(val element: UiElement, val score: Double, val runnerUp: Double)

    /** Best element for a spoken label ("subscribe", "the settings icon", "privacy"). */
    fun findByLabel(screen: ScreenState, query: String): LabelMatch? {
        val q = cleanLabelQuery(query)
        if (q.isBlank()) return null
        val scored = screen.elements.mapNotNull { e ->
            val base = maxOf(
                TextMatch.labelScore(q, e.text.orEmpty()),
                TextMatch.labelScore(q, e.description.orEmpty()),
                TextMatch.labelScore(q, e.hint.orEmpty()) * 0.9,
                TextMatch.labelScore(q, e.viewId?.substringAfterLast('/')?.replace('_', ' ').orEmpty()) * 0.8
            )
            if (base <= 0.0) null else e to base + (if (e.actionable) 0.05 else 0.0) + (if (e.enabled) 0.0 else -0.2)
        }.sortedByDescending { it.second }
        val best = scored.firstOrNull() ?: return null
        val second = scored.drop(1).firstOrNull { it.first.primaryLabel != best.first.primaryLabel }?.second ?: 0.0
        return LabelMatch(best.first, best.second, second)
    }

    fun cleanLabelQuery(q: String): String =
        TextMatch.normalize(q)
            .replace(Regex("^(on |the |a |an )+"), "")
            .replace(Regex("\\s+(button|tab|option|icon|link|menu|item|toggle|switch)$"), "")
            .trim()

    fun searchField(screen: ScreenState): UiElement? =
        screen.elements.filter { it.editable && it.enabled && !it.isPassword }
            .maxByOrNull { (if (it.searchText.contains("search")) 2 else 0) + (if (it.focused) 1 else 0) }

    fun searchButton(screen: ScreenState): UiElement? =
        screen.elements.filter { e ->
            e.clickable && !e.editable && e.enabled &&
                (e.searchText.split(' ').any { it == "search" } || e.viewId?.contains("search", true) == true)
        }.minByOrNull { it.bounds.top }
}
