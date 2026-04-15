package com.xbk.lattice.governance;

import java.util.List;

/**
 * Inspection 报告
 *
 * 职责：汇总待人工确认的问题清单
 *
 * @author xiexu
 */
public class InspectionReport {

    private final List<InspectionQuestion> questions;

    /**
     * 创建 inspection 报告。
     *
     * @param questions 问题清单
     */
    public InspectionReport(List<InspectionQuestion> questions) {
        this.questions = questions;
    }

    public List<InspectionQuestion> getQuestions() {
        return questions;
    }

    public int getTotalQuestions() {
        return questions.size();
    }
}
