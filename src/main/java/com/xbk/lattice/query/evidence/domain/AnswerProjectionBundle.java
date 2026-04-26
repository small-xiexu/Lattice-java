package com.xbk.lattice.query.evidence.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 答案投影包
 *
 * 职责：承载最终用户答案与其 projection 白名单
 *
 * @author xiexu
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnswerProjectionBundle {

    private String answerMarkdown;

    private List<AnswerProjection> projections = new ArrayList<AnswerProjection>();
}
