package com.xbk.lattice.query.deepresearch.domain;

import com.xbk.lattice.query.citation.CitationCheckReport;
import com.xbk.lattice.query.evidence.domain.AnswerProjectionBundle;
import lombok.Getter;
import lombok.Setter;

/**
 * Deep Research 综合结果。
 *
 * <p>承载综合器生成的最终答案与质量指标——包含内部草稿、最终答案、引用检查和投影包。
 * 引用 B17 的 AnswerProjectionBundle。
 *
 * @author xiexu
 */
@Getter
@Setter
public class DeepResearchSynthesisResult {

    /** 内部答案草稿（Synthesizer 中间产物）。 */
    private InternalAnswerDraft internalAnswerDraft;
    /** 最终综合答案 Markdown 文本。可能为大型文本。 */
    private String answerMarkdown;
    /** 引用检查报告。 */
    private CitationCheckReport citationCheckReport;
    /** 答案投影包（B17 AnswerProjectionBundle）。 */
    private AnswerProjectionBundle answerProjectionBundle;
    /** 是否为部分答案（仍有未解决的事实）。 */
    private boolean partialAnswer;
    /** 是否存在事实冲突。 */
    private boolean hasConflicts;
    /** 使用的证据卡总数。 */
    private int evidenceCardCount;
}
