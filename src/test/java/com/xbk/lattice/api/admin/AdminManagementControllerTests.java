package com.xbk.lattice.api.admin;

import com.xbk.lattice.compiler.service.CompileApplicationFacade;
import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.ContributionJdbcRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
 * AdminManagementController 测试
 *
 * 职责：验证 B8 管理侧可完成文章浏览、生命周期切换与 pending 管理
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
class AdminManagementControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CompileApplicationFacade compileApplicationFacade;

    @Autowired
    private ContributionJdbcRepository contributionJdbcRepository;

    @Autowired
    private ArticleJdbcRepository articleJdbcRepository;

    /**
     * 验证管理侧可浏览文章列表与详情。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldBrowseArticlesViaAdminApi(@TempDir Path tempDir) throws Exception {
        resetTables();
        prepareKnowledgeBase(tempDir);

        mockMvc.perform(get("/api/v1/admin/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].articleKey").value("legacy-default--payment-timeout"))
                .andExpect(jsonPath("$.items[0].conceptId").value("payment-timeout"))
                .andExpect(jsonPath("$.items[0].title").value("Payment Timeout"))
                .andExpect(jsonPath("$.items[0].sourceCount").value(1))
                .andExpect(jsonPath("$.items[0].primarySourcePath").value("payment/analyze.json"))
                .andExpect(jsonPath("$.items[0].primarySourceName").value("payment/analyze.json"))
                .andExpect(jsonPath("$.items[0].sourcePaths[0]").value("payment/analyze.json"))
                .andExpect(jsonPath("$.items[0].updatedAt").exists())
                .andExpect(jsonPath("$.items[0].lifecycle").value("ACTIVE"));

        mockMvc.perform(get("/api/v1/admin/articles/payment-timeout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articleKey").value("legacy-default--payment-timeout"))
                .andExpect(jsonPath("$.conceptId").value("payment-timeout"))
                .andExpect(jsonPath("$.summary").value("Handles payment timeout recovery"))
                .andExpect(jsonPath("$.sourceCount").value(1))
                .andExpect(jsonPath("$.primarySourcePath").value("payment/analyze.json"))
                .andExpect(jsonPath("$.updatedAt").exists())
                .andExpect(jsonPath("$.sourcePaths[0]").value("payment/analyze.json"));
    }

    /**
     * 验证管理侧列表支持来源文件搜索，并可返回多来源聚合条目。
     *
     * @throws Exception 测试异常
     */
    @Test
    void shouldSearchArticlesBySourcePathAndExposeTraceabilityFields() throws Exception {
        resetTables();
        prepareArticleFixtures();

        mockMvc.perform(get("/api/v1/admin/articles").queryParam("query", "payment_retry_policy.xlsx"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.items[0].articleKey").value("knowledge-schema--payments"))
                .andExpect(jsonPath("$.items[0].sourceCount").value(4))
                .andExpect(jsonPath("$.items[0].primarySourcePath").value("payments/PaymentRetryPolicy.java"))
                .andExpect(jsonPath("$.items[0].sourcePaths[1]").value("docs/payment/PaymentRetryPolicy.md"))
                .andExpect(jsonPath("$.items[0].sourcePaths[2]").value("excel/payment_retry_policy.xlsx"))
                .andExpect(jsonPath("$.items[0].sourcePaths[3]").value("pdf/payment-retry-policy.pdf"));

        mockMvc.perform(get("/api/v1/admin/articles").queryParam("query", "RetryHelper.java"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.items[0].articleKey").value("retry-helper"))
                .andExpect(jsonPath("$.items[0].sourceCount").value(1))
                .andExpect(jsonPath("$.items[0].primarySourcePath").value("src/main/java/com/xbk/lattice/RetryHelper.java"));

        mockMvc.perform(get("/api/v1/admin/articles").queryParam("query", "quick-start.md"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.items[0].articleKey").value("quick-start"))
                .andExpect(jsonPath("$.items[0].sourceCount").value(1))
                .andExpect(jsonPath("$.items[0].primarySourcePath").value("docs/quick-start.md"));

        mockMvc.perform(get("/api/v1/admin/articles/knowledge-schema--payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articleKey").value("knowledge-schema--payments"))
                .andExpect(jsonPath("$.sourceCount").value(4))
                .andExpect(jsonPath("$.primarySourcePath").value("payments/PaymentRetryPolicy.java"))
                .andExpect(jsonPath("$.sourcePaths[2]").value("excel/payment_retry_policy.xlsx"))
                .andExpect(jsonPath("$.sourcePaths[3]").value("pdf/payment-retry-policy.pdf"));
    }

    /**
     * 验证管理侧可切换文章生命周期。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldTransitionArticleLifecycleViaAdminApi(@TempDir Path tempDir) throws Exception {
        resetTables();
        prepareKnowledgeBase(tempDir);

        mockMvc.perform(post("/api/v1/admin/articles/payment-timeout/lifecycle/archive")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"duplicated\",\"updatedBy\":\"admin\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conceptId").value("payment-timeout"))
                .andExpect(jsonPath("$.lifecycle").value("archived"))
                .andExpect(jsonPath("$.updatedBy").value("admin"));

        mockMvc.perform(get("/api/v1/admin/articles/payment-timeout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lifecycle").value("archived"));
    }

    /**
     * 验证管理侧可管理 pending 查询。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldManagePendingQueriesViaAdminApi(@TempDir Path tempDir) throws Exception {
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

        mockMvc.perform(get("/api/v1/admin/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.items[0].queryId").value(queryId));

        mockMvc.perform(post("/api/v1/admin/pending/" + queryId + "/correct")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correction\":\"答案里请补充 interval=30s\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queryId").value(queryId));

        mockMvc.perform(post("/api/v1/admin/pending/" + queryId + "/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        assertThat(contributionJdbcRepository.findAll()).hasSize(1);
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

    /**
     * 重置测试表，避免不同用例之间相互污染。
     */
    private void resetTables() {
        jdbcTemplate.execute("TRUNCATE TABLE lattice.pending_queries");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.contributions");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.source_files CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.articles CASCADE");
    }

    /**
     * 准备内容列表理解性验证所需的多来源与单来源文章数据。
     */
    private void prepareArticleFixtures() {
        articleJdbcRepository.upsert(new ArticleRecord(
                null,
                "knowledge-schema--payments",
                "knowledge-schema--payments",
                "knowledge-schema--payments",
                "Payment retry policy body",
                "ACTIVE",
                OffsetDateTime.parse("2026-04-20T10:15:30+08:00"),
                List.of(
                        "payments/PaymentRetryPolicy.java",
                        "docs/payment/PaymentRetryPolicy.md",
                        "excel/payment_retry_policy.xlsx",
                        "pdf/payment-retry-policy.pdf"
                ),
                "{\"domain\":\"payments\"}",
                "",
                List.of("retry", "payment"),
                List.of(),
                List.of(),
                "medium",
                "pending"
        ));
        articleJdbcRepository.upsert(new ArticleRecord(
                null,
                "retry-helper",
                "retry-helper",
                "Retry Helper",
                "Retry helper body",
                "ACTIVE",
                OffsetDateTime.parse("2026-04-20T09:30:00+08:00"),
                List.of("src/main/java/com/xbk/lattice/RetryHelper.java"),
                "{\"domain\":\"payments\"}",
                "Handles retry helper logic",
                List.of("retry"),
                List.of(),
                List.of(),
                "high",
                "passed"
        ));
        articleJdbcRepository.upsert(new ArticleRecord(
                null,
                "quick-start",
                "quick-start",
                "Quick Start",
                "Quick start body",
                "ACTIVE",
                OffsetDateTime.parse("2026-04-20T08:45:00+08:00"),
                List.of("docs/quick-start.md"),
                "{\"domain\":\"guide\"}",
                "Quick start summary",
                List.of("guide"),
                List.of(),
                List.of(),
                "high",
                "passed"
        ));
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
}
