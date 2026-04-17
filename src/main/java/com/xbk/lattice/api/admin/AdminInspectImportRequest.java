package com.xbk.lattice.api.admin;

/**
 * 管理侧 inspection 导入请求
 *
 * 职责：承载 inspection 最终答案导入参数
 *
 * @author xiexu
 */
public class AdminInspectImportRequest {

    private String inspectionId;

    private String finalAnswer;

    private String confirmedBy;

    public String getInspectionId() {
        return inspectionId;
    }

    public void setInspectionId(String inspectionId) {
        this.inspectionId = inspectionId;
    }

    public String getFinalAnswer() {
        return finalAnswer;
    }

    public void setFinalAnswer(String finalAnswer) {
        this.finalAnswer = finalAnswer;
    }

    public String getConfirmedBy() {
        return confirmedBy;
    }

    public void setConfirmedBy(String confirmedBy) {
        this.confirmedBy = confirmedBy;
    }
}
