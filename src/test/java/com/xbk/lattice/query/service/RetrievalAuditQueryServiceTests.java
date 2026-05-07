package com.xbk.lattice.query.service;

import com.xbk.lattice.query.service.RetrievalAuditSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RetrievalAuditQueryService 测试
 *
 * 职责：验证 retrieval audit 可按 queryId 与 recent runs 读取
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
class RetrievalAuditQueryServiceTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RetrievalAuditService retrievalAuditService;

    @Autowired
    private RetrievalAuditQueryService retrievalAuditQueryService;

    /**
     * 验证可按 queryId 读取最新 run、历史摘要与通道命中。
     */
    @Test
    void shouldLoadLatestSnapshotByQueryId() {
        resetTables();
        persistAudit("query-compare-001", "为什么支付超时要做重试编排", false, false, "timeout-policy-v1");
        persistAudit("query-compare-001", "为什么支付超时要做重试编排", true, true, "timeout-policy-v2");

        RetrievalAuditSnapshot snapshot = retrievalAuditQueryService.getLatestSnapshot("query-compare-001", 5);

        assertThat(snapshot.isFound()).isTrue();
        assertThat(snapshot.getLatestRun()).isNotNull();
        assertThat(snapshot.getLatestRun().getRetrievalQuestion()).isEqualTo("timeout-policy-v2");
        assertThat(snapshot.getLatestRun().isRewriteApplied()).isTrue();
        assertThat(snapshot.getLatestRun().getAnswerShape()).isEqualTo("GENERAL");
        assertThat(snapshot.getLatestRun().getCoverageStatus()).isEqualTo("not_applicable");
        assertThat(snapshot.getLatestRun().getFactCardHitCount()).isZero();
        assertThat(snapshot.getLatestRun().getSourceChunkHitCount()).isZero();
        assertThat(snapshot.getRunHistory()).hasSize(2);
        assertThat(snapshot.getRunHistory().get(0).getRetrievalQuestion()).isEqualTo("timeout-policy-v2");
        assertThat(snapshot.getRunHistory().get(1).getRetrievalQuestion()).isEqualTo("timeout-policy-v1");
        assertThat(snapshot.getChannelHits()).hasSize(3);
        assertThat(snapshot.getChannelHits())
                .anyMatch(channelHitView -> !channelHitView.isIncludedInFused() && "timeout-fallback".equals(channelHitView.getArticleKey()));
    }

    /**
     * 验证 recent runs 会按最近时间倒序返回。
     */
    @Test
    void shouldListRecentRunsInReverseChronologicalOrder() {
        resetTables();
        persistAudit("query-audit-a", "第一个问题", false, false, "audit-a");
        persistAudit("query-audit-b", "第二个问题", true, true, "audit-b");

        List<com.xbk.lattice.infra.persistence.QueryRetrievalRunView> recentRuns = retrievalAuditQueryService.listRecentRuns(10);

        assertThat(recentRuns).hasSize(2);
        assertThat(recentRuns.get(0).getQueryId()).isEqualTo("query-audit-b");
        assertThat(recentRuns.get(1).getQueryId()).isEqualTo("query-audit-a");
    }

    /**
     * 验证查询不存在的 queryId 时返回空快照。
     */
    @Test
    void shouldReturnEmptySnapshotWhenQueryIdDoesNotExist() {
        resetTables();

        RetrievalAuditSnapshot snapshot = retrievalAuditQueryService.getLatestSnapshot("missing-query", 5);

        assertThat(snapshot.isFound()).isFalse();
        assertThat(snapshot.getLatestRun()).isNull();
        assertThat(snapshot.getRunHistory()).isEmpty();
        assertThat(snapshot.getChannelHits()).isEmpty();
    }

    /**
     * 重置测试表。
     */
    private void resetTables() {
        jdbcTemplate.execute("TRUNCATE TABLE lattice.query_retrieval_runs CASCADE");
    }

    /**
     * 写入一条带命中明细的审计数据。
     *
     * @param queryId 查询标识
     * @param question 问题
     * @param rewriteApplied 是否改写
     * @param graphEnabled 是否启用 graph
     * @param retrievalQuestion 实际检索问题
     */
    private void persistAudit(
            String queryId,
            String question,
            boolean rewriteApplied,
            boolean graphEnabled,
            String retrievalQuestion
    ) {
        Map<String, Double> channelWeights = new LinkedHashMap<String, Double>();
        channelWeights.put(RetrievalStrategyResolver.CHANNEL_FTS, 1.0D);
        if (graphEnabled) {
            channelWeights.put(RetrievalStrategyResolver.CHANNEL_GRAPH, 1.2D);
        }
        RetrievalStrategy retrievalStrategy = new RetrievalStrategy(
                retrievalQuestion,
                QueryIntent.ARCHITECTURE,
                graphEnabled,
                60,
                channelWeights,
                new LinkedHashSet<String>(channelWeights.keySet())
        );

        QueryArticleHit ftsPrimaryHit = new QueryArticleHit(
                1L,
                "timeout-policy",
                "timeout-policy",
                "Timeout Policy",
                "primary fts",
                "{\"channel\":\"fts\"}",
                List.of("docs/timeout-policy.md"),
                9.2D
        );
        QueryArticleHit ftsFallbackHit = new QueryArticleHit(
                2L,
                "timeout-fallback",
                "timeout-fallback",
                "Timeout Fallback",
                "fallback fts",
                "{\"channel\":\"fts\"}",
                List.of("docs/timeout-fallback.md"),
                8.1D
        );
        QueryArticleHit graphHit = new QueryArticleHit(
                QueryEvidenceType.GRAPH,
                1L,
                "timeout-policy",
                "timeout-policy",
                "Timeout Policy",
                "graph evidence",
                "{\"channel\":\"graph\"}",
                List.of("src/main/java/com/xbk/lattice/payment/PaymentRetryService.java"),
                11.0D
        );

        Map<String, List<QueryArticleHit>> channelHits = new LinkedHashMap<String, List<QueryArticleHit>>();
        channelHits.put(RetrievalStrategyResolver.CHANNEL_FTS, List.of(ftsPrimaryHit, ftsFallbackHit));
        if (graphEnabled) {
            channelHits.put(RetrievalStrategyResolver.CHANNEL_GRAPH, List.of(graphHit));
        }

        List<QueryArticleHit> fusedHits = graphEnabled
                ? List.of(graphHit, ftsPrimaryHit)
                : List.of(ftsPrimaryHit);
        retrievalAuditService.persist(
                queryId,
                question,
                question,
                retrievalStrategy,
                graphEnabled ? "parallel" : "serial",
                rewriteApplied,
                rewriteApplied ? "query_rewrite_audits:" + queryId : "",
                "retrieval_strategy:" + queryId,
                channelHits,
                fusedHits
        );
    }
}
