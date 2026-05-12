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
 * <p>
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
        return containsStructuredQuestionSignal(normalizedQuestion)
                || !QueryTokenExtractor.extractExactIdentifierTokens(question).isEmpty();
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
                || looksLikePathQuestion(question)
                || looksLikeRuleConstraintQuestion(question)
                || looksLikeChangeTrackingQuestion(question)
                || looksLikeNumericQuestion(question)
                || containsStructuredQuestionSignal(normalizedQuestion);
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
        return normalizedQuestion.matches("(?s).*\\d+.*")
                || normalizedQuestion.contains("count")
                || normalizedQuestion.contains("number")
                || normalizedQuestion.contains("value")
                || normalizedQuestion.contains("threshold")
                || normalizedQuestion.contains("window");
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
        return lowerCaseLine.matches("(?s).*\\d+.*")
                || answerEvidenceNormalizer.structuredAssignmentDelimiterIndex(lowerCaseLine) > 0;
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
        return lowerCaseLine.contains("rule")
                || lowerCaseLine.contains("policy")
                || lowerCaseLine.contains("constraint")
                || lowerCaseLine.contains("required")
                || lowerCaseLine.contains("forbidden");
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
        return normalizedQuestion.contains("current")
                || normalizedQuestion.contains("latest")
                || normalizedQuestion.contains("now");
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
        return lowerCaseLine.contains("current")
                || lowerCaseLine.contains("latest")
                || lowerCaseLine.contains("enabled")
                || lowerCaseLine.contains("active");
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
        return lowerCaseLine.contains("must")
                || lowerCaseLine.contains("required")
                || lowerCaseLine.contains("forbidden")
                || lowerCaseLine.contains("disallowed");
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
        return lowerCaseLine.contains("changed")
                || lowerCaseLine.contains("updated")
                || lowerCaseLine.contains("deleted")
                || lowerCaseLine.contains("merged")
                || lowerCaseLine.contains("renamed")
                || containsAssignmentLikeMappingSignal(lowerCaseLine);
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
     * @param question       用户问题
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
        return normalizedQuestion.contains("endpoint")
                || normalizedQuestion.contains("url")
                || !QueryTokenExtractor.extractExactIdentifierTokens(question).isEmpty()
                || querySemanticRules.containsAnyConfigIdentifierSignal(question);
    }

    /**
     * 判断当前问题是否更像“启动前需要先做哪些准备/步骤”的 setup checklist 题。
     *
     * @param question 用户问题
     * @return setup 题返回 true
     */
    boolean looksLikeSetupChecklistQuestion(String question) {
        String normalizedQuestion = lowerCase(question);
        return normalizedQuestion.contains("setup")
                || normalizedQuestion.contains("bootstrap")
                || normalizedQuestion.contains("before start");
    }

    /**
     * 判断当前问题是否更像“规则 / 命名 / 约束 / 格式”这类题。
     *
     * @param question 用户问题
     * @return 规则题返回 true
     */
    boolean looksLikeRuleConstraintQuestion(String question) {
        String normalizedQuestion = lowerCase(question);
        return normalizedQuestion.contains("policy")
                || normalizedQuestion.contains("rule")
                || normalizedQuestion.contains("constraint")
                || normalizedQuestion.contains("requirement")
                || normalizedQuestion.contains("must")
                || normalizedQuestion.contains("forbidden");
    }

    /**
     * 判断当前问题是否更像“修正 / 变更 / 合并 / 重排 / 承接变化”这类题。
     *
     * @param question 用户问题
     * @return 变更题返回 true
     */
    boolean looksLikeChangeTrackingQuestion(String question) {
        String normalizedQuestion = lowerCase(question);
        return normalizedQuestion.contains("changed")
                || normalizedQuestion.contains("updated")
                || normalizedQuestion.contains("deleted")
                || normalizedQuestion.contains("merged")
                || normalizedQuestion.contains("renamed")
                || normalizedQuestion.contains("before and after");
    }

    /**
     * 判断当前问题是否更像“支持哪些方式 / 入口 / 能力”这类能力枚举题。
     *
     * @param question 用户问题
     * @return 能力题返回 true
     */
    boolean looksLikeCapabilityQuestion(String question) {
        String normalizedQuestion = lowerCase(question);
        return normalizedQuestion.contains("capability")
                || normalizedQuestion.contains("support")
                || normalizedQuestion.contains("entry")
                || normalizedQuestion.contains("integration")
                || normalizedQuestion.contains("option")
                || querySemanticRules.containsAnyCapabilitySignal(question);
    }

    /**
     * 判断当前问题是否在要求枚举多个事实项。
     *
     * @param question 用户问题
     * @return 枚举题返回 true
     */
    boolean looksLikeEnumerationQuestion(String question) {
        String normalizedQuestion = lowerCase(question);
        return normalizedQuestion.contains("list")
                || normalizedQuestion.contains("items")
                || normalizedQuestion.contains("options")
                || normalizedQuestion.contains("types")
                || normalizedQuestion.contains("fields")
                || normalizedQuestion.contains("steps")
                || querySemanticRules.containsAnyEnumSignal(question);
    }

    /**
     * 判断当前问题是否在要求对比差异。
     *
     * @param question 用户问题
     * @return 对比题返回 true
     */
    boolean looksLikeComparisonQuestion(String question) {
        String normalizedQuestion = lowerCase(question);
        return normalizedQuestion.contains("compare")
                || normalizedQuestion.contains("comparison")
                || normalizedQuestion.contains("difference")
                || normalizedQuestion.contains(" vs ")
                || normalizedQuestion.contains(" versus ")
                || querySemanticRules.containsAnyComparisonSignal(question);
    }

    /**
     * 判断当前问题是否更像“当前状态/是否可用/是否已就绪”这类状态题。
     *
     * @param question 用户问题
     * @return 状态题返回 true
     */
    boolean looksLikeStatusQuestion(String question) {
        String normalizedQuestion = lowerCase(question);
        boolean explicitStatusQuestion = normalizedQuestion.contains("status")
                || normalizedQuestion.contains("state")
                || normalizedQuestion.contains("enabled")
                || normalizedQuestion.contains("ready")
                || normalizedQuestion.contains("available")
                || normalizedQuestion.contains("pending");
        if (explicitStatusQuestion) {
            return true;
        }
        if (looksLikeEnumerationQuestion(question) || looksLikeStructuredFactQuestion(question)) {
            return false;
        }
        return looksLikeCurrentFactQuestion(normalizedQuestion);
    }

    /**
     * 判断当前问题是否更像“运行流程 / 主链路 / 步骤”这类链路题。
     *
     * @param question 用户问题
     * @return 链路题返回 true
     */
    boolean looksLikeFlowQuestion(String question) {
        if (looksLikePathQuestion(question)) {
            return false;
        }
        String normalizedQuestion = lowerCase(question);
        return normalizedQuestion.contains("workflow")
                || normalizedQuestion.contains("process")
                || normalizedQuestion.contains("steps")
                || normalizedQuestion.contains("topic")
                || normalizedQuestion.contains("queue")
                || normalizedQuestion.contains("route")
                || querySemanticRules.containsAnyFlowSignal(question);
    }

    /**
     * 判断问题是否携带通用结构化取值信号。
     *
     * @param normalizedQuestion 归一化问题
     * @return 携带结构信号返回 true
     */
    private boolean containsStructuredQuestionSignal(String normalizedQuestion) {
        if (normalizedQuestion == null || normalizedQuestion.isBlank()) {
            return false;
        }
        return normalizedQuestion.contains("=")
                || normalizedQuestion.contains("/")
                || normalizedQuestion.contains(".")
                || normalizedQuestion.contains("endpoint")
                || normalizedQuestion.contains("url")
                || normalizedQuestion.contains("config")
                || normalizedQuestion.contains("parameter")
                || normalizedQuestion.contains("value")
                || normalizedQuestion.contains("count");
    }
}
