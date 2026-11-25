#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MODEL_FILE="Qwen2.5-0.5B-Instruct-Q4_K_M.gguf"
MODEL_URL="https://huggingface.co/bartowski/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/${MODEL_FILE}"
MODEL_SHA256="6eb923e7d26e9cea28811e1a8e852009b21242fb157b26149d3b188f3a8c8653"
ASSET_DIR="$ROOT/app/src/main/assets"
DEST="$ASSET_DIR/educafit-qwen2.5-0.5b-q4km.gguf"
mkdir -p "$ASSET_DIR"
if [[ -s "$DEST" ]]; then
  echo "Modelo já existe: $DEST"
else
  echo "Baixando modelo GGUF (aprox. 398 MB)…"
  curl -L --fail --retry 3 --retry-delay 5 "$MODEL_URL" -o "$DEST"
fi
echo "$MODEL_SHA256  $DEST" | sha256sum -c -
echo "Modelo conferido: $DEST"
