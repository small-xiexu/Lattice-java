package com.xbk.lattice.api.admin;

/**
 * 当前处理任务汇总卡响应。
 *
 * 职责：承载工作台顶部概览卡片的展示文案与数值
 *
 * @author xiexu
 */
public class AdminProcessingTaskSummaryCardResponse {

    private final String label;

    private final int value;

    private final String note;

    private final String tone;

    /**
     * 创建当前处理任务汇总卡响应。
     *
     * @param label 卡片标题
     * @param value 卡片数值
     * @param note 卡片说明
     * @param tone 卡片语气
     */
    public AdminProcessingTaskSummaryCardResponse(String label, int value, String note, String tone) {
        this.label = label;
        this.value = value;
        this.note = note;
        this.tone = tone;
    }

    public String getLabel() {
        return label;
    }

    public int getValue() {
        return value;
    }

    public String getNote() {
        return note;
    }

    public String getTone() {
        return tone;
    }
}
