package com.xbk.lattice.api.query;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.compiler.service.CompileApplicationFacade;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
@ExtendWith(OutputCaptureExtension.class)
class QueryControllerTests {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CompileApplicationFacade compileApplicationFacade;

    /**
     * 验证查询接口可返回最小答案、来源和命中文章信息。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldQueryKnowledgeBaseAndReturnAnswerSourcesAndArticles(@TempDir Path tempDir) throws Exception {
        truncateKnowledgeTables();

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

        mockMvc.perform(post("/api/v1/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"payment timeout retry=3 是什么配置\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queryId").isNotEmpty())
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("retry=3")))
                .andExpect(jsonPath("$.answerOutcome").isNotEmpty())
                .andExpect(jsonPath("$.generationMode").isNotEmpty())
                .andExpect(jsonPath("$.modelExecutionStatus").isNotEmpty())
                .andExpect(jsonPath("$.sources[0].conceptId").value("payment-timeout"))
                .andExpect(jsonPath("$.sources[0].sourcePaths[0]").value("payment/analyze.json"))
                .andExpect(jsonPath("$.articles[0].conceptId").value("payment-timeout"))
                .andExpect(jsonPath("$.articles[0].title").value("Payment Timeout"));
    }

    /**
     * 验证查询接口会使用源文件层证据回答仅存在于 source 内容中的问题。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldQueryKnowledgeBaseUsingSourceEvidence(@TempDir Path tempDir) throws Exception {
        truncateKnowledgeTables();

        Path paymentDir = Files.createDirectories(tempDir.resolve("payment"));
        Files.writeString(
                paymentDir.resolve("analyze.json"),
                "{"
                        + "\"concepts\":["
                        + "{\"id\":\"payment-routing\",\"title\":\"Payment Routing\","
                        + "\"description\":\"支付路由总览\","
                        + "\"snippets\":[\"route=standard\"],"
                        + "\"sections\":["
                        + "{\"heading\":\"Routing Rules\",\"content\":[\"route=standard\"],\"sources\":[\"payment/analyze.json#routing-rules\"]}"
                        + "]"
                        + "}"
                        + "]"
                        + "}",
                StandardCharsets.UTF_8
        );
        Files.writeString(
                paymentDir.resolve("context.md"),
                """
                        # Settlement Window

                        settle_window=45m
                        当支付网关返回 delayed-settlement 时，结算窗口固定为 45 分钟。
                        """,
                StandardCharsets.UTF_8
        );
        compileApplicationFacade.compile(tempDir, false, null);

        mockMvc.perform(post("/api/v1/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"settle_window=45m 是什么配置\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("settle_window=45m")))
                .andExpect(jsonPath("$.sources[*].sourcePaths[*]")
                        .value(org.hamcrest.Matchers.hasItem("payment/context.md")));
    }

    /**
     * 验证查询接口会使用已确认 contribution 作为后续回答证据。
     *
     * @throws Exception 测试异常
     */
    @Test
    void shouldQueryKnowledgeBaseUsingConfirmedContribution() throws Exception {
        truncateKnowledgeTables();
        jdbcTemplate.update(
                """
                        insert into lattice_b2_query_test.contributions (
                            id, question, answer, corrections, confirmed_by, confirmed_at
                        )
                        values (?, ?, ?, ?::jsonb, ?, ?)
                        """,
                UUID.randomUUID(),
                "refund-manual-review 是什么",
                """
                        # Refund Manual Review

                        refund-manual-review 表示退款请求进入人工复核队列。
                        """,
                "[]",
                "tester",
                OffsetDateTime.now()
        );

        mockMvc.perform(post("/api/v1/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"refund-manual-review 是什么意思\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("refund-manual-review 表示退款请求进入人工复核队列")));
    }

    /**
     * 验证查询接口会输出最小结构化问答事件。
     *
     * @param tempDir 临时目录
     * @param output 控制台输出
     * @throws Exception 测试异常
     */
    @Test
    void shouldEmitStructuredQueryLifecycleEvents(@TempDir Path tempDir, CapturedOutput output) throws Exception {
        truncateKnowledgeTables();

        Path paymentDir = Files.createDirectories(tempDir.resolve("payment"));
        Files.writeString(
                paymentDir.resolve("analyze.json"),
                "{"
                        + "\"concepts\":["
                        + "{\"id\":\"payment-routing\",\"title\":\"Payment Routing\","
                        + "\"description\":\"支付路由总览\","
                        + "\"snippets\":[\"route=standard\"],"
                        + "\"sections\":[{\"heading\":\"Routing Rules\",\"content\":[\"route=standard\"],\"sources\":[\"payment/analyze.json#routing-rules\"]}]"
                        + "}"
                        + "]"
                        + "}",
                StandardCharsets.UTF_8
        );
        Files.writeString(
                paymentDir.resolve("context.md"),
                """
                        # Settlement Window

                        settle_window=45m
                        当支付网关返回 delayed-settlement 时，结算窗口固定为 45 分钟。
                        """,
                StandardCharsets.UTF_8
        );
        compileApplicationFacade.compile(tempDir, false, null);

        MvcResult mvcResult = mockMvc.perform(post("/api/v1/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"settle_window=45m 是什么配置\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queryId").isNotEmpty())
                .andReturn();

        String responseBody = mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        String queryId = extractJsonValue(responseBody, "queryId");
        List<JsonNode> structuredEvents = parseStructuredEvents(output.getOut());
        JsonNode queryReceivedEvent = findStructuredEvent(structuredEvents, "query_received", "queryId", queryId);
        JsonNode llmStartedEvent = findStructuredEvent(structuredEvents, "llm_raw_call_started", "queryId", queryId);
        JsonNode llmSucceededEvent = findStructuredEvent(structuredEvents, "llm_raw_call_succeeded", "queryId", queryId);
        JsonNode queryGraphStartedEvent = findStructuredEvent(structuredEvents, "query_graph_step_started", "queryId", queryId);
        JsonNode queryGraphCompletedEvent = findStructuredEvent(structuredEvents, "query_graph_step_completed", "queryId", queryId);
        JsonNode queryCompletedEvent = findStructuredEvent(structuredEvents, "query_completed", "queryId", queryId);

        assertThat(queryReceivedEvent).isNotNull();
        assertThat(llmStartedEvent).isNotNull();
        assertThat(llmSucceededEvent).isNotNull();
        assertThat(queryGraphStartedEvent).isNotNull();
        assertThat(queryGraphCompletedEvent).isNotNull();
        assertThat(queryCompletedEvent).isNotNull();
        assertThat(queryReceivedEvent.path("traceId").asText()).isNotBlank();
        assertThat(llmStartedEvent.path("traceId").asText()).isEqualTo(queryReceivedEvent.path("traceId").asText());
        assertThat(llmSucceededEvent.path("traceId").asText()).isEqualTo(queryReceivedEvent.path("traceId").asText());
        assertThat(queryGraphStartedEvent.path("traceId").asText()).isEqualTo(queryReceivedEvent.path("traceId").asText());
        assertThat(queryGraphCompletedEvent.path("traceId").asText()).isEqualTo(queryReceivedEvent.path("traceId").asText());
        assertThat(queryCompletedEvent.path("traceId").asText()).isEqualTo(queryReceivedEvent.path("traceId").asText());
        assertThat(llmStartedEvent.path("spanId").asText()).isNotBlank();
        assertThat(llmSucceededEvent.path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(queryGraphStartedEvent.path("nodeId").asText()).isNotBlank();
        assertThat(queryGraphStartedEvent.path("status").asText()).isEqualTo("STARTED");
        assertThat(queryGraphCompletedEvent.path("nodeId").asText()).isNotBlank();
        assertThat(queryGraphCompletedEvent.path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(queryCompletedEvent.path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(queryCompletedEvent.path("answerOutcome").asText()).isNotBlank();
        assertThat(queryCompletedEvent.path("generationMode").asText()).isNotBlank();
        assertThat(queryCompletedEvent.path("modelExecutionStatus").asText()).isNotBlank();
    }

    /**
     * 清理查询相关表数据，避免测试之间互相污染。
     */
    private void truncateKnowledgeTables() {
        jdbcTemplate.execute("TRUNCATE TABLE lattice_b2_query_test.pending_queries");
        jdbcTemplate.execute("TRUNCATE TABLE lattice_b2_query_test.contributions");
        jdbcTemplate.execute("TRUNCATE TABLE lattice_b2_query_test.source_files CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE lattice_b2_query_test.articles CASCADE");
    }

    private String extractJsonValue(String responseBody, String fieldName) throws Exception {
        JsonNode responseJson = OBJECT_MAPPER.readTree(responseBody);
        return responseJson.path(fieldName).asText();
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
}
