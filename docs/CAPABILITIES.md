# OmniControl — Device Capabilities Matrix

Evaluation of phone capabilities and Android OS security policies for OmniControl.

| Feature | Status | Requirement / Constraints |
|---|---|---|
| Screen Reading (Accessibility Tree) | **Works** | Enabled via Accessibility Service (`canRetrieveWindowContent="true"`). Sideloaded APK requires "Allow restricted settings" on Android 13+. |
| Screenshot Capture | **Works** | `AccessibilityService.takeScreenshot()` supported on Android 11+ (API 30+). Fast hardware buffer capture without user consent popup. |
| Streaming Offline ASR | **Works** | Sherpa-ONNX streaming Zipformer on CPU (arm64-v8a). Low memory and zero network requirement. |
| Voice Activity Detection (VAD) | **Works** | Silero VAD on 100ms / 512-sample windows with fast speech start/stop triggers. |
| On-Device LLM Inference | **Works** | Llama.cpp native JNI runtime (`libomnillama.so`) supporting Q4_K_M quantized GGUF models. |
| Grammar Constrained JSON Output | **Works** | GBNF / schema grammar compiler enforces strict action output format. |
| Tap & Gesture Injection | **Works** | `AccessibilityService.dispatchGesture()` handles click, swipe, scroll, drag. |
| Background Microphone Recording | **Works with Limits** | Android 14+ restricts FGS microphone starts. Bubble tap initiates upgrade to `microphone` FGS with `TrampolineActivity` fallback if required by OEM. |
| Secure Screens (FLAG_SECURE) | **Not Possible** | Blocked by Android OS by design (banking apps, DRM players return black screenshot & empty tree). Safe handoff to user. |
| Lock Screen Operation | **Not Possible** | Blocked by design for security; operates once device is unlocked. |
| Background Clipboard Access | **Not Possible** | Blocked on Android 10+; assistant extracts onscreen text directly from UI tree instead. |
