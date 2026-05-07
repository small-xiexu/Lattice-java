package com.xbk.lattice.query.service;

import com.xbk.lattice.query.evidence.domain.AnswerShape;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AnswerShapeClassifier 测试
 *
 * 职责：验证通用问法可以被归类为结构化答案形态
 *
 * @author xiexu
 */
class AnswerShapeClassifierTests {

    private final AnswerShapeClassifier answerShapeClassifier = new AnswerShapeClassifier();

    /**
     * 验证结构化题型分类回归集达到每类准确率门槛。
     */
    @Test
    void shouldPassGenericAnswerShapeClassificationRegression() {
        List<ClassificationCase> cases = List.of(
                new ClassificationCase("字段清单有哪些", AnswerShape.ENUM),
                new ClassificationCase("请列出所有可选状态", AnswerShape.ENUM),
                new ClassificationCase("这份表里包含哪些类型", AnswerShape.ENUM),
                new ClassificationCase("支持的处理项包括什么", AnswerShape.ENUM),
                new ClassificationCase("新旧版本的差异是什么", AnswerShape.COMPARE),
                new ClassificationCase("两个方案分别有什么不同", AnswerShape.COMPARE),
                new ClassificationCase("A 模式 vs B 模式怎么比较", AnswerShape.COMPARE),
                new ClassificationCase("变更前后配置如何对比", AnswerShape.COMPARE),
                new ClassificationCase("处理流程有哪些步骤", AnswerShape.SEQUENCE),
                new ClassificationCase("请按第一步到第三步说明执行顺序", AnswerShape.SEQUENCE),
                new ClassificationCase("从创建到完成的 workflow 是什么", AnswerShape.SEQUENCE),
                new ClassificationCase("上线阶段先后怎么走", AnswerShape.SEQUENCE),
                new ClassificationCase("当前任务是什么状态", AnswerShape.STATUS),
                new ClassificationCase("最新结论是什么", AnswerShape.STATUS),
                new ClassificationCase("这个开关是否启用", AnswerShape.STATUS),
                new ClassificationCase("指标被调整的原因和现状是什么", AnswerShape.STATUS),
                new ClassificationCase("访问控制必须遵守哪些规则", AnswerShape.POLICY),
                new ClassificationCase("这个参数的适用范围和边界是什么", AnswerShape.POLICY),
                new ClassificationCase("失败处理规则是什么", AnswerShape.POLICY),
                new ClassificationCase("有哪些禁止行为和约束要求", AnswerShape.POLICY),
                new ClassificationCase("为什么需要知识编译", AnswerShape.GENERAL),
                new ClassificationCase("请解释这个模块的背景", AnswerShape.GENERAL),
                new ClassificationCase("这段文档主要讲什么", AnswerShape.GENERAL),
                new ClassificationCase("系统的设计目标是什么", AnswerShape.GENERAL)
        );

        Map<AnswerShape, List<ClassificationCase>> casesByShape = cases.stream()
                .collect(Collectors.groupingBy(ClassificationCase::getExpectedShape));
        for (Map.Entry<AnswerShape, List<ClassificationCase>> entry : casesByShape.entrySet()) {
            long matchedCount = entry.getValue().stream()
                    .filter(classificationCase -> answerShapeClassifier.classify(classificationCase.getQuestion())
                            == classificationCase.getExpectedShape())
                    .count();
            double accuracy = (double) matchedCount / entry.getValue().size();

            assertThat(accuracy)
                    .as(entry.getKey().name())
                    .isGreaterThan(0.70D);
        }
    }

    /**
     * 验证清单类问法归为枚举。
     */
    @Test
    void shouldClassifyEnumQuestion() {
        AnswerShape answerShape = answerShapeClassifier.classify("巡检项目有哪些");

        assertThat(answerShape).isEqualTo(AnswerShape.ENUM);
    }

    /**
     * 验证对照类问法归为对照。
     */
    @Test
    void shouldClassifyCompareQuestion() {
        AnswerShape answerShape = answerShapeClassifier.classify("新旧方案的差异是什么");

        assertThat(answerShape).isEqualTo(AnswerShape.COMPARE);
    }

    /**
     * 验证顺序类问法归为顺序。
     */
    @Test
    void shouldClassifySequenceQuestion() {
        AnswerShape answerShape = answerShapeClassifier.classify("处理流程有哪些步骤");

        assertThat(answerShape).isEqualTo(AnswerShape.SEQUENCE);
    }

    /**
     * 验证状态类问法归为状态。
     */
    @Test
    void shouldClassifyStatusQuestion() {
        AnswerShape answerShape = answerShapeClassifier.classify("当前同步任务是什么状态");

        assertThat(answerShape).isEqualTo(AnswerShape.STATUS);
    }

    /**
     * 验证结论和调整原因类问法归为状态。
     */
    @Test
    void shouldClassifyConclusionAndAdjustmentQuestionAsStatus() {
        AnswerShape answerShape = answerShapeClassifier.classify("近 30 天指标结论是什么？为什么处理批次被调整？");

        assertThat(answerShape).isEqualTo(AnswerShape.STATUS);
    }

    /**
     * 验证规则类问法归为规则。
     */
    @Test
    void shouldClassifyPolicyQuestion() {
        AnswerShape answerShape = answerShapeClassifier.classify("访问控制有哪些必须遵守的规则");

        assertThat(answerShape).isEqualTo(AnswerShape.POLICY);
    }

    /**
     * 验证普通解释类问法归为通用。
     */
    @Test
    void shouldClassifyGeneralQuestion() {
        AnswerShape answerShape = answerShapeClassifier.classify("为什么需要知识编译");

        assertThat(answerShape).isEqualTo(AnswerShape.GENERAL);
    }

    /**
     * 验证空问题归为通用。
     */
    @Test
    void shouldClassifyBlankQuestionAsGeneral() {
        AnswerShape answerShape = answerShapeClassifier.classify("   ");

        assertThat(answerShape).isEqualTo(AnswerShape.GENERAL);
    }

    /**
     * 分类回归用例。
     *
     * 职责：记录问题和期望答案形态
     *
     * @author xiexu
     */
    private static class ClassificationCase {

        private final String question;

        private final AnswerShape expectedShape;

        /**
         * 创建分类回归用例。
         *
         * @param question 问题
         * @param expectedShape 期望答案形态
         */
        private ClassificationCase(String question, AnswerShape expectedShape) {
            this.question = question;
            this.expectedShape = expectedShape;
        }

        /**
         * 获取问题。
         *
         * @return 问题
         */
        private String getQuestion() {
            return question;
        }

        /**
         * 获取期望答案形态。
         *
         * @return 期望答案形态
         */
        private AnswerShape getExpectedShape() {
            return expectedShape;
        }
    }
}
