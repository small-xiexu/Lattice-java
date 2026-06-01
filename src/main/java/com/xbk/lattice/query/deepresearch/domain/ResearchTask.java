package com.xbk.lattice.query.deepresearch.domain;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 研究任务。
 *
 * <p>表示 Deep Research 中单个研究员节点的任务定义——含任务类型、问题、期望输出和上游依赖。
 *
 * @author xiexu
 */
@Getter
@Setter
public class ResearchTask {

    /** 任务唯一标识。 */
    private String taskId;
    /** 任务类型。默认 FACT_LOOKUP（事实查找）。 */
    private ResearchTaskType taskType = ResearchTaskType.FACT_LOOKUP;
    /** 任务对应的研究问题。 */
    private String question;
    /** 期望输出描述。 */
    private String expectedOutput;
    /** 期望的事实 schema（字段名列表）。 */
    private List<String> expectedFactSchema = new ArrayList<String>();
    /** 要求的证据类型列表。 */
    private List<String> requiredEvidenceTypes = new ArrayList<String>();
    /** 上游依赖任务 ID 列表（用于层间依赖解析）。 */
    private List<String> preferredUpstreamTaskIds = new ArrayList<String>();
    /** 检索焦点（限定检索范围的关键词或范围说明）。 */
    private String retrievalFocus;
    /** 是否必须解决（true 时该任务失败会导致所在层失败）。 */
    private boolean mustResolve;
}
