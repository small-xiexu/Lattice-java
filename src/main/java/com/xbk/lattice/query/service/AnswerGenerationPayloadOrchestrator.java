package com.xbk.lattice.query.service;

import com.xbk.lattice.llm.service.LlmInvocationEnvelope;
import com.xbk.lattice.llm.service.PromptCacheWritePolicy;
import com.xbk.lattice.query.domain.AnswerOutcome;
import com.xbk.lattice.query.domain.GenerationMode;
import com.xbk.lattice.query.domain.ModelExecutionStatus;
import com.xbk.lattice.query.domain.QueryAnswerPayload;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 答案生成载荷编排器
 *
 * 职责：编排单篇兜底、结构化 LLM 生成、Markdown 复用、修订兜底与答案语义归一化
 *
 * @author xiexu
 */
@Slf4j
final class AnswerGenerationPayloadOrchestrator {

    private static final String NO_KNOWLEDGE_MESSAGE = "当前未找到与该问题直接相关的知识。";

    private static final int PROMPT_AUDIT_QUERY_TOKEN_LIMIT = 12;

    private static final String PROMPT_AUDIT_SNAPSHOT_ENABLED_ENV =
            "LATTICE_QUERY_ANSWER_PROMPT_AUDIT_SNAPSHOT_ENABLED";

    private static final String[] PROMPT_AUDIT_SECTION_TITLES = {
            "QUESTION-FOCUSED EVIDENCE",
            "CONTRIBUTION EVIDENCE",
            "STRUCTURED FACT CARD EVIDENCE",
            "SOURCE EVIDENCE",
            "GRAPH EVIDENCE",
            "ARTICLE EVIDENCE"
    };

    private static final String[] INSUFFICIENT_EVIDENCE_SIGNALS = {
            "当前证据不足",
            "证据不足",
            "没有足够信息",
            "缺少直接证据",
            "缺乏直接证据",
            "缺少可验证证据",
            "缺乏可验证证据",
            "无法确认",
            "不能确认",
            "无法判定",
            "不能判定",
            "无法判断",
            "不能判断",
            "无法确定",
            "不能确定",
            "未提供",
            "没有提供",
            "未明确说明",
            "没有明确说明",
            "无法得出",
            "不能得出",
            "不足以判断",
            "不支持直接判断",
            "insufficient evidence",
            "not enough evidence",
            "no direct evidence",
            "cannot confirm",
            "cannot determine",
            "not provided"
    };

    private static final String[] INSUFFICIENT_EVIDENCE_CONCLUSION_PREFIXES = {
            "结论",
            "因此",
            "所以",
            "综上",
            "整体来看",
            "总体来看",
            "基于当前证据",
            "根据当前证据",
            "从当前证据看",
            "基于证据"
    };

    private final AnswerGenerationService support;

    /**
     * 创建答案生成载荷编排器。
     *
     * @param support 答案生成支撑服务
     */
    AnswerGenerationPayloadOrchestrator(AnswerGenerationService support) {
        this.support = support;
    }

    /**
     * 基于单条文章命中生成确定性答案。
     *
     * @param question 查询问题
     * @param articleHit 文章命中
     * @return Markdown 答案
     */
    String generateSingleArticleAnswer(String question, QueryArticleHit articleHit) {
        if (articleHit == null) {
            return NO_KNOWLEDGE_MESSAGE;
        }

        List<String> queryTokens = support.extractQueryTokens(question);
        List<String> matchedLines = support.selectQuestionFocusedFallbackSnippets(
                question,
                articleHit,
                queryTokens,
                support.desiredStructuredFactCount(question)
        );
        if (matchedLines.isEmpty()) {
            matchedLines = support.selectMatchedLines(articleHit.getContent(), queryTokens);
        }

        StringBuilder answerBuilder = new StringBuilder();
        answerBuilder.append(articleHit.getTitle());
        if (!matchedLines.isEmpty()) {
            answerBuilder.append("：").append(String.join("；", matchedLines));
            answerBuilder.append(" ").append(support.resolveCitationLiteral(articleHit));
            return SensitiveTextMasker.mask(answerBuilder.toString());
        }

        String description = support.extractDescription(articleHit.getMetadataJson());
        if (!description.isEmpty()) {
            answerBuilder.append("：").append(description);
            answerBuilder.append(" ").append(support.resolveCitationLiteral(articleHit));
            return SensitiveTextMasker.mask(answerBuilder.toString());
        }

        answerBuilder.append("：").append(articleHit.getContent());
        answerBuilder.append(" ").append(support.resolveCitationLiteral(articleHit));
        return SensitiveTextMasker.mask(answerBuilder.toString());
    }

    /**
     * 基于多路证据生成结构化答案载荷。
     *
     * @param scopeId 作用域标识
     * @param scene 场景
     * @param agentRole Agent 角色
     * @param question 查询问题
     * @param queryArticleHits 融合命中
     * @return 结构化答案载荷
     */
    QueryAnswerPayload generatePayload(
            String scopeId,
            String scene,
            String agentRole,
            String question,
            List<QueryArticleHit> queryArticleHits
    ) {
        if (queryArticleHits == null || queryArticleHits.isEmpty()) {
            return QueryAnswerPayload.ruleBased("当前未找到与该问题直接相关的知识。", AnswerOutcome.NO_RELEVANT_KNOWLEDGE);
        }
        if (support.containsOnlyArticleEvidence(queryArticleHits)) {
            QueryArticleHit articleHit = queryArticleHits.get(0);
            return QueryAnswerPayload.ruleBased(
                    generateSingleArticleAnswer(question, articleHit),
                    support.resolveSingleArticleAnswerOutcome(question, articleHit)
            );
        }

        QueryAnswerPayload llmPayload = generatePayloadByLlm(scopeId, scene, agentRole, question, queryArticleHits);
        if (llmPayload != null) {
            return llmPayload;
        }
        if (!support.answerLlmInvoker.isAvailable()) {
            return support.buildEvidencePayload(
                    question,
                    queryArticleHits,
                    null,
                    GenerationMode.RULE_BASED,
                    ModelExecutionStatus.SKIPPED,
                    ""
            );
        }
        return buildLlmFallbackPayload(question, queryArticleHits);
    }

    /**
     * 调用 LLM 并解析结构化答案载荷。
     *
     * @param scopeId 作用域标识
     * @param scene 场景
     * @param agentRole Agent 角色
     * @param question 查询问题
     * @param queryArticleHits 融合命中
     * @return LLM 载荷；失败返回 null
     */
    private QueryAnswerPayload generatePayloadByLlm(
            String scopeId,
            String scene,
            String agentRole,
            String question,
            List<QueryArticleHit> queryArticleHits
    ) {
        if (!support.answerLlmInvoker.isAvailable()) {
            return null;
        }
        try {
            String answerPrompt = support.answerPromptBuilder.buildAnswerPrompt(question, queryArticleHits);
            logPromptAudit(question, answerPrompt);
            LlmInvocationEnvelope envelope = support.answerLlmInvoker.invokeRawWithScope(
                    scopeId,
                    scene,
                    agentRole,
                    "query-answer-structured",
                    support.answerPromptBuilder.systemQueryAnswer(),
                    answerPrompt
            );
            QueryAnswerPayload parsedPayload = parseLlmPayload(envelope, question, queryArticleHits);
            if (parsedPayload != null) {
                return parsedPayload;
            }
        }
        catch (RuntimeException ex) {
            return support.buildEvidencePayload(
                    question,
                    queryArticleHits,
                    AnswerOutcome.PARTIAL_ANSWER,
                    GenerationMode.FALLBACK,
                    ModelExecutionStatus.FAILED,
                    AnswerGenerationService.FALLBACK_REASON_LLM_CALL_FAILED
            );
        }
        return null;
    }

    /**
     * 记录答案 LLM Prompt 的运行时审计摘要，不改变 Prompt 内容。
     *
     * @param question 用户问题
     * @param answerPrompt 答案 Prompt
     */
    private void logPromptAudit(String question, String answerPrompt) {
        String safePrompt = answerPrompt == null ? "" : answerPrompt;
        String evidencePrompt = extractEvidencePrompt(safePrompt);
        log.info(
                "Answer LLM prompt audit. promptLength: {}, sectionAudit: {}, evidenceQueryTermPresence: {}, "
                        + "containsTruncatedSuffix: {}, containsOmittedMarker: {}",
                Integer.valueOf(safePrompt.length()),
                buildSectionAuditSummary(safePrompt),
                buildEvidenceQueryTermPresenceSummary(question, evidencePrompt),
                Boolean.valueOf(safePrompt.contains(AnswerGenerationBaseSupport.PROMPT_TRUNCATED_SUFFIX)),
                Boolean.valueOf(safePrompt.contains("OMITTED:"))
        );
        if (isPromptAuditSnapshotEnabled()) {
            log.info("Answer LLM prompt snapshot. maskedPrompt: {}", SensitiveTextMasker.mask(safePrompt));
        }
        else if (log.isDebugEnabled()) {
            log.debug("Answer LLM prompt snapshot. maskedPrompt: {}", SensitiveTextMasker.mask(safePrompt));
        }
    }

    /**
     * 判断是否输出脱敏后的完整 Prompt snapshot。
     *
     * @return 是否启用 snapshot 审计
     */
    private boolean isPromptAuditSnapshotEnabled() {
        String enabled = System.getenv(PROMPT_AUDIT_SNAPSHOT_ENABLED_ENV);
        if (enabled == null || enabled.isBlank()) {
            enabled = System.getProperty(PROMPT_AUDIT_SNAPSHOT_ENABLED_ENV);
        }
        if (enabled == null || enabled.isBlank()) {
            return false;
        }
        return "1".equals(enabled.trim())
                || "true".equalsIgnoreCase(enabled.trim())
                || "yes".equalsIgnoreCase(enabled.trim());
    }

    /**
     * 截取 Prompt 中证据段起始后的内容。
     *
     * @param answerPrompt 答案 Prompt
     * @return 证据 Prompt 内容
     */
    private String extractEvidencePrompt(String answerPrompt) {
        if (answerPrompt == null || answerPrompt.isBlank()) {
            return "";
        }
        int evidenceStart = answerPrompt.indexOf(PROMPT_AUDIT_SECTION_TITLES[0]);
        if (evidenceStart < 0) {
            return "";
        }
        return answerPrompt.substring(evidenceStart);
    }

    /**
     * 构建证据段摘要审计。
     *
     * @param answerPrompt 答案 Prompt
     * @return 证据段摘要
     */
    private String buildSectionAuditSummary(String answerPrompt) {
        StringBuilder summaryBuilder = new StringBuilder();
        String safePrompt = answerPrompt == null ? "" : answerPrompt;
        for (String sectionTitle : PROMPT_AUDIT_SECTION_TITLES) {
            if (summaryBuilder.length() > 0) {
                summaryBuilder.append("; ");
            }
            String sectionContent = extractPromptSection(safePrompt, sectionTitle);
            boolean present = !sectionContent.isEmpty() || safePrompt.contains(sectionTitle);
            boolean containsTruncatedSuffix = sectionContent.contains(AnswerGenerationBaseSupport.PROMPT_TRUNCATED_SUFFIX);
            boolean containsOmittedMarker = sectionContent.contains("OMITTED:");
            summaryBuilder.append(sectionTitle)
                    .append("[present=")
                    .append(present)
                    .append(", length=")
                    .append(sectionContent.length())
                    .append(", truncated=")
                    .append(containsTruncatedSuffix)
                    .append(", omitted=")
                    .append(containsOmittedMarker)
                    .append("]");
        }
        return summaryBuilder.toString();
    }

    /**
     * 提取指定 Prompt section 的正文。
     *
     * @param answerPrompt 答案 Prompt
     * @param sectionTitle section 标题
     * @return section 正文
     */
    private String extractPromptSection(String answerPrompt, String sectionTitle) {
        if (answerPrompt == null || answerPrompt.isBlank() || sectionTitle == null || sectionTitle.isBlank()) {
            return "";
        }
        int sectionStart = answerPrompt.indexOf(sectionTitle);
        if (sectionStart < 0) {
            return "";
        }
        int contentStart = sectionStart + sectionTitle.length();
        int nextSectionStart = answerPrompt.length();
        for (String candidateTitle : PROMPT_AUDIT_SECTION_TITLES) {
            if (candidateTitle.equals(sectionTitle)) {
                continue;
            }
            int candidateStart = answerPrompt.indexOf(candidateTitle, contentStart);
            if (candidateStart >= 0 && candidateStart < nextSectionStart) {
                nextSectionStart = candidateStart;
            }
        }
        return answerPrompt.substring(contentStart, nextSectionStart).trim();
    }

    /**
     * 构建查询高信号 token 在证据 Prompt 中的出现情况。
     *
     * @param question 用户问题
     * @param evidencePrompt 证据 Prompt
     * @return token 出现情况摘要
     */
    private String buildEvidenceQueryTermPresenceSummary(String question, String evidencePrompt) {
        List<String> queryTokens = support.extractQueryTokens(question);
        Map<String, Boolean> termPresence = new LinkedHashMap<String, Boolean>();
        String normalizedEvidencePrompt = evidencePrompt == null ? "" : evidencePrompt.toLowerCase(Locale.ROOT);
        int tokenCount = 0;
        for (String queryToken : queryTokens) {
            if (queryToken == null || queryToken.isBlank()) {
                continue;
            }
            String normalizedQueryToken = queryToken.toLowerCase(Locale.ROOT);
            String safeQueryToken = SensitiveTextMasker.mask(normalizedQueryToken);
            termPresence.put(safeQueryToken, Boolean.valueOf(normalizedEvidencePrompt.contains(normalizedQueryToken)));
            tokenCount++;
            if (tokenCount >= PROMPT_AUDIT_QUERY_TOKEN_LIMIT) {
                break;
            }
        }
        return termPresence.toString();
    }

    /**
     * 解析 LLM 原始输出。
     *
     * @param envelope LLM 调用结果
     * @param question 查询问题
     * @param queryArticleHits 融合命中
     * @return 解析出的载荷；不可复用返回 null
     */
    private QueryAnswerPayload parseLlmPayload(
            LlmInvocationEnvelope envelope,
            String question,
            List<QueryArticleHit> queryArticleHits
    ) {
        String llmAnswer = envelope.getContent();
        QueryAnswerPayload parsedPayload = support.answerPayloadParser.parseStructuredAnswerPayload(
                llmAnswer,
                question,
                queryArticleHits
        );
        if (parsedPayload != null) {
            PromptCacheWritePolicy writePolicy = support.resolvePromptCacheWritePolicy(parsedPayload);
            support.answerLlmInvoker.applyPromptCacheWritePolicy(envelope, writePolicy);
            return parsedPayload;
        }
        support.answerLlmInvoker.applyPromptCacheWritePolicy(envelope, PromptCacheWritePolicy.EVICT_AFTER_READ);
        QueryAnswerPayload markdownPayload = support.parseMarkdownAnswerPayload(llmAnswer, question, queryArticleHits);
        if (markdownPayload != null) {
            return markdownPayload;
        }
        if (support.canReuseMarkdownAnswer(llmAnswer)) {
            List<QueryArticleHit> fallbackHits = support.selectFallbackEvidenceHits(question, queryArticleHits);
            AnswerOutcome outcome = support.resolveFallbackAnswerOutcome(question, fallbackHits, null);
            return new QueryAnswerPayload(
                    SensitiveTextMasker.mask(llmAnswer.trim()),
                    outcome,
                    GenerationMode.FALLBACK,
                    ModelExecutionStatus.DEGRADED,
                    false,
                    AnswerGenerationService.FALLBACK_REASON_LLM_UNSTRUCTURED_FALLBACK
            );
        }
        return null;
    }

    /**
     * 构造 LLM 可用但输出不可用时的 fallback 载荷。
     *
     * @param question 查询问题
     * @param queryArticleHits 查询命中
     * @return fallback 载荷
     */
    private QueryAnswerPayload buildLlmFallbackPayload(String question, List<QueryArticleHit> queryArticleHits) {
        return support.buildEvidencePayload(
                question,
                queryArticleHits,
                AnswerOutcome.PARTIAL_ANSWER,
                GenerationMode.FALLBACK,
                ModelExecutionStatus.DEGRADED,
                AnswerGenerationService.FALLBACK_REASON_LLM_OUTPUT_INVALID
        );
    }

    /**
     * 基于纠正信息重生成修订答案。
     *
     * @param scopeId 作用域标识
     * @param scene 场景
     * @param agentRole Agent 角色
     * @param question 查询问题
     * @param currentAnswer 当前答案
     * @param correction 用户纠正
     * @param queryArticleHits 修订证据
     * @return 修订后的 Markdown 答案
     */
    String revise(
            String scopeId,
            String scene,
            String agentRole,
            String question,
            String currentAnswer,
            String correction,
            List<QueryArticleHit> queryArticleHits
    ) {
        if (support.answerLlmInvoker.isAvailable()) {
            try {
                String revisePrompt = support.answerPromptBuilder.buildRevisePrompt(
                        question,
                        currentAnswer,
                        correction,
                        queryArticleHits
                );
                String llmAnswer = support.answerLlmInvoker.generateTextWithScope(
                        scopeId,
                        scene,
                        agentRole,
                        "query-revise",
                        support.answerPromptBuilder.systemQueryRevise(),
                        revisePrompt
                );
                if (llmAnswer != null && !llmAnswer.isBlank()) {
                    return llmAnswer.trim();
                }
            }
            catch (RuntimeException ex) {
                // 修订阶段沿用确定性 Markdown 兜底，避免用户反馈闭环被外部模型阻塞。
            }
        }
        return support.answerFallbackMarkdownBuilder.buildRevisionEvidenceMarkdown(
                question,
                currentAnswer,
                correction,
                queryArticleHits
        );
    }

    /**
     * 当模型已经给出可支撑的操作或枚举清单时，把过度保守的 PARTIAL 口径收敛为 SUCCESS。
     *
     * @param answerOutcome 模型声明的答案语义
     * @param answerMarkdown 答案正文
     * @param question 用户问题
     * @param queryArticleHits 查询命中
     * @return 规范化后的答案语义
     */
    AnswerOutcome normalizeStructuredAnswerOutcome(
            AnswerOutcome answerOutcome,
            String answerMarkdown,
            String question,
            List<QueryArticleHit> queryArticleHits
    ) {
        if (answerOutcome == AnswerOutcome.SUCCESS && looksLikeInsufficientEvidenceAnswer(answerMarkdown)) {
            return AnswerOutcome.INSUFFICIENT_EVIDENCE;
        }
        if (answerOutcome != AnswerOutcome.PARTIAL_ANSWER) {
            return answerOutcome;
        }
        String normalizedAnswer = support.lowerCase(answerMarkdown);
        if (normalizedAnswer.contains("当前证据不足") || normalizedAnswer.contains("暂无法确认")) {
            return answerOutcome;
        }
        if (support.looksLikeExactLookupQuestion(question)) {
            List<QueryArticleHit> fallbackHits = support.selectFallbackEvidenceHits(question, queryArticleHits);
            if (!fallbackHits.isEmpty()
                    && support.isDirectFallbackAnswerable(question, fallbackHits.get(0))
                    && support.coversExactLookupAnswerText(answerMarkdown, question)
                    && support.isExactLookupAnswerGroundedByFocusedEvidence(question, fallbackHits, answerMarkdown)) {
                return AnswerOutcome.SUCCESS;
            }
        }
        if (!support.looksLikeEnumerationQuestion(question) && !support.looksLikeFlowQuestion(question)) {
            return answerOutcome;
        }
        List<QueryArticleHit> fallbackHits = support.selectFallbackEvidenceHits(question, queryArticleHits);
        if (fallbackHits.isEmpty()) {
            return answerOutcome;
        }
        return support.isDirectFallbackAnswerable(question, fallbackHits.get(0))
                ? AnswerOutcome.SUCCESS
                : answerOutcome;
    }

    /**
     * 判断 SUCCESS 载荷正文是否整体更像证据不足或无法判定，而非可缓存的完整回答。
     *
     * @param answerMarkdown 答案正文
     * @return 应下调答案语义返回 true
     */
    private boolean looksLikeInsufficientEvidenceAnswer(String answerMarkdown) {
        if (answerMarkdown == null || answerMarkdown.isBlank()) {
            return false;
        }
        String normalizedAnswer = support.lowerCase(support.stripEmbeddedCitationLiterals(answerMarkdown));
        String firstAnswerLine = firstMeaningfulAnswerLine(normalizedAnswer);
        if (containsEarlyInsufficientEvidenceSignal(firstAnswerLine)) {
            return true;
        }
        int signalLineCount = 0;
        for (String rawLine : normalizedAnswer.split("\\R")) {
            String normalizedLine = normalizeOutcomeLine(rawLine);
            if (normalizedLine.isBlank()) {
                continue;
            }
            if (!containsInsufficientEvidenceSignal(normalizedLine)) {
                continue;
            }
            signalLineCount++;
            if (signalLineCount >= 2 && looksLikeInsufficientEvidenceConclusionLine(normalizedLine)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 提取第一条承载答案语义的正文行。
     *
     * @param normalizedAnswer 已归一化答案
     * @return 正文行；没有则返回空串
     */
    private String firstMeaningfulAnswerLine(String normalizedAnswer) {
        for (String rawLine : normalizedAnswer.split("\\R")) {
            String normalizedLine = normalizeOutcomeLine(rawLine);
            if (normalizedLine.isBlank() || isGenericAnswerSectionLabel(normalizedLine)) {
                continue;
            }
            return normalizedLine;
        }
        return "";
    }

    /**
     * 归一化用于 outcome 判断的答案行。
     *
     * @param rawLine 原始行
     * @return 归一化后的行
     */
    private String normalizeOutcomeLine(String rawLine) {
        if (rawLine == null) {
            return "";
        }
        return rawLine.trim()
                .replaceFirst("^#+\\s*", "")
                .replaceFirst("^(?:[-*+]|\\d+[.)]|[（(]?[一二三四五六七八九十]+[）).、])\\s*", "")
                .replace("`", "")
                .replace("|", " ")
                .trim();
    }

    /**
     * 判断是否为答案包装中的通用章节标签。
     *
     * @param line 归一化行
     * @return 章节标签返回 true
     */
    private boolean isGenericAnswerSectionLabel(String line) {
        return "查询回答".equals(line)
                || "问题".equals(line)
                || "证据".equals(line)
                || "参考说明".equals(line);
    }

    /**
     * 判断答案首行是否在开头附近表达证据不足。
     *
     * @param line 答案首行
     * @return 命中返回 true
     */
    private boolean containsEarlyInsufficientEvidenceSignal(String line) {
        if (line == null || line.isBlank()) {
            return false;
        }
        for (String signal : INSUFFICIENT_EVIDENCE_SIGNALS) {
            int signalIndex = line.indexOf(signal);
            if (signalIndex >= 0 && signalIndex <= 12) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断结论行是否表达证据不足。
     *
     * @param line 归一化行
     * @return 命中返回 true
     */
    private boolean looksLikeInsufficientEvidenceConclusionLine(String line) {
        if (!containsInsufficientEvidenceSignal(line)) {
            return false;
        }
        for (String prefix : INSUFFICIENT_EVIDENCE_CONCLUSION_PREFIXES) {
            if (line.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断文本是否包含通用证据不足信号。
     *
     * @param line 归一化行
     * @return 命中返回 true
     */
    private boolean containsInsufficientEvidenceSignal(String line) {
        if (line == null || line.isBlank()) {
            return false;
        }
        for (String signal : INSUFFICIENT_EVIDENCE_SIGNALS) {
            if (line.contains(signal)) {
                return true;
            }
        }
        return false;
    }
}
