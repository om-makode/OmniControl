# Phase 6 Report — Visual Understanding

## Summary
Phase 6 implemented the on-device visual understanding pipeline in `:feature:vision`. It provides rate-limited screenshot capture via the accessibility service, secure window black-screen detection, HSV-based dominant color analysis for natural language color targeting ("the red button", "blue icon"), offline ML Kit text recognition for extracting text in custom views/canvases/images, Set-of-Marks visual labeling for numbered clarification overlays, and a multi-tiered `VisualResolver` integrated into the ReAct agent loop.

## Files created
- `feature/vision/build.gradle.kts`
- `feature/vision/consumer-rules.pro`
- `feature/vision/src/main/AndroidManifest.xml`
- `feature/vision/src/main/kotlin/com/omnicontrol/feature/vision/screenshot/ScreenshotManager.kt`
- `feature/vision/src/main/kotlin/com/omnicontrol/feature/vision/color/ColorAnalyzer.kt`
- `feature/vision/src/main/kotlin/com/omnicontrol/feature/vision/ocr/OcrEngine.kt`
- `feature/vision/src/main/kotlin/com/omnicontrol/feature/vision/som/SetOfMarksRenderer.kt`
- `feature/vision/src/main/kotlin/com/omnicontrol/feature/vision/resolver/VisualResolver.kt`
- `feature/vision/src/test/kotlin/com/omnicontrol/feature/vision/ColorAnalyzerTest.kt`

## Files modified
- `settings.gradle.kts` (added `:feature:vision`)
- `gradle/libs.versions.toml` (added `mlkitTextRecognition = "16.0.1"`)
- `app/build.gradle.kts` (added `:feature:vision`)
- `feature/agent/build.gradle.kts` (added `:feature:vision`)
- `feature/accessibility/src/main/kotlin/com/omnicontrol/feature/accessibility/OmniAccessibilityService.kt` (added `suspend fun captureScreenshotBitmap()`)
- `feature/agent/src/main/kotlin/com/omnicontrol/feature/agent/llm/ActionGrammar.kt` (added `click_visual` action type)
- `feature/agent/src/main/kotlin/com/omnicontrol/feature/agent/loop/AgentLoop.kt` (integrated OCR tree enrichment and visual target resolution)

## How it works
- **ScreenshotManager:** Captures bitmaps via `OmniAccessibilityService.takeScreenshot`. Uses an 1100ms rate limit and caches the latest bitmap per `screenFingerprint` to avoid `ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT`. Downscales images wider than 1080 px and detects black screens for secure window protection.
- **ColorAnalyzer:** Samples pixel colors within element bounds (skipping boundary pixels to avoid stroke artifacts), converts to HSV color space, and assigns dominant named colors (RED, ORANGE, YELLOW, GREEN, BLUE, PURPLE, PINK, BROWN, BLACK, WHITE, GRAY). Enables queries like "the red button" and "open the blue thumbnail".
- **Bundled ML Kit OCR:** Analyzes bitmaps completely offline on-device without requiring Google Play Services model downloads. Merges text into accessibility elements missing text or creates new OCR-sourced `UiElement` nodes when the accessibility tree is text-sparse (< 5 text elements).
- **Set-of-Marks Renderer:** Renders color-coded bounding boxes with numbered badges (`#1`, `#2`, `#3`, etc.) onto screenshots for disambiguation.
- **VisualResolver:** Orchestrates visual resolution: parses natural language queries for color names or OCR matches; identifies candidates; and resolves to concrete element IDs or generates numbered options when ambiguous.

## Build status (commands + result)
- `./gradlew :app:assembleDebug` -> **BUILD SUCCESSFUL**
- `./gradlew testDebugUnitTest` -> **BUILD SUCCESSFUL**

## Tests (unit tests run + result)
- `ColorAnalyzerTest.testParseColorNames` -> **PASSED**
- All unit tests across all modules -> **PASSED**
