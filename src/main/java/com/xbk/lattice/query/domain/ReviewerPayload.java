package com.xbk.lattice.query.domain;

import com.xbk.lattice.llm.service.PromptCacheWritePolicy;
import lombok.Getter;

import java.util.List;

/**
 * Reviewer 审查载荷。
 *
 * <p>承载 Query / compile reviewer 结构化输出的最小语义——含审批结果、重写标记、问题列表和缓存策略。
 *
 * @author xiexu
 */
@Getter
public class ReviewerPayload {

    /** 是否审批通过。 */
    private final boolean approved;
    /** 是否需要重写。true 时触发 auto-fix 或 query rewrite。 */
    private final boolean rewriteRequired;
    /** 风险等级（如 low / medium / high）。 */
    private final String riskLevel;
    /** 审查问题列表。 */
    private final List<ReviewIssue> issues;
    /** 用户可见的重写提示列表。可能为大型文本列表。 */
    private final List<String> userFacingRewriteHints;
    /** L1 prompt cache 写策略（决定审查结果是否写入缓存）。 */
    private final PromptCacheWritePolicy cacheWritePolicy;

    public ReviewerPayload(
            boolean approved, boolean rewriteRequired, String riskLevel,
            List<ReviewIssue> issues, List<String> userFacingRewriteHints,
            PromptCacheWritePolicy cacheWritePolicy
    ) {
        this.approved = approved;
        this.rewriteRequired = rewriteRequired;
        this.riskLevel = riskLevel;
        this.issues = issues;
        this.userFacingRewriteHints = userFacingRewriteHints;
        this.cacheWritePolicy = cacheWritePolicy;
    }
}
