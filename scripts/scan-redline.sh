#!/usr/bin/env bash

set -euo pipefail

REPORT_FILE="${1:-}"
ROOTS=(
    "src/main/java/com/xbk/lattice/query"
    "src/main/java/com/xbk/lattice/compiler"
    "src/main/java/com/xbk/lattice/article"
    "src/main/java/com/xbk/lattice/source"
)
FOCUS_FILE_REGEX='(Fallback|Intent|Reranker|Grounding|Lookup|PostProcessor|Prompt|Rewrite)'
REPORT_BLOCKER_COUNT=0
REPORT_REVIEW_COUNT=0
REPORT_ALLOWLIST_COUNT=0
FAIL_ON_REVIEW="${REDLINE_FAIL_ON_REVIEW:-0}"
FAIL_ON_ALLOWLIST="${REDLINE_FAIL_ON_ALLOWLIST:-0}"

if ! command -v rg >/dev/null 2>&1; then
    printf 'redline scan requires rg\n' >&2
    exit 127
fi

tmp_raw="$(mktemp)"
tmp_hits="$(mktemp)"
tmp_report="$(mktemp)"
trap 'rm -f "$tmp_raw" "$tmp_hits" "$tmp_report"' EXIT

run_rule() {
    local label="$1"
    local pattern="$2"
    local result

    while IFS= read -r result; do
        [[ -z "$result" ]] && continue
        local file line snippet
        IFS=: read -r file line snippet <<< "$result"
        [[ -z "${file:-}" || -z "${line:-}" ]] && continue
        printf '%s\t%s\t%s\t%s\n' "$file" "$line" "$label" "${snippet:-}" >> "$tmp_raw"
    done < <(rg -n -P --no-heading --color never -g '*.java' -e "$pattern" "${ROOTS[@]}" || true)
}

run_query_question_predicate_block_rule() {
    local file

    while IFS= read -r file; do
        awk -v file="$file" '
            function has_question_predicate(value) {
                return value ~ /(normalizedQuestion|question)/ &&
                    value ~ /(containsAny[[:space:]]*\(|\.(contains|matches|equals|equalsIgnoreCase)[[:space:]]*\()/
            }

            function has_redline_token(value) {
                return value ~ /"[^"]*[一-龥][^"]*"/ ||
                    value ~ /(样例|期望|答案|结论|expected|answer|conclusion|sample|example|fixture|regression|case)/
            }

            function should_start_block(value) {
                return value ~ /containsAny[[:space:]]*\(/ ||
                    value ~ /(normalizedQuestion|question).*\\.(contains|matches|equals|equalsIgnoreCase)[[:space:]]*\(/ ||
                    value ~ /\\.(contains|matches|equals|equalsIgnoreCase)[[:space:]]*\(.*(normalizedQuestion|question)/
            }

            function should_end_block(value) {
                return value ~ /\)[[:space:]]*\{?[[:space:]]*$/ ||
                    value ~ /\);[[:space:]]*$/ ||
                    value ~ /;[[:space:]]*$/
            }

            function emit_if_redline() {
                if (has_question_predicate(block) && has_redline_token(block)) {
                    gsub(/[[:space:]]+/, " ", block)
                    print file "\t" start_line "\tQuery question predicate block\t" block
                }
            }

            in_block == 1 {
                block = block " " $0
                if ($0 ~ /\)[[:space:]]*\{?[[:space:]]*$/ || $0 ~ /\);[[:space:]]*$/) {
                    emit_if_redline()
                    in_block = 0
                    block = ""
                }
                next
            }

            should_start_block($0) {
                start_line = NR
                block = $0
                if (should_end_block($0)) {
                    emit_if_redline()
                    block = ""
                } else {
                    in_block = 1
                }
            }
        ' "$file" >> "$tmp_raw"
    done < <(rg --files "${ROOTS[@]}" | rg '\.java$')
}

aggregate_hits() {
    sort -t "$(printf '\t')" -k1,1 -k2,2n "$tmp_raw" \
        | awk -F '\t' '
            BEGIN { OFS = FS }
            {
                key = $1 FS $2
                if (!(key in seen)) {
                    seen[key] = 1
                    order[++count] = key
                    file[key] = $1
                    line[key] = $2
                    snippet[key] = $4
                }
                if (rules[key] == "") {
                    rules[key] = $3
                } else if (index("," rules[key] ",", "," $3 ",") == 0) {
                    rules[key] = rules[key] ", " $3
                }
            }
            END {
                for (i = 1; i <= count; i++) {
                    key = order[i]
                    print file[key], line[key], rules[key], snippet[key]
                }
            }
        ' > "$tmp_hits"
}

symbol_for_line() {
    local file="$1"
    local class_name="${file##*/}"
    printf '%s\n' "${class_name%.java}"
}

contains_han_literal() {
    local text="$1"
    [[ "$text" =~ \"[^\"]*[一-龥][^\"]*\" ]]
}

has_han_text() {
    local text="$1"
    [[ "$text" =~ [一-龥] ]]
}

is_allowlist_candidate() {
    local text="$1"

    [[ "$text" =~ 路径|URL|url|endpoint|uri|http|https|json|JSON|markdown|Markdown|table|Table|表格|数字|编号|编码|标识|代码|class|method|controller|service|repository|provider|Provider|model|Model|status|Status|state|State|scene|Scene|role|Role|format|Format|parser|Parser|extract|Extract|node|Node|graph|Graph|mime|Mime|extension|Extension|\.java|\.yaml|\.yml|\.properties|\.md|\.json|\.pdf|\.docx|\.xlsx|\.pptx ]] \
        || [[ "$text" == *"|---|" ]] \
        || [[ "$text" == *"| ---"* ]] \
        || [[ "$text" == *"|:---"* ]]
}

is_query_path() {
    local file="$1"
    [[ "$file" == src/main/java/com/xbk/lattice/query/* ]]
}

has_metadata_name() {
    local snippet="$1"
    [[ "$snippet" == *"fileName"* ||
        "$snippet" == *"sourceName"* ||
        "$snippet" == *"sourceTitle"* ||
        "$snippet" == *"documentTitle"* ]]
}

has_predicate_rule() {
    local rules="$1"
    [[ "$rules" == *"containsAny"* ||
        "$rules" == *".contains("* ||
        "$rules" == *".matches("* ||
        "$rules" == *".equals("* ||
        "$rules" == *".equalsIgnoreCase("* ||
        "$rules" == *"具体问题/文件/文档/术语分支"* ]]
}

has_specific_literal_signal() {
    local snippet="$1"
    contains_han_literal "$snippet" ||
        [[ "$snippet" =~ (样例|期望|答案|结论|expected|answer|conclusion|sample|example|fixture|regression|test|case|golden|baseline|snapshot|测试) ]]
}

has_content_text_subject() {
    local snippet="$1"
    [[ "$snippet" =~ (normalizedQuestion|question|normalizedLine|paragraph|answerMarkdown|currentAnswer|sourceTitle|documentTitle|fileName|sourceName|标题) ]] ||
        [[ "$snippet" =~ (^|[^[:alnum:]_])(line|content|heading|title|term|keyword)([^[:alnum:]_]|$) ]]
}

has_forbidden_content_literal_signal() {
    local snippet="$1"
    [[ "$snippet" =~ (业务|场景|方案|答案|结论|期望|样例|回归|承接方|链路|差异|区别|为什么|原因|关键差异|权威|为准|统一以|不含|分裂|修正|合并|删除|移除|改为|状态|命中数|归属|对应|是否一致|是否生效|是否启用|第几|哪一|批|阶段|承接|责任|影响|排查|调用链|策略|配置|规范|规则) ]] ||
        [[ "$snippet" =~ (expected|answer|conclusion|sample|example|fixture|regression|golden|baseline|snapshot|business[[:space:]_-]*conclusion|special[[:space:]_-]*case|fixed[[:space:]_-]*answer) ]]
}

is_general_structure_literal() {
    local snippet="$1"
    [[ "$snippet" =~ (URL|url|uri|endpoint|http|https|json|JSON|markdown|Markdown|table|Table|表格|数字|编号|代码|文件后缀|后缀|扩展名|format|Format|mime|Mime|extension|Extension|代码块|标题层级|列表|目录|页码|行号|路径格式|\.java|\.yaml|\.yml|\.properties|\.md|\.json|\.pdf|\.docx|\.xlsx|\.pptx) ]] ||
        [[ "$snippet" =~ \\\\d|\\\\b|\`\`\`|\-\-\-|\|\-\-\-|\-\>|=\  ]]
}

is_specific_content_predicate() {
    local rules="$1"
    local snippet="$2"

    has_predicate_rule "$rules" || return 1
    has_content_text_subject "$snippet" || return 1
    has_forbidden_content_literal_signal "$snippet" || return 1
    is_general_structure_literal "$snippet" && return 1
    return 0
}

is_comment_line() {
    local snippet="$1"
    snippet="${snippet#"${snippet%%[![:space:]]*}"}"
    [[ "$snippet" == "/*"* || "$snippet" == "*"* || "$snippet" == "//"* ]]
}

extract_return_literal() {
    local snippet="$1"
    if [[ "$snippet" =~ return[[:space:]]+\"([^\"]+)\"[[:space:]]*\; ]]; then
        printf '%s' "${BASH_REMATCH[1]}"
    fi
}

is_engineering_literal() {
    local literal="$1"
    local file="$2"
    local snippet="$3"
    local context="$file $snippet"

    [[ -z "$literal" ]] && return 1

    if [[ "$context" =~ (provider|Provider|model|Model|status|Status|state|State|scene|Scene|role|Role|format|Format|parser|Parser|extract|Extract|node|Node|graph|Graph|mime|Mime|extension|Extension) ]]; then
        return 0
    fi

    if [[ "$literal" =~ ^(SUCCEEDED|FAILED|RUNNING|PENDING|COMPLETED|SKIPPED|SKIPPED_[A-Z0-9_]+|DISABLED|ARCHIVED|ACTIVE|INACTIVE|PASSED|REJECTED|HIGH|MEDIUM|LOW)$ ]]; then
        return 0
    fi

    if [[ "$file" == *"/graph/"* && "$literal" =~ ^[A-Za-z0-9_.:/-]+$ ]]; then
        return 0
    fi

    if [[ "$file" == *"RawSource.java" &&
        "$literal" =~ ^(pdf_text|office_extract|text_read|placeholder|pdfbox|poi_xwpf|poi_hwpf|poi_excel|poi_ppt|ocr|image|plain_text)$ ]]; then
        return 0
    fi

    return 1
}

is_query_question_predicate() {
    local file="$1"
    local rules="$2"
    local snippet="$3"

    is_query_path "$file" || return 1
    [[ "$rules" == *"Query question predicate block"* ]] && return 0
    [[ "$snippet" == *"normalizedQuestion"* || "$snippet" == *"question"* ]] || return 1
    [[ "$rules" == *"containsAny"* || "$rules" == *".contains("* || "$rules" == *".matches("* || "$rules" == *".equals("* || "$rules" == *".equalsIgnoreCase("* ]] || return 1
    has_han_text "$snippet" || [[ "$snippet" =~ (答案|结论|状态|命中|分别|是否|哪个|为什么|关键差异|路径是什么|样例|example|answer|conclusion) ]]
}

is_fallback_blocker() {
    local file="$1"
    local rules="$2"
    local snippet="$3"

    is_query_path "$file" || return 1
    [[ "$file" == *"Fallback"* || "$snippet" == *"fallback"* || "$snippet" == *"Fallback"* || "$snippet" == *"兜底"* ]] || return 1
    is_comment_line "$snippet" && return 1
    [[ "$snippet" =~ (buildFallbackMarkdown|buildFallbackRevisionMarkdown|buildDeterministicFallbackPayload|appendFallbackConclusion|buildFallbackConclusionLines|markdownBuilder\.append|return[[:space:]]+markdownBuilder|answerMarkdown[[:space:]]*=[^=]|currentAnswer[[:space:]]*=[^=]|强制替换|确定性.*(答案|结论)|业务结论) ]] && return 0
    if [[ "$rules" == *"固定答案 return"* ]] && [[ ! "$(extract_return_literal "$snippet")" =~ ^[A-Za-z0-9_.:/-]+$ ]]; then
        return 0
    fi
    if [[ "$rules" == *"固定答案 append"* ]] && has_han_text "$snippet"; then
        return 0
    fi
    return 1
}

classify_hit() {
    local file="$1"
    local rules="$2"
    local snippet="$3"
    local risk="中"
    local risk_type="REVIEW"
    local category="术语特判"
    local recommendation="保留但需人工确认"
    local human="是"
    local reason="命中疑似红线模式，需要人工复核"
    local literal

    literal="$(extract_return_literal "$snippet")"

    if is_allowlist_candidate "$snippet"; then
        risk="低"
        risk_type="ALLOWLIST"
        category="allowlist candidate"
        recommendation="保留但需人工确认"
        reason="路径、URL、文件格式、数字、Markdown、JSON、表格或工程标识等通用解析候选"
    fi

    if [[ "$file" == *"/compiler/"* && "$risk_type" != "ALLOWLIST" ]]; then
        category="编译补丁"
        recommendation="迁移到 compiler/config"
        reason="编译链路命中，默认进入人工复核或配置迁移候选"
    fi

    if is_engineering_literal "$literal" "$file" "$snippet"; then
        risk="低"
        risk_type="ALLOWLIST"
        category="allowlist candidate"
        recommendation="保留但需人工确认"
        reason="工程枚举、provider 名称、Graph 节点 ID、状态常量或文件格式解析返回值，不按固定答案阻断"
    fi

    if is_query_question_predicate "$file" "$rules" "$snippet"; then
        risk="高"
        risk_type="BLOCKER"
        category="查询路由补丁"
        recommendation="迁移到 eval/bad_cases.jsonl"
        reason="Query 主链中 normalizedQuestion/question 通过 contains/containsAny/matches/equals 命中中文业务词、样例词或答案词"
    fi

    if is_specific_content_predicate "$rules" "$snippet"; then
        risk="高"
        risk_type="BLOCKER"
        if is_query_path "$file"; then
            category="查询内容特判"
            recommendation="删除；必要时迁移到 eval/bad_cases.jsonl、config/synonyms.yaml 或通用证据排序"
            reason="Query 主链中问题、正文、答案或元数据文本通过 contains/matches/equals 命中非通用结构的中文业务/样例/结论信号"
        elif [[ "$file" == src/main/java/com/xbk/lattice/compiler/* ]]; then
            category="编译内容特判"
            recommendation="迁移到 compiler/config 或通用抽取规则；不得在编译链路硬编码资料结论词"
            reason="编译链路中正文、标题或关键词判断命中非通用结构的中文业务/结论信号，可能把具体资料答案写进生成规则"
        else
            category="内容特判"
            recommendation="删除或迁移到可审查配置；保留前必须人工确认"
            reason="内容文本判断命中非通用结构的中文业务/样例/结论信号"
        fi
    fi

    if [[ "$file" == *"Fallback"* || "$snippet" == *"兜底"* || "$snippet" == *"fallback"* || "$snippet" == *"Fallback"* ]]; then
        if is_fallback_blocker "$file" "$rules" "$snippet"; then
            risk="高"
            risk_type="BLOCKER"
            category="固定兜底"
            recommendation="删除"
            reason="fallback 代码生成确定性业务结论、返回固定答案或强制替换 answer"
        elif [[ "$risk_type" != "BLOCKER" ]]; then
            risk="中"
            risk_type="REVIEW"
            category="固定兜底"
            recommendation="保留但需人工确认"
            reason="fallback 变量名、参数名、注释或说明类命中，最多进入人工复核"
        fi
    fi

    if [[ "$rules" == *"强制替换 answer"* ]] && is_query_path "$file" && [[ "$risk_type" != "BLOCKER" ]]; then
        risk="中"
        risk_type="REVIEW"
        category="查询路由补丁"
        recommendation="保留但需人工确认"
        reason="answer 赋值或修复命中；未满足 fallback BLOCKER 条件，但需要人工确认是否会强制替换回答"
    fi

    if [[ "$rules" == *"固定答案 return"* || "$rules" == *"中文业务文案 return"* || "$rules" == *"固定答案 append"* || "$snippet" == *"固定答案"* ]]; then
        if [[ "$risk_type" != "BLOCKER" && -n "$literal" ]] && is_engineering_literal "$literal" "$file" "$snippet"; then
            risk="低"
            risk_type="ALLOWLIST"
            category="allowlist candidate"
            recommendation="保留但需人工确认"
            reason="固定字符串属于工程枚举/provider/状态/格式解析返回值，不按固定答案阻断"
        elif is_query_path "$file" && has_han_text "$snippet"; then
            risk="高"
            risk_type="BLOCKER"
            category="固定答案"
            recommendation="删除"
            reason="Query 主链返回或拼接中文固定答案/兜底文案"
        elif [[ "$risk_type" != "BLOCKER" ]]; then
            risk="中"
            risk_type="REVIEW"
            category="固定答案"
            recommendation="保留但需人工确认"
            reason="固定字符串命中但未确认是回答模板，进入人工复核"
        fi
    fi

    if has_metadata_name "$snippet"; then
        if has_predicate_rule "$rules" && has_specific_literal_signal "$snippet"; then
            risk="高"
            risk_type="BLOCKER"
            if [[ "$snippet" == *"documentTitle"* || "$snippet" == *"sourceTitle"* ]]; then
                category="文档名特判"
            else
                category="文件名特判"
            fi
            recommendation="迁移到 kb/sources"
            reason="metadata 名称与 contains/equals/matches/containsAny 结合，并比较中文字符串、样例词、测试文件名或 expected answer 词"
        elif [[ "$risk_type" != "BLOCKER" ]]; then
            risk="中"
            risk_type="REVIEW"
            if [[ "$snippet" == *"documentTitle"* || "$snippet" == *"sourceTitle"* ]]; then
                category="文档名特判"
            else
                category="文件名特判"
            fi
            recommendation="保留但需人工确认"
            reason="metadata 读取、空值判断、后缀判断或 source 字段传递命中，未达到 BLOCKER 条件"
        fi
    fi

    if contains_han_literal "$snippet" && [[ "$category" == "术语特判" && "$risk_type" != "BLOCKER" ]]; then
        risk="中"
        risk_type="REVIEW"
        category="业务域特判"
        recommendation="迁移到 config/synonyms.yaml"
        reason="中文业务词或术语命中，但未满足 BLOCKER 条件，需人工确认是否迁移"
    fi

    if [[ "$snippet" == *"normalizedQuestion"* || "$snippet" == *"question"* ]] && [[ "$risk_type" == "ALLOWLIST" ]]; then
        recommendation="保留但需人工确认"
        reason="问题变量参与通用结构判断，作为 allowlist candidate 暴露给人工确认"
    fi

    HIT_RISK="$risk"
    HIT_RISK_TYPE="$risk_type"
    HIT_CATEGORY="$category"
    HIT_RECOMMENDATION="$recommendation"
    HIT_HUMAN="$human"
    HIT_REASON="$reason"
}

escape_markdown_cell() {
    local value="$1"
    value="${value//$'\t'/ }"
    value="${value//$'\r'/ }"
    value="${value//$'\n'/ }"
    value="${value//\\/\\\\}"
    value="${value//|/\\|}"
    value="${value//\`/\\\`}"
    while [[ "$value" == *"  "* ]]; do
        value="${value//  / }"
    done
    value="${value#"${value%%[![:space:]]*}"}"
    value="${value%"${value##*[![:space:]]}"}"
    printf '%s' "${value:0:260}"
}

write_report() {
    local high_count medium_count low_count blocker_count review_count allowlist_count total_count
    total_count="$(wc -l < "$tmp_hits" | tr -d ' ')"
    high_count=0
    medium_count=0
    low_count=0
    blocker_count=0
    review_count=0
    allowlist_count=0

    {
        printf '# Special Cases Report\n\n'
        printf '%s\n' "- 扫描时间：$(date '+%Y-%m-%d %H:%M:%S %z')"
        printf '%s\n' "- 扫描范围：\`${ROOTS[*]}\`"
        printf '%s\n' "- 重点文件名：\`$FOCUS_FILE_REGEX\`"
        printf '%s\n' '- risk_type：`BLOCKER` 会导致脚本返回 1；`REVIEW` 只进入人工复核；`ALLOWLIST` 是通用解析或工程常量候选。'
        printf '%s\n' '- 注意：`REVIEW` 和 `ALLOWLIST` 都不是自动放行结论，只是不同强度的人工确认候选。'
        printf '%s\n\n' '- 说明：路径、URL、文件后缀、数字、Markdown、JSON、表格，以及明确工程上下文中的 provider/model/status/scene/role/format/parser/extract/node/graph/mime/extension 等不静默放过，只标记为 `ALLOWLIST`。'
        printf '| 文件路径 | 函数名或类名 | 命中的代码片段 | 命中的红线规则 | risk_type | reason | 风险等级 | 问题分类 | 推荐处理方式 | 是否需要人工确认 |\n'
        printf '|---|---|---|---|---|---|---|---|---|---|\n'
    } > "$tmp_report"

    while IFS=$'\t' read -r file line rules snippet; do
        local symbol risk risk_type category recommendation human reason
        symbol="$(symbol_for_line "$file" "$line")"
        classify_hit "$file" "$rules" "$snippet"
        risk="$HIT_RISK"
        risk_type="$HIT_RISK_TYPE"
        category="$HIT_CATEGORY"
        recommendation="$HIT_RECOMMENDATION"
        human="$HIT_HUMAN"
        reason="$HIT_REASON"
        case "$risk" in
            高) high_count=$((high_count + 1)) ;;
            中) medium_count=$((medium_count + 1)) ;;
            低) low_count=$((low_count + 1)) ;;
        esac
        case "$risk_type" in
            BLOCKER) blocker_count=$((blocker_count + 1)) ;;
            REVIEW) review_count=$((review_count + 1)) ;;
            ALLOWLIST) allowlist_count=$((allowlist_count + 1)) ;;
        esac
        printf '| `%s:%s` | `%s` | `%s` | `%s` | %s | `%s` | %s | %s | %s | %s |\n' \
            "$(escape_markdown_cell "$file")" \
            "$line" \
            "$(escape_markdown_cell "$symbol")" \
            "$(escape_markdown_cell "$snippet")" \
            "$(escape_markdown_cell "$rules")" \
            "$risk_type" \
            "$(escape_markdown_cell "$reason")" \
            "$risk" \
            "$category" \
            "$recommendation" \
            "$human" >> "$tmp_report"
    done < "$tmp_hits"

    {
        printf '\n## 汇总\n\n'
        printf '%s\n' "- 总命中：$total_count"
        printf '%s\n' "- 高风险：$high_count"
        printf '%s\n' "- 中风险：$medium_count"
        printf '%s\n' "- 低风险：$low_count"
        printf '%s\n' "- BLOCKER：$blocker_count"
        printf '%s\n' "- REVIEW：$review_count"
        printf '%s\n' "- ALLOWLIST：$allowlist_count"
    } >> "$tmp_report"

    REPORT_BLOCKER_COUNT="$blocker_count"
    REPORT_REVIEW_COUNT="$review_count"
    REPORT_ALLOWLIST_COUNT="$allowlist_count"
}

run_rule "containsAny" 'containsAny[[:space:]]*\('
run_rule ".contains(" '\.contains[[:space:]]*\('
run_rule ".matches(" '\.matches[[:space:]]*\('
run_rule ".equals(" '\.equals[[:space:]]*\('
run_rule ".equalsIgnoreCase(" '\.equalsIgnoreCase[[:space:]]*\('
run_rule "switch / case" '^[[:space:]]*(switch|case)\b'
run_rule "固定答案 return" 'return[[:space:]]+"[^"]{1,240}"[[:space:]]*;'
run_rule "中文业务文案 return" 'return[[:space:]]+"[^"]*\p{Han}[^"]*"[[:space:]]*;'
run_rule "固定答案 append" '\.append[[:space:]]*\("[^"]*\p{Han}[^"]*"\)'
run_rule "白名单" '白名单|whitelist|allowlist'
run_rule "强制命中" '强制命中|forced[[:space:]_-]*hit|force[[:space:]_-]*hit'
run_rule "特判" '特判|special[[:space:]_-]*case'
run_rule "兜底" '兜底|fallback|Fallback'
run_rule "固定答案" '固定答案|fixed[[:space:]_-]*answer'
run_rule "fallback 中生成确定性业务结论" '(fallback|Fallback|兜底).*(确定性|业务结论|business[[:space:]_-]*conclusion)|确定性.*(fallback|Fallback|兜底)'
run_rule "具体问题/文件/文档/术语分支" '(normalizedQuestion|question|fileName|sourceName|sourceTitle|documentTitle|term|keyword|标题).*(contains|containsAny|matches|equals|equalsIgnoreCase)[[:space:]]*\('
run_rule "强制替换 answer" '(answerMarkdown|currentAnswer|answer)[[:space:]]*=[^=]'
run_query_question_predicate_block_rule

aggregate_hits
write_report

if [[ -n "$REPORT_FILE" ]]; then
    mkdir -p "$(dirname "$REPORT_FILE")"
    cp "$tmp_report" "$REPORT_FILE"
    chmod 0644 "$REPORT_FILE"
else
    cat "$tmp_report"
fi

if [[ "$REPORT_BLOCKER_COUNT" -gt 0 ]]; then
    exit 1
fi

if [[ "$FAIL_ON_REVIEW" == "1" && "$REPORT_REVIEW_COUNT" -gt 0 ]]; then
    exit 1
fi

if [[ "$FAIL_ON_ALLOWLIST" == "1" && "$REPORT_ALLOWLIST_COUNT" -gt 0 ]]; then
    exit 1
fi
