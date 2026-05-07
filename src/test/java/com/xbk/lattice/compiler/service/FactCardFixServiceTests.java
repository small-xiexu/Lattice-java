package com.xbk.lattice.compiler.service;

import com.xbk.lattice.infra.persistence.FactCardRecord;
import com.xbk.lattice.query.evidence.domain.AnswerShape;
import com.xbk.lattice.query.evidence.domain.FactCardReviewStatus;
import com.xbk.lattice.query.evidence.domain.FactCardType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FactCardFixService 测试
 *
 * 职责：验证事实证据卡结构修复只做保守归一，不创造新事实
 *
 * @author xiexu
 */
class FactCardFixServiceTests {

    private final FactCardFixService factCardFixService = new FactCardFixService();

    /**
     * 验证枚举卡会清理空白条目并保留 source 回指有效性。
     */
    @Test
    void shouldTrimEnumItemsAndKeepSourceReferenceValid() {
        FactCardRecord factCard = factCard(
                FactCardType.FACT_ENUM,
                AnswerShape.ENUM,
                """
                        {"items":[
                          {"label":" alpha ","value":" enabled "},
                          {"label":"   ","value":"   "}
                        ]}
                        """,
                "alpha enabled",
                List.of(10L),
                0.90D
        );

        FactCardFixResult result = factCardFixService.fix(factCard, List.of("alpha enabled"));

        assertThat(result.getFactCardRecord().getItemsJson()).contains("\"label\":\"alpha\"");
        assertThat(result.getFactCardRecord().getItemsJson()).doesNotContain("\"label\":\"   \"");
        assertThat(result.getReviewResult().getReviewStatus()).isEqualTo(FactCardReviewStatus.VALID);
    }

    /**
     * 验证顺序卡可按 position 重排步骤。
     */
    @Test
    void shouldSortSequenceStepsByPosition() {
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

        FactCardFixResult result = factCardFixService.fix(factCard, List.of("validate input\ncollect input"));

        String itemsJson = result.getFactCardRecord().getItemsJson();
        assertThat(itemsJson.indexOf("collect input")).isLessThan(itemsJson.indexOf("validate input"));
        assertThat(result.getActions()).contains("按 position 重排顺序步骤");
    }

    /**
     * 验证状态卡会补充冲突主语标记。
     */
    @Test
    void shouldMarkStatusConflictSubjects() {
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

        FactCardFixResult result = factCardFixService.fix(factCard, List.of("alpha: 已确认\nalpha: 待确认"));

        assertThat(result.getFactCardRecord().getItemsJson()).contains("\"conflictSubjects\":[\"alpha\"]");
        assertThat(result.getReviewResult().getReviewStatus()).isEqualTo(FactCardReviewStatus.CONFLICT);
    }

    /**
     * 验证规则卡只归一已有字段，不补造缺失适用范围。
     */
    @Test
    void shouldNotInventMissingPolicyScope() {
        FactCardRecord factCard = factCard(
                FactCardType.FACT_POLICY,
                AnswerShape.POLICY,
                """
                        {"constraints":[{"constraint":" 必须保留原文引用 "}],"scopes":[]}
                        """,
                "必须保留原文引用",
                List.of(10L),
                0.70D
        );

        FactCardFixResult result = factCardFixService.fix(factCard, List.of("必须保留原文引用"));

        assertThat(result.getFactCardRecord().getItemsJson()).contains("\"constraint\":\"必须保留原文引用\"");
        assertThat(result.getFactCardRecord().getItemsJson()).contains("\"scopes\":[]");
        assertThat(result.getReviewResult().getReviewStatus()).isEqualTo(FactCardReviewStatus.INCOMPLETE);
    }

    /**
     * 验证修复后若 source 回指仍无法定位，不会被误标为 valid。
     */
    @Test
    void shouldKeepLowConfidenceWhenFixedEvidenceCannotBeLocated() {
        FactCardRecord factCard = factCard(
                FactCardType.FACT_ENUM,
                AnswerShape.ENUM,
                """
                        {"items":[{"label":"alpha","value":"enabled"}]}
                        """,
                "alpha enabled",
                List.of(10L),
                0.90D
        );

        FactCardFixResult result = factCardFixService.fix(factCard, List.of("beta disabled"));

        assertThat(result.getReviewResult().getReviewStatus()).isEqualTo(FactCardReviewStatus.LOW_CONFIDENCE);
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
                "fact-card-fix-" + factCardType.name().toLowerCase(),
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
                FactCardReviewStatus.INCOMPLETE,
                "hash-" + factCardType.name().toLowerCase()
        );
    }
}
