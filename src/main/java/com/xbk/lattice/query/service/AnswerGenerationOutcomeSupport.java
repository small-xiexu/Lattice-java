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
 * 答案生成 答案语义支持
 *
 * 职责：承载 AnswerGenerationService 拆分出来的同类辅助逻辑
 *
 * @author xiexu
 */
@Slf4j
abstract class AnswerGenerationOutcomeSupport extends AnswerGenerationFallbackOutcomeSupport {

    /**
     * 创建无 LLM 的答案生成拆分支持。
     */
    AnswerGenerationOutcomeSupport() {
        super();
    }

    /**
     * 创建答案生成拆分支持。
     *
     * @param llmGateway LLM 网关
     */
    AnswerGenerationOutcomeSupport(LlmGateway llmGateway) {
        super(llmGateway);
    }

String stripMarkdownCodeFence(String content) {
        String normalizedContent = content;
        if (normalizedContent.startsWith("```json")) {
            normalizedContent = normalizedContent.substring("```json".length()).trim();
        }
        else if (normalizedContent.startsWith("```")) {
            normalizedContent = normalizedContent.substring("```".length()).trim();
        }
        if (normalizedContent.endsWith("```")) {
            normalizedContent = normalizedContent.substring(0, normalizedContent.length() - 3).trim();
        }
        return normalizedContent;
    }

    /**
     * 判断当前输出是否看起来像结构化 JSON。
     *
     * @param rawPayload 原始输出
     * @return 是否像结构化 JSON
     */
    boolean looksLikeStructuredJson(String rawPayload) {
        String normalizedPayload = rawPayload == null ? "" : rawPayload.trim();
        return normalizedPayload.startsWith("{")
                || normalizedPayload.startsWith("```json")
                || normalizedPayload.contains("\"answerMarkdown\"")
                || normalizedPayload.contains("\"answerOutcome\"");
    }

    /**
     * 判断旧式自由文本答案是否仍可作为 fallback 直接复用。
     *
     * @param rawPayload 原始输出
     * @return 可直接复用返回 true
     */
    boolean canReuseMarkdownAnswer(String rawPayload) {
        if (rawPayload == null || rawPayload.isBlank()) {
            return false;
        }
        if (looksLikeStructuredJson(rawPayload)) {
            return false;
        }
        return containsCitationLiteral(rawPayload);
    }

    /**
     * 对非 JSON 但已经像完整答案的 Markdown 做温和复用，减少模型偶发未包 JSON 时的主链退化。
     *
     * @param rawPayload 原始模型输出
     * @param question 用户问题
     * @param queryArticleHits 查询命中
     * @return 可复用答案；不可复用返回 null
     */
    QueryAnswerPayload parseMarkdownAnswerPayload(
            String rawPayload,
            String question,
            List<QueryArticleHit> queryArticleHits
    ) {
        if (!canReuseUnstructuredMarkdownAsLlmAnswer(rawPayload, question)) {
            return null;
        }
        String normalizedMarkdown = answerPostProcessor.normalizeStructuredAnswerMarkdown(
                stripMarkdownCodeFence(rawPayload.trim()),
                question,
                queryArticleHits
        );
        if (!containsCitationLiteral(normalizedMarkdown)) {
            return null;
        }
        List<QueryArticleHit> fallbackHits = selectFallbackEvidenceHits(question, queryArticleHits);
        AnswerOutcome answerOutcome = inferUnstructuredMarkdownAnswerOutcome(question, normalizedMarkdown, fallbackHits);
        return QueryAnswerPayload.llm(SensitiveTextMasker.mask(normalizedMarkdown.trim()), answerOutcome, false);
    }

    /**
     * 根据自由文本答案自身覆盖度和 fallback 证据推导答案语义。
     *
     * @param question 用户问题
     * @param normalizedMarkdown 归一后的 Markdown
     * @param fallbackHits fallback 证据
     * @return 答案语义
     */
    AnswerOutcome inferUnstructuredMarkdownAnswerOutcome(
            String question,
            String normalizedMarkdown,
            List<QueryArticleHit> fallbackHits
    ) {
        if (coversExactLookupUnstructuredAnswer(normalizedMarkdown, question)) {
            return AnswerOutcome.SUCCESS;
        }
        return inferFallbackEvidenceOutcome(question, fallbackHits);
    }

    /**
     * 判断非结构化 Markdown 是否足够像当前题目的完整答案。
     *
     * @param rawPayload 原始模型输出
     * @param question 用户问题
     * @return 可复用返回 true
     */
    boolean canReuseUnstructuredMarkdownAsLlmAnswer(String rawPayload, String question) {
        if (rawPayload == null || rawPayload.isBlank() || looksLikeStructuredJson(rawPayload)) {
            return false;
        }
        String normalizedPayload = stripMarkdownCodeFence(rawPayload.trim());
        if (looksLikeModelRefusalOrError(normalizedPayload)) {
            return false;
        }
        if (looksLikeFocusedReferentialDefinitionQuestion(question)) {
            return coversRequestedReferentialIdentifiers(normalizedPayload, question)
                    && countEnumerationFactLines(normalizedPayload) >= 1;
        }
        if (looksLikeExactLookupQuestion(question)
                && (!looksLikeEnumerationQuestion(question) || containsRequestedExactPathIdentifier(question))
                && !(looksLikeNumericQuestion(question)
                        && (querySemanticRules.containsAnySequenceSignal(question)
                                || querySemanticRules.containsAnyEnumSignal(question))
                        && coversRequestedQuestionAnchors(normalizedPayload, question))) {
            return coversExactLookupUnstructuredAnswer(normalizedPayload, question);
        }
        if (looksLikeEnumerationQuestion(question)) {
            return countEnumerationFactLines(normalizedPayload) >= 2
                    || coversRequestedQuestionAnchors(normalizedPayload, question);
        }
        if (looksLikeFlowQuestion(question)) {
            return containsFlowSignal(normalizedPayload);
        }
        if (looksLikeStatusQuestion(question)) {
            return containsStatusSignal(lowerCase(normalizedPayload));
        }
        if ((querySemanticRules.containsAnySequenceSignal(question)
                || querySemanticRules.containsAnyEnumSignal(question))
                && coversRequestedQuestionAnchors(normalizedPayload, question)) {
            return true;
        }
        return false;
    }

    /**
     * 判断自由文本是否已经覆盖精确查值题的关键标识和问题维度。
     *
     * @param markdown 模型 Markdown
     * @param question 用户问题
     * @return 覆盖返回 true
     */
    boolean coversExactLookupUnstructuredAnswer(String markdown, String question) {
        if (!containsSourceCitationLiteral(markdown)) {
            return false;
        }
        return coversExactLookupAnswerText(markdown, question);
    }

    /**
     * 判断答案正文是否覆盖精确查值题的关键标识和问题维度。
     *
     * @param markdown 答案 Markdown
     * @param question 用户问题
     * @return 覆盖返回 true
     */
    boolean coversExactLookupAnswerText(String markdown, String question) {
        if (!coversRequestedReferentialIdentifiers(markdown, question)
                && !coversRequestedQuestionAnchors(markdown, question)) {
            return false;
        }
        String normalizedMarkdown = lowerCase(stripEmbeddedCitationLiterals(markdown));
        if (looksLikePathQuestion(question)
                && !containsPathSignal(normalizedMarkdown)) {
            return false;
        }
        if (looksLikeRuleConstraintQuestion(question)
                && !containsRuleConstraintSignal(normalizedMarkdown)
                && !containsStrongConstraintSignal(normalizedMarkdown)) {
            return false;
        }
        if (looksLikeChangeTrackingQuestion(question)
                && !containsChangeTrackingSignal(normalizedMarkdown)
                && !containsStrongConstraintSignal(normalizedMarkdown)) {
            return false;
        }
        return true;
    }

    /**
     * 判断 Markdown 是否包含源文件式 citation，避免把普通内部文章引用误当作完整模型答案。
     *
     * @param markdown Markdown 文本
     * @return 包含源文件引用返回 true
     */
    boolean containsSourceCitationLiteral(String markdown) {
        return answerCitationResolver.containsSourceCitationLiteral(markdown);
    }

    /**
     * 判断非结构化答案是否覆盖了问题中明确点名的可复用锚点。
     *
     * @param markdown 模型 Markdown
     * @param question 用户问题
     * @return 覆盖返回 true
     */
    boolean coversRequestedQuestionAnchors(String markdown, String question) {
        List<String> reusableAnchors = extractReusableQuestionAnchors(question);
        if (reusableAnchors.size() < 2) {
            return false;
        }
        String normalizedMarkdown = lowerCase(stripEmbeddedCitationLiterals(markdown));
        int matchedAnchorCount = countMatchedReusableAnchors(normalizedMarkdown, reusableAnchors);
        if (matchedAnchorCount < reusableAnchors.size()) {
            return false;
        }
        int anchoredFactUnitCount = countAnchoredFactUnits(normalizedMarkdown, reusableAnchors);
        if (anchoredFactUnitCount >= Math.min(2, reusableAnchors.size())) {
            return true;
        }
        return anchoredFactUnitCount == 1 && normalizedMarkdown.length() >= 40;
    }

    /**
     * 从问题中提取适合做答案覆盖校验的通用锚点。
     *
     * @param question 用户问题
     * @return 去重后的锚点
     */
    @Override
    List<String> extractReusableQuestionAnchors(String question) {
        Set<String> reusableAnchors = new LinkedHashSet<String>();
        for (String rawToken : QueryTokenExtractor.extract(question)) {
            String normalizedToken = lowerCase(rawToken);
            if (isReusableQuestionAnchor(normalizedToken)) {
                reusableAnchors.add(normalizedToken);
            }
        }
        return new ArrayList<String>(reusableAnchors);
    }

    /**
     * 判断 token 是否适合作为自由文本答案的覆盖锚点。
     *
     * @param token 待判断 token
     * @return 适合返回 true
     */
    boolean isReusableQuestionAnchor(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        if (token.matches("\\d{2,}(?:\\.\\d+)?%?")) {
            return true;
        }
        if (!containsHanText(token) && token.matches("[a-z][a-z0-9_.=-]{3,}")) {
            return true;
        }
        return token.matches("[a-z0-9_.=-]*[_.=-][a-z0-9_.=-]*");
    }

    /**
     * 统计答案中命中的问题锚点数量。
     *
     * @param normalizedMarkdown 已归一化答案
     * @param reusableAnchors 问题锚点
     * @return 命中数量
     */
    @Override
    int countMatchedReusableAnchors(String normalizedMarkdown, List<String> reusableAnchors) {
        int matchedAnchorCount = 0;
        for (String reusableAnchor : reusableAnchors) {
            if (normalizedMarkdown.contains(reusableAnchor)) {
                matchedAnchorCount++;
            }
        }
        return matchedAnchorCount;
    }

    /**
     * 统计包含问题锚点的事实单元数量。
     *
     * @param normalizedMarkdown 已归一化答案
     * @param reusableAnchors 问题锚点
     * @return 事实单元数量
     */
    int countAnchoredFactUnits(String normalizedMarkdown, List<String> reusableAnchors) {
        if (normalizedMarkdown == null || normalizedMarkdown.isBlank()) {
            return 0;
        }
        int factUnitCount = 0;
        String[] segments = normalizedMarkdown.split("\\R|[。；;]+");
        for (String rawSegment : segments) {
            String normalizedSegment = answerEvidenceNormalizer.normalizeFallbackLineCandidate(rawSegment);
            if (normalizedSegment.length() < 8) {
                continue;
            }
            if (containsAnyReusableAnchor(lowerCase(normalizedSegment), reusableAnchors)) {
                factUnitCount++;
            }
        }
        return factUnitCount;
    }

    /**
     * 判断文本片段是否包含任一问题锚点。
     *
     * @param normalizedSegment 已归一化片段
     * @param reusableAnchors 问题锚点
     * @return 包含返回 true
     */
    boolean containsAnyReusableAnchor(String normalizedSegment, List<String> reusableAnchors) {
        for (String reusableAnchor : reusableAnchors) {
            if (normalizedSegment.contains(reusableAnchor)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断模型自由文本是否覆盖了问题中点名的精确标识。
     *
     * @param markdown 模型 Markdown
     * @param question 用户问题
     * @return 覆盖返回 true
     */
    boolean coversRequestedReferentialIdentifiers(String markdown, String question) {
        List<String> identifiers = extractRequestedReferentialIdentifiers(question);
        if (identifiers.isEmpty()) {
            return false;
        }
        String normalizedMarkdown = lowerCase(markdown);
        for (String identifier : identifiers) {
            if (!normalizedMarkdown.contains(lowerCase(identifier))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断模型输出是否只是拒答或错误说明。
     *
     * @param markdown 模型 Markdown
     * @return 拒答/错误返回 true
     */
    boolean looksLikeModelRefusalOrError(String markdown) {
        String normalizedMarkdown = lowerCase(markdown);
        return normalizedMarkdown.contains("无法回答")
                || normalizedMarkdown.contains("不能回答")
                || normalizedMarkdown.contains("没有足够信息")
                || normalizedMarkdown.contains("error")
                || normalizedMarkdown.contains("exception");
    }

    /**
     * 统计 Markdown 中像枚举事实的行数。
     *
     * @param markdown 模型 Markdown
     * @return 枚举事实行数
     */
    int countEnumerationFactLines(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return 0;
        }
        int factLineCount = 0;
        for (String rawLine : markdown.split("\\R")) {
            String normalizedLine = answerEvidenceNormalizer.normalizeFallbackLineCandidate(rawLine);
            if (looksLikeEnumerationFactLine(rawLine, normalizedLine)) {
                factLineCount++;
            }
        }
        return factLineCount;
    }

    /**
     * 基于答案载荷推导 prompt cache 写策略。
     *
     * @param answerPayload 结构化答案载荷
     * @return prompt cache 写策略
     */
    PromptCacheWritePolicy resolvePromptCacheWritePolicy(QueryAnswerPayload answerPayload) {
        if (answerPayload == null) {
            return PromptCacheWritePolicy.EVICT_AFTER_READ;
        }
        if (answerPayload.getAnswerOutcome() == AnswerOutcome.SUCCESS && answerPayload.isAnswerCacheable()) {
            return PromptCacheWritePolicy.WRITE;
        }
        return PromptCacheWritePolicy.SKIP_WRITE;
    }
}
