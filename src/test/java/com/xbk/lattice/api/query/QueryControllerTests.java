package com.xbk.lattice.api.query;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.compiler.service.CompileApplicationFacade;
import com.xbk.lattice.compiler.service.SourceIngestSupport;
import com.xbk.lattice.compiler.domain.RawSource;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge?currentSchema=lattice",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
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

    @Autowired
    private SourceIngestSupport sourceIngestSupport;

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
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
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
                        insert into lattice.contributions (
                            id, question, answer, corrections, confirmed_by, confirmed_at
                        )
                        values (?, ?, ?, ?::jsonb, ?, ?)
                        """,
                UUID.randomUUID(),
                "approval-manual-review 是什么",
                """
                        # Approval Manual Review

                        approval-manual-review 表示请求进入人工复核队列。
                        """,
                "[]",
                "tester",
                OffsetDateTime.now()
        );

        mockMvc.perform(post("/api/v1/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"approval-manual-review 是什么意思\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("approval-manual-review 表示请求进入人工复核队列")));
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
     * 验证表格字段等值过滤与字段投影会走结构化查询短路。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldAnswerStructuredTableFieldLookupDeterministically(@TempDir Path tempDir) throws Exception {
        truncateKnowledgeTables();
        Path docsDir = Files.createDirectories(tempDir.resolve("docs"));
        Files.writeString(
                docsDir.resolve("cases.csv"),
                "id,name,remark\n100,alpha,first row\n101,beta,second row",
                StandardCharsets.UTF_8
        );
        List<RawSource> rawSources = sourceIngestSupport.ingest(tempDir);
        sourceIngestSupport.persistSourceFiles(rawSources, null, null);

        mockMvc.perform(post("/api/v1/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"id=100 这一行的 name、remark 分别是什么？\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("name=alpha")))
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("remark=first row")))
                .andExpect(jsonPath("$.sources[0].derivation").value("structured_table"))
                .andExpect(jsonPath("$.structuredEvidence.queryType").value("ROW_LOOKUP"))
                .andExpect(jsonPath("$.structuredEvidence.rows[0].sourcePath").value("docs/cases.csv"))
                .andExpect(jsonPath("$.structuredEvidence.rows[0].rowNumber").value(2))
                .andExpect(jsonPath("$.structuredEvidence.rows[0].cells[*].columnName")
                        .value(org.hamcrest.Matchers.containsInAnyOrder("id", "name", "remark")))
                .andExpect(jsonPath("$.structuredEvidence.rows[0].cells[*].role")
                        .value(org.hamcrest.Matchers.containsInAnyOrder("filter", "projection", "projection")))
                .andExpect(jsonPath("$.answerOutcome").value("SUCCESS"))
                .andExpect(jsonPath("$.generationMode").value("RULE_BASED"))
                .andExpect(jsonPath("$.fallbackReason").value("STRUCTURED_QUERY"));
    }

    /**
     * 验证结构化行定位可用通用列名解析匹配自然语言投影字段。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldAnswerStructuredTableNaturalProjectionAliasesDeterministically(@TempDir Path tempDir) throws Exception {
        truncateKnowledgeTables();
        Path docsDir = Files.createDirectories(tempDir.resolve("docs"));
        Files.writeString(
                docsDir.resolve("records.csv"),
                "record_id,name,remark\n100,alpha,first row\n101,beta,second row",
                StandardCharsets.UTF_8
        );
        List<RawSource> rawSources = sourceIngestSupport.ingest(tempDir);
        sourceIngestSupport.persistSourceFiles(rawSources, null, null);

        mockMvc.perform(post("/api/v1/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"record_id=100 这一行的 记录名称、备注 分别是什么？\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("name=alpha")))
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("remark=first row")))
                .andExpect(jsonPath("$.sources[0].sourcePaths[0]").value("docs/records.csv"))
                .andExpect(jsonPath("$.structuredEvidence.queryType").value("ROW_LOOKUP"))
                .andExpect(jsonPath("$.structuredEvidence.rows[0].cells[*].columnName")
                        .value(org.hamcrest.Matchers.containsInAnyOrder("record_id", "name", "remark")))
                .andExpect(jsonPath("$.answerOutcome").value("SUCCESS"))
                .andExpect(jsonPath("$.generationMode").value("RULE_BASED"))
                .andExpect(jsonPath("$.fallbackReason").value("STRUCTURED_QUERY"));
    }

    /**
     * 验证过滤值命中多张表时优先返回覆盖投影字段的行。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldPreferRowsCoveringProjectionFieldsWhenFilterMatchesMultipleTables(
            @TempDir Path tempDir
    ) throws Exception {
        truncateKnowledgeTables();
        Path docsDir = Files.createDirectories(tempDir.resolve("docs"));
        Files.writeString(
                docsDir.resolve("summary.csv"),
                "case_num,name,expected,module\n100714,alpha,paid,core",
                StandardCharsets.UTF_8
        );
        Files.writeString(
                docsDir.resolve("details.csv"),
                "case_num,step_index,action\n100714,1,create\n100714,2,pay",
                StandardCharsets.UTF_8
        );
        List<RawSource> rawSources = sourceIngestSupport.ingest(tempDir);
        sourceIngestSupport.persistSourceFiles(rawSources, null, null);

        mockMvc.perform(post("/api/v1/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"case_num=100714 这一行的 name、expected、module 分别是什么？\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("name=alpha")))
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("expected=paid")))
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("module=core")))
                .andExpect(jsonPath("$.sources[0].sourcePaths[0]").value("docs/summary.csv"))
                .andExpect(jsonPath("$.structuredEvidence.queryType").value("ROW_LOOKUP"))
                .andExpect(jsonPath("$.structuredEvidence.rows[0].sourcePath").value("docs/summary.csv"))
                .andExpect(jsonPath("$.answerOutcome").value("SUCCESS"))
                .andExpect(jsonPath("$.generationMode").value("RULE_BASED"))
                .andExpect(jsonPath("$.fallbackReason").value("STRUCTURED_QUERY"));
    }

    /**
     * 验证结构化表格 count 问题不会交给 LLM 数数。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldAnswerStructuredTableCountDeterministically(@TempDir Path tempDir) throws Exception {
        truncateKnowledgeTables();
        Path docsDir = Files.createDirectories(tempDir.resolve("docs"));
        Files.writeString(
                docsDir.resolve("cases.csv"),
                "status,name\ndone,alpha\ndone,beta\npending,gamma",
                StandardCharsets.UTF_8
        );
        List<RawSource> rawSources = sourceIngestSupport.ingest(tempDir);
        sourceIngestSupport.persistSourceFiles(rawSources, null, null);

        mockMvc.perform(post("/api/v1/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"status=done 有多少条？\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("2")))
                .andExpect(jsonPath("$.structuredEvidence.queryType").value("COUNT"))
                .andExpect(jsonPath("$.answerOutcome").value("SUCCESS"))
                .andExpect(jsonPath("$.generationMode").value("RULE_BASED"))
                .andExpect(jsonPath("$.fallbackReason").value("STRUCTURED_QUERY"));
    }

    /**
     * 验证结构化表格分组统计问题不会交给 LLM 聚合。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldAnswerStructuredTableGroupByDeterministically(@TempDir Path tempDir) throws Exception {
        truncateKnowledgeTables();
        Path docsDir = Files.createDirectories(tempDir.resolve("docs"));
        Files.writeString(
                docsDir.resolve("cases.csv"),
                "status,name\ndone,alpha\ndone,beta\npending,gamma",
                StandardCharsets.UTF_8
        );
        List<RawSource> rawSources = sourceIngestSupport.ingest(tempDir);
        sourceIngestSupport.persistSourceFiles(rawSources, null, null);

        mockMvc.perform(post("/api/v1/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"按 status 统计各多少\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("done=2")))
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("pending=1")))
                .andExpect(jsonPath("$.structuredEvidence.queryType").value("GROUP_BY"))
                .andExpect(jsonPath("$.structuredEvidence.groups[*].groupByField")
                        .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.equalTo("status"))))
                .andExpect(jsonPath("$.structuredEvidence.groups[*].count")
                        .value(org.hamcrest.Matchers.containsInAnyOrder(2, 1)))
                .andExpect(jsonPath("$.sources[0].sourcePaths[0]").value("docs/cases.csv"))
                .andExpect(jsonPath("$.answerOutcome").value("SUCCESS"))
                .andExpect(jsonPath("$.generationMode").value("RULE_BASED"))
                .andExpect(jsonPath("$.fallbackReason").value("STRUCTURED_QUERY"));
    }

    /**
     * 验证结构化表格两行对比问题不会交给 LLM 拼接。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldAnswerStructuredTableRowCompareDeterministically(@TempDir Path tempDir) throws Exception {
        truncateKnowledgeTables();
        Path docsDir = Files.createDirectories(tempDir.resolve("docs"));
        Files.writeString(
                docsDir.resolve("cases.csv"),
                "id,name,remark\n100,alpha,first row\n101,beta,second row",
                StandardCharsets.UTF_8
        );
        List<RawSource> rawSources = sourceIngestSupport.ingest(tempDir);
        sourceIngestSupport.persistSourceFiles(rawSources, null, null);

        mockMvc.perform(post("/api/v1/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"id=100 和 id=101 的 name、remark 对比\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("id=100")))
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("id=101")))
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("name: alpha / beta")))
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("remark: first row / second row")))
                .andExpect(jsonPath("$.structuredEvidence.queryType").value("ROW_COMPARE"))
                .andExpect(jsonPath("$.structuredEvidence.rows[*].cells[*].columnName")
                        .value(org.hamcrest.Matchers.hasItems("id", "name", "remark")))
                .andExpect(jsonPath("$.structuredEvidence.rows[*].rowNumber")
                        .value(org.hamcrest.Matchers.containsInAnyOrder(2, 3)))
                .andExpect(jsonPath("$.answerOutcome").value("SUCCESS"))
                .andExpect(jsonPath("$.generationMode").value("RULE_BASED"))
                .andExpect(jsonPath("$.fallbackReason").value("STRUCTURED_QUERY"));
    }

    /**
     * 验证结构化 count 无命中时也不会回退给 LLM 猜测。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldAnswerStructuredTableZeroCountDeterministically(@TempDir Path tempDir) throws Exception {
        truncateKnowledgeTables();
        Path docsDir = Files.createDirectories(tempDir.resolve("docs"));
        Files.writeString(
                docsDir.resolve("cases.csv"),
                "status,name\ndone,alpha\npending,beta",
                StandardCharsets.UTF_8
        );
        List<RawSource> rawSources = sourceIngestSupport.ingest(tempDir);
        sourceIngestSupport.persistSourceFiles(rawSources, null, null);

        mockMvc.perform(post("/api/v1/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"status=missing 有多少条？\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("0")))
                .andExpect(jsonPath("$.structuredEvidence.queryType").value("COUNT"))
                .andExpect(jsonPath("$.answerOutcome").value("SUCCESS"))
                .andExpect(jsonPath("$.generationMode").value("RULE_BASED"))
                .andExpect(jsonPath("$.fallbackReason").value("STRUCTURED_QUERY"));
    }

    /**
     * 验证 OCR / 文档识别运行态问答直读后台配置且不创建 pending。
     *
     * @throws Exception 测试异常
     */
    @Test
    void shouldAnswerOcrRuntimeStatusWithoutPendingQuery() throws Exception {
        truncateKnowledgeTables();
        Long connectionId = insertEnabledDocumentParseConnection();
        insertDefaultDocumentParsePolicy(connectionId);

        mockMvc.perform(post("/api/v1/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"现在 OCR / 文档识别状态怎样，图片和扫描 PDF 可用吗？\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queryId").doesNotExist())
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("已启用连接数：1")))
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("默认图片 OCR 路由：已绑定")))
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("默认扫描 PDF 路由：已绑定")))
                .andExpect(jsonPath("$.sources[0].derivation").value("RUNTIME_STATUS"))
                .andExpect(jsonPath("$.sources[0].sourcePaths[*]").value(org.hamcrest.Matchers.hasItems(
                        "/api/v1/admin/document-parse/connections",
                        "/api/v1/admin/document-parse/policies/default"
                )))
                .andExpect(jsonPath("$.articles[0].derivation").value("RUNTIME_STATUS"))
                .andExpect(jsonPath("$.answerOutcome").value("SUCCESS"))
                .andExpect(jsonPath("$.generationMode").value("RULE_BASED"))
                .andExpect(jsonPath("$.modelExecutionStatus").value("SKIPPED"));

        Integer pendingCount = jdbcTemplate.queryForObject(
                "select count(*) from lattice.pending_queries",
                Integer.class
        );
        assertThat(pendingCount).isZero();
    }

    /**
     * 清理查询相关表数据，避免测试之间互相污染。
     */
    private void truncateKnowledgeTables() {
        jdbcTemplate.execute("TRUNCATE TABLE lattice.pending_queries");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.contributions");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.source_files CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.articles CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.document_parse_route_policies CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.document_parse_connections CASCADE");
    }

    /**
     * 插入已启用文档识别连接。
     *
     * @return 连接主键
     */
    private Long insertEnabledDocumentParseConnection() {
        return jdbcTemplate.queryForObject(
                """
                        insert into lattice.document_parse_connections (
                            connection_code, provider_type, base_url,
                            credential_ciphertext, credential_mask, config_json,
                            enabled, created_by, updated_by
                        )
                        values (?, ?, ?, ?, ?, ?::jsonb, true, ?, ?)
                        returning id
                        """,
                Long.class,
                "runtime-ocr-main",
                "tencent_ocr",
                "https://ocr.example.test",
                "{\"secretId\":\"***\",\"secretKey\":\"***\"}",
                "已配置",
                "{\"endpointPath\":\"/ocr/v1/general-basic\"}",
                "test",
                "test"
        );
    }

    /**
     * 插入默认文档识别路由策略。
     *
     * @param connectionId 连接主键
     */
    private void insertDefaultDocumentParsePolicy(Long connectionId) {
        jdbcTemplate.update(
                """
                        insert into lattice.document_parse_route_policies (
                            policy_scope, image_connection_id, scanned_pdf_connection_id,
                            cleanup_enabled, fallback_policy_json, created_by, updated_by
                        )
                        values (?, ?, ?, false, ?::jsonb, ?, ?)
                        """,
                "default",
                connectionId,
                connectionId,
                "{}",
                "test",
                "test"
        );
    }

    /**
     * 提取 JSON 字段值。
     *
     * @param responseBody 响应体
     * @param fieldName 字段名
     * @return 字段值
     * @throws Exception JSON 解析异常
     */
    private String extractJsonValue(String responseBody, String fieldName) throws Exception {
        JsonNode responseJson = OBJECT_MAPPER.readTree(responseBody);
        return responseJson.path(fieldName).asText();
    }

    /**
     * 解析结构化日志事件。
     *
     * @param output 控制台输出
     * @return 结构化事件列表
     * @throws Exception JSON 解析异常
     */
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

    /**
     * 查找指定结构化事件。
     *
     * @param events 结构化事件列表
     * @param eventName 事件名
     * @param correlationField 关联字段
     * @param correlationValue 关联值
     * @return 命中事件
     */
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
