package com.xbk.lattice.api.admin;

import lombok.Getter;

/**
 * 当前处理任务汇总卡响应。
 *
 * <p>承载工作台顶部概览卡片的展示文案与数值，
 * 由 {@code AdminProcessingTaskController} 组装返回。
 *
 * @author xiexu
 */
@Getter
public class AdminProcessingTaskSummaryCardResponse {

    /** 卡片标题。 */
    private final String label;

    /** 卡片数值（如任务计数）。 */
    private final int value;

    /** 卡片补充说明。可为空。 */
    private final String note;

    /** 卡片语气（驱动前端展示色调，如 {@code info} / {@code warning} / {@code success}）。 */
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
}
