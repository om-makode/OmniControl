#!/usr/bin/env bash
# Copies a .gguf model into OmniControl's private storage on a connected phone (debug builds only).
# Usage: ./tools/push_model.sh path/to/model.gguf
set -euo pipefail
MODEL="${1:?usage: push_model.sh model.gguf}"
PKG=com.omnicontrol.app
NAME=$(basename "$MODEL")
adb push "$MODEL" "/data/local/tmp/$NAME"
adb shell run-as "$PKG" mkdir -p files/models/llm
adb shell run-as "$PKG" cp "/data/local/tmp/$NAME" "files/models/llm/$NAME"
adb shell rm "/data/local/tmp/$NAME"
echo "Done. In OmniControl: AI brain > On-device model."
