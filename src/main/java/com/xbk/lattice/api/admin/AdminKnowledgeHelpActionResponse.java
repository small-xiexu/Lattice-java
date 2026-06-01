package com.xbk.lattice.api.admin;

import lombok.Getter;

/**
 * 知识库帮助卡动作响应。
 *
 * <p>承载工作台帮助卡可执行动作的展示文案与前端动作标识，
 * 嵌套于 {@link AdminKnowledgeHelpStateResponse} 中。
 *
 * @author xiexu
 */
@Getter
public class AdminKnowledgeHelpActionResponse {

    /** 动作展示文案。 */
    private final String label;

    /** 前端动作标识（用于路由到对应的处理页面或触发操作）。 */
    private final String action;

    /** 按钮样式类名（前端 CSS class）。 */
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
}
