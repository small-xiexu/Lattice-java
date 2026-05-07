package com.xbk.lattice.admin.service;

import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 管理侧文章查询服务测试
 *
 * 职责：验证文章列表组合筛选能力
 *
 * @author xiexu
 */
class AdminArticleQueryServiceTests {

    /**
     * 验证复核状态可与关键字、生命周期、资料源组合过滤。
     */
    @Test
    void shouldFilterArticlesByReviewStatusWithExistingFilters() {
        AdminArticleQueryService queryService = new AdminArticleQueryService(new FakeArticleJdbcRepository(List.of(
                article(1L, "gateway-active-review", "Gateway Active", "ACTIVE", "needs_human_review"),
                article(1L, "gateway-active-passed", "Gateway Active", "ACTIVE", "passed"),
                article(2L, "gateway-active-other-source", "Gateway Active", "ACTIVE", "needs_human_review"),
                article(1L, "gateway-archived-review", "Gateway Archived", "ARCHIVED", "needs_human_review")
        )));

        List<ArticleRecord> articleRecords = queryService.list(
                "gateway",
                "active",
                Long.valueOf(1L),
                "needs_human_review"
        );

        assertThat(articleRecords)
                .extracting(ArticleRecord::getConceptId)
                .containsExactly("gateway-active-review");
    }

    /**
     * 验证空复核状态不会改变既有列表行为。
     */
    @Test
    void shouldIgnoreBlankReviewStatusFilter() {
        AdminArticleQueryService queryService = new AdminArticleQueryService(new FakeArticleJdbcRepository(List.of(
                article(1L, "knowledge-pending", "Knowledge Pending", "ACTIVE", "pending"),
                article(1L, "knowledge-passed", "Knowledge Passed", "ACTIVE", "passed")
        )));

        List<ArticleRecord> articleRecords = queryService.list("knowledge", "active", Long.valueOf(1L), " ");

        assertThat(articleRecords)
                .extracting(ArticleRecord::getConceptId)
                .containsExactly("knowledge-passed", "knowledge-pending");
    }

    /**
     * 验证风险维度可独立于复核状态组合过滤。
     */
    @Test
    void shouldFilterArticlesByGenericRiskDimensions() {
        AdminArticleQueryService queryService = new AdminArticleQueryService(new FakeArticleJdbcRepository(List.of(
                riskArticle("source-conflict", "ACTIVE", "passed", "high", List.of("source_conflict"), false, false),
                riskArticle("user-feedback-hotspot", "ACTIVE", "passed", "medium", List.of("user_reported"), true, true),
                riskArticle("low-risk", "ACTIVE", "needs_human_review", "low", List.of(), false, false)
        )));

        List<ArticleRecord> highRiskArticles = queryService.list(
                "",
                "active",
                null,
                "",
                "high",
                "",
                null,
                null
        );
        List<ArticleRecord> reportedHotspotArticles = queryService.list(
                "",
                "active",
                null,
                "passed",
                "",
                "user_reported",
                Boolean.TRUE,
                Boolean.TRUE
        );

        assertThat(highRiskArticles)
                .extracting(ArticleRecord::getConceptId)
                .containsExactly("source-conflict");
        assertThat(reportedHotspotArticles)
                .extracting(ArticleRecord::getConceptId)
                .containsExactly("user-feedback-hotspot");
    }

    /**
     * 构造文章记录。
     *
     * @param sourceId 资料源主键
     * @param conceptId 概念标识
     * @param title 标题
     * @param lifecycle 生命周期
     * @param reviewStatus 复核状态
     * @return 文章记录
     */
    private ArticleRecord article(
            Long sourceId,
            String conceptId,
            String title,
            String lifecycle,
            String reviewStatus
    ) {
        OffsetDateTime compiledAt = OffsetDateTime.parse("2026-05-05T10:00:00+08:00");
        return new ArticleRecord(
                sourceId,
                "article-key-" + conceptId,
                conceptId,
                title,
                "# " + title,
                lifecycle,
                compiledAt,
                List.of("docs/" + conceptId + ".md"),
                "{}",
                title + " summary",
                List.of(),
                List.of(),
                List.of(),
                "medium",
                reviewStatus
        );
    }

    /**
     * 构造带风险维度的文章记录。
     *
     * @param conceptId 概念标识
     * @param lifecycle 生命周期
     * @param reviewStatus 复核状态
     * @param riskLevel 风险等级
     * @param riskReasons 风险原因
     * @param hotspot 是否热点
     * @param requiresResultVerification 是否需要结果抽检
     * @return 文章记录
     */
    private ArticleRecord riskArticle(
            String conceptId,
            String lifecycle,
            String reviewStatus,
            String riskLevel,
            List<String> riskReasons,
            boolean hotspot,
            boolean requiresResultVerification
    ) {
        OffsetDateTime compiledAt = OffsetDateTime.parse("2026-05-05T10:00:00+08:00");
        return new ArticleRecord(
                1L,
                "article-key-" + conceptId,
                conceptId,
                "Risk " + conceptId,
                "# Risk " + conceptId,
                lifecycle,
                compiledAt,
                List.of("docs/" + conceptId + ".md"),
                "{}",
                "Risk summary",
                List.of(),
                List.of(),
                List.of(),
                "medium",
                reviewStatus,
                riskLevel,
                riskReasons,
                hotspot,
                requiresResultVerification
        );
    }

    /**
     * 固定文章列表仓储。
     *
     * 职责：为查询服务测试提供内存文章数据
     *
     * @author xiexu
     */
    private static class FakeArticleJdbcRepository extends ArticleJdbcRepository {

        private final List<ArticleRecord> articleRecords;

        /**
         * 创建固定文章列表仓储。
         *
         * @param articleRecords 文章记录列表
         */
        private FakeArticleJdbcRepository(List<ArticleRecord> articleRecords) {
            super(null);
            this.articleRecords = articleRecords;
        }

        /**
         * 查询全部文章。
         *
         * @return 文章记录列表
         */
        @Override
        public List<ArticleRecord> findAll() {
            return articleRecords;
        }
    }
}
