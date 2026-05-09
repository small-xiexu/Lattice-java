package com.xbk.lattice.query.service;

import com.xbk.lattice.compiler.service.LlmGateway;
import com.xbk.lattice.llm.service.ExecutionLlmSnapshotService;
import com.xbk.lattice.query.domain.AnswerOutcome;
import com.xbk.lattice.query.domain.GenerationMode;
import com.xbk.lattice.query.domain.ModelExecutionStatus;
import com.xbk.lattice.query.domain.QueryAnswerPayload;

import java.util.List;

/**
 * 答案生成公开 API 支撑
 *
 * 职责：承载答案生成服务的公开方法、默认参数桥接与结构化答案后处理桥接
 *
 * @author xiexu
 */
abstract class AnswerGenerationApiSupport extends AnswerGenerationOutcomeSupport {

    private final AnswerGenerationPayloadOrchestrator payloadOrchestrator;

    /**
     * 创建无 LLM 网关的公开 API 支撑。
     */
    AnswerGenerationApiSupport() {
        super();
        this.payloadOrchestrator = new AnswerGenerationPayloadOrchestrator((AnswerGenerationService) this);
    }

    /**
     * 创建公开 API 支撑。
     *
     * @param llmGateway LLM 网关
     */
    AnswerGenerationApiSupport(LlmGateway llmGateway) {
        super(llmGateway);
        this.payloadOrchestrator = new AnswerGenerationPayloadOrchestrator((AnswerGenerationService) this);
    }

    /**
     * 基于单条文章命中生成确定性答案。
     *
     * @param question 查询问题
     * @param articleHit 文章命中
     * @return Markdown 答案
     */
    public String generate(String question, QueryArticleHit articleHit) {
        return payloadOrchestrator.generateSingleArticleAnswer(question, articleHit);
    }

    /**
     * 基于多路证据生成 Markdown 答案。
     *
     * @param question 查询问题
     * @param queryArticleHits 融合命中
     * @return Markdown 答案
     */
    public String generate(String question, List<QueryArticleHit> queryArticleHits) {
        return generatePayload(question, queryArticleHits).getAnswerMarkdown();
    }

    /**
     * 基于指定模型角色生成 Markdown 答案。
     *
     * @param scopeId 作用域标识
     * @param scene 场景
     * @param agentRole Agent 角色
     * @param question 查询问题
     * @param queryArticleHits 融合命中
     * @return Markdown 答案
     */
    public String generate(
            String scopeId,
            String scene,
            String agentRole,
            String question,
            List<QueryArticleHit> queryArticleHits
    ) {
        return generatePayload(scopeId, scene, agentRole, question, queryArticleHits).getAnswerMarkdown();
    }

    /**
     * 基于多路证据生成结构化答案载荷。
     *
     * @param question 查询问题
     * @param queryArticleHits 融合命中
     * @return 结构化答案载荷
     */
    public QueryAnswerPayload generatePayload(String question, List<QueryArticleHit> queryArticleHits) {
        return generatePayload(
                null,
                ExecutionLlmSnapshotService.QUERY_SCENE,
                ExecutionLlmSnapshotService.ROLE_ANSWER,
                question,
                queryArticleHits
        );
    }

    /**
     * 基于当前命中构造确定性 fallback 答案载荷。
     *
     * @param question 查询问题
     * @param queryArticleHits 查询命中
     * @return fallback 答案载荷
     */
    public QueryAnswerPayload fallbackPayload(String question, List<QueryArticleHit> queryArticleHits) {
        return fallbackPayload(question, queryArticleHits, null);
    }

    /**
     * 基于当前命中构造确定性 fallback 答案载荷，并允许保留原有负向 outcome。
     *
     * @param question 查询问题
     * @param queryArticleHits 查询命中
     * @param answerOutcome 期望保留的答案语义
     * @return fallback 答案载荷
     */
    public QueryAnswerPayload fallbackPayload(
            String question,
            List<QueryArticleHit> queryArticleHits,
            AnswerOutcome answerOutcome
    ) {
        return buildDeterministicFallbackPayload(
                question,
                queryArticleHits,
                answerOutcome,
                GenerationMode.FALLBACK,
                ModelExecutionStatus.DEGRADED,
                FALLBACK_REASON_LLM_UNSTRUCTURED_FALLBACK
        );
    }

    /**
     * 基于指定模型角色生成结构化答案载荷。
     *
     * @param scopeId 作用域标识
     * @param scene 场景
     * @param agentRole Agent 角色
     * @param question 查询问题
     * @param queryArticleHits 融合命中
     * @return 结构化答案载荷
     */
    public QueryAnswerPayload generatePayload(
            String scopeId,
            String scene,
            String agentRole,
            String question,
            List<QueryArticleHit> queryArticleHits
    ) {
        return payloadOrchestrator.generatePayload(scopeId, scene, agentRole, question, queryArticleHits);
    }

    /**
     * 基于纠正信息重生成修订答案。
     *
     * @param question 查询问题
     * @param currentAnswer 当前答案
     * @param correction 用户纠正
     * @param queryArticleHits 修订证据
     * @return 修订后的 Markdown 答案
     */
    public String revise(
            String question,
            String currentAnswer,
            String correction,
            List<QueryArticleHit> queryArticleHits
    ) {
        return revise(
                null,
                ExecutionLlmSnapshotService.QUERY_SCENE,
                ExecutionLlmSnapshotService.ROLE_REWRITE,
                question,
                currentAnswer,
                correction,
                queryArticleHits
        );
    }

    /**
     * 基于指定模型角色重生成修订答案。
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
    public String revise(
            String scopeId,
            String scene,
            String agentRole,
            String question,
            String currentAnswer,
            String correction,
            List<QueryArticleHit> queryArticleHits
    ) {
        return payloadOrchestrator.revise(scopeId, scene, agentRole, question, currentAnswer, correction, queryArticleHits);
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
    public String rewriteFromReviewFeedback(
            String scopeId,
            String scene,
            String agentRole,
            String question,
            String currentAnswer,
            String reviewFindings,
            List<QueryArticleHit> queryArticleHits
    ) {
        return answerRewriteService.rewriteFromReviewFeedback(
                scopeId,
                scene,
                agentRole,
                question,
                currentAnswer,
                reviewFindings,
                queryArticleHits
        );
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
    public QueryAnswerPayload rewriteFromReviewPayload(
            String scopeId,
            String scene,
            String agentRole,
            String question,
            String currentAnswer,
            String reviewFindings,
            List<QueryArticleHit> queryArticleHits
    ) {
        return answerRewriteService.rewriteFromReviewPayload(
                scopeId,
                scene,
                agentRole,
                question,
                currentAnswer,
                reviewFindings,
                queryArticleHits
        );
    }

    /**
     * 返回当前作用域下的路由标签。
     *
     * @param scopeId 作用域标识
     * @param scene 场景
     * @param agentRole Agent 角色
     * @return 路由标签
     */
    public String currentRoute(String scopeId, String scene, String agentRole) {
        return answerLlmInvoker.currentRoute(scopeId, scene, agentRole);
    }

    /**
     * 归一化结构化答案语义。
     *
     * @param answerOutcome 模型声明语义
     * @param answerMarkdown 答案正文
     * @param question 用户问题
     * @param queryArticleHits 查询命中
     * @return 归一化后的语义
     */
    AnswerOutcome normalizeStructuredAnswerOutcome(
            AnswerOutcome answerOutcome,
            String answerMarkdown,
            String question,
            List<QueryArticleHit> queryArticleHits
    ) {
        return payloadOrchestrator.normalizeStructuredAnswerOutcome(
                answerOutcome,
                answerMarkdown,
                question,
                queryArticleHits
        );
    }

    /**
     * 对精确查值题收敛结构化答案。
     *
     * @param answerMarkdown 模型答案
     * @param question 用户问题
     * @return 收敛后的答案
     */
    String compressStructuredExactLookupAnswer(String answerMarkdown, String question) {
        return answerPostProcessor.compressStructuredExactLookupAnswer(answerMarkdown, question);
    }

    /**
     * 为缺少 citation 的结构化答案补默认引用。
     *
     * @param answerMarkdown 模型答案
     * @param question 用户问题
     * @param queryArticleHits 查询命中
     * @return 补引用后的答案
     */
    String attachDefaultCitationWhenMissing(
            String answerMarkdown,
            String question,
            List<QueryArticleHit> queryArticleHits
    ) {
        return answerPostProcessor.attachDefaultCitationWhenMissing(answerMarkdown, question, queryArticleHits);
    }
}
