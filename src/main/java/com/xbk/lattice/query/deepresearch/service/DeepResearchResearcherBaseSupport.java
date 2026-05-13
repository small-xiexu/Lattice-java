package com.xbk.lattice.query.deepresearch.service;

import com.xbk.lattice.shared.json.JsonMappers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.llm.service.ExecutionLlmSnapshotService;
import com.xbk.lattice.query.deepresearch.domain.EvidenceCard;
import com.xbk.lattice.query.deepresearch.domain.LayerSummary;
import com.xbk.lattice.query.deepresearch.domain.ResearchTaskHit;
import com.xbk.lattice.query.deepresearch.domain.ResearchTask;
import com.xbk.lattice.query.domain.QueryAnswerPayload;
import com.xbk.lattice.query.evidence.domain.EvidenceAnchor;
import com.xbk.lattice.query.evidence.domain.EvidenceAnchorSourceType;
import com.xbk.lattice.query.evidence.domain.FactFinding;
import com.xbk.lattice.query.evidence.domain.FactValueType;
import com.xbk.lattice.query.evidence.domain.FindingSupportLevel;
import com.xbk.lattice.query.service.AnswerGenerationService;
import com.xbk.lattice.query.service.KnowledgeSearchService;
import com.xbk.lattice.query.service.QueryArticleHit;
import com.xbk.lattice.query.service.QueryEvidenceRelevanceSupport;
import com.xbk.lattice.query.service.QueryEvidenceType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deep Research 研究员基础支持。
 *
 * 职责：承载证据锚点、finding 构造、文本裁剪和结构化枚举解析工具。
 *
 * @author xiexu
 */
@Slf4j
abstract class DeepResearchResearcherBaseSupport {

    protected static final ObjectMapper OBJECT_MAPPER = JsonMappers.defaultMapper();

    protected static final Pattern NUMERIC_LITERAL_PATTERN = Pattern.compile("\\b(\\d+(?:\\.\\d+)?)\\b");

    protected static final Pattern SNAKE_CASE_PATTERN = Pattern.compile("\\b([a-z][a-z0-9]*(?:_[a-z0-9]+){1,})\\b");

    protected static final Pattern JAVA_SYMBOL_PATTERN = Pattern.compile("\\b([A-Z][A-Za-z0-9]{2,})\\b");

    protected static final Pattern FRONT_MATTER_SUMMARY_PATTERN = Pattern.compile("(?ms)^---\\s*\\R.*?^summary:\\s*\"([^\"]+)\".*?^---");

    protected KnowledgeSearchService knowledgeSearchService;

    protected AnswerGenerationService answerGenerationService;

    /**
     * 返回任务标识，缺失时给出稳定占位值。
     *
     * @param task 研究任务
     * @return 任务标识
     */
    protected String resolveTaskId(ResearchTask task) {
        if (task == null || task.getTaskId() == null || task.getTaskId().isBlank()) {
            return "deep_research_task";
        }
        return task.getTaskId();
    }

    /**
     * 返回任务问题，缺失时给出空字符串。
     *
     * @param task 研究任务
     * @return 任务问题
     */
    protected String resolveTaskQuestion(ResearchTask task) {
        if (task == null || task.getQuestion() == null) {
            return "";
        }
        return task.getQuestion();
    }

    protected EvidenceAnchor buildEvidenceAnchor(String anchorId, QueryArticleHit hit, String quoteText) {
        EvidenceAnchorSourceType sourceType = mapSourceType(hit);
        String sourceId = resolveSourceId(hit);
        if (hit.getEvidenceType() == QueryEvidenceType.FACT_CARD && firstSourcePath(hit) != null) {
            sourceId = firstSourcePath(hit);
        }
        if (sourceType == null || sourceId.isBlank() || quoteText == null || quoteText.isBlank()) {
            return null;
        }
        EvidenceAnchor evidenceAnchor = new EvidenceAnchor();
        evidenceAnchor.setAnchorId(anchorId);
        evidenceAnchor.setSourceType(sourceType);
        evidenceAnchor.setSourceId(sourceId);
        evidenceAnchor.setQuoteText(quoteText);
        evidenceAnchor.setRetrievalScore(normalizeConfidence(hit.getScore()));
        if (sourceType == EvidenceAnchorSourceType.SOURCE_FILE) {
            evidenceAnchor.setPath(sourceId);
        }
        return evidenceAnchor;
    }
    protected FactFinding buildFactFinding(
            String anchorId,
            EvidenceCard evidenceCard,
            ResearchTask task,
            QueryArticleHit hit,
            String claimText
    ) {
        if (claimText == null || claimText.isBlank()) {
            return null;
        }
        String subject = normalizeFactToken(task == null ? evidenceCard.getTaskId() : task.getTaskId());
        if (subject.isBlank()) {
            subject = "deep_research_task";
        }
        FactFinding factFinding = new FactFinding();
        factFinding.setFindingId(anchorId + "-finding");
        factFinding.setSubject(subject);
        factFinding.setPredicate("claim");
        factFinding.setQualifier("deep_research");
        factFinding.setFactKey(factFinding.expectedFactKey());
        factFinding.setValueText(claimText.trim());
        factFinding.setValueType(FactValueType.STRING);
        factFinding.setClaimText(claimText.trim());
        factFinding.setConfidence(normalizeConfidence(hit.getScore()));
        factFinding.setSupportLevel(FindingSupportLevel.DIRECT);
        factFinding.setAnchorIds(List.of(anchorId));
        return factFinding;
    }
    protected String resolveClaim(String answerSummary, QueryArticleHit hit) {
        String fallbackClaim = stripExistingCitationLiteral(hit.getTitle() + "：" + extractEvidenceSnippet(hit));
        if (answerSummary != null && !answerSummary.isBlank()) {
            String[] lines = answerSummary.split("\\R");
            for (String line : lines) {
                String normalizedLine = line == null ? "" : line.trim();
                if (!normalizedLine.isBlank() && !normalizedLine.startsWith("#")) {
                    String claimText = stripExistingCitationLiteral(normalizedLine);
                    if (claimText.contains("冲突") || claimText.contains("不一致")) {
                        String focusedEvidenceSnippet = stripExistingCitationLiteral(
                                extractEvidenceSnippet(hit)
                        );
                        if (!focusedEvidenceSnippet.isBlank()) {
                            return focusedEvidenceSnippet;
                        }
                    }
                    String focusedClaimSnippet = extractFocusedClaimSnippet(hit, claimText);
                    if (!focusedClaimSnippet.isBlank()) {
                        return focusedClaimSnippet;
                    }
                    String focusedEvidenceSnippet = extractEvidenceSnippet(hit);
                    if (looksLikeLowValueEvidenceSnippet(claimText) && !focusedEvidenceSnippet.isBlank()) {
                        return focusedEvidenceSnippet;
                    }
                    return claimText;
                }
            }
        }
        return fallbackClaim;
    }
    protected String resolveConflictClaim(QueryArticleHit hit, String answerSummary) {
        String focusedClaimSnippet = extractFocusedClaimSnippet(hit, answerSummary);
        if (!focusedClaimSnippet.isBlank()) {
            return focusedClaimSnippet;
        }
        return stripExistingCitationLiteral(extractEvidenceSnippet(hit));
    }
    protected String stripExistingCitationLiteral(String claimText) {
        if (claimText == null || claimText.isBlank()) {
            return "";
        }
        String normalizedClaimText = claimText
                .replaceAll("\\[\\[[^\\]]+]]", "")
                .replaceAll("\\[→\\s*[^\\]]+]", "")
                .replaceAll("\\s{2,}", " ")
                .trim();
        return normalizedClaimText;
    }
    protected boolean isHitRelevantToClaim(QueryArticleHit hit, String claimText) {
        if (hit == null || claimText == null || claimText.isBlank()) {
            return false;
        }
        List<String> hardFactTokens = extractHardFactTokens(claimText);
        if (hardFactTokens.isEmpty()) {
            return true;
        }
        String evidenceText = buildEvidenceText(hit).toLowerCase(Locale.ROOT);
        for (String hardFactToken : hardFactTokens) {
            if (evidenceText.contains(hardFactToken)) {
                return true;
            }
        }
        return false;
    }
    protected List<String> extractHardFactTokens(String claimText) {
        List<String> preferredTokens = new ArrayList<String>();
        List<String> numericTokens = new ArrayList<String>();
        appendMatches(preferredTokens, SNAKE_CASE_PATTERN.matcher(claimText));
        appendMatches(preferredTokens, JAVA_SYMBOL_PATTERN.matcher(claimText));
        appendMatches(numericTokens, NUMERIC_LITERAL_PATTERN.matcher(claimText));
        if (!preferredTokens.isEmpty()) {
            return preferredTokens;
        }
        return numericTokens;
    }
    protected String extractFocusedClaimSnippet(QueryArticleHit hit, String claimText) {
        if (hit == null || claimText == null || claimText.isBlank()) {
            return "";
        }
        List<String> hardFactTokens = extractHardFactTokens(claimText);
        if (hardFactTokens.isEmpty()) {
            return "";
        }
        for (String hardFactToken : hardFactTokens) {
            String body = sanitizeEvidenceBody(hit.getContent());
            String snippetFromBody = extractSentenceContainingToken(body, hardFactToken);
            if (!snippetFromBody.isBlank()) {
                return snippetFromBody;
            }
            String summary = extractArticleSummary(hit.getContent());
            String snippetFromSummary = extractSentenceContainingToken(summary, hardFactToken);
            if (!snippetFromSummary.isBlank()) {
                return snippetFromSummary;
            }
        }
        return "";
    }
    protected String buildEvidenceText(QueryArticleHit hit) {
        if (hit == null) {
            return "";
        }
        StringBuilder evidenceTextBuilder = new StringBuilder();
        if (hit.getTitle() != null) {
            evidenceTextBuilder.append(hit.getTitle()).append(' ');
        }
        String summary = extractArticleSummary(hit.getContent());
        if (summary != null && !summary.isBlank()) {
            evidenceTextBuilder.append(summary).append(' ');
        }
        String content = sanitizeEvidenceBody(hit.getContent());
        if (content != null && !content.isBlank()) {
            evidenceTextBuilder.append(content).append(' ');
        }
        if (hit.getConceptId() != null) {
            evidenceTextBuilder.append(hit.getConceptId()).append(' ');
        }
        return evidenceTextBuilder.toString();
    }
    protected void appendMatches(List<String> hardFactTokens, Matcher matcher) {
        while (matcher.find()) {
            String literal = matcher.group(1);
            if (literal == null || literal.isBlank()) {
                continue;
            }
            String normalizedLiteral = literal.trim().toLowerCase(Locale.ROOT);
            if (!hardFactTokens.contains(normalizedLiteral)) {
                hardFactTokens.add(normalizedLiteral);
            }
        }
    }
    protected String resolveSourceId(QueryArticleHit hit) {
        if (hit == null) {
            return "";
        }
        if (hit.getEvidenceType() == QueryEvidenceType.ARTICLE
                && hit.getConceptId() != null
                && !hit.getConceptId().isBlank()) {
            return hit.getConceptId();
        }
        if (hit.getEvidenceType() == QueryEvidenceType.SOURCE && hit.getSourcePaths() != null && !hit.getSourcePaths().isEmpty()) {
            return hit.getSourcePaths().get(0);
        }
        if (hit.getArticleKey() != null && !hit.getArticleKey().isBlank()) {
            return hit.getArticleKey();
        }
        if (hit.getSourcePaths() != null && !hit.getSourcePaths().isEmpty()) {
            return hit.getSourcePaths().get(0);
        }
        return hit.getConceptId() == null ? "" : hit.getConceptId();
    }
    protected String firstSourcePath(QueryArticleHit hit) {
        if (hit == null || hit.getSourcePaths() == null || hit.getSourcePaths().isEmpty()) {
            return null;
        }
        return hit.getSourcePaths().get(0);
    }
    protected EvidenceAnchorSourceType mapSourceType(QueryArticleHit hit) {
        if (hit == null || hit.getEvidenceType() == null) {
            return null;
        }
        if (hit.getEvidenceType() == QueryEvidenceType.ARTICLE) {
            return EvidenceAnchorSourceType.ARTICLE;
        }
        if (hit.getEvidenceType() == QueryEvidenceType.SOURCE) {
            return EvidenceAnchorSourceType.SOURCE_FILE;
        }
        if (hit.getEvidenceType() == QueryEvidenceType.GRAPH) {
            return EvidenceAnchorSourceType.GRAPH_FACT;
        }
        if (hit.getEvidenceType() == QueryEvidenceType.FACT_CARD) {
            return EvidenceAnchorSourceType.SOURCE_FILE;
        }
        if (hit.getEvidenceType() == QueryEvidenceType.CONTRIBUTION) {
            return EvidenceAnchorSourceType.CONTRIBUTION;
        }
        return null;
    }
    protected EvidenceAnchorSourceType parseSourceType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return EvidenceAnchorSourceType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException exception) {
            return null;
        }
    }
    protected FactValueType parseFactValueType(String value) {
        if (value == null || value.isBlank()) {
            return FactValueType.STRING;
        }
        try {
            return FactValueType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException exception) {
            return FactValueType.STRING;
        }
    }
    protected FindingSupportLevel parseSupportLevel(String value) {
        if (value == null || value.isBlank()) {
            return FindingSupportLevel.DIRECT;
        }
        try {
            return FindingSupportLevel.valueOf(value.trim().toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException exception) {
            return FindingSupportLevel.DIRECT;
        }
    }
    protected List<String> parseAnchorIds(JsonNode anchorIdsNode) {
        List<String> anchorIds = new ArrayList<String>();
        if (anchorIdsNode == null || !anchorIdsNode.isArray()) {
            return anchorIds;
        }
        for (JsonNode anchorIdNode : anchorIdsNode) {
            String anchorId = anchorIdNode.asText("");
            if (!anchorId.isBlank()) {
                anchorIds.add(anchorId.trim());
            }
        }
        return anchorIds;
    }
    protected String resolveDefaultFindingId(JsonNode findingNode, StructuredEvidenceBundle structuredEvidenceBundle) {
        String firstAnchorId = "";
        if (findingNode.path("anchorIds").isArray() && !findingNode.path("anchorIds").isEmpty()) {
            firstAnchorId = findingNode.path("anchorIds").get(0).asText("");
        }
        if (firstAnchorId.isBlank() && structuredEvidenceBundle.getEvidenceAnchors().size() == 1) {
            firstAnchorId = structuredEvidenceBundle.getEvidenceAnchors().get(0).getAnchorId();
        }
        return firstAnchorId.isBlank() ? "finding-unresolved" : firstAnchorId + "-finding";
    }
    protected Integer nullableInt(JsonNode jsonNode) {
        if (jsonNode == null || jsonNode.isMissingNode() || jsonNode.isNull()) {
            return null;
        }
        return Integer.valueOf(jsonNode.asInt());
    }
    protected boolean looksLikeStructuredEvidenceJson(String answerSummary) {
        if (answerSummary == null) {
            return false;
        }
        String normalized = answerSummary.trim();
        return normalized.startsWith("{")
                && (normalized.contains("\"factFindings\"") || normalized.contains("\"evidenceAnchors\""));
    }
    protected String normalizeFactToken(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("[^a-z0-9_]+", "_");
        normalized = normalized.replaceAll("_+", "_");
        normalized = normalized.replaceAll("^_+|_+$", "");
        return normalized;
    }
    protected String extractSnippet(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String normalized = sanitizeEvidenceBody(content).trim().replaceAll("\\s+", " ");
        if (normalized.length() <= 180) {
            return normalized;
        }
        return normalized.substring(0, 180);
    }
    protected String extractSentenceContainingToken(String content, String token) {
        if (content == null || content.isBlank() || token == null || token.isBlank()) {
            return "";
        }
        String normalizedContent = content.replaceAll("\\R+", " ").trim();
        String[] sentences = normalizedContent.split("(?<=[。；!?！？])");
        String loweredToken = token.toLowerCase(Locale.ROOT);
        for (String sentence : sentences) {
            String normalizedSentence = sentence == null ? "" : sentence.trim();
            if (!normalizedSentence.isBlank()
                    && normalizedSentence.toLowerCase(Locale.ROOT).contains(loweredToken)) {
                return stripExistingCitationLiteral(normalizedSentence);
            }
        }
        return "";
    }
    protected String stripFrontMatter(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String normalizedContent = content.trim();
        return normalizedContent.replaceFirst("(?s)^---\\s*\\R.*?\\R---\\s*\\R?", "").trim();
    }
    protected String extractArticleSummary(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        Matcher matcher = FRONT_MATTER_SUMMARY_PATTERN.matcher(content.trim());
        if (!matcher.find()) {
            return "";
        }
        return matcher.group(1).trim();
    }
    protected String extractEvidenceSnippet(QueryArticleHit hit) {
        if (hit == null) {
            return "";
        }
        String focusedSnippet = extractFocusedQuestionSnippet(hit);
        if (!focusedSnippet.isBlank()) {
            return focusedSnippet;
        }
        String summary = extractArticleSummary(hit.getContent());
        if (summary != null && !summary.isBlank()) {
            return summary;
        }
        String bodySnippet = extractSnippet(hit.getContent());
        if (!bodySnippet.isBlank()) {
            return bodySnippet;
        }
        return "";
    }
    protected String extractFocusedQuestionSnippet(QueryArticleHit hit) {
        List<String> focusTokens = extractHardFactTokens(
                (hit.getTitle() == null ? "" : hit.getTitle()) + " " + buildEvidenceText(hit)
        );
        if (focusTokens.isEmpty()) {
            return "";
        }
        String body = sanitizeEvidenceBody(hit.getContent());
        for (String focusToken : focusTokens) {
            String focusedBodySnippet = extractSentenceContainingToken(body, focusToken);
            if (!focusedBodySnippet.isBlank() && !looksLikeLowValueEvidenceSnippet(focusedBodySnippet)) {
                return focusedBodySnippet;
            }
        }
        String summary = extractArticleSummary(hit.getContent());
        for (String focusToken : focusTokens) {
            String focusedSummarySnippet = extractSentenceContainingToken(summary, focusToken);
            if (!focusedSummarySnippet.isBlank() && !looksLikeLowValueEvidenceSnippet(focusedSummarySnippet)) {
                return focusedSummarySnippet;
            }
        }
        return "";
    }
    protected boolean looksLikeLowValueEvidenceSnippet(String snippet) {
        if (snippet == null || snippet.isBlank()) {
            return true;
        }
        String normalizedSnippet = snippet.trim();
        String lowerCaseSnippet = normalizedSnippet.toLowerCase(Locale.ROOT);
        return lowerCaseSnippet.contains("目录")
                || lowerCaseSnippet.contains("table of contents")
                || normalizedSnippet.matches("(?s).*\\[[^\\]]+]\\(#[^)]+\\).*");
    }
    protected String sanitizeEvidenceBody(String content) {
        String normalizedContent = stripFrontMatter(content);
        if (normalizedContent.isBlank()) {
            return normalizedContent;
        }
        String strippedBody = normalizedContent
                .replaceAll("\\[\\[[^\\]]+]]", "")
                .replaceAll("\\[→\\s*[^\\]]+]", "")
                .replaceAll("\\[[^\\]]*编译[^\\]]*]", "")
                .replaceAll("(?m)^#+\\s*", "")
                .replace('|', ' ');
        StringBuilder bodyBuilder = new StringBuilder();
        for (String rawLine : strippedBody.split("\\R")) {
            String normalizedLine = rawLine == null ? "" : rawLine.trim();
            if (normalizedLine.isEmpty() || looksLikeTableOfContentsLine(normalizedLine)) {
                continue;
            }
            if (bodyBuilder.length() > 0) {
                bodyBuilder.append(' ');
            }
            bodyBuilder.append(normalizedLine);
        }
        return bodyBuilder.toString().replaceAll("\\s{2,}", " ").trim();
    }
    protected boolean looksLikeTableOfContentsLine(String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        String compactLine = normalizedLine.trim();
        if ("目录".equals(compactLine) || compactLine.matches("^[-+*]?\\s*目录\\s*$")) {
            return true;
        }
        if (compactLine.matches("^[-+*]\\s*\\[[^\\]]+]\\(#[^)]+\\).*$")) {
            return true;
        }
        return compactLine.matches("^\\d+(?:\\.\\d+)*\\s+.+$")
                && compactLine.contains("#")
                && compactLine.contains("-");
    }
    protected double normalizeConfidence(double score) {
        if (score <= 0.0D) {
            return 0.2D;
        }
        // 真实检索返回的是融合排序分，不是校准后的概率值；对 Deep Research top hits
        // 需要映射到可投影的 confidence 区间，否则 ARTICLE/SOURCE_FILE 永远过不了 v2.6 证据门槛。
        if (score < 0.55D) {
            return Math.min(0.95D, 0.55D + score);
        }
        if (score >= 1.0D) {
            return 1.0D;
        }
        return score;
    }
    /**
     * 从已有检索命中生成最小降级摘要。
     *
     * @param hits 检索命中
     * @return 降级摘要
     */
    protected String fallbackSummaryFromHits(List<QueryArticleHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return "";
        }
        QueryArticleHit firstHit = hits.get(0);
        return firstHit.getTitle() + "：" + extractEvidenceSnippet(firstHit);
    }

    protected static class StructuredEvidenceBundle {

        protected List<EvidenceAnchor> evidenceAnchors = new ArrayList<EvidenceAnchor>();

        protected List<FactFinding> factFindings = new ArrayList<FactFinding>();

        protected List<EvidenceAnchor> getEvidenceAnchors() {
            return evidenceAnchors;
        }

        protected List<FactFinding> getFactFindings() {
            return factFindings;
        }

        protected boolean isValid() {
            return !evidenceAnchors.isEmpty() && !factFindings.isEmpty();
        }
    }
}
