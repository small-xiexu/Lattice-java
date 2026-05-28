package com.xbk.lattice.infra.persistence;

import com.xbk.lattice.compiler.service.FactCardTerminalUnitMaterializer;
import com.xbk.lattice.query.evidence.domain.AnswerShape;
import com.xbk.lattice.query.evidence.domain.FactCardReviewStatus;
import com.xbk.lattice.query.evidence.domain.FactCardType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FactCardTerminalUnitJdbcRepository 测试
 *
 * 职责：验证 terminal unit 持久化、幂等重建、删除与 FTS 检索能力
 *
 * @author xiexu
 */
@SpringBootTest(properties = {
        "spring.ai.openai.api-key=test-openai-key",
        "spring.ai.anthropic.api-key=test-anthropic-key"
})
class FactCardTerminalUnitJdbcRepositoryTests {

    @Autowired
    private SourceFileJdbcRepository sourceFileJdbcRepository;

    @Autowired
    private FactCardJdbcRepository factCardJdbcRepository;

    @Autowired
    private FactCardTerminalUnitJdbcRepository factCardTerminalUnitJdbcRepository;

    private final FactCardTerminalUnitMaterializer materializer = new FactCardTerminalUnitMaterializer();

    /**
     * 验证 terminal unit upsert 以 unit_id 保持幂等。
     */
    @Test
    void shouldUpsertTerminalUnitsIdempotently() {
        assumeTerminalUnitTable();
        resetFixtures();
        List<FactCardTerminalUnitRecord> terminalUnitRecords = materializedTerminalUnits("upsert");

        List<FactCardTerminalUnitRecord> firstSavedRecords =
                factCardTerminalUnitJdbcRepository.upsertAll(terminalUnitRecords);
        List<FactCardTerminalUnitRecord> secondSavedRecords =
                factCardTerminalUnitJdbcRepository.upsertAll(terminalUnitRecords);

        assertThat(firstSavedRecords).hasSize(2);
        assertThat(secondSavedRecords).hasSize(2);
        assertThat(secondSavedRecords.get(0).getId()).isEqualTo(firstSavedRecords.get(0).getId());
        assertThat(factCardTerminalUnitJdbcRepository.countAll()).isEqualTo(2);
        assertThat(factCardTerminalUnitJdbcRepository.findByUnitId(firstSavedRecords.get(0).getUnitId()))
                .isPresent();
    }

    /**
     * 验证可按 fact card 与 source file 删除 terminal units。
     */
    @Test
    void shouldDeleteTerminalUnitsByFactCardAndSourceFile() {
        assumeTerminalUnitTable();
        resetFixtures();
        List<FactCardTerminalUnitRecord> firstRecords = materializedTerminalUnits("delete-card");
        FactCardTerminalUnitRecord firstRecord = firstRecords.get(0);
        factCardTerminalUnitJdbcRepository.upsertAll(firstRecords);

        int deletedByCard = factCardTerminalUnitJdbcRepository.deleteByFactCardId(firstRecord.getFactCardId());

        assertThat(deletedByCard).isEqualTo(2);
        assertThat(factCardTerminalUnitJdbcRepository.findByFactCardId(firstRecord.getFactCardId())).isEmpty();

        List<FactCardTerminalUnitRecord> secondRecords = materializedTerminalUnits("delete-source");
        FactCardTerminalUnitRecord secondRecord = secondRecords.get(0);
        factCardTerminalUnitJdbcRepository.upsertAll(secondRecords);

        int deletedBySource = factCardTerminalUnitJdbcRepository.deleteBySourceFileId(secondRecord.getSourceFileId());

        assertThat(deletedBySource).isEqualTo(2);
        assertThat(factCardTerminalUnitJdbcRepository.findBySourceFileId(secondRecord.getSourceFileId())).isEmpty();
    }

    /**
     * 验证 terminal unit 可通过 FTS 命中，并返回完整 metadata。
     */
    @Test
    void shouldSearchTerminalUnitsByFtsWithCompleteMetadata() {
        assumeTerminalUnitTable();
        resetFixtures();
        List<FactCardTerminalUnitRecord> terminalUnitRecords = materializedTerminalUnits("search");
        List<FactCardTerminalUnitRecord> savedRecords =
                factCardTerminalUnitJdbcRepository.upsertAll(terminalUnitRecords);
        FactCardTerminalUnitRecord expectedRecord = savedRecords.get(0);

        List<LexicalSearchRecord> hits = factCardTerminalUnitJdbcRepository.searchLexical(
                "alpha_limit",
                List.of("alpha_limit"),
                5,
                "simple"
        );

        assertThat(hits).isNotEmpty();
        LexicalSearchRecord hit = hits.get(0);
        assertThat(hit.getItemKey()).isEqualTo(expectedRecord.getTerminalUnitIdentity());
        assertThat(hit.getConceptId()).isEqualTo(expectedRecord.getTerminalUnitIdentity());
        assertThat(hit.getContent()).contains("alpha_limit = 31");
        assertThat(hit.getContent()).doesNotContain("\"items\"");
        assertThat(hit.getMetadataJson()).contains("\"terminalUnitId\"");
        assertThat(hit.getMetadataJson()).contains("\"unitId\"");
        assertThat(hit.getMetadataJson()).contains("\"terminalUnitIdentity\"");
        assertThat(hit.getMetadataJson()).contains("\"factCardId\"");
        assertThat(hit.getMetadataJson()).contains("\"cardId\"");
        assertThat(hit.getMetadataJson()).contains("\"keyPath\"");
        assertThat(hit.getMetadataJson()).contains("\"parentPath\"");
        assertThat(hit.getMetadataJson()).contains("\"terminalKey\"");
        assertThat(hit.getMetadataJson()).contains("\"value\"");
        assertThat(hit.getMetadataJson()).contains("\"valueType\"");
        assertThat(hit.getMetadataJson()).contains("\"displayText\"");
        assertThat(hit.getSourcePaths()).contains("terminal-unit/synthetic-search.md");
    }

    /**
     * 跳过未应用本轮 schema 的当前数据库。
     */
    private void assumeTerminalUnitTable() {
        Assumptions.assumeTrue(
                factCardTerminalUnitJdbcRepository.tableAvailable(),
                "fact_card_terminal_units table is not present in current schema"
        );
    }

    /**
     * 清理本测试写入的事实卡与 terminal unit。
     */
    private void resetFixtures() {
        factCardTerminalUnitJdbcRepository.deleteAll();
        factCardJdbcRepository.deleteAll();
    }

    /**
     * 构造并物化 terminal unit 测试记录。
     *
     * @param suffix 数据后缀
     * @return terminal unit 记录列表
     */
    private List<FactCardTerminalUnitRecord> materializedTerminalUnits(String suffix) {
        SourceFileRecord sourceFileRecord = sourceFileJdbcRepository.upsert(new SourceFileRecord(
                "terminal-unit/synthetic-" + suffix + ".md",
                "Synthetic terminal unit source",
                "md",
                64L,
                "alpha_limit=31\nbeta_mode=enabled",
                "{}",
                true,
                "terminal-unit/synthetic-" + suffix + ".md"
        ));
        FactCardRecord factCardRecord = factCardJdbcRepository.upsert(new FactCardRecord(
                "fc:terminal-repo:" + suffix,
                sourceFileRecord.getSourceId(),
                sourceFileRecord.getId(),
                FactCardType.FACT_ENUM,
                AnswerShape.ENUM,
                "Synthetic terminal fields",
                "Synthetic scalar facts.",
                """
                        {
                          "structure": "key_value_list",
                          "pathAware": true,
                          "items": [
                            {
                              "key": "alpha_limit",
                              "value": "31",
                              "parentPath": "synthetic.settings",
                              "keyPath": "synthetic.settings.alpha_limit",
                              "displayText": "alpha_limit = 31",
                              "pathSegments": ["synthetic", "settings", "alpha_limit"]
                            },
                            {
                              "key": "beta_mode",
                              "value": "enabled",
                              "parentPath": "synthetic.settings",
                              "keyPath": "synthetic.settings.beta_mode",
                              "displayText": "beta_mode = enabled",
                              "pathSegments": ["synthetic", "settings", "beta_mode"]
                            }
                          ]
                        }
                        """,
                "alpha_limit=31\nbeta_mode=enabled",
                List.of(),
                List.of(),
                0.93D,
                FactCardReviewStatus.VALID,
                "hash-terminal-repo-" + suffix
        ));
        return materializer.materialize(factCardRecord);
    }
}
