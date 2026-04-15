package com.xbk.lattice.api.compiler;

import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.compiler.service.CompilePipelineService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CompileController 测试
 *
 * 职责：验证最小编译入口 API 可触发编译并落表
 *
 * @author xiexu
 */
@SpringBootTest(properties = {
        "spring.profiles.active=jdbc",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge?currentSchema=lattice_b1_api_test",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.flyway.enabled=true",
        "spring.flyway.schemas=lattice_b1_api_test",
        "spring.flyway.default-schema=lattice_b1_api_test",
        "spring.ai.openai.api-key=test-openai-key",
        "spring.ai.anthropic.api-key=test-anthropic-key"
})
@AutoConfigureMockMvc
class CompileControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ArticleJdbcRepository articleJdbcRepository;

    @Autowired
    private CompilePipelineService compilePipelineService;

    /**
     * 验证编译接口可触发最小编译链路。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldCompileSourceDirectoryViaHttpApi(@TempDir Path tempDir) throws Exception {
        jdbcTemplate.execute("TRUNCATE TABLE lattice_b1_api_test.source_files");
        jdbcTemplate.execute("TRUNCATE TABLE lattice_b1_api_test.articles CASCADE");

        Path paymentDir = Files.createDirectories(tempDir.resolve("payment"));
        Files.writeString(paymentDir.resolve("order.md"), "order-flow", StandardCharsets.UTF_8);

        String requestBody = "{\"sourceDir\":\"" + escapeJson(tempDir.toString()) + "\"}";

        mockMvc.perform(post("/api/v1/compile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.persistedCount").value(1));

        Optional<ArticleRecord> articleRecord = articleJdbcRepository.findByConceptId("payment");
        assertThat(articleRecord).isPresent();
    }

    /**
     * 验证编译接口在 sourceDir 不存在时返回可读错误。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldReturnReadableErrorWhenSourceDirectoryDoesNotExist(@TempDir Path tempDir) throws Exception {
        Path missingDir = tempDir.resolve("missing-dir");
        String requestBody = "{\"sourceDir\":\"" + escapeJson(missingDir.toString()) + "\"}";

        mockMvc.perform(post("/api/v1/compile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMPILE_REQUEST_INVALID"))
                .andExpect(jsonPath("$.message").value("sourceDir 不存在或不是目录"));
    }

    /**
     * 验证增量编译请求会增强已有文章，而不是直接覆盖旧内容。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldEnhanceExistingArticleViaIncrementalCompileApi(@TempDir Path tempDir) throws Exception {
        jdbcTemplate.execute("TRUNCATE TABLE lattice_b1_api_test.source_files");
        jdbcTemplate.execute("TRUNCATE TABLE lattice_b1_api_test.synthesis_artifacts");
        jdbcTemplate.execute("TRUNCATE TABLE lattice_b1_api_test.articles CASCADE");

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
        compilePipelineService.compile(baselineRoot);

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

        String requestBody = "{"
                + "\"sourceDir\":\"" + escapeJson(incrementalRoot.toString()) + "\","
                + "\"incremental\":true"
                + "}";

        mockMvc.perform(post("/api/v1/compile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.persistedCount").value(1));

        ArticleRecord updatedArticle = articleJdbcRepository.findByConceptId("payment-timeout").orElseThrow();
        assertThat(updatedArticle.getSourcePaths()).contains("payment/base.json", "payment/update.json");
        assertThat(updatedArticle.getContent()).contains("payment/base.json");
        assertThat(updatedArticle.getContent()).contains("payment/update.json");
        assertThat(updatedArticle.getContent()).contains("manual-review");
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
}
