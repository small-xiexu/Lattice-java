package com.xbk.lattice.query.citation;

/**
 * Citation 校验结果
 *
 * 职责：表示单条引用规则校验后的细节结果
 *
 * @author xiexu
 */
public class CitationValidationResult {

    private final String targetKey;

    private final CitationSourceType sourceType;

    private final CitationValidationStatus status;

    private final double overlapScore;

    private final String reason;

    private final String matchedExcerpt;

    private final int ordinal;

    private final CitationValidationSource validatedBy;

    /**
     * 创建 Citation 校验结果。
     *
     * @param targetKey 目标键
     * @param sourceType 来源类型
     * @param status 校验状态
     * @param overlapScore 文本重叠分
     * @param reason 结果原因
     * @param matchedExcerpt 命中摘录
     * @param ordinal 引用序号
     */
    public CitationValidationResult(
            String targetKey,
            CitationSourceType sourceType,
            CitationValidationStatus status,
            double overlapScore,
            String reason,
            String matchedExcerpt,
            int ordinal
    ) {
        this(
                targetKey,
                sourceType,
                status,
                overlapScore,
                reason,
                matchedExcerpt,
                ordinal,
                CitationValidationSource.RULE
        );
    }

    /**
     * 创建 Citation 校验结果。
     *
     * @param targetKey 目标键
     * @param sourceType 来源类型
     * @param status 校验状态
     * @param overlapScore 文本重叠分
     * @param reason 结果原因
     * @param matchedExcerpt 命中摘录
     * @param ordinal 引用序号
     * @param validatedBy 校验来源
     */
    public CitationValidationResult(
            String targetKey,
            CitationSourceType sourceType,
            CitationValidationStatus status,
            double overlapScore,
            String reason,
            String matchedExcerpt,
            int ordinal,
            CitationValidationSource validatedBy
    ) {
        this.targetKey = targetKey;
        this.sourceType = sourceType;
        this.status = status;
        this.overlapScore = overlapScore;
        this.reason = reason;
        this.matchedExcerpt = matchedExcerpt;
        this.ordinal = ordinal;
        this.validatedBy = validatedBy == null ? CitationValidationSource.RULE : validatedBy;
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
     * 返回来源类型。
     *
     * @return 来源类型
     */
    public CitationSourceType getSourceType() {
        return sourceType;
    }

    /**
     * 返回校验状态。
     *
     * @return 校验状态
     */
    public CitationValidationStatus getStatus() {
        return status;
    }

    /**
     * 返回文本重叠分。
     *
     * @return 文本重叠分
     */
    public double getOverlapScore() {
        return overlapScore;
    }

    /**
     * 返回结果原因。
     *
     * @return 结果原因
     */
    public String getReason() {
        return reason;
    }

    /**
     * 返回命中摘录。
     *
     * @return 命中摘录
     */
    public String getMatchedExcerpt() {
        return matchedExcerpt;
    }

    /**
     * 返回引用序号。
     *
     * @return 引用序号
     */
    public int getOrdinal() {
        return ordinal;
    }

    /**
     * 返回校验来源。
     *
     * @return 校验来源
     */
    public CitationValidationSource getValidatedBy() {
        return validatedBy;
    }

    /**
     * 返回当前引用是否已验证。
     *
     * @return 是否已验证
     */
    public boolean isVerified() {
        return status == CitationValidationStatus.VERIFIED;
    }

    /**
     * 返回当前引用是否已降级。
     *
     * @return 是否已降级
     */
    public boolean isDemoted() {
        return status == CitationValidationStatus.DEMOTED || status == CitationValidationStatus.NOT_FOUND;
    }

    /**
     * 返回当前引用是否已跳过。
     *
     * @return 是否已跳过
     */
    public boolean isSkipped() {
        return status == CitationValidationStatus.SKIPPED;
    }
}
