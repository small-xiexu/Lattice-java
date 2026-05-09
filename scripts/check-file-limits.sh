#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MAX_JAVA_LINES="${MAX_JAVA_LINES:-1000}"
MAX_JS_BYTES="${MAX_JS_BYTES:-50000}"

status=0

java_over_limit="$(
  find "$ROOT_DIR/src/main/java" -type f -name '*.java' -print0 \
    | xargs -0 wc -l \
    | awk -v max="$MAX_JAVA_LINES" '$2 != "total" && $1 > max {print $1 " " $2}'
)"

js_over_limit="$(
  find "$ROOT_DIR/src/main/resources/static/admin" -type f -name '*.js' -print0 \
    | xargs -0 wc -c \
    | awk -v max="$MAX_JS_BYTES" '$2 != "total" && $1 > max {print $1 " " $2}'
)"

if [[ -n "$java_over_limit" ]]; then
  echo "Java files over ${MAX_JAVA_LINES} lines:"
  echo "$java_over_limit"
  status=1
fi

if [[ -n "$js_over_limit" ]]; then
  echo "Admin JS files over ${MAX_JS_BYTES} bytes:"
  echo "$js_over_limit"
  status=1
fi

if [[ "$status" -eq 0 ]]; then
  echo "file limits ok"
fi

exit "$status"
