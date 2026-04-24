package com.xbk.lattice.query.citation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CitationExtractor 测试
 *
 * 职责：验证只对最终结论段抽取 claim，不把问题/摘要等元信息混入核验
 */
class CitationExtractorTests {

    @Test
    void shouldOnlyExtractConclusionClaimsFromDeepResearchAnswer() {
        CitationExtractor citationExtractor = new CitationExtractor();

        List<ClaimSegment> claimSegments = citationExtractor.extractClaims("""
                # 深度研究结论

                ## 问题
                同时说明库存锁和补偿重试

                ## 结论
                - 库存扣减采用乐观锁版本号校验 [[inventory-lock]]
                - 补偿失败后会进入 retry_queue 异步重试 [[retry-queue]]

                ## 分层摘要
                - 第 1 层：分别梳理库存锁与补偿重试

                ## 冲突提示
                - 当前证据链中存在不同来源结论不一致的情况，建议结合原始文档进一步确认。
                """);

        assertThat(claimSegments).hasSize(2);
        assertThat(claimSegments).extracting(ClaimSegment::getClaimText)
                .containsExactly(
                        "- 库存扣减采用乐观锁版本号校验",
                        "- 补偿失败后会进入 retry_queue 异步重试"
                );
    }
}
