package com.xbk.lattice.api.admin;

import lombok.Getter;

import java.util.List;

/**
 * 知识库帮助卡响应。
 *
 * <p>承载工作台"现在该怎么做"帮助卡所需的全部展示字段——含语气、标题、描述和可执行动作，
 * 嵌套于 {@link AdminProcessingTaskSummaryResponse} 中。
 *
 * @author xiexu
 */
@Getter
public class AdminKnowledgeHelpStateResponse {

    /** 帮助卡色调（驱动前端展示风格，如 {@code info} / {@code warning}）。 */
    private final String tone;

    /** 帮助卡标题。 */
    private final String title;

    /** 帮助卡描述文本。 */
    private final String description;

    /** FAQ 锚点键（前端用于跳转到对应帮助文档）。可为空。 */
    private final String faqKey;

    /** 可执行动作列表。 */
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
}
