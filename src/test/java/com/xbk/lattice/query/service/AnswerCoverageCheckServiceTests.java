package com.xbk.lattice.query.service;

import com.xbk.lattice.infra.persistence.FactCardRecord;
import com.xbk.lattice.query.domain.AnswerOutcome;
import com.xbk.lattice.query.evidence.domain.AnswerShape;
import com.xbk.lattice.query.evidence.domain.FactCardReviewStatus;
import com.xbk.lattice.query.evidence.domain.FactCardType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AnswerCoverageCheckService 测试
 *
 * 职责：验证结构化证据卡驱动的答案覆盖校验
 *
 * @author xiexu
 */
class AnswerCoverageCheckServiceTests {

    private final AnswerCoverageCheckService answerCoverageCheckService = new AnswerCoverageCheckService();

    /**
     * 验证通用结构化答案完整覆盖时返回 covered。
     */
    @Test
    void shouldReturnCoveredWhenStructuredAnswerCoversAllRequirements() {
        FactCardRecord factCard = factCard(
                FactCardType.FACT_ENUM,
                AnswerShape.ENUM,
                """
                        {"items":[
                          {"label":"alpha","value":"enabled"},
                          {"label":"beta","value":"disabled"}
                        ]}
                        """
        );

        AnswerCoverageCheckResult result = answerCoverageCheckService.check(
                "列出所有项目",
                AnswerShape.ENUM,
                List.of(factCard),
                "alpha = enabled；beta = disabled。"
        );

        assertThat(result.getCoverageStatus()).isEqualTo(AnswerCoverageStatus.COVERED);
        assertThat(result.getSuggestedOutcome()).isEqualTo(AnswerOutcome.SUCCESS);
        assertThat(result.getMissingItems()).isEmpty();
    }

    /**
     * 验证枚举题漏掉条目时降为部分答案。
     */
    @Test
    void shouldMarkEnumAnswerPartialWhenItemValueMissing() {
        FactCardRecord factCard = factCard(
                FactCardType.FACT_ENUM,
                AnswerShape.ENUM,
                """
                        {"items":[
                          {"label":"alpha","value":"enabled"},
                          {"label":"beta","value":"disabled"}
                        ]}
                        """
        );

        AnswerCoverageCheckResult result = answerCoverageCheckService.check(
                "有哪些项目",
                AnswerShape.ENUM,
                List.of(factCard),
                "当前只确认 alpha = enabled。"
        );

        assertThat(result.getCoverageStatus()).isEqualTo(AnswerCoverageStatus.PARTIAL);
        assertThat(result.getSuggestedOutcome()).isEqualTo(AnswerOutcome.PARTIAL_ANSWER);
        assertThat(result.getMissingItems()).anyMatch(item -> item.contains("beta") && item.contains("disabled"));
    }

    /**
     * 验证对照题只答一侧时降为部分答案。
     */
    @Test
    void shouldMarkCompareAnswerPartialWhenOneSideMissing() {
        FactCardRecord factCard = factCard(
                FactCardType.FACT_COMPARE,
                AnswerShape.COMPARE,
                """
                        {"rows":[
                          {"item":"module-a","left":"sync","right":"async","conclusion":"switch to async"}
                        ]}
                        """
        );

        AnswerCoverageCheckResult result = answerCoverageCheckService.check(
                "比较前后差异",
                AnswerShape.COMPARE,
                List.of(factCard),
                "module-a 原先是 sync。"
        );

        assertThat(result.getCoverageStatus()).isEqualTo(AnswerCoverageStatus.PARTIAL);
        assertThat(result.getSuggestedOutcome()).isEqualTo(AnswerOutcome.PARTIAL_ANSWER);
        assertThat(result.getMissingItems()).anyMatch(item -> item.contains("async"));
    }

    /**
     * 验证顺序题答案乱序时降为部分答案。
     */
    @Test
    void shouldMarkSequenceAnswerPartialWhenOrderIsReversed() {
        FactCardRecord factCard = factCard(
                FactCardType.FACT_SEQUENCE,
                AnswerShape.SEQUENCE,
                """
                        {"steps":[
                          {"position":1,"text":"collect input"},
                          {"position":2,"text":"validate input"},
                          {"position":3,"text":"publish result"}
                        ]}
                        """
        );

        AnswerCoverageCheckResult result = answerCoverageCheckService.check(
                "处理流程有哪些步骤",
                AnswerShape.SEQUENCE,
                List.of(factCard),
                "publish result，然后 validate input，最后 collect input。"
        );

        assertThat(result.getCoverageStatus()).isEqualTo(AnswerCoverageStatus.PARTIAL);
        assertThat(result.getSuggestedOutcome()).isEqualTo(AnswerOutcome.PARTIAL_ANSWER);
        assertThat(result.getMissingItems()).anyMatch(item -> item.contains("顺序不一致"));
    }

    /**
     * 验证状态题互斥状态混淆时降为部分答案。
     */
    @Test
    void shouldMarkStatusAnswerPartialWhenSubjectUsesWrongStatusGroup() {
        FactCardRecord factCard = factCard(
                FactCardType.FACT_STATUS,
                AnswerShape.STATUS,
                """
                        {"items":[
                          {"subject":"alpha","status":"已确认","statusGroup":"CONFIRMED"},
                          {"subject":"beta","status":"待确认","statusGroup":"PENDING"}
                        ]}
                        """
        );

        AnswerCoverageCheckResult result = answerCoverageCheckService.check(
                "按状态列出项目",
                AnswerShape.STATUS,
                List.of(factCard),
                "alpha 待确认，beta 已确认。"
        );

        assertThat(result.getCoverageStatus()).isEqualTo(AnswerCoverageStatus.MISSING);
        assertThat(result.getSuggestedOutcome()).isEqualTo(AnswerOutcome.INSUFFICIENT_EVIDENCE);
        assertThat(result.getMissingItems()).anyMatch(item -> item.contains("状态混淆") && item.contains("alpha"));
        assertThat(result.getMissingItems()).anyMatch(item -> item.contains("状态混淆") && item.contains("beta"));
    }

    /**
     * 验证状态题会按问题点名的状态值收窄覆盖范围。
     */
    @Test
    void shouldFocusStatusCoverageWhenQuestionNamesSpecificStatus() {
        FactCardRecord factCard = factCard(
                FactCardType.FACT_STATUS,
                AnswerShape.STATUS,
                """
                        {"items":[
                          {"subject":"alpha","status":"已确认","statusGroup":"CONFIRMED"},
                          {"subject":"beta","status":"待确认","statusGroup":"PENDING"}
                        ]}
                        """
        );

        AnswerCoverageCheckResult result = answerCoverageCheckService.check(
                "哪些项目已确认",
                AnswerShape.STATUS,
                List.of(factCard),
                "已确认项目是 alpha。"
        );

        assertThat(result.getCoverageStatus()).isEqualTo(AnswerCoverageStatus.COVERED);
    }

    /**
     * 验证规则题漏掉适用范围时降为部分答案。
     */
    @Test
    void shouldMarkPolicyAnswerPartialWhenScopeMissing() {
        FactCardRecord factCard = factCard(
                FactCardType.FACT_POLICY,
                AnswerShape.POLICY,
                """
                        {"constraints":[
                          {"constraint":"必须保留原始引用"},
                          {"constraint":"不得删除证据行"}
                        ],"scopes":["适用范围：所有输入文件"]}
                        """
        );

        AnswerCoverageCheckResult result = answerCoverageCheckService.check(
                "有哪些规则",
                AnswerShape.POLICY,
                List.of(factCard),
                "规则包括：必须保留原始引用；不得删除证据行。"
        );

        assertThat(result.getCoverageStatus()).isEqualTo(AnswerCoverageStatus.PARTIAL);
        assertThat(result.getSuggestedOutcome()).isEqualTo(AnswerOutcome.PARTIAL_ANSWER);
        assertThat(result.getMissingItems()).anyMatch(item -> item.contains("适用范围"));
    }

    /**
     * 验证通用答案形态不执行结构化覆盖校验。
     */
    @Test
    void shouldReturnNotApplicableForGeneralShape() {
        AnswerCoverageCheckResult result = answerCoverageCheckService.check(
                "为什么需要索引",
                AnswerShape.GENERAL,
                List.of(),
                "因为索引可以提升查询效率。"
        );

        assertThat(result.getCoverageStatus()).isEqualTo(AnswerCoverageStatus.NOT_APPLICABLE);
        assertThat(result.getSuggestedOutcome()).isEqualTo(AnswerOutcome.SUCCESS);
    }

    /**
     * 验证结构化题没有可校验证据卡时返回 missing。
     */
    @Test
    void shouldReturnMissingWhenNoFactCardsAvailable() {
        AnswerCoverageCheckResult result = answerCoverageCheckService.check(
                "有哪些项目",
                AnswerShape.ENUM,
                List.of(),
                "当前答案没有结构化证据。"
        );

        assertThat(result.getCoverageStatus()).isEqualTo(AnswerCoverageStatus.MISSING);
        assertThat(result.getSuggestedOutcome()).isEqualTo(AnswerOutcome.INSUFFICIENT_EVIDENCE);
    }

    /**
     * 构造事实证据卡测试夹具。
     *
     * @param factCardType 证据卡类型
     * @param answerShape 答案形态
     * @param itemsJson 结构化条目 JSON
     * @return 事实证据卡
     */
    private FactCardRecord factCard(FactCardType factCardType, AnswerShape answerShape, String itemsJson) {
        return new FactCardRecord(
                "fact-card-test-" + factCardType.name().toLowerCase(),
                1L,
                2L,
                factCardType,
                answerShape,
                "测试结构化证据卡",
                "测试结论",
                itemsJson,
                "测试原文证据",
                List.of(10L),
                List.of(),
                0.90D,
                FactCardReviewStatus.VALID,
                "hash-" + factCardType.name().toLowerCase()
        );
    }
}
