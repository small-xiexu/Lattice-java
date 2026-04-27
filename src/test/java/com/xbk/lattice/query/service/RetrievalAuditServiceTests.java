package com.xbk.lattice.query.service;

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
 * RetrievalAuditService 测试
 *
 * 职责：验证检索审计主表与通道命中明细可按一次检索完整落盘
 *
 * @author xiexu
 */
@SpringBootTest(properties = {
        "spring.profiles.active=jdbc",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge?currentSchema=lattice_b9_retrieval_audit_test",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.flyway.enabled=true",
        "spring.flyway.schemas=lattice_b9_retrieval_audit_test",
        "spring.flyway.default-schema=lattice_b9_retrieval_audit_test",
        "spring.ai.openai.api-key=test-openai-key",
        "spring.ai.anthropic.api-key=test-anthropic-key"
})
class RetrievalAuditServiceTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RetrievalAuditService retrievalAuditService;

    /**
     * 验证 Flyway 已创建检索审计主表与通道明细表。
     */
    @Test
    void shouldCreateRetrievalAuditTablesByFlyway() {
        Integer runTableCount = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'lattice_b9_retrieval_audit_test' and table_name = 'query_retrieval_runs'",
                Integer.class
        );
        Integer hitTableCount = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'lattice_b9_retrieval_audit_test' and table_name = 'query_retrieval_channel_hits'",
                Integer.class
        );

        assertThat(runTableCount).isEqualTo(1);
        assertThat(hitTableCount).isEqualTo(1);
    }

    /**
     * 验证检索审计会写入主记录、策略标签和通道命中明细。
     */
    @Test
    void shouldPersistRetrievalRunAndChannelHits() {
        jdbcTemplate.execute("TRUNCATE TABLE lattice_b9_retrieval_audit_test.query_retrieval_runs CASCADE");

        Map<String, Double> channelWeights = new LinkedHashMap<String, Double>();
        channelWeights.put(RetrievalStrategyResolver.CHANNEL_FTS, 1.0D);
        channelWeights.put(RetrievalStrategyResolver.CHANNEL_GRAPH, 1.2D);
        channelWeights.put(RetrievalStrategyResolver.CHANNEL_ARTICLE_VECTOR, 1.0D);
        channelWeights.put(RetrievalStrategyResolver.CHANNEL_CHUNK_VECTOR, 1.35D);
        RetrievalStrategy retrievalStrategy = new RetrievalStrategy(
                "payment timeout retry policy",
                QueryIntent.ARCHITECTURE,
                true,
                60,
                channelWeights,
                new LinkedHashSet<String>(channelWeights.keySet())
        );
        QueryArticleHit ftsHit = new QueryArticleHit(
                1L,
                "payment-timeout",
                "payment-timeout",
                "Payment Timeout",
                "retry=3",
                "{\"description\":\"timeout article\"}",
                List.of("docs/payment-timeout.md"),
                9.5D
        );
        QueryArticleHit graphHit = new QueryArticleHit(
                QueryEvidenceType.GRAPH,
                1L,
                "payment-timeout",
                "payment-timeout",
                "Payment Timeout",
                "实体=PaymentRetryService；CALLS->RetryExecutor",
                "{\"entity\":\"PaymentRetryService\"}",
                List.of("src/main/java/com/xbk/lattice/payment/PaymentRetryService.java"),
                11.0D
        );
        Map<String, List<QueryArticleHit>> channelHits = Map.of(
                RetrievalStrategyResolver.CHANNEL_FTS, List.of(ftsHit),
                RetrievalStrategyResolver.CHANNEL_GRAPH, List.of(graphHit)
        );
        List<QueryArticleHit> fusedHits = List.of(graphHit, ftsHit);

        String auditRef = retrievalAuditService.persist(
                "query-arch-001",
                "为什么支付超时要做重试编排",
                "为什么支付超时要做重试编排",
                retrievalStrategy,
                "parallel",
                true,
                "query_rewrite_audits:query-arch-001",
                "query-arch-001:retrieval-strategy",
                channelHits,
                fusedHits
        );

        Integer runCount = jdbcTemplate.queryForObject(
                "select count(*) from lattice_b9_retrieval_audit_test.query_retrieval_runs",
                Integer.class
        );
        Integer hitCount = jdbcTemplate.queryForObject(
                "select count(*) from lattice_b9_retrieval_audit_test.query_retrieval_channel_hits",
                Integer.class
        );
        String strategyTag = jdbcTemplate.queryForObject(
                "select strategy_tag from lattice_b9_retrieval_audit_test.query_retrieval_runs where query_id = 'query-arch-001'",
                String.class
        );
        Integer fusedRank = jdbcTemplate.queryForObject(
                "select fused_rank from lattice_b9_retrieval_audit_test.query_retrieval_channel_hits where channel_name = 'graph' and article_key = 'payment-timeout'",
                Integer.class
        );
        Integer sourcePathCount = jdbcTemplate.queryForObject(
                "select jsonb_array_length(source_paths_json) from lattice_b9_retrieval_audit_test.query_retrieval_channel_hits where channel_name = 'graph' and article_key = 'payment-timeout'",
                Integer.class
        );

        assertThat(auditRef).startsWith("query_retrieval_runs:");
        assertThat(runCount).isEqualTo(1);
        assertThat(hitCount).isEqualTo(2);
        assertThat(strategyTag).contains("intent=ARCHITECTURE");
        assertThat(strategyTag).contains("rewrite=on");
        assertThat(strategyTag).contains("vector=on");
        assertThat(fusedRank).isEqualTo(1);
        assertThat(sourcePathCount).isEqualTo(1);
    }
}
