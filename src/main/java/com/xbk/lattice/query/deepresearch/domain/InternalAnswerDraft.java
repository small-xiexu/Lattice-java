package com.xbk.lattice.query.deepresearch.domain;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 内部答案草稿
 *
 * 职责：承载 Synthesizer 产出的内部结论、缺失事实与冲突事实
 *
 * @author xiexu
 */
@Data
public class InternalAnswerDraft {

    private String draftMarkdown;

    private List<String> resolvedFactKeys = new ArrayList<String>();

    private List<String> missingFactKeys = new ArrayList<String>();

    private List<String> conflictingFactKeys = new ArrayList<String>();
}
