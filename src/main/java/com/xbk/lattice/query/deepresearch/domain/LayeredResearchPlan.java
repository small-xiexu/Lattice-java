package com.xbk.lattice.query.deepresearch.domain;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 分层研究计划。
 *
 * <p>承载 Deep Research 的分层并行计划定义——各层按顺序执行，层内任务可并行。
 *
 * @author xiexu
 */
@Getter
@Setter
public class LayeredResearchPlan {

    /** 根问题（用户原始查询的深层意图）。 */
    private String rootQuestion;
    /** 研究层列表。按层序执行（索引低的层先执行）。 */
    private List<ResearchLayer> layers = new ArrayList<ResearchLayer>();

    public int layerCount() {
        return layers.size();
    }

    public int taskCount() {
        int taskCount = 0;
        for (ResearchLayer layer : layers) {
            taskCount += layer.getTasks().size();
        }
        return taskCount;
    }
}
