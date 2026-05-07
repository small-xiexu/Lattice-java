package com.xbk.lattice.api.query;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.compiler.domain.RawSource;
import com.xbk.lattice.compiler.service.SourceIngestSupport;
import com.xbk.lattice.query.domain.ModelExecutionStatus;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 结构化表格固定回归测试
 *
 * 职责：用通用 CSV/XLSX 样本验证结构化资料问答准确率门槛
 *
 * @author xiexu
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge?currentSchema=lattice",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.ai.openai.api-key=test-openai-key",
        "spring.ai.anthropic.api-key=test-anthropic-key"
})
@AutoConfigureMockMvc
class StructuredTableQueryRegressionTests {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SourceIngestSupport sourceIngestSupport;

    /**
     * 验证结构化资料固定回归集达到本期 P0 门槛。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldPassStructuredTableRegressionSuite(@TempDir Path tempDir) throws Exception {
        truncateKnowledgeTables();
        prepareSources(tempDir);
        List<RawSource> rawSources = sourceIngestSupport.ingest(tempDir);
        sourceIngestSupport.persistSourceFiles(rawSources, null, null);

        List<RegressionCase> cases = List.of(
                new RegressionCase(
                        "code=A100 这一行的 owner、remark 分别是什么？",
                        List.of("owner=alpha", "remark=first account"),
                        "ROW_LOOKUP",
                        2
                ),
                new RegressionCase(
                        "code=A101 这一行的 status 是什么？",
                        List.of("status=done"),
                        "ROW_LOOKUP",
                        3
                ),
                new RegressionCase(
                        "step=2 这一行的 action、expected 分别是什么？",
                        List.of("action=pay", "expected=paid"),
                        "ROW_LOOKUP",
                        3
                ),
                new RegressionCase(
                        "status=done 有多少条？",
                        List.of("2"),
                        "COUNT",
                        null
                ),
                new RegressionCase(
                        "status=missing 有多少条？",
                        List.of("0"),
                        "COUNT",
                        null
                ),
                new RegressionCase(
                        "按 status 统计各多少",
                        List.of("done=2", "pending=1", "总行数=3"),
                        "GROUP_BY",
                        null
                ),
                new RegressionCase(
                        "按 category 统计各多少",
                        List.of("core=2", "edge=1", "总行数=3"),
                        "GROUP_BY",
                        null
                ),
                new RegressionCase(
                        "code=A100 和 code=A101 的 owner、status 对比",
                        List.of("owner: alpha / beta", "status: done / done"),
                        "ROW_COMPARE",
                        null
                ),
                new RegressionCase(
                        "对比 code=A100 和 code=A101：两行的 owner、status 有什么差异？",
                        List.of("owner: alpha / beta", "status: done / done"),
                        "ROW_COMPARE",
                        null
                ),
                new RegressionCase(
                        "code=A100 这一行的 missing_field 是什么？",
                        List.of("missing_field"),
                        null,
                        null
                ),
                new RegressionCase(
                        "step=3 这一行的 expected 是什么？",
                        List.of("expected=refunded"),
                        "ROW_LOOKUP",
                        4
                )
        );

        int passed = 0;
        int structuredQuestionCount = 0;
        int structuredFallbackCount = 0;
        int checkedRowCount = 0;
        int correctRowCount = 0;
        int groupCaseCount = 0;
        int correctGroupCount = 0;
        for (RegressionCase regressionCase : cases) {
            JsonNode responseJson = query(regressionCase.question());
            assertThat(responseJson.path("modelExecutionStatus").asText())
                    .as(regressionCase.question())
                    .isNotEqualTo(ModelExecutionStatus.SUCCESS.name());
            boolean answerMatched = assertAnswerContains(responseJson, regressionCase.expectedAnswerParts());
            boolean typeMatched = regressionCase.expectedQueryType() == null
                    || regressionCase.expectedQueryType().equals(responseJson.path("structuredEvidence").path("queryType").asText());
            if (regressionCase.expectedQueryType() != null) {
                structuredQuestionCount++;
                if (!"STRUCTURED_QUERY".equals(responseJson.path("fallbackReason").asText())) {
                    structuredFallbackCount++;
                }
            }
            if (regressionCase.expectedRowNumber() != null) {
                checkedRowCount++;
                int actualRowNumber = responseJson.path("structuredEvidence").path("rows").path(0).path("rowNumber").asInt();
                if (regressionCase.expectedRowNumber().intValue() == actualRowNumber) {
                    correctRowCount++;
                }
            }
            if ("GROUP_BY".equals(regressionCase.expectedQueryType())) {
                groupCaseCount++;
                if (answerMatched && responseJson.path("structuredEvidence").path("groups").size() > 0) {
                    correctGroupCount++;
                }
            }
            if (answerMatched && typeMatched) {
                passed++;
            }
        }

        assertThat(passed).isGreaterThanOrEqualTo(9);
        assertThat(correctRowCount).isEqualTo(checkedRowCount);
        assertThat(correctGroupCount).isEqualTo(groupCaseCount);
        assertThat(structuredFallbackCount).isLessThanOrEqualTo(1);
        assertThat(structuredQuestionCount).isGreaterThanOrEqualTo(10);
    }

    private void prepareSources(Path tempDir) throws Exception {
        Path docsDir = Files.createDirectories(tempDir.resolve("docs"));
        Files.writeString(
                docsDir.resolve("cases.csv"),
                """
                        code,status,owner,category,remark
                        A100,done,alpha,core,first account
                        A101,done,beta,core,second account
                        A102,pending,gamma,edge,third account
                        """,
                StandardCharsets.UTF_8
        );
        Path workbookPath = docsDir.resolve("steps.xlsx");
        try (Workbook workbook = new XSSFWorkbook(); OutputStream outputStream = Files.newOutputStream(workbookPath)) {
            Sheet sheet = workbook.createSheet("steps");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("step");
            header.createCell(1).setCellValue("action");
            header.createCell(2).setCellValue("expected");
            Row first = sheet.createRow(1);
            first.createCell(0).setCellValue("1");
            first.createCell(1).setCellValue("create");
            first.createCell(2).setCellValue("created");
            Row second = sheet.createRow(2);
            second.createCell(0).setCellValue("2");
            second.createCell(1).setCellValue("pay");
            second.createCell(2).setCellValue("paid");
            Row third = sheet.createRow(3);
            third.createCell(0).setCellValue("3");
            third.createCell(1).setCellValue("refund");
            third.createCell(2).setCellValue("refunded");
            workbook.write(outputStream);
        }
    }

    private JsonNode query(String question) throws Exception {
        String requestBody = OBJECT_MAPPER.writeValueAsString(Map.of("question", question));
        MvcResult mvcResult = mockMvc.perform(post("/api/v1/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andReturn();
        assertThat(mvcResult.getResponse().getStatus()).isEqualTo(200);
        return OBJECT_MAPPER.readTree(mvcResult.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private boolean assertAnswerContains(JsonNode responseJson, List<String> expectedAnswerParts) {
        String answer = responseJson.path("answer").asText("");
        for (String expectedAnswerPart : expectedAnswerParts) {
            if (!answer.contains(expectedAnswerPart)) {
                return false;
            }
        }
        return true;
    }

    private void truncateKnowledgeTables() {
        jdbcTemplate.execute("TRUNCATE TABLE lattice.pending_queries");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.contributions");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.source_files CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.articles CASCADE");
    }

    private record RegressionCase(
            String question,
            List<String> expectedAnswerParts,
            String expectedQueryType,
            Integer expectedRowNumber
    ) {
    }
}
