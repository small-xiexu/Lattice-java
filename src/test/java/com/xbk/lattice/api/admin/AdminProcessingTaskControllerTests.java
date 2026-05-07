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
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AdminProcessingTaskController 测试。
 *
 * 职责：验证工作台统一处理任务接口能够同时暴露 source sync 与 standalone compile 两类任务
 *
 * @author xiexu
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge?currentSchema=lattice",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.ai.openai.api-key=test-openai-key",
        "spring.ai.anthropic.api-key=test-anthropic-key",
        "lattice.query.cache.store=in-memory",
        "lattice.compiler.jobs.worker-enabled=false",
        "lattice.source.admin.allowed-server-dirs[0]=${java.io.tmpdir}"
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
                "select compile_job_id from lattice.source_sync_runs where id = ?",
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
        assertThat(rootNode.path("summary").path("cards").size()).isEqualTo(4);

        JsonNode sourceSyncTask = findTaskByType(rootNode.path("items"), "SOURCE_SYNC");
        assertThat(sourceSyncTask).isNotNull();
        assertThat(sourceSyncTask.path("title").asText()).isEqualTo("readme.md");
        assertThat(sourceSyncTask.path("runId").asLong()).isEqualTo(runId.longValue());
        assertThat(sourceSyncTask.path("compileJobId").asText()).isEqualTo(linkedCompileJobId);
        assertThat(sourceSyncTask.path("compileCurrentStep").asText()).isEqualTo("review_articles");
        assertThat(sourceSyncTask.path("compileProgressCurrent").asInt()).isEqualTo(2);
        assertThat(sourceSyncTask.path("displayStatus").asText()).isEqualTo("RUNNING");
        assertThat(sourceSyncTask.path("displayStatusLabel").asText()).isEqualTo("进行中");
        assertThat(sourceSyncTask.path("currentStepLabel").asText()).isEqualTo("质量检查");
        assertThat(sourceSyncTask.path("progressText").asText()).contains("2 / 3");
        assertThat(sourceSyncTask.path("reasonSummary").asText()).contains("正在审查文章");
        assertThat(sourceSyncTask.path("operationalNote").asText()).contains("当前步骤");
        assertThat(sourceSyncTask.path("displayTone").asText()).isEqualTo("warning");
        assertThat(sourceSyncTask.path("processingActive").asBoolean()).isTrue();
        assertThat(sourceSyncTask.path("requiresManualAction").asBoolean()).isFalse();
        assertThat(sourceSyncTask.path("noticeTone").asText()).isEqualTo("success");
        assertThat(sourceSyncTask.path("completionNotice").asText()).contains("正在审查文章");
        assertThat(sourceSyncTask.path("progressSteps").isArray()).isTrue();
        assertThat(sourceSyncTask.path("progressSteps").size()).isEqualTo(4);
        assertThat(sourceSyncTask.path("progressSteps").get(0).path("label").asText()).isEqualTo("资料接收");
        assertThat(sourceSyncTask.path("progressSteps").get(1).path("label").asText()).isEqualTo("内容生成");
        assertThat(sourceSyncTask.path("progressSteps").get(2).path("label").asText()).isEqualTo("质量检查");
        assertThat(sourceSyncTask.path("progressSteps").get(3).path("label").asText()).isEqualTo("写入知识库");
        assertThat(sourceSyncTask.path("progressSteps").get(1).path("key").asText()).isEqualTo("COMPILE_NEW_ARTICLES");
        assertThat(sourceSyncTask.path("progressSteps").get(2).path("key").asText()).isEqualTo("REVIEW_ARTICLES");
        assertThat(sourceSyncTask.path("progressSteps").get(2).path("status").asText()).isEqualTo("ACTIVE");
        assertThat(sourceSyncTask.path("progressSteps").get(2).path("detail").asText()).isEqualTo("正在审查文章草稿");
        assertThat(sourceSyncTask.path("progressSteps").get(2).path("detail").asText()).doesNotContain("细分状态");
        assertThat(sourceSyncTask.path("actions").isArray()).isTrue();

        JsonNode standaloneTask = findTaskByType(rootNode.path("items"), "STANDALONE_COMPILE");
        assertThat(standaloneTask).isNotNull();
        assertThat(standaloneTask.path("runId").isNull()).isTrue();
        assertThat(standaloneTask.path("compileJobId").asText()).isEqualTo(standaloneJobId);
        assertThat(standaloneTask.path("sourceType").asText()).isEqualTo("DIRECT_COMPILE");
        assertThat(standaloneTask.path("title").asText()).isEqualTo("overview.md");
        assertThat(standaloneTask.path("compileCurrentStep").asText()).isEqualTo("fix_review_issues");
        assertThat(standaloneTask.path("compileProgressCurrent").asInt()).isEqualTo(6);
        assertThat(standaloneTask.path("compileProgressTotal").asInt()).isEqualTo(7);
        assertThat(standaloneTask.path("displayStatus").asText()).isEqualTo("RUNNING");
        assertThat(standaloneTask.path("displayStatusLabel").asText()).isEqualTo("进行中");
        assertThat(standaloneTask.path("currentStepLabel").asText()).isEqualTo("质量检查");
        assertThat(standaloneTask.path("operationalNote").asText()).contains("当前步骤");
        assertThat(standaloneTask.path("displayTone").asText()).isEqualTo("warning");
        assertThat(standaloneTask.path("processingActive").asBoolean()).isTrue();
        assertThat(standaloneTask.path("progressSteps").isArray()).isTrue();
        assertThat(standaloneTask.path("progressSteps").size()).isEqualTo(4);
        assertThat(standaloneTask.path("progressSteps").get(0).path("label").asText()).isEqualTo("资料接收");
        assertThat(standaloneTask.path("progressSteps").get(1).path("label").asText()).isEqualTo("内容生成");
        assertThat(standaloneTask.path("progressSteps").get(2).path("label").asText()).isEqualTo("质量检查");
        assertThat(standaloneTask.path("progressSteps").get(3).path("label").asText()).isEqualTo("写入知识库");
        assertThat(standaloneTask.path("progressSteps").get(2).path("detail").asText()).isEqualTo("正在修复审查问题");
        assertThat(standaloneTask.path("progressSteps").get(2).path("detail").asText()).doesNotContain("细分状态");
        assertThat(rootNode.path("summary").path("helpState").path("title").asText()).isNotBlank();
        assertThat(rootNode.path("summary").path("cards").isArray()).isTrue();
    }

    /**
     * 验证目录资料源同步会进入统一处理任务列表。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldExposeServerDirSyncInProcessingTasks(@TempDir Path tempDir) throws Exception {
        resetTables();
        Path serverDir = Files.createDirectories(tempDir.resolve("server-reference"));
        Files.writeString(
                serverDir.resolve("guide.md"),
                "# Guide\n\nruntime config",
                StandardCharsets.UTF_8
        );

        String sourceResponseBody = mockMvc.perform(post("/api/v1/admin/sources/server-dir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceCode": "server-reference",
                                  "name": "Server Reference",
                                  "contentProfile": "DOCUMENT",
                                  "serverDir": "%s"
                                }
                                """.formatted(serverDir.toString().replace("\\", "\\\\"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        Long sourceId = readLong(sourceResponseBody, "id");

        String syncResponseBody = mockMvc.perform(post("/api/v1/admin/sources/" + sourceId + "/sync"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        Long runId = readLong(syncResponseBody, "runId");
        String compileJobId = readText(syncResponseBody, "compileJobId");

        String responseBody = mockMvc.perform(get("/api/v1/admin/processing-tasks?limit=10"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode rootNode = OBJECT_MAPPER.readTree(responseBody);

        assertThat(rootNode.path("summary").path("runningCount").asInt()).isEqualTo(1);
        assertThat(rootNode.path("items").size()).isEqualTo(1);
        JsonNode sourceSyncTask = findTaskByType(rootNode.path("items"), "SOURCE_SYNC");
        assertThat(sourceSyncTask).isNotNull();
        assertThat(sourceSyncTask.path("runId").asLong()).isEqualTo(runId.longValue());
        assertThat(sourceSyncTask.path("sourceId").asLong()).isEqualTo(sourceId.longValue());
        assertThat(sourceSyncTask.path("sourceName").asText()).isEqualTo("Server Reference");
        assertThat(sourceSyncTask.path("sourceType").asText()).isEqualTo("SERVER_DIR");
        assertThat(sourceSyncTask.path("status").asText()).isEqualTo("COMPILE_QUEUED");
        assertThat(sourceSyncTask.path("compileDerivedStatus").asText()).isEqualTo("QUEUED");
        assertThat(sourceSyncTask.path("compileJobId").asText()).isEqualTo(compileJobId);
        assertThat(sourceSyncTask.path("displayStatus").asText()).isEqualTo("QUEUED");
        assertThat(sourceSyncTask.path("processingActive").asBoolean()).isTrue();
        assertThat(rootNode.path("items").findValuesAsText("compileJobId")).containsExactly(compileJobId);
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
                        update lattice.compile_jobs
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

        assertThat(rootNode.path("summary").path("stalledCount").asInt()).isEqualTo(0);
        assertThat(rootNode.path("summary").path("failedCount").asInt()).isEqualTo(1);
        JsonNode stalledTask = findTaskByType(rootNode.path("items"), "STANDALONE_COMPILE");
        assertThat(stalledTask).isNotNull();
        assertThat(stalledTask.path("displayStatus").asText()).isEqualTo("STALLED");
        assertThat(stalledTask.path("displayStatusLabel").asText()).isEqualTo("失败");
        assertThat(stalledTask.path("compileDerivedStatus").asText()).isEqualTo("STALLED");
        assertThat(stalledTask.path("compileCurrentStep").asText()).isEqualTo("fix_review_issues");
        assertThat(stalledTask.path("progressSteps").get(2).path("status").asText()).isEqualTo("FAILED");
    }

    /**
     * 验证当前处理任务看板对同一资料源只保留最新一条同步记录。
     *
     * @throws Exception 测试异常
     */
    @Test
    void shouldOnlyKeepLatestSourceRunPerSourceInProcessingTasks() throws Exception {
        resetTables();
        Long sourceId = createUploadSource("srkit-svc-fc-dpfm", "卡券三期-迁移方案.md");
        insertSourceSyncRun(
                sourceId,
                "FAILED",
                "2026-05-04T00:05:54.828323Z",
                "2026-05-04T00:16:25.588161Z",
                "处理失败",
                "compile job heartbeat expired and lease timed out"
        );
        insertSourceSyncRun(
                sourceId,
                "SUCCEEDED",
                "2026-05-04T01:38:33.848574Z",
                "2026-05-04T02:10:10.834603Z",
                "处理成功，资料已写入知识库",
                null
        );

        String responseBody = mockMvc.perform(get("/api/v1/admin/processing-tasks?limit=20"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode rootNode = OBJECT_MAPPER.readTree(responseBody);

        JsonNode items = rootNode.path("items");
        assertThat(items.isArray()).isTrue();
        assertThat(items).hasSize(1);

        JsonNode latestTask = items.get(0);
        assertThat(latestTask.path("sourceId").asLong()).isEqualTo(sourceId.longValue());
        assertThat(latestTask.path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(latestTask.path("displayStatus").asText()).isEqualTo("SUCCEEDED");
        assertThat(latestTask.path("requestedAt").asText()).isEqualTo("2026-05-04T01:38:33.848574Z");
        assertThat(rootNode.path("summary").path("succeededCount").asInt()).isEqualTo(1);
        assertThat(rootNode.path("summary").path("failedCount").asInt()).isEqualTo(0);
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
     * 创建上传型资料源。
     *
     * @param sourceCode 资料源编码
     * @param name 资料源名称
     * @return 新建资料源主键
     */
    private Long createUploadSource(String sourceCode, String name) {
        jdbcTemplate.update(
                """
                        insert into lattice.knowledge_sources (
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
                        values (?, ?, 'UPLOAD', 'DOCUMENT', 'ACTIVE', 'NORMAL', 'FULL', '{}'::jsonb, '{}'::jsonb)
                        """,
                sourceCode,
                name
        );
        return jdbcTemplate.queryForObject(
                "select id from lattice.knowledge_sources where source_code = ?",
                Long.class,
                sourceCode
        );
    }

    /**
     * 插入一条同步运行记录。
     *
     * @param sourceId 资料源主键
     * @param status 运行状态
     * @param requestedAt 提交时间
     * @param updatedAt 更新时间
     * @param message 提示文案
     * @param errorMessage 错误文案
     */
    private void insertSourceSyncRun(
            Long sourceId,
            String status,
            String requestedAt,
            String updatedAt,
            String message,
            String errorMessage
    ) {
        String evidenceJson = "{\"message\":\"" + escapeJson(message) + "\"}";
        jdbcTemplate.update(
                """
                        insert into lattice.source_sync_runs (
                            source_id,
                            source_type,
                            trigger_type,
                            resolver_mode,
                            status,
                            evidence_json,
                            error_message,
                            requested_at,
                            updated_at,
                            finished_at
                        )
                        values (?, 'UPLOAD', 'MANUAL', 'RULE_ONLY', ?, cast(? as jsonb), ?, ?::timestamptz, ?::timestamptz, ?::timestamptz)
                        """,
                sourceId,
                status,
                evidenceJson,
                errorMessage,
                requestedAt,
                updatedAt,
                updatedAt
        );
    }

    /**
     * 转义 JSON 字符串。
     *
     * @param value 原始文案
     * @return 转义结果
     */
    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    /**
     * 重置测试表。
     */
    private void resetTables() {
        jdbcTemplate.execute("TRUNCATE TABLE lattice.pending_queries");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.contributions");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.knowledge_sources RESTART IDENTITY CASCADE");
        jdbcTemplate.update(
                """
                        insert into lattice.knowledge_sources (
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

    /**
     * 从 JSON 响应中读取文本字段。
     *
     * @param responseBody 响应体
     * @param fieldName 字段名
     * @return 文本值
     * @throws Exception 解析异常
     */
    private String readText(String responseBody, String fieldName) throws Exception {
        JsonNode rootNode = OBJECT_MAPPER.readTree(responseBody);
        JsonNode valueNode = rootNode.path(fieldName);
        if (valueNode.isMissingNode() || valueNode.isNull()) {
            return null;
        }
        return valueNode.asText();
    }
}
