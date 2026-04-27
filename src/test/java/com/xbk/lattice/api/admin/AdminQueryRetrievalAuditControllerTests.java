package com.xbk.lattice.api.admin;

import com.xbk.lattice.query.service.QueryArticleHit;
import com.xbk.lattice.query.service.QueryEvidenceType;
import com.xbk.lattice.query.service.QueryIntent;
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
        "spring.profiles.active=jdbc",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge?currentSchema=lattice_b9_retrieval_audit_api_test",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.flyway.enabled=true",
        "spring.flyway.schemas=lattice_b9_retrieval_audit_api_test",
        "spring.flyway.default-schema=lattice_b9_retrieval_audit_api_test",
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
                .andExpect(jsonPath("$.items[0].retrievalQuestion").value("recent-b"));
    }

    /**
     * 重置测试表。
     */
    private void resetTables() {
        jdbcTemplate.execute("TRUNCATE TABLE lattice_b9_retrieval_audit_api_test.query_retrieval_runs CASCADE");
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
                List.of(graphHit, ftsPrimaryHit)
        );
    }
}
