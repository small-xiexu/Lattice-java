package com.xbk.lattice.query.deepresearch.domain;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 内部答案草稿。
 *
 * <p>承载 Synthesizer 产出的内部结论、缺失事实与冲突事实——供综合器进一步处理和引用校验。
 *
 * @author xiexu
 */
@Getter
@Setter
public class InternalAnswerDraft {

    /** 内部草稿 Markdown 文本。可能为大型文本。 */
    private String draftMarkdown;
    /** 已解决的事实键列表。 */
    private List<String> resolvedFactKeys = new ArrayList<String>();
    /** 缺失的事实键列表（证据不足）。 */
    private List<String> missingFactKeys = new ArrayList<String>();
    /** 冲突的事实键列表（多源证据不一致）。 */
    private List<String> conflictingFactKeys = new ArrayList<String>();
}
