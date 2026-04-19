package com.xbk.lattice.api.admin;

import com.xbk.lattice.compiler.service.CompileJobService;
import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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
class AdminCompileJobControllerTests {

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
