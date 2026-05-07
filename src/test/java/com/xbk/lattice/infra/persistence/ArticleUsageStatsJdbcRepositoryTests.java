package com.xbk.lattice.infra.persistence;

import com.xbk.lattice.governance.ArticleHotspotRefreshResult;
import com.xbk.lattice.governance.ArticleHotspotRefreshService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ArticleUsageStatsJdbcRepository 测试
 *
 * 职责：验证文章热度统计和热点待抽检队列生成能力
 *
 * @author xiexu
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge?currentSchema=lattice",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.ai.openai.api-key=test-openai-key",
        "spring.ai.anthropic.api-key=test-anthropic-key"
})
class ArticleUsageStatsJdbcRepositoryTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ArticleJdbcRepository articleJdbcRepository;

    @Autowired
    private ArticleUsageStatsJdbcRepository articleUsageStatsJdbcRepository;

    @Autowired
    private ArticleHotspotRefreshService articleHotspotRefreshService;

    /**
     * 验证 手动 DDL 已创建文章热度统计表。
     */
    @Test
    void shouldCreateArticleUsageStatsTableByManualDdl() {
        Integer tableCount = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from information_schema.tables
                        where table_schema = 'lattice'
                          and table_name = 'article_usage_stats'
                        """,
                Integer.class
        );

        assertThat(tableCount).isEqualTo(1);
    }

    /**
     * 验证热度统计会综合检索命中、答案引用、答案反馈和人工热点标记。
     */
    @Test
    void shouldRebuildStatsFromGenericSignals() {
        resetTables();
        prepareArticles();
        Long runId = insertRetrievalRun("query-hot-1");
        insertRetrievalHit(runId, "article-hot", "concept-hot", true);
        insertRetrievalHit(runId, "article-cold", "concept-cold", false);
        Long auditId = insertAnswerAudit("query-hot-1");
        insertAnswerCitation(auditId, 1, "article-hot", "VERIFIED");
        insertAnswerCitation(auditId, 2, "article-cold", "DEMOTED");
        insertAnswerFeedback("answer_problem", "article-hot", "docs/hot.md");
        insertAnswerFeedback("reliable", "article-cold", "docs/cold.md");

        int rebuiltCount = articleUsageStatsJdbcRepository.rebuildStats(1, 2, 3, 3);
        List<ArticleUsageStatsRecord> candidates = articleUsageStatsJdbcRepository.findHotspotCandidates(3, 10);

        assertThat(rebuiltCount).isEqualTo(2);
        assertThat(candidates).hasSize(1);
        ArticleUsageStatsRecord hotRecord = candidates.get(0);
        assertThat(hotRecord.getArticleKey()).isEqualTo("article-hot");
        assertThat(hotRecord.getRetrievalHitCount()).isEqualTo(1);
        assertThat(hotRecord.getCitationCount()).isEqualTo(1);
        assertThat(hotRecord.getAnswerFeedbackCount()).isEqualTo(1);
        assertThat(hotRecord.getManualMarkCount()).isZero();
        assertThat(hotRecord.getHeatScore()).isEqualTo(6);
    }

    /**
     * 验证热点刷新服务会把达到阈值的文章标记为热点待抽检。
     */
    @Test
    void shouldRefreshHotspotQueueAndMarkArticles() {
        resetTables();
        prepareArticles();
        insertAnswerFeedback("answer_problem", "article-hot", "docs/hot.md");

        ArticleHotspotRefreshResult result = articleHotspotRefreshService.refresh(3, 10);
        ArticleRecord hotArticle = articleJdbcRepository.findByArticleKey("article-hot").orElseThrow();
        ArticleRecord coldArticle = articleJdbcRepository.findByArticleKey("article-cold").orElseThrow();

        assertThat(result.getRebuiltStatsCount()).isEqualTo(2);
        assertThat(result.getHotspotCandidateCount()).isEqualTo(1);
        assertThat(result.getUpdatedArticleCount()).isEqualTo(1);
        assertThat(hotArticle.isHotspot()).isTrue();
        assertThat(hotArticle.isRequiresResultVerification()).isTrue();
        assertThat(hotArticle.getRiskLevel()).isEqualTo("medium");
        assertThat(hotArticle.getRiskReasons()).containsExactly("hotspot_unverified");
        assertThat(coldArticle.isHotspot()).isFalse();
    }

    /**
     * 重置测试表。
     */
    private void resetTables() {
        jdbcTemplate.execute("TRUNCATE TABLE lattice.article_usage_stats CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.answer_feedback_audits RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.pending_answer_feedback RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.query_answer_citations RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.query_answer_claims RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.query_answer_audits RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.query_retrieval_channel_hits RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.query_retrieval_runs RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.articles CASCADE");
    }

    /**
     * 准备测试文章。
     */
    private void prepareArticles() {
        articleJdbcRepository.upsert(buildArticle("article-hot", "concept-hot", "Hot Article", "docs/hot.md", false));
        articleJdbcRepository.upsert(buildArticle("article-cold", "concept-cold", "Cold Article", "docs/cold.md", false));
    }

    /**
     * 构造测试文章。
     *
     * @param articleKey 文章唯一键
     * @param conceptId 概念标识
     * @param title 标题
     * @param sourcePath 来源路径
     * @param hotspot 是否热点
     * @return 文章记录
     */
    private ArticleRecord buildArticle(
            String articleKey,
            String conceptId,
            String title,
            String sourcePath,
            boolean hotspot
    ) {
        return new ArticleRecord(
                null,
                articleKey,
                conceptId,
                title,
                "# " + title,
                "ACTIVE",
                OffsetDateTime.parse("2026-05-06T10:00:00+08:00"),
                Arrays.asList(sourcePath),
                "{}",
                title,
                Arrays.asList(conceptId),
                Arrays.<String>asList(),
                Arrays.<String>asList(),
                "high",
                "passed",
                "low",
                Arrays.<String>asList(),
                hotspot,
                false
        );
    }

    /**
     * 插入检索审计主记录。
     *
     * @param queryId 查询标识
     * @return run 主键
     */
    private Long insertRetrievalRun(String queryId) {
        return jdbcTemplate.queryForObject(
                """
                        insert into lattice.query_retrieval_runs (query_id, question)
                        values (?, ?)
                        returning run_id
                        """,
                Long.class,
                queryId,
                "generic question"
        );
    }

    /**
     * 插入检索命中。
     *
     * @param runId run 主键
     * @param articleKey 文章唯一键
     * @param conceptId 概念标识
     * @param includedInFused 是否进入融合结果
     */
    private void insertRetrievalHit(Long runId, String articleKey, String conceptId, boolean includedInFused) {
        jdbcTemplate.update(
                """
                        insert into lattice.query_retrieval_channel_hits (
                            run_id, channel_name, hit_rank, fused_rank, included_in_fused,
                            evidence_type, article_key, concept_id, title
                        )
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                runId,
                "fts",
                Integer.valueOf(1),
                includedInFused ? Integer.valueOf(1) : null,
                Boolean.valueOf(includedInFused),
                "ARTICLE",
                articleKey,
                conceptId,
                articleKey
        );
    }

    /**
     * 插入答案审计主记录。
     *
     * @param queryId 查询标识
     * @return audit 主键
     */
    private Long insertAnswerAudit(String queryId) {
        return jdbcTemplate.queryForObject(
                """
                        insert into lattice.query_answer_audits (
                            query_id, answer_version, question, answer_markdown, route_type
                        )
                        values (?, ?, ?, ?, ?)
                        returning audit_id
                        """,
                Long.class,
                queryId,
                Integer.valueOf(1),
                "generic question",
                "generic answer",
                "QUERY"
        );
    }

    /**
     * 插入答案引用。
     *
     * @param auditId audit 主键
     * @param ordinal 引用序号
     * @param targetKey 引用目标键
     * @param validationStatus 核验状态
     */
    private void insertAnswerCitation(Long auditId, int ordinal, String targetKey, String validationStatus) {
        jdbcTemplate.update(
                """
                        insert into lattice.query_answer_citations (
                            audit_id, citation_ordinal, citation_literal, source_type, target_key, validation_status
                        )
                        values (?, ?, ?, ?, ?, ?)
                        """,
                auditId,
                Integer.valueOf(ordinal),
                "[[" + targetKey + "]]",
                "ARTICLE",
                targetKey,
                validationStatus
        );
    }

    /**
     * 插入答案反馈。
     *
     * @param feedbackType 反馈类型
     * @param articleKey 文章唯一键
     * @param sourcePath 来源路径
     */
    private void insertAnswerFeedback(String feedbackType, String articleKey, String sourcePath) {
        jdbcTemplate.update(
                """
                        insert into lattice.pending_answer_feedback (
                            feedback_type, question, answer_summary, article_keys, source_paths, reported_by
                        )
                        values (?, ?, ?, ?, ?, ?)
                        """,
                feedbackType,
                "generic question",
                "generic answer",
                new String[] {articleKey},
                new String[] {sourcePath},
                "tester"
        );
    }
}
