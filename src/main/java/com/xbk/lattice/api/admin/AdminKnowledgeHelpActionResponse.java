package com.xbk.lattice.api.admin;

/**
 * 知识库帮助卡动作响应。
 *
 * 职责：承载工作台帮助卡可执行动作的展示文案与前端动作标识
 *
 * @author xiexu
 */
public class AdminKnowledgeHelpActionResponse {

    private final String label;

    private final String action;

    private final String className;

    /**
     * 创建知识库帮助卡动作响应。
     *
     * @param label 动作文案
     * @param action 前端动作标识
     * @param className 按钮样式
     */
    public AdminKnowledgeHelpActionResponse(String label, String action, String className) {
        this.label = label;
        this.action = action;
        this.className = className;
    }

    public String getLabel() {
        return label;
    }

    public String getAction() {
        return action;
    }

    public String getClassName() {
        return className;
    }
}
