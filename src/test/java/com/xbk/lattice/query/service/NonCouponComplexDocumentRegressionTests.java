package com.xbk.lattice.query.service;

import com.xbk.lattice.compiler.service.FactCardGenerationService;
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
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 非卡券复杂文档回归测试
 *
 * 职责：用通用复杂文档样本验证结构化证据生成、召回与覆盖校验闭环
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
class NonCouponComplexDocumentRegressionTests {

    private final JdbcTemplate jdbcTemplate;

    private final SourceFileJdbcRepository sourceFileJdbcRepository;

    private final SourceFileChunkJdbcRepository sourceFileChunkJdbcRepository;

    private final FactCardGenerationService factCardGenerationService;

    private final FactCardFtsSearchService factCardFtsSearchService;

    private final AnswerCoverageCheckService answerCoverageCheckService;

    /**
     * 创建非卡券复杂文档回归测试。
     *
     * @param jdbcTemplate JDBC 操作模板
     * @param sourceFileJdbcRepository source 文件仓储
     * @param sourceFileChunkJdbcRepository source chunk 仓储
     * @param factCardGenerationService 事实卡生成服务
     * @param factCardFtsSearchService 事实卡全文检索服务
     * @param answerCoverageCheckService 答案覆盖校验服务
     */
    @Autowired
    NonCouponComplexDocumentRegressionTests(JdbcTemplate jdbcTemplate,
                                            SourceFileJdbcRepository sourceFileJdbcRepository,
                                            SourceFileChunkJdbcRepository sourceFileChunkJdbcRepository,
                                            FactCardGenerationService factCardGenerationService,
                                            FactCardFtsSearchService factCardFtsSearchService,
                                            AnswerCoverageCheckService answerCoverageCheckService) {
        this.jdbcTemplate = jdbcTemplate;
        this.sourceFileJdbcRepository = sourceFileJdbcRepository;
        this.sourceFileChunkJdbcRepository = sourceFileChunkJdbcRepository;
        this.factCardGenerationService = factCardGenerationService;
        this.factCardFtsSearchService = factCardFtsSearchService;
        this.answerCoverageCheckService = answerCoverageCheckService;
    }

    /**
     * 验证两份非卡券复杂文档可形成结构化证据闭环。
     */
    @Test
    void shouldPassNonCouponComplexDocumentRegression() {
        truncateKnowledgeTables();
        SourceFileRecord operationsHandbook = saveSourceFileWithChunks(
                "samples/non-coupon/operations-handbook.md",
                List.of(
                        """
                                # Operations Readiness
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
                                exporter: 已完成
                                """
                )
        );
        SourceFileRecord dataQualityPlaybook = saveSourceFileWithChunks(
                "samples/non-coupon/data-quality-playbook.md",
                List.of(
                        """
                                # Data Quality Playbook
                                适用范围：所有批处理任务
                                必须保留原始记录引用
                                禁止覆盖校验失败的输出

                                校验流程：
                                1. ingest records
                                2. normalize fields
                                """,
                        """
                                3. compare totals
                                4. publish quality report

                                检查项：
                                - schema alignment
                                - duplicate detection
                                - total reconciliation
                                """,
                        """
                                | check | before | after |
                                |---|---|---|
                                | schema | loose | strict |
                                | totals | manual | automated |
                                """,
                        """
                                schema alignment: 已确认
                                duplicate detection: 待确认
                                total reconciliation: 已完成
                                """
                )
        );

        List<FactCardRecord> operationsCards = factCardGenerationService.rebuildForSourceFile(operationsHandbook.getId());
        List<FactCardRecord> dataQualityCards = factCardGenerationService.rebuildForSourceFile(dataQualityPlaybook.getId());

        assertDocumentCardCoverage(operationsCards);
        assertDocumentCardCoverage(dataQualityCards);
        assertSourceReferencesPresent(operationsCards);
        assertSourceReferencesPresent(dataQualityCards);
        assertSearchCanFindFactCard("gateway automated", "operations-handbook.md");
        assertSearchCanFindFactCard("schema strict", "data-quality-playbook.md");
        assertCoveredAnswer(operationsCards);
        assertPartialAnswerDetected(dataQualityCards);
    }

    /**
     * 断言文档生成了全部结构化证据类型。
     *
     * @param factCards 事实证据卡列表
     */
    private void assertDocumentCardCoverage(List<FactCardRecord> factCards) {
        assertThat(findAll(factCards, FactCardType.FACT_ENUM)).isNotEmpty();
        assertThat(findAll(factCards, FactCardType.FACT_COMPARE)).isNotEmpty();
        assertThat(findAll(factCards, FactCardType.FACT_SEQUENCE)).isNotEmpty();
        assertThat(findAll(factCards, FactCardType.FACT_STATUS)).isNotEmpty();
        assertThat(findAll(factCards, FactCardType.FACT_POLICY)).isNotEmpty();
    }

    /**
     * 断言所有事实卡均带 source chunk 回指且非冲突。
     *
     * @param factCards 事实证据卡列表
     */
    private void assertSourceReferencesPresent(List<FactCardRecord> factCards) {
        assertThat(factCards).isNotEmpty();
        for (FactCardRecord factCard : factCards) {
            assertThat(factCard.getSourceChunkIds()).isNotEmpty();
            assertThat(factCard.getEvidenceText()).isNotBlank();
            assertThat(factCard.getReviewStatus()).isNotEqualTo(FactCardReviewStatus.CONFLICT);
        }
    }

    /**
     * 断言事实卡可被 lexical 检索召回。
     *
     * @param question 查询问题
     * @param expectedSourcePath 期望来源路径片段
     */
    private void assertSearchCanFindFactCard(String question, String expectedSourcePath) {
        List<QueryArticleHit> hits = factCardFtsSearchService.search(question, 5);

        assertThat(hits).anySatisfy(hit -> {
            assertThat(hit.getEvidenceType()).isEqualTo(QueryEvidenceType.FACT_CARD);
            assertThat(hit.getSourcePaths()).anyMatch(sourcePath -> sourcePath.contains(expectedSourcePath));
        });
    }

    /**
     * 断言完整答案可通过覆盖校验。
     *
     * @param factCards 事实证据卡列表
     */
    private void assertCoveredAnswer(List<FactCardRecord> factCards) {
        FactCardRecord sequenceCard = findFirst(factCards, FactCardType.FACT_SEQUENCE);
        AnswerCoverageCheckResult result = answerCoverageCheckService.check(
                "发布步骤有哪些",
                AnswerShape.SEQUENCE,
                List.of(sequenceCard),
                "步骤依次是 collect change list、validate rollback path、notify operations team、archive release notes。"
        );

        assertThat(result.getCoverageStatus()).isEqualTo(AnswerCoverageStatus.COVERED);
    }

    /**
     * 断言漏答会被覆盖校验识别。
     *
     * @param factCards 事实证据卡列表
     */
    private void assertPartialAnswerDetected(List<FactCardRecord> factCards) {
        FactCardRecord enumCard = findFirst(factCards, FactCardType.FACT_ENUM);
        AnswerCoverageCheckResult result = answerCoverageCheckService.check(
                "检查项有哪些",
                AnswerShape.ENUM,
                List.of(enumCard),
                "检查项包括 schema alignment 和 duplicate detection。"
        );

        assertThat(result.getCoverageStatus()).isEqualTo(AnswerCoverageStatus.PARTIAL);
        assertThat(result.getMissingItems()).anyMatch(item -> item.contains("total reconciliation"));
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
        sourceFileChunkJdbcRepository.replaceChunks(sourceFileRecord.getId(), filePath, chunkRecords);
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
     * 清理知识表测试数据。
     */
    private void truncateKnowledgeTables() {
        jdbcTemplate.execute("TRUNCATE TABLE lattice.fact_cards CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.source_file_chunks");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.source_files CASCADE");
    }
}
