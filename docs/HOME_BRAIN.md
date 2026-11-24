# OmniControl — Home Brain Integration

Home Brain connects your Android phone to a high-powered home PC or local server (running Ollama or llama-server) over your local Wi-Fi network.

## Architecture
- **Protocol:** Local HTTP REST API (`/v1/chat/completions` or `/api/generate`) over LAN.
- **Privacy:** 100% local — zero data is sent to external clouds or third parties.
- **Failover:** If the PC is offline or out of Wi-Fi range, OmniControl seamlessly falls back to on-device models or the built-in fast path in under one step.

## Setup Instructions

1. **Start Ollama / llama-server on your PC:**
   ```bash
   ollama serve
   # or with llama-server
   ./llama-server -m Qwen2.5-7B-Instruct-Q4_K_M.gguf --host 0.0.0.0 --port 11434
   ```

2. **Configure in OmniControl App:**
   - Go to `Settings -> Home Brain`.
   - Enter your PC's local LAN IP (e.g. `http://192.168.1.50:11434`).
   - Toggle **Enable Home Brain**.
   - Tap **Test Connection** to verify ping and handshake.
