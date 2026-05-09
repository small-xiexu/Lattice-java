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
 * 答案生成 精确查值支持
 *
 * 职责：承载 AnswerGenerationService 拆分出来的同类辅助逻辑
 *
 * @author xiexu
 */
@Slf4j
abstract class AnswerGenerationExactLookupSupport extends AnswerGenerationExactLookupGroundingSupport {

    /**
     * 创建无 LLM 的答案生成拆分支持。
     */
    AnswerGenerationExactLookupSupport() {
        super();
    }

    /**
     * 创建答案生成拆分支持。
     *
     * @param llmGateway LLM 网关
     */
    AnswerGenerationExactLookupSupport(LlmGateway llmGateway) {
        super(llmGateway);
    }

QueryAnswerPayload preferDeterministicExactLookupPayload(
            String question,
            List<QueryArticleHit> queryArticleHits,
            QueryAnswerPayload answerPayload
    ) {
        if (answerPayload == null || !looksLikeExactLookupQuestion(question)) {
            return answerPayload;
        }
        List<QueryArticleHit> fallbackHits = selectFallbackEvidenceHits(question, queryArticleHits);
        if (fallbackHits.isEmpty() || !isDirectFallbackAnswerable(question, fallbackHits.get(0))) {
            return answerPayload;
        }
        String normalizedAnswer = lowerCase(answerPayload.getAnswerMarkdown());
        ExactLookupPreferenceReason preferenceReason = ExactLookupPreferenceReason.NONE;
        ExactLookupGroundingStatus groundingStatus = ExactLookupGroundingStatus.GROUNDED;
        if (answerPayload.getAnswerOutcome() != AnswerOutcome.SUCCESS) {
            preferenceReason = ExactLookupPreferenceReason.OUTCOME_NOT_SUCCESS;
        }
        else if (containsOvercautiousExactLookupPhrase(normalizedAnswer)) {
            preferenceReason = ExactLookupPreferenceReason.OVERCAUTIOUS_PHRASE;
        }
        else {
            groundingStatus = evaluateExactLookupAnswerGrounding(
                    question,
                    fallbackHits,
                    answerPayload.getAnswerMarkdown()
            );
            if (groundingStatus != ExactLookupGroundingStatus.GROUNDED) {
                preferenceReason = ExactLookupPreferenceReason.GROUNDING_MISMATCH;
            }
        }
        if (preferenceReason != ExactLookupPreferenceReason.NONE) {
            logExactLookupPreference(preferenceReason, groundingStatus);
            return buildDeterministicFallbackPayload(
                    question,
                    queryArticleHits,
                    AnswerOutcome.SUCCESS,
                    GenerationMode.FALLBACK,
                    ModelExecutionStatus.DEGRADED,
                    FALLBACK_REASON_DETERMINISTIC_EXACT_LOOKUP_PREFERRED
            );
        }
        return answerPayload;
    }

    /**
     * 判断模型答案是否带有精确题常见的过度保守表达。
     *
     * @param normalizedAnswer 归一化答案
     * @return 命中返回 true
     */
    boolean containsOvercautiousExactLookupPhrase(String normalizedAnswer) {
        if (normalizedAnswer == null || normalizedAnswer.isBlank()) {
            return false;
        }
        return normalizedAnswer.contains("当前证据不足")
                || normalizedAnswer.contains("暂无法确认")
                || normalizedAnswer.contains("无法根据当前证据确定")
                || normalizedAnswer.contains("没有直接给出")
                || normalizedAnswer.contains("未直接给出")
                || normalizedAnswer.contains("未提供");
    }

    /**
     * 记录精确查值题偏向 deterministic fallback 的通用原因。
     *
     * @param preferenceReason 偏向 fallback 的原因
     * @param groundingStatus grounding 判定状态
     */
    void logExactLookupPreference(
            ExactLookupPreferenceReason preferenceReason,
            ExactLookupGroundingStatus groundingStatus
    ) {
        log.info(
                "query_exact_lookup_deterministic_preferred reason: {}, groundingStatus: {}",
                preferenceReason,
                groundingStatus
        );
    }

    /**
     * 判断精确查值题的模型答案是否至少覆盖了证据中的关键形态。
     *
     * @param question 用户问题
     * @param fallbackHits fallback 命中
     * @param answerMarkdown 模型答案
     * @return 基本贴合证据返回 true
     */
    boolean isExactLookupAnswerGroundedByFocusedEvidence(
            String question,
            List<QueryArticleHit> fallbackHits,
            String answerMarkdown
    ) {
        return evaluateExactLookupAnswerGrounding(question, fallbackHits, answerMarkdown)
                == ExactLookupGroundingStatus.GROUNDED;
    }

    /**
     * 判断精确查值题的模型答案是否覆盖了证据里的关键形态，并返回通用失败原因。
     *
     * @param question 用户问题
     * @param fallbackHits fallback 命中
     * @param answerMarkdown 模型答案
     * @return grounding 判定状态
     */
    ExactLookupGroundingStatus evaluateExactLookupAnswerGrounding(
            String question,
            List<QueryArticleHit> fallbackHits,
            String answerMarkdown
    ) {
        if (answerMarkdown == null || answerMarkdown.isBlank() || fallbackHits == null || fallbackHits.isEmpty()) {
            return ExactLookupGroundingStatus.GROUNDED;
        }
        List<String> queryTokens = extractQueryTokens(question);
        List<String> focusSnippets = new ArrayList<String>();
        int evidenceLimit = Math.min(8, fallbackHits.size());
        int snippetLimit = shouldAggregateEvidenceConclusion(question) || looksLikeCompoundExactLookupQuestion(question)
                ? Math.max(4, desiredFallbackConclusionSnippetCount(question))
                : Math.max(2, desiredStructuredFactCount(question));
        for (int index = 0; index < evidenceLimit; index++) {
            if (shouldAggregateEvidenceConclusion(question) || looksLikeCompoundExactLookupQuestion(question)) {
                focusSnippets.addAll(selectAggregationCandidateLines(
                        question,
                        fallbackHits.get(index),
                        queryTokens,
                        snippetLimit
                ));
            }
            else {
                focusSnippets.addAll(selectQuestionFocusedFallbackSnippets(
                        question,
                        fallbackHits.get(index),
                        queryTokens,
                        snippetLimit
                ));
            }
        }
        if (focusSnippets.isEmpty()) {
            return ExactLookupGroundingStatus.GROUNDED;
        }
        String normalizedQuestion = lowerCase(question);
        String normalizedAnswer = lowerCase(answerMarkdown);
        if ((normalizedQuestion.contains("路径") || normalizedQuestion.contains("接口"))
                && containsAnySnippetToken(focusSnippets, "/")
                && !coversRequiredPathShape(question, normalizedAnswer, focusSnippets)) {
            return ExactLookupGroundingStatus.MISSING_PATH_SHAPE;
        }
        if ((normalizedQuestion.contains("命中数") || looksLikeNumericQuestion(question))
                && containsAnySnippetDigit(focusSnippets)
                && !normalizedAnswer.matches("(?s).*\\d.*")) {
            return ExactLookupGroundingStatus.MISSING_DIGIT;
        }
        if (looksLikeNumericQuestion(question)
                && !coversRequiredNumericShape(normalizedQuestion, normalizedAnswer, focusSnippets)) {
            return ExactLookupGroundingStatus.MISSING_NUMERIC_SHAPE;
        }
        if (expectsBatchOrOrdinalAnswer(normalizedQuestion)
                && containsAnyBatchOrOrdinalSignal(focusSnippets)
                && !containsBatchOrOrdinalSignal(normalizedAnswer)) {
            return ExactLookupGroundingStatus.MISSING_BATCH_OR_ORDINAL;
        }
        if (looksLikeStatusQuestion(question)
                && containsAnyStatusSignal(focusSnippets)
                && !containsStatusSignal(normalizedAnswer)) {
            return ExactLookupGroundingStatus.MISSING_STATUS;
        }
        if (looksLikeFlowQuestion(question)
                && containsAnyFlowTransitionSignal(focusSnippets)
                && !containsFlowTransitionSignal(answerMarkdown)) {
            return ExactLookupGroundingStatus.MISSING_FLOW;
        }
        if (normalizedQuestion.contains("结论")
                && containsAnyCorrectionOrStatusSignal(focusSnippets)
                && !containsCorrectionOrStatusSignal(normalizedAnswer)) {
            return ExactLookupGroundingStatus.MISSING_CORRECTION_OR_STATUS;
        }
        if (looksLikeRuleConstraintQuestion(question)
                && containsAnyStrongConstraintSignal(focusSnippets)
                && !containsStrongConstraintSignal(normalizedAnswer)) {
            return ExactLookupGroundingStatus.MISSING_STRONG_CONSTRAINT;
        }
        if (looksLikeRuleConstraintQuestion(question)
                && containsAnyRuleConstraintSignal(focusSnippets)
                && !containsRuleConstraintSignal(normalizedAnswer)) {
            return ExactLookupGroundingStatus.MISSING_RULE_CONSTRAINT;
        }
        if (looksLikeChangeTrackingQuestion(question)
                && !coversChangeTrackingAnswer(question, normalizedAnswer, focusSnippets)) {
            return ExactLookupGroundingStatus.MISSING_CHANGE_TRACKING;
        }
        if (coversRequestedPathContractAnswer(question, normalizedAnswer, focusSnippets)) {
            return ExactLookupGroundingStatus.GROUNDED;
        }
        if (looksLikeCompoundExactLookupQuestion(question)
                && !coversMultipleEvidenceDimensions(question, normalizedAnswer, focusSnippets)) {
            return ExactLookupGroundingStatus.MISSING_COMPOUND_DIMENSIONS;
        }
        return ExactLookupGroundingStatus.GROUNDED;
    }

    /**
     * 判断数值题答案是否覆盖证据中的关键数值形态。
     *
     * @param normalizedQuestion 归一化问题
     * @param normalizedAnswer 归一化答案
     * @param focusSnippets 贴题证据句
     * @return 覆盖足够返回 true
     */
    boolean coversRequiredNumericShape(
            String normalizedQuestion,
            String normalizedAnswer,
            List<String> focusSnippets
    ) {
        List<String> evidenceNumbers = extractRequiredEvidenceNumbers(normalizedQuestion, focusSnippets);
        if (evidenceNumbers.isEmpty()) {
            return true;
        }
        int coveredNumberCount = countCoveredNumbers(normalizedAnswer, evidenceNumbers);
        if (coveredNumberCount > 0) {
            return true;
        }
        if (normalizedQuestion.contains("命中数") || normalizedQuestion.contains("多少")) {
            return false;
        }
        if (normalizedQuestion.contains("分别") && evidenceNumbers.size() >= 2) {
            return false;
        }
        return normalizedAnswer.matches("(?s).*\\d.*");
    }

    /**
     * 判断路径题答案是否覆盖了证据里的具体路径形态。
     *
     * @param normalizedAnswer 归一化答案
     * @param focusSnippets 贴题证据句
     * @return 覆盖足够返回 true
     */
}
