package com.xbk.lattice.compiler.service;

import com.xbk.lattice.compiler.model.AnalyzedConcept;
import com.xbk.lattice.compiler.model.MergedConcept;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 最小跨批次合并节点
 *
 * 职责：按 conceptId 合并同组批次输出
 *
 * @author xiexu
 */
public class CrossGroupMergeNode {

    /**
     * 合并分析结果。
     *
     * @param analyzedConcepts 分析结果
     * @return 合并后的概念列表
     */
    public List<MergedConcept> merge(List<AnalyzedConcept> analyzedConcepts) {
        Map<String, MergeBucket> buckets = new LinkedHashMap<String, MergeBucket>();
        for (AnalyzedConcept analyzedConcept : analyzedConcepts) {
            MergeBucket mergeBucket = buckets.computeIfAbsent(
                    analyzedConcept.getConceptId(),
                    key -> new MergeBucket(analyzedConcept.getTitle())
            );
            mergeBucket.sourcePaths.addAll(analyzedConcept.getSourcePaths());
            mergeBucket.snippets.addAll(analyzedConcept.getSnippets());
        }

        List<MergedConcept> mergedConcepts = new ArrayList<MergedConcept>();
        for (Map.Entry<String, MergeBucket> entry : buckets.entrySet()) {
            MergeBucket mergeBucket = entry.getValue();
            mergedConcepts.add(new MergedConcept(
                    entry.getKey(),
                    mergeBucket.title,
                    new ArrayList<String>(mergeBucket.sourcePaths),
                    new ArrayList<String>(mergeBucket.snippets)
            ));
        }
        return mergedConcepts;
    }

    /**
     * 合并桶
     *
     * 职责：累积单个概念的来源与片段
     *
     * @author xiexu
     */
    private static class MergeBucket {

        private final String title;

        private final Set<String> sourcePaths = new LinkedHashSet<String>();

        private final Set<String> snippets = new LinkedHashSet<String>();

        /**
         * 创建合并桶。
         *
         * @param title 标题
         */
        private MergeBucket(String title) {
            this.title = title;
        }
    }
}
