package com.xbk.lattice.query.deepresearch.domain;

import com.xbk.lattice.query.citation.CitationCheckReport;
import lombok.Data;

/**
 * Deep Research 综合结果
 *
 * 职责：承载综合器生成的最终答案与质量指标
 *
 * @author xiexu
 */
@Data
public class DeepResearchSynthesisResult {

    private String answerMarkdown;

    private CitationCheckReport citationCheckReport;

    private boolean partialAnswer;

    private boolean hasConflicts;

    private int evidenceCardCount;
}
