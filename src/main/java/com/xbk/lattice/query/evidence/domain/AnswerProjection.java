package com.xbk.lattice.query.evidence.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 对外答案投影
 *
 * 职责：表示最终答案中一个可见 citation literal 对应的白名单投影记录
 *
 * @author xiexu
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnswerProjection {

    private int projectionOrdinal;

    private String anchorId;

    private ProjectionCitationFormat sourceType;

    private String citationLiteral;

    private String targetKey;

    private ProjectionStatus status = ProjectionStatus.ACTIVE;

    private int repairRound;

    private Integer repairedFromProjectionOrdinal;
}
