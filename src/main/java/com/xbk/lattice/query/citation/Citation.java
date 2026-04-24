package com.xbk.lattice.query.citation;

/**
 * Citation 引用对象
 *
 * 职责：表示从答案文本中解析出的单条引用
 *
 * @author xiexu
 */
public class Citation {

    private final int ordinal;

    private final String literal;

    private final CitationSourceType sourceType;

    private final String targetKey;

    private final String claimText;

    private final String contextWindow;

    /**
     * 创建 Citation。
     *
     * @param ordinal 顺序号
     * @param literal 原始引用文本
     * @param sourceType 来源类型
     * @param targetKey 引用目标键
     * @param claimText 所属 claim 文本
     * @param contextWindow 所属上下文
     */
    public Citation(
            int ordinal,
            String literal,
            CitationSourceType sourceType,
            String targetKey,
            String claimText,
            String contextWindow
    ) {
        this.ordinal = ordinal;
        this.literal = literal;
        this.sourceType = sourceType;
        this.targetKey = targetKey;
        this.claimText = claimText;
        this.contextWindow = contextWindow;
    }

    /**
     * 返回顺序号。
     *
     * @return 顺序号
     */
    public int getOrdinal() {
        return ordinal;
    }

    /**
     * 返回原始引用文本。
     *
     * @return 原始引用文本
     */
    public String getLiteral() {
        return literal;
    }

    /**
     * 返回来源类型。
     *
     * @return 来源类型
     */
    public CitationSourceType getSourceType() {
        return sourceType;
    }

    /**
     * 返回目标键。
     *
     * @return 目标键
     */
    public String getTargetKey() {
        return targetKey;
    }

    /**
     * 返回所属 claim 文本。
     *
     * @return 所属 claim 文本
     */
    public String getClaimText() {
        return claimText;
    }

    /**
     * 返回上下文窗口。
     *
     * @return 上下文窗口
     */
    public String getContextWindow() {
        return contextWindow;
    }
}
