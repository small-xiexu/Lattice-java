package com.xbk.lattice.api.admin;

import com.xbk.lattice.query.evidence.domain.AnswerShape;
import com.xbk.lattice.query.service.QueryArticleHit;
import com.xbk.lattice.query.service.QueryEvidenceType;
import com.xbk.lattice.query.service.QueryIntent;
import com.xbk.lattice.query.service.RetrievalChannelRun;
import com.xbk.lattice.query.service.RetrievalAuditService;
import com.xbk.lattice.query.service.RetrievalStrategy;
import com.xbk.lattice.query.service.RetrievalStrategyResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AdminQueryRetrievalAuditController 测试
 *
 * 职责：验证管理侧可查看 retrieval audit 明细与 recent runs
 *
 * @author xiexu
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge?currentSchema=lattice",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.ai.openai.api-key=test-openai-key",
        "spring.ai.anthropic.api-key=test-anthropic-key",
        "lattice.query.cache.store=in-memory"
})
@AutoConfigureMockMvc
class AdminQueryRetrievalAuditControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RetrievalAuditService retrievalAuditService;

    /**
     * 验证管理侧可按 queryId 查看最新 run、历史摘要与通道命中。
     *
     * @throws Exception 测试异常
     */
    @Test
    void shouldExposeLatestAuditByQueryId() throws Exception {
        resetTables();
        persistAudit("query-api-001", true, "api-retrieval-v1");
        persistAudit("query-api-001", false, "api-retrieval-v2");

        mockMvc.perform(get("/api/v1/admin/query/retrieval/audits/latest")
                        .param("queryId", "query-api-001")
                        .param("historyLimit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queryId").value("query-api-001"))
                .andExpect(jsonPath("$.found").value(true))
                .andExpect(jsonPath("$.latestRun.retrievalQuestion").value("api-retrieval-v2"))
                .andExpect(jsonPath("$.latestRun.answerShape").value("GENERAL"))
                .andExpect(jsonPath("$.latestRun.coverageStatus").value("not_applicable"))
                .andExpect(jsonPath("$.latestRun.factCardHitCount").value(0))
                .andExpect(jsonPath("$.latestRun.sourceChunkHitCount").value(0))
                .andExpect(jsonPath("$.latestRun.channelRuns[0].channelName").value("fts"))
                .andExpect(jsonPath("$.latestRun.channelRuns[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.latestRun.channelRuns[0].zeroHit").value(false))
                .andExpect(jsonPath("$.latestRun.channelRuns[1].channelName").value("graph"))
                .andExpect(jsonPath("$.latestRun.channelRuns[1].status").value("SUCCESS"))
                .andExpect(jsonPath("$.historyCount").value(2))
                .andExpect(jsonPath("$.runHistory[0].retrievalQuestion").value("api-retrieval-v2"))
                .andExpect(jsonPath("$.runHistory[1].retrievalQuestion").value("api-retrieval-v1"))
                .andExpect(jsonPath("$.channelHitCount").value(3))
                .andExpect(jsonPath("$.channelHits[0].channelName").value("fts"))
                .andExpect(jsonPath("$.channelHits[1].includedInFused").value(false))
                .andExpect(jsonPath("$.channelHits[2].channelName").value("graph"));
    }

    /**
     * 验证管理侧可查看 recent retrieval audit runs。
     *
     * @throws Exception 测试异常
     */
    @Test
    void shouldExposeRecentAuditRuns() throws Exception {
        resetTables();
        persistAudit("query-api-a", true, "recent-a");
        persistAudit("query-api-b", true, "recent-b");

        mockMvc.perform(get("/api/v1/admin/query/retrieval/audits/recent")
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.items[0].queryId").value("query-api-b"))
                .andExpect(jsonPath("$.items[0].retrievalQuestion").value("recent-b"))
                .andExpect(jsonPath("$.items[0].coverageStatus").value("not_applicable"))
                .andExpect(jsonPath("$.items[0].channelRuns[0].channelName").value("fts"));
    }

    /**
     * 验证管理侧可按 queryId 查看 Fact Card 通道结构化明细。
     *
     * @throws Exception 测试异常
     */
    @Test
    void shouldExposeFactCardAuditFieldsByQueryId() throws Exception {
        resetTables();
        persistFactCardAudit("query-fact-card-api-001");

        mockMvc.perform(get("/api/v1/admin/query/retrieval/audits/latest")
                        .param("queryId", "query-fact-card-api-001")
                        .param("historyLimit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latestRun.answerShape").value("POLICY"))
                .andExpect(jsonPath("$.latestRun.coverageStatus").value("covered"))
                .andExpect(jsonPath("$.latestRun.factCardHitCount").value(1))
                .andExpect(jsonPath("$.latestRun.sourceChunkHitCount").value(1))
                .andExpect(jsonPath("$.channelHitCount").value(2))
                .andExpect(jsonPath("$.channelHits[0].channelName").value("fact_card_vector"))
                .andExpect(jsonPath("$.channelHits[0].factCardId").value(101))
                .andExpect(jsonPath("$.channelHits[0].cardType").value("FACT_POLICY"))
                .andExpect(jsonPath("$.channelHits[0].reviewStatus").value("valid"))
                .andExpect(jsonPath("$.channelHits[0].confidence").value(0.91D))
                .andExpect(jsonPath("$.channelHits[0].sourceChunkIdsJson").value("[11, 12]"))
                .andExpect(jsonPath("$.channelHits[1].channelName").value("source_chunk_fts"))
                .andExpect(jsonPath("$.channelHits[1].includedInFused").value(true));
    }

    /**
     * 验证管理侧可结构化展示 skipped、failed、timeout 和 zero-hit 通道运行状态。
     *
     * @throws Exception 测试异常
     */
    @Test
    void shouldExposeChannelRunStates() throws Exception {
        resetTables();
        persistAuditWithChannelRuns("query-channel-run-api-001");

        mockMvc.perform(get("/api/v1/admin/query/retrieval/audits/latest")
                        .param("queryId", "query-channel-run-api-001")
                        .param("historyLimit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latestRun.channelRuns[0].channelName").value("fts"))
                .andExpect(jsonPath("$.latestRun.channelRuns[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.latestRun.channelRuns[0].zeroHit").value(true))
                .andExpect(jsonPath("$.latestRun.channelRuns[1].channelName").value("graph"))
                .andExpect(jsonPath("$.latestRun.channelRuns[1].status").value("SKIPPED"))
                .andExpect(jsonPath("$.latestRun.channelRuns[1].skippedReason").value("channel_disabled"))
                .andExpect(jsonPath("$.latestRun.channelRuns[2].channelName").value("article_vector"))
                .andExpect(jsonPath("$.latestRun.channelRuns[2].status").value("TIMEOUT"))
                .andExpect(jsonPath("$.latestRun.channelRuns[2].timeout").value(true))
                .andExpect(jsonPath("$.latestRun.channelRuns[3].channelName").value("chunk_vector"))
                .andExpect(jsonPath("$.latestRun.channelRuns[3].status").value("FAILED"))
                .andExpect(jsonPath("$.latestRun.channelRuns[3].errorSummary").value("IllegalStateException: dimension mismatch"));
    }

    /**
     * 重置测试表。
     */
    private void resetTables() {
        jdbcTemplate.execute("TRUNCATE TABLE lattice.query_retrieval_runs CASCADE");
    }

    /**
     * 写入一条带 graph 与 FTS 命中的审计数据。
     *
     * @param queryId 查询标识
     * @param rewriteApplied 是否改写
     * @param retrievalQuestion 实际检索问题
     */
    private void persistAudit(String queryId, boolean rewriteApplied, String retrievalQuestion) {
        Map<String, Double> channelWeights = new LinkedHashMap<String, Double>();
        channelWeights.put(RetrievalStrategyResolver.CHANNEL_FTS, 1.0D);
        channelWeights.put(RetrievalStrategyResolver.CHANNEL_GRAPH, 1.2D);
        RetrievalStrategy retrievalStrategy = new RetrievalStrategy(
                retrievalQuestion,
                QueryIntent.ARCHITECTURE,
                true,
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
        channelHits.put(RetrievalStrategyResolver.CHANNEL_GRAPH, List.of(graphHit));

        retrievalAuditService.persist(
                queryId,
                "为什么支付超时要做重试编排",
                "为什么支付超时要做重试编排",
                retrievalStrategy,
                "parallel",
                rewriteApplied,
                rewriteApplied ? "query_rewrite_audits:" + queryId : "",
                "retrieval_strategy:" + queryId,
                channelHits,
                List.of(graphHit, ftsPrimaryHit),
                graphAndFtsChannelRuns()
        );
    }

    /**
     * 构建 Graph 与 FTS 通道运行摘要。
     *
     * @return 通道运行摘要
     */
    private Map<String, RetrievalChannelRun> graphAndFtsChannelRuns() {
        Map<String, RetrievalChannelRun> channelRuns = new LinkedHashMap<String, RetrievalChannelRun>();
        channelRuns.put(
                RetrievalStrategyResolver.CHANNEL_FTS,
                RetrievalChannelRun.success(RetrievalStrategyResolver.CHANNEL_FTS, 12L, 2)
        );
        channelRuns.put(
                RetrievalStrategyResolver.CHANNEL_GRAPH,
                RetrievalChannelRun.success(RetrievalStrategyResolver.CHANNEL_GRAPH, 7L, 1)
        );
        return channelRuns;
    }

    /**
     * 写入一条带多状态 channel run 的审计数据。
     *
     * @param queryId 查询标识
     */
    private void persistAuditWithChannelRuns(String queryId) {
        Map<String, Double> channelWeights = new LinkedHashMap<String, Double>();
        channelWeights.put(RetrievalStrategyResolver.CHANNEL_FTS, 1.0D);
        channelWeights.put(RetrievalStrategyResolver.CHANNEL_ARTICLE_VECTOR, 1.0D);
        channelWeights.put(RetrievalStrategyResolver.CHANNEL_CHUNK_VECTOR, 1.0D);
        channelWeights.put(RetrievalStrategyResolver.CHANNEL_GRAPH, 1.2D);
        RetrievalStrategy retrievalStrategy = new RetrievalStrategy(
                "通道运行状态怎么看",
                QueryIntent.GENERAL,
                true,
                60,
                channelWeights,
                new LinkedHashSet<String>(channelWeights.keySet())
        );
        Map<String, List<QueryArticleHit>> channelHits = new LinkedHashMap<String, List<QueryArticleHit>>();
        channelHits.put(RetrievalStrategyResolver.CHANNEL_FTS, List.of());
        channelHits.put(RetrievalStrategyResolver.CHANNEL_ARTICLE_VECTOR, List.of());
        channelHits.put(RetrievalStrategyResolver.CHANNEL_CHUNK_VECTOR, List.of());
        channelHits.put(RetrievalStrategyResolver.CHANNEL_GRAPH, List.of());
        Map<String, RetrievalChannelRun> channelRuns = new LinkedHashMap<String, RetrievalChannelRun>();
        channelRuns.put(
                RetrievalStrategyResolver.CHANNEL_FTS,
                RetrievalChannelRun.success(RetrievalStrategyResolver.CHANNEL_FTS, 6L, 0)
        );
        channelRuns.put(
                RetrievalStrategyResolver.CHANNEL_ARTICLE_VECTOR,
                RetrievalChannelRun.timeout(RetrievalStrategyResolver.CHANNEL_ARTICLE_VECTOR, 81L, 80L)
        );
        channelRuns.put(
                RetrievalStrategyResolver.CHANNEL_CHUNK_VECTOR,
                RetrievalChannelRun.failed(
                        RetrievalStrategyResolver.CHANNEL_CHUNK_VECTOR,
                        12L,
                        "IllegalStateException: dimension mismatch"
                )
        );
        channelRuns.put(
                RetrievalStrategyResolver.CHANNEL_GRAPH,
                RetrievalChannelRun.skipped(RetrievalStrategyResolver.CHANNEL_GRAPH, "channel_disabled")
        );

        retrievalAuditService.persist(
                queryId,
                "通道运行状态怎么看",
                "通道运行状态怎么看",
                retrievalStrategy,
                "parallel",
                false,
                "",
                "retrieval_strategy:" + queryId,
                channelHits,
                List.of(),
                channelRuns
        );
    }

    /**
     * 写入一条带 Fact Card 与 Source Chunk 命中的审计数据。
     *
     * @param queryId 查询标识
     */
    private void persistFactCardAudit(String queryId) {
        Map<String, Double> channelWeights = new LinkedHashMap<String, Double>();
        channelWeights.put(RetrievalStrategyResolver.CHANNEL_FACT_CARD_VECTOR, 1.4D);
        channelWeights.put(RetrievalStrategyResolver.CHANNEL_SOURCE_CHUNK_FTS, 1.3D);
        RetrievalStrategy retrievalStrategy = new RetrievalStrategy(
                "访问控制必须遵守哪些规则",
                QueryIntent.GENERAL,
                AnswerShape.POLICY,
                true,
                60,
                channelWeights,
                new LinkedHashSet<String>(channelWeights.keySet())
        );
        QueryArticleHit factCardHit = new QueryArticleHit(
                QueryEvidenceType.FACT_CARD,
                1L,
                "fc:access-control-policy",
                "fc:access-control-policy",
                "Access Control Policy",
                "必须启用审批并记录操作人",
                """
                        {"factCardId":101,"cardId":"fc:access-control-policy","cardType":"FACT_POLICY",
                        "answerShape":"POLICY","confidence":0.91,"sourceChunkIds":[11,12]}
                        """,
                "valid",
                List.of(),
                10.5D
        );
        QueryArticleHit sourceChunkHit = new QueryArticleHit(
                QueryEvidenceType.SOURCE,
                1L,
                "source:access-control#1",
                "docs/access-control.md",
                "Access Control Source",
                "必须启用审批并记录操作人，禁止绕过审计。",
                "{\"chunkIndex\":1}",
                List.of("docs/access-control.md"),
                10.0D
        );
        Map<String, List<QueryArticleHit>> channelHits = new LinkedHashMap<String, List<QueryArticleHit>>();
        channelHits.put(RetrievalStrategyResolver.CHANNEL_FACT_CARD_VECTOR, List.of(factCardHit));
        channelHits.put(RetrievalStrategyResolver.CHANNEL_SOURCE_CHUNK_FTS, List.of(sourceChunkHit));

        retrievalAuditService.persist(
                queryId,
                "访问控制必须遵守哪些规则",
                "访问控制必须遵守哪些规则",
                retrievalStrategy,
                "parallel",
                false,
                "",
                "retrieval_strategy:" + queryId,
                channelHits,
                List.of(factCardHit, sourceChunkHit)
        );
    }
}
