package com.xbk.lattice.query.deepresearch.domain;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 证据卡
 *
 * 职责：表示单个研究任务产出的结构化证据载体
 *
 * @author xiexu
 */
@Data
public class EvidenceCard {

    private String evidenceId;

    private int layerIndex;

    private String taskId;

    private String scope;

    private List<EvidenceFinding> findings = new ArrayList<EvidenceFinding>();

    private List<String> gaps = new ArrayList<String>();

    private List<String> relatedLeads = new ArrayList<String>();

    private List<String> selectedArticleKeys = new ArrayList<String>();
}
