package com.xbk.lattice.query.citation;

import java.util.List;

/**
 * Claim 分段
 *
 * 职责：表示答案中的一个可核验结论片段及其关联引用
 *
 * @author xiexu
 */
public class ClaimSegment {

    private final int claimIndex;

    private final String claimText;

    private final String paragraphText;

    private final List<Citation> citations;

    /**
     * 创建 Claim 分段。
     *
     * @param claimIndex claim 顺序
     * @param claimText claim 文本
     * @param paragraphText 原始段落文本
     * @param citations 段落内引用
     */
    public ClaimSegment(int claimIndex, String claimText, String paragraphText, List<Citation> citations) {
        this.claimIndex = claimIndex;
        this.claimText = claimText;
        this.paragraphText = paragraphText;
        this.citations = citations;
    }

    /**
     * 返回 claim 顺序。
     *
     * @return claim 顺序
     */
    public int getClaimIndex() {
        return claimIndex;
    }

    /**
     * 返回 claim 文本。
     *
     * @return claim 文本
     */
    public String getClaimText() {
        return claimText;
    }

    /**
     * 返回原始段落文本。
     *
     * @return 原始段落文本
     */
    public String getParagraphText() {
        return paragraphText;
    }

    /**
     * 返回段落内引用。
     *
     * @return 段落内引用
     */
    public List<Citation> getCitations() {
        return citations;
    }
}
