package com.xbk.lattice.compiler.service;

import com.xbk.lattice.compiler.config.CompilerProperties;
import com.xbk.lattice.compiler.model.RawSource;
import com.xbk.lattice.compiler.model.SourceBatch;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BatchSplitNode 排序测试
 *
 * 职责：验证分批前会先按关键文件优先级排序
 *
 * @author xiexu
 */
class BatchSplitNodeRankingTests {

    /**
     * 验证 overview 会在普通文件之前进入首批。
     */
    @Test
    void shouldRankSourcesBeforeSplittingIntoBatches() {
        CompilerProperties compilerProperties = new CompilerProperties();
        compilerProperties.setBatchMaxChars(6);
        BatchSplitNode batchSplitNode = new BatchSplitNode(
                compilerProperties,
                new FileRankingService(compilerProperties)
        );

        List<SourceBatch> batches = batchSplitNode.split("knowledge-docs", List.of(
                RawSource.text("notes.md", "12345", "md", 5L),
                RawSource.text("overview.md", "abcde", "md", 5L),
                RawSource.text("details.md", "vwxyz", "md", 5L)
        ));

        assertThat(batches).hasSize(3);
        assertThat(batches.get(0).getSources())
                .extracting(RawSource::getRelativePath)
                .containsExactly("overview.md");
    }
}
