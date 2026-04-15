package com.xbk.lattice.api.query;

import com.xbk.lattice.compiler.service.CompilePipelineService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * QueryController 测试
 *
 * 职责：验证最小查询闭环可返回答案、来源和命中文章
 *
 * @author xiexu
 */
@SpringBootTest(properties = {
        "spring.profiles.active=jdbc",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge?currentSchema=lattice_b2_query_test",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.flyway.enabled=true",
        "spring.flyway.schemas=lattice_b2_query_test",
        "spring.flyway.default-schema=lattice_b2_query_test",
        "spring.ai.openai.api-key=test-openai-key",
        "spring.ai.anthropic.api-key=test-anthropic-key",
        "lattice.compiler.ingest-max-chars=800",
        "lattice.compiler.batch-max-chars=200"
})
@AutoConfigureMockMvc
class QueryControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CompilePipelineService compilePipelineService;

    /**
     * 验证查询接口可返回最小答案、来源和命中文章信息。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldQueryKnowledgeBaseAndReturnAnswerSourcesAndArticles(@TempDir Path tempDir) throws Exception {
        jdbcTemplate.execute("TRUNCATE TABLE lattice_b2_query_test.source_files");
        jdbcTemplate.execute("TRUNCATE TABLE lattice_b2_query_test.articles CASCADE");

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
        compilePipelineService.compile(tempDir);

        mockMvc.perform(post("/api/v1/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"payment timeout retry=3 是什么配置\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("retry=3")))
                .andExpect(jsonPath("$.sources[0].conceptId").value("payment-timeout"))
                .andExpect(jsonPath("$.sources[0].sourcePaths[0]").value("payment/analyze.json"))
                .andExpect(jsonPath("$.articles[0].conceptId").value("payment-timeout"))
                .andExpect(jsonPath("$.articles[0].title").value("Payment Timeout"));
    }
}
