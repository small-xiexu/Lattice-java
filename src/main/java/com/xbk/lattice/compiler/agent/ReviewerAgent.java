package com.xbk.lattice.compiler.agent;

/**
 * ReviewerAgent
 *
 * 职责：对单篇草稿执行结构化审查
 *
 * @author xiexu
 */
public interface ReviewerAgent {

    /**
     * 执行单篇草稿审查。
     *
     * @param reviewTask 审查输入任务
     * @return 审查输出结果
     */
    ReviewerResult review(ReviewTask reviewTask);
}
