package com.xbk.lattice.query.deepresearch.domain;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 分层研究计划
 *
 * 职责：承载 Deep Research 的分层并行计划定义
 *
 * @author xiexu
 */
@Data
public class LayeredResearchPlan {

    private String rootQuestion;

    private List<ResearchLayer> layers = new ArrayList<ResearchLayer>();

    /**
     * 返回总层数。
     *
     * @return 总层数
     */
    public int layerCount() {
        return layers.size();
    }

    /**
     * 返回总任务数。
     *
     * @return 总任务数
     */
    public int taskCount() {
        int taskCount = 0;
        for (ResearchLayer layer : layers) {
            taskCount += layer.getTasks().size();
        }
        return taskCount;
    }
}
