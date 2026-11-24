# OmniControl — Models & Specifications

All model files reside on the phone in `context.filesDir/models/<subfolder>/`.
Never bundled in `assets/` to ensure small APK size and fast updates.

## 1. Supported Model Matrix

| Subsystem | Model | File(s) | Size | License | Source / URL | Notes |
|---|---|---|---|---|---|---|
| ASR | Streaming Zipformer English (20M) | `encoder.onnx`, `decoder.onnx`, `joiner.onnx`, `tokens.txt` | ~60 MB | Apache-2.0 | `k2-fsa/sherpa-onnx` | Fast, low latency streaming transcription |
| VAD | Silero VAD | `silero_vad.onnx` | ~1.5 MB | MIT | `snakers4/silero-vad` | Accurate voice activity detection |
| KWS | Sherpa-ONNX Keyword Spotter | `tokens.txt`, keyword table | < 1 MB | Apache-2.0 | `k2-fsa/sherpa-onnx` | Detects "stop", "cancel", "done" |
| LLM | Qwen 2.5 1.5B Instruct Q4_K_M | `qwen2.5-1.5b-instruct-q4_k_m.gguf` | ~1.1 GB | Apache-2.0 | HuggingFace | Low RAM tier (≤ 6 GB devices) |
| LLM | Qwen 2.5 3B Instruct Q4_K_M | `qwen2.5-3b-instruct-q4_k_m.gguf` | ~2.0 GB | Apache-2.0 | HuggingFace | Medium RAM tier (8 GB devices) |
| LLM | Llama 3.2 3B Instruct Q4_K_M | `llama-3.2-3b-instruct-q4_k_m.gguf` | ~2.0 GB | Llama 3.2 Community | Meta / HuggingFace | High accuracy on action routing |

## 2. Installation to Device

Using the developer utility:
```bash
# Push VAD model
./tools/push_models.sh path/to/silero_vad.onnx vad

# Push ASR model directory
./tools/push_models.sh path/to/streaming-zipformer-en asr

# Push LLM GGUF model
./tools/push_models.sh path/to/qwen2.5-1.5b-instruct-q4_k_m.gguf llm
```
Or on Windows PowerShell:
```powershell
.\tools\push_models.ps1 -Src "path\to\model.gguf" -Subfolder "llm"
```
