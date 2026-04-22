package com.xbk.lattice.api.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.compiler.service.CompileJobService;
import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AdminCompileJobController 测试
 *
 * 职责：验证 B8 管理侧上传、异步作业、作业查询与重试链路
 *
 * @author xiexu
 */
@SpringBootTest(properties = {
        "spring.profiles.active=jdbc",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge?currentSchema=lattice_b8_compile_job_test",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.flyway.enabled=true",
        "spring.flyway.schemas=lattice_b8_compile_job_test",
        "spring.flyway.default-schema=lattice_b8_compile_job_test",
        "spring.ai.openai.api-key=test-openai-key",
        "spring.ai.anthropic.api-key=test-anthropic-key",
        "lattice.query.cache.store=in-memory",
        "lattice.compiler.jobs.worker-enabled=false"
})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class AdminCompileJobControllerTests {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CompileJobService compileJobService;

    @Autowired
    private ArticleJdbcRepository articleJdbcRepository;

    /**
     * 验证管理侧可提交异步编译作业并查询执行结果。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldQueueAndExecuteCompileJobViaAdminApi(@TempDir Path tempDir) throws Exception {
        resetTables();
        Path sourceDir = prepareSourceDirectory(tempDir);

        String responseBody = mockMvc.perform(post("/api/v1/admin/compile/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"
                                + "\"sourceDir\":\"" + escapeJson(sourceDir.toString()) + "\","
                                + "\"async\":true,"
                                + "\"orchestrationMode\":\"state_graph\""
                                + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.orchestrationMode").value("state_graph"))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        String jobId = extractJsonValue(responseBody, "jobId");

        compileJobService.processNextQueuedJob();

        mockMvc.perform(get("/api/v1/admin/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.items[0].jobId").value(jobId));

        mockMvc.perform(get("/api/v1/admin/jobs/" + jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(jobId))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.persistedCount").value(1))
                .andExpect(jsonPath("$.orchestrationMode").value("state_graph"));

        assertThat(articleJdbcRepository.findByConceptId("payment-timeout")).isPresent();
    }

    /**
     * 验证管理侧提交编译作业时会输出 compile_submitted 结构化事件。
     *
     * @param tempDir 临时目录
     * @param output 控制台输出
     * @throws Exception 测试异常
     */
    @Test
    void shouldEmitStructuredCompileSubmittedEvent(@TempDir Path tempDir, CapturedOutput output) throws Exception {
        resetTables();
        Path sourceDir = prepareSourceDirectory(tempDir);

        String responseBody = mockMvc.perform(post("/api/v1/admin/compile/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"
                                + "\"sourceDir\":\"" + escapeJson(sourceDir.toString()) + "\","
                                + "\"async\":true,"
                                + "\"orchestrationMode\":\"state_graph\""
                                + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        String jobId = extractJsonValue(responseBody, "jobId");

        List<JsonNode> structuredEvents = parseStructuredEvents(output.getOut());
        JsonNode compileSubmittedEvent = findStructuredEvent(structuredEvents, "compile_submitted", "compileJobId", jobId);

        assertThat(compileSubmittedEvent).isNotNull();
        assertThat(compileSubmittedEvent.path("status").asText()).isEqualTo("QUEUED");
        assertThat(compileSubmittedEvent.path("scene").asText()).isEqualTo("compile");
        assertThat(compileSubmittedEvent.path("traceId").asText()).isNotBlank();
        assertThat(compileSubmittedEvent.path("rootTraceId").asText()).isEqualTo(compileSubmittedEvent.path("traceId").asText());
    }

    /**
     * 验证 compile job 会持久化 rootTraceId，并在后台执行阶段复用同一根追踪链。
     *
     * @param tempDir 临时目录
     * @param output 控制台输出
     * @throws Exception 测试异常
     */
    @Test
    void shouldPersistRootTraceIdAndReuseItDuringCompileExecution(@TempDir Path tempDir, CapturedOutput output)
            throws Exception {
        resetTables();
        Path sourceDir = prepareSourceDirectory(tempDir);

        String responseBody = mockMvc.perform(post("/api/v1/admin/compile/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"
                                + "\"sourceDir\":\"" + escapeJson(sourceDir.toString()) + "\","
                                + "\"async\":true,"
                                + "\"orchestrationMode\":\"state_graph\""
                                + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        String jobId = extractJsonValue(responseBody, "jobId");

        String persistedRootTraceId = jdbcTemplate.queryForObject(
                "select root_trace_id from lattice_b8_compile_job_test.compile_jobs where job_id = ?",
                String.class,
                jobId
        );
        assertThat(persistedRootTraceId).isNotBlank();

        compileJobService.processNextQueuedJob();

        List<JsonNode> structuredEvents = parseStructuredEvents(output.getOut());
        JsonNode compileSubmittedEvent = findStructuredEvent(structuredEvents, "compile_submitted", "compileJobId", jobId);
        JsonNode compileStartedEvent = findStructuredEvent(structuredEvents, "compile_started", "compileJobId", jobId);
        JsonNode compileCompletedEvent = findStructuredEvent(structuredEvents, "compile_completed", "compileJobId", jobId);
        JsonNode compileGraphStartedEvent = findStructuredEvent(structuredEvents, "compile_graph_step_started", "compileJobId", jobId);
        JsonNode compileGraphCompletedEvent = findStructuredEvent(structuredEvents, "compile_graph_step_completed", "compileJobId", jobId);

        assertThat(compileSubmittedEvent).isNotNull();
        assertThat(compileStartedEvent).isNotNull();
        assertThat(compileCompletedEvent).isNotNull();
        assertThat(compileGraphStartedEvent).isNotNull();
        assertThat(compileGraphCompletedEvent).isNotNull();
        assertThat(compileSubmittedEvent.path("rootTraceId").asText()).isEqualTo(persistedRootTraceId);
        assertThat(compileStartedEvent.path("traceId").asText()).isEqualTo(persistedRootTraceId);
        assertThat(compileStartedEvent.path("rootTraceId").asText()).isEqualTo(persistedRootTraceId);
        assertThat(compileCompletedEvent.path("traceId").asText()).isEqualTo(persistedRootTraceId);
        assertThat(compileCompletedEvent.path("rootTraceId").asText()).isEqualTo(persistedRootTraceId);
        assertThat(compileCompletedEvent.path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(compileGraphStartedEvent.path("traceId").asText()).isEqualTo(persistedRootTraceId);
        assertThat(compileGraphStartedEvent.path("rootTraceId").asText()).isEqualTo(persistedRootTraceId);
        assertThat(compileGraphStartedEvent.path("nodeId").asText()).isNotBlank();
        assertThat(compileGraphStartedEvent.path("status").asText()).isEqualTo("STARTED");
        assertThat(compileGraphCompletedEvent.path("traceId").asText()).isEqualTo(persistedRootTraceId);
        assertThat(compileGraphCompletedEvent.path("rootTraceId").asText()).isEqualTo(persistedRootTraceId);
        assertThat(compileGraphCompletedEvent.path("nodeId").asText()).isNotBlank();
        assertThat(compileGraphCompletedEvent.path("status").asText()).isEqualTo("SUCCEEDED");
    }

    /**
     * 验证管理侧可上传源文件并同步触发编译。
     *
     * @throws Exception 测试异常
     */
    @Test
    void shouldUploadSourcesAndCompileSynchronouslyViaAdminApi() throws Exception {
        resetTables();
        MockMultipartFile sourceFile = new MockMultipartFile(
                "files",
                "payment/analyze.json",
                MediaType.APPLICATION_JSON_VALUE,
                ("{"
                        + "\"concepts\":["
                        + "{\"id\":\"payment-timeout\",\"title\":\"Payment Timeout\","
                        + "\"description\":\"Handles payment timeout recovery\","
                        + "\"snippets\":[\"retry=3\"],"
                        + "\"sections\":[{\"heading\":\"Timeout Rules\",\"content\":[\"retry=3\"],\"sources\":[\"payment/analyze.json#timeout-rules\"]}]"
                        + "}"
                        + "]"
                        + "}").getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/v1/admin/compile/upload")
                        .file(sourceFile)
                        .param("async", "false")
                        .param("orchestrationMode", "state_graph"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.persistedCount").value(1))
                .andExpect(jsonPath("$.orchestrationMode").value("state_graph"))
                .andExpect(jsonPath("$.sourceNames[0]").value("payment/analyze.json"));

        assertThat(articleJdbcRepository.findByConceptId("payment-timeout")).isPresent();
    }

    /**
     * 验证管理侧可上传 Word 文件并同步触发编译。
     *
     * @throws Exception 测试异常
     */
    @Test
    void shouldUploadWordDocumentAndCompileSynchronouslyViaAdminApi() throws Exception {
        resetTables();
        MockMultipartFile sourceFile = new MockMultipartFile(
                "files",
                "docs/payment-brief.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                buildSimpleWordBytes()
        );

        mockMvc.perform(multipart("/api/v1/admin/compile/upload")
                        .file(sourceFile)
                        .param("async", "false")
                        .param("orchestrationMode", "state_graph"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.persistedCount").value(1))
                .andExpect(jsonPath("$.sourceNames[0]").value("docs/payment-brief.docx"));

        Integer articleCount = jdbcTemplate.queryForObject(
                "select count(*) from lattice_b8_compile_job_test.articles",
                Integer.class
        );
        assertThat(articleCount).isNotNull();
        assertThat(articleCount.intValue()).isGreaterThan(0);
    }

    /**
     * 验证失败作业可从管理侧重试。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldRetryFailedCompileJobViaAdminApi(@TempDir Path tempDir) throws Exception {
        resetTables();
        Path missingDir = tempDir.resolve("missing-dir");

        String responseBody = mockMvc.perform(post("/api/v1/admin/compile/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"
                                + "\"sourceDir\":\"" + escapeJson(missingDir.toString()) + "\","
                                + "\"async\":true,"
                                + "\"orchestrationMode\":\"state_graph\""
                                + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        String jobId = extractJsonValue(responseBody, "jobId");

        compileJobService.processNextQueuedJob();

        mockMvc.perform(get("/api/v1/admin/jobs/" + jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));

        mockMvc.perform(post("/api/v1/admin/jobs/" + jobId + "/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(jobId))
                .andExpect(jsonPath("$.status").value("QUEUED"));
    }

    /**
     * 准备可编译的源目录。
     *
     * @param tempDir 临时目录
     * @return 源目录
     * @throws Exception 测试异常
     */
    private Path prepareSourceDirectory(Path tempDir) throws Exception {
        Path paymentDir = Files.createDirectories(tempDir.resolve("payment"));
        Files.writeString(
                paymentDir.resolve("analyze.json"),
                "{"
                        + "\"concepts\":["
                        + "{\"id\":\"payment-timeout\",\"title\":\"Payment Timeout\","
                        + "\"description\":\"Handles payment timeout recovery\","
                        + "\"snippets\":[\"retry=3\",\"interval=30s\"],"
                        + "\"sections\":[{\"heading\":\"Timeout Rules\",\"content\":[\"retry=3\",\"interval=30s\"],\"sources\":[\"payment/analyze.json#timeout-rules\"]}]"
                        + "}"
                        + "]"
                        + "}",
                StandardCharsets.UTF_8
        );
        return tempDir;
    }

    /**
     * 重置测试表。
     */
    private void resetTables() {
        jdbcTemplate.execute("TRUNCATE TABLE lattice_b8_compile_job_test.pending_queries");
        jdbcTemplate.execute("TRUNCATE TABLE lattice_b8_compile_job_test.contributions");
        jdbcTemplate.execute("TRUNCATE TABLE lattice_b8_compile_job_test.knowledge_sources RESTART IDENTITY CASCADE");
        jdbcTemplate.update(
                """
                        insert into lattice_b8_compile_job_test.knowledge_sources (
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
     * 转义 JSON 字符串。
     *
     * @param value 原始值
     * @return 转义后的值
     */
    private String escapeJson(String value) {
        return value.replace("\\", "\\\\");
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

    private List<JsonNode> parseStructuredEvents(String output) throws Exception {
        List<JsonNode> events = new ArrayList<JsonNode>();
        for (String line : output.split("\\R")) {
            String trimmedLine = line.trim();
            if (!trimmedLine.startsWith("{") || !trimmedLine.contains("\"eventName\"")) {
                continue;
            }
            events.add(OBJECT_MAPPER.readTree(trimmedLine));
        }
        return events;
    }

    private JsonNode findStructuredEvent(
            List<JsonNode> events,
            String eventName,
            String correlationField,
            String correlationValue
    ) {
        for (JsonNode event : events) {
            if (!eventName.equals(event.path("eventName").asText())) {
                continue;
            }
            if (!correlationValue.equals(event.path(correlationField).asText())) {
                continue;
            }
            return event;
        }
        return null;
    }

    /**
     * 构建简单 Word 文件字节数组。
     *
     * @return Word 文件字节数组
     * @throws Exception 测试异常
     */
    private byte[] buildSimpleWordBytes() throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("Payment timeout recovery guide");
            document.createParagraph().createRun().setText("retry=3");
            document.write(outputStream);
            return outputStream.toByteArray();
        }
    }
}
