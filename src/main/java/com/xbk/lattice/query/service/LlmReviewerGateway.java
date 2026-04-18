package com.xbk.lattice.query.service;

import com.xbk.lattice.compiler.service.LlmGateway;
import com.xbk.lattice.llm.service.ExecutionLlmSnapshotService;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
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
@Profile("jdbc")
public class LlmReviewerGateway implements ReviewerGateway {

    private static final String SYSTEM_QUERY_REVIEW = """
            你是 Lattice 查询审查助手。请根据问题、答案与来源路径，判断答案是否可靠。

            输出要求：
            1. 只能输出 JSON
            2. JSON 结构必须是 {"pass":true|false,"issues":[...]}
            3. 如果答案缺少来源、与问题不匹配、包含无法证实的结论或明显遗漏，pass 必须为 false
            4. issues 中每项必须包含 severity、category、description
            5. 不要输出 Markdown、解释性前后缀或代码块
            """;

    private final LlmGateway llmGateway;

    private final LocalReviewerGateway localReviewerGateway;

    /**
     * 创建 LLM 审查网关。
     *
     * @param llmGateway LLM 网关
     * @param localReviewerGateway 本地规则审查网关
     */
    public LlmReviewerGateway(
            LlmGateway llmGateway,
            LocalReviewerGateway localReviewerGateway
    ) {
        this.llmGateway = llmGateway;
        this.localReviewerGateway = localReviewerGateway;
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
            if (scopeId == null || scopeId.isBlank()) {
                return llmGateway.review("query-review", SYSTEM_QUERY_REVIEW, reviewPrompt);
            }
            return llmGateway.reviewWithScope(
                    scopeId,
                    scene,
                    agentRole,
                    "query-review",
                    SYSTEM_QUERY_REVIEW,
                    reviewPrompt
            );
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
