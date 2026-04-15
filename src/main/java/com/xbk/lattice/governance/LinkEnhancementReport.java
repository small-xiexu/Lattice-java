package com.xbk.lattice.governance;

import java.util.List;

/**
 * 链接增强报告
 *
 * 职责：汇总知识库链接增强的处理数量、修复数量与明细条目
 *
 * @author xiexu
 */
public class LinkEnhancementReport {

    private final int totalArticles;

    private final int processedArticleCount;

    private final int updatedArticleCount;

    private final int fixedLinkCount;

    private final int syncedSectionCount;

    private final int unresolvedLinkCount;

    private final List<LinkEnhancementItem> items;

    /**
     * 创建链接增强报告。
     *
     * @param totalArticles 文章总数
     * @param processedArticleCount 已处理文章数
     * @param updatedArticleCount 已更新文章数
     * @param fixedLinkCount 修复链接数量
     * @param syncedSectionCount 同步区块数量
     * @param unresolvedLinkCount 未解析链接数量
     * @param items 明细条目
     */
    public LinkEnhancementReport(
            int totalArticles,
            int processedArticleCount,
            int updatedArticleCount,
            int fixedLinkCount,
            int syncedSectionCount,
            int unresolvedLinkCount,
            List<LinkEnhancementItem> items
    ) {
        this.totalArticles = totalArticles;
        this.processedArticleCount = processedArticleCount;
        this.updatedArticleCount = updatedArticleCount;
        this.fixedLinkCount = fixedLinkCount;
        this.syncedSectionCount = syncedSectionCount;
        this.unresolvedLinkCount = unresolvedLinkCount;
        this.items = items;
    }

    /**
     * 获取文章总数。
     *
     * @return 文章总数
     */
    public int getTotalArticles() {
        return totalArticles;
    }

    /**
     * 获取已处理文章数。
     *
     * @return 已处理文章数
     */
    public int getProcessedArticleCount() {
        return processedArticleCount;
    }

    /**
     * 获取已更新文章数。
     *
     * @return 已更新文章数
     */
    public int getUpdatedArticleCount() {
        return updatedArticleCount;
    }

    /**
     * 获取修复链接数量。
     *
     * @return 修复链接数量
     */
    public int getFixedLinkCount() {
        return fixedLinkCount;
    }

    /**
     * 获取同步区块数量。
     *
     * @return 同步区块数量
     */
    public int getSyncedSectionCount() {
        return syncedSectionCount;
    }

    /**
     * 获取未解析链接数量。
     *
     * @return 未解析链接数量
     */
    public int getUnresolvedLinkCount() {
        return unresolvedLinkCount;
    }

    /**
     * 获取明细条目。
     *
     * @return 明细条目
     */
    public List<LinkEnhancementItem> getItems() {
        return items;
    }
}
