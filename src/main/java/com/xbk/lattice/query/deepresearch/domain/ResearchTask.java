package com.xbk.lattice.query.deepresearch.domain;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 研究任务
 *
 * 职责：表示 Deep Research 中单个研究员节点的任务定义
 *
 * @author xiexu
 */
@Data
public class ResearchTask {

    private String taskId;

    private ResearchTaskType taskType = ResearchTaskType.FACT_LOOKUP;

    private String question;

    private String expectedOutput;

    private List<String> expectedFactSchema = new ArrayList<String>();

    private List<String> requiredEvidenceTypes = new ArrayList<String>();

    private List<String> preferredUpstreamTaskIds = new ArrayList<String>();

    private String retrievalFocus;

    private boolean mustResolve;
}
