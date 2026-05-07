package com.xbk.lattice.compiler.service;

import com.xbk.lattice.infra.persistence.FactCardRecord;
import com.xbk.lattice.query.evidence.domain.AnswerShape;
import com.xbk.lattice.query.evidence.domain.FactCardReviewStatus;
import com.xbk.lattice.query.evidence.domain.FactCardType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FactCardReviewer 测试
 *
 * 职责：验证事实证据卡卡级通用质量审查
 *
 * @author xiexu
 */
class FactCardReviewerTests {

    private final FactCardReviewer factCardReviewer = new FactCardReviewer();

    /**
     * 验证完整枚举卡且 source 回指可定位时为 valid。
     */
    @Test
    void shouldReturnValidWhenEnumCardIsCompleteAndSourceLocated() {
        FactCardRecord factCard = factCard(
                FactCardType.FACT_ENUM,
                AnswerShape.ENUM,
                """
                        {"items":[
                          {"label":"alpha","value":"enabled"},
                          {"label":"beta","value":"disabled"}
                        ]}
                        """,
                "alpha: enabled\nbeta: disabled",
                List.of(10L),
                0.90D
        );

        FactCardReviewResult result = factCardReviewer.review(
                factCard,
                List.of("alpha: enabled\nbeta: disabled")
        );

        assertThat(result.getReviewStatus()).isEqualTo(FactCardReviewStatus.VALID);
        assertThat(result.getReasons()).isEmpty();
    }

    /**
     * 验证高置信卡无法定位 source 证据时降为 low_confidence。
     */
    @Test
    void shouldReturnLowConfidenceWhenHighConfidenceEvidenceCannotBeLocated() {
        FactCardRecord factCard = factCard(
                FactCardType.FACT_ENUM,
                AnswerShape.ENUM,
                """
                        {"items":[{"label":"alpha","value":"enabled"}]}
                        """,
                "alpha: enabled",
                List.of(10L),
                0.91D
        );

        FactCardReviewResult result = factCardReviewer.review(factCard, List.of("beta: disabled"));

        assertThat(result.getReviewStatus()).isEqualTo(FactCardReviewStatus.LOW_CONFIDENCE);
        assertThat(result.getReasons()).anyMatch(reason -> reason.contains("source chunk"));
    }

    /**
     * 验证高置信跨 chunk 证据可通过相邻文本定位。
     */
    @Test
    void shouldReturnValidWhenEvidenceSpansAdjacentChunks() {
        FactCardRecord factCard = factCard(
                FactCardType.FACT_SEQUENCE,
                AnswerShape.SEQUENCE,
                """
                        {"steps":[
                          {"position":1,"text":"collect input"},
                          {"position":2,"text":"validate input"},
                          {"position":3,"text":"publish result"}
                        ]}
                        """,
                "1. collect input\n2. validate input\n3. publish result",
                List.of(10L, 11L),
                0.91D
        );

        FactCardReviewResult result = factCardReviewer.review(
                factCard,
                List.of("1. collect input\n2. validate input", "3. publish result")
        );

        assertThat(result.getReviewStatus()).isEqualTo(FactCardReviewStatus.VALID);
    }

    /**
     * 验证对照卡缺侧时为 incomplete。
     */
    @Test
    void shouldReturnIncompleteWhenCompareRowMissesSide() {
        FactCardRecord factCard = factCard(
                FactCardType.FACT_COMPARE,
                AnswerShape.COMPARE,
                """
                        {"rows":[{"item":"module-a","left":"sync","right":""}]}
                        """,
                "module-a sync",
                List.of(10L),
                0.70D
        );

        FactCardReviewResult result = factCardReviewer.review(factCard, List.of("module-a sync"));

        assertThat(result.getReviewStatus()).isEqualTo(FactCardReviewStatus.INCOMPLETE);
        assertThat(result.getReasons()).anyMatch(reason -> reason.contains("缺侧"));
    }

    /**
     * 验证顺序卡步骤倒序时为 conflict。
     */
    @Test
    void shouldReturnConflictWhenSequencePositionsAreReversed() {
        FactCardRecord factCard = factCard(
                FactCardType.FACT_SEQUENCE,
                AnswerShape.SEQUENCE,
                """
                        {"steps":[
                          {"position":2,"text":"validate input"},
                          {"position":1,"text":"collect input"}
                        ]}
                        """,
                "validate input\ncollect input",
                List.of(10L),
                0.70D
        );

        FactCardReviewResult result = factCardReviewer.review(factCard, List.of("validate input\ncollect input"));

        assertThat(result.getReviewStatus()).isEqualTo(FactCardReviewStatus.CONFLICT);
        assertThat(result.getReasons()).anyMatch(reason -> reason.contains("倒序"));
    }

    /**
     * 验证同一主语出现互斥状态时为 conflict。
     */
    @Test
    void shouldReturnConflictWhenStatusSubjectHasMultipleGroups() {
        FactCardRecord factCard = factCard(
                FactCardType.FACT_STATUS,
                AnswerShape.STATUS,
                """
                        {"items":[
                          {"subject":"alpha","status":"已确认","statusGroup":"CONFIRMED"},
                          {"subject":"alpha","status":"待确认","statusGroup":"PENDING"}
                        ]}
                        """,
                "alpha: 已确认\nalpha: 待确认",
                List.of(10L),
                0.70D
        );

        FactCardReviewResult result = factCardReviewer.review(factCard, List.of("alpha: 已确认\nalpha: 待确认"));

        assertThat(result.getReviewStatus()).isEqualTo(FactCardReviewStatus.CONFLICT);
        assertThat(result.getReasons()).anyMatch(reason -> reason.contains("互斥状态"));
    }

    /**
     * 验证规则卡缺少适用范围时为 incomplete。
     */
    @Test
    void shouldReturnIncompleteWhenPolicyScopeMissing() {
        FactCardRecord factCard = factCard(
                FactCardType.FACT_POLICY,
                AnswerShape.POLICY,
                """
                        {"constraints":[{"constraint":"必须保留原文引用"}],"scopes":[]}
                        """,
                "必须保留原文引用",
                List.of(10L),
                0.70D
        );

        FactCardReviewResult result = factCardReviewer.review(factCard, List.of("必须保留原文引用"));

        assertThat(result.getReviewStatus()).isEqualTo(FactCardReviewStatus.INCOMPLETE);
        assertThat(result.getReasons()).anyMatch(reason -> reason.contains("适用范围"));
    }

    /**
     * 验证空卡需要人工审查。
     */
    @Test
    void shouldReturnNeedsHumanReviewWhenFactCardMissing() {
        FactCardReviewResult result = factCardReviewer.review(null, List.of());

        assertThat(result.getReviewStatus()).isEqualTo(FactCardReviewStatus.NEEDS_HUMAN_REVIEW);
    }

    /**
     * 构造事实证据卡测试夹具。
     *
     * @param factCardType 证据卡类型
     * @param answerShape 答案形态
     * @param itemsJson 结构化条目 JSON
     * @param evidenceText 证据文本
     * @param sourceChunkIds source chunk 主键
     * @param confidence 置信度
     * @return 事实证据卡
     */
    private FactCardRecord factCard(
            FactCardType factCardType,
            AnswerShape answerShape,
            String itemsJson,
            String evidenceText,
            List<Long> sourceChunkIds,
            double confidence
    ) {
        return new FactCardRecord(
                "fact-card-review-" + factCardType.name().toLowerCase(),
                1L,
                2L,
                factCardType,
                answerShape,
                "测试事实证据卡",
                "测试结论",
                itemsJson,
                evidenceText,
                sourceChunkIds,
                List.of(),
                confidence,
                FactCardReviewStatus.VALID,
                "hash-" + factCardType.name().toLowerCase()
        );
    }
}
