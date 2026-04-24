package com.xbk.lattice.api.admin;

import com.xbk.lattice.LatticeApplication;
import com.xbk.lattice.compiler.service.CompileExecutionRequest;
import com.xbk.lattice.compiler.service.CompileJobService;
import com.xbk.lattice.compiler.service.CompileOrchestrator;
import com.xbk.lattice.compiler.service.CompileOrchestratorRegistry;
import com.xbk.lattice.compiler.service.CompileOrchestrationModes;
import com.xbk.lattice.compiler.service.CompileResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.List;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AdminCompileFailureRegressionTests 测试
 *
 * 职责：验证 compile job 在超时和链路异常场景下会以稳定错误码失败收口
 *
 * @author xiexu
 */
@SpringBootTest(classes = {
        LatticeApplication.class,
        AdminCompileFailureRegressionTests.TestCompileFailureConfiguration.class
}, properties = {
        "spring.profiles.active=jdbc",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge?currentSchema=lattice_w8_compile_failure_test",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.flyway.enabled=true",
        "spring.flyway.schemas=lattice_w8_compile_failure_test",
        "spring.flyway.default-schema=lattice_w8_compile_failure_test",
        "spring.ai.openai.api-key=test-openai-key",
        "spring.ai.anthropic.api-key=test-anthropic-key",
        "lattice.query.cache.store=in-memory",
        "lattice.compiler.jobs.worker-enabled=false"
})
@AutoConfigureMockMvc
class AdminCompileFailureRegressionTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CompileJobService compileJobService;

    @Autowired
    private FailureThrowingCompileOrchestrator failureThrowingCompileOrchestrator;

    /**
     * 验证模型调用超时会以稳定错误码失败收口。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldCloseCompileJobWithTimeoutErrorCode(@TempDir Path tempDir) throws Exception {
        resetTables();
        Path sourceDir = Files.createDirectories(tempDir.resolve("timeout-source"));
        failureThrowingCompileOrchestrator.setFailure(
                new TransientAiException("request timed out", new SocketTimeoutException("Read timed out"))
        );

        String jobId = submitCompileJob(sourceDir);
        compileJobService.processNextQueuedJob();

        mockMvc.perform(get("/api/v1/admin/jobs/" + jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorCode").value("LLM_REQUEST_TIMEOUT"))
                .andExpect(jsonPath("$.errorMessage").value("SocketTimeoutException: Read timed out"));
    }

    /**
     * 验证链路或代理异常会以传输类稳定错误码失败收口。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldCloseCompileJobWithTransportErrorCode(@TempDir Path tempDir) throws Exception {
        resetTables();
        Path sourceDir = Files.createDirectories(tempDir.resolve("proxy-source"));
        failureThrowingCompileOrchestrator.setFailure(
                new ResourceAccessException("proxy connect failed", new ConnectException("Connection refused"))
        );

        String jobId = submitCompileJob(sourceDir);
        compileJobService.processNextQueuedJob();

        mockMvc.perform(get("/api/v1/admin/jobs/" + jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorCode").value("LLM_TRANSPORT_ERROR"))
                .andExpect(jsonPath("$.errorMessage").value("ConnectException: Connection refused"));
    }

    /**
     * 提交异步 compile job。
     *
     * @param sourceDir 源目录
     * @return jobId
     * @throws Exception 测试异常
     */
    private String submitCompileJob(Path sourceDir) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/compile/jobs")
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
                .getContentAsString(StandardCharsets.UTF_8)
                .replaceAll("(?s).*\"jobId\":\"([^\"]+)\".*", "$1");
    }

    /**
     * 重置测试表。
     */
    private void resetTables() {
        jdbcTemplate.execute("TRUNCATE TABLE lattice_w8_compile_failure_test.compile_jobs CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE lattice_w8_compile_failure_test.knowledge_sources RESTART IDENTITY CASCADE");
        failureThrowingCompileOrchestrator.setFailure(new IllegalStateException("compile failure not configured"));
    }

    /**
     * 转义 JSON 字符串。
     *
     * @param value 原始值
     * @return 转义后的字符串
     */
    private String escapeJson(String value) {
        return value.replace("\\", "\\\\");
    }

    /**
     * 测试专用失败编排器配置。
     *
     * 职责：用可控异常替代真实 state graph 编排器，验证 compile job 失败收口
     *
     * @author xiexu
     */
    @TestConfiguration
    static class TestCompileFailureConfiguration {

        /**
         * 注册失败编排器。
         *
         * @return 失败编排器
         */
        @Bean
        FailureThrowingCompileOrchestrator failureThrowingCompileOrchestrator() {
            return new FailureThrowingCompileOrchestrator();
        }

        /**
         * 使用测试专用 registry 覆盖默认编排器注册表。
         *
         * @param failureThrowingCompileOrchestrator 失败编排器
         * @return registry
         */
        @Bean
        @Primary
        CompileOrchestratorRegistry compileOrchestratorRegistry(
                FailureThrowingCompileOrchestrator failureThrowingCompileOrchestrator
        ) {
            return new CompileOrchestratorRegistry(List.<CompileOrchestrator>of(failureThrowingCompileOrchestrator));
        }
    }

    /**
     * 可控失败的测试编排器。
     *
     * 职责：始终以测试注入的异常终止 state_graph 执行
     *
     * @author xiexu
     */
    static class FailureThrowingCompileOrchestrator implements CompileOrchestrator {

        private RuntimeException failure = new IllegalStateException("compile failure not configured");

        /**
         * 设置本次执行要抛出的异常。
         *
         * @param failure 异常
         */
        void setFailure(RuntimeException failure) {
            this.failure = failure;
        }

        /**
         * 返回测试编排模式。
         *
         * @return 模式标识
         */
        @Override
        public String getMode() {
            return CompileOrchestrationModes.STATE_GRAPH;
        }

        /**
         * 执行编译并按预期抛出异常。
         *
         * @param executionRequest 执行请求
         * @return 永不返回
         * @throws IOException 不抛出 checked IOException
         */
        @Override
        public CompileResult execute(CompileExecutionRequest executionRequest) throws IOException {
            throw failure;
        }
    }
}
