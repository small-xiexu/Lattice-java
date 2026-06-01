package com.xbk.lattice.query.evidence.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 答案投影包。
 *
 * <p>承载最终用户答案与其 projection 白名单——用于出站时的 citation 验证和引用修复。
 *
 * @author xiexu
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AnswerProjectionBundle {

    /** 完整 Markdown 格式答案。可能很长，禁止依赖 toString() 输出。 */
    private String answerMarkdown;
    /** 投影白名单列表。 */
    private List<AnswerProjection> projections = new ArrayList<AnswerProjection>();
}
