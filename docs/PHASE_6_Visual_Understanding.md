# Phase 6 — Visual Understanding (screenshots, OCR, visual matching, small VLM)

**Goal:** the agent can handle requests about what things **look like** ("the video with a car in the thumbnail", "the red button", "the blue icon") and screens where the accessibility tree has no text (games, Flutter/custom views, images with text).

**Prerequisite:** Phase 5 accepted.

**Principle:** cheapest method first. Tree → OCR → color stats → image-text matcher → VLM.

---

## Task 6.1 — ScreenshotManager

`suspend fun capture(): Bitmap?` in `:feature:vision` using the accessibility service's `takeScreenshot` (Phase 0 code, now production):
- **Rate limit:** at most one call per ~1.1 s (the system rejects faster calls with `ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT`). Cache the last bitmap with its `contentFp`; reuse if fp unchanged.
- **Hide our overlays** first if they overlap content: set bubble/pill/panel alpha 0 (or hide windows) → wait 1 frame (~32 ms) → capture → restore. Make sure the highlight layer has nothing drawn.
- Convert `HardwareBuffer` → software `Bitmap` (ARGB_8888), close buffer. Downscale copy to max 1080 px width for ML.
- **Secure detection:** if > 98% of sampled pixels are pure black and the tree is empty/near-empty → `isSecure = true`.
- Errors: `ERROR_TAKE_SCREENSHOT_SECURE_WINDOW` → isSecure; `NO_ACCESSIBILITY_ACCESS`/`INVALID_DISPLAY` → log + return null.
- Screenshots stay in memory only; saved to disk only if `recordTraces` is on (then auto-deleted after 7 days).

## Task 6.2 — OCR + merge

- Default: **ML Kit Text Recognition v2, bundled model** (`com.google.mlkit:text-recognition` — the *bundled* artifact works offline without Play Services download; do not use the `play-services-mlkit-text-recognition` variant). Free, on-device. Record its license/terms in MODELS.md.
- Fully-open alternative (optional): PaddleOCR ONNX models (det + rec) via ONNX Runtime — only if the user prefers no Google libraries.
- When to run OCR: the tree has < 5 text elements on a visibly busy screen, or the agent asks for a label not found in the tree, or `read_screen` on an image/canvas/WebView with missing text.
- Merge: each OCR line → `UiElement(role=TEXT, source=OCR, box)`. If an OCR box lies inside a tree element without text → assign the text to that element instead. If inside no clickable element → still add (clicking will use a coordinate tap; mark `clickable=false` but "tap-able via gesture").
- OCR results cached per `contentFp`.

## Task 6.3 — Color analysis (no ML)

`ColorAnalyzer.dominant(bitmap, box): NamedColor` — sample pixels inside box (skip 15% border), convert to HSV, histogram hue buckets with saturation/value thresholds → name: red, orange, yellow, green, blue, purple, pink, brown, black, white, gray.
- Used for "the red button", "the blue thumbnail", "the green icon".
- Router/agent tool: `findByColor(colorName, roles)` → candidate elements ranked by color share.

## Task 6.4 — Image-text matcher (CLIP-style)

Purpose: rank thumbnails/images by similarity to a text ("a car", "a cat", "mountains", "a man wearing glasses").

1. **Choose a model** (record license): an OpenCLIP / MobileCLIP / SigLIP small model with a permissive license. Preferred: an OpenCLIP ViT-B/32 or MobileCLIP-S variant.
2. **Export on the Mac** (`tools/vision/export_clip.py`, Python venv):
   - Load model with `open_clip` (or HF `transformers`), export **image encoder** and **text encoder** separately to ONNX (opset 17), fixed input 224×224 (or the model's size), batch dynamic.
   - Quantize to INT8 (dynamic) or FP16 with `onnxruntime.quantization`; check accuracy vs FP32 on 10 images.
   - Save `tokenizer` files (BPE vocab + merges for CLIP / SentencePiece model for SigLIP).
   - Also write `tools/vision/reference_outputs.json`: embeddings for 3 sample images + 3 texts → used to validate the Android port.
3. **Runtime on Android:** ONNX Runtime Android.
   - ⚠️ sherpa-onnx already ships `libonnxruntime.so`. Two different copies conflict (TROUBLESHOOTING #9). Options in order: (a) use the **same onnxruntime version** as the sherpa-onnx release and `pickFirsts` the .so; (b) convert the CLIP model to **LiteRT (TFLite)** instead and use LiteRT runtime (no conflict). Decide in this task and document.
4. **Tokenizer in Kotlin:** implement the exact tokenizer (CLIP BPE: lowercase, regex split, byte-level BPE with merges, `<start_of_text>`/`<end_of_text>`, pad/truncate to 77). Unit test: Kotlin tokens == Python tokens for 10 sample strings. **This is where ports usually break — test it first.**
5. **Preprocessing:** resize shortest side → center crop → normalize with the model's mean/std, CHW float. Unit test vs Python on one image (max abs diff < 1e-3).
6. **Matching flow:**
   - Candidates = IMAGE / CARD / IMAGE_BUTTON elements in the main list with box ≥ 64×64 dp (for CARD, crop the largest image child box if known, else the top 60% of the card).
   - Crop from screenshot → batch encode (cache embeddings per element signature + contentFp).
   - Text query → prompt ensembling: "a photo of {q}", "a thumbnail showing {q}", "{q}" → average embedding.
   - Cosine similarity → softmax over candidates (temperature 100).
   - Confident if top prob ≥ 0.45 **and** top − second ≥ 0.15. Otherwise → clarification (6.7).
   - Performance target: 10 thumbnails < 500 ms.

## Task 6.5 — Set-of-Marks renderer

`SetOfMarks.render(bitmap, elements): Bitmap` — draws each candidate's box with a bright outline and a numbered label (white text on colored rounded rect, size scaled to box). Used for (a) VLM input, (b) clarification overlay ("Which one? Say a number").

## Task 6.6 — On-demand VLM (optional by RAM tier)

- Only if RAM tier ≥ 8 GB, or via Home Brain (preferred if available).
- On-device: llama.cpp multimodal (`mtmd` library in the pinned llama.cpp version) with a small VLM GGUF + its `mmproj` file (e.g. SmolVLM, a small Qwen-VL, or Gemma 3 4B/3n if supported by the pinned version — check the llama.cpp multimodal docs of that tag). Add JNI `describeImage(handle, imageBytes, prompt, grammar)`.
- Loaded **only when needed**, unloaded right after (or when the text LLM must be unloaded first to free RAM — implement a `ModelMemoryManager` that guarantees at most one large model at a time on ≤ 8 GB phones).
- Prompt: Set-of-Marks image + "Which numbered box best matches: '<query>'? Answer with the number only." + grammar `root ::= [1-9] [0-9]?`.
- Home Brain: send the SoM image (JPEG, 1024 px max) to an Ollama/llama-server VLM over LAN.

## Task 6.7 — Vision tools in the agent + clarification

Add vision to the agent as **internal tools** (not raw actions the LLM can misuse):
- Router/LLM can emit `click` with `text` like `visual:a car` → the Executor calls `VisualResolver.resolve("a car", obs)` → returns element id or ambiguity.
  (Implementation: add an optional `"visual"` type to the grammar: `{"type":"click_visual","text":"car in thumbnail"}`; validator turns it into `ClickVisual(query)`.)
- `VisualResolver` pipeline: parse query → color words → ColorAnalyzer; object words → CLIP matcher; text inside image → OCR; still ambiguous → VLM (if available) → still ambiguous → **ask user**: draw SoM numbers on the highlight layer and say "I see 3 possible videos. Which number?" → user says "two" → click.
- Also feed OCR-only elements into the normal observation when the tree is poor (6.2), so the LLM can click them by ID (executor taps coordinates).
- Record which layer resolved each request (for tuning).

---

## Acceptance checklist

- [ ] YouTube results: "open the video with a car in the thumbnail" → correct in ≥ 4/5 real tries (different searches).
- [ ] "Tap the red button" on a test screen with one red button → correct.
- [ ] "Open the one with the blue thumbnail" uses color analysis and works.
- [ ] Ambiguous query → numbered highlights + voice question → answer by number works.
- [ ] An app with text only inside images (or a game menu) → OCR text appears in the debug element list and can be clicked.
- [ ] Banking/secure screen → detected, handoff message.
- [ ] Kotlin tokenizer + preprocessing unit tests match Python reference.
- [ ] Measured: OCR time, CLIP time for 10 crops, VLM time (if used), RAM peak.

## Common problems
TROUBLESHOOTING.md #9 (onnxruntime conflict), #31 (screenshot interval error), #32 (black screenshots), #33 (CLIP results random → tokenizer/normalization), #28 (OOM with two models).
