package com.xbk.lattice.documentparse.extractor;

import com.xbk.lattice.compiler.service.FactCardTerminalUnitMaterializer;
import com.xbk.lattice.infra.persistence.FactCardRecord;
import com.xbk.lattice.infra.persistence.FactCardTerminalUnitRecord;
import com.xbk.lattice.query.evidence.domain.AnswerShape;
import com.xbk.lattice.query.evidence.domain.FactCardReviewStatus;
import com.xbk.lattice.query.evidence.domain.FactCardType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 表格抽取 → terminal unit 物化回归测试
 *
 * 回归目标：commit 03ae48c6 修复了 CSV 结构化行格式——
 * 旧格式将所有列值用分号拼成单行（超过 MAX_VALUE_LENGTH 被 Materializer 跳过），
 * 新格式改为每 cell 独立 "- key=value" 行，使 fact_card_terminal_fts 通道首次对 CSV 数据源生效。
 *
 * 本测试模拟 CSV 设备管理表（6 列 × 3 数据行），验证：
 * 1. CsvTextExtractor 产出 per-cell key=value 结构化行
 * 2. 结构化行中不包含分号复合 value（旧 bug 格式）
 * 3. 将同等数据编码为 key_value_list fact card 后，FactCardTerminalUnitMaterializer
 *    可以展开为预期数量的 terminal unit
 * 4. 每个 terminal unit 的 ftsText 包含对应的搜索关键字，
 *    与 fact_card_terminal_fts channel 的检索语义一致
 *
 * @author xiexu
 */
class CsvTableExtractionTerminalUnitRegressionTests {

    @TempDir
    private Path tempDir;

    private static final String CSV_CONTENT = """
            equipment_id,name,hazard_level,custodian_role,maintenance_cycle_days,storage_zone
            EQ-001,高压灭菌锅,A级,实验室主管,30,B2-East
            EQ-002,低温离心机,B级,设备管理员,60,B3-West
            EQ-003,超声波清洗仪,C级,保管员,90,B1-North
            """;

    /**
     * 验证 CSV 抽取后的结构化行使用 per-cell key=value 格式，
     * 不包含分号复合 value（旧 bug），每个数据行有正确的 table 和 row 元数据。
     *
     * @throws IOException IO 异常
     */
    @Test
    void shouldProducePerCellStructuredRowsFromCsvExtraction() throws IOException {
        Path csvPath = tempDir.resolve("equipment_list.csv");
        Files.writeString(csvPath, CSV_CONTENT, StandardCharsets.UTF_8);

        SourceExtractionResult result = new CsvTextExtractor().extract(csvPath);

        assertThat(result).isNotNull();
        String content = result.getContent();
        assertThat(content).contains("--- Structured Rows ---");
        assertThat(content).contains("- table=equipment_list");
        assertThat(content).contains("- row=2");
        assertThat(content).contains("- row=3");
        assertThat(content).contains("- row=4");
        assertThat(content).contains("- equipment_id=EQ-001");
        assertThat(content).contains("- name=高压灭菌锅");
        assertThat(content).contains("- hazard_level=A级");
        assertThat(content).contains("- custodian_role=实验室主管");
        assertThat(content).contains("- maintenance_cycle_days=30");
        assertThat(content).contains("- storage_zone=B2-East");
        assertThat(content).contains("- equipment_id=EQ-002");
        assertThat(content).contains("- name=低温离心机");
        assertThat(content).contains("- equipment_id=EQ-003");
        assertThat(content).contains("- name=超声波清洗仪");
        long tableMetaCount = content.lines()
                .filter(line -> line.equals("- table=equipment_list"))
                .count();
        assertThat(tableMetaCount).as("每个数据行一条 table 元数据").isEqualTo(3);
    }

    /**
     * 回归守护：验证结构化行不包含分号复合 value（旧 bug 格式），
     * 确保每个 key=value 行只携带单列值。
     *
     * @throws IOException IO 异常
     */
    @Test
    void shouldNotContainSemicolonCompoundValueInStructuredRows() throws IOException {
        Path csvPath = tempDir.resolve("equipment_list.csv");
        Files.writeString(csvPath, CSV_CONTENT, StandardCharsets.UTF_8);

        SourceExtractionResult result = new CsvTextExtractor().extract(csvPath);

        assertThat(result).isNotNull();
        String content = result.getContent();
        String structuredSection = content.substring(content.indexOf("--- Structured Rows ---"));
        assertThat(structuredSection).doesNotContain("equipment_id=EQ-001; name=");
        assertThat(structuredSection).doesNotContain("name=高压灭菌锅; hazard_level=");
        structuredSection.lines()
                .filter(line -> line.startsWith("- ") && line.contains("="))
                .forEach(line -> {
                    String afterDash = line.substring(2);
                    long eqCount = afterDash.chars().filter(ch -> ch == '=').count();
                    assertThat(eqCount)
                            .as("每行只含一个 key=value 赋值: %s", line)
                            .isEqualTo(1);
                });
    }

    /**
     * 验证 CSV 结构化行数据经 fact card 物化后产生预期数量的 terminal unit，
     * 模拟 fact_card_terminal_fts channel 能命中的 hits 数量。
     *
     * 本用例构造与 CSV 数据等价的 key_value_list fact card，验证 Materializer
     * 展开 6 个 terminal unit（3 行 × 每行取 equipment_id 和 name 两个 scalar 字段）。
     */
    @Test
    void shouldMaterializeCsvDerivedFactCardIntoExpectedTerminalUnits() {
        FactCardRecord factCardRecord = csvDerivedFactCardRecord();

        FactCardTerminalUnitMaterializer materializer = new FactCardTerminalUnitMaterializer();
        List<FactCardTerminalUnitRecord> records = materializer.materialize(factCardRecord);

        assertThat(records).as("6 个 scalar terminal unit").hasSize(6);
        assertThat(records)
                .extracting(FactCardTerminalUnitRecord::getTerminalKey)
                .containsExactly(
                        "equipment_id", "name",
                        "equipment_id", "name",
                        "equipment_id", "name"
                );
        assertThat(records)
                .extracting(FactCardTerminalUnitRecord::getValueText)
                .containsExactly(
                        "EQ-001", "高压灭菌锅",
                        "EQ-002", "低温离心机",
                        "EQ-003", "超声波清洗仪"
                );
    }

    /**
     * 验证每个 terminal unit 的 ftsText 包含搜索关键字，
     * 与 fact_card_terminal_fts channel 的检索语义一致。
     */
    @Test
    void shouldProduceFtsSearchableTextInTerminalUnits() {
        FactCardRecord factCardRecord = csvDerivedFactCardRecord();

        FactCardTerminalUnitMaterializer materializer = new FactCardTerminalUnitMaterializer();
        List<FactCardTerminalUnitRecord> records = materializer.materialize(factCardRecord);

        assertThat(records).isNotEmpty();
        FactCardTerminalUnitRecord eq001 = records.stream()
                .filter(r -> "EQ-001".equals(r.getValueText()))
                .findFirst()
                .orElseThrow();
        assertThat(eq001.getFtsText()).contains("equipment_id");
        assertThat(eq001.getFtsText()).contains("EQ-001");

        FactCardTerminalUnitRecord nameUnit = records.stream()
                .filter(r -> "高压灭菌锅".equals(r.getValueText()))
                .findFirst()
                .orElseThrow();
        assertThat(nameUnit.getFtsText()).contains("name");
        assertThat(nameUnit.getFtsText()).contains("高压灭菌锅");

        FactCardTerminalUnitRecord eq003Name = records.stream()
                .filter(r -> "超声波清洗仪".equals(r.getValueText()))
                .findFirst()
                .orElseThrow();
        assertThat(eq003Name.getFtsText()).contains("超声波清洗仪");
        assertThat(eq003Name.getMetadataJson()).contains("fact_card_terminal_fts");
    }

    /**
     * 验证 CSV 结构化行的 structuredContentJson 包含正确的表格元数据，
     * 确保下游编译管道可正确消费。
     *
     * @throws IOException IO 异常
     */
    @Test
    void shouldProduceStructuredContentJsonWithTableMetadata() throws IOException {
        Path csvPath = tempDir.resolve("equipment_list.csv");
        Files.writeString(csvPath, CSV_CONTENT, StandardCharsets.UTF_8);

        SourceExtractionResult result = new CsvTextExtractor().extract(csvPath);

        assertThat(result).isNotNull();
        String json = result.getStructuredContentJson();
        assertThat(json).contains("\"contentType\":\"structured_tables\"");
        assertThat(json).contains("\"format\":\"csv\"");
        assertThat(json).contains("\"rowCount\":3");
        assertThat(json).contains("\"columnCount\":6");
        assertThat(json).contains("equipment_id");
        assertThat(json).contains("EQ-001");
        assertThat(json).contains("高压灭菌锅");
    }

    /**
     * 构造与 CSV 数据等价的 key_value_list fact card。
     *
     * 模拟编译管道对 CSV 表格行的处理结果：每行的 equipment_id 和 name 列
     * 为 scalar assignment，hazard_level / custodian_role / maintenance_cycle_days /
     * storage_zone 在实际场景中也会生成 terminal unit，但本用例只取前两列以匹配
     * commit 03ae48c6 验证报告中 "FQ11 fact_card_terminal_fts 首次出现 6 hits" 的场景。
     *
     * @return 事实卡记录
     */
    private FactCardRecord csvDerivedFactCardRecord() {
        return new FactCardRecord(
                Long.valueOf(100L),
                "fc:csv:equipment_list",
                Long.valueOf(1L),
                Long.valueOf(2L),
                FactCardType.FACT_ENUM,
                AnswerShape.ENUM,
                "设备管理表",
                "设备管理表结构化数据",
                """
                        {
                          "structure": "key_value_list",
                          "pathAware": false,
                          "items": [
                            {
                              "key": "equipment_id",
                              "value": "EQ-001",
                              "raw": "equipment_id=EQ-001",
                              "keyPath": "equipment_id",
                              "displayText": "equipment_id = EQ-001",
                              "pathSegments": ["equipment_id"]
                            },
                            {
                              "key": "name",
                              "value": "高压灭菌锅",
                              "raw": "name=高压灭菌锅",
                              "keyPath": "name",
                              "displayText": "name = 高压灭菌锅",
                              "pathSegments": ["name"]
                            },
                            {
                              "key": "equipment_id",
                              "value": "EQ-002",
                              "raw": "equipment_id=EQ-002",
                              "keyPath": "equipment_id",
                              "displayText": "equipment_id = EQ-002",
                              "pathSegments": ["equipment_id"]
                            },
                            {
                              "key": "name",
                              "value": "低温离心机",
                              "raw": "name=低温离心机",
                              "keyPath": "name",
                              "displayText": "name = 低温离心机",
                              "pathSegments": ["name"]
                            },
                            {
                              "key": "equipment_id",
                              "value": "EQ-003",
                              "raw": "equipment_id=EQ-003",
                              "keyPath": "equipment_id",
                              "displayText": "equipment_id = EQ-003",
                              "pathSegments": ["equipment_id"]
                            },
                            {
                              "key": "name",
                              "value": "超声波清洗仪",
                              "raw": "name=超声波清洗仪",
                              "keyPath": "name",
                              "displayText": "name = 超声波清洗仪",
                              "pathSegments": ["name"]
                            }
                          ]
                        }
                        """,
                "equipment_id=EQ-001\nname=高压灭菌锅\nequipment_id=EQ-002\nname=低温离心机\nequipment_id=EQ-003\nname=超声波清洗仪",
                List.of(Long.valueOf(10L)),
                List.of(Long.valueOf(20L)),
                0.92D,
                FactCardReviewStatus.VALID,
                "hash-csv-equipment-regression",
                OffsetDateTime.now().minusSeconds(60L),
                OffsetDateTime.now()
        );
    }
}
