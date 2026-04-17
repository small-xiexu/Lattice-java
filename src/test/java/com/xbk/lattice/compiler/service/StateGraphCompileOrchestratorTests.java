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
}
