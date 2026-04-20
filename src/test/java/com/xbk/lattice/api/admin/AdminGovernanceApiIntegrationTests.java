package com.xbk.lattice.api.admin;

import com.xbk.lattice.compiler.service.CompileApplicationFacade;
import com.xbk.lattice.governance.ArticleCorrectionResult;
import com.xbk.lattice.governance.ArticleCorrectionService;
import com.xbk.lattice.governance.LintFixResult;
import com.xbk.lattice.governance.LintFixService;
import com.xbk.lattice.governance.LintReport;
import com.xbk.lattice.infra.persistence.ContributionJdbcRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AdminGovernanceApi 集成测试
 *
 * 职责：验证治理类 HTTP API 已补齐并可与现有编译/查询链路协同工作
 *
 * @author xiexu
 */
@SpringBootTest(properties = {
        "spring.profiles.active=jdbc",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge?currentSchema=lattice_b9_governance_api_test",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.flyway.enabled=true",
        "spring.flyway.schemas=lattice_b9_governance_api_test",
        "spring.flyway.default-schema=lattice_b9_governance_api_test",
        "spring.ai.openai.api-key=test-openai-key",
        "spring.ai.anthropic.api-key=test-anthropic-key",
        "lattice.query.cache.store=in-memory"
})
@AutoConfigureMockMvc
class AdminGovernanceApiIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CompileApplicationFacade compileApplicationFacade;

    @Autowired
    private ContributionJdbcRepository contributionJdbcRepository;

    /**
     * 验证搜索、质量、覆盖率与遗漏接口。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldExposeSearchAndGovernanceReadApis(@TempDir Path tempDir) throws Exception {
        resetTables();
        prepareKnowledgeBase(tempDir);

        mockMvc.perform(get("/api/v1/search")
                        .param("question", "retry=3")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").isNumber())
                .andExpect(jsonPath("$.items[0].conceptId").value("payment-timeout"));

        mockMvc.perform(get("/api/v1/admin/quality").param("days", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.report.totalArticles").value(1))
                .andExpect(jsonPath("$.trend.days").value(7));

        mockMvc.perform(get("/api/v1/admin/coverage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSourceFileCount").value(1))
                .andExpect(jsonPath("$.coveredSourceFileCount").value(1));

        mockMvc.perform(get("/api/v1/admin/omissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSourceFileCount").value(1))
                .andExpect(jsonPath("$.omittedSourceFileCount").value(0));
    }

    /**
     * 验证 lint 与 lint-fix 接口。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldExposeLintApis(@TempDir Path tempDir) throws Exception {
        resetTables();
        prepareKnowledgeBase(tempDir);

        mockMvc.perform(get("/api/v1/admin/lint"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkedDimensions").isArray())
                .andExpect(jsonPath("$.totalIssues").isNumber());

        mockMvc.perform(post("/api/v1/admin/lint/fix")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetIds\":[\"payment-timeout\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fixed").value(1))
                .andExpect(jsonPath("$.skipped").value(0));
    }

    /**
     * 验证 inspection 读取与答案导入接口。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldExposeInspectApis(@TempDir Path tempDir) throws Exception {
        resetTables();
        prepareKnowledgeBase(tempDir);

        String queryResponseBody = mockMvc.perform(post("/api/v1/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"payment timeout retry=3 是什么配置\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        String queryId = extractJsonValue(queryResponseBody, "queryId");

        mockMvc.perform(get("/api/v1/admin/inspect"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalQuestions").value(1))
                .andExpect(jsonPath("$.questions[0].id").value("pending:" + queryId));

        mockMvc.perform(post("/api/v1/admin/inspect/import-answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inspectionId\":\"pending:" + queryId + "\",\"finalAnswer\":\"retry=5\",\"confirmedBy\":\"admin\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importedCount").value(1));

        assertThat(contributionJdbcRepository.findAll()).hasSize(1);
    }

    /**
     * 验证文章快照列表与文章级回滚接口。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldExposeArticleSnapshotApis(@TempDir Path tempDir) throws Exception {
        resetTables();
        prepareKnowledgeBase(tempDir);
        Long snapshotId = jdbcTemplate.queryForObject(
                "select max(snapshot_id) from lattice_b9_governance_api_test.article_snapshots where concept_id = 'payment-timeout'",
                Long.class
        );

        mockMvc.perform(get("/api/v1/admin/snapshot/article").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").isNumber())
                .andExpect(jsonPath("$.items[0].conceptId").value("payment-timeout"));

        mockMvc.perform(get("/api/v1/admin/snapshot/article")
                        .param("conceptId", "payment-timeout")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conceptId").value("payment-timeout"))
                .andExpect(jsonPath("$.count").isNumber())
                .andExpect(jsonPath("$.items[0].conceptId").value("payment-timeout"));

        mockMvc.perform(post("/api/v1/admin/rollback/article")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conceptId\":\"payment-timeout\",\"snapshotId\":" + snapshotId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conceptId").value("payment-timeout"))
                .andExpect(jsonPath("$.restoredSnapshotId").value(snapshotId));
    }

    /**
     * 验证整库级 repo snapshot 历史接口。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldExposeRepoSnapshotHistoryApi(@TempDir Path tempDir) throws Exception {
        resetTables();
        prepareKnowledgeBase(tempDir);

        mockMvc.perform(get("/api/v1/admin/snapshot/repo").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.items[0].triggerEvent").value("compile.full.graph"));
    }

    /**
     * 验证单篇文章纠错接口。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldExposeArticleCorrectionApi(@TempDir Path tempDir) throws Exception {
        resetTables();
        prepareKnowledgeBase(tempDir);

        mockMvc.perform(post("/api/v1/admin/articles/payment-timeout/correct")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correctionSummary\":\"retry=5\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conceptId").value("payment-timeout"))
                .andExpect(jsonPath("$.downstreamIds[0]").value("order-timeout"))
                .andExpect(jsonPath("$.validationSupported").value(true));
    }

    /**
     * 验证 Vault 导出接口。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldExposeVaultExportApi(@TempDir Path tempDir) throws Exception {
        resetTables();
        prepareKnowledgeBase(tempDir);
        Path vaultDir = tempDir.resolve("vault");

        mockMvc.perform(post("/api/v1/admin/vault/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vaultDir\":\"" + escapeJson(vaultDir.toString()) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vaultDir").value(vaultDir.toString()))
                .andExpect(jsonPath("$.writtenFiles").isNumber());

        Path conceptFile = resolveSingleConceptFile(vaultDir);
        assertThat(Files.exists(conceptFile)).isTrue();
    }

    /**
     * 验证 Vault 回写接口。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldExposeVaultSyncApi(@TempDir Path tempDir) throws Exception {
        resetTables();
        prepareKnowledgeBase(tempDir);
        Path vaultDir = tempDir.resolve("vault");

        mockMvc.perform(post("/api/v1/admin/vault/export")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vaultDir\":\"" + escapeJson(vaultDir.toString()) + "\"}"))
                .andExpect(status().isOk());

        Path conceptFile = resolveSingleConceptFile(vaultDir);
        Files.writeString(
                conceptFile,
                """
                        ---
                        title: "Payment Timeout Updated"
                        summary: "updated summary"
                        ---

                        # Payment Timeout

                        retry=5
                        """,
                StandardCharsets.UTF_8
        );

        mockMvc.perform(post("/api/v1/admin/vault/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vaultDir\":\"" + escapeJson(vaultDir.toString()) + "\",\"force\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.syncedFiles").value(1))
                .andExpect(jsonPath("$.conflictCount").value(0));
    }

    /**
     * 准备最小知识库测试数据。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    private void prepareKnowledgeBase(Path tempDir) throws Exception {
        Path paymentDir = Files.createDirectories(tempDir.resolve("payment"));
        Files.writeString(
                paymentDir.resolve("analyze.json"),
                "{"
                        + "\"concepts\":["
                        + "{\"id\":\"payment-timeout\",\"title\":\"Payment Timeout\","
                        + "\"description\":\"Handles payment timeout recovery\","
                        + "\"snippets\":[\"retry=3\",\"interval=30s\"],"
                        + "\"sections\":["
                        + "{\"heading\":\"Timeout Rules\",\"content\":[\"retry=3\",\"interval=30s\"],\"sources\":[\"payment/analyze.json#timeout-rules\"]}"
                        + "]"
                        + "}"
                        + "]"
                        + "}",
                StandardCharsets.UTF_8
        );
        compileApplicationFacade.compile(tempDir, false, null);
    }

    private Path resolveSingleConceptFile(Path vaultDir) throws Exception {
        try (java.util.stream.Stream<Path> stream = Files.list(vaultDir.resolve("concepts"))) {
            return stream
                    .filter(path -> path.getFileName().toString().endsWith(".md"))
                    .findFirst()
                    .orElseThrow();
        }
    }

    /**
     * 重置测试表。
     */
    private void resetTables() {
        jdbcTemplate.execute("TRUNCATE TABLE lattice_b9_governance_api_test.pending_queries");
        jdbcTemplate.execute("TRUNCATE TABLE lattice_b9_governance_api_test.contributions");
        jdbcTemplate.execute("TRUNCATE TABLE lattice_b9_governance_api_test.repo_snapshot_items");
        jdbcTemplate.execute("TRUNCATE TABLE lattice_b9_governance_api_test.repo_snapshots RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE lattice_b9_governance_api_test.source_files CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE lattice_b9_governance_api_test.synthesis_artifacts");
        jdbcTemplate.execute("TRUNCATE TABLE lattice_b9_governance_api_test.articles CASCADE");
    }

    /**
     * 从简单 JSON 文本中提取指定字段值。
     *
     * @param json JSON 文本
     * @param fieldName 字段名
     * @return 字段值
     */
    private String extractJsonValue(String json, String fieldName) {
        String quotedField = "\"" + fieldName + "\":\"";
        int startIndex = json.indexOf(quotedField);
        if (startIndex < 0) {
            throw new IllegalStateException("field not found: " + fieldName);
        }
        int valueStartIndex = startIndex + quotedField.length();
        int valueEndIndex = json.indexOf('"', valueStartIndex);
        if (valueEndIndex < 0) {
            throw new IllegalStateException("field value not closed: " + fieldName);
        }
        return json.substring(valueStartIndex, valueEndIndex);
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\");
    }

    /**
     * 治理类接口测试替身配置。
     *
     * @author xiexu
     */
    @TestConfiguration
    static class GovernanceTestConfiguration {

        @Bean
        @Primary
        LintFixService lintFixService() {
            return new LintFixService(null, null, null) {
                @Override
                public LintFixResult fix(LintReport report) {
                    return new LintFixResult(1, 0, List.of());
                }

                @Override
                public LintFixResult fix(LintReport report, List<String> issueTargetIds) {
                    return new LintFixResult(1, 0, List.of());
                }
            };
        }

        @Bean
        @Primary
        ArticleCorrectionService articleCorrectionService() {
            return new ArticleCorrectionService(null, null, null, null, null) {
                @Override
                public ArticleCorrectionResult correct(String conceptId, String correctionSummary) {
                    return new ArticleCorrectionResult(
                            conceptId,
                            "# Corrected Article",
                            List.of("order-timeout"),
                            true
                    );
                }
            };
        }
    }
}
