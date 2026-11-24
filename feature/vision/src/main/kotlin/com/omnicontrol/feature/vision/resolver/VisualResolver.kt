package com.omnicontrol.feature.vision.resolver

import android.graphics.Bitmap
import com.omnicontrol.core.common.OmniLog
import com.omnicontrol.core.model.Observation
import com.omnicontrol.core.model.Role
import com.omnicontrol.core.model.UiElement
import com.omnicontrol.feature.vision.color.ColorAnalyzer
import com.omnicontrol.feature.vision.color.NamedColor
import com.omnicontrol.feature.vision.ocr.OcrEngine
import com.omnicontrol.feature.vision.screenshot.ScreenshotManager

sealed interface VisualResult {
    data class Found(val element: UiElement) : VisualResult
    data class Ambiguous(val candidates: List<UiElement>, val screenshot: Bitmap) : VisualResult
    data object SecureScreen : VisualResult
    data class NotFound(val reason: String) : VisualResult
}

object VisualResolver {
    private const val TAG = "VisualResolver"

    suspend fun resolve(query: String, observation: Observation): VisualResult {
        val cleanQuery = query.removePrefix("visual:").trim()
        OmniLog.i(TAG, "Resolving visual query: '$cleanQuery'")

        val bitmap = ScreenshotManager.capture(observation.screenFingerprint)
        if (bitmap == null) {
            if (ScreenshotManager.isSecureScreen) {
                return VisualResult.SecureScreen
            }
            return VisualResult.NotFound("Could not capture screenshot for visual analysis")
        }

        // 1. Color-based matching ("red button", "blue icon", etc.)
        val targetColor = ColorAnalyzer.parseColorName(cleanQuery)
        if (targetColor != null && targetColor != NamedColor.UNKNOWN) {
            val matchingElements = ColorAnalyzer.findByColor(bitmap, observation.elements, targetColor)
            OmniLog.d(TAG, "Found ${matchingElements.size} elements matching color $targetColor")
            return when {
                matchingElements.size == 1 -> VisualResult.Found(matchingElements.first())
                matchingElements.size in 2..6 -> VisualResult.Ambiguous(matchingElements, bitmap)
                matchingElements.size > 6 -> {
                    // Filter to clickable or larger elements
                    val clickables = matchingElements.filter { it.clickable }
                    if (clickables.size == 1) {
                        VisualResult.Found(clickables.first())
                    } else if (clickables.isNotEmpty()) {
                        VisualResult.Ambiguous(clickables.take(6), bitmap)
                    } else {
                        VisualResult.Ambiguous(matchingElements.take(6), bitmap)
                    }
                }
                else -> VisualResult.NotFound("No elements found with color ${targetColor.name.lowercase()}")
            }
        }

        // 2. OCR text inside images / canvas
        val ocrItems = OcrEngine.recognizeText(bitmap)
        val matchedOcr = ocrItems.find { it.text.contains(cleanQuery, ignoreCase = true) }
        if (matchedOcr != null) {
            val treeElement = observation.elements.find { elem ->
                elem.bounds.left <= matchedOcr.bounds.left &&
                elem.bounds.right >= matchedOcr.bounds.right &&
                elem.bounds.top <= matchedOcr.bounds.top &&
                elem.bounds.bottom >= matchedOcr.bounds.bottom
            }
            if (treeElement != null) {
                OmniLog.i(TAG, "Matched visual query to tree element #${treeElement.id} via OCR")
                return VisualResult.Found(treeElement)
            }
        }

        // 3. Candidate visual elements (cards/images/thumbnails)
        val visualCandidates = observation.elements.filter { elem ->
            val role = elem.role
            (role == Role.IMAGE || role == Role.BUTTON || role == Role.CONTAINER) &&
            (elem.bounds.right - elem.bounds.left) >= 64 &&
            (elem.bounds.bottom - elem.bounds.top) >= 64
        }

        return if (visualCandidates.isNotEmpty()) {
            OmniLog.d(TAG, "Ambiguous visual candidates: ${visualCandidates.size} elements")
            VisualResult.Ambiguous(visualCandidates.take(5), bitmap)
        } else {
            VisualResult.NotFound("Could not locate visual target: '$cleanQuery'")
        }
    }
}
