package com.xbk.lattice.query.evidence.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 投影候选。
 *
 * <p>承接 Synthesizer 与 Projector 之间可出站的引用候选——通过 priority、verified、retrievalScore 排序筛选。
 *
 * @author xiexu
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProjectionCandidate {

    /** 候选唯一标识。 */
    private String projectionCandidateId;
    /** 关联的事实键。 */
    private String factKey;
    /** 关联的锚点 ID。 */
    private String anchorId;
    /** 首选引用格式（ARTICLE / SOURCE_FILE）。 */
    private ProjectionCitationFormat preferredCitationFormat;
    /** 引用目标键。 */
    private String targetKey;
    /** 优先级（数值越大越优先）。 */
    private int priority;
    /** 是否已验证（通过 quality gate）。 */
    private boolean verified;
    /** 检索相关性分数。 */
    private double retrievalScore;
}
