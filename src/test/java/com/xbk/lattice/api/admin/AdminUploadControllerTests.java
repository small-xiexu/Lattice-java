package com.xbk.lattice.api.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.compiler.config.CompileJobProperties;
import com.xbk.lattice.compiler.service.CompileJobLeaseManager;
import com.xbk.lattice.compiler.service.CompileJobService;
import com.xbk.lattice.infra.persistence.CompileJobJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.source.domain.KnowledgeSource;
import com.xbk.lattice.source.service.SourceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 统一上传接口集成测试。
 *
 * 职责：验证 uploads、WAIT_CONFIRM 与无变更跳过主链路
 *
 * @author xiexu
 */
@SpringBootTest(properties = {
        "spring.profiles.active=jdbc",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge?currentSchema=lattice_phase_e_upload_test",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.flyway.enabled=true",
        "spring.flyway.schemas=lattice_phase_e_upload_test",
        "spring.flyway.default-schema=lattice_phase_e_upload_test",
        "spring.ai.openai.api-key=test-openai-key",
        "spring.ai.anthropic.api-key=test-anthropic-key",
        "lattice.query.cache.store=in-memory",
        "lattice.compiler.jobs.worker-enabled=false"
})
@AutoConfigureMockMvc
class AdminUploadControllerTests {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CompileJobService compileJobService;

    @Autowired
    private ArticleJdbcRepository articleJdbcRepository;

    @Autowired
    private CompileJobJdbcRepository compileJobJdbcRepository;

    @Autowired
    private CompileJobLeaseManager compileJobLeaseManager;

    @Autowired
    private CompileJobProperties compileJobProperties;

    @Autowired
    private SourceService sourceService;

    /**
     * 验证统一上传可自动创建新资料源并触发编译。
     *
     * @throws Exception 测试异常
     */
    @Test
    void shouldCreateNewSourceAndCompileViaUnifiedUpload() throws Exception {
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

        String responseBody = mockMvc.perform(multipart("/api/v1/admin/uploads").file(sourceFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPILE_QUEUED"))
                .andExpect(jsonPath("$.resolverDecision").value("NEW_SOURCE"))
                .andExpect(jsonPath("$.syncAction").value("CREATE"))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        Long runId = readLong(responseBody, "runId");
        Long sourceId = readLong(responseBody, "sourceId");

        compileJobService.processNextQueuedJob();

        mockMvc.perform(get("/api/v1/admin/source-runs/" + runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.sourceId").value(sourceId))
                .andExpect(jsonPath("$.compileJobStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.compileDerivedStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.compileCurrentStep").value("finalize_job"))
                .andExpect(jsonPath("$.compileProgressCurrent").value(1))
                .andExpect(jsonPath("$.compileProgressTotal").value(1))
                .andExpect(jsonPath("$.compileProgressMessage").value("编译完成"))
                .andExpect(jsonPath("$.compileLastHeartbeatAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/admin/sources/" + sourceId + "/runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].runId").value(runId));

        mockMvc.perform(get("/api/v1/admin/source-runs?limit=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].runId").value(runId));

        Integer articleCount = jdbcTemplate.queryForObject(
                "select count(*) from lattice_phase_e_upload_test.articles",
                Integer.class
        );
        assertThat(articleCount).isNotNull();
        assertThat(articleCount.intValue()).isGreaterThan(0);
        Integer snapshotCount = jdbcTemplate.queryForObject(
                "select count(*) from lattice_phase_e_upload_test.source_snapshots where source_id = ?",
                Integer.class,
                sourceId
        );
        assertThat(snapshotCount).isEqualTo(1);
    }

    /**
     * 验证统一上传会接收 `.doc` 与 `.csv` 并落入主编译链。
     *
     * @throws Exception 测试异常
     */
    @Test
    void shouldAcceptLegacyDocAndCsvViaUnifiedUpload() throws Exception {
        resetTables();
        MockMultipartFile legacyDocFile;
        try (InputStream inputStream = getClass().getResourceAsStream("/documentparse/legacy-word.doc")) {
            assertThat(inputStream).isNotNull();
            legacyDocFile = new MockMultipartFile(
                    "files",
                    "payments/legacy-word.doc",
                    "application/msword",
                    inputStream.readAllBytes()
            );
        }
        MockMultipartFile csvFile = new MockMultipartFile(
                "files",
                "payments/rules.csv",
                "text/csv",
                """
                        businessSubTypeCode,meaning
                        1210,refund
                        """.getBytes(StandardCharsets.UTF_8)
        );

        String responseBody = mockMvc.perform(multipart("/api/v1/admin/uploads")
                        .file(legacyDocFile)
                        .file(csvFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPILE_QUEUED"))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        Long runId = readLong(responseBody, "runId");

        compileJobService.processNextQueuedJob();

        mockMvc.perform(get("/api/v1/admin/source-runs/" + runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.compileJobStatus").value("SUCCEEDED"));

        Integer docCount = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from lattice_phase_e_upload_test.source_files
                        where format = 'doc'
                          and content_text like '%Legacy DOC payment timeout%'
                        """,
                Integer.class
        );
        Integer csvCount = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from lattice_phase_e_upload_test.source_files
                        where format = 'csv'
                          and content_text like '%businessSubTypeCode,meaning%'
                        """,
                Integer.class
        );
        assertThat(docCount).isEqualTo(1);
        assertThat(csvCount).isEqualTo(1);
    }

    /**
     * 验证模糊命中时会进入 WAIT_CONFIRM，人工确认后可继续编译。
     *
     * @throws Exception 测试异常
     */
    @Test
    void shouldEnterWaitConfirmAndResumeAfterManualConfirm() throws Exception {
        resetTables();
        KnowledgeSource existingSource = sourceService.save(new KnowledgeSource(
                null,
                "payments-docs",
                "Payments Docs",
                "UPLOAD",
                "DOCUMENT",
                "ACTIVE",
                "NORMAL",
                "AUTO",
                "{}",
                "{\"bundleSummary\":{\"displayName\":\"Payments Docs\"}}",
                null,
                null,
                null,
                null,
                null,
                null
        ));
        MockMultipartFile sourceFile = new MockMultipartFile(
                "files",
                "payments-docs/readme.md",
                MediaType.TEXT_PLAIN_VALUE,
                """
                        # Payments Docs

                        manual-review
                        """.getBytes(StandardCharsets.UTF_8)
        );

        String responseBody = mockMvc.perform(multipart("/api/v1/admin/uploads").file(sourceFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAIT_CONFIRM"))
                .andExpect(jsonPath("$.resolverDecision").value("AMBIGUOUS"))
                .andExpect(jsonPath("$.matchedSourceId").value(existingSource.getId()))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        Long runId = readLong(responseBody, "runId");

        mockMvc.perform(post("/api/v1/admin/source-runs/" + runId + "/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"EXISTING_SOURCE_APPEND\",\"sourceId\":" + existingSource.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPILE_QUEUED"))
                .andExpect(jsonPath("$.resolverMode").value("MANUAL_OVERRIDE"))
                .andExpect(jsonPath("$.resolverDecision").value("EXISTING_SOURCE_APPEND"));

        compileJobService.processNextQueuedJob();

        mockMvc.perform(get("/api/v1/admin/source-runs/" + runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.sourceId").value(existingSource.getId()))
                .andExpect(jsonPath("$.compileJobStatus").value("SUCCEEDED"));
    }

    /**
     * 验证运行中的 compile / review / fix 子阶段都会通过 source-runs 暴露当前步骤与进度。
     *
     * @throws Exception 测试异常
     */
    @Test
    void shouldExposeRunningCompileReviewAndFixProgressViaSourceRuns() throws Exception {
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

        String responseBody = mockMvc.perform(multipart("/api/v1/admin/uploads").file(sourceFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPILE_QUEUED"))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        Long runId = readLong(responseBody, "runId");
        String compileJobId = jdbcTemplate.queryForObject(
                "select compile_job_id from lattice_phase_e_upload_test.source_sync_runs where id = ?",
                String.class,
                runId
        );
        OffsetDateTime startedAt = OffsetDateTime.now();
        compileJobJdbcRepository.markRunning(
                compileJobId,
                compileJobProperties.getWorkerId(),
                startedAt,
                startedAt.plusSeconds(compileJobProperties.getLeaseDurationSeconds())
        );

        compileJobLeaseManager.touchProgress(
                compileJobId,
                "compile_new_articles",
                1,
                3,
                "正在生成文章（1/3）：payment-timeout"
        );
        mockMvc.perform(get("/api/v1/admin/source-runs/" + runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.compileDerivedStatus").value("RUNNING"))
                .andExpect(jsonPath("$.compileCurrentStep").value("compile_new_articles"))
                .andExpect(jsonPath("$.compileProgressCurrent").value(1))
                .andExpect(jsonPath("$.compileProgressTotal").value(3))
                .andExpect(jsonPath("$.compileProgressMessage").value("正在生成文章（1/3）：payment-timeout"))
                .andExpect(jsonPath("$.compileLastHeartbeatAt").isNotEmpty());

        compileJobLeaseManager.touchProgress(
                compileJobId,
                "review_articles",
                2,
                3,
                "正在审查文章（2/3）：payment-timeout"
        );
        mockMvc.perform(get("/api/v1/admin/source-runs/" + runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compileCurrentStep").value("review_articles"))
                .andExpect(jsonPath("$.compileProgressCurrent").value(2))
                .andExpect(jsonPath("$.compileProgressTotal").value(3))
                .andExpect(jsonPath("$.compileProgressMessage").value("正在审查文章（2/3）：payment-timeout"));

        compileJobLeaseManager.touchProgress(
                compileJobId,
                "fix_review_issues",
                1,
                1,
                "正在修复审查问题（1/1）：payment-timeout"
        );
        mockMvc.perform(get("/api/v1/admin/source-runs/" + runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compileCurrentStep").value("fix_review_issues"))
                .andExpect(jsonPath("$.compileProgressCurrent").value(1))
                .andExpect(jsonPath("$.compileProgressTotal").value(1))
                .andExpect(jsonPath("$.compileProgressMessage").value("正在修复审查问题（1/1）：payment-timeout"));
    }

    /**
     * 验证重复上传同一资料源时可返回 SKIPPED_NO_CHANGE。
     *
     * @throws Exception 测试异常
     */
    @Test
    void shouldSkipNoChangeWhenUploadingSameSourceAgain() throws Exception {
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

        String firstResponse = mockMvc.perform(multipart("/api/v1/admin/uploads").file(sourceFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPILE_QUEUED"))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        Long firstRunId = readLong(firstResponse, "runId");
        Long sourceId = readLong(firstResponse, "sourceId");

        compileJobService.processNextQueuedJob();
        mockMvc.perform(get("/api/v1/admin/source-runs/" + firstRunId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));

        MockMultipartFile secondFile = new MockMultipartFile(
                "files",
                "payments/readme.md",
                MediaType.TEXT_PLAIN_VALUE,
                """
                        # Payments Docs

                        retry=3
                        """.getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/v1/admin/uploads")
                        .file(secondFile)
                        .param("sourceId", String.valueOf(sourceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SKIPPED_NO_CHANGE"))
                .andExpect(jsonPath("$.resolverDecision").value("EXISTING_SOURCE_UPDATE"))
                .andExpect(jsonPath("$.syncAction").value("UPDATE"));
    }

    /**
     * 验证同一资料源存在活动运行时会返回明确冲突。
     *
     * @throws Exception 测试异常
     */
    @Test
    void shouldRejectUploadWhenSourceHasActiveRun() throws Exception {
        resetTables();
        KnowledgeSource source = sourceService.save(new KnowledgeSource(
                null,
                "payments-docs",
                "Payments Docs",
                "UPLOAD",
                "DOCUMENT",
                "ACTIVE",
                "NORMAL",
                "AUTO",
                "{}",
                "{}",
                null,
                null,
                null,
                null,
                null,
                null
        ));
        MockMultipartFile firstFile = new MockMultipartFile(
                "files",
                "payments/readme.md",
                MediaType.TEXT_PLAIN_VALUE,
                """
                        # Payments Docs

                        retry=3
                        """.getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile secondFile = new MockMultipartFile(
                "files",
                "payments/appendix.md",
                MediaType.TEXT_PLAIN_VALUE,
                """
                        # Payments Appendix

                        timeout=30s
                        """.getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/v1/admin/uploads")
                        .file(firstFile)
                        .param("sourceId", String.valueOf(source.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPILE_QUEUED"));

        mockMvc.perform(multipart("/api/v1/admin/uploads")
                        .file(secondFile)
                        .param("sourceId", String.valueOf(source.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SOURCE_SYNC_CONFLICT"))
                .andExpect(jsonPath("$.message").exists());
    }

    /**
     * 验证 WAIT_CONFIRM 超时后会自动转为 FAILED。
     *
     * @throws Exception 测试异常
     */
    @Test
    void shouldExpireWaitConfirmRunAfterTimeout() throws Exception {
        resetTables();
        KnowledgeSource existingSource = sourceService.save(new KnowledgeSource(
                null,
                "payments-docs",
                "Payments Docs",
                "UPLOAD",
                "DOCUMENT",
                "ACTIVE",
                "NORMAL",
                "AUTO",
                "{}",
                "{\"bundleSummary\":{\"displayName\":\"Payments Docs\"}}",
                null,
                null,
                null,
                null,
                null,
                null
        ));
        MockMultipartFile sourceFile = new MockMultipartFile(
                "files",
                "payments-docs/readme.md",
                MediaType.TEXT_PLAIN_VALUE,
                """
                        # Payments Docs

                        manual-review
                        """.getBytes(StandardCharsets.UTF_8)
        );

        String responseBody = mockMvc.perform(multipart("/api/v1/admin/uploads").file(sourceFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAIT_CONFIRM"))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        Long runId = readLong(responseBody, "runId");

        jdbcTemplate.update(
                """
                        update lattice_phase_e_upload_test.source_sync_runs
                        set requested_at = current_timestamp - interval '8 days',
                            updated_at = current_timestamp - interval '8 days'
                        where id = ?
                        """,
                runId
        );

        mockMvc.perform(get("/api/v1/admin/source-runs/" + runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.matchedSourceId").value(existingSource.getId()))
                .andExpect(jsonPath("$.errorMessage").value("WAIT_CONFIRM timed out after 7 days"));
    }

    /**
     * 重置测试表。
     */
    private void resetTables() {
        jdbcTemplate.execute("TRUNCATE TABLE lattice_phase_e_upload_test.pending_queries");
        jdbcTemplate.execute("TRUNCATE TABLE lattice_phase_e_upload_test.contributions");
        jdbcTemplate.execute("TRUNCATE TABLE lattice_phase_e_upload_test.knowledge_sources RESTART IDENTITY CASCADE");
        jdbcTemplate.update(
                """
                        insert into lattice_phase_e_upload_test.knowledge_sources (
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
        return rootNode.path(fieldName).isMissingNode() || rootNode.path(fieldName).isNull()
                ? null
                : rootNode.path(fieldName).asLong();
    }
}
