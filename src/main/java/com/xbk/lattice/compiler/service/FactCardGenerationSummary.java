package com.xbk.lattice.compiler.service;

import java.util.List;

/**
 * 事实证据卡生成摘要
 *
 * 职责：承载证据卡生成后的数量与 source 回指质量指标
 *
 * @author xiexu
 */
public class FactCardGenerationSummary {

    private final int generatedCount;

    private final int withSourceChunkCount;

    private final int evidenceLocatedCount;

    private final List<String> cardIds;

    /**
     * 创建事实证据卡生成摘要。
     *
     * @param generatedCount 生成数量
     * @param withSourceChunkCount 带 source chunk 回指数量
     * @param evidenceLocatedCount 证据文本可定位数量
     * @param cardIds 证据卡标识列表
     */
    public FactCardGenerationSummary(
            int generatedCount,
            int withSourceChunkCount,
            int evidenceLocatedCount,
            List<String> cardIds
    ) {
        this.generatedCount = generatedCount;
        this.withSourceChunkCount = withSourceChunkCount;
        this.evidenceLocatedCount = evidenceLocatedCount;
        this.cardIds = cardIds == null ? List.of() : List.copyOf(cardIds);
    }

    /**
     * 获取生成数量。
     *
     * @return 生成数量
     */
    public int getGeneratedCount() {
        return generatedCount;
    }

    /**
     * 获取带 source chunk 回指数量。
     *
     * @return 带 source chunk 回指数量
     */
    public int getWithSourceChunkCount() {
        return withSourceChunkCount;
    }

    /**
     * 获取证据文本可定位数量。
     *
     * @return 证据文本可定位数量
     */
    public int getEvidenceLocatedCount() {
        return evidenceLocatedCount;
    }

    /**
     * 获取 source 回指率。
     *
     * @return source 回指率
     */
    public double getSourceReferenceRate() {
        if (generatedCount == 0) {
            return 0.0D;
        }
        return (double) withSourceChunkCount / (double) generatedCount;
    }

    /**
     * 获取证据定位率。
     *
     * @return 证据定位率
     */
    public double getEvidenceLocatedRate() {
        if (generatedCount == 0) {
            return 0.0D;
        }
        return (double) evidenceLocatedCount / (double) generatedCount;
    }

    /**
     * 获取证据卡标识列表。
     *
     * @return 证据卡标识列表
     */
    public List<String> getCardIds() {
        return cardIds;
    }
}
