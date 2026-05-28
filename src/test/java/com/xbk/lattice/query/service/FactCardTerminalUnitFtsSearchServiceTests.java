package com.xbk.lattice.query.service;

import com.xbk.lattice.infra.persistence.FactCardTerminalUnitJdbcRepository;
import com.xbk.lattice.infra.persistence.LexicalSearchRecord;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FactCardTerminalUnitFtsSearchService 测试
 *
 * 职责：验证 terminal unit FTS 命中以 unit identity 进入查询候选
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
                new FakeFactCardTerminalUnitJdbcRepository(List.of(record("terminal-unit:alpha")))
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
                new FakeFactCardTerminalUnitJdbcRepository(List.of(record("terminal-unit:beta")))
        );

        List<QueryArticleHit> hits = searchService.search("beta_mode", 5);

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).getContent()).contains("beta_mode = enabled");
        assertThat(hits.get(0).getContent()).contains("field: beta_mode");
        assertThat(hits.get(0).getContent()).doesNotContain("\"items\"");
        assertThat(hits.get(0).getContent()).doesNotContain("items_json");
    }

    /**
     * 构造 terminal unit lexical 命中。
     *
     * @param identity terminal unit identity
     * @return lexical 命中记录
     */
    private LexicalSearchRecord record(String identity) {
        return new LexicalSearchRecord(
                Long.valueOf(14L),
                identity,
                identity,
                "Synthetic terminal unit",
                "beta_mode = enabled\nfield: beta_mode; valueType: string",
                "{\"terminalUnitIdentity\":\"" + identity + "\",\"unitId\":\"unit-alpha\","
                        + "\"factCardId\":42,\"cardId\":\"fc:synthetic\","
                        + "\"keyPath\":\"settings.beta_mode\",\"parentPath\":\"settings\","
                        + "\"terminalKey\":\"beta_mode\",\"value\":\"enabled\","
                        + "\"valueType\":\"string\",\"displayText\":\"beta_mode = enabled\"}",
                "valid",
                List.of("terminal-unit/synthetic.md"),
                null,
                Boolean.FALSE,
                9.0D
        );
    }

    /**
     * Terminal unit 仓储替身。
     *
     * @author xiexu
     */
    private static class FakeFactCardTerminalUnitJdbcRepository extends FactCardTerminalUnitJdbcRepository {

        private final List<LexicalSearchRecord> records;

        /**
         * 创建 terminal unit 仓储替身。
         *
         * @param records 预置记录
         */
        private FakeFactCardTerminalUnitJdbcRepository(List<LexicalSearchRecord> records) {
            super(null);
            this.records = records;
        }

        /**
         * 返回预置 terminal unit 记录。
         *
         * @param question 查询问题
         * @param queryTokens 查询 token
         * @param limit 返回数量
         * @param tsConfig FTS 配置
         * @return terminal unit 命中
         */
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
