package com.xbk.lattice.query.citation;

import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.FactCardTerminalUnitJdbcRepository;
import com.xbk.lattice.infra.persistence.FactCardTerminalUnitRecord;
import com.xbk.lattice.infra.persistence.SourceFileJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileRecord;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Citation 校验器
 *
 * 职责：对单条引用执行规则校验，并输出重叠分与命中摘录
 *
 * @author xiexu
 */
@Component
public class CitationValidator {

    private static final Pattern NUMERIC_LITERAL_PATTERN = Pattern.compile("\\b(\\d+(?:\\.\\d+)?)\\b");

    private static final Pattern SNAKE_CASE_PATTERN = Pattern.compile("\\b([a-z][a-z0-9]*(?:_[a-z0-9]+){1,})\\b");

    private static final Pattern FQN_PATTERN = Pattern.compile("\\b(com(?:\\.[A-Za-z_][\\w$]*){2,})\\b");

    private static final Pattern HTTP_PATH_PATTERN = Pattern.compile("(/[-A-Za-z0-9_./]+)");

    private static final Pattern JAVA_SYMBOL_PATTERN = Pattern.compile(
            "\\b([A-Z][A-Za-z0-9]*(?:Mapper|Service|ServiceImpl|Impl|Controller|Dao))\\b"
    );

    private static final Pattern LATIN_TERM_PATTERN = Pattern.compile(
            "(?<![A-Za-z0-9_./-])([A-Za-z][-A-Za-z0-9./]{2,})(?![A-Za-z0-9_./-])"
    );

    private static final Pattern HAN_TERM_PATTERN = Pattern.compile("([\\p{IsHan}]{3,})");

    private final ArticleJdbcRepository articleJdbcRepository;

    private final SourceFileJdbcRepository sourceFileJdbcRepository;

    private final FactCardTerminalUnitJdbcRepository factCardTerminalUnitJdbcRepository;

    private final QueryTraceManager traceManager;

    /**
     * 创建 Citation 校验器。
     *
     * @param articleJdbcRepository 文章仓储
     * @param sourceFileJdbcRepository 源文件仓储
     * @param factCardTerminalUnitJdbcRepository 终端字段证据单元仓储
     * @param traceManager trace 管理器
     */
    public CitationValidator(
            ArticleJdbcRepository articleJdbcRepository,
            SourceFileJdbcRepository sourceFileJdbcRepository,
            FactCardTerminalUnitJdbcRepository factCardTerminalUnitJdbcRepository,
            QueryTraceManager traceManager
    ) {
        this.articleJdbcRepository = articleJdbcRepository;
        this.sourceFileJdbcRepository = sourceFileJdbcRepository;
        this.factCardTerminalUnitJdbcRepository = factCardTerminalUnitJdbcRepository;
        this.traceManager = traceManager;
    }

    /**
     * 校验单条引用。
     *
     * @param citation 引用
     * @return 校验结果
     */
    public CitationValidationResult validate(Citation citation) {
        if (citation == null) {
            return new CitationValidationResult(null, null, CitationValidationStatus.DEMOTED, 0.0D, "citation_missing", "", -1);
        }
        if (citation.getSourceType() == null) {
            return new CitationValidationResult(
                    citation.getTargetKey(),
                    null,
                    CitationValidationStatus.DEMOTED,
                    0.0D,
                    "unsupported_source_type",
                    "",
                    citation.getOrdinal()
            );
        }
        if (isBlank(citation.getTargetKey())) {
            return new CitationValidationResult(
                    citation.getTargetKey(),
                    citation.getSourceType(),
                    CitationValidationStatus.DEMOTED,
                    0.0D,
                    "target_key_missing",
                    "",
                    citation.getOrdinal()
            );
        }
        List<String> hardFactTokens = extractHardFactTokens(citation.getClaimText());
        if (hardFactTokens.isEmpty()) {
            return new CitationValidationResult(
                    citation.getTargetKey(),
                    citation.getSourceType(),
                    CitationValidationStatus.SKIPPED,
                    0.0D,
                    "no_hard_fact_literals",
                    "",
                    citation.getOrdinal()
            );
        }
        CitationValidationResult result = validateWithHardFacts(citation, hardFactTokens);
        emitValidationTrace(citation, result, hardFactTokens);
        return result;
    }

    private CitationValidationResult validateWithHardFacts(Citation citation, List<String> hardFactTokens) {
        if (citation.getSourceType() == CitationSourceType.SOURCE_FILE) {
            if (sourceFileJdbcRepository == null) {
                return new CitationValidationResult(
                        citation.getTargetKey(),
                        citation.getSourceType(),
                        CitationValidationStatus.DEMOTED,
                        0.0D,
                        "source_file_repository_unavailable",
                        "",
                        citation.getOrdinal()
                );
            }
            SourceFileRecord sourceFileRecord = sourceFileJdbcRepository.findByPath(citation.getTargetKey()).orElse(null);
            if (sourceFileRecord == null) {
                return new CitationValidationResult(
                        citation.getTargetKey(),
                        citation.getSourceType(),
                        CitationValidationStatus.NOT_FOUND,
                        0.0D,
                        "source_file_not_found",
                        "",
                        citation.getOrdinal()
                );
            }
            CitationValidationResult terminalUnitResult = validateAgainstTerminalUnitEvidence(
                    sourceFileRecord, hardFactTokens, citation);
            if (terminalUnitResult != null) {
                return terminalUnitResult;
            }
            double overlapScore = calculateOverlapScore(hardFactTokens, sourceFileRecord.getContentText());
            if (hasDirectEvidenceLineMatch(citation.getClaimText(), sourceFileRecord.getContentText())) {
                return new CitationValidationResult(
                        citation.getTargetKey(),
                        citation.getSourceType(),
                        CitationValidationStatus.VERIFIED,
                        Math.max(overlapScore, 1.0D),
                        "source_direct_line_match_verified",
                        extractMatchedExcerpt(sourceFileRecord.getContentText(), hardFactTokens),
                        citation.getOrdinal()
                );
            }
            if (overlapScore >= 1.0D) {
                return new CitationValidationResult(
                        citation.getTargetKey(),
                        citation.getSourceType(),
                        CitationValidationStatus.VERIFIED,
                        overlapScore,
                        "source_rule_overlap_verified",
                        extractMatchedExcerpt(sourceFileRecord.getContentText(), hardFactTokens),
                        citation.getOrdinal()
                );
            }
            if (isHighConfidencePartialOverlap(hardFactTokens, overlapScore)) {
                return new CitationValidationResult(
                        citation.getTargetKey(),
                        citation.getSourceType(),
                        CitationValidationStatus.VERIFIED,
                        overlapScore,
                        "source_near_complete_overlap_verified",
                        extractMatchedExcerpt(sourceFileRecord.getContentText(), hardFactTokens),
                        citation.getOrdinal()
                );
            }
            CitationValidationResult contextValidationResult = validateAgainstContextWindow(
                    citation,
                    sourceFileRecord.getContentText(),
                    hardFactTokens,
                    overlapScore,
                    "source_context_overlap_verified"
            );
            if (contextValidationResult != null) {
                return contextValidationResult;
            }
            return new CitationValidationResult(
                    citation.getTargetKey(),
                    citation.getSourceType(),
                    CitationValidationStatus.DEMOTED,
                    overlapScore,
                    "source_insufficient_overlap",
                    extractMatchedExcerpt(sourceFileRecord.getContentText(), hardFactTokens),
                    citation.getOrdinal()
            );
        }
        if (articleJdbcRepository == null) {
            return new CitationValidationResult(
                    citation.getTargetKey(),
                    citation.getSourceType(),
                    CitationValidationStatus.DEMOTED,
                    0.0D,
                    "article_repository_unavailable",
                    "",
                    citation.getOrdinal()
            );
        }
        ArticleRecord articleRecord = articleJdbcRepository.findByArticleKey(citation.getTargetKey())
                .or(() -> articleJdbcRepository.findByConceptId(citation.getTargetKey()))
                .orElse(null);
        if (articleRecord == null) {
            return new CitationValidationResult(
                    citation.getTargetKey(),
                    citation.getSourceType(),
                    CitationValidationStatus.NOT_FOUND,
                    0.0D,
                    "article_not_found",
                    "",
                    citation.getOrdinal()
            );
        }
        double overlapScore = calculateOverlapScore(hardFactTokens, buildEvidenceText(articleRecord));
        if (hasDirectEvidenceLineMatch(citation.getClaimText(), articleRecord.getContent())) {
            return new CitationValidationResult(
                    citation.getTargetKey(),
                    citation.getSourceType(),
                    CitationValidationStatus.VERIFIED,
                    Math.max(overlapScore, 1.0D),
                    "direct_line_match_verified",
                    extractMatchedExcerpt(articleRecord.getContent(), hardFactTokens),
                    citation.getOrdinal()
            );
        }
        if (overlapScore >= 1.0D) {
            return new CitationValidationResult(
                    citation.getTargetKey(),
                    citation.getSourceType(),
                    CitationValidationStatus.VERIFIED,
                    overlapScore,
                    "rule_overlap_verified",
                    extractMatchedExcerpt(articleRecord.getContent(), hardFactTokens),
                    citation.getOrdinal()
            );
        }
        if (isHighConfidencePartialOverlap(hardFactTokens, overlapScore)) {
            return new CitationValidationResult(
                    citation.getTargetKey(),
                    citation.getSourceType(),
                    CitationValidationStatus.VERIFIED,
                    overlapScore,
                    "near_complete_overlap_verified",
                    extractMatchedExcerpt(articleRecord.getContent(), hardFactTokens),
                    citation.getOrdinal()
            );
        }
        CitationValidationResult contextValidationResult = validateAgainstContextWindow(
                citation,
                buildEvidenceText(articleRecord),
                hardFactTokens,
                overlapScore,
                "context_overlap_verified"
        );
        if (contextValidationResult != null) {
            return contextValidationResult;
        }
        return new CitationValidationResult(
                citation.getTargetKey(),
                citation.getSourceType(),
                CitationValidationStatus.DEMOTED,
                overlapScore,
                "insufficient_overlap",
                extractMatchedExcerpt(articleRecord.getContent(), hardFactTokens),
                citation.getOrdinal()
        );
    }

    /**
     * 用 terminal unit 结构化证据验证指向源文件的 citation。
     *
     * 对 key=value 格式的 claim 逐条 terminal unit 计算 overlap，只有同一条
     * unit 的 evidence text 同时支撑 claim 的 path/key 和 value 才允许 VERIFIED。
     * 禁止将多个 terminal unit 拼接后整体计算 overlap——这会允许 key token 来自
     * unit A、value token 来自 unit B，造成假阳性。
     *
     * 如果没有任何单条 terminal unit 验证通过，返回 null，由调用方回退到现有
     * source file 逐句 overlap 验证。
     */
    private CitationValidationResult validateAgainstTerminalUnitEvidence(
            SourceFileRecord sourceFileRecord,
            List<String> hardFactTokens,
            Citation citation
    ) {
        if (factCardTerminalUnitJdbcRepository == null
                || !factCardTerminalUnitJdbcRepository.tableAvailable()) {
            logTuGuard("repo_unavailable", sourceFileRecord, citation, null);
            return null;
        }
        List<FactCardTerminalUnitRecord> terminalUnits =
                factCardTerminalUnitJdbcRepository.findBySourceFileId(sourceFileRecord.getId());
        if (terminalUnits == null || terminalUnits.isEmpty()) {
            logTuGuard("no_terminal_units", sourceFileRecord, citation, null);
            return null;
        }
        boolean isKeyValue = isKeyValueClaim(citation.getClaimText());
        if (!isKeyValue) {
            logTuGuard("not_key_value_claim", sourceFileRecord, citation, null);
            return null;
        }
        int unitIndex = 0;
        for (FactCardTerminalUnitRecord unit : terminalUnits) {
            String evidenceText = buildSingleUnitEvidenceText(unit);
            if (evidenceText.isBlank()) {
                unitIndex++;
                continue;
            }
            boolean valueMatched = claimValueMatchesUnit(citation.getClaimText(), unit);
            if (!valueMatched) {
                logTuUnitTrace(sourceFileRecord, citation, unit, null, unitIndex,
                        isKeyValue, valueMatched, -1.0D, false);
                unitIndex++;
                continue;
            }
            double overlapScore = calculateOverlapScore(hardFactTokens, evidenceText);
            boolean isHighConfidence = isHighConfidencePartialOverlap(hardFactTokens, overlapScore);
            logTuUnitTrace(sourceFileRecord, citation, unit, evidenceText, unitIndex,
                    isKeyValue, valueMatched, overlapScore, isHighConfidence);
            if (overlapScore >= 1.0D) {
                return new CitationValidationResult(
                        citation.getTargetKey(),
                        citation.getSourceType(),
                        CitationValidationStatus.VERIFIED,
                        overlapScore,
                        "terminal_unit_evidence_verified",
                        extractMatchedExcerpt(evidenceText, hardFactTokens),
                        citation.getOrdinal()
                );
            }
            if (isHighConfidence) {
                return new CitationValidationResult(
                        citation.getTargetKey(),
                        citation.getSourceType(),
                        CitationValidationStatus.VERIFIED,
                        overlapScore,
                        "terminal_unit_evidence_near_complete_verified",
                        extractMatchedExcerpt(evidenceText, hardFactTokens),
                        citation.getOrdinal()
                );
            }
            unitIndex++;
        }
        logTuResult(sourceFileRecord, citation, "null", terminalUnits.size());
        return null;
    }

    private void logTuGuard(
            String guard,
            SourceFileRecord sourceFileRecord,
            Citation citation,
            FactCardTerminalUnitRecord unit
    ) {
        if (traceManager == null || !traceManager.isL2Enabled("citation_validation")) {
            return;
        }
        Map<String, Object> fields = new LinkedHashMap<String, Object>();
        fields.put("tu_guard", guard);
        fields.put("tu_source_file_id", sourceFileRecord.getId());
        fields.put("tu_is_key_value_claim", isKeyValueClaim(citation.getClaimText()));
        if (unit != null) {
            fields.put("tu_matched_unit_id", unit.getUnitId());
        }
        traceManager.logL2Event("citation_terminal_unit_checked", "citation_validation", fields);
    }

    private void logTuUnitTrace(
            SourceFileRecord sourceFileRecord,
            Citation citation,
            FactCardTerminalUnitRecord unit,
            String evidenceText,
            int unitIndex,
            boolean isKeyValue,
            boolean valueMatched,
            double overlapScore,
            boolean isHighConfidence
    ) {
        if (traceManager == null || !traceManager.isL2Enabled("citation_validation")) {
            return;
        }
        Map<String, Object> fields = new LinkedHashMap<String, Object>();
        fields.put("tu_source_file_id", sourceFileRecord.getId());
        fields.put("tu_candidate_count", -1);
        fields.put("tu_is_key_value_claim", isKeyValue);
        fields.put("tu_claim_value_matched", valueMatched);
        fields.put("tu_overlap_score", overlapScore);
        fields.put("tu_is_high_confidence", isHighConfidence);
        fields.put("tu_unit_index", unitIndex);
        if (unit != null) {
            fields.put("tu_matched_unit_id", unit.getUnitId());
        }
        if (evidenceText != null) {
            fields.put("tu_evidence_text", traceManager.truncateTuEvidenceText(evidenceText));
        }
        fields.put("tu_claim_text", traceManager.truncateClaimText(citation.getClaimText()));
        traceManager.logL2Event("citation_terminal_unit_checked", "citation_validation", fields);
    }

    private void logTuResult(
            SourceFileRecord sourceFileRecord,
            Citation citation,
            String resultStatus,
            int candidateCount
    ) {
        if (traceManager == null || !traceManager.isL2Enabled("citation_validation")) {
            return;
        }
        Map<String, Object> fields = new LinkedHashMap<String, Object>();
        fields.put("tu_result", resultStatus);
        fields.put("tu_source_file_id", sourceFileRecord.getId());
        fields.put("tu_candidate_count", candidateCount);
        fields.put("tu_is_key_value_claim", isKeyValueClaim(citation.getClaimText()));
        fields.put("tu_claim_text", traceManager.truncateClaimText(citation.getClaimText()));
        traceManager.logL2Event("citation_terminal_unit_checked", "citation_validation", fields);
    }

    private void emitValidationTrace(Citation citation, CitationValidationResult result, List<String> hardFactTokens) {
        if (traceManager == null) {
            return;
        }
        Map<String, Object> fields = new LinkedHashMap<String, Object>();
        fields.put("citation_ordinal", citation.getOrdinal());
        fields.put("source_type", citation.getSourceType() == null ? "null" : citation.getSourceType().name());
        fields.put("target_key", citation.getTargetKey());
        fields.put("validation_status", result.getStatus().name());
        fields.put("reason", result.getReason());
        fields.put("hard_fact_token_count", hardFactTokens.size());
        fields.put("overlap_score", result.getOverlapScore());
        fields.put("matched_excerpt", traceManager.truncateMatchedExcerpt(result.getMatchedExcerpt()));
        String validationPath = inferValidationPath(result.getReason());
        fields.put("validation_path", validationPath);
        traceManager.logL1Event("citation_validated", "citation_validation", fields);
    }

    private String inferValidationPath(String reason) {
        if (reason == null) {
            return "UNKNOWN";
        }
        if (reason.startsWith("terminal_unit_evidence")) {
            return "TERMINAL_UNIT";
        }
        if (reason.contains("direct_line")) {
            return "DIRECT_LINE";
        }
        if (reason.contains("rule_overlap") || reason.contains("near_complete")) {
            return "RULE_OVERLAP";
        }
        if (reason.contains("context_overlap")) {
            return "CONTEXT_WINDOW";
        }
        if (reason.contains("insufficient")) {
            return "INSUFFICIENT";
        }
        if (reason.contains("not_found")) {
            return "NOT_FOUND";
        }
        return reason;
    }

    /**
     * 判断 claim 是否为通用 key=value / path=value 格式。
     *
     * 只检查 claim 文本中是否存在 `=` 且等号两侧均有非空文本，
     * 不识别具体字段名或值。
     */
    private boolean isKeyValueClaim(String claimText) {
        if (isBlank(claimText)) {
            return false;
        }
        int eqIndex = claimText.indexOf('=');
        if (eqIndex <= 0 || eqIndex >= claimText.length() - 1) {
            return false;
        }
        String left = claimText.substring(0, eqIndex).stripTrailing();
        String right = claimText.substring(eqIndex + 1).stripLeading();
        return !left.isBlank() && !right.isBlank();
    }

    /**
     * 为单条 terminal unit 构建验证用 evidence text。
     *
     * 包含 displayText（key_path = value 格式）和 valueText/normalizedValue，
     * 确保同一条 unit 内同时覆盖 path/key 和 value token。
     */
    private String buildSingleUnitEvidenceText(FactCardTerminalUnitRecord unit) {
        StringBuilder sb = new StringBuilder();
        if (!isBlank(unit.getDisplayText())) {
            sb.append(unit.getDisplayText());
        }
        sb.append(' ');
        if (!isBlank(unit.getValueText())) {
            sb.append(unit.getValueText());
        }
        if (!isBlank(unit.getNormalizedValue())
                && !unit.getNormalizedValue().equals(unit.getValueText())) {
            sb.append(' ').append(unit.getNormalizedValue());
        }
        return sb.toString();
    }

    /**
     * 判断 claim 的值部分是否与 terminal unit 的值字段匹配。
     *
     * 双层检查：（1）直接字符串匹配——claim 值归一化后是否等于或包含于 unit
     * 的 valueText/normalizedValue；（2）hard fact token 匹配——claim 值中的
     * 数字/snake_case 等事实 token 是否全部出现在 unit 值字段中。
     *
     * 仅当两条路径都无冲突时才返回 true，防止 key/path token 来自 unit A、
     * value token 来自 unit B 的跨 unit 假阳性。
     */
    private boolean claimValueMatchesUnit(String claimText, FactCardTerminalUnitRecord unit) {
        String claimValue = extractClaimValuePart(claimText);
        if (claimValue.isBlank()) {
            return true;
        }
        String normalizedClaimValue = normalizeToken(claimValue);
        if (normalizedClaimValue.isBlank()) {
            return true;
        }
        String unitValueText = buildUnitValueText(unit);
        if (unitValueText.isBlank()) {
            return false;
        }
        if (unitValueText.contains(normalizedClaimValue)) {
            return true;
        }
        List<String> valueTokens = extractHardFactTokens(claimValue);
        if (valueTokens.isEmpty()) {
            String unitNormalized = normalizeToken(unitValueText);
            return unitNormalized.contains(normalizedClaimValue)
                    || normalizedClaimValue.contains(unitNormalized);
        }
        Set<String> unitValueTokens = tokenize(unitValueText);
        for (String valueToken : valueTokens) {
            if (!unitValueTokens.contains(valueToken)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 从 claim 文本中提取 `=` 右侧的值部分。
     */
    private String extractClaimValuePart(String claimText) {
        if (isBlank(claimText)) {
            return "";
        }
        int eqIndex = claimText.indexOf('=');
        if (eqIndex <= 0 || eqIndex >= claimText.length() - 1) {
            return "";
        }
        return claimText.substring(eqIndex + 1).strip();
    }

    /**
     * 构建 terminal unit 的值验证文本。
     */
    private String buildUnitValueText(FactCardTerminalUnitRecord unit) {
        StringBuilder sb = new StringBuilder();
        if (!isBlank(unit.getValueText())) {
            sb.append(unit.getValueText());
        }
        if (!isBlank(unit.getNormalizedValue())
                && !unit.getNormalizedValue().equals(unit.getValueText())) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(unit.getNormalizedValue());
        }
        return sb.toString();
    }

    private CitationValidationResult validateAgainstContextWindow(
            Citation citation,
            String evidenceText,
            List<String> hardFactTokens,
            double overlapScore,
            String verifiedReason
    ) {
        if (!hasContextWindowSupport(citation, evidenceText, overlapScore)) {
            return null;
        }
        List<String> contextTokens = extractHardFactTokens(citation.getContextWindow());
        double contextOverlapScore = calculateOverlapScore(contextTokens, evidenceText);
        return new CitationValidationResult(
                citation.getTargetKey(),
                citation.getSourceType(),
                CitationValidationStatus.VERIFIED,
                Math.max(overlapScore, contextOverlapScore),
                verifiedReason,
                extractMatchedExcerpt(evidenceText, hardFactTokens),
                citation.getOrdinal()
        );
    }

    private boolean hasContextWindowSupport(Citation citation, String evidenceText, double overlapScore) {
        if (citation == null || overlapScore <= 0.0D || evidenceText == null || evidenceText.isBlank()) {
            return false;
        }
        String claimText = citation.getClaimText();
        String contextWindow = citation.getContextWindow();
        String normalizedClaimText = normalizeForDirectLineMatch(claimText);
        String normalizedContextWindow = normalizeForDirectLineMatch(contextWindow);
        if (normalizedClaimText.isBlank()
                || normalizedContextWindow.isBlank()
                || normalizedClaimText.equals(normalizedContextWindow)
                || !normalizedContextWindow.contains(normalizedClaimText)) {
            return false;
        }
        if (hasUnmatchedStrictHardFactToken(claimText, evidenceText)) {
            return false;
        }
        List<String> contextTokens = extractHardFactTokens(contextWindow);
        double contextOverlapScore = calculateOverlapScore(contextTokens, evidenceText);
        return contextOverlapScore >= 1.0D || isHighConfidencePartialOverlap(contextTokens, contextOverlapScore);
    }

    private boolean hasUnmatchedStrictHardFactToken(String claimText, String evidenceText) {
        List<String> strictHardFactTokens = extractStrictHardFactTokens(claimText);
        if (strictHardFactTokens.isEmpty()) {
            return false;
        }
        Set<String> evidenceTokens = tokenize(evidenceText);
        for (String strictHardFactToken : strictHardFactTokens) {
            if (!evidenceTokens.contains(strictHardFactToken)) {
                return true;
            }
        }
        return false;
    }

    private boolean isHighConfidencePartialOverlap(List<String> hardFactTokens, double overlapScore) {
        return hardFactTokens != null
                && ((hardFactTokens.size() >= 4 && overlapScore >= 0.75D)
                || (hardFactTokens.size() >= 2 && overlapScore >= 0.60D));
    }

    private String buildEvidenceText(ArticleRecord articleRecord) {
        StringBuilder textBuilder = new StringBuilder();
        if (articleRecord.getTitle() != null) {
            textBuilder.append(articleRecord.getTitle()).append('\n');
        }
        if (articleRecord.getSummary() != null) {
            textBuilder.append(articleRecord.getSummary()).append('\n');
        }
        if (articleRecord.getContent() != null) {
            textBuilder.append(articleRecord.getContent());
        }
        return textBuilder.toString();
    }

    private double calculateOverlapScore(List<String> hardFactTokens, String evidenceText) {
        Set<String> claimTokens = new LinkedHashSet<String>(hardFactTokens);
        if (claimTokens.isEmpty()) {
            return 0.0D;
        }
        Set<String> evidenceTokens = tokenize(evidenceText);
        if (evidenceTokens.isEmpty()) {
            return 0.0D;
        }
        int matchedCount = 0;
        for (String claimToken : claimTokens) {
            if (evidenceTokens.contains(claimToken)) {
                matchedCount++;
            }
        }
        return matchedCount * 1.0D / claimTokens.size();
    }

    private List<String> extractHardFactTokens(String claimText) {
        List<String> hardFactTokens = new ArrayList<String>();
        if (claimText == null || claimText.isBlank()) {
            return hardFactTokens;
        }
        String normalizedClaimText = normalizeForHardFactExtraction(claimText);
        appendMatches(hardFactTokens, NUMERIC_LITERAL_PATTERN.matcher(normalizedClaimText));
        appendMatches(hardFactTokens, SNAKE_CASE_PATTERN.matcher(normalizedClaimText));
        appendMatches(hardFactTokens, FQN_PATTERN.matcher(normalizedClaimText));
        appendMatches(hardFactTokens, HTTP_PATH_PATTERN.matcher(normalizedClaimText));
        appendMatches(hardFactTokens, JAVA_SYMBOL_PATTERN.matcher(normalizedClaimText));
        appendMatches(hardFactTokens, LATIN_TERM_PATTERN.matcher(normalizedClaimText));
        if (hardFactTokens.size() < 2) {
            appendHanTermMatches(hardFactTokens, HAN_TERM_PATTERN.matcher(normalizedClaimText));
        }
        appendCompositeTokenPartsForClaim(hardFactTokens);
        return hardFactTokens;
    }

    private List<String> extractStrictHardFactTokens(String claimText) {
        List<String> hardFactTokens = new ArrayList<String>();
        if (claimText == null || claimText.isBlank()) {
            return hardFactTokens;
        }
        String normalizedClaimText = normalizeForHardFactExtraction(claimText);
        appendMatches(hardFactTokens, NUMERIC_LITERAL_PATTERN.matcher(normalizedClaimText));
        appendMatches(hardFactTokens, SNAKE_CASE_PATTERN.matcher(normalizedClaimText));
        appendMatches(hardFactTokens, FQN_PATTERN.matcher(normalizedClaimText));
        appendMatches(hardFactTokens, HTTP_PATH_PATTERN.matcher(normalizedClaimText));
        appendMatches(hardFactTokens, JAVA_SYMBOL_PATTERN.matcher(normalizedClaimText));
        return hardFactTokens;
    }

    private void appendMatches(List<String> hardFactTokens, Matcher matcher) {
        while (matcher.find()) {
            String literal = matcher.group(1);
            if (literal == null || literal.isBlank()) {
                continue;
            }
            String normalizedLiteral = normalizeToken(literal);
            if (!normalizedLiteral.isBlank() && !hardFactTokens.contains(normalizedLiteral)) {
                hardFactTokens.add(normalizedLiteral);
            }
        }
    }

    private void appendHanTermMatches(List<String> hardFactTokens, Matcher matcher) {
        while (matcher.find()) {
            String literal = matcher.group(1);
            if (literal == null || literal.isBlank() || isGenericHanLiteral(literal)) {
                continue;
            }
            String normalizedLiteral = normalizeToken(literal);
            if (!hardFactTokens.contains(normalizedLiteral)) {
                hardFactTokens.add(normalizedLiteral);
            }
            if (literal.length() >= 5) {
                for (int start = 0; start <= literal.length() - 3; start++) {
                    String slice = literal.substring(start, start + 3);
                    if (isGenericHanLiteral(slice)) {
                        continue;
                    }
                    String normalizedSlice = normalizeToken(slice);
                    if (!hardFactTokens.contains(normalizedSlice)) {
                        hardFactTokens.add(normalizedSlice);
                    }
                }
            }
        }
    }

    private void appendCompositeTokenPartsForClaim(List<String> hardFactTokens) {
        List<String> compositeParts = new ArrayList<String>();
        for (String token : hardFactTokens) {
            if (token == null || token.isBlank()) {
                continue;
            }
            String[] parts = token.split("[./-]+");
            for (String part : parts) {
                if (part != null && !part.isBlank() && (part.length() >= 2 || isNumericToken(part))) {
                    String normalized = normalizeToken(part);
                    if (!hardFactTokens.contains(normalized) && !compositeParts.contains(normalized)) {
                        compositeParts.add(normalized);
                    }
                }
            }
        }
        hardFactTokens.addAll(compositeParts);
    }

    private boolean isGenericHanLiteral(String literal) {
        if (literal == null || literal.isBlank()) {
            return true;
        }
        return literal.contains("当前证据不足")
                || literal.contains("主要")
                || literal.contains("包括")
                || literal.contains("需要")
                || literal.contains("可以")
                || literal.contains("通过")
                || literal.contains("相关")
                || literal.contains("不同")
                || literal.contains("如下")
                || literal.contains("例如")
                || literal.contains("使用")
                || literal.contains("暴露")
                || literal.contains("采用")
                || literal.contains("处理")
                || literal.contains("这是一个")
                || literal.contains("一般性")
                || literal.contains("系统描述");
    }

    private Set<String> tokenize(String content) {
        Set<String> tokens = new LinkedHashSet<String>();
        if (content == null || content.isBlank()) {
            return tokens;
        }
        String[] parts = content.toLowerCase(Locale.ROOT).split("[^\\p{IsAlphabetic}\\p{IsDigit}_./-]+");
        for (String part : parts) {
            if (part != null && !part.isBlank() && (part.length() >= 2 || isNumericToken(part))) {
                tokens.add(part);
                appendCompositeTokenParts(tokens, part);
            }
        }
        appendEmbeddedNumericTokens(tokens, content);
        return tokens;
    }

    private void appendCompositeTokenParts(Set<String> tokens, String token) {
        if (tokens == null || token == null || token.isBlank()) {
            return;
        }
        String[] parts = token.split("[./-]+");
        for (String part : parts) {
            if (part != null && !part.isBlank() && (part.length() >= 2 || isNumericToken(part))) {
                tokens.add(part);
            }
        }
    }

    /**
     * 补充嵌在中文单位或连续文本里的数字事实。
     *
     * @param tokens token 集合
     * @param content 原始内容
     */
    private void appendEmbeddedNumericTokens(Set<String> tokens, String content) {
        Matcher matcher = NUMERIC_LITERAL_PATTERN.matcher(content);
        while (matcher.find()) {
            String numericToken = normalizeToken(matcher.group(1));
            if (!numericToken.isBlank()) {
                tokens.add(numericToken);
            }
        }
    }

    private String normalizeToken(String literal) {
        return literal == null ? "" : literal.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeForHardFactExtraction(String claimText) {
        return claimText == null
                ? ""
                : claimText
                .replace("**", "")
                .replace("`", "")
                .replaceAll("\\[\\[[^\\]]+]]", "")
                .replaceAll("\\[→\\s*[^\\]]+]", "")
                .replaceAll("(?m)^#+\\s*", "")
                .replaceAll("(?<=\\s|^)\\d+\\.\\s*", "")
                .trim();
    }

    private boolean hasDirectEvidenceLineMatch(String claimText, String evidenceText) {
        String normalizedClaimText = normalizeForDirectLineMatch(claimText);
        if (normalizedClaimText.isBlank() || evidenceText == null || evidenceText.isBlank()) {
            return false;
        }
        for (String line : evidenceText.split("\\R")) {
            String normalizedEvidenceLine = normalizeForDirectLineMatch(line);
            if (normalizedEvidenceLine.isBlank()) {
                continue;
            }
            if (normalizedEvidenceLine.contains(normalizedClaimText) || normalizedClaimText.contains(normalizedEvidenceLine)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeForDirectLineMatch(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text
                .replace("**", "")
                .replace("`", "")
                .replaceAll("\\[\\[[^\\]]+]]", "")
                .replaceAll("\\[→\\s*[^\\]]+]", "")
                .replaceFirst("^\\s*[-*]\\s*", "")
                .replaceFirst("^当前可确认的信息是[:：]\\s*", "")
                .replaceFirst("^补充证据还提到[:：]\\s*", "")
                .replaceFirst("^同一份资料还给出[:：]\\s*", "")
                .replaceFirst("^支持“[^”]+”的材料提到[:：]\\s*", "")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean isNumericToken(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            if (!Character.isDigit(value.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private String extractMatchedExcerpt(String content, List<String> hardFactTokens) {
        if (content == null || content.isBlank()) {
            return "";
        }
        if (hardFactTokens == null || hardFactTokens.isEmpty()) {
            return content.length() <= 200 ? content : content.substring(0, 200);
        }
        String[] lines = content.split("\\R");
        for (String line : lines) {
            String normalizedLine = line == null ? "" : line.trim().toLowerCase(Locale.ROOT);
            if (normalizedLine.isBlank()) {
                continue;
            }
            for (String hardFactToken : hardFactTokens) {
                String normalizedToken = hardFactToken == null ? "" : hardFactToken.trim().toLowerCase(Locale.ROOT);
                if (!normalizedToken.isBlank() && normalizedLine.contains(normalizedToken)) {
                    return line.length() <= 200 ? line : line.substring(0, 200);
                }
            }
        }
        return content.length() <= 200 ? content : content.substring(0, 200);
    }
}
