package com.xbk.lattice.api.query;

/**
 * 待确认查询纠错请求。
 *
 * <p>承载对一条待确认（pending）查询的纠错内容。由 Spring MVC 从 JSON 请求体绑定。
 *
 * @author xiexu
 */
public class PendingQueryCorrectionRequest {

    /**
     * 纠错内容。
     *
     * <p>调用方提交的更正文本，用于修正 pending query 中的错误答案或补充缺失信息。
     * 该内容会被系统用于重新生成更准确的回答。</p>
     */
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
