package com.xbk.lattice.query.citation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CitationExtractor 测试
 *
 * 职责：验证只对最终结论段抽取 claim，不把问题/摘要等元信息混入核验
 *
 * @author xiexu
 */
class CitationExtractorTests {

    /**
     * 验证只对结论正文抽取 claim，不把问题/摘要/冲突提示混入核验。
     */
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
                        "库存扣减采用乐观锁版本号校验",
                        "补偿失败后会进入 retry_queue 异步重试"
                );
    }

    /**
     * 验证普通正文 block 会按句号/分号切成多个 claim，并跳过 footnote 行。
     */
    @Test
    void shouldSplitNarrativeBlockIntoSentenceLevelClaims() {
        CitationExtractor citationExtractor = new CitationExtractor();

        List<ClaimSegment> claimSegments = citationExtractor.extractClaims("""
                # 深度研究结论

                支付路由默认最多重试 5 次 [[payment-routing]]。超限后会写入 retry_queue [[retry-queue]]；
                最终由补偿任务异步处理。

                [^1]: 以上结论来自运行手册附录
                """);

        assertThat(claimSegments).hasSize(3);
        assertThat(claimSegments).extracting(ClaimSegment::getClaimText)
                .containsExactly(
                        "支付路由默认最多重试 5 次",
                        "超限后会写入 retry_queue",
                        "最终由补偿任务异步处理"
                );
        assertThat(claimSegments.get(0).getCitations()).hasSize(1);
        assertThat(claimSegments.get(1).getCitations()).hasSize(1);
        assertThat(claimSegments.get(2).getCitations()).isEmpty();
    }

    /**
     * 验证段末独立 citation 尾巴会回挂到同段前置 claim，而不是在切句后被静默丢弃。
     */
    @Test
    void shouldPropagateTrailingCitationTailToNarrativeClaims() {
        CitationExtractor citationExtractor = new CitationExtractor();

        List<ClaimSegment> claimSegments = citationExtractor.extractClaims("""
                `payment timeout retry` 是“支付超时重试”的配置。根据现有证据，它使用的配置键是 `payment.timeout.retry`，默认值是 `5`。[[readme]][→ README.md]
                """);

        assertThat(claimSegments).hasSize(2);
        assertThat(claimSegments.get(0).getCitations()).extracting(Citation::getLiteral)
                .containsExactly("[[readme]]", "[→ README.md]");
        assertThat(claimSegments.get(1).getCitations()).extracting(Citation::getLiteral)
                .containsExactly("[[readme]]", "[→ README.md]");
    }

    /**
     * 验证 SOURCE_FILE literal 携带行号时，targetKey 仍归一化为 relative path。
     */
    @Test
    void shouldNormalizeSourceFileLiteralWithLineRange() {
        CitationExtractor citationExtractor = new CitationExtractor();

        List<ClaimSegment> claimSegments = citationExtractor.extractClaims("""
                RoutePlanner 暴露了 /payments 路径 [→ src/main/java/payment/RoutePlanner.java:12-24]
                """);

        assertThat(claimSegments).hasSize(1);
        assertThat(claimSegments.get(0).getCitations()).hasSize(1);
        assertThat(claimSegments.get(0).getCitations().get(0).getTargetKey())
                .isEqualTo("src/main/java/payment/RoutePlanner.java");
    }

    /**
     * 验证误包成 article 形式的 SOURCE_FILE literal 仍会被归一化为标准 source-file 引用。
     */
    @Test
    void shouldNormalizeNestedSourceFileLiteralToCanonicalFormat() {
        CitationExtractor citationExtractor = new CitationExtractor();

        List<ClaimSegment> claimSegments = citationExtractor.extractClaims("""
                RoutePlanner 暴露了 /payments 路径 [[→ RoutePlanner.java]]
                """);

        assertThat(claimSegments).hasSize(1);
        assertThat(claimSegments.get(0).getCitations()).hasSize(1);
        assertThat(claimSegments.get(0).getCitations().get(0).getLiteral()).isEqualTo("[→ RoutePlanner.java]");
        assertThat(claimSegments.get(0).getCitations().get(0).getSourceType()).isEqualTo(CitationSourceType.SOURCE_FILE);
        assertThat(claimSegments.get(0).getCitations().get(0).getTargetKey()).isEqualTo("RoutePlanner.java");
    }

    /**
     * 验证代码块与 Markdown 表格不会被当作可核验 claim。
     */
    @Test
    void shouldSkipCodeBlocksAndTablesWhenExtractingClaims() {
        CitationExtractor citationExtractor = new CitationExtractor();

        List<ClaimSegment> claimSegments = citationExtractor.extractClaims("""
                # 深度研究结论

                | 字段 | 值 |
                | --- | --- |
                | maxAttempts | 5 |

                ```java
                class RoutePlanner {
                    void route() {}
                }
                ```

                ## 结论
                - PaymentService 默认最多重试 5 次 [[payment-routing]]
                """);

        assertThat(claimSegments).hasSize(1);
        assertThat(claimSegments.get(0).getClaimText()).isEqualTo("PaymentService 默认最多重试 5 次");
    }
}
