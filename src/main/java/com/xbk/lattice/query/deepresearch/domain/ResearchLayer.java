package com.xbk.lattice.query.deepresearch.domain;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 研究层
 *
 * 职责：表示同一层可并行执行的一组研究任务
 *
 * @author xiexu
 */
@Data
public class ResearchLayer {

    private int layerIndex;

    private List<ResearchTask> tasks = new ArrayList<ResearchTask>();
}
