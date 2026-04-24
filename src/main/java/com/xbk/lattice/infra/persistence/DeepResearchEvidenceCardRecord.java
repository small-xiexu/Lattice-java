package com.xbk.lattice.infra.persistence;

/**
 * Deep Research 证据卡记录
 *
 * 职责：承载 deep_research_evidence_cards 表的最小写入字段
 *
 * @author xiexu
 */
public class DeepResearchEvidenceCardRecord {

    private final Long runId;

    private final String evidenceId;

    private final int layerIndex;

    private final String taskId;

    private final String scope;

    private final String findingsJson;

    private final String gapsJson;

    private final String relatedLeadsJson;

    /**
     * 创建 Deep Research 证据卡记录。
     *
     * @param runId 运行主键
     * @param evidenceId 证据卡标识
     * @param layerIndex 层序号
     * @param taskId 任务标识
     * @param scope 研究范围
     * @param findingsJson findings JSON
     * @param gapsJson gaps JSON
     * @param relatedLeadsJson related leads JSON
     */
    public DeepResearchEvidenceCardRecord(
            Long runId,
            String evidenceId,
            int layerIndex,
            String taskId,
            String scope,
            String findingsJson,
            String gapsJson,
            String relatedLeadsJson
    ) {
        this.runId = runId;
        this.evidenceId = evidenceId;
        this.layerIndex = layerIndex;
        this.taskId = taskId;
        this.scope = scope;
        this.findingsJson = findingsJson;
        this.gapsJson = gapsJson;
        this.relatedLeadsJson = relatedLeadsJson;
    }

    public Long getRunId() {
        return runId;
    }

    public String getEvidenceId() {
        return evidenceId;
    }

    public int getLayerIndex() {
        return layerIndex;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getScope() {
        return scope;
    }

    public String getFindingsJson() {
        return findingsJson;
    }

    public String getGapsJson() {
        return gapsJson;
    }

    public String getRelatedLeadsJson() {
        return relatedLeadsJson;
    }
}
