package com.xbk.lattice.api.query;

/**
 * 待确认查询纠错请求
 *
 * 职责：承载 pending query 的纠错内容
 *
 * @author xiexu
 */
public class PendingQueryCorrectionRequest {

    private String correction;

    /**
     * 获取纠错内容。
     *
     * @return 纠错内容
     */
    public String getCorrection() {
        return correction;
    }

    /**
     * 设置纠错内容。
     *
     * @param correction 纠错内容
     */
    public void setCorrection(String correction) {
        this.correction = correction;
    }
}
