package com.xbk.lattice.query.service;

import com.xbk.lattice.article.service.ArticleMarkdownSupport;
import com.xbk.lattice.compiler.service.LlmGateway;
import com.xbk.lattice.llm.service.ExecutionLlmSnapshotService;
import com.xbk.lattice.llm.service.LlmInvocationEnvelope;
import com.xbk.lattice.llm.service.PromptCacheWritePolicy;
import com.xbk.lattice.query.domain.AnswerOutcome;
import com.xbk.lattice.query.domain.GenerationMode;
import com.xbk.lattice.query.domain.ModelExecutionStatus;
import com.xbk.lattice.query.domain.QueryAnswerPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * 答案生成基础问题类型支持
 *
 * 职责：识别精确查值、枚举、对比、状态、流程、规则和变更类问题
 *
 * @author xiexu
 */
@Slf4j
abstract class AnswerGenerationQuestionTypeBasicSupport extends AnswerGenerationEvidenceSignalSupport {

    /**
     * 创建无 LLM 的问题类型基础支持。
     */
    AnswerGenerationQuestionTypeBasicSupport() {
        super();
    }

    /**
     * 创建问题类型基础支持。
     *
     * @param llmGateway LLM 网关
     */
    AnswerGenerationQuestionTypeBasicSupport(LlmGateway llmGateway) {
        super(llmGateway);
    }

boolean looksLikeStructuredFactQuestion(String question) {
        String normalizedQuestion = lowerCase(question);
        return normalizedQuestion.contains("配置")
                || normalizedQuestion.contains("参数")
                || normalizedQuestion.contains("规范")
                || normalizedQuestion.contains("规则")
                || normalizedQuestion.contains("阈值")
                || normalizedQuestion.contains("结论")
                || normalizedQuestion.contains("命中数")
                || normalizedQuestion.contains("路径")
                || normalizedQuestion.contains("接口")
                || normalizedQuestion.contains("归属")
                || normalizedQuestion.contains("对应")
                || normalizedQuestion.contains("多少")
                || normalizedQuestion.contains("几")
                || normalizedQuestion.contains("数值")
                || normalizedQuestion.contains("值")
                || normalizedQuestion.contains("分别");
    }

    /**
     * 判断当前问题是否属于精确查值/精确结论类问题。
     *
     * @param question 用户问题
     * @return 精确查值题返回 true
     */
    boolean looksLikeExactLookupQuestion(String question) {
        String normalizedQuestion = lowerCase(question);
        return looksLikeStructuredFactQuestion(question)
                || looksLikeRuleConstraintQuestion(question)
                || looksLikeChangeTrackingQuestion(question)
                || normalizedQuestion.contains("是否一致")
                || normalizedQuestion.contains("是否生效")
                || normalizedQuestion.contains("是否启用");
    }

    /**
     * 判断问题是否同时在问多种结构化维度，而不是单一查值。
     *
     * @param question 用户问题
     * @return 多维查值题返回 true
     */
    boolean looksLikeCompoundExactLookupQuestion(String question) {
        String normalizedQuestion = lowerCase(question);
        int dimensionCount = 0;
        if (containsPathSignal(normalizedQuestion)) {
            dimensionCount++;
        }
        if (containsBatchOrOrdinalSignal(normalizedQuestion)) {
            dimensionCount++;
        }
        if (containsChangeTrackingSignal(normalizedQuestion)) {
            dimensionCount++;
        }
        if (containsCorrectionOrStatusSignal(normalizedQuestion)) {
            dimensionCount++;
        }
        if (looksLikeNumericQuestion(question)) {
            dimensionCount++;
        }
        return dimensionCount >= 2;
    }

    /**
     * 判断当前问题是否主要在问具体数值。
     *
     * @param question 用户问题
     * @return 数值题返回 true
     */
    boolean looksLikeNumericQuestion(String question) {
        String normalizedQuestion = lowerCase(question);
        return normalizedQuestion.contains("多少")
                || normalizedQuestion.contains("几")
                || normalizedQuestion.contains("命中数")
                || normalizedQuestion.contains("数值")
                || normalizedQuestion.contains("值")
                || normalizedQuestion.contains("阈值")
                || normalizedQuestion.contains("窗口")
                || normalizedQuestion.contains("分别");
    }

    /**
     * 判断候选句是否带有总数/修正结论信号。
     *
     * @param normalizedLine 归一化候选句
     * @return 命中返回 true
     */
    boolean containsCountConclusionSignal(String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        String lowerCaseLine = lowerCase(normalizedLine);
        return lowerCaseLine.contains("只有")
                || lowerCaseLine.contains("一共")
                || lowerCaseLine.contains("总计")
                || lowerCaseLine.contains("合计")
                || lowerCaseLine.contains("共 ")
                || lowerCaseLine.contains("共")
                || lowerCaseLine.contains("不是")
                || lowerCaseLine.contains("修正");
    }

    /**
     * 判断候选句是否包含规则、命名或约束类原始事实信号。
     *
     * @param normalizedLine 归一化候选句
     * @return 命中返回 true
     */
    boolean containsRuleConstraintSignal(String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        String lowerCaseLine = lowerCase(normalizedLine);
        return lowerCaseLine.contains("命名规范")
                || lowerCaseLine.contains("格式")
                || lowerCaseLine.contains("统一")
                || lowerCaseLine.contains("采用")
                || lowerCaseLine.contains("规则")
                || lowerCaseLine.contains("约束");
    }

    /**
     * 判断问题是否强调当前值或当前口径。
     *
     * @param normalizedQuestion 归一化问题
     * @return 当前事实题返回 true
     */
    boolean looksLikeCurrentFactQuestion(String normalizedQuestion) {
        if (normalizedQuestion == null || normalizedQuestion.isBlank()) {
            return false;
        }
        return normalizedQuestion.contains("当前")
                || normalizedQuestion.contains("现在")
                || normalizedQuestion.contains("目前")
                || normalizedQuestion.contains("最新");
    }

    /**
     * 判断候选句是否带有当前值、建议值或生效口径信号。
     *
     * @param lowerCaseLine 归一化候选句
     * @return 当前事实信号返回 true
     */
    boolean containsCurrentFactSignal(String lowerCaseLine) {
        if (lowerCaseLine == null || lowerCaseLine.isBlank()) {
            return false;
        }
        return lowerCaseLine.contains("当前")
                || lowerCaseLine.contains("现在")
                || lowerCaseLine.contains("目前")
                || lowerCaseLine.contains("最新")
                || lowerCaseLine.contains("建议值")
                || lowerCaseLine.contains("生效")
                || lowerCaseLine.contains("现行");
    }

    /**
     * 判断候选句是否包含“必须 / 禁止 / 强约束”这类强限制语义。
     *
     * @param normalizedLine 归一化候选句
     * @return 命中返回 true
     */
    boolean containsStrongConstraintSignal(String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        String lowerCaseLine = lowerCase(normalizedLine);
        return lowerCaseLine.contains("强约束")
                || lowerCaseLine.contains("禁止")
                || lowerCaseLine.contains("必须")
                || lowerCaseLine.contains("不得");
    }

    /**
     * 判断候选句是否带有修正 / 合并 / 删除 / 改为 / 承接变化等变更语义。
     *
     * @param normalizedLine 归一化候选句
     * @return 命中返回 true
     */
    boolean containsChangeTrackingSignal(String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        String lowerCaseLine = lowerCase(normalizedLine);
        return lowerCaseLine.contains("修正")
                || lowerCaseLine.contains("改为")
                || lowerCaseLine.contains("删除")
                || lowerCaseLine.contains("合并")
                || lowerCaseLine.contains("并入")
                || lowerCaseLine.contains("调整")
                || lowerCaseLine.contains("承接")
                || lowerCaseLine.contains("保持不变");
    }

    /**
     * 判断候选句是否带有“X = Y / A -> B / A→B”这类映射或重排信号。
     *
     * @param normalizedLine 归一化候选句
     * @return 命中返回 true
     */
    boolean containsAssignmentLikeMappingSignal(String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        return normalizedLine.contains(" = ")
                || normalizedLine.contains("→")
                || normalizedLine.contains("->");
    }

    /**
     * 判断候选句是否更像与主问题无关的相邻枚举项。
     *
     * @param normalizedLine 归一化候选句
     * @param question 用户问题
     * @return 相邻枚举噪音返回 true
     */
    boolean looksLikeAdjacentEnumerationNoise(String normalizedLine, String question) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        String lowerCaseLine = lowerCase(normalizedLine);
        if (!lowerCaseLine.matches("^\\d+\\..*")) {
            return false;
        }
        List<String> questionTokens = QueryEvidenceRelevanceSupport.extractHighSignalTokens(question);
        int matchedTokenCount = 0;
        for (String questionToken : questionTokens) {
            if (questionToken != null
                    && !questionToken.isBlank()
                    && lowerCaseLine.contains(lowerCase(questionToken))) {
                matchedTokenCount++;
            }
        }
        return matchedTokenCount == 0;
    }

    /**
     * 判断当前问题是否主要在问接口/文件/HTTP 路径。
     *
     * @param question 用户问题
     * @return 路径题返回 true
     */
    boolean looksLikePathQuestion(String question) {
        String normalizedQuestion = lowerCase(question);
        return normalizedQuestion.contains("路径")
                || normalizedQuestion.contains("接口")
                || normalizedQuestion.contains("endpoint")
                || normalizedQuestion.contains("url");
    }

    /**
     * 判断当前问题是否更像“启动前需要先做哪些准备/步骤”的 setup checklist 题。
     *
     * @param question 用户问题
     * @return setup 题返回 true
     */
    boolean looksLikeSetupChecklistQuestion(String question) {
        String normalizedQuestion = lowerCase(question);
        return (normalizedQuestion.contains("启动前")
                || (normalizedQuestion.contains("启动") && normalizedQuestion.contains("之前"))
                || (normalizedQuestion.contains("启动") && normalizedQuestion.contains("先")))
                && (normalizedQuestion.contains("需要")
                || normalizedQuestion.contains("配置")
                || normalizedQuestion.contains("准备")
                || normalizedQuestion.contains("顺序"));
    }

    /**
     * 判断当前问题是否更像“规则 / 命名 / 约束 / 格式”这类题。
     *
     * @param question 用户问题
     * @return 规则题返回 true
     */
    boolean looksLikeRuleConstraintQuestion(String question) {
        String normalizedQuestion = lowerCase(question);
        return normalizedQuestion.contains("规范")
                || normalizedQuestion.contains("规则")
                || normalizedQuestion.contains("命名")
                || normalizedQuestion.contains("格式")
                || normalizedQuestion.contains("约束")
                || normalizedQuestion.contains("原则")
                || normalizedQuestion.contains("契约")
                || normalizedQuestion.contains("怎么处理")
                || normalizedQuestion.contains("必须")
                || normalizedQuestion.contains("禁止");
    }

    /**
     * 判断当前问题是否更像“修正 / 变更 / 合并 / 重排 / 承接变化”这类题。
     *
     * @param question 用户问题
     * @return 变更题返回 true
     */
    boolean looksLikeChangeTrackingQuestion(String question) {
        String normalizedQuestion = lowerCase(question);
        return normalizedQuestion.contains("修正后")
                || normalizedQuestion.contains("调整后")
                || normalizedQuestion.contains("原来的")
                || normalizedQuestion.contains("怎么处理")
                || normalizedQuestion.contains("合并")
                || normalizedQuestion.contains("并入")
                || normalizedQuestion.contains("改为")
                || normalizedQuestion.contains("变成")
                || normalizedQuestion.contains("删除")
                || normalizedQuestion.contains("承接");
    }

    /**
     * 判断当前问题是否更像“支持哪些方式 / 入口 / 能力”这类能力枚举题。
     *
     * @param question 用户问题
     * @return 能力题返回 true
     */
    boolean looksLikeCapabilityQuestion(String question) {
        String normalizedQuestion = lowerCase(question);
        return normalizedQuestion.contains("支持")
                || normalizedQuestion.contains("接入")
                || normalizedQuestion.contains("入口")
                || normalizedQuestion.contains("方式")
                || normalizedQuestion.contains("能力")
                || normalizedQuestion.contains("有哪些");
    }

    /**
     * 判断当前问题是否在要求枚举多个事实项。
     *
     * @param question 用户问题
     * @return 枚举题返回 true
     */
    boolean looksLikeEnumerationQuestion(String question) {
        String normalizedQuestion = lowerCase(question);
        return normalizedQuestion.contains("有哪些")
                || normalizedQuestion.contains("哪些")
                || normalizedQuestion.contains("几种")
                || normalizedQuestion.contains("几个")
                || normalizedQuestion.contains("列出")
                || normalizedQuestion.contains("包括")
                || normalizedQuestion.contains("包含")
                || normalizedQuestion.contains("分别")
                || normalizedQuestion.contains("技巧")
                || normalizedQuestion.contains("形态")
                || normalizedQuestion.contains("字段")
                || normalizedQuestion.contains("渠道")
                || normalizedQuestion.contains("步骤")
                || normalizedQuestion.contains("主要完成");
    }

    /**
     * 判断当前问题是否在要求对比差异。
     *
     * @param question 用户问题
     * @return 对比题返回 true
     */
    boolean looksLikeComparisonQuestion(String question) {
        String normalizedQuestion = lowerCase(question);
        return normalizedQuestion.contains("差异")
                || normalizedQuestion.contains("区别")
                || normalizedQuestion.contains("不同")
                || normalizedQuestion.contains("对比")
                || normalizedQuestion.contains("比较");
    }

    /**
     * 判断当前问题是否更像“当前状态/是否可用/是否已就绪”这类状态题。
     *
     * @param question 用户问题
     * @return 状态题返回 true
     */
    boolean looksLikeStatusQuestion(String question) {
        String normalizedQuestion = lowerCase(question);
        boolean explicitStatusQuestion = normalizedQuestion.contains("状态")
                || normalizedQuestion.contains("可用")
                || normalizedQuestion.contains("是否可用")
                || normalizedQuestion.contains("是否已经")
                || normalizedQuestion.contains("是否已")
                || normalizedQuestion.contains("是否启用")
                || normalizedQuestion.contains("就绪")
                || normalizedQuestion.contains("实现状态")
                || normalizedQuestion.contains("当前是否")
                || normalizedQuestion.contains("实际已有")
                || normalizedQuestion.contains("待配置")
                || normalizedQuestion.contains("是否正常");
        if (explicitStatusQuestion) {
            return true;
        }
        if (looksLikeEnumerationQuestion(question) || looksLikeStructuredFactQuestion(question)) {
            return false;
        }
        return normalizedQuestion.contains("现在") || normalizedQuestion.contains("当前");
    }

    /**
     * 判断当前问题是否更像“运行流程 / 主链路 / 步骤”这类链路题。
     *
     * @param question 用户问题
     * @return 链路题返回 true
     */
    boolean looksLikeFlowQuestion(String question) {
        String normalizedQuestion = lowerCase(question);
        return normalizedQuestion.contains("流程")
                || normalizedQuestion.contains("链路")
                || normalizedQuestion.contains("步骤")
                || normalizedQuestion.contains("发送")
                || normalizedQuestion.contains("投递")
                || normalizedQuestion.contains("转发")
                || normalizedQuestion.contains("队列")
                || normalizedQuestion.contains("topic")
                || normalizedQuestion.contains("queue")
                || normalizedQuestion.contains("怎么跑")
                || normalizedQuestion.contains("怎么走")
                || normalizedQuestion.contains("运行路径");
    }
}
