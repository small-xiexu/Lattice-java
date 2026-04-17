package com.xbk.lattice.api.admin;

/**
 * 管理侧文章纠错请求
 *
 * 职责：承载单篇文章纠错摘要
 *
 * @author xiexu
 */
public class AdminArticleCorrectionRequest {

    private String correctionSummary;

    /**
     * 获取纠错摘要。
     *
     * @return 纠错摘要
     */
    public String getCorrectionSummary() {
        return correctionSummary;
    }

    /**
     * 设置纠错摘要。
     *
     * @param correctionSummary 纠错摘要
     */
    public void setCorrectionSummary(String correctionSummary) {
        this.correctionSummary = correctionSummary;
    }
}
