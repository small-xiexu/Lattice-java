package com.xbk.lattice.compiler.service;

import com.xbk.lattice.compiler.model.AnalyzedConcept;
import com.xbk.lattice.compiler.model.ConceptSection;
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
     * 验证合并节点会把分隔符不同的 conceptId 收敛为同一个概念，并选择信息量更高的标题。
     */
    @Test
    void shouldMergeConceptIdVariantsAndPreferRicherTitle() {
        CrossGroupMergeNode crossGroupMergeNode = new CrossGroupMergeNode();
        List<AnalyzedConcept> analyzedConcepts = Arrays.asList(
                new AnalyzedConcept("payment-service", "Payment", "", Arrays.asList("payment/a.md"), Arrays.asList("snippet-a")),
                new AnalyzedConcept(
                        "payment_service",
                        "Payment Service",
                        "Payment orchestration service",
                        Arrays.asList("payment/b.md"),
                        Arrays.asList("snippet-b"),
                        Arrays.asList(
                                new ConceptSection(
                                        "Core Flow",
                                        Arrays.asList("route-payment", "settle-order"),
                                        Arrays.asList("payment/b.md#core-flow")
                                )
                        )
                )
        );

        List<MergedConcept> mergedConcepts = crossGroupMergeNode.merge(analyzedConcepts);

        assertThat(mergedConcepts).hasSize(1);
        assertThat(mergedConcepts.get(0).getConceptId()).isEqualTo("payment-service");
        assertThat(mergedConcepts.get(0).getTitle()).isEqualTo("Payment Service");
        assertThat(mergedConcepts.get(0).getDescription()).isEqualTo("Payment orchestration service");
        assertThat(mergedConcepts.get(0).getSections()).containsExactly(
                new ConceptSection(
                        "Core Flow",
                        Arrays.asList("route-payment", "settle-order"),
                        Arrays.asList("payment/b.md#core-flow")
                )
        );
    }

    /**
     * 验证合并节点会稳定排序来源，并去掉空白或仅因首尾空格不同的重复片段。
     */
    @Test
    void shouldSortMergedSourcesAndDeduplicateNormalizedSnippets() {
        CrossGroupMergeNode crossGroupMergeNode = new CrossGroupMergeNode();
        List<AnalyzedConcept> analyzedConcepts = Arrays.asList(
                new AnalyzedConcept(
                        "payment",
                        "Payment",
                        "",
                        Arrays.asList("payment/b.md", "payment/a.md"),
                        Arrays.asList(" snippet-a ", "   "),
                        Arrays.asList(
                                new ConceptSection(
                                        " Rules ",
                                        Arrays.asList("retry=3", "  "),
                                        Arrays.asList("payment/a.md#rules")
                                )
                        )
                ),
                new AnalyzedConcept(
                        "payment",
                        "Payment",
                        "Payment domain summary",
                        Arrays.asList("payment/c.md", "payment/a.md"),
                        Arrays.asList("snippet-a", "snippet-c"),
                        Arrays.asList(
                                new ConceptSection(
                                        "Rules",
                                        Arrays.asList("retry=3", "interval=30s"),
                                        Arrays.asList("payment/c.md#rules", "payment/a.md#rules")
                                ),
                                new ConceptSection(
                                        "Fallback",
                                        Arrays.asList("manual-review"),
                                        Arrays.asList("payment/c.md#fallback")
                                )
                        )
                )
        );

        List<MergedConcept> mergedConcepts = crossGroupMergeNode.merge(analyzedConcepts);

        assertThat(mergedConcepts).hasSize(1);
        assertThat(mergedConcepts.get(0).getSourcePaths()).containsExactly("payment/a.md", "payment/b.md", "payment/c.md");
        assertThat(mergedConcepts.get(0).getSnippets()).containsExactly("snippet-a", "snippet-c");
        assertThat(mergedConcepts.get(0).getSections()).containsExactly(
                new ConceptSection(
                        "Rules",
                        Arrays.asList("retry=3", "interval=30s"),
                        Arrays.asList("payment/a.md#rules", "payment/c.md#rules")
                ),
                new ConceptSection(
                        "Fallback",
                        Arrays.asList("manual-review"),
                        Arrays.asList("payment/c.md#fallback")
                )
        );
    }

    /**
     * 验证合并节点会去重 sourcePaths 和 snippets。
     */
    @Test
    void shouldMergeConceptsAndDeduplicateSourcesAndSnippets() {
        CrossGroupMergeNode crossGroupMergeNode = new CrossGroupMergeNode();
        List<AnalyzedConcept> analyzedConcepts = Arrays.asList(
                new AnalyzedConcept("payment", "Payment", "", Arrays.asList("payment/a.md"), Arrays.asList("snippet-a")),
                new AnalyzedConcept("payment", "Payment", "", Arrays.asList("payment/a.md", "payment/b.md"), Arrays.asList("snippet-a", "snippet-b"))
        );

        List<MergedConcept> mergedConcepts = crossGroupMergeNode.merge(analyzedConcepts);

        assertThat(mergedConcepts).hasSize(1);
        assertThat(mergedConcepts.get(0).getSourcePaths()).containsExactly("payment/a.md", "payment/b.md");
        assertThat(mergedConcepts.get(0).getSnippets()).containsExactly("snippet-a", "snippet-b");
        assertThat(mergedConcepts.get(0).getSections()).isEmpty();
    }
}
