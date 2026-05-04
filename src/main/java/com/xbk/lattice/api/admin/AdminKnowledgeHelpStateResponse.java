package com.xbk.lattice.api.admin;

import java.util.List;

/**
 * 知识库帮助卡响应。
 *
 * 职责：承载工作台“现在该怎么做”帮助卡所需的全部展示字段
 *
 * @author xiexu
 */
public class AdminKnowledgeHelpStateResponse {

    private final String tone;

    private final String title;

    private final String description;

    private final String faqKey;

    private final List<AdminKnowledgeHelpActionResponse> actions;

    /**
     * 创建知识库帮助卡响应。
     *
     * @param tone 语气
     * @param title 标题
     * @param description 描述
     * @param faqKey 常见问题锚点
     * @param actions 可执行动作
     */
    public AdminKnowledgeHelpStateResponse(
            String tone,
            String title,
            String description,
            String faqKey,
            List<AdminKnowledgeHelpActionResponse> actions
    ) {
        this.tone = tone;
        this.title = title;
        this.description = description;
        this.faqKey = faqKey;
        this.actions = actions;
    }

    public String getTone() {
        return tone;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getFaqKey() {
        return faqKey;
    }

    public List<AdminKnowledgeHelpActionResponse> getActions() {
        return actions;
    }
}
