package com.xbk.lattice.compiler.service;

import com.xbk.lattice.compiler.config.CompilerProperties;
import com.xbk.lattice.compiler.domain.RawSource;
import com.xbk.lattice.compiler.domain.SourceBatch;
import com.xbk.lattice.compiler.node.BatchSplitNode;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BatchSplitNode 测试
 *
 * 职责：验证按字符数分批与批次标识稳定性
 *
 * @author xiexu
 */
class BatchSplitNodeTests {

    /**
     * 验证同组文件会按照字符上限切分为多个批次。
     */
    @Test
    void shouldSplitSourcesIntoStableBatchesByCharacterLimit() {
        CompilerProperties properties = new CompilerProperties();
        properties.setBatchMaxChars(10);

        BatchSplitNode batchSplitNode = new BatchSplitNode(properties);
        List<RawSource> rawSources = Arrays.asList(
                RawSource.text("docs/a.md", "12345", "md", 5L),
                RawSource.text("docs/b.md", "6789", "md", 4L),
                RawSource.text("docs/c.md", "abcde", "md", 5L)
        );

        List<SourceBatch> batches = batchSplitNode.split("knowledge-docs", rawSources);

        assertThat(batches).hasSize(2);
        assertThat(batches.get(0).getGroupKey()).isEqualTo("knowledge-docs");
        assertThat(batches.get(0).getSources()).hasSize(2);
        assertThat(batches.get(1).getSources()).hasSize(1);
        assertThat(batches.get(0).getBatchId()).isNotBlank();
        assertThat(batches.get(1).getBatchId()).isNotBlank();
        assertThat(batches.get(0).getBatchId()).isNotEqualTo(batches.get(1).getBatchId());
    }
}
