package com.xbk.lattice.governance;

import java.util.List;

/**
 * 文章纠错结果
 *
 * 职责：承载单次纠错后的文章正文、下游影响范围与证据支持结论
 *
 * @author xiexu
 */
public class ArticleCorrectionResult {

    private final String conceptId;

    private final String revisedContent;

    private final List<String> downstreamIds;

    private final boolean validationSupported;

    /**
     * 创建文章纠错结果。
     *
     * @param conceptId 概念标识
     * @param revisedContent 修正后的完整文章
     * @param downstreamIds 下游受影响概念标识
     * @param validationSupported 源文件是否支持本次纠错
     */
    public ArticleCorrectionResult(
            String conceptId,
            String revisedContent,
            List<String> downstreamIds,
            boolean validationSupported
    ) {
        this.conceptId = conceptId;
        this.revisedContent = revisedContent;
        this.downstreamIds = downstreamIds;
        this.validationSupported = validationSupported;
    }

    public String getConceptId() {
        return conceptId;
    }

    public String getRevisedContent() {
        return revisedContent;
    }

    public List<String> getDownstreamIds() {
        return downstreamIds;
    }

    public boolean isValidationSupported() {
        return validationSupported;
    }
}
