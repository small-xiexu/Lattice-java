package com.xbk.lattice.compiler.service;

import com.xbk.lattice.compiler.model.AnalyzedConcept;
import com.xbk.lattice.compiler.model.MergedConcept;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CrossGroupMergeNode 测试
 *
 * 职责：验证同 conceptId 的来源和片段会去重合并
 *
 * @author xiexu
 */
class CrossGroupMergeNodeTests {

    /**
     * 验证合并节点会去重 sourcePaths 和 snippets。
     */
    @Test
    void shouldMergeConceptsAndDeduplicateSourcesAndSnippets() {
        CrossGroupMergeNode crossGroupMergeNode = new CrossGroupMergeNode();
        List<AnalyzedConcept> analyzedConcepts = Arrays.asList(
                new AnalyzedConcept("payment", "Payment", Arrays.asList("payment/a.md"), Arrays.asList("snippet-a")),
                new AnalyzedConcept("payment", "Payment", Arrays.asList("payment/a.md", "payment/b.md"), Arrays.asList("snippet-a", "snippet-b"))
        );

        List<MergedConcept> mergedConcepts = crossGroupMergeNode.merge(analyzedConcepts);

        assertThat(mergedConcepts).hasSize(1);
        assertThat(mergedConcepts.get(0).getSourcePaths()).containsExactly("payment/a.md", "payment/b.md");
        assertThat(mergedConcepts.get(0).getSnippets()).containsExactly("snippet-a", "snippet-b");
    }
}
