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
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
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
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge?currentSchema=lattice",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
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
                "select max(snapshot_id) from lattice.article_snapshots where concept_id = 'payment-timeout'",
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
     * 验证人工复核确认通过接口会联动文章状态、快照、审计和统计。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldExposeArticleManualReviewApproveApis(@TempDir Path tempDir) throws Exception {
        resetTables();
        prepareKnowledgeBase(tempDir);
        markArticleNeedsHumanReview("payment-timeout");

        mockMvc.perform(get("/api/v1/admin/articles").param("reviewStatus", "needs_human_review"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.items[0].conceptId").value("payment-timeout"))
                .andExpect(jsonPath("$.items[0].reviewStatus").value("needs_human_review"));

        mockMvc.perform(post("/api/v1/admin/articles/payment-timeout/review/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewedBy\":\"reviewer\",\"comment\":\"证据一致\",\"expectedReviewStatus\":\"needs_human_review\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conceptId").value("payment-timeout"))
                .andExpect(jsonPath("$.previousReviewStatus").value("needs_human_review"))
                .andExpect(jsonPath("$.reviewStatus").value("passed"))
                .andExpect(jsonPath("$.reviewedBy").value("reviewer"))
                .andExpect(jsonPath("$.auditId").isNumber());

        String content = jdbcTemplate.queryForObject(
                "select content from lattice.articles where concept_id = 'payment-timeout'",
                String.class
        );
        Integer snapshotCount = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from lattice.article_snapshots
                        where concept_id = 'payment-timeout'
                          and snapshot_reason = 'manual_review_approve'
                          and review_status = 'passed'
                        """,
                Integer.class
        );
        assertThat(content).contains("review_status: passed");
        assertThat(snapshotCount).isEqualTo(1);

        mockMvc.perform(get("/api/v1/admin/articles/payment-timeout/review/audits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.items[0].action").value("approve"))
                .andExpect(jsonPath("$.items[0].previousReviewStatus").value("needs_human_review"))
                .andExpect(jsonPath("$.items[0].nextReviewStatus").value("passed"));

        mockMvc.perform(get("/api/v1/admin/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.reviewPendingArticleCount").value(0))
                .andExpect(jsonPath("$.quality.needsHumanReviewArticles").value(0));

        mockMvc.perform(get("/api/v1/admin/quality").param("days", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.report.passedArticles").value(1))
                .andExpect(jsonPath("$.report.needsHumanReviewArticles").value(0));
    }

    /**
     * 验证人工复核提交修正接口会复用纠错链路并写入审计。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldExposeArticleManualReviewRequestChangesApi(@TempDir Path tempDir) throws Exception {
        resetTables();
        prepareKnowledgeBase(tempDir);
        markArticleNeedsHumanReview("payment-timeout");

        mockMvc.perform(post("/api/v1/admin/articles/payment-timeout/review/request-changes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewedBy\":\"reviewer\",\"comment\":\"需要补证据\",\"expectedReviewStatus\":\"needs_human_review\",\"correctionSummary\":\"补充来源说明\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conceptId").value("payment-timeout"))
                .andExpect(jsonPath("$.previousReviewStatus").value("needs_human_review"))
                .andExpect(jsonPath("$.reviewStatus").value("needs_review"))
                .andExpect(jsonPath("$.reviewedBy").value("reviewer"));

        mockMvc.perform(get("/api/v1/admin/articles/payment-timeout/review/audits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.items[0].action").value("request_changes"))
                .andExpect(jsonPath("$.items[0].previousReviewStatus").value("needs_human_review"))
                .andExpect(jsonPath("$.items[0].nextReviewStatus").value("needs_review"))
                .andExpect(jsonPath("$.items[0].metadataJson", containsString("\"validationSupported\"")));

        String reviewStatus = jdbcTemplate.queryForObject(
                "select review_status from lattice.articles where concept_id = 'payment-timeout'",
                String.class
        );
        assertThat(reviewStatus).isEqualTo("needs_review");
    }

    /**
     * 验证答案反馈接口会写入独立结果反馈队列并支持处理审计。
     *
     * @throws Exception 测试异常
     */
    @Test
    void shouldExposeQueryFeedbackApis() throws Exception {
        resetTables();

        String createResponseBody = mockMvc.perform(post("/api/v1/admin/query-feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "queryId": "query-feedback-1",
                                  "question": "接口用途是什么",
                                  "answerSummary": "答案混入不相关内容",
                                  "feedbackType": "answer_problem",
                                  "comment": "需要核对来源",
                                  "articleKeys": ["article-1"],
                                  "sourcePaths": ["docs/api.md"],
                                  "reportedBy": "tester"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queryId").value("query-feedback-1"))
                .andExpect(jsonPath("$.feedbackType").value("answer_problem"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.articleKeys[0]").value("article-1"))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        String feedbackId = extractJsonNumber(createResponseBody, "id");

        mockMvc.perform(get("/api/v1/admin/query-feedback").param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.items[0].queryId").value("query-feedback-1"));

        mockMvc.perform(get("/api/v1/admin/query-feedback/" + feedbackId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedback.queryId").value("query-feedback-1"))
                .andExpect(jsonPath("$.audits[0].action").value("CREATE"));

        mockMvc.perform(get("/api/v1/admin/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.answerFeedbackPendingCount").value(1));

        mockMvc.perform(post("/api/v1/admin/query-feedback/" + feedbackId + "/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"handledBy\":\"handler\",\"comment\":\"已补回归\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.handledBy").value("handler"))
                .andExpect(jsonPath("$.resolutionComment").value("已补回归"));

        mockMvc.perform(get("/api/v1/admin/query-feedback/" + feedbackId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.audits[0].action").value("RESOLVE"))
                .andExpect(jsonPath("$.audits[1].action").value("CREATE"));

        mockMvc.perform(get("/api/v1/admin/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.answerFeedbackPendingCount").value(0));
    }

    /**
     * 验证热点刷新接口会基于通用结果反馈信号生成待抽检队列。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldExposeArticleHotspotRefreshApi(@TempDir Path tempDir) throws Exception {
        resetTables();
        prepareKnowledgeBase(tempDir);
        String articleKey = jdbcTemplate.queryForObject(
                """
                        select article_key
                        from lattice.articles
                        where concept_id = 'payment-timeout'
                        """,
                String.class
        );
        jdbcTemplate.update(
                """
                        insert into lattice.pending_answer_feedback (
                            feedback_type, question, answer_summary, article_keys, source_paths, reported_by
                        )
                        values (?, ?, ?, ?, ?, ?)
                        """,
                "answer_problem",
                "generic question",
                "generic answer",
                new String[] {articleKey},
                new String[0],
                "tester"
        );

        mockMvc.perform(post("/api/v1/admin/articles/hotspots/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"heatScoreThreshold\":3,\"limit\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rebuiltStatsCount").value(1))
                .andExpect(jsonPath("$.hotspotCandidateCount").value(1))
                .andExpect(jsonPath("$.updatedArticleCount").value(1))
                .andExpect(jsonPath("$.heatScoreThreshold").value(3))
                .andExpect(jsonPath("$.candidates[0].articleKey").value(articleKey))
                .andExpect(jsonPath("$.candidates[0].answerFeedbackCount").value(1))
                .andExpect(jsonPath("$.candidates[0].heatScore").value(3));

        mockMvc.perform(get("/api/v1/admin/articles")
                        .param("isHotspot", "true")
                        .param("requiresResultVerification", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.items[0].conceptId").value("payment-timeout"))
                .andExpect(jsonPath("$.items[0].riskLevel").value("medium"))
                .andExpect(jsonPath("$.items[0].riskReasons[0]").value("hotspot_unverified"));

        mockMvc.perform(get("/api/v1/admin/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.hotspotPendingVerificationCount").value(1));
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
        jdbcTemplate.execute("TRUNCATE TABLE lattice.article_usage_stats CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.answer_feedback_audits RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.pending_answer_feedback RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.query_answer_citations RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.query_answer_claims RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.query_answer_audits RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.query_retrieval_channel_hits RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.query_retrieval_runs RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.article_review_audits RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.pending_queries");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.contributions");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.repo_snapshot_items");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.repo_snapshots RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.source_files CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.synthesis_artifacts");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.articles CASCADE");
    }

    /**
     * 将测试文章标记为需人工复核。
     *
     * @param conceptId 概念标识
     */
    private void markArticleNeedsHumanReview(String conceptId) {
        String content = jdbcTemplate.queryForObject(
                "select content from lattice.articles where concept_id = ?",
                String.class,
                conceptId
        );
        String normalizedContent = normalizeReviewStatusFrontmatter(content, "needs_human_review");
        jdbcTemplate.update(
                """
                        update lattice.articles
                        set review_status = ?,
                            content = ?,
                            updated_at = CURRENT_TIMESTAMP
                        where concept_id = ?
                        """,
                "needs_human_review",
                normalizedContent,
                conceptId
        );
    }

    /**
     * 归一测试文章 frontmatter 审查状态。
     *
     * @param content 文章正文
     * @param reviewStatus 审查状态
     * @return 归一后的正文
     */
    private String normalizeReviewStatusFrontmatter(String content, String reviewStatus) {
        if (content == null || content.isBlank()) {
            return """
                    ---
                    title: "Article"
                    review_status: %s
                    ---

                    # Article
                    """.formatted(reviewStatus);
        }
        String normalizedContent = content.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (normalizedContent.contains("review_status:")) {
            return normalizedContent.replaceFirst("(?m)^review_status:\\s*.*$", "review_status: " + reviewStatus);
        }
        if (normalizedContent.startsWith("---\n")) {
            return normalizedContent.replaceFirst("\\A---\\n", "---\nreview_status: " + reviewStatus + "\n");
        }
        return """
                ---
                title: "Article"
                review_status: %s
                ---

                %s
                """.formatted(reviewStatus, normalizedContent);
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

    /**
     * 从简单 JSON 文本中提取指定数字字段。
     *
     * @param json JSON 文本
     * @param fieldName 字段名
     * @return 数字文本
     */
    private String extractJsonNumber(String json, String fieldName) {
        String quotedField = "\"" + fieldName + "\":";
        int startIndex = json.indexOf(quotedField);
        if (startIndex < 0) {
            throw new IllegalStateException("field not found: " + fieldName);
        }
        int valueStartIndex = startIndex + quotedField.length();
        int valueEndIndex = valueStartIndex;
        while (valueEndIndex < json.length() && Character.isDigit(json.charAt(valueEndIndex))) {
            valueEndIndex++;
        }
        if (valueEndIndex == valueStartIndex) {
            throw new IllegalStateException("numeric field not found: " + fieldName);
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
        ArticleCorrectionService articleCorrectionService(JdbcTemplate jdbcTemplate) {
            return new ArticleCorrectionService(null, null, null, null, null) {
                @Override
                public ArticleCorrectionResult correct(String conceptId, String correctionSummary) {
                    return new ArticleCorrectionResult(conceptId, "# Corrected Article", List.of("order-timeout"), true);
                }

                @Override
                public ArticleCorrectionResult correct(String articleId, Long sourceId, String correctionSummary) {
                    List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                            """
                                    select content
                                    from lattice.articles
                                    where concept_id = ? or article_key = ?
                                    limit 1
                                    """,
                            articleId,
                            articleId
                    );
                    if (!rows.isEmpty()) {
                        Object content = rows.get(0).get("content");
                        jdbcTemplate.update(
                                """
                                        update lattice.articles
                                        set review_status = ?,
                                            content = ?,
                                            updated_at = CURRENT_TIMESTAMP
                                        where concept_id = ? or article_key = ?
                                        """,
                                "needs_review",
                                normalizeReviewStatusFrontmatter(String.valueOf(content), "needs_review"),
                                articleId,
                                articleId
                        );
                    }
                    return correct(articleId, correctionSummary);
                }

                private String normalizeReviewStatusFrontmatter(String content, String reviewStatus) {
                    if (content == null || content.isBlank()) {
                        return """
                                ---
                                title: "Article"
                                review_status: %s
                                ---

                                # Article
                                """.formatted(reviewStatus);
                    }
                    String normalizedContent = content.replace("\r\n", "\n").replace('\r', '\n').trim();
                    if (normalizedContent.contains("review_status:")) {
                        return normalizedContent.replaceFirst(
                                "(?m)^review_status:\\s*.*$",
                                "review_status: " + reviewStatus
                        );
                    }
                    if (normalizedContent.startsWith("---\n")) {
                        return normalizedContent.replaceFirst(
                                "\\A---\\n",
                                "---\nreview_status: " + reviewStatus + "\n"
                        );
                    }
                    return """
                            ---
                            title: "Article"
                            review_status: %s
                            ---

                            %s
                            """.formatted(reviewStatus, normalizedContent);
                }
            };
        }
    }
}
