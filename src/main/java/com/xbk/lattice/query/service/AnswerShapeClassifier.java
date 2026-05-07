package com.xbk.lattice.query.service;

import com.xbk.lattice.query.evidence.domain.AnswerShape;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * 答案形态分类器
 *
 * 职责：基于通用问法识别 query 期望的结构化答案形态
 *
 * @author xiexu
 */
@Service
public class AnswerShapeClassifier {

    /**
     * 识别答案形态。
     *
     * @param question 查询问题
     * @return 答案形态
     */
    public AnswerShape classify(String question) {
        if (question == null || question.isBlank()) {
            return AnswerShape.GENERAL;
        }
        String normalizedQuestion = question.toLowerCase(Locale.ROOT);
        if (looksLikeStrongStatusQuestion(normalizedQuestion)) {
            return AnswerShape.STATUS;
        }
        if (looksLikeSequenceQuestion(normalizedQuestion)) {
            return AnswerShape.SEQUENCE;
        }
        if (looksLikeCompareQuestion(normalizedQuestion)) {
            return AnswerShape.COMPARE;
        }
        if (looksLikeStatusQuestion(normalizedQuestion)) {
            return AnswerShape.STATUS;
        }
        if (looksLikePolicyQuestion(normalizedQuestion)) {
            return AnswerShape.POLICY;
        }
        if (looksLikeEnumQuestion(normalizedQuestion)) {
            return AnswerShape.ENUM;
        }
        return AnswerShape.GENERAL;
    }

    /**
     * 判断问题是否期望对照答案。
     *
     * @param question 归一化问题
     * @return 对照题返回 true
     */
    private boolean looksLikeCompareQuestion(String question) {
        return containsAny(
                question,
                "对比",
                "比较",
                "区别",
                "差异",
                "不同",
                "相比",
                "分别",
                "vs",
                "versus",
                "compare",
                "comparison",
                "difference",
                "before and after",
                "旧",
                "新"
        );
    }

    /**
     * 判断问题是否期望顺序答案。
     *
     * @param question 归一化问题
     * @return 顺序题返回 true
     */
    private boolean looksLikeSequenceQuestion(String question) {
        return containsAny(
                question,
                "步骤",
                "流程",
                "顺序",
                "先后",
                "阶段",
                "批次",
                "第几步",
                "从哪到哪",
                "怎么走",
                "如何推进",
                "sequence",
                "step",
                "steps",
                "process",
                "workflow",
                "timeline"
        );
    }

    /**
     * 判断问题是否强烈期望状态/结论答案。
     *
     * @param question 归一化问题
     * @return 状态题返回 true
     */
    private boolean looksLikeStrongStatusQuestion(String question) {
        return containsAny(
                question,
                "结论是什么",
                "当前结论",
                "最新结论",
                "数据结论",
                "指标结论",
                "趋势结论",
                "被调整",
                "为什么",
                "如何降级",
                "怎么降级",
                "修正为"
        ) && containsAny(
                question,
                "结论",
                "数据",
                "指标",
                "趋势",
                "调整",
                "降级",
                "修正"
        );
    }

    /**
     * 判断问题是否期望状态答案。
     *
     * @param question 归一化问题
     * @return 状态题返回 true
     */
    private boolean looksLikeStatusQuestion(String question) {
        return containsAny(
                question,
                "状态",
                "现状",
                "当前",
                "是否完成",
                "是否启用",
                "是否生效",
                "是否删除",
                "是否移除",
                "待确认",
                "已确认",
                "完成了吗",
                "结论",
                "结果",
                "进展",
                "数据",
                "指标",
                "趋势",
                "调整",
                "修正",
                "降级",
                "status",
                "state",
                "current",
                "enabled",
                "disabled",
                "done",
                "pending"
        );
    }

    /**
     * 判断问题是否期望规则答案。
     *
     * @param question 归一化问题
     * @return 规则题返回 true
     */
    private boolean looksLikePolicyQuestion(String question) {
        return containsAny(
                question,
                "规则",
                "原则",
                "约束",
                "要求",
                "必须",
                "禁止",
                "不得",
                "不允许",
                "适用范围",
                "边界",
                "policy",
                "rule",
                "constraint",
                "requirement",
                "must",
                "forbid",
                "forbidden",
                "allowed"
        );
    }

    /**
     * 判断问题是否期望枚举答案。
     *
     * @param question 归一化问题
     * @return 枚举题返回 true
     */
    private boolean looksLikeEnumQuestion(String question) {
        return containsAny(
                question,
                "有哪些",
                "哪些",
                "清单",
                "列表",
                "包括什么",
                "包含什么",
                "多少种",
                "几类",
                "列出",
                "枚举",
                "批次",
                "list",
                "items",
                "options",
                "types",
                "categories"
        );
    }

    /**
     * 判断文本是否包含任一片段。
     *
     * @param text 文本
     * @param fragments 片段
     * @return 包含任一片段返回 true
     */
    private boolean containsAny(String text, String... fragments) {
        for (String fragment : fragments) {
            if (text.contains(fragment)) {
                return true;
            }
        }
        return false;
    }
}
