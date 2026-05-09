#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="${1:-src/main/java/com/xbk/lattice/query}"
REPORT_FILE="${2:-}"

tmp_report="$(mktemp)"
trap 'rm -f "$tmp_report"' EXIT

append_section() {
    local title="$1"
    local pattern="$2"
    local tmp_matches
    tmp_matches="$(mktemp)"
    set +e
    scan_pattern "$pattern" > "$tmp_matches"
    local scan_status=$?
    set -e
    if [[ "$scan_status" -eq 127 ]]; then
        rm -f "$tmp_matches"
        exit "$scan_status"
    fi
    if [[ -s "$tmp_matches" ]]; then
        {
            printf '%s\n' "$title"
            cat "$tmp_matches"
            printf '\n'
        } >> "$tmp_report"
    fi
    rm -f "$tmp_matches"
}

scan_pattern() {
    local pattern="$1"
    if command -v rg >/dev/null 2>&1; then
        rg -n -P "$pattern" "$ROOT_DIR"
        return
    fi
    if command -v perl >/dev/null 2>&1; then
        find "$ROOT_DIR" -type f -name '*.java' -print0 \
            | xargs -0 perl -Mutf8 -ne 'BEGIN { $pattern = shift @ARGV } if (/$pattern/) { print "$ARGV:$.:$_" }' "$pattern"
        return
    fi
    printf 'redline scan requires rg or perl\n' >&2
    return 127
}

append_section "1. question.contains / question.matches 明确命中" 'question\.(contains|matches)\('
append_section "2. paragraph / answerMarkdown 中文 contains 命中" '(paragraph|answerMarkdown)\.contains\(".*\p{Han}'
append_section "3. answer 阶段 HTTP 方法补丁残留" 'ensureRequestedPathHttpMethods|findHttpMethodForPath|patchFirstRequestedPathWithMethod'
append_section "4. 历史红线关键词残留" '(question|paragraph|answerMarkdown)\.(contains|matches|split)\(".*(还是|承接方|链路分析|答案|结论)'

if [[ -s "$tmp_report" ]]; then
    if [[ -n "$REPORT_FILE" ]]; then
        mkdir -p "$(dirname "$REPORT_FILE")"
        cp "$tmp_report" "$REPORT_FILE"
    fi
    cat "$tmp_report"
    exit 1
fi

if [[ -n "$REPORT_FILE" ]]; then
    mkdir -p "$(dirname "$REPORT_FILE")"
    printf '红线扫描通过：零命中\n' > "$REPORT_FILE"
fi

printf '红线扫描通过：零命中\n'
