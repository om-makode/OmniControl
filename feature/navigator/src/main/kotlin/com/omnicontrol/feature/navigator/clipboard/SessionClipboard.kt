package com.omnicontrol.feature.navigator.clipboard

import com.omnicontrol.core.model.Observation

object SessionClipboard {
    private val URL_REGEX = Regex("https?://[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(?:/[^\\s]*)?")

    var lastCopiedUrl: String? = null
        private set

    var lastCopiedText: String? = null
        private set

    fun copyText(text: String) {
        lastCopiedText = text
        val urlMatch = URL_REGEX.find(text)
        if (urlMatch != null) {
            lastCopiedUrl = urlMatch.value
        }
    }

    fun extractUrlFromObservation(observation: Observation): String? {
        for (element in observation.elements) {
            val label = element.primaryLabel
            val match = URL_REGEX.find(label)
            if (match != null) {
                lastCopiedUrl = match.value
                return match.value
            }
        }
        return lastCopiedUrl
    }

    fun clear() {
        lastCopiedUrl = null
        lastCopiedText = null
    }
}
