package com.xbk.lattice.query.evidence.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 对外答案投影。
 *
 * <p>表示最终答案中一个可见 citation literal 对应的白名单投影记录——包含引用修复追踪信息。
 *
 * @author xiexu
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AnswerProjection {

    /** 投影序号（答案中 citation literal 的位置）。 */
    private int projectionOrdinal;
    /** 关联的证据锚点 ID。 */
    private String anchorId;
    /** 引用来源类型（ARTICLE / SOURCE_FILE）。 */
    private ProjectionCitationFormat sourceType;
    /** 引用字面量（答案中显示的可点击引用文本，如 [1]）。 */
    private String citationLiteral;
    /** 目标键（引用指向的实体标识）。 */
    private String targetKey;
    /** 投影状态。默认 ACTIVE（活跃）。 */
    private ProjectionStatus status = ProjectionStatus.ACTIVE;
    /** 引用修复轮次（0 = 未修复）。 */
    private int repairRound;
    /** 修复来源投影序号。null = 未修复或原始投影。 */
    private Integer repairedFromProjectionOrdinal;
}
