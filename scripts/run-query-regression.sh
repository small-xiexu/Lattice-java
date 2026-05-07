#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
NODE_BIN="${NODE_BIN:-node}"

cd "$ROOT_DIR"
"$NODE_BIN" scripts/run-query-regression.mjs "$@"
