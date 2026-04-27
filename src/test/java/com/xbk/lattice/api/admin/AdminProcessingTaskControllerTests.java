package com.xbk.lattice.api.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.compiler.config.CompileJobProperties;
import com.xbk.lattice.compiler.service.CompileJobLeaseManager;
import com.xbk.lattice.compiler.service.CompileJobService;
import com.xbk.lattice.infra.persistence.CompileJobJdbcRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AdminProcessingTaskController 测试。
 *
 * 职责：验证工作台统一处理任务接口能够同时暴露 source sync 与 standalone compile 两类任务
 *
 * @author xiexu
 */
@SpringBootTest(properties = {
        "spring.profiles.active=jdbc",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge?currentSchema=lattice_processing_task_test",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.flyway.enabled=true",
        "spring.flyway.schemas=lattice_processing_task_test",
        "spring.flyway.default-schema=lattice_processing_task_test",
        "spring.ai.openai.api-key=test-openai-key",
        "spring.ai.anthropic.api-key=test-anthropic-key",
        "lattice.query.cache.store=in-memory",
        "lattice.compiler.jobs.worker-enabled=false"
})
@AutoConfigureMockMvc
class AdminProcessingTaskControllerTests {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CompileJobService compileJobService;

    @Autowired
    private CompileJobJdbcRepository compileJobJdbcRepository;

    @Autowired
    private CompileJobLeaseManager compileJobLeaseManager;

    @Autowired
    private CompileJobProperties compileJobProperties;

    /**
     * 验证统一处理任务接口会同时返回 source sync 与 standalone compile 任务，并避免重复展示关联 compile job。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldExposeSourceSyncAndStandaloneCompileTasksWithoutDuplicatingLinkedCompileJob(@TempDir Path tempDir)
            throws Exception {
        resetTables();
        MockMultipartFile sourceFile = new MockMultipartFile(
                "files",
                "payments/readme.md",
                MediaType.TEXT_PLAIN_VALUE,
                """
                        # Payments Docs

                        retry=3
                        """.getBytes(StandardCharsets.UTF_8)
        );

        String uploadResponseBody = mockMvc.perform(multipart("/api/v1/admin/uploads").file(sourceFile))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        Long runId = readLong(uploadResponseBody, "runId");
        String linkedCompileJobId = jdbcTemplate.queryForObject(
                "select compile_job_id from lattice_processing_task_test.source_sync_runs where id = ?",
                String.class,
                runId
        );
        markJobRunningWithProgress(
                linkedCompileJobId,
                "review_articles",
                2,
                3,
                "正在审查文章（2/3）：payment-timeout"
        );

        Path standaloneDir = Files.createDirectories(tempDir.resolve("docs"));
        Files.writeString(
                standaloneDir.resolve("overview.md"),
                "# Overview\n\ncompile only",
                StandardCharsets.UTF_8
        );
        String standaloneJobId = compileJobService.submit(
                standaloneDir.toString(),
                false,
                true,
                "state_graph"
        ).getJobId();
        markJobRunningWithProgress(
                standaloneJobId,
                "fix_review_issues",
                6,
                7,
                "正在修复文章（6/7）：项目全流程真实验收手册"
        );

        String responseBody = mockMvc.perform(get("/api/v1/admin/processing-tasks?limit=10"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode rootNode = OBJECT_MAPPER.readTree(responseBody);

        assertThat(rootNode.path("summary").path("runningCount").asInt()).isEqualTo(2);
        assertThat(rootNode.path("items").size()).isEqualTo(2);

        JsonNode sourceSyncTask = findTaskByType(rootNode.path("items"), "SOURCE_SYNC");
        assertThat(sourceSyncTask).isNotNull();
        assertThat(sourceSyncTask.path("runId").asLong()).isEqualTo(runId.longValue());
        assertThat(sourceSyncTask.path("compileJobId").asText()).isEqualTo(linkedCompileJobId);
        assertThat(sourceSyncTask.path("compileCurrentStep").asText()).isEqualTo("review_articles");
        assertThat(sourceSyncTask.path("compileProgressCurrent").asInt()).isEqualTo(2);

        JsonNode standaloneTask = findTaskByType(rootNode.path("items"), "STANDALONE_COMPILE");
        assertThat(standaloneTask).isNotNull();
        assertThat(standaloneTask.path("runId").isNull()).isTrue();
        assertThat(standaloneTask.path("compileJobId").asText()).isEqualTo(standaloneJobId);
        assertThat(standaloneTask.path("sourceType").asText()).isEqualTo("DIRECT_COMPILE");
        assertThat(standaloneTask.path("title").asText()).isEqualTo("docs");
        assertThat(standaloneTask.path("compileCurrentStep").asText()).isEqualTo("fix_review_issues");
        assertThat(standaloneTask.path("compileProgressCurrent").asInt()).isEqualTo(6);
        assertThat(standaloneTask.path("compileProgressTotal").asInt()).isEqualTo(7);
    }

    /**
     * 验证统一处理任务接口会将长时间未推进的独立编译任务标记为 STALLED。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldExposeStandaloneCompileAsStalledWhenHeartbeatExpires(@TempDir Path tempDir) throws Exception {
        resetTables();
        Path standaloneDir = Files.createDirectories(tempDir.resolve("reference"));
        Files.writeString(
                standaloneDir.resolve("readme.md"),
                "# Reference\n\nslow compile",
                StandardCharsets.UTF_8
        );
        String jobId = compileJobService.submit(
                standaloneDir.toString(),
                false,
                true,
                "state_graph"
        ).getJobId();
        OffsetDateTime startedAt = OffsetDateTime.now().minusMinutes(10);
        OffsetDateTime expiredAt = startedAt.plusSeconds(compileJobProperties.getLeaseDurationSeconds());
        compileJobJdbcRepository.markRunning(jobId, compileJobProperties.getWorkerId(), startedAt, expiredAt);
        jdbcTemplate.update(
                """
                        update lattice_processing_task_test.compile_jobs
                        set last_heartbeat_at = current_timestamp - interval '10 minutes',
                            progress_updated_at = current_timestamp - interval '10 minutes',
                            current_step = 'fix_review_issues',
                            progress_current = 2,
                            progress_total = 7,
                            progress_message = '正在修复文章（2/7）：reference'
                        where job_id = ?
                        """,
                jobId
        );

        String responseBody = mockMvc.perform(get("/api/v1/admin/processing-tasks?limit=10"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode rootNode = OBJECT_MAPPER.readTree(responseBody);

        assertThat(rootNode.path("summary").path("stalledCount").asInt()).isEqualTo(1);
        JsonNode stalledTask = findTaskByType(rootNode.path("items"), "STANDALONE_COMPILE");
        assertThat(stalledTask).isNotNull();
        assertThat(stalledTask.path("compileDerivedStatus").asText()).isEqualTo("STALLED");
        assertThat(stalledTask.path("compileCurrentStep").asText()).isEqualTo("fix_review_issues");
    }

    /**
     * 将作业标记为运行中并写入进度快照。
     *
     * @param jobId 作业标识
     * @param currentStep 当前步骤
     * @param progressCurrent 当前进度
     * @param progressTotal 总进度
     * @param progressMessage 进度文案
     */
    private void markJobRunningWithProgress(
            String jobId,
            String currentStep,
            int progressCurrent,
            int progressTotal,
            String progressMessage
    ) {
        OffsetDateTime startedAt = OffsetDateTime.now();
        OffsetDateTime runningExpiresAt = startedAt.plusSeconds(compileJobProperties.getLeaseDurationSeconds());
        compileJobJdbcRepository.markRunning(
                jobId,
                compileJobProperties.getWorkerId(),
                startedAt,
                runningExpiresAt
        );
        compileJobLeaseManager.touchProgress(
                jobId,
                currentStep,
                progressCurrent,
                progressTotal,
                progressMessage
        );
    }

    /**
     * 在任务列表中查找指定类型的任务。
     *
     * @param items 任务列表
     * @param taskType 任务类型
     * @return 命中的任务节点
     */
    private JsonNode findTaskByType(JsonNode items, String taskType) {
        if (items == null || !items.isArray()) {
            return null;
        }
        for (JsonNode itemNode : items) {
            if (taskType.equals(itemNode.path("taskType").asText())) {
                return itemNode;
            }
        }
        return null;
    }

    /**
     * 重置测试表。
     */
    private void resetTables() {
        jdbcTemplate.execute("TRUNCATE TABLE lattice_processing_task_test.pending_queries");
        jdbcTemplate.execute("TRUNCATE TABLE lattice_processing_task_test.contributions");
        jdbcTemplate.execute("TRUNCATE TABLE lattice_processing_task_test.knowledge_sources RESTART IDENTITY CASCADE");
        jdbcTemplate.update(
                """
                        insert into lattice_processing_task_test.knowledge_sources (
                            source_code,
                            name,
                            source_type,
                            content_profile,
                            status,
                            visibility,
                            default_sync_mode,
                            config_json,
                            metadata_json
                        )
                        values (
                            'legacy-default',
                            'Legacy Default Source',
                            'UPLOAD',
                            'DOCUMENT',
                            'ACTIVE',
                            'NORMAL',
                            'FULL',
                            '{}'::jsonb,
                            '{"legacyDefault":true}'::jsonb
                        )
                        """
        );
    }

    /**
     * 从 JSON 响应中读取 Long 字段。
     *
     * @param responseBody 响应体
     * @param fieldName 字段名
     * @return Long 值
     * @throws Exception 解析异常
     */
    private Long readLong(String responseBody, String fieldName) throws Exception {
        JsonNode rootNode = OBJECT_MAPPER.readTree(responseBody);
        JsonNode valueNode = rootNode.path(fieldName);
        if (valueNode.isMissingNode() || valueNode.isNull()) {
            return null;
        }
        return Long.valueOf(valueNode.asLong());
    }
}
