package com.xbk.lattice.compiler.service;

import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.CompileJobStepJdbcRepository;
import com.xbk.lattice.infra.persistence.CompileJobStepRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * StateGraphCompileOrchestrator 测试
 *
 * 职责：验证 StateGraph 模式可执行 full / incremental compile
 *
 * @author xiexu
 */
@SpringBootTest(properties = {
        "spring.profiles.active=jdbc",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge?currentSchema=lattice_b8_state_graph_test",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.flyway.enabled=true",
        "spring.flyway.schemas=lattice_b8_state_graph_test",
        "spring.flyway.default-schema=lattice_b8_state_graph_test",
        "spring.ai.openai.api-key=test-openai-key",
        "spring.ai.anthropic.api-key=test-anthropic-key"
})
class StateGraphCompileOrchestratorTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StateGraphCompileOrchestrator stateGraphCompileOrchestrator;

    @Autowired
    private ArticleJdbcRepository articleJdbcRepository;

    @Autowired
    private CompileJobStepJdbcRepository compileJobStepJdbcRepository;

    /**
     * 验证 StateGraph 模式可完成 full compile 与 incremental compile。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldRunFullAndIncrementalCompileViaStateGraph(@TempDir Path tempDir) throws Exception {
        resetTables();

        Path baselineRoot = Files.createDirectories(tempDir.resolve("baseline"));
        Path baselinePaymentDir = Files.createDirectories(baselineRoot.resolve("payment"));
        Files.writeString(
                baselinePaymentDir.resolve("base.json"),
                "{"
                        + "\"concepts\":["
                        + "{\"id\":\"payment-timeout\",\"title\":\"Payment Timeout\",\"description\":\"现有支付超时规则\","
                        + "\"snippets\":[\"retry=3\"],"
                        + "\"sections\":[{\"heading\":\"Timeout Rules\",\"content\":[\"retry=3\"],\"sources\":[\"payment/base.json#timeout-rules\"]}]}"
                        + "]"
                        + "}",
                StandardCharsets.UTF_8
        );

        CompileResult baselineResult = stateGraphCompileOrchestrator.execute(baselineRoot, false);

        Path incrementalRoot = Files.createDirectories(tempDir.resolve("incremental"));
        Path incrementalPaymentDir = Files.createDirectories(incrementalRoot.resolve("payment"));
        Files.writeString(
                incrementalPaymentDir.resolve("update.json"),
                "{"
                        + "\"concepts\":["
                        + "{\"id\":\"payment-timeout\",\"title\":\"Payment Timeout\",\"description\":\"补充支付超时补偿策略\","
                        + "\"snippets\":[\"manual-review\"],"
                        + "\"sections\":[{\"heading\":\"Compensation\",\"content\":[\"manual-review\",\"retry=5\"],\"sources\":[\"payment/update.json#compensation\"]}]}"
                        + "]"
                        + "}",
                StandardCharsets.UTF_8
        );

        CompileResult incrementalResult = stateGraphCompileOrchestrator.execute(incrementalRoot, true);
        ArticleRecord articleRecord = articleJdbcRepository.findByConceptId("payment-timeout").orElseThrow();

        assertThat(baselineResult.getPersistedCount()).isEqualTo(1);
        assertThat(incrementalResult.getPersistedCount()).isEqualTo(1);
        assertThat(articleRecord.getSourcePaths()).contains("payment/base.json", "payment/update.json");
        assertThat(articleRecord.getContent()).contains("manual-review");
        assertThat(articleRecord.getContent()).contains("payment/update.json");
        assertStepLogs(baselineResult.getJobId());
        assertStepLogs(incrementalResult.getJobId());
    }

    /**
     * 验证 StateGraph 增量编译只刷新直命中文章及其下游文章。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldOnlyRefreshDirectAndDownstreamArticlesViaStateGraph(@TempDir Path tempDir) throws Exception {
        resetTables();

        articleJdbcRepository.upsert(createExistingArticle(
                "payment-timeout",
                "Payment Timeout",
                List.of("payment/base.json"),
                List.of(),
                "现有支付超时规则",
                """
                        # Payment Timeout

                        现有支付超时规则
                        """
        ));
        articleJdbcRepository.upsert(createExistingArticle(
                "payment-overview",
                "Payment Overview",
                List.of("overview/index.md"),
                List.of("payment-timeout"),
                "支付总览",
                """
                        # Payment Overview

                        依赖 [[payment-timeout]]
                        """
        ));
        articleJdbcRepository.upsert(createExistingArticle(
                "refund-policy",
                "Refund Policy",
                List.of("refund/base.json"),
                List.of(),
                "退款规则",
                """
                        # Refund Policy

                        原有退款规则
                        """
        ));

        String unrelatedContent = articleJdbcRepository.findByConceptId("refund-policy").orElseThrow().getContent();

        Path incrementalRoot = Files.createDirectories(tempDir.resolve("incremental-only"));
        Path incrementalPaymentDir = Files.createDirectories(incrementalRoot.resolve("payment"));
        Files.writeString(
                incrementalPaymentDir.resolve("update.json"),
                "{"
                        + "\"concepts\":["
                        + "{\"id\":\"payment-timeout\",\"title\":\"Payment Timeout\",\"description\":\"补充支付超时补偿策略\","
                        + "\"snippets\":[\"manual-review\"],"
                        + "\"sections\":[{\"heading\":\"Compensation\",\"content\":[\"manual-review\",\"retry=5\"],\"sources\":[\"payment/update.json#compensation\"]}]}"
                        + "]"
                        + "}",
                StandardCharsets.UTF_8
        );

        CompileResult incrementalResult = stateGraphCompileOrchestrator.execute(incrementalRoot, true);

        ArticleRecord directHitArticle = articleJdbcRepository.findByConceptId("payment-timeout").orElseThrow();
        ArticleRecord downstreamArticle = articleJdbcRepository.findByConceptId("payment-overview").orElseThrow();
        ArticleRecord unrelatedArticle = articleJdbcRepository.findByConceptId("refund-policy").orElseThrow();
        assertThat(incrementalResult.getPersistedCount()).isEqualTo(2);
        assertThat(directHitArticle.getContent()).contains("manual-review");
        assertThat(downstreamArticle.getContent()).contains("manual-review");
        assertThat(downstreamArticle.getSourcePaths()).contains("overview/index.md", "payment/update.json");
        assertThat(unrelatedArticle.getContent()).isEqualTo(unrelatedContent);
        assertStepLogs(incrementalResult.getJobId());
    }

    /**
     * 验证整目录增量时，StateGraph 会先滤掉未变化文件再执行受影响文章刷新。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldIgnoreUnchangedFilesInStateGraphIncrementalCompile(@TempDir Path tempDir) throws Exception {
        resetTables();

        Path baselineRoot = Files.createDirectories(tempDir.resolve("baseline-mixed"));
        Path baselinePaymentDir = Files.createDirectories(baselineRoot.resolve("payment"));
        Path baselineRefundDir = Files.createDirectories(baselineRoot.resolve("refund"));
        Files.writeString(
                baselinePaymentDir.resolve("base.json"),
                """
                        {
                          "concepts": [
                            {
                              "id": "payment-timeout",
                              "title": "Payment Timeout",
                              "description": "现有支付超时规则",
                              "snippets": ["retry=3"],
                              "sections": [
                                {
                                  "heading": "Timeout Rules",
                                  "content": ["retry=3"],
                                  "sources": ["payment/base.json#timeout-rules"]
                                }
                              ]
                            }
                          ]
                        }
                        """.trim(),
                StandardCharsets.UTF_8
        );
        String refundBaseline = """
                {
                  "concepts": [
                    {
                      "id": "refund-policy",
                      "title": "Refund Policy",
                      "description": "退款规则",
                      "snippets": ["refund-window=7"],
                      "sections": [
                        {
                          "heading": "Refund Rules",
                          "content": ["refund-window=7"],
                          "sources": ["refund/base.json#refund-rules"]
                        }
                      ]
                    }
                  ]
                }
                """.trim();
        Files.writeString(
                baselineRefundDir.resolve("base.json"),
                refundBaseline,
                StandardCharsets.UTF_8
        );

        CompileResult baselineResult = stateGraphCompileOrchestrator.execute(baselineRoot, false);
        articleJdbcRepository.upsert(createExistingArticle(
                "payment-overview",
                "Payment Overview",
                List.of("overview/index.md"),
                List.of("payment-timeout"),
                "支付总览",
                """
                        # Payment Overview

                        依赖 [[payment-timeout]]
                        """
        ));
        String unrelatedContent = articleJdbcRepository.findByConceptId("refund-policy").orElseThrow().getContent();

        Path incrementalRoot = Files.createDirectories(tempDir.resolve("incremental-mixed"));
        Path incrementalPaymentDir = Files.createDirectories(incrementalRoot.resolve("payment"));
        Path incrementalRefundDir = Files.createDirectories(incrementalRoot.resolve("refund"));
        Files.writeString(
                incrementalPaymentDir.resolve("base.json"),
                """
                        {
                          "concepts": [
                            {
                              "id": "payment-timeout",
                              "title": "Payment Timeout",
                              "description": "补充支付超时补偿策略",
                              "snippets": ["manual-review"],
                              "sections": [
                                {
                                  "heading": "Compensation",
                                  "content": ["manual-review", "retry=5"],
                                  "sources": ["payment/base.json#compensation"]
                                }
                              ]
                            }
                          ]
                        }
                        """.trim(),
                StandardCharsets.UTF_8
        );
        Files.writeString(
                incrementalRefundDir.resolve("base.json"),
                refundBaseline,
                StandardCharsets.UTF_8
        );

        CompileResult incrementalResult = stateGraphCompileOrchestrator.execute(incrementalRoot, true);

        ArticleRecord directHitArticle = articleJdbcRepository.findByConceptId("payment-timeout").orElseThrow();
        ArticleRecord downstreamArticle = articleJdbcRepository.findByConceptId("payment-overview").orElseThrow();
        ArticleRecord unrelatedArticle = articleJdbcRepository.findByConceptId("refund-policy").orElseThrow();
        assertThat(baselineResult.getPersistedCount()).isEqualTo(2);
        assertThat(incrementalResult.getPersistedCount()).isEqualTo(2);
        assertThat(directHitArticle.getContent()).contains("manual-review");
        assertThat(downstreamArticle.getContent()).contains("manual-review");
        assertThat(unrelatedArticle.getContent()).isEqualTo(unrelatedContent);
        assertStepLogs(incrementalResult.getJobId());
    }

    /**
     * 重置测试表。
     */
    private void resetTables() {
        jdbcTemplate.execute("TRUNCATE TABLE lattice_b8_state_graph_test.source_files CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE lattice_b8_state_graph_test.synthesis_artifacts");
        jdbcTemplate.execute("TRUNCATE TABLE lattice_b8_state_graph_test.articles CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE lattice_b8_state_graph_test.compile_job_steps");
    }

    /**
     * 断言步骤日志包含稳定关联令牌与角色路由元信息。
     *
     * @param jobId 作业标识
     */
    private void assertStepLogs(String jobId) {
        List<CompileJobStepRecord> stepRecords = compileJobStepJdbcRepository.findByJobId(jobId);

        assertThat(stepRecords).isNotEmpty();
        assertThat(stepRecords)
                .extracting(CompileJobStepRecord::getStepExecutionId)
                .allMatch(value -> value != null && !value.isBlank())
                .doesNotHaveDuplicates();
        assertThat(stepRecords)
                .extracting(CompileJobStepRecord::getSequenceNo)
                .allMatch(value -> value.intValue() > 0);

        CompileJobStepRecord compileArticlesStep = stepRecords.stream()
                .filter(stepRecord -> "compile_new_articles".equals(stepRecord.getStepName()))
                .findFirst()
                .orElseThrow();
        CompileJobStepRecord reviewArticlesStep = stepRecords.stream()
                .filter(stepRecord -> "review_articles".equals(stepRecord.getStepName()))
                .findFirst()
                .orElseThrow();

        assertThat(compileArticlesStep.getAgentRole()).isEqualTo("WriterAgent");
        assertThat(compileArticlesStep.getModelRoute()).isNotBlank();
        assertThat(reviewArticlesStep.getAgentRole()).isEqualTo("ReviewerAgent");
        assertThat(reviewArticlesStep.getModelRoute()).isEqualTo("rule-based");
    }

    /**
     * 创建已有文章。
     *
     * @param conceptId 概念标识
     * @param title 标题
     * @param sourcePaths 来源路径
     * @param dependsOn 依赖概念
     * @param summary 摘要
     * @param body 主体
     * @return 文章记录
     */
    private ArticleRecord createExistingArticle(
            String conceptId,
            String title,
            List<String> sourcePaths,
            List<String> dependsOn,
            String summary,
            String body
    ) {
        return new ArticleRecord(
                conceptId,
                title,
                buildArticleContent(title, summary, sourcePaths, dependsOn, List.of(), body),
                "ACTIVE",
                OffsetDateTime.now(),
                sourcePaths,
                "{\"incremental\":false}",
                summary,
                List.of(),
                dependsOn,
                List.of(),
                "medium",
                "pending"
        );
    }

    /**
     * 构建文章 Markdown。
     *
     * @param title 标题
     * @param summary 摘要
     * @param sourcePaths 来源路径
     * @param dependsOn 依赖概念
     * @param related 相关概念
     * @param body 主体
     * @return Markdown 内容
     */
    private String buildArticleContent(
            String title,
            String summary,
            List<String> sourcePaths,
            List<String> dependsOn,
            List<String> related,
            String body
    ) {
        return """
                ---
                title: "%s"
                summary: "%s"
                referential_keywords: []
                sources: %s
                depends_on: %s
                related: %s
                confidence: medium
                compiled_at: "%s"
                review_status: pending
                ---

                %s
                """.formatted(
                title,
                summary,
                formatYamlList(sourcePaths),
                formatYamlList(dependsOn),
                formatYamlList(related),
                OffsetDateTime.now(),
                body
        ).trim();
    }

    /**
     * 格式化 YAML 行内列表。
     *
     * @param values 值列表
     * @return YAML 行内列表
     */
    private String formatYamlList(List<String> values) {
        List<String> escapedValues = new ArrayList<String>();
        for (String value : values) {
            escapedValues.add("\"" + value + "\"");
        }
        return "[" + String.join(", ", escapedValues) + "]";
    }
}
