package com.xbk.lattice.api.admin;

import lombok.Getter;

/**
 * 管理侧向量索引重建响应。
 *
 * <p>承载向量索引全量重建后的关键统计结果，由 {@code AdminVectorIndexController} 在重建完成后返回。
 *
 * @author xiexu
 */
@Getter
public class AdminVectorIndexRebuildResponse {

    /**
     * 本次重建目标文章数。
     *
     * <p>重建启动时快照的文章总量，不代表实际完成索引的数量。</p>
     */
    private final int targetArticleCount;

    /**
     * 重建前已索引文章数。
     *
     * <p>与 {@code indexedArticleCount} 对比可知本次重建的增量或减量。</p>
     */
    private final int previousIndexedArticleCount;

    /**
     * 重建后已索引文章数。
     *
     * <p>重建完成时刻的实际索引文章数。</p>
     */
    private final int indexedArticleCount;

    /**
     * 重建前已索引分块数。
     */
    private final int previousIndexedChunkCount;

    /**
     * 重建后已索引分块数。
     *
     * <p>与 {@code previousIndexedChunkCount} 对比可知分块粒度变化（如分块策略调整后的差异）。</p>
     */
    private final int indexedChunkCount;

    /**
     * 是否先清空旧索引。
     *
     * <p>回显请求中的 {@code truncateFirst} 值，便于管理侧确认实际执行模式。</p>
     */
    private final boolean truncateFirst;

    /**
     * 重建使用的 embedding 模型名。
     */
    private final String configuredModelName;

    /**
     * 操作人。
     */
    private final String operator;

    /**
     * 重建完成时间（ISO-8601 字符串）。
     */
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
}
