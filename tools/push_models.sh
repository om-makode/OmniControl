#!/usr/bin/env bash
# Push models to phone app-private storage: files/models/<subfolder>/
set -e

SRC="$1"
SUBFOLDER="$2"
PKG="com.omnicontrol.app"

if [ -z "$SRC" ] || [ -z "$SUBFOLDER" ]; then
    echo "Usage: $0 <local-file-or-dir> <subfolder>"
    echo "Example: $0 models/silero_vad.onnx vad"
    exit 1
fi

TMP_DIR="/data/local/tmp"

if [ -f "$SRC" ]; then
    FILENAME=$(basename "$SRC")
    echo "Pushing file $FILENAME to $TMP_DIR..."
    adb push "$SRC" "$TMP_DIR/$FILENAME"
    echo "Moving to app storage files/models/$SUBFOLDER/..."
    adb shell "run-as $PKG mkdir -p files/models/$SUBFOLDER"
    adb shell "run-as $PKG cp $TMP_DIR/$FILENAME files/models/$SUBFOLDER/"
    adb shell "rm $TMP_DIR/$FILENAME"
    echo "Done! File installed at files/models/$SUBFOLDER/$FILENAME"
elif [ -d "$SRC" ]; then
    TAR_NAME="temp_models.tar"
    echo "Packing directory $SRC into $TAR_NAME..."
    tar -cf "$TAR_NAME" -C "$SRC" .
    adb push "$TAR_NAME" "$TMP_DIR/$TAR_NAME"
    rm "$TAR_NAME"
    echo "Extracting into app storage files/models/$SUBFOLDER/..."
    adb shell "run-as $PKG mkdir -p files/models/$SUBFOLDER"
    adb shell "run-as $PKG tar -xf $TMP_DIR/$TAR_NAME -C files/models/$SUBFOLDER"
    adb shell "rm $TMP_DIR/$TAR_NAME"
    echo "Done! Directory installed at files/models/$SUBFOLDER"
else
    echo "Error: $SRC is not a valid file or directory."
    exit 1
fi
