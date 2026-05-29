package com.xbk.lattice.query.service;

import com.xbk.lattice.infra.persistence.FactCardTerminalUnitJdbcRepository;
import com.xbk.lattice.infra.persistence.LexicalSearchRecord;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FactCardTerminalUnitFtsSearchService 测试
 *
 * 职责：验证 terminal unit FTS 命中以 unit identity 进入查询候选，
 * 且 search 返回结果已按字段意图重排。
 *
 * @author xiexu
 */
class FactCardTerminalUnitFtsSearchServiceTests {

    /**
     * 验证 QueryArticleHit 使用 terminal unit identity，不退回 card id。
     */
    @Test
    void shouldReturnQueryHitWithTerminalUnitIdentity() {
        FactCardTerminalUnitFtsSearchService searchService = new FactCardTerminalUnitFtsSearchService(
                new FakeFactCardTerminalUnitJdbcRepository(List.of(record("terminal-unit:alpha",
                        "beta_mode", "settings", "enabled", "string")))
        );

        List<QueryArticleHit> hits = searchService.search("alpha_limit", 5);

        assertThat(hits).hasSize(1);
        QueryArticleHit hit = hits.get(0);
        assertThat(hit.getEvidenceType()).isEqualTo(QueryEvidenceType.FACT_CARD);
        assertThat(hit.getArticleKey()).isEqualTo("terminal-unit:alpha");
        assertThat(hit.getConceptId()).isEqualTo("terminal-unit:alpha");
        assertThat(hit.getMetadataJson()).contains("\"terminalUnitIdentity\":\"terminal-unit:alpha\"");
        assertThat(hit.getMetadataJson()).contains("\"cardId\":\"fc:synthetic\"");
    }

    /**
     * 验证命中内容是 displayText 与字段描述，不包含整张 items_json。
     */
    @Test
    void shouldExposeDisplayTextAndDescriptionWithoutFullItemsJson() {
        FactCardTerminalUnitFtsSearchService searchService = new FactCardTerminalUnitFtsSearchService(
                new FakeFactCardTerminalUnitJdbcRepository(List.of(record("terminal-unit:beta",
                        "beta_mode", "settings", "enabled", "string")))
        );

        List<QueryArticleHit> hits = searchService.search("beta_mode", 5);

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).getContent()).contains("beta_mode = enabled");
        assertThat(hits.get(0).getContent()).contains("field: beta_mode");
        assertThat(hits.get(0).getContent()).doesNotContain("\"items\"");
        assertThat(hits.get(0).getContent()).doesNotContain("items_json");
    }

    /**
     * 验证多 hit 场景下 search 返回结果已按字段意图重排：
     * terminalKey 匹配 query token 的 hit 排在 value-text-only 匹配的 hit 前面。
     */
    @Test
    void shouldReturnRerankedResultsWithFieldIntentFirst() {
        LexicalSearchRecord valueOnlyRecord = record("terminal-unit:eta_type",
                "eta_type", "root", "配置项A示例名称长文本", "string");
        LexicalSearchRecord fieldMatchRecord = record("terminal-unit:eta_limit",
                "eta_limit", "root", "31", "number");

        FactCardTerminalUnitFtsSearchService searchService = new FactCardTerminalUnitFtsSearchService(
                new FakeFactCardTerminalUnitJdbcRepository(List.of(valueOnlyRecord, fieldMatchRecord))
        );

        List<QueryArticleHit> hits = searchService.search("eta_limit 配置项A", 5);

        assertThat(hits).hasSize(2);
        assertThat(hits.get(0).getArticleKey())
                .as("field-intent hit (eta_limit) should rank first after rerank")
                .isEqualTo("terminal-unit:eta_limit");
        assertThat(hits.get(1).getArticleKey())
                .isEqualTo("terminal-unit:eta_type");
    }

    /**
     * 构造 terminal unit lexical 命中。
     */
    private LexicalSearchRecord record(String identity, String terminalKey,
            String parentPath, String value, String valueType) {
        String keyPath = parentPath + "." + terminalKey;
        String displayText = keyPath + " = " + value;
        String metadataJson = "{\"terminalUnitIdentity\":\"" + identity + "\","
                + "\"unitId\":\"unit-alpha\","
                + "\"factCardId\":42,\"cardId\":\"fc:synthetic\","
                + "\"terminalKey\":\"" + terminalKey + "\","
                + "\"fieldLabel\":\"" + terminalKey + "\","
                + "\"fieldAliases\":[],"
                + "\"keyPath\":\"" + keyPath + "\","
                + "\"parentPath\":\"" + parentPath + "\","
                + "\"value\":\"" + escapeJson(value) + "\","
                + "\"valueType\":\"" + valueType + "\","
                + "\"displayText\":\"" + escapeJson(displayText) + "\"}";
        return new LexicalSearchRecord(
                Long.valueOf(14L),
                identity,
                identity,
                "Synthetic terminal unit",
                displayText + "\nfield: " + terminalKey + "; valueType: " + valueType,
                metadataJson,
                "valid",
                List.of("terminal-unit/synthetic.md"),
                null,
                Boolean.FALSE,
                9.0D);
    }

    private String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * Terminal unit 仓储替身。
     */
    private static class FakeFactCardTerminalUnitJdbcRepository extends FactCardTerminalUnitJdbcRepository {

        private final List<LexicalSearchRecord> records;

        private FakeFactCardTerminalUnitJdbcRepository(List<LexicalSearchRecord> records) {
            super(null);
            this.records = records;
        }

        @Override
        public List<LexicalSearchRecord> searchLexical(
                String question,
                List<String> queryTokens,
                int limit,
                String tsConfig
        ) {
            return records;
        }
    }
}
