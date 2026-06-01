package com.xbk.lattice.query.deepresearch.domain;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 分层摘要。
 *
 * <p>表示每一层研究任务完成后的汇总结果——供上层任务和 Synthesizer 消费。
 *
 * @author xiexu
 */
@Getter
@Setter
public class LayerSummary {

    /** 层级序号。 */
    private int layerIndex;
    /**
     * 层级摘要 Markdown 文本。
     *
     * <p>可能为大型文本，禁止进入日志型 toString()。</p>
     */
    private String summaryMarkdown;
    /** 该层包含的任务 ID 列表。 */
    private List<String> taskIds = new ArrayList<String>();
    /** 该层产出的证据 ID 列表。 */
    private List<String> evidenceIds = new ArrayList<String>();
    /** 该层发现的知识缺口数量。 */
    private int gapCount;
}
