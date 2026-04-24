package com.xbk.lattice.query.deepresearch.domain;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 分层摘要
 *
 * 职责：表示每一层研究任务完成后的汇总结果
 *
 * @author xiexu
 */
@Data
public class LayerSummary {

    private int layerIndex;

    private String summaryMarkdown;

    private List<String> taskIds = new ArrayList<String>();

    private List<String> evidenceIds = new ArrayList<String>();

    private int gapCount;
}
