package com.xbk.lattice.governance;

import java.util.List;

/**
 * 链接增强条目
 *
 * 职责：描述单篇文章的 wiki-link 修复与关系区块同步结果
 *
 * @author xiexu
 */
public class LinkEnhancementItem {

    private final String conceptId;

    private final String title;

    private final boolean updated;

    private final int fixedLinkCount;

    private final int syncedSectionCount;

    private final List<String> unresolvedLinks;

    /**
     * 创建链接增强条目。
     *
     * @param conceptId 概念标识
     * @param title 标题
     * @param updated 是否已更新
     * @param fixedLinkCount 修复链接数量
     * @param syncedSectionCount 同步区块数量
     * @param unresolvedLinks 未解析链接列表
     */
    public LinkEnhancementItem(
            String conceptId,
            String title,
            boolean updated,
            int fixedLinkCount,
            int syncedSectionCount,
            List<String> unresolvedLinks
    ) {
        this.conceptId = conceptId;
        this.title = title;
        this.updated = updated;
        this.fixedLinkCount = fixedLinkCount;
        this.syncedSectionCount = syncedSectionCount;
        this.unresolvedLinks = unresolvedLinks;
    }

    /**
     * 获取概念标识。
     *
     * @return 概念标识
     */
    public String getConceptId() {
        return conceptId;
    }

    /**
     * 获取标题。
     *
     * @return 标题
     */
    public String getTitle() {
        return title;
    }

    /**
     * 是否已更新。
     *
     * @return 是否已更新
     */
    public boolean isUpdated() {
        return updated;
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
     * 获取未解析链接列表。
     *
     * @return 未解析链接列表
     */
    public List<String> getUnresolvedLinks() {
        return unresolvedLinks;
    }
}
