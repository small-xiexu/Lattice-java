package com.xbk.lattice.query.evidence.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 投影候选
 *
 * 职责：承接 Synthesizer 与 Projector 之间可出站的引用候选
 *
 * @author xiexu
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProjectionCandidate {

    private String projectionCandidateId;

    private String factKey;

    private String anchorId;

    private ProjectionCitationFormat preferredCitationFormat;

    private String targetKey;

    private int priority;

    private boolean verified;

    private double retrievalScore;
}
