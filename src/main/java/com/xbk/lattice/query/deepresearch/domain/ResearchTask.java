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

    private String question;

    private String expectedOutput;

    private List<String> preferredUpstreamTaskIds = new ArrayList<String>();

    private String retrievalFocus;
}
