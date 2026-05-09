package com.xbk.lattice.query.service;

import com.xbk.lattice.llm.service.LlmInvocationEnvelope;
import com.xbk.lattice.llm.service.PromptCacheWritePolicy;
import com.xbk.lattice.query.domain.AnswerOutcome;
import com.xbk.lattice.query.domain.GenerationMode;
import com.xbk.lattice.query.domain.ModelExecutionStatus;
import com.xbk.lattice.query.domain.QueryAnswerPayload;

import java.util.List;

/**
 * 答案审查重写服务
 *
 * 职责：根据审查反馈重新生成最终答案，并在模型不可用或输出无效时回落到确定性 fallback
 *
 * 不属于本类的事：不构造普通问答 prompt、不选择检索证据、不执行 citation 后处理细则
 *
 * @author xiexu
 */
final class AnswerRewriteService {

    private final AnswerGenerationService support;

    private final AnswerPromptBuilder answerPromptBuilder;

    private final AnswerPayloadParser answerPayloadParser;

    private final AnswerLlmInvoker answerLlmInvoker;

    /**
     * 创建答案审查重写服务。
     *
     * @param support 答案生成支撑逻辑
     * @param answerPromptBuilder prompt 构造器
     * @param answerPayloadParser 结构化答案解析器
     * @param answerLlmInvoker LLM 调用器
     */
    AnswerRewriteService(
            AnswerGenerationService support,
            AnswerPromptBuilder answerPromptBuilder,
            AnswerPayloadParser answerPayloadParser,
            AnswerLlmInvoker answerLlmInvoker
    ) {
        this.support = support;
        this.answerPromptBuilder = answerPromptBuilder;
        this.answerPayloadParser = answerPayloadParser;
        this.answerLlmInvoker = answerLlmInvoker;
    }

    /**
     * 基于审查问题重写最终答案。
     *
     * @param scopeId 作用域标识
     * @param scene 场景
     * @param agentRole Agent 角色
     * @param question 查询问题
     * @param currentAnswer 当前答案
     * @param reviewFindings 审查问题
     * @param queryArticleHits 修订证据
     * @return 面向最终用户的 Markdown 答案
     */
    String rewriteFromReviewFeedback(
            String scopeId,
            String scene,
            String agentRole,
            String question,
            String currentAnswer,
            String reviewFindings,
            List<QueryArticleHit> queryArticleHits
    ) {
        return rewriteFromReviewPayload(
                scopeId,
                scene,
                agentRole,
                question,
                currentAnswer,
                reviewFindings,
                queryArticleHits
        ).getAnswerMarkdown();
    }

    /**
     * 基于审查问题重写最终答案，并返回结构化载荷。
     *
     * @param scopeId 作用域标识
     * @param scene 场景
     * @param agentRole Agent 角色
     * @param question 查询问题
     * @param currentAnswer 当前答案
     * @param reviewFindings 审查问题
     * @param queryArticleHits 修订证据
     * @return 结构化答案载荷
     */
    QueryAnswerPayload rewriteFromReviewPayload(
            String scopeId,
            String scene,
            String agentRole,
            String question,
            String currentAnswer,
            String reviewFindings,
            List<QueryArticleHit> queryArticleHits
    ) {
        boolean llmExecutionFailed = false;
        boolean llmOutputInvalid = false;
        if (answerLlmInvoker.isAvailable()) {
            try {
                LlmInvocationEnvelope envelope = answerLlmInvoker.invokeRawWithScope(
                        scopeId,
                        scene,
                        agentRole,
                        "query-rewrite-from-review-structured",
                        answerPromptBuilder.systemQueryRewriteFromReview(),
                        answerPromptBuilder.buildReviewRewritePrompt(
                                question,
                                currentAnswer,
                                reviewFindings,
                                queryArticleHits
                        )
                );
                QueryAnswerPayload parsedPayload = parseStructuredRewritePayload(envelope, question, queryArticleHits);
                if (parsedPayload != null) {
                    return parsedPayload;
                }
                llmOutputInvalid = true;
                QueryAnswerPayload markdownPayload = parseRewritePayload(envelope.getContent(), question, queryArticleHits);
                if (markdownPayload != null) {
                    return markdownPayload;
                }
            }
            catch (RuntimeException ex) {
                // 审查驱动的重写失败时，降级回基于证据的结构化答案，避免把问题单直接返回给用户。
                llmExecutionFailed = true;
            }
        }
        if (!answerLlmInvoker.isAvailable()) {
            return QueryAnswerPayload.ruleBased(
                    support.buildFallbackMarkdown(question, queryArticleHits),
                    AnswerOutcome.PARTIAL_ANSWER
            );
        }
        String fallbackReason = resolveFallbackReason(llmExecutionFailed, llmOutputInvalid);
        ModelExecutionStatus modelExecutionStatus = llmExecutionFailed
                ? ModelExecutionStatus.FAILED
                : ModelExecutionStatus.DEGRADED;
        return support.buildDeterministicFallbackPayload(
                question,
                queryArticleHits,
                AnswerOutcome.PARTIAL_ANSWER,
                GenerationMode.FALLBACK,
                modelExecutionStatus,
                fallbackReason
        );
    }

    /**
     * 解析结构化审查重写结果，并应用 prompt cache 写策略。
     *
     * @param envelope LLM 调用信封
     * @param question 查询问题
     * @param queryArticleHits 修订证据
     * @return 结构化答案载荷；无法解析时返回 null
     */
    private QueryAnswerPayload parseStructuredRewritePayload(
            LlmInvocationEnvelope envelope,
            String question,
            List<QueryArticleHit> queryArticleHits
    ) {
        String llmAnswer = envelope.getContent();
        QueryAnswerPayload parsedPayload = answerPayloadParser.parseStructuredAnswerPayload(
                llmAnswer,
                question,
                queryArticleHits
        );
        if (parsedPayload != null) {
            PromptCacheWritePolicy promptCacheWritePolicy = support.resolvePromptCacheWritePolicy(parsedPayload);
            answerLlmInvoker.applyPromptCacheWritePolicy(envelope, promptCacheWritePolicy);
            return parsedPayload;
        }
        answerLlmInvoker.applyPromptCacheWritePolicy(envelope, PromptCacheWritePolicy.EVICT_AFTER_READ);
        return null;
    }

    /**
     * 解析旧式 Markdown 审查重写结果。
     *
     * @param llmAnswer LLM 文本输出
     * @param question 查询问题
     * @param queryArticleHits 修订证据
     * @return 可复用答案载荷；无法复用时返回 null 或 fallback 载荷
     */
    private QueryAnswerPayload parseRewritePayload(
            String llmAnswer,
            String question,
            List<QueryArticleHit> queryArticleHits
    ) {
        QueryAnswerPayload markdownPayload = support.parseMarkdownAnswerPayload(
                llmAnswer,
                question,
                queryArticleHits
        );
        if (markdownPayload != null) {
            return markdownPayload;
        }
        if (support.canReuseMarkdownAnswer(llmAnswer)) {
            return QueryAnswerPayload.fallback(
                    llmAnswer.trim(),
                    AnswerGenerationService.FALLBACK_REASON_LLM_UNSTRUCTURED_FALLBACK
            );
        }
        return null;
    }

    /**
     * 解析审查重写 fallback 原因。
     *
     * @param llmExecutionFailed LLM 调用是否失败
     * @param llmOutputInvalid LLM 输出是否无效
     * @return fallback reason
     */
    private String resolveFallbackReason(boolean llmExecutionFailed, boolean llmOutputInvalid) {
        if (llmExecutionFailed) {
            return AnswerGenerationService.FALLBACK_REASON_REWRITE_FAILED;
        }
        if (llmOutputInvalid) {
            return AnswerGenerationService.FALLBACK_REASON_LLM_OUTPUT_INVALID;
        }
        return AnswerGenerationService.FALLBACK_REASON_LLM_UNSTRUCTURED_FALLBACK;
    }
}
