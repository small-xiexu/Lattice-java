package com.xbk.lattice.api.admin;

import lombok.Getter;

import java.util.List;

/**
 * 管理侧 pending 条目响应。
 *
 * <p>承载管理侧 pending 列表中单条记录的完整信息——含用户问题、答案、审查状态和关联数据。
 * 禁止引入 {@code @Data}：{@code question} 和 {@code answer} 为用户查询内容和生成结果，
 * 可能含 PII 或长文本。
 *
 * @author xiexu
 */
@Getter
public class AdminPendingItemResponse {

    /** 查询会话标识。 */
    private final String queryId;

    /**
     * 用户原始问题文本。
     *
     * <p>可能含 PII，禁止参与 {@code toString()}。</p>
     */
    private final String question;

    /**
     * 系统生成答案文本。
     *
     * <p>可能为长文本，禁止参与 {@code toString()}。</p>
     */
    private final String answer;

    /**
     * 审查状态。
     *
     * <p>如 {@code needs_human_review} / {@code pending_review}。
     * 驱动前端展示处理标签和操作按钮。</p>
     */
    private final String reviewStatus;

    /** 选中的概念标识列表。 */
    private final List<String> selectedConceptIds;

    /** 来源文件路径列表。 */
    private final List<String> sourceFilePaths;

    /** 记录创建时间（ISO-8601 字符串）。 */
    private final String createdAt;

    /**
     * 记录过期时间（ISO-8601 字符串）。
     *
     * <p>过期后 pending 记录可能被自动清理，不再展示给管理侧。</p>
     */
    private final String expiresAt;

    /**
     * 创建管理侧 pending 条目响应。
     *
     * @param queryId 查询标识
     * @param question 问题
     * @param answer 答案
     * @param reviewStatus 审查状态
     * @param selectedConceptIds 概念标识
     * @param sourceFilePaths 来源路径
     * @param createdAt 创建时间
     * @param expiresAt 过期时间
     */
    public AdminPendingItemResponse(
            String queryId,
            String question,
            String answer,
            String reviewStatus,
            List<String> selectedConceptIds,
            List<String> sourceFilePaths,
            String createdAt,
            String expiresAt
    ) {
        this.queryId = queryId;
        this.question = question;
        this.answer = answer;
        this.reviewStatus = reviewStatus;
        this.selectedConceptIds = selectedConceptIds;
        this.sourceFilePaths = sourceFilePaths;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }
}
