package com.xbk.lattice.query.deepresearch.domain;

import com.xbk.lattice.query.evidence.domain.EvidenceAnchor;
import com.xbk.lattice.query.evidence.domain.FactFinding;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 证据卡。
 *
 * <p>表示单个研究任务产出的结构化证据载体——整合来自多个检索通道的事实发现和证据锚点。
 * 引用 B17 evidence domain 的 FactFinding 和 EvidenceAnchor 类型。
 *
 * @author xiexu
 */
@Getter
@Setter
public class EvidenceCard {

    /** 证据卡唯一标识。 */
    private String evidenceId;
    /** 所属研究层序号。 */
    private int layerIndex;
    /** 所属研究任务 ID。 */
    private String taskId;
    /** 研究范围说明。 */
    private String scope;
    /** 事实发现列表（引用 FactFinding）。 */
    private List<FactFinding> factFindings = new ArrayList<FactFinding>();
    /** 证据锚点列表（引用 EvidenceAnchor）。 */
    private List<EvidenceAnchor> evidenceAnchors = new ArrayList<EvidenceAnchor>();
    /** 关联的检索命中列表。 */
    private List<ResearchTaskHit> taskHits = new ArrayList<ResearchTaskHit>();
    /** 知识缺口列表。 */
    private List<String> gaps = new ArrayList<String>();
    /** 后续跟进建议列表。 */
    private List<String> followUps = new ArrayList<String>();
    /** 相关线索列表。 */
    private List<String> relatedLeads = new ArrayList<String>();
    /** 选中的文章键列表（用于跨层引用）。 */
    private List<String> selectedArticleKeys = new ArrayList<String>();
}
