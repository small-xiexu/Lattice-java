package com.xbk.lattice.query.service;

import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.query.service.mapper.ArticleFtsSearchMapper;
import com.xbk.lattice.query.service.mapper.RefKeySearchMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文章查询可见性 Mapper 测试
 *
 * 职责：验证 article-backed FTS 与 RefKey 查询只返回 passed/ACTIVE 文章
 *
 * @author xiexu
 */
@SpringBootTest(properties = {
        "spring.ai.openai.api-key=test-openai-key",
        "spring.ai.anthropic.api-key=test-anthropic-key",
        "lattice.llm.deep-research-startup-validation-enabled=false"
})
class ArticleVisibilitySearchMapperTests {

    private static final String MATCH_TOKEN = "visibilitytoken";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ArticleJdbcRepository articleJdbcRepository;

    @Autowired
    private ArticleFtsSearchMapper articleFtsSearchMapper;

    @Autowired
    private RefKeySearchMapper refKeySearchMapper;

    /**
     * 验证 Article FTS 只返回 passed/ACTIVE 文章。
     */
    @Test
    void shouldFilterArticleFtsByReviewStatusAndLifecycle() {
        resetArticles();
        seedVisibilityArticles();

        List<QueryArticleHit> hits = articleFtsSearchMapper.search(
                "simple",
                MATCH_TOKEN,
                10
        );

        assertOnlyPassedActiveVisible(hits);
    }

    /**
     * 验证 RefKey 原有 OR 条件不会绕过 hard filter。
     */
    @Test
    void shouldFilterRefKeySearchByReviewStatusAndLifecycle() {
        resetArticles();
        seedVisibilityArticles();

        List<QueryArticleHit> hits = refKeySearchMapper.search(
                List.of("%" + MATCH_TOKEN + "%"),
                10
        );

        assertOnlyPassedActiveVisible(hits);
    }

    /**
     * 清理文章表。
     */
    private void resetArticles() {
        jdbcTemplate.execute("TRUNCATE TABLE lattice.articles CASCADE");
    }

    /**
     * 写入同一检索 token 下的正例与负例文章。
     */
    private void seedVisibilityArticles() {
        seedArticle("visibility-passed-active", "passed", "ACTIVE");
        seedArticle("visibility-pending-active", "pending", "ACTIVE");
        seedArticle("visibility-needs-human-review-active", "needs_human_review", "ACTIVE");
        seedArticle("visibility-rejected-active", "rejected", "ACTIVE");
        seedArticle("visibility-passed-archived", "passed", "ARCHIVED");
    }

    /**
     * 写入指定审查状态与生命周期的文章。
     *
     * @param articleKey 文章唯一键
     * @param reviewStatus 审查状态
     * @param lifecycle 生命周期
     */
    private void seedArticle(String articleKey, String reviewStatus, String lifecycle) {
        articleJdbcRepository.upsert(new ArticleRecord(
                null,
                articleKey,
                articleKey,
                "Visibility " + articleKey,
                "Visibility searchable content " + MATCH_TOKEN,
                lifecycle,
                OffsetDateTime.now(),
                List.of("visibility/" + articleKey + ".md"),
                "{\"visibility\":\"" + articleKey + "\"}",
                "Visibility summary " + MATCH_TOKEN,
                List.of(MATCH_TOKEN),
                List.of(),
                List.of(),
                "medium",
                reviewStatus
        ));
    }

    /**
     * 断言查询结果只包含 passed/ACTIVE 正例。
     *
     * @param hits 查询命中
     */
    private void assertOnlyPassedActiveVisible(List<QueryArticleHit> hits) {
        assertThat(hits).isNotEmpty();
        assertThat(hits)
                .extracting(QueryArticleHit::getArticleKey)
                .containsExactly("visibility-passed-active");
        assertThat(hits.get(0).getReviewStatus()).isEqualTo("passed");
    }
}
