package com.xbk.lattice.query.service;

import com.xbk.lattice.llm.service.LlmInvocationEnvelope;
import com.xbk.lattice.llm.service.PromptCacheWritePolicy;
import com.xbk.lattice.query.domain.AnswerOutcome;
import com.xbk.lattice.query.domain.GenerationMode;
import com.xbk.lattice.query.domain.ModelExecutionStatus;
import com.xbk.lattice.query.domain.QueryAnswerPayload;

import java.util.List;

/**
 * 答案生成载荷编排器
 *
 * 职责：编排单篇兜底、结构化 LLM 生成、Markdown 复用、修订兜底与答案语义归一化
 *
 * @author xiexu
 */
final class AnswerGenerationPayloadOrchestrator {

    private static final String NO_KNOWLEDGE_MESSAGE = "当前未找到与该问题直接相关的知识。";

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
            LlmInvocationEnvelope envelope = support.answerLlmInvoker.invokeRawWithScope(
                    scopeId,
                    scene,
                    agentRole,
                    "query-answer-structured",
                    support.answerPromptBuilder.systemQueryAnswer(),
                    support.answerPromptBuilder.buildAnswerPrompt(question, queryArticleHits)
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
}
