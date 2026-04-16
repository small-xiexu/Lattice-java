package com.xbk.lattice.compiler.service;

/**
 * Chunk 全量重建结果
 *
 * 职责：承载 article/source chunks 完整重建后的统计信息
 *
 * @author xiexu
 */
public class ChunkRebuildResult {

    private final int rebuiltArticleCount;

    private final int rebuiltSourceFileCount;

    private final int articleChunkCount;

    private final int sourceFileChunkCount;

    private final String rebuiltAt;

    /**
     * 创建 chunk 全量重建结果。
     *
     * @param rebuiltArticleCount 重建文章数量
     * @param rebuiltSourceFileCount 重建源文件数量
     * @param articleChunkCount 文章 chunk 数量
     * @param sourceFileChunkCount 源文件 chunk 数量
     * @param rebuiltAt 重建完成时间
     */
    public ChunkRebuildResult(
            int rebuiltArticleCount,
            int rebuiltSourceFileCount,
            int articleChunkCount,
            int sourceFileChunkCount,
            String rebuiltAt
    ) {
        this.rebuiltArticleCount = rebuiltArticleCount;
        this.rebuiltSourceFileCount = rebuiltSourceFileCount;
        this.articleChunkCount = articleChunkCount;
        this.sourceFileChunkCount = sourceFileChunkCount;
        this.rebuiltAt = rebuiltAt;
    }

    /**
     * 获取重建文章数量。
     *
     * @return 重建文章数量
     */
    public int getRebuiltArticleCount() {
        return rebuiltArticleCount;
    }

    /**
     * 获取重建源文件数量。
     *
     * @return 重建源文件数量
     */
    public int getRebuiltSourceFileCount() {
        return rebuiltSourceFileCount;
    }

    /**
     * 获取文章 chunk 数量。
     *
     * @return 文章 chunk 数量
     */
    public int getArticleChunkCount() {
        return articleChunkCount;
    }

    /**
     * 获取源文件 chunk 数量。
     *
     * @return 源文件 chunk 数量
     */
    public int getSourceFileChunkCount() {
        return sourceFileChunkCount;
    }

    /**
     * 获取重建完成时间。
     *
     * @return 重建完成时间
     */
    public String getRebuiltAt() {
        return rebuiltAt;
    }
}
