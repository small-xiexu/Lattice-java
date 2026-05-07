package com.xbk.lattice.query.service;

import com.xbk.lattice.infra.persistence.FactCardJdbcRepository;
import com.xbk.lattice.infra.persistence.LexicalSearchRecord;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FactCardFtsSearchService 测试
 *
 * 职责：验证结构化事实证据卡可以作为独立检索证据召回
 *
 * @author xiexu
 */
class FactCardFtsSearchServiceTests {

    /**
     * 验证中文结构化问句可以命中 fact card。
     */
    @Test
    void shouldMatchFactCardForChineseStructuredQuestion() {
        FactCardFtsSearchService factCardFtsSearchService = new FactCardFtsSearchService(
                new FakeFactCardJdbcRepository(List.of(
                        new LexicalSearchRecord(
                                Long.valueOf(12L),
                                "fact-card:100:0:fact_enum:abc12345",
                                "fact-card:100:0:fact_enum:abc12345",
                                "巡检项目清单",
                                "项目包括接口可用性、任务积压、告警确认。\n\n{\"items\":[{\"label\":\"接口可用性\"}]}",
                                "{\"cardId\":\"fact-card:100:0:fact_enum:abc12345\",\"cardType\":\"FACT_ENUM\",\"answerShape\":\"ENUM\",\"sourceChunkIds\":[31]}",
                                "valid",
                                List.of(),
                                null,
                                Boolean.FALSE,
                                8.5D
                        )
                ))
        );

        List<QueryArticleHit> hits = factCardFtsSearchService.search("巡检项目有哪些", 5);

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).getEvidenceType()).isEqualTo(QueryEvidenceType.FACT_CARD);
        assertThat(hits.get(0).getArticleKey()).isEqualTo("fact-card:100:0:fact_enum:abc12345");
        assertThat(hits.get(0).getReviewStatus()).isEqualTo("valid");
        assertThat(hits.get(0).getMetadataJson()).contains("\"sourceChunkIds\":[31]");
    }

    /**
     * 验证空问题不会触发仓储检索。
     */
    @Test
    void shouldSkipSearchWhenQuestionHasNoToken() {
        FakeFactCardJdbcRepository factCardJdbcRepository = new FakeFactCardJdbcRepository(List.of(
                new LexicalSearchRecord(
                        null,
                        "fact-card:skip",
                        "fact-card:skip",
                        "空问题不应召回",
                        "空问题不应触发检索。",
                        "{}",
                        "valid",
                        List.of(),
                        null,
                        Boolean.FALSE,
                        1.0D
                )
        ));
        FactCardFtsSearchService factCardFtsSearchService = new FactCardFtsSearchService(factCardJdbcRepository);

        List<QueryArticleHit> hits = factCardFtsSearchService.search("   ", 5);

        assertThat(hits).isEmpty();
        assertThat(factCardJdbcRepository.isCalled()).isFalse();
    }

    /**
     * 验证 conflict 卡不会进入 lexical query 候选。
     */
    @Test
    void shouldFilterConflictFactCardHits() {
        FactCardFtsSearchService factCardFtsSearchService = new FactCardFtsSearchService(
                new FakeFactCardJdbcRepository(List.of(
                        record("fact-card:conflict", "conflict", 9.0D),
                        record("fact-card:valid", "valid", 8.0D)
                ))
        );

        List<QueryArticleHit> hits = factCardFtsSearchService.search("巡检项目有哪些", 5);

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).getArticleKey()).isEqualTo("fact-card:valid");
    }

    /**
     * 验证低置信卡在 lexical query 候选中保留但降权。
     */
    @Test
    void shouldDemoteLowConfidenceFactCardScore() {
        FactCardFtsSearchService factCardFtsSearchService = new FactCardFtsSearchService(
                new FakeFactCardJdbcRepository(List.of(record("fact-card:low-confidence", "low_confidence", 10.0D)))
        );

        List<QueryArticleHit> hits = factCardFtsSearchService.search("巡检项目有哪些", 5);

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).getReviewStatus()).isEqualTo("low_confidence");
        assertThat(hits.get(0).getScore()).isLessThan(10.0D);
    }

    /**
     * 构造 fact card lexical 命中记录。
     *
     * @param itemKey 命中 key
     * @param reviewStatus 审查状态
     * @param score 分数
     * @return lexical 命中记录
     */
    private LexicalSearchRecord record(String itemKey, String reviewStatus, double score) {
        return new LexicalSearchRecord(
                Long.valueOf(12L),
                itemKey,
                itemKey,
                "结构化证据卡",
                "项目包括 alpha 与 beta。",
                "{\"cardId\":\"" + itemKey + "\",\"answerShape\":\"ENUM\"}",
                reviewStatus,
                List.of("source.md"),
                null,
                Boolean.FALSE,
                score
        );
    }

    /**
     * Fact Card 仓储替身。
     *
     * @author xiexu
     */
    private static class FakeFactCardJdbcRepository extends FactCardJdbcRepository {

        private final List<LexicalSearchRecord> records;

        private boolean called;

        /**
         * 创建 Fact Card 仓储替身。
         *
         * @param records 预置记录
         */
        private FakeFactCardJdbcRepository(List<LexicalSearchRecord> records) {
            super(new JdbcTemplate());
            this.records = records;
        }

        /**
         * 返回预置 fact card 记录。
         *
         * @param question 查询问题
         * @param queryTokens 查询 token
         * @param limit 返回数量
         * @param tsConfig FTS 配置
         * @return fact card 命中
         */
        @Override
        public List<LexicalSearchRecord> searchLexical(
                String question,
                List<String> queryTokens,
                int limit,
                String tsConfig
        ) {
            this.called = true;
            return records;
        }

        /**
         * 返回仓储是否被调用。
         *
         * @return 仓储是否被调用
         */
        private boolean isCalled() {
            return called;
        }
    }
}
