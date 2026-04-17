package com.xbk.lattice.compiler.service;

import com.xbk.lattice.compiler.model.MergedConcept;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 增量编译规划结果
 *
 * 职责：承载 Graph 增量分支所需的增强映射、新建概念与空跑标记
 *
 * @author xiexu
 */
@Data
public class IncrementalCompilePlanResult {

    private Map<String, List<MergedConcept>> enhancementConcepts = new LinkedHashMap<String, List<MergedConcept>>();

    private List<MergedConcept> conceptsToCreate = List.of();

    private boolean nothingToDo;
}
