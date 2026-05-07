package com.xbk.lattice.compiler.service;

import com.xbk.lattice.infra.persistence.FactCardJdbcRepository;
import com.xbk.lattice.infra.persistence.FactCardRecord;
import com.xbk.lattice.infra.persistence.SourceFileChunkJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileChunkRecord;
import com.xbk.lattice.infra.persistence.SourceFileJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileRecord;
import com.xbk.lattice.query.evidence.domain.AnswerShape;
import com.xbk.lattice.query.evidence.domain.FactCardReviewStatus;
import com.xbk.lattice.query.evidence.domain.FactCardType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FactCardGenerationService 测试
 *
 * 职责：验证无 LLM 场景下从通用 source chunk 生成结构化事实证据卡
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
class FactCardGenerationServiceTests {

    @Autowired
    private SourceFileJdbcRepository sourceFileJdbcRepository;

    @Autowired
    private SourceFileChunkJdbcRepository sourceFileChunkJdbcRepository;

    @Autowired
    private FactCardJdbcRepository factCardJdbcRepository;

    @Autowired
    private FactCardGenerationService factCardGenerationService;

    /**
     * 验证 bullet 列表可生成枚举卡。
     */
    @Test
    void shouldGenerateEnumCardFromBulletList() {
        SourceFileRecord sourceFileRecord = saveSourceFileWithChunk(
                "samples/bullet-list.md",
                """
                        - alpha item
                        - beta item
                        - gamma item
                        """
        );

        List<FactCardRecord> records = factCardGenerationService.rebuildForSourceFile(sourceFileRecord.getId());

        FactCardRecord enumCard = findFirst(records, FactCardType.FACT_ENUM);
        assertThat(enumCard).isNotNull();
        assertThat(enumCard.getAnswerShape()).isEqualTo(AnswerShape.ENUM);
        assertThat(enumCard.getItemsJson()).contains("bullet_list");
        assertThat(enumCard.getItemsJson()).contains("alpha item");
        assertThat(enumCard.getEvidenceText()).contains("- alpha item");
        assertThat(enumCard.getSourceChunkIds()).hasSize(1);
        assertThat(enumCard.getReviewStatus()).isEqualTo(FactCardReviewStatus.VALID);
        assertThat(factCardJdbcRepository.findByCardId(enumCard.getCardId())).isPresent();
    }

    /**
     * 验证星号 bullet 列表也可生成枚举卡。
     */
    @Test
    void shouldGenerateEnumCardFromAsteriskList() {
        SourceFileRecord sourceFileRecord = saveSourceFileWithChunk(
                "samples/asterisk-list.md",
                """
                        * first option
                        * second option
                        * third option
                        """
        );

        List<FactCardRecord> records = factCardGenerationService.rebuildForSourceFile(sourceFileRecord.getId());

        FactCardRecord enumCard = findFirst(records, FactCardType.FACT_ENUM);
        assertThat(enumCard).isNotNull();
        assertThat(enumCard.getItemsJson()).contains("first option");
        assertThat(enumCard.getItemsJson()).contains("third option");
    }

    /**
     * 验证普通键值结构可生成枚举卡。
     */
    @Test
    void shouldGenerateEnumCardFromKeyValueLines() {
        SourceFileRecord sourceFileRecord = saveSourceFileWithChunk(
                "samples/key-value.md",
                """
                        alpha: enabled
                        beta: disabled
                        gamma: pending
                        """
        );

        List<FactCardRecord> records = factCardGenerationService.rebuildForSourceFile(sourceFileRecord.getId());

        FactCardRecord enumCard = findFirst(records, FactCardType.FACT_ENUM);
        assertThat(enumCard).isNotNull();
        assertThat(enumCard.getItemsJson()).contains("key_value_list");
        assertThat(enumCard.getItemsJson()).contains("alpha");
        assertThat(enumCard.getItemsJson()).contains("enabled");
    }

    /**
     * 验证列表内键值结构可生成枚举卡。
     */
    @Test
    void shouldGenerateEnumCardFromBulletKeyValueLines() {
        SourceFileRecord sourceFileRecord = saveSourceFileWithChunk(
                "samples/bullet-key-value.md",
                """
                        - name: alpha
                        - mode: active
                        - owner: team
                        """
        );

        List<FactCardRecord> records = factCardGenerationService.rebuildForSourceFile(sourceFileRecord.getId());

        List<FactCardRecord> enumCards = findAll(records, FactCardType.FACT_ENUM);
        assertThat(enumCards).isNotEmpty();
        assertThat(joinItemsJson(enumCards)).contains("key_value_list");
        assertThat(joinItemsJson(enumCards)).contains("owner");
    }

    /**
     * 验证 Markdown 表格可生成枚举卡。
     */
    @Test
    void shouldGenerateEnumCardFromMarkdownTable() {
        SourceFileRecord sourceFileRecord = saveSourceFileWithChunk(
                "samples/table-enum.md",
                """
                        | item | value |
                        |---|---|
                        | alpha | 10 |
                        | beta | 20 |
                        """
        );

        List<FactCardRecord> records = factCardGenerationService.rebuildForSourceFile(sourceFileRecord.getId());

        FactCardRecord enumCard = findFirst(records, FactCardType.FACT_ENUM);
        assertThat(enumCard).isNotNull();
        assertThat(enumCard.getItemsJson()).contains("markdown_table");
        assertThat(enumCard.getItemsJson()).contains("alpha");
        assertThat(enumCard.getEvidenceText()).contains("| item | value |");
    }

    /**
     * 验证完整表格可生成对照卡。
     */
    @Test
    void shouldGenerateCompareCardFromMarkdownTable() {
        SourceFileRecord sourceFileRecord = saveSourceFileWithChunk(
                "samples/table-compare.md",
                """
                        | name | before | after |
                        |---|---|---|
                        | alpha | small | large |
                        | beta | off | on |
                        """
        );

        List<FactCardRecord> records = factCardGenerationService.rebuildForSourceFile(sourceFileRecord.getId());

        FactCardRecord compareCard = findFirst(records, FactCardType.FACT_COMPARE);
        assertThat(compareCard).isNotNull();
        assertThat(compareCard.getAnswerShape()).isEqualTo(AnswerShape.COMPARE);
        assertThat(compareCard.getItemsJson()).contains("markdown_compare_table");
        assertThat(compareCard.getItemsJson()).contains("before");
        assertThat(compareCard.getItemsJson()).contains("after");
        assertThat(compareCard.getReviewStatus()).isEqualTo(FactCardReviewStatus.VALID);
    }

    /**
     * 验证缺少对照侧的表格会进入 incomplete。
     */
    @Test
    void shouldMarkIncompleteCompareCardWhenTableSideMissing() {
        SourceFileRecord sourceFileRecord = saveSourceFileWithChunk(
                "samples/table-incomplete.md",
                """
                        | name | left | right |
                        |---|---|---|
                        | alpha | enabled |  |
                        | beta |  | disabled |
                        """
        );

        List<FactCardRecord> records = factCardGenerationService.rebuildForSourceFile(sourceFileRecord.getId());

        FactCardRecord compareCard = findFirst(records, FactCardType.FACT_COMPARE);
        assertThat(compareCard).isNotNull();
        assertThat(compareCard.getReviewStatus()).isEqualTo(FactCardReviewStatus.INCOMPLETE);
    }

    /**
     * 验证跨 chunk 的 Markdown 表格会合并为同一张结构化卡。
     */
    @Test
    void shouldMergeAdjacentChunksForSplitMarkdownTable() {
        SourceFileRecord sourceFileRecord = saveSourceFileWithChunks(
                "samples/table-split.md",
                List.of(
                        """
                                | name | before | after |
                                |---|---|---|
                                | alpha | small | large |
                                """,
                        """
                                | beta | off | on |
                                | gamma | cold | warm |
                                """
                )
        );

        List<FactCardRecord> records = factCardGenerationService.rebuildForSourceFile(sourceFileRecord.getId());

        FactCardRecord compareCard = findFirst(records, FactCardType.FACT_COMPARE);
        assertThat(compareCard).isNotNull();
        assertThat(compareCard.getItemsJson()).contains("alpha");
        assertThat(compareCard.getItemsJson()).contains("gamma");
        assertThat(compareCard.getSourceChunkIds()).hasSize(2);
        assertThat(compareCard.getReviewStatus()).isEqualTo(FactCardReviewStatus.VALID);
    }

    /**
     * 验证阿拉伯数字有序列表可生成顺序卡。
     */
    @Test
    void shouldGenerateSequenceCardFromNumberedList() {
        SourceFileRecord sourceFileRecord = saveSourceFileWithChunk(
                "samples/numbered-sequence.md",
                """
                        1. collect input
                        2. validate input
                        3. publish result
                        """
        );

        List<FactCardRecord> records = factCardGenerationService.rebuildForSourceFile(sourceFileRecord.getId());

        FactCardRecord sequenceCard = findFirst(records, FactCardType.FACT_SEQUENCE);
        assertThat(sequenceCard).isNotNull();
        assertThat(sequenceCard.getAnswerShape()).isEqualTo(AnswerShape.SEQUENCE);
        assertThat(sequenceCard.getItemsJson()).contains("ordered_sequence");
        assertThat(sequenceCard.getItemsJson()).contains("\"position\":1");
        assertThat(sequenceCard.getItemsJson()).contains("publish result");
    }

    /**
     * 验证中文序号列表可生成顺序卡。
     */
    @Test
    void shouldGenerateSequenceCardFromChineseNumberedList() {
        SourceFileRecord sourceFileRecord = saveSourceFileWithChunk(
                "samples/chinese-sequence.md",
                """
                        一、准备输入
                        二、校验输入
                        三、发布结果
                        """
        );

        List<FactCardRecord> records = factCardGenerationService.rebuildForSourceFile(sourceFileRecord.getId());

        FactCardRecord sequenceCard = findFirst(records, FactCardType.FACT_SEQUENCE);
        assertThat(sequenceCard).isNotNull();
        assertThat(sequenceCard.getItemsJson()).contains("\"order\":\"一\"");
        assertThat(sequenceCard.getItemsJson()).contains("发布结果");
    }

    /**
     * 验证跨 chunk 的连续编号会合并为同一张顺序卡。
     */
    @Test
    void shouldMergeAdjacentChunksForContinuousOrderedList() {
        SourceFileRecord sourceFileRecord = saveSourceFileWithChunks(
                "samples/sequence-split.md",
                List.of(
                        """
                                1. collect input
                                2. validate input
                                """,
                        """
                                3. publish result
                                4. archive result
                                """
                )
        );

        List<FactCardRecord> records = factCardGenerationService.rebuildForSourceFile(sourceFileRecord.getId());

        List<FactCardRecord> sequenceCards = findAll(records, FactCardType.FACT_SEQUENCE);
        assertThat(sequenceCards).hasSize(1);
        FactCardRecord sequenceCard = sequenceCards.get(0);
        assertThat(sequenceCard.getItemsJson()).contains("collect input");
        assertThat(sequenceCard.getItemsJson()).contains("archive result");
        assertThat(sequenceCard.getSourceChunkIds()).hasSize(2);
        assertThat(sequenceCard.getReviewStatus()).isEqualTo(FactCardReviewStatus.VALID);
    }

    /**
     * 验证通用状态分组可生成状态卡。
     */
    @Test
    void shouldGenerateStatusCardFromStatusLines() {
        SourceFileRecord sourceFileRecord = saveSourceFileWithChunk(
                "samples/status-lines.md",
                """
                        alpha: 已确认
                        beta: 待确认
                        gamma: 无流量
                        """
        );

        List<FactCardRecord> records = factCardGenerationService.rebuildForSourceFile(sourceFileRecord.getId());

        FactCardRecord statusCard = findFirst(records, FactCardType.FACT_STATUS);
        assertThat(statusCard).isNotNull();
        assertThat(statusCard.getAnswerShape()).isEqualTo(AnswerShape.STATUS);
        assertThat(statusCard.getItemsJson()).contains("status_group");
        assertThat(statusCard.getItemsJson()).contains("alpha");
        assertThat(statusCard.getItemsJson()).contains("已确认");
        assertThat(statusCard.getReviewStatus()).isEqualTo(FactCardReviewStatus.VALID);
    }

    /**
     * 验证同一事项命中互斥状态时进入 conflict。
     */
    @Test
    void shouldMarkConflictWhenSameSubjectHasMutuallyExclusiveStatuses() {
        SourceFileRecord sourceFileRecord = saveSourceFileWithChunk(
                "samples/status-conflict.md",
                """
                        alpha: 已确认
                        alpha: 待确认
                        beta: 已完成
                        """
        );

        List<FactCardRecord> records = factCardGenerationService.rebuildForSourceFile(sourceFileRecord.getId());

        FactCardRecord statusCard = findFirst(records, FactCardType.FACT_STATUS);
        assertThat(statusCard).isNotNull();
        assertThat(statusCard.getReviewStatus()).isEqualTo(FactCardReviewStatus.CONFLICT);
        assertThat(statusCard.getItemsJson()).contains("conflictSubjects");
        assertThat(statusCard.getItemsJson()).contains("alpha");
    }

    /**
     * 验证带适用范围的通用规则可生成有效规则卡。
     */
    @Test
    void shouldGeneratePolicyCardWithScope() {
        SourceFileRecord sourceFileRecord = saveSourceFileWithChunk(
                "samples/policy-with-scope.md",
                """
                        适用范围：所有输入文件
                        必须保留原文引用
                        不得删除证据行
                        """
        );

        List<FactCardRecord> records = factCardGenerationService.rebuildForSourceFile(sourceFileRecord.getId());

        FactCardRecord policyCard = findFirst(records, FactCardType.FACT_POLICY);
        assertThat(policyCard).isNotNull();
        assertThat(policyCard.getAnswerShape()).isEqualTo(AnswerShape.POLICY);
        assertThat(policyCard.getItemsJson()).contains("policy_constraints");
        assertThat(policyCard.getItemsJson()).contains("适用范围");
        assertThat(policyCard.getItemsJson()).contains("必须保留原文引用");
        assertThat(policyCard.getReviewStatus()).isEqualTo(FactCardReviewStatus.VALID);
    }

    /**
     * 验证标题与列表分离到相邻 chunk 时仍能合并生成结构化卡。
     */
    @Test
    void shouldMergeAdjacentChunksWhenTitleIsSeparatedFromList() {
        SourceFileRecord sourceFileRecord = saveSourceFileWithChunks(
                "samples/title-list-split.md",
                List.of(
                        "处理步骤：",
                        """
                                1. collect input
                                2. validate input
                                3. publish result
                                """
                )
        );

        List<FactCardRecord> records = factCardGenerationService.rebuildForSourceFile(sourceFileRecord.getId());

        FactCardRecord sequenceCard = findFirst(records, FactCardType.FACT_SEQUENCE);
        assertThat(sequenceCard).isNotNull();
        assertThat(sequenceCard.getEvidenceText()).contains("1. collect input");
        assertThat(sequenceCard.getSourceChunkIds()).hasSize(2);
    }

    /**
     * 验证缺少适用范围的规则卡进入 incomplete。
     */
    @Test
    void shouldMarkPolicyCardIncompleteWhenScopeMissing() {
        SourceFileRecord sourceFileRecord = saveSourceFileWithChunk(
                "samples/policy-missing-scope.md",
                """
                        必须保留原文引用
                        禁止删除证据行
                        """
        );

        List<FactCardRecord> records = factCardGenerationService.rebuildForSourceFile(sourceFileRecord.getId());

        FactCardRecord policyCard = findFirst(records, FactCardType.FACT_POLICY);
        assertThat(policyCard).isNotNull();
        assertThat(policyCard.getReviewStatus()).isEqualTo(FactCardReviewStatus.INCOMPLETE);
        assertThat(policyCard.getItemsJson()).contains("constraints");
    }

    /**
     * 验证贴近真实复杂文档的 source chunk 小样本可生成各类结构化卡。
     */
    @Test
    void shouldGenerateCardsFromRealisticComplexSourceChunks() {
        SourceFileRecord sourceFileRecord = saveSourceFileWithChunks(
                "samples/complex-handbook.md",
                List.of(
                        """
                                # Release Readiness Notes
                                适用范围：所有生产发布
                                必须完成风险复核
                                不得跳过回滚演练

                                发布步骤：
                                1. collect change list
                                2. validate rollback path
                                """,
                        """
                                3. notify operations team
                                4. archive release notes

                                组件清单：
                                - gateway service
                                - scheduler worker
                                - audit exporter
                                """,
                        """
                                | module | current | target |
                                |---|---|---|
                                | gateway | manual | automated |
                                | worker | disabled | enabled |
                                """,
                        """
                                gateway: 已确认
                                worker: 待确认
                                exporter: 无流量
                                """
                )
        );

        List<FactCardRecord> records = factCardGenerationService.rebuildForSourceFile(sourceFileRecord.getId());

        assertThat(findAll(records, FactCardType.FACT_ENUM)).isNotEmpty();
        assertThat(findFirst(records, FactCardType.FACT_COMPARE)).isNotNull();
        assertThat(findFirst(records, FactCardType.FACT_SEQUENCE)).isNotNull();
        assertThat(findFirst(records, FactCardType.FACT_STATUS)).isNotNull();
        assertThat(findFirst(records, FactCardType.FACT_POLICY)).isNotNull();
        for (FactCardRecord record : records) {
            assertThat(record.getSourceChunkIds()).isNotEmpty();
        }
        FactCardRecord sequenceCard = findFirst(records, FactCardType.FACT_SEQUENCE);
        assertThat(sequenceCard.getSourceChunkIds()).hasSize(2);
        assertThat(sequenceCard.getItemsJson()).contains("archive release notes");
    }

    /**
     * 验证重复重建同一源文件不会累积重复卡。
     */
    @Test
    void shouldKeepFactCardCountStableWhenRebuildingSameSourceFile() {
        SourceFileRecord sourceFileRecord = saveSourceFileWithChunk(
                "samples/rebuild-stable.md",
                """
                        - alpha item
                        - beta item
                        """
        );

        List<FactCardRecord> firstRecords = factCardGenerationService.rebuildForSourceFile(sourceFileRecord.getId());
        int firstCount = factCardJdbcRepository.findBySourceFileId(sourceFileRecord.getId()).size();
        List<FactCardRecord> secondRecords = factCardGenerationService.rebuildForSourceFile(sourceFileRecord.getId());
        int secondCount = factCardJdbcRepository.findBySourceFileId(sourceFileRecord.getId()).size();

        assertThat(firstRecords).hasSize(1);
        assertThat(secondRecords).hasSize(1);
        assertThat(secondCount).isEqualTo(firstCount);
    }

    /**
     * 验证证据层可单独输出 source 回指与定位质量摘要。
     */
    @Test
    void shouldSummarizeSourceReferenceQuality() {
        SourceFileRecord sourceFileRecord = saveSourceFileWithChunk(
                "samples/summary.md",
                """
                        - alpha item
                        - beta item
                        """
        );

        FactCardGenerationSummary summary = factCardGenerationService.rebuildForSourceFileWithSummary(
                sourceFileRecord.getId()
        );

        assertThat(summary.getGeneratedCount()).isEqualTo(1);
        assertThat(summary.getWithSourceChunkCount()).isEqualTo(1);
        assertThat(summary.getEvidenceLocatedCount()).isEqualTo(1);
        assertThat(summary.getSourceReferenceRate()).isEqualTo(1.0D);
        assertThat(summary.getEvidenceLocatedRate()).isEqualTo(1.0D);
        assertThat(summary.getCardIds()).hasSize(1);
    }

    /**
     * 保存源文件与单个 source chunk。
     *
     * @param filePath 文件路径
     * @param chunkText chunk 文本
     * @return 源文件记录
     */
    private SourceFileRecord saveSourceFileWithChunk(String filePath, String chunkText) {
        return saveSourceFileWithChunks(filePath, List.of(chunkText));
    }

    /**
     * 保存源文件与多个 source chunk。
     *
     * @param filePath 文件路径
     * @param chunkTexts chunk 文本列表
     * @return 源文件记录
     */
    private SourceFileRecord saveSourceFileWithChunks(String filePath, List<String> chunkTexts) {
        String contentText = String.join("\n", chunkTexts);
        SourceFileRecord sourceFileRecord = sourceFileJdbcRepository.upsert(new SourceFileRecord(
                filePath,
                "sample",
                "md",
                contentText.length(),
                contentText,
                "{}",
                true,
                filePath
        ));
        List<SourceFileChunkRecord> chunkRecords = new ArrayList<SourceFileChunkRecord>();
        for (int index = 0; index < chunkTexts.size(); index++) {
            chunkRecords.add(new SourceFileChunkRecord(
                    sourceFileRecord.getId(),
                    filePath,
                    index,
                    chunkTexts.get(index),
                    true
            ));
        }
        sourceFileChunkJdbcRepository.replaceChunks(
                sourceFileRecord.getId(),
                filePath,
                chunkRecords
        );
        factCardJdbcRepository.deleteBySourceFileId(sourceFileRecord.getId());
        return sourceFileRecord;
    }

    /**
     * 查询第一张指定类型事实卡。
     *
     * @param records 事实卡列表
     * @param cardType 事实卡类型
     * @return 事实卡记录
     */
    private FactCardRecord findFirst(List<FactCardRecord> records, FactCardType cardType) {
        for (FactCardRecord record : records) {
            if (record.getCardType() == cardType) {
                return record;
            }
        }
        return null;
    }

    /**
     * 查询全部指定类型事实卡。
     *
     * @param records 事实卡列表
     * @param cardType 事实卡类型
     * @return 事实卡记录
     */
    private List<FactCardRecord> findAll(List<FactCardRecord> records, FactCardType cardType) {
        List<FactCardRecord> filteredRecords = new ArrayList<FactCardRecord>();
        for (FactCardRecord record : records) {
            if (record.getCardType() == cardType) {
                filteredRecords.add(record);
            }
        }
        return filteredRecords;
    }

    /**
     * 拼接 itemsJson 便于断言。
     *
     * @param records 事实卡列表
     * @return 拼接后的 JSON 文本
     */
    private String joinItemsJson(List<FactCardRecord> records) {
        StringBuilder builder = new StringBuilder();
        for (FactCardRecord record : records) {
            builder.append(record.getItemsJson()).append('\n');
        }
        return builder.toString();
    }
}
