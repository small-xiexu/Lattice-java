package com.xbk.lattice.query.deepresearch.domain;

import com.xbk.lattice.query.evidence.domain.EvidenceAnchor;
import com.xbk.lattice.query.evidence.domain.FactFinding;
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

    private List<FactFinding> factFindings = new ArrayList<FactFinding>();

    private List<EvidenceAnchor> evidenceAnchors = new ArrayList<EvidenceAnchor>();

    private List<ResearchTaskHit> taskHits = new ArrayList<ResearchTaskHit>();

    private List<String> gaps = new ArrayList<String>();

    private List<String> followUps = new ArrayList<String>();

    private List<String> relatedLeads = new ArrayList<String>();

    private List<String> selectedArticleKeys = new ArrayList<String>();
}
