package com.xbk.lattice.documentparse.extractor;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PaginatedTextCleaner 测试
 *
 * 职责：验证分页文档抽取后会通用清理重复页眉页脚
 *
 * @author xiexu
 */
class PaginatedTextCleanerTests {

    /**
     * 验证重复日期、URL 与页码行会被清理，但正文事实保留。
     */
    @Test
    void shouldRemoveRepeatedHeaderFooterAndKeepBusinessFacts() {
        PaginatedTextCleaner cleaner = new PaginatedTextCleaner();

        String cleanedText = cleaner.cleanAndJoin(List.of(
                """
                2026/4/29 11:04 Internal Migration Plan
                Scenario A keeps /api/v2/orders/add compatible.
                https://docs.example.test/pdf#print 1/4
                """,
                """
                2026/4/29 11:04 Internal Migration Plan
                Scenario B uses queue.order.created.
                https://docs.example.test/pdf#print 2/4
                """,
                """
                2026/4/29 11:04 Internal Migration Plan
                businessTypeCode=26 has traffic.
                https://docs.example.test/pdf#print 3/4
                """,
                """
                2026/4/29 11:04 Internal Migration Plan
                SubTypeCode 1301 means create and recharge.
                https://docs.example.test/pdf#print 4/4
                """
        ));

        assertThat(cleanedText).contains("=== Page: 1 ===");
        assertThat(cleanedText).contains("Scenario A keeps /api/v2/orders/add compatible.");
        assertThat(cleanedText).contains("businessTypeCode=26 has traffic.");
        assertThat(cleanedText).doesNotContain("2026/4/29 11:04 Internal Migration Plan");
        assertThat(cleanedText).doesNotContain("pdf#print");
    }
}
