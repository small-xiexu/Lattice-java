package com.xbk.lattice.infra.persistence;

/**
 * Deep Research 运行记录
 *
 * 职责：承载 deep_research_runs 表的最小写入字段
 *
 * @author xiexu
 */
public class DeepResearchRunRecord {

    private final String queryId;

    private final String question;

    private final String routeReason;

    private final String planJson;

    private final int layerCount;

    private final int taskCount;

    private final int llmCallCount;

    private final double citationCoverage;

    private final boolean partialAnswer;

    private final boolean hasConflicts;

    private final Long finalAnswerAuditId;

    /**
     * 创建 Deep Research 运行记录。
     *
     * @param queryId 查询标识
     * @param question 查询问题
     * @param routeReason 路由原因
     * @param planJson 计划 JSON
     * @param layerCount 层数
     * @param taskCount 任务数
     * @param llmCallCount LLM 调用数
     * @param citationCoverage 引用覆盖率
     * @param partialAnswer 是否部分答案
     * @param hasConflicts 是否存在冲突
     * @param finalAnswerAuditId 最终答案审计主键
     */
    public DeepResearchRunRecord(
            String queryId,
            String question,
            String routeReason,
            String planJson,
            int layerCount,
            int taskCount,
            int llmCallCount,
            double citationCoverage,
            boolean partialAnswer,
            boolean hasConflicts,
            Long finalAnswerAuditId
    ) {
        this.queryId = queryId;
        this.question = question;
        this.routeReason = routeReason;
        this.planJson = planJson;
        this.layerCount = layerCount;
        this.taskCount = taskCount;
        this.llmCallCount = llmCallCount;
        this.citationCoverage = citationCoverage;
        this.partialAnswer = partialAnswer;
        this.hasConflicts = hasConflicts;
        this.finalAnswerAuditId = finalAnswerAuditId;
    }

    public String getQueryId() {
        return queryId;
    }

    public String getQuestion() {
        return question;
    }

    public String getRouteReason() {
        return routeReason;
    }

    public String getPlanJson() {
        return planJson;
    }

    public int getLayerCount() {
        return layerCount;
    }

    public int getTaskCount() {
        return taskCount;
    }

    public int getLlmCallCount() {
        return llmCallCount;
    }

    public double getCitationCoverage() {
        return citationCoverage;
    }

    public boolean isPartialAnswer() {
        return partialAnswer;
    }

    public boolean isHasConflicts() {
        return hasConflicts;
    }

    public Long getFinalAnswerAuditId() {
        return finalAnswerAuditId;
    }
}
