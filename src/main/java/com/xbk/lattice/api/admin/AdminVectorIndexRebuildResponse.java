package com.xbk.lattice.api.admin;

/**
 * 管理侧向量索引重建响应
 *
 * 职责：承载向量索引全量重建后的关键统计结果
 *
 * @author xiexu
 */
public class AdminVectorIndexRebuildResponse {

    private final int targetArticleCount;

    private final int previousIndexedArticleCount;

    private final int indexedArticleCount;

    private final int previousIndexedChunkCount;

    private final int indexedChunkCount;

    private final boolean truncateFirst;

    private final String configuredModelName;

    private final String operator;

    private final String rebuiltAt;

    /**
     * 创建管理侧向量索引重建响应。
     *
     * @param targetArticleCount 本次目标文章数
     * @param previousIndexedArticleCount 重建前向量索引数
     * @param indexedArticleCount 重建后向量索引数
     * @param previousIndexedChunkCount 重建前分块向量索引数
     * @param indexedChunkCount 重建后分块向量索引数
     * @param truncateFirst 是否先清空旧索引
     * @param configuredModelName 当前配置模型名
     * @param operator 操作人
     * @param rebuiltAt 重建完成时间
     */
    public AdminVectorIndexRebuildResponse(
            int targetArticleCount,
            int previousIndexedArticleCount,
            int indexedArticleCount,
            int previousIndexedChunkCount,
            int indexedChunkCount,
            boolean truncateFirst,
            String configuredModelName,
            String operator,
            String rebuiltAt
    ) {
        this.targetArticleCount = targetArticleCount;
        this.previousIndexedArticleCount = previousIndexedArticleCount;
        this.indexedArticleCount = indexedArticleCount;
        this.previousIndexedChunkCount = previousIndexedChunkCount;
        this.indexedChunkCount = indexedChunkCount;
        this.truncateFirst = truncateFirst;
        this.configuredModelName = configuredModelName;
        this.operator = operator;
        this.rebuiltAt = rebuiltAt;
    }

    /**
     * 返回本次目标文章数。
     *
     * @return 本次目标文章数
     */
    public int getTargetArticleCount() {
        return targetArticleCount;
    }

    /**
     * 返回重建前向量索引数。
     *
     * @return 重建前向量索引数
     */
    public int getPreviousIndexedArticleCount() {
        return previousIndexedArticleCount;
    }

    /**
     * 返回重建后向量索引数。
     *
     * @return 重建后向量索引数
     */
    public int getIndexedArticleCount() {
        return indexedArticleCount;
    }

    /**
     * 返回重建前分块向量索引数。
     *
     * @return 重建前分块向量索引数
     */
    public int getPreviousIndexedChunkCount() {
        return previousIndexedChunkCount;
    }

    /**
     * 返回重建后分块向量索引数。
     *
     * @return 重建后分块向量索引数
     */
    public int getIndexedChunkCount() {
        return indexedChunkCount;
    }

    /**
     * 返回是否先清空旧索引。
     *
     * @return 是否先清空旧索引
     */
    public boolean isTruncateFirst() {
        return truncateFirst;
    }

    /**
     * 返回当前配置模型名。
     *
     * @return 当前配置模型名
     */
    public String getConfiguredModelName() {
        return configuredModelName;
    }

    /**
     * 返回操作人。
     *
     * @return 操作人
     */
    public String getOperator() {
        return operator;
    }

    /**
     * 返回重建完成时间。
     *
     * @return 重建完成时间
     */
    public String getRebuiltAt() {
        return rebuiltAt;
    }
}
