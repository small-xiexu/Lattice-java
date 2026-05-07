package com.xbk.lattice.query.service;

import com.xbk.lattice.compiler.service.LlmGateway;
import com.xbk.lattice.llm.service.ExecutionLlmSnapshotService;
import com.xbk.lattice.llm.service.LlmInvocationEnvelope;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * LLM 审查网关
 *
 * 职责：让 Query 审查节点复用统一模型中心、运行时快照与路由选路，并在失败时回退到本地规则审查
 *
 * @author xiexu
 */
@Service
@Primary
public class LlmReviewerGateway implements ReviewerGateway {

    private static final String SYSTEM_QUERY_REVIEW = """
            你是 Lattice 查询审查助手。请根据问题、答案与来源路径，判断答案是否可靠。

            输出要求：
            1. 只能输出 JSON
            2. JSON 结构必须是 {"approved":true|false,"rewriteRequired":true|false,"riskLevel":"LOW|MEDIUM|HIGH","issues":[...],"userFacingRewriteHints":[...],"cacheWritePolicy":"WRITE|SKIP_WRITE|EVICT_AFTER_READ"}
            3. 你会同时看到 answerOutcome 字段；如果 answerOutcome=SUCCESS 且答案已直接回答问题、关键结论可由引用支撑，不要仅因答案简短就判失败
            4. 如果 answerOutcome=PARTIAL_ANSWER 或 INSUFFICIENT_EVIDENCE，只要答案明确说明了证据缺口且没有编造，不要仅因“未完全覆盖”就判失败；只有遗漏了现有来源已能直接支撑的关键结论时才判失败
            4.1 如果答案先直接回答了用户真正问的核心问题，再补充“其他实现细节当前证据不足/暂未确认”，这类额外边界说明不算缺陷，不要因此判失败
            5. 如果答案缺少来源、与问题不匹配、包含无法证实的结论或明显遗漏，approved 必须为 false，rewriteRequired 必须为 true
            6. issues 中每项必须包含 severity、category、description
            7. 审查通过时，issues 与 userFacingRewriteHints 必须为空数组，cacheWritePolicy 返回 WRITE
            8. 审查未通过时，cacheWritePolicy 默认返回 SKIP_WRITE
            9. 不要输出 Markdown、解释性前后缀或代码块
            """;

    private final LlmGateway llmGateway;

    private final LocalReviewerGateway localReviewerGateway;

    private final ReviewResultParser reviewResultParser;

    /**
     * 创建 LLM 审查网关。
     *
     * @param llmGateway LLM 网关
     * @param localReviewerGateway 本地规则审查网关
     * @param reviewResultParser 审查结果解析器
     */
    public LlmReviewerGateway(
            LlmGateway llmGateway,
            LocalReviewerGateway localReviewerGateway,
            ReviewResultParser reviewResultParser
    ) {
        this.llmGateway = llmGateway;
        this.localReviewerGateway = localReviewerGateway;
        this.reviewResultParser = reviewResultParser;
    }

    /**
     * 执行默认场景审查。
     *
     * @param reviewPrompt 审查提示词
     * @return 原始审查输出
     */
    @Override
    public String review(String reviewPrompt) {
        return review(
                null,
                ExecutionLlmSnapshotService.QUERY_SCENE,
                ExecutionLlmSnapshotService.ROLE_REVIEWER,
                reviewPrompt
        );
    }

    /**
     * 在指定作用域下执行审查。
     *
     * @param scopeId 作用域标识
     * @param scene 场景
     * @param agentRole Agent 角色
     * @param reviewPrompt 审查提示词
     * @return 原始审查输出
     */
    @Override
    public String review(String scopeId, String scene, String agentRole, String reviewPrompt) {
        if (!llmGateway.isReviewEnabled()) {
            return localReviewerGateway.review(reviewPrompt);
        }
        try {
            LlmInvocationEnvelope envelope;
            if (scopeId == null || scopeId.isBlank()) {
                envelope = llmGateway.invokeRaw(
                        scene,
                        agentRole,
                        "query-review",
                        SYSTEM_QUERY_REVIEW,
                        reviewPrompt
                );
            }
            else {
                envelope = llmGateway.invokeRawWithScope(
                        scopeId,
                        scene,
                        agentRole,
                        "query-review",
                        SYSTEM_QUERY_REVIEW,
                        reviewPrompt
                );
            }
            llmGateway.applyPromptCacheWritePolicy(
                    envelope,
                    reviewResultParser.resolvePromptCacheWritePolicy(envelope.getContent())
            );
            return envelope.getContent();
        }
        catch (RuntimeException exception) {
            return localReviewerGateway.review(reviewPrompt);
        }
    }

    /**
     * 返回当前审查路由标签。
     *
     * @param scopeId 作用域标识
     * @param scene 场景
     * @param agentRole Agent 角色
     * @return 路由标签
     */
    @Override
    public String currentRoute(String scopeId, String scene, String agentRole) {
        if (!llmGateway.isReviewEnabled()) {
            return "rule-based";
        }
        try {
            if (scopeId == null || scopeId.isBlank()) {
                return llmGateway.reviewRoute();
            }
            return llmGateway.routeFor(scopeId, scene, agentRole);
        }
        catch (RuntimeException exception) {
            return "rule-based";
        }
    }
}
