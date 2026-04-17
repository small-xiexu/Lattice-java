package com.xbk.lattice.api.query;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.compiler.service.CompileApplicationFacade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PendingQueryController 测试
 *
 * 职责：验证 B6 的纠错重生成、确认与丢弃闭环
 *
 * @author xiexu
 */
@SpringBootTest(properties = {
        "spring.profiles.active=jdbc",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge?currentSchema=lattice_b3_query_feedback_test",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.flyway.enabled=true",
        "spring.flyway.schemas=lattice_b3_query_feedback_test",
        "spring.flyway.default-schema=lattice_b3_query_feedback_test",
        "spring.ai.openai.api-key=test-openai-key",
        "spring.ai.anthropic.api-key=test-anthropic-key",
        "lattice.query.cache.store=in-memory"
})
@AutoConfigureMockMvc
class PendingQueryControllerTests {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CompileApplicationFacade compileApplicationFacade;

    /**
     * 验证纠错后答案仍保持 pending，确认后会写入 contributions。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldCorrectPendingAnswerAndConfirmContribution(@TempDir Path tempDir) throws Exception {
        resetTables();
        prepareKnowledgeBase(tempDir);

        String queryId = createPendingQuery();

        mockMvc.perform(post("/api/v1/query/" + queryId + "/correct")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correction\":\"manual-review 仅在 retry=5 时触发\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queryId").value(queryId))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("manual-review 仅在 retry=5 时触发")))
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("#")))
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("用户纠正："))));

        String correctionsJson = jdbcTemplate.queryForObject(
                "select corrections::text from lattice_b3_query_feedback_test.pending_queries where query_id = ?",
                String.class,
                queryId
        );
        assertThat(correctionsJson).contains("\"version\": 1");
        assertThat(correctionsJson).contains("manual-review 仅在 retry=5 时触发");

        Integer pendingCountAfterCorrect = jdbcTemplate.queryForObject(
                "select count(*) from lattice_b3_query_feedback_test.pending_queries",
                Integer.class
        );
        assertThat(pendingCountAfterCorrect).isEqualTo(1);

        mockMvc.perform(post("/api/v1/query/" + queryId + "/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        Integer pendingCountAfterConfirm = jdbcTemplate.queryForObject(
                "select count(*) from lattice_b3_query_feedback_test.pending_queries",
                Integer.class
        );
        Integer contributionCount = jdbcTemplate.queryForObject(
                "select count(*) from lattice_b3_query_feedback_test.contributions",
                Integer.class
        );
        assertThat(pendingCountAfterConfirm).isZero();
        assertThat(contributionCount).isEqualTo(1);

        mockMvc.perform(post("/api/v1/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"manual-review 在什么情况下触发\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("manual-review 仅在 retry=5 时触发")))
                .andExpect(jsonPath("$.sources[*].sourcePaths[*]").value(org.hamcrest.Matchers.hasItem("[用户反馈]")));
    }

    /**
     * 验证丢弃 pending 查询后不会写入 contributions。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldDiscardPendingQueryWithoutWritingContribution(@TempDir Path tempDir) throws Exception {
        resetTables();
        prepareKnowledgeBase(tempDir);

        String queryId = createPendingQuery();

        mockMvc.perform(post("/api/v1/query/" + queryId + "/discard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISCARDED"));

        Integer pendingCount = jdbcTemplate.queryForObject(
                "select count(*) from lattice_b3_query_feedback_test.pending_queries",
                Integer.class
        );
        Integer contributionCount = jdbcTemplate.queryForObject(
                "select count(*) from lattice_b3_query_feedback_test.contributions",
                Integer.class
        );
        assertThat(pendingCount).isZero();
        assertThat(contributionCount).isZero();
    }

    /**
     * 构造一条 pending 查询并返回 queryId。
     *
     * @return queryId
     * @throws Exception 测试异常
     */
    private String createPendingQuery() throws Exception {
        MvcResult mvcResult = mockMvc.perform(post("/api/v1/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"payment timeout retry=3 是什么配置\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queryId").isNotEmpty())
                .andExpect(jsonPath("$.reviewStatus").value("PASSED"))
                .andReturn();

        JsonNode responseJson = OBJECT_MAPPER.readTree(mvcResult.getResponse().getContentAsString());
        return responseJson.get("queryId").asText();
    }

    /**
     * 准备知识库测试数据。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    private void prepareKnowledgeBase(Path tempDir) throws Exception {
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
        compileApplicationFacade.compile(tempDir, false, null);
    }

    /**
     * 重置测试表。
     */
    private void resetTables() {
        jdbcTemplate.execute("TRUNCATE TABLE lattice_b3_query_feedback_test.contributions");
        jdbcTemplate.execute("TRUNCATE TABLE lattice_b3_query_feedback_test.pending_queries");
        jdbcTemplate.execute("TRUNCATE TABLE lattice_b3_query_feedback_test.source_files CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE lattice_b3_query_feedback_test.articles CASCADE");
    }
}
