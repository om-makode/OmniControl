package com.omnicontrol.feature.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.omnicontrol.core.common.OmniLog
import com.omnicontrol.core.model.ElementSource
import com.omnicontrol.core.model.Observation
import com.omnicontrol.core.model.RectBounds
import com.omnicontrol.core.model.Role
import com.omnicontrol.core.model.UiElement
import java.security.MessageDigest

class ScreenReader(private val service: AccessibilityService) {

    private val packageManager: PackageManager = service.packageManager

    fun captureObservation(): Observation {
        val startTime = System.currentTimeMillis()
        var currentPackage = ""
        var appLabel = ""
        var keyboardVisible = false
        val dialogElementIds = mutableListOf<Int>()
        var focusedElementId: Int? = null

        val windows = try {
            service.windows
        } catch (e: Exception) {
            OmniLog.w(TAG, "Failed to get service.windows", e)
            emptyList<AccessibilityWindowInfo>()
        }

        // 1. Inspect window list for keyboard and target app windows
        val interactiveRoots = mutableListOf<Pair<AccessibilityNodeInfo, Boolean>>()

        var appPackage: String? = null
        if (windows.isNotEmpty()) {
            for (window in windows) {
                if (window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                    keyboardVisible = true
                    continue
                }

                val root = window.root ?: continue
                val pkg = root.packageName?.toString() ?: ""

                // Ignore OmniControl's own overlay windows
                if (pkg == service.packageName) {
                    root.recycle()
                    continue
                }

                if (window.type == AccessibilityWindowInfo.TYPE_APPLICATION && pkg.isNotEmpty() && pkg != "com.android.systemui") {
                    appPackage = pkg
                } else if (currentPackage.isEmpty() && pkg.isNotEmpty()) {
                    currentPackage = pkg
                }

                val isDialog = window.type == AccessibilityWindowInfo.TYPE_SYSTEM

                interactiveRoots.add(root to isDialog)
            }
        }
        if (!appPackage.isNullOrEmpty()) {
            currentPackage = appPackage
        }

        // Fallback to rootInActiveWindow if window list didn't yield any roots
        if (interactiveRoots.isEmpty()) {
            val fallbackRoot = service.rootInActiveWindow
            if (fallbackRoot != null) {
                val pkg = fallbackRoot.packageName?.toString() ?: ""
                if (pkg != service.packageName) {
                    currentPackage = pkg
                    interactiveRoots.add(fallbackRoot to false)
                } else {
                    fallbackRoot.recycle()
                }
            }
        }

        // 2. Resolve App Label
        if (currentPackage.isNotEmpty()) {
            try {
                val appInfo = packageManager.getApplicationInfo(currentPackage, 0)
                appLabel = packageManager.getApplicationLabel(appInfo).toString()
            } catch (_: Exception) {
                appLabel = currentPackage.substringAfterLast(".")
            }
        }

        // 3. Traverse interactive roots
        var nextId = 1
        val finalElements = mutableListOf<UiElement>()

        for ((rootNode, isDialog) in interactiveRoots) {
            val rootElements = mutableListOf<UiElement>()
            traverseNode(
                node = rootNode,
                parentId = null,
                containerId = null,
                idProvider = { nextId++ },
                sink = rootElements
            )
            rootNode.recycle()

            if (isDialog) {
                dialogElementIds.addAll(rootElements.map { it.id })
            }
            finalElements.addAll(rootElements)
        }

        // Check for focused element
        focusedElementId = finalElements.find { it.role == Role.TEXT_FIELD && it.editable }?.id

        // 4. Secure Window / Empty Screen Detection
        val isSecureWindow = finalElements.isEmpty() && currentPackage.isNotEmpty()

        // 5. Compute Screen Fingerprint
        val fingerprint = computeFingerprint(currentPackage, finalElements)

        val elapsed = System.currentTimeMillis() - startTime
        OmniLog.d(TAG, "Screen observation captured: ${finalElements.size} elements from $currentPackage in ${elapsed}ms")

        return Observation(
            packageName = currentPackage,
            activityHint = null,
            appLabel = appLabel,
            elements = finalElements,
            screenFingerprint = fingerprint,
            keyboardVisible = keyboardVisible,
            focusedElementId = focusedElementId,
            dialogs = dialogElementIds,
            isSecureWindow = isSecureWindow,
            timestamp = System.currentTimeMillis()
        )
    }

    private fun traverseNode(
        node: AccessibilityNodeInfo,
        parentId: Int?,
        containerId: Int?,
        idProvider: () -> Int,
        sink: MutableList<UiElement>
    ) {
        if (!node.isVisibleToUser) {
            return
        }

        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.width() <= 0 || bounds.height() <= 0) {
            return
        }

        val text = node.text?.toString()?.trim()
        val desc = node.contentDescription?.toString()?.trim()
        val hint = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            node.hintText?.toString()?.trim()
        } else {
            null
        }
        val viewId = node.viewIdResourceName
        val className = node.className?.toString() ?: ""

        val isClickable = node.isClickable
        val isLongClickable = node.isLongClickable
        val isScrollable = node.isScrollable
        val isEditable = node.isEditable

        val role = resolveRole(node, className)

        // Check if this node is a container or clickable parent that contains child texts
        val hasMeaningfulInfo = !text.isNullOrEmpty() ||
            !desc.isNullOrEmpty() ||
            !hint.isNullOrEmpty() ||
            isClickable ||
            isEditable ||
            isScrollable

        var assignedId: Int? = null

        if (hasMeaningfulInfo) {
            assignedId = idProvider()

            // If the element is clickable, try to collect child text if its own text is blank (card pattern)
            val combinedText = if (isClickable && text.isNullOrEmpty() && desc.isNullOrEmpty()) {
                collectChildTexts(node)
            } else {
                text
            }

            val uiElement = UiElement(
                id = assignedId,
                role = role,
                text = combinedText,
                description = desc,
                hint = hint,
                viewId = viewId,
                className = className,
                bounds = RectBounds(bounds.left, bounds.top, bounds.right, bounds.bottom),
                clickable = isClickable,
                longClickable = isLongClickable,
                scrollable = isScrollable,
                editable = isEditable,
                checked = if (node.isCheckable) node.isChecked else null,
                selected = node.isSelected,
                enabled = node.isEnabled,
                isPassword = node.isPassword,
                source = ElementSource.TREE,
                parentId = parentId,
                containerId = containerId
            )
            sink.add(uiElement)
        }

        val currentContainerId = if (isScrollable || role == Role.CONTAINER) assignedId ?: containerId else containerId

        // Recursively traverse children
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            traverseNode(
                node = child,
                parentId = assignedId ?: parentId,
                containerId = currentContainerId,
                idProvider = idProvider,
                sink = sink
            )
            child.recycle()
        }
    }

    private fun collectChildTexts(node: AccessibilityNodeInfo): String? {
        val texts = mutableListOf<String>()
        collectChildTextsRecursive(node, texts)
        return if (texts.isNotEmpty()) texts.joinToString(" · ") else null
    }

    private fun collectChildTextsRecursive(node: AccessibilityNodeInfo, sink: MutableList<String>) {
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val text = child.text?.toString()?.trim()
            if (!text.isNullOrEmpty() && !sink.contains(text)) {
                sink.add(text)
            }
            val desc = child.contentDescription?.toString()?.trim()
            if (!desc.isNullOrEmpty() && !sink.contains(desc)) {
                sink.add(desc)
            }
            collectChildTextsRecursive(child, sink)
            child.recycle()
        }
    }

    private fun resolveRole(node: AccessibilityNodeInfo, className: String): Role {
        return when {
            node.isEditable || className.contains("EditText", ignoreCase = true) -> Role.TEXT_FIELD
            className.contains("Button", ignoreCase = true) -> Role.BUTTON
            className.contains("CheckBox", ignoreCase = true) -> Role.CHECKBOX
            className.contains("Switch", ignoreCase = true) || className.contains("ToggleButton", ignoreCase = true) -> Role.SWITCH
            className.contains("Tab", ignoreCase = true) -> Role.TAB
            className.contains("ImageView", ignoreCase = true) -> if (node.isClickable) Role.BUTTON else Role.IMAGE
            className.contains("TextView", ignoreCase = true) -> if (node.isClickable) Role.BUTTON else Role.TEXT
            node.isScrollable || className.contains("RecyclerView", ignoreCase = true) ||
                className.contains("ListView", ignoreCase = true) ||
                className.contains("ScrollView", ignoreCase = true) -> Role.CONTAINER
            node.isClickable -> Role.BUTTON
            else -> Role.OTHER
        }
    }

    companion object {
        private const val TAG = "ScreenReader"

        fun computeFingerprint(packageName: String, elements: List<UiElement>): String {
            val meaningfulSignatures = elements
                .filter { it.clickable || it.editable || !it.text.isNullOrBlank() || !it.description.isNullOrBlank() }
                .take(15)
                .joinToString(";") { "${it.role}:${it.viewId ?: ""}:${it.primaryLabel}" }

            val rawInput = "$packageName|${elements.size}|$meaningfulSignatures"
            return sha256(rawInput)
        }

        private fun sha256(input: String): String {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(input.toByteArray(Charsets.UTF_8))
            return digest.fold("") { str, it -> str + "%02x".format(it) }.take(16)
        }
    }
}
