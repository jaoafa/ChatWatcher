#!/bin/sh
# Fetch Silero VAD + all candidate ASR models into ./data/models (idempotent).
set -e
B=https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models
mkdir -p data/models && cd data/models
[ -f silero_vad.onnx ] || curl -fsSL -o silero_vad.onnx $B/silero_vad.onnx
fetch() { [ -d "$2" ] || { curl -fsSL "$B/$1.tar.bz2" | tar xj; [ "$1" = "$2" ] || mv "$1" "$2"; }; }
fetch sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17 sensevoice
fetch sherpa-onnx-zipformer-ja-en-reazonspeech-2025-01-17 sherpa-onnx-zipformer-ja-en-reazonspeech-2025-01-17
fetch sherpa-onnx-zipformer-ja-reazonspeech-2024-08-01 sherpa-onnx-zipformer-ja-reazonspeech-2024-08-01
fetch sherpa-onnx-qwen3-asr-0.6B-int8-2026-03-25 sherpa-onnx-qwen3-asr-0.6B-int8-2026-03-25
fetch sherpa-onnx-whisper-small sherpa-onnx-whisper-small
# extra candidates (benchmark)
fetch sherpa-onnx-nemo-parakeet-tdt_ctc-0.6b-ja-35000-int8 sherpa-onnx-nemo-parakeet-tdt_ctc-0.6b-ja-35000-int8
fetch sherpa-onnx-whisper-turbo sherpa-onnx-whisper-turbo
fetch sherpa-onnx-dolphin-small-ctc-multi-lang-int8-2025-04-02 sherpa-onnx-dolphin-small-ctc-multi-lang-int8-2025-04-02
