package com.xbk.lattice.compiler.service;

import com.xbk.lattice.compiler.model.AnalyzedConcept;
import com.xbk.lattice.compiler.model.RawSource;
import com.xbk.lattice.compiler.model.SourceBatch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 最小分析节点
 *
 * 职责：把批次内容转换为可合并的概念对象
 *
 * @author xiexu
 */
public class AnalyzeNode {

    /**
     * 分析分组内的所有批次。
     *
     * @param groupKey 分组键
     * @param sourceBatches 批次列表
     * @return 分析后的概念列表
     */
    public List<AnalyzedConcept> analyze(String groupKey, List<SourceBatch> sourceBatches) {
        List<AnalyzedConcept> analyzedConcepts = new ArrayList<AnalyzedConcept>();
        String conceptId = normalizeGroupKey(groupKey);
        String title = toTitle(groupKey);

        for (SourceBatch sourceBatch : sourceBatches) {
            List<String> sourcePaths = new ArrayList<String>();
            List<String> snippets = new ArrayList<String>();
            for (RawSource rawSource : sourceBatch.getSources()) {
                sourcePaths.add(rawSource.getRelativePath());
                snippets.add(rawSource.getContent());
            }
            analyzedConcepts.add(new AnalyzedConcept(conceptId, title, sourcePaths, snippets));
        }
        return analyzedConcepts;
    }

    /**
     * 归一化分组键为概念标识。
     *
     * @param groupKey 分组键
     * @return 概念标识
     */
    private String normalizeGroupKey(String groupKey) {
        return groupKey.trim().toLowerCase().replace('_', '-').replace(' ', '-');
    }

    /**
     * 把分组键转换为标题。
     *
     * @param groupKey 分组键
     * @return 标题
     */
    private String toTitle(String groupKey) {
        if (groupKey.isEmpty()) {
            return groupKey;
        }
        String normalized = groupKey.replace('-', ' ').replace('_', ' ');
        String[] words = normalized.split("\\s+");
        List<String> titledWords = new ArrayList<String>();
        for (String word : Arrays.asList(words)) {
            if (word.isEmpty()) {
                continue;
            }
            titledWords.add(Character.toUpperCase(word.charAt(0)) + word.substring(1));
        }
        return String.join(" ", titledWords);
    }
}
