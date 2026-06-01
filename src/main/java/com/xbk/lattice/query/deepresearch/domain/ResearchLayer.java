package com.xbk.lattice.query.deepresearch.domain;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 研究层。
 *
 * <p>表示同一层可并行执行的一组研究任务——层内任务共享相同依赖上下文，层间按顺序执行。
 *
 * @author xiexu
 */
@Getter
@Setter
public class ResearchLayer {

    /** 层级序号（从 0 开始，递增）。 */
    private int layerIndex;
    /** 该层包含的研究任务列表。 */
    private List<ResearchTask> tasks = new ArrayList<ResearchTask>();
}
