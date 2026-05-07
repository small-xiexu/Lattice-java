package com.xbk.lattice.query.service;

import com.xbk.lattice.query.evidence.domain.AnswerShape;
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
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge?currentSchema=lattice",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.ai.openai.api-key=test-openai-key",
        "spring.ai.anthropic.api-key=test-anthropic-key"
})
class RetrievalAuditServiceTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RetrievalAuditService retrievalAuditService;

    /**
     * 验证 手动 DDL 已创建检索审计主表与通道明细表。
     */
    @Test
    void shouldCreateRetrievalAuditTablesByManualDdl() {
        Integer runTableCount = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'lattice' and table_name = 'query_retrieval_runs'",
                Integer.class
        );
        Integer hitTableCount = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'lattice' and table_name = 'query_retrieval_channel_hits'",
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
        jdbcTemplate.execute("TRUNCATE TABLE lattice.query_retrieval_runs CASCADE");

        Map<String, Double> channelWeights = new LinkedHashMap<String, Double>();
        channelWeights.put(RetrievalStrategyResolver.CHANNEL_FTS, 1.0D);
        channelWeights.put(RetrievalStrategyResolver.CHANNEL_GRAPH, 1.2D);
        channelWeights.put(RetrievalStrategyResolver.CHANNEL_FACT_CARD_VECTOR, 1.4D);
        channelWeights.put(RetrievalStrategyResolver.CHANNEL_ARTICLE_VECTOR, 1.0D);
        channelWeights.put(RetrievalStrategyResolver.CHANNEL_CHUNK_VECTOR, 1.35D);
        RetrievalStrategy retrievalStrategy = new RetrievalStrategy(
                "payment timeout retry policy",
                QueryIntent.ARCHITECTURE,
                AnswerShape.POLICY,
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
        QueryArticleHit factCardVectorHit = new QueryArticleHit(
                QueryEvidenceType.FACT_CARD,
                1L,
                "fc:payment-timeout",
                "fc:payment-timeout",
                "Payment Timeout Fact Card",
                "retry=3",
                """
                        {"factCardId":101,"cardId":"fc:payment-timeout","cardType":"FACT_POLICY",
                        "answerShape":"POLICY","confidence":0.91,"sourceChunkIds":[11,12]}
                        """,
                "valid",
                List.of(),
                10.5D
        );
        QueryArticleHit sourceChunkHit = new QueryArticleHit(
                QueryEvidenceType.SOURCE,
                1L,
                "source:timeout-policy#1",
                "docs/timeout-policy.md",
                "Timeout Policy Source",
                "retry=3 原文片段",
                "{\"chunkIndex\":1}",
                List.of("docs/timeout-policy.md"),
                10.0D
        );
        Map<String, List<QueryArticleHit>> channelHits = Map.of(
                RetrievalStrategyResolver.CHANNEL_FTS, List.of(ftsHit),
                RetrievalStrategyResolver.CHANNEL_GRAPH, List.of(graphHit),
                RetrievalStrategyResolver.CHANNEL_FACT_CARD_VECTOR, List.of(factCardVectorHit),
                RetrievalStrategyResolver.CHANNEL_SOURCE_CHUNK_FTS, List.of(sourceChunkHit)
        );
        List<QueryArticleHit> fusedHits = List.of(graphHit, factCardVectorHit, sourceChunkHit, ftsHit);

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
                "select count(*) from lattice.query_retrieval_runs",
                Integer.class
        );
        Integer hitCount = jdbcTemplate.queryForObject(
                "select count(*) from lattice.query_retrieval_channel_hits",
                Integer.class
        );
        String strategyTag = jdbcTemplate.queryForObject(
                "select strategy_tag from lattice.query_retrieval_runs where query_id = 'query-arch-001'",
                String.class
        );
        String questionTypeTag = jdbcTemplate.queryForObject(
                "select question_type_tag from lattice.query_retrieval_runs where query_id = 'query-arch-001'",
                String.class
        );
        Map<String, Object> runStats = jdbcTemplate.queryForMap(
                """
                        select answer_shape, fact_card_hit_count, source_chunk_hit_count, coverage_status
                        from lattice.query_retrieval_runs
                        where query_id = 'query-arch-001'
                        """
        );
        Integer fusedRank = jdbcTemplate.queryForObject(
                "select fused_rank from lattice.query_retrieval_channel_hits where channel_name = 'graph' and article_key = 'payment-timeout'",
                Integer.class
        );
        Integer sourcePathCount = jdbcTemplate.queryForObject(
                "select jsonb_array_length(source_paths_json) from lattice.query_retrieval_channel_hits where channel_name = 'graph' and article_key = 'payment-timeout'",
                Integer.class
        );
        Map<String, Object> factCardHitStats = jdbcTemplate.queryForMap(
                """
                        select fact_card_id, card_type, review_status, confidence::float8 as confidence
                        from lattice.query_retrieval_channel_hits
                        where channel_name = 'fact_card_vector'
                          and evidence_type = 'FACT_CARD'
                        """
        );
        Integer sourceChunkIdCount = jdbcTemplate.queryForObject(
                """
                        select jsonb_array_length(source_chunk_ids_json)
                        from lattice.query_retrieval_channel_hits
                        where channel_name = 'fact_card_vector'
                          and evidence_type = 'FACT_CARD'
                        """,
                Integer.class
        );
        Integer firstSourceChunkId = jdbcTemplate.queryForObject(
                """
                        select (source_chunk_ids_json ->> 0)::int
                        from lattice.query_retrieval_channel_hits
                        where channel_name = 'fact_card_vector'
                          and evidence_type = 'FACT_CARD'
                        """,
                Integer.class
        );

        assertThat(auditRef).startsWith("query_retrieval_runs:");
        assertThat(runCount).isEqualTo(1);
        assertThat(hitCount).isEqualTo(4);
        assertThat(strategyTag).contains("intent=ARCHITECTURE");
        assertThat(strategyTag).contains("shape=POLICY");
        assertThat(questionTypeTag).isEqualTo("ARCHITECTURE");
        assertThat(runStats).containsEntry("answer_shape", "POLICY");
        assertThat(runStats).containsEntry("fact_card_hit_count", 1);
        assertThat(runStats).containsEntry("source_chunk_hit_count", 1);
        assertThat(runStats).containsEntry("coverage_status", "covered");
        assertThat(strategyTag).contains("rewrite=on");
        assertThat(strategyTag).contains("vector=on");
        assertThat(fusedRank).isEqualTo(1);
        assertThat(sourcePathCount).isEqualTo(1);
        Integer factCardVectorCount = jdbcTemplate.queryForObject(
                "select count(*) from lattice.query_retrieval_channel_hits where channel_name = 'fact_card_vector' and evidence_type = 'FACT_CARD'",
                Integer.class
        );
        assertThat(factCardVectorCount).isEqualTo(1);
        assertThat(factCardHitStats).containsEntry("fact_card_id", 101L);
        assertThat(factCardHitStats).containsEntry("card_type", "FACT_POLICY");
        assertThat(factCardHitStats).containsEntry("review_status", "valid");
        assertThat((Double) factCardHitStats.get("confidence")).isCloseTo(0.91D, org.assertj.core.data.Offset.offset(0.001D));
        assertThat(sourceChunkIdCount).isEqualTo(2);
        assertThat(firstSourceChunkId).isEqualTo(11);
    }

    /**
     * 验证检索审计会保存通道运行摘要，便于识别失败通道。
     */
    @Test
    void shouldPersistChannelRunSummary() {
        jdbcTemplate.execute("TRUNCATE TABLE lattice.query_retrieval_runs CASCADE");

        Map<String, Double> channelWeights = new LinkedHashMap<String, Double>();
        channelWeights.put(RetrievalStrategyResolver.CHANNEL_FTS, 1.0D);
        channelWeights.put(RetrievalStrategyResolver.CHANNEL_FACT_CARD_VECTOR, 1.4D);
        RetrievalStrategy retrievalStrategy = new RetrievalStrategy(
                "provider timeout",
                QueryIntent.GENERAL,
                AnswerShape.GENERAL,
                false,
                60,
                channelWeights,
                new LinkedHashSet<String>(channelWeights.keySet())
        );
        QueryArticleHit ftsHit = new QueryArticleHit(
                "timeout-policy",
                "Timeout Policy",
                "timeout fallback",
                "{}",
                List.of("docs/timeout.md"),
                9.0D
        );
        Map<String, List<QueryArticleHit>> channelHits = new LinkedHashMap<String, List<QueryArticleHit>>();
        channelHits.put(RetrievalStrategyResolver.CHANNEL_FTS, List.of(ftsHit));
        channelHits.put(RetrievalStrategyResolver.CHANNEL_FACT_CARD_VECTOR, List.of());
        Map<String, RetrievalChannelRun> channelRuns = new LinkedHashMap<String, RetrievalChannelRun>();
        channelRuns.put(
                RetrievalStrategyResolver.CHANNEL_FTS,
                RetrievalChannelRun.success(RetrievalStrategyResolver.CHANNEL_FTS, 12L, 1)
        );
        channelRuns.put(
                RetrievalStrategyResolver.CHANNEL_FACT_CARD_VECTOR,
                RetrievalChannelRun.failed(
                        RetrievalStrategyResolver.CHANNEL_FACT_CARD_VECTOR,
                        30L,
                        "IllegalStateException: embedding provider timeout"
                )
        );

        retrievalAuditService.persist(
                "query-channel-runs-001",
                "provider timeout",
                "provider timeout",
                retrievalStrategy,
                "serial",
                false,
                "",
                "query-channel-runs-001:retrieval-strategy",
                channelHits,
                List.of(ftsHit),
                channelRuns
        );

        String vectorStatus = jdbcTemplate.queryForObject(
                """
                        select channel_run_summary_json -> 'fact_card_vector' ->> 'status'
                        from lattice.query_retrieval_runs
                        where query_id = 'query-channel-runs-001'
                        """,
                String.class
        );
        String vectorErrorSummary = jdbcTemplate.queryForObject(
                """
                        select channel_run_summary_json -> 'fact_card_vector' ->> 'errorSummary'
                        from lattice.query_retrieval_runs
                        where query_id = 'query-channel-runs-001'
                        """,
                String.class
        );
        Integer ftsHitCount = jdbcTemplate.queryForObject(
                """
                        select (channel_run_summary_json -> 'fts' ->> 'hitCount')::int
                        from lattice.query_retrieval_runs
                        where query_id = 'query-channel-runs-001'
                        """,
                Integer.class
        );

        assertThat(vectorStatus).isEqualTo("FAILED");
        assertThat(vectorErrorSummary).contains("embedding provider timeout");
        assertThat(ftsHitCount).isEqualTo(1);
    }
}
