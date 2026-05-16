package com.xbk.lattice.query.service;

import com.xbk.lattice.article.service.ArticleMarkdownSupport;
import com.xbk.lattice.compiler.service.LlmGateway;
import com.xbk.lattice.llm.service.ExecutionLlmSnapshotService;
import com.xbk.lattice.llm.service.LlmInvocationEnvelope;
import com.xbk.lattice.llm.service.PromptCacheWritePolicy;
import com.xbk.lattice.query.domain.AnswerOutcome;
import com.xbk.lattice.query.domain.GenerationMode;
import com.xbk.lattice.query.domain.ModelExecutionStatus;
import com.xbk.lattice.query.domain.QueryAnswerPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * 答案生成 Prompt 证据支持
 *
 * 职责：承载 AnswerGenerationService 拆分出来的同类辅助逻辑
 *
 * @author xiexu
 */
@Slf4j
abstract class AnswerGenerationPromptEvidenceSupport extends AnswerGenerationFallbackConclusionSupport {

    private static final int PROMPT_FOCUS_DISTRIBUTED_SNIPPET_LIMIT = 3;

    private static final int PROMPT_FOCUS_WINDOW_CHAR_LIMIT = 560;

    private static final int PROMPT_FOCUS_WINDOW_FORWARD_LINE_LIMIT = 6;

    /**
     * 创建无 LLM 的答案生成拆分支持。
     */
    AnswerGenerationPromptEvidenceSupport() {
        super();
    }

    /**
     * 创建答案生成拆分支持。
     *
     * @param llmGateway LLM 网关
     */
    AnswerGenerationPromptEvidenceSupport(LlmGateway llmGateway) {
        super(llmGateway);
    }

Map<QueryEvidenceType, List<QueryArticleHit>> groupHitsByEvidenceType(List<QueryArticleHit> queryArticleHits) {
        Map<QueryEvidenceType, List<QueryArticleHit>> groupedHits =
                new EnumMap<QueryEvidenceType, List<QueryArticleHit>>(QueryEvidenceType.class);
        for (QueryEvidenceType queryEvidenceType : QueryEvidenceType.values()) {
            groupedHits.put(queryEvidenceType, new ArrayList<QueryArticleHit>());
        }
        for (QueryArticleHit queryArticleHit : queryArticleHits) {
            QueryEvidenceType evidenceType = resolvePromptEvidenceType(queryArticleHit);
            groupedHits.get(evidenceType).add(queryArticleHit);
        }
        return groupedHits;
    }

    /**
     * 解析 Prompt 中使用的证据分组类型。
     *
     * @param queryArticleHit 查询命中
     * @return Prompt 证据类型
     */
    QueryEvidenceType resolvePromptEvidenceType(QueryArticleHit queryArticleHit) {
        if (queryArticleHit == null) {
            return QueryEvidenceType.ARTICLE;
        }
        if (queryArticleHit.getEvidenceType() == QueryEvidenceType.FACT_CARD
                && FactCardReviewUsagePolicy.isBackgroundOnly(queryArticleHit.getReviewStatus())) {
            return QueryEvidenceType.ARTICLE;
        }
        return queryArticleHit.getEvidenceType();
    }

    /**
     * 追加单个证据分组。
     *
     * @param promptBuilder Prompt 构建器
     * @param sectionTitle 段落标题
     * @param queryArticleHits 证据列表
     */
    void appendEvidenceSection(
            StringBuilder promptBuilder,
            String sectionTitle,
            List<QueryArticleHit> queryArticleHits,
            List<QueryArticleHit> citationCandidateHits,
            String question,
            List<String> queryTokens
    ) {
        promptBuilder.append(sectionTitle).append("\n");
        if (queryArticleHits == null || queryArticleHits.isEmpty()) {
            promptBuilder.append("- NONE").append("\n\n");
            return;
        }
        int appendedHitCount = 0;
        for (QueryArticleHit queryArticleHit : queryArticleHits) {
            if (appendedHitCount >= PROMPT_EVIDENCE_SECTION_HIT_LIMIT) {
                promptBuilder.append("- OMITTED: evidence section hit limit reached").append("\n");
                break;
            }
            if (isPromptEvidenceBudgetExhausted(promptBuilder)) {
                promptBuilder.append("- OMITTED: prompt evidence budget exhausted").append("\n");
                break;
            }
            List<String> focusSnippets = buildPromptFocusSnippets(question, queryTokens, queryArticleHit);
            boolean fullyAppended = appendPromptLineWithinBudget(
                    promptBuilder,
                    "- title: " + normalizePromptInlineText(queryArticleHit.getTitle())
            );
            fullyAppended = fullyAppended && appendPromptLineWithinBudget(
                    promptBuilder,
                    "  id: " + normalizePromptInlineText(queryArticleHit.getConceptId())
            );
            fullyAppended = fullyAppended && appendPromptLineWithinBudget(
                    promptBuilder,
                    "  sources: " + normalizePromptInlineText(String.join(", ", queryArticleHit.getSourcePaths()))
            );
            fullyAppended = fullyAppended && appendPromptLineWithinBudget(
                    promptBuilder,
                    "  citation: " + resolveCitationLiteral(queryArticleHit, citationCandidateHits)
            );
            if (fullyAppended && !focusSnippets.isEmpty()) {
                fullyAppended = appendPromptLineWithinBudget(promptBuilder, "  focus_snippets:");
                for (String focusSnippet : focusSnippets) {
                    if (!fullyAppended) {
                        break;
                    }
                    fullyAppended = appendPromptLineWithinBudget(promptBuilder, "    - " + focusSnippet);
                }
            }
            String evidenceContent = buildBoundedPromptEvidenceContent(queryTokens, queryArticleHit, focusSnippets);
            fullyAppended = fullyAppended && appendPromptLineWithinBudget(promptBuilder, "  content: " + evidenceContent);
            String metadata = truncatePromptText(
                    normalizePromptInlineText(SensitiveTextMasker.mask(queryArticleHit.getMetadataJson())),
                    PROMPT_EVIDENCE_METADATA_CHAR_LIMIT
            );
            fullyAppended = fullyAppended && appendPromptLineWithinBudget(promptBuilder, "  metadata: " + metadata);
            appendedHitCount++;
            if (!fullyAppended) {
                promptBuilder.append("- OMITTED: prompt evidence budget exhausted").append("\n");
                break;
            }
        }
        promptBuilder.append("\n");
    }

    /**
     * 判断回答 Prompt 的证据预算是否已经耗尽。
     *
     * @param promptBuilder Prompt 构建器
     * @return 耗尽返回 true
     */
    boolean isPromptEvidenceBudgetExhausted(StringBuilder promptBuilder) {
        return promptBuilder.length() >= PROMPT_USER_PROMPT_CHAR_LIMIT;
    }

    /**
     * 在 Prompt 剩余预算内追加一行。
     *
     * @param promptBuilder Prompt 构建器
     * @param line 待追加行
     * @return 完整追加返回 true，被截断或跳过返回 false
     */
    boolean appendPromptLineWithinBudget(StringBuilder promptBuilder, String line) {
        int remainingBudget = PROMPT_USER_PROMPT_CHAR_LIMIT - promptBuilder.length();
        if (remainingBudget <= 0) {
            return false;
        }
        String safeLine = line == null ? "" : line;
        int requiredLength = safeLine.length() + 1;
        if (requiredLength <= remainingBudget) {
            promptBuilder.append(safeLine).append("\n");
            return true;
        }
        if (remainingBudget <= PROMPT_TRUNCATED_SUFFIX.length() + 8) {
            return false;
        }
        promptBuilder.append(truncatePromptText(safeLine, remainingBudget - 1)).append("\n");
        return false;
    }

    /**
     * 构建单条命中的有界证据正文。
     *
     * @param queryTokens 查询 token
     * @param queryArticleHit 查询命中
     * @param focusSnippets 贴题证据句
     * @return 有界证据正文
     */
    String buildBoundedPromptEvidenceContent(
            List<String> queryTokens,
            QueryArticleHit queryArticleHit,
            List<String> focusSnippets
    ) {
        List<String> candidateParts = new ArrayList<String>();
        if (focusSnippets != null) {
            for (String focusSnippet : focusSnippets) {
                appendDistinctPromptEvidencePart(candidateParts, focusSnippet);
            }
        }
        String fallbackSnippet = selectFallbackEvidenceSnippet(queryArticleHit, queryTokens);
        appendDistinctPromptEvidencePart(candidateParts, fallbackSnippet);
        if (candidateParts.isEmpty()) {
            String boundedContent = sanitizeEvidenceContentForPrompt(
                    queryArticleHit.getContent(),
                    PROMPT_EVIDENCE_CONTENT_CHAR_LIMIT
            );
            return normalizePromptInlineText(boundedContent);
        }
        String focusedContent = String.join(" | ", candidateParts);
        if (focusedContent.length() >= PROMPT_EVIDENCE_CONTENT_CHAR_LIMIT) {
            return truncatePromptText(focusedContent, PROMPT_EVIDENCE_CONTENT_CHAR_LIMIT);
        }
        int contextBudget = PROMPT_EVIDENCE_CONTENT_CHAR_LIMIT - focusedContent.length() - " | context: ".length();
        if (contextBudget > 120) {
            String boundedContent = normalizePromptInlineText(sanitizeEvidenceContentForPrompt(queryArticleHit.getContent(), contextBudget));
            if (!boundedContent.isBlank()) {
                focusedContent = focusedContent + " | context: " + boundedContent;
            }
        }
        return truncatePromptText(focusedContent, PROMPT_EVIDENCE_CONTENT_CHAR_LIMIT);
    }

    /**
     * 追加去重后的 Prompt 证据片段。
     *
     * @param candidateParts 候选片段
     * @param rawPart 原始片段
     */
    void appendDistinctPromptEvidencePart(List<String> candidateParts, String rawPart) {
        String normalizedPart = normalizePromptInlineText(rawPart);
        if (normalizedPart.isBlank()) {
            return;
        }
        for (String candidatePart : candidateParts) {
            if (candidatePart.equals(normalizedPart)) {
                return;
            }
        }
        candidateParts.add(normalizedPart);
    }

    /**
     * 把 Prompt 证据文本归一化为单行。
     *
     * @param text 原始文本
     * @return 单行文本
     */
    String normalizePromptInlineText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.replace("\r", " ")
                .replace("\n", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    /**
     * 追加与当前问题最贴近的证据句，降低模型在长文章里抓错焦点的概率。
     *
     * @param promptBuilder Prompt 构建器
     * @param question 用户问题
     * @param queryArticleHits 查询命中
     * @param queryTokens 查询 token
     */
    void appendQuestionFocusedEvidenceSection(
            StringBuilder promptBuilder,
            String question,
            List<QueryArticleHit> queryArticleHits,
            List<String> queryTokens
    ) {
        promptBuilder.append("QUESTION-FOCUSED EVIDENCE").append("\n");
        if (queryArticleHits == null || queryArticleHits.isEmpty()) {
            promptBuilder.append("- NONE").append("\n\n");
            return;
        }
        List<QueryArticleHit> sortedHits = sortPromptEvidenceHits(question, queryArticleHits, queryTokens);
        int evidenceCount = 0;
        for (QueryArticleHit queryArticleHit : sortedHits) {
            List<String> focusSnippets = buildPromptFocusSnippets(question, queryTokens, queryArticleHit);
            if (focusSnippets.isEmpty()) {
                continue;
            }
            promptBuilder.append("- title: ").append(queryArticleHit.getTitle()).append("\n");
            promptBuilder.append("  citation: ").append(resolveCitationLiteral(queryArticleHit, sortedHits)).append("\n");
            promptBuilder.append("  snippets:").append("\n");
            for (String focusSnippet : focusSnippets) {
                promptBuilder.append("    - ").append(focusSnippet).append("\n");
            }
            evidenceCount++;
            if (evidenceCount >= 6) {
                break;
            }
        }
        if (evidenceCount == 0) {
            promptBuilder.append("- NONE").append("\n");
        }
        promptBuilder.append("\n");
    }

    /**
     * 为 Prompt 证据段挑选若干条更贴题的证据句。
     *
     * @param question 用户问题
     * @param queryTokens 查询 token
     * @param queryArticleHit 查询命中
     * @return 贴题证据句
     */
    List<String> buildPromptFocusSnippets(
            String question,
            List<String> queryTokens,
            QueryArticleHit queryArticleHit
    ) {
        if (queryArticleHit == null) {
            return List.of();
        }
        List<String> effectiveQueryTokens = queryTokens == null ? extractQueryTokens(question) : queryTokens;
        int snippetCount = resolvePromptFocusSnippetLimit(question);
        List<String> snippets = new ArrayList<String>();
        if (shouldUseDistributedPromptFocusSnippets(question)) {
            snippets.addAll(selectDistributedPromptFocusSnippets(
                    question,
                    effectiveQueryTokens,
                    queryArticleHit,
                    snippetCount
            ));
        }
        snippets.addAll(selectQuestionFocusedFallbackSnippets(
                question,
                queryArticleHit,
                effectiveQueryTokens,
                snippetCount
        ));
        List<String> sanitizedSnippets = new ArrayList<String>();
        for (String snippet : snippets) {
            String normalizedSnippet = sanitizePromptEvidenceSnippet(snippet);
            if (!normalizedSnippet.isBlank() && !sanitizedSnippets.contains(normalizedSnippet)) {
                sanitizedSnippets.add(normalizedSnippet);
            }
            if (sanitizedSnippets.size() >= snippetCount) {
                break;
            }
        }
        return sanitizedSnippets;
    }

    /**
     * 解析单条 hit 内允许放入 Prompt 的贴题片段数量。
     *
     * @param question 用户问题
     * @return 片段数量上限
     */
    private int resolvePromptFocusSnippetLimit(String question) {
        if (shouldUseDistributedPromptFocusSnippets(question)) {
            return PROMPT_FOCUS_DISTRIBUTED_SNIPPET_LIMIT;
        }
        return looksLikeExactLookupQuestion(question)
                || looksLikeEnumerationQuestion(question)
                || looksLikeFlowQuestion(question)
                ? 2
                : 1;
    }

    /**
     * 判断是否应在同一 hit 内按多个局部窗口分散选择片段。
     *
     * @param question 用户问题
     * @return 需要分散选择返回 true
     */
    private boolean shouldUseDistributedPromptFocusSnippets(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        if (looksLikePathQuestion(question) || containsRequestedExactPathIdentifier(question)) {
            return false;
        }
        return looksLikeFlowQuestion(question)
                || looksLikeEnumerationQuestion(question)
                || looksLikeStatusQuestion(question)
                || looksLikeCompoundExactLookupQuestion(question)
                || querySemanticRules.containsAnyStatusSignal(question)
                || querySemanticRules.containsAnySequenceSignal(question)
                || querySemanticRules.containsAnyMultiFocusSeparator(question);
    }

    /**
     * 从同一 hit 内容中选择覆盖不同局部窗口的贴题片段。
     *
     * @param question 用户问题
     * @param queryTokens 查询 token
     * @param queryArticleHit 查询命中
     * @param limit 片段数量上限
     * @return 贴题片段
     */
    private List<String> selectDistributedPromptFocusSnippets(
            String question,
            List<String> queryTokens,
            QueryArticleHit queryArticleHit,
            int limit
    ) {
        if (queryArticleHit == null || limit <= 0) {
            return List.of();
        }
        List<String> contentLines = selectPromptFocusContentLines(queryArticleHit.getContent());
        if (contentLines.isEmpty()) {
            return List.of();
        }
        List<PromptFocusWindowCandidate> candidates = new ArrayList<PromptFocusWindowCandidate>();
        for (int index = 0; index < contentLines.size(); index++) {
            String contentLine = contentLines.get(index);
            if (!isPromptFocusAnchorLine(question, contentLine, queryTokens)) {
                continue;
            }
            PromptFocusWindowCandidate candidate = buildPromptFocusWindowCandidate(
                    question,
                    queryTokens,
                    contentLines,
                    index
            );
            if (candidate != null) {
                candidates.add(candidate);
            }
        }
        candidates.sort((leftCandidate, rightCandidate) -> Integer.compare(
                rightCandidate.score,
                leftCandidate.score
        ));
        return selectNonOverlappingPromptFocusWindows(candidates, limit);
    }

    /**
     * 提取可用于 Prompt focus 的有序正文行。
     *
     * @param content 原始正文
     * @return 有序正文行
     */
    private List<String> selectPromptFocusContentLines(String content) {
        List<String> contentLines = new ArrayList<String>();
        String bodyContent = ArticleMarkdownSupport.extractBody(content);
        if (bodyContent == null || bodyContent.isBlank()) {
            return contentLines;
        }
        String[] rawLines = bodyContent.split("\\R");
        for (int index = 0; index < rawLines.length; index++) {
            String rawLine = rawLines[index];
            String normalizedLine = rawLine == null ? "" : rawLine.trim();
            if (normalizedLine.isEmpty()
                    || normalizedLine.startsWith("#")
                    || normalizedLine.startsWith(">")
                    || answerEvidenceNormalizer.isMarkdownTableHeaderWithDivider(
                        normalizedLine,
                        index + 1 < rawLines.length ? rawLines[index + 1] : null)
                    || answerEvidenceNormalizer.isNonTextMediaLine(normalizedLine)) {
                continue;
            }
            String plainLine = normalizedLine.startsWith("- ") ? normalizedLine.substring(2) : normalizedLine;
            String normalizedCandidate = answerEvidenceNormalizer.normalizeFallbackLineCandidate(plainLine);
            if (!normalizedCandidate.isBlank()) {
                contentLines.add(normalizedCandidate);
            }
        }
        return contentLines;
    }

    /**
     * 判断某行是否适合作为局部 focus 窗口起点。
     *
     * @param question 用户问题
     * @param contentLine 正文行
     * @param queryTokens 查询 token
     * @return 适合返回 true
     */
    private boolean isPromptFocusAnchorLine(String question, String contentLine, List<String> queryTokens) {
        if (contentLine == null || contentLine.isBlank()) {
            return false;
        }
        if (containsAnyPromptFocusQueryToken(contentLine, queryTokens)) {
            return true;
        }
        return (looksLikeFlowQuestion(question) || looksLikeStatusQuestion(question))
                && containsPromptFocusStructureSignal(contentLine);
    }

    /**
     * 构造某个 anchor 起点附近的有界 focus 窗口。
     *
     * @param question 用户问题
     * @param queryTokens 查询 token
     * @param contentLines 有序正文行
     * @param anchorIndex 起点行索引
     * @return focus 窗口候选
     */
    private PromptFocusWindowCandidate buildPromptFocusWindowCandidate(
            String question,
            List<String> queryTokens,
            List<String> contentLines,
            int anchorIndex
    ) {
        List<String> selectedLines = new ArrayList<String>();
        Set<String> coveredTokens = new LinkedHashSet<String>();
        int score = 0;
        for (int index = anchorIndex; index < contentLines.size(); index++) {
            if (index > anchorIndex + PROMPT_FOCUS_WINDOW_FORWARD_LINE_LIMIT) {
                break;
            }
            String contentLine = contentLines.get(index);
            if (index > anchorIndex && !shouldKeepPromptFocusNeighborLine(question, contentLine, queryTokens)) {
                continue;
            }
            String nextWindow = selectedLines.isEmpty()
                    ? contentLine
                    : String.join(" / ", selectedLines) + " / " + contentLine;
            if (nextWindow.length() > PROMPT_FOCUS_WINDOW_CHAR_LIMIT) {
                break;
            }
            selectedLines.add(contentLine);
            score += scorePromptFocusLine(question, contentLine, queryTokens, index - anchorIndex);
            addCoveredPromptFocusTokens(coveredTokens, contentLine, queryTokens);
        }
        if (selectedLines.isEmpty()) {
            return null;
        }
        String snippet = String.join(" / ", selectedLines);
        int coverageScore = coveredTokens.size() * 36;
        return new PromptFocusWindowCandidate(snippet, anchorIndex, score + coverageScore, coveredTokens);
    }

    /**
     * 判断相邻行是否值得并入当前 focus 窗口。
     *
     * @param question 用户问题
     * @param contentLine 正文行
     * @param queryTokens 查询 token
     * @return 值得并入返回 true
     */
    private boolean shouldKeepPromptFocusNeighborLine(String question, String contentLine, List<String> queryTokens) {
        if (contentLine == null || contentLine.isBlank()) {
            return false;
        }
        return containsAnyPromptFocusQueryToken(contentLine, queryTokens)
                || containsPromptFocusStructureSignal(contentLine)
                || looksLikePromptFocusListOrTableLine(contentLine)
                || looksLikeSequentialPromptFocusQuestion(question);
    }

    /**
     * 计算局部 focus 行的通用证据信号分。
     *
     * @param question 用户问题
     * @param contentLine 正文行
     * @param queryTokens 查询 token
     * @param distanceFromAnchor 距离 anchor 的行数
     * @return 分值
     */
    private int scorePromptFocusLine(
            String question,
            String contentLine,
            List<String> queryTokens,
            int distanceFromAnchor
    ) {
        int score = 0;
        if (containsAnyPromptFocusQueryToken(contentLine, queryTokens)) {
            score += 42;
        }
        if (containsPromptFocusStructureSignal(contentLine)) {
            score += 24;
        }
        if (looksLikePromptFocusListOrTableLine(contentLine)) {
            score += 16;
        }
        if (looksLikeSequentialPromptFocusQuestion(question)) {
            score += 18;
        }
        score -= distanceFromAnchor * 3;
        return score;
    }

    /**
     * 判断候选行是否包含查询 token。
     *
     * @param contentLine 正文行
     * @param queryTokens 查询 token
     * @return 包含返回 true
     */
    private boolean containsAnyPromptFocusQueryToken(String contentLine, List<String> queryTokens) {
        if (contentLine == null || contentLine.isBlank() || queryTokens == null || queryTokens.isEmpty()) {
            return false;
        }
        String lowerCaseLine = lowerCase(contentLine);
        for (String queryToken : queryTokens) {
            String normalizedToken = lowerCase(queryToken);
            if (!normalizedToken.isBlank() && lowerCaseLine.contains(normalizedToken)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 记录当前行覆盖到的问题 token。
     *
     * @param coveredTokens 已覆盖 token
     * @param contentLine 正文行
     * @param queryTokens 查询 token
     */
    private void addCoveredPromptFocusTokens(Set<String> coveredTokens, String contentLine, List<String> queryTokens) {
        if (coveredTokens == null || contentLine == null || queryTokens == null) {
            return;
        }
        String lowerCaseLine = lowerCase(contentLine);
        for (String queryToken : queryTokens) {
            String normalizedToken = lowerCase(queryToken);
            if (!normalizedToken.isBlank() && lowerCaseLine.contains(normalizedToken)) {
                coveredTokens.add(normalizedToken);
            }
        }
    }

    /**
     * 判断候选行是否带有结构化 evidence 信号。
     *
     * @param contentLine 正文行
     * @return 命中返回 true
     */
    private boolean containsPromptFocusStructureSignal(String contentLine) {
        if (contentLine == null || contentLine.isBlank()) {
            return false;
        }
        String lowerCaseLine = lowerCase(contentLine);
        return containsFlowSignal(contentLine)
                || containsFlowTransitionSignal(contentLine)
                || containsStatusSignal(lowerCaseLine)
                || containsCorrectionOrStatusSignal(lowerCaseLine)
                || containsSetupSignal(contentLine)
                || answerEvidenceNormalizer.startsWithDirectStructuredFactAssignment(contentLine);
    }

    /**
     * 判断候选行是否像列表、表格或连续步骤项。
     *
     * @param contentLine 正文行
     * @return 命中返回 true
     */
    private boolean looksLikePromptFocusListOrTableLine(String contentLine) {
        if (contentLine == null || contentLine.isBlank()) {
            return false;
        }
        String trimmedLine = contentLine.trim();
        return trimmedLine.startsWith("|")
                || trimmedLine.matches("^\\d+[.、].*")
                || trimmedLine.startsWith("- ")
                || trimmedLine.contains(" | ");
    }

    /**
     * 按覆盖增益挑选互不重复的局部窗口。
     *
     * @param candidates 候选窗口
     * @param limit 数量上限
     * @return 选中的窗口文本
     */
    private List<String> selectNonOverlappingPromptFocusWindows(
            List<PromptFocusWindowCandidate> candidates,
            int limit
    ) {
        List<String> snippets = new ArrayList<String>();
        List<PromptFocusWindowCandidate> selectedCandidates = new ArrayList<PromptFocusWindowCandidate>();
        Set<String> coveredTokens = new LinkedHashSet<String>();
        for (PromptFocusWindowCandidate candidate : candidates) {
            if (snippets.size() >= limit) {
                break;
            }
            if (overlapsSelectedPromptFocusWindow(candidate, selectedCandidates)
                    && coveredTokens.containsAll(candidate.coveredTokens)) {
                continue;
            }
            snippets.add(candidate.snippet);
            selectedCandidates.add(candidate);
            coveredTokens.addAll(candidate.coveredTokens);
        }
        return snippets;
    }

    /**
     * 判断候选窗口是否与已选窗口过度重叠。
     *
     * @param candidate 当前候选
     * @param selectedCandidates 已选候选
     * @return 重叠返回 true
     */
    private boolean overlapsSelectedPromptFocusWindow(
            PromptFocusWindowCandidate candidate,
            List<PromptFocusWindowCandidate> selectedCandidates
    ) {
        if (candidate == null || selectedCandidates == null || selectedCandidates.isEmpty()) {
            return false;
        }
        for (PromptFocusWindowCandidate selectedCandidate : selectedCandidates) {
            int distance = Math.abs(candidate.anchorIndex - selectedCandidate.anchorIndex);
            if (distance <= PROMPT_FOCUS_WINDOW_FORWARD_LINE_LIMIT) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断当前问题是否适合保留 anchor 后方的短连续窗口。
     *
     * @param question 用户问题
     * @return 适合返回 true
     */
    private boolean looksLikeSequentialPromptFocusQuestion(String question) {
        return looksLikeFlowQuestion(question)
                || looksLikeEnumerationQuestion(question)
                || looksLikeStatusQuestion(question)
                || querySemanticRules.containsAnyStatusSignal(question)
                || querySemanticRules.containsAnySequenceSignal(question)
                || querySemanticRules.containsAnyMultiFocusSeparator(question);
    }

    /**
     * Prompt focus 局部窗口候选。
     */
    private static final class PromptFocusWindowCandidate {

        private final String snippet;

        private final int anchorIndex;

        private final int score;

        private final Set<String> coveredTokens;

        private PromptFocusWindowCandidate(
                String snippet,
                int anchorIndex,
                int score,
                Set<String> coveredTokens
        ) {
            this.snippet = snippet;
            this.anchorIndex = anchorIndex;
            this.score = score;
            this.coveredTokens = coveredTokens;
        }
    }

    /**
     * 按当前问题重新排序 Prompt 里的证据，优先展示更可能直接回答问题的命中。
     *
     * @param question 用户问题
     * @param queryArticleHits 原始命中
     * @param queryTokens 查询 token
     * @return 排序后的命中
     */
    List<QueryArticleHit> sortPromptEvidenceHits(
            String question,
            List<QueryArticleHit> queryArticleHits,
            List<String> queryTokens
    ) {
        if (queryArticleHits == null || queryArticleHits.size() <= 1) {
            return queryArticleHits;
        }
        List<QueryArticleHit> sortedHits = new ArrayList<QueryArticleHit>(queryArticleHits);
        sortedHits.sort((leftHit, rightHit) -> {
            int scoreCompare = Integer.compare(
                    scoreQuestionFocusedFallbackHit(question, rightHit, queryTokens),
                    scoreQuestionFocusedFallbackHit(question, leftHit, queryTokens)
            );
            if (scoreCompare != 0) {
                return scoreCompare;
            }
            return Double.compare(rightHit.getScore(), leftHit.getScore());
        });
        return sortedHits;
    }

    /**
     * 清理 Prompt 中的贴题证据句，避免换行和多余空白干扰模型读取。
     *
     * @param snippet 原始证据句
     * @return 清理后的证据句
     */
    String sanitizePromptEvidenceSnippet(String snippet) {
        if (snippet == null || snippet.isBlank()) {
            return "";
        }
        return SensitiveTextMasker.mask(
                snippet.replace("\r", " ")
                        .replace("\n", " ")
                        .replaceAll("\\s{2,}", " ")
                        .trim()
        );
    }

    /**
     * 追加问题中显式点名的精确标识，提醒模型逐项覆盖。
     *
     * @param promptBuilder Prompt 构建器
     * @param question 用户问题
     */
    void appendReferentialFocusSection(StringBuilder promptBuilder, String question) {
        List<String> identifiers = extractRequestedReferentialIdentifiers(question);
        if (identifiers.isEmpty()) {
            return;
        }
        promptBuilder.append("REFERENTIAL FOCUS").append("\n");
        promptBuilder.append("- exact identifiers to cover: ").append(String.join(", ", identifiers)).append("\n\n");
    }
}
