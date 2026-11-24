package com.omnicontrol.feature.vision.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.omnicontrol.core.common.OmniLog
import com.omnicontrol.core.model.ElementSource
import com.omnicontrol.core.model.Observation
import com.omnicontrol.core.model.RectBounds
import com.omnicontrol.core.model.Role
import com.omnicontrol.core.model.UiElement
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class OcrItem(
    val text: String,
    val bounds: RectBounds
)

object OcrEngine {
    private const val TAG = "OcrEngine"
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun recognizeText(bitmap: Bitmap): List<OcrItem> {
        return suspendCancellableCoroutine { continuation ->
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val items = mutableListOf<OcrItem>()
                    for (block in visionText.textBlocks) {
                        for (line in block.lines) {
                            val box = line.boundingBox ?: Rect()
                            items.add(
                                OcrItem(
                                    text = line.text.trim(),
                                    bounds = RectBounds(
                                        left = box.left,
                                        top = box.top,
                                        right = box.right,
                                        bottom = box.bottom
                                    )
                                )
                            )
                        }
                    }
                    OmniLog.d(TAG, "Recognized ${items.size} text lines via ML Kit OCR")
                    if (continuation.isActive) {
                        continuation.resume(items)
                    }
                }
                .addOnFailureListener { e ->
                    OmniLog.w(TAG, "OCR recognition failed", e)
                    if (continuation.isActive) {
                        continuation.resume(emptyList())
                    }
                }
        }
    }

    fun mergeWithObservation(
        observation: Observation,
        ocrItems: List<OcrItem>
    ): Observation {
        if (ocrItems.isEmpty()) return observation

        val updatedElements = observation.elements.toMutableList()
        val unassignedOcr = mutableListOf<OcrItem>()

        for (ocr in ocrItems) {
            var matched = false
            for (i in updatedElements.indices) {
                val elem = updatedElements[i]
                if (containsBox(elem.bounds, ocr.bounds)) {
                    if (elem.text.isNullOrBlank()) {
                        updatedElements[i] = elem.copy(text = ocr.text)
                    }
                    matched = true
                    break
                }
            }
            if (!matched) {
                unassignedOcr.add(ocr)
            }
        }

        // Add remaining OCR items as visual text elements
        var nextId = (observation.elements.maxOfOrNull { it.id } ?: 0) + 1
        for (unmatched in unassignedOcr) {
            updatedElements.add(
                UiElement(
                    id = nextId++,
                    role = Role.TEXT,
                    text = unmatched.text,
                    description = null,
                    bounds = unmatched.bounds,
                    clickable = false,
                    enabled = true,
                    scrollable = false,
                    source = ElementSource.OCR
                )
            )
        }

        return observation.copy(elements = updatedElements)
    }

    private fun containsBox(parent: RectBounds, child: RectBounds): Boolean {
        return child.left >= parent.left - 10 &&
                child.top >= parent.top - 10 &&
                child.right <= parent.right + 10 &&
                child.bottom <= parent.bottom + 10
    }
}
