package com.xbk.lattice.compiler.service;

import com.xbk.lattice.compiler.model.AnalyzedConcept;
import com.xbk.lattice.compiler.model.RawSource;
import com.xbk.lattice.compiler.model.SourceBatch;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AnalyzeNode 测试
 *
 * 职责：验证最小分析节点输出稳定的标题、来源和摘要
 *
 * @author xiexu
 */
class AnalyzeNodeTests {

    /**
     * 验证分析节点会为每个批次生成概念，并保留来源与片段。
     */
    @Test
    void shouldAnalyzeBatchesIntoStableConcepts() {
        AnalyzeNode analyzeNode = new AnalyzeNode();
        List<SourceBatch> sourceBatches = Arrays.asList(
                new SourceBatch("batch-1", "payment-service", Arrays.asList(
                        RawSource.text("payment/a.md", "snippet-a", "md", 9L),
                        RawSource.text("payment/b.md", "snippet-b", "md", 9L)
                ))
        );

        List<AnalyzedConcept> analyzedConcepts = analyzeNode.analyze("payment-service", sourceBatches);

        assertThat(analyzedConcepts).hasSize(1);
        assertThat(analyzedConcepts.get(0).getConceptId()).isEqualTo("payment-service");
        assertThat(analyzedConcepts.get(0).getTitle()).isEqualTo("Payment Service");
        assertThat(analyzedConcepts.get(0).getSourcePaths()).containsExactly("payment/a.md", "payment/b.md");
        assertThat(analyzedConcepts.get(0).getSnippets()).containsExactly("snippet-a", "snippet-b");
    }
}
