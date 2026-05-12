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
 * 答案生成 问题类型支持
 *
 * 职责：承载 AnswerGenerationService 拆分出来的同类辅助逻辑
 *
 * @author xiexu
 */
@Slf4j
abstract class AnswerGenerationQuestionTypeSupport extends AnswerGenerationQuestionTypeBasicSupport {

    /**
     * 创建无 LLM 的答案生成拆分支持。
     */
    AnswerGenerationQuestionTypeSupport() {
        super();
    }

    /**
     * 创建答案生成拆分支持。
     *
     * @param llmGateway LLM 网关
     */
    AnswerGenerationQuestionTypeSupport(LlmGateway llmGateway) {
        super(llmGateway);
    }

    boolean looksLikeStructuredFactCandidate(String question, String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        int assignmentDelimiterIndex = answerEvidenceNormalizer.structuredAssignmentDelimiterIndex(normalizedLine);
        if (assignmentDelimiterIndex > 0) {
            String assignmentKey = normalizedLine.substring(0, assignmentDelimiterIndex).trim();
            String assignmentValue = structuredAssignmentValue(normalizedLine, assignmentDelimiterIndex);
            if (assignmentValue.isBlank() || !answerEvidenceNormalizer.looksLikeScalarTableValue(assignmentValue)) {
                return false;
            }
            return answerEvidenceNormalizer.looksLikeConfigFactKey(assignmentKey)
                    || (looksLikeStructuredFactQuestion(question)
                    && matchesStructuredFactFocusToken(question, assignmentKey)
            );
        }
        if (!looksLikeStructuredFactQuestion(question)) {
            return false;
        }
        return answerEvidenceNormalizer.looksLikeConfigFactKey(normalizedLine)
                && normalizedLine.matches(".*\\d.*")
                && !looksLikeOrdinalListLine(normalizedLine);
    }

    /**
     * 判断候选句是否只是编号列表项，而不是配置键或标签值事实。
     *
     * @param normalizedLine 归一化候选句
     * @return 编号列表项返回 true
     */
    boolean looksLikeOrdinalListLine(String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        return normalizedLine.trim().matches("\\d+[.)、].*");
    }

    /**
     * 判断结构化事实键是否命中用户问题里的焦点。
     *
     * @param question 用户问题
     * @param assignmentKey 结构化事实键
     * @return 命中焦点返回 true
     */
    boolean matchesStructuredFactFocusToken(String question, String assignmentKey) {
        if (assignmentKey == null || assignmentKey.isBlank()) {
            return false;
        }
        String normalizedAssignmentKey = lowerCase(assignmentKey);
        for (String focusToken : extractStructuredFactFocusTokens(question)) {
            String normalizedFocusToken = lowerCase(focusToken);
            if (!normalizedFocusToken.isBlank()
                    && (normalizedAssignmentKey.contains(normalizedFocusToken)
                    || normalizedFocusToken.contains(normalizedAssignmentKey))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 推导当前问题最希望直接回答几条结构化事实。
     *
     * @param question 用户问题
     * @return 期望条数
     */
    int desiredStructuredFactCount(String question) {
        String normalizedQuestion = lowerCase(question);
        int requestedShapeCount = 1;
        if (normalizedQuestion.contains("count") || normalizedQuestion.matches("(?s).*\\d+.*")) {
            requestedShapeCount++;
        }
        if (normalizedQuestion.contains("status") || normalizedQuestion.contains("state")) {
            requestedShapeCount++;
        }
        if (expectsBatchOrOrdinalAnswer(normalizedQuestion)) {
            requestedShapeCount++;
        }
        if (requestedShapeCount > 1) {
            if (containsMultiFocusSeparator(normalizedQuestion)) {
                int focusTokenCount = extractStructuredFactFocusTokens(question).size();
                if (focusTokenCount > requestedShapeCount) {
                    return Math.min(6, focusTokenCount);
                }
            }
            return Math.min(4, requestedShapeCount);
        }
        if (containsMultiFocusSeparator(normalizedQuestion)) {
            int focusTokenCount = extractStructuredFactFocusTokens(question).size();
            if (focusTokenCount > 0) {
                return Math.min(6, Math.max(2, focusTokenCount));
            }
            return 2;
        }
        if (looksLikeNumericQuestion(question) && containsMultiFocusSeparator(normalizedQuestion)) {
            return 2;
        }
        return 1;
    }

    /**
     * 推导 fallback 结论最多应保留几条事实句。
     *
     * @param question 用户问题
     * @return 期望事实句数量
     */
    int desiredFallbackConclusionSnippetCount(String question) {
        String normalizedQuestion = lowerCase(question);
        if (looksLikeStructuredFactQuestion(question)
                && (looksLikeNumericQuestion(question)
                || containsMultiFocusSeparator(normalizedQuestion)
                || normalizedQuestion.contains("status")
                || normalizedQuestion.contains("state")
                || normalizedQuestion.contains("value"))) {
            return desiredStructuredFactCount(question);
        }
        if (looksLikeCapabilityQuestion(question)
                && !normalizedQuestion.contains("fields")
                && !normalizedQuestion.contains("steps")) {
            return 1;
        }
        if (normalizedQuestion.contains("steps")) {
            return 8;
        }
        if (normalizedQuestion.contains("fields")) {
            return 6;
        }
        if (looksLikeEnumerationQuestion(question)) {
            return 6;
        }
        return desiredStructuredFactCount(question);
    }

    /**
     * 从"X 和 Y 分别是多少"这类题目里提取需要覆盖的结构化焦点。
     *
     * @param question 用户问题
     * @return 焦点列表
     */
    List<String> extractStructuredFactFocusTokens(String question) {
        List<String> focusTokens = new ArrayList<String>();
        if (question == null || question.isBlank() || !looksLikeStructuredFactQuestion(question)) {
            return focusTokens;
        }
        if (!shouldExtractStructuredFactFocusTokens(question)) {
            return focusTokens;
        }
        String[] rawSegments = question.split("[,/&+;、，]");
        for (String rawSegment : rawSegments) {
            String focusToken = cleanupStructuredFactQuestionSegment(rawSegment);
            if (focusToken.isBlank()) {
                continue;
            }
            for (String subToken : focusToken.split("和|与|以及|及")) {
                String trimmed = subToken.trim();
                if (!trimmed.isBlank() && !focusTokens.contains(trimmed)) {
                    focusTokens.add(trimmed);
                }
            }
        }
        if (!focusTokens.isEmpty()) {
            return focusTokens;
        }
        for (String queryToken : QueryEvidenceRelevanceSupport.extractHighSignalTokens(question)) {
            String normalizedToken = cleanupStructuredFactQuestionSegment(queryToken);
            if (normalizedToken.isBlank() || focusTokens.contains(normalizedToken)) {
                continue;
            }
            focusTokens.add(normalizedToken);
            if (focusTokens.size() >= 2) {
                break;
            }
        }
        return focusTokens;
    }

    /**
     * 判断当前问题是否真的在要求按多个结构化焦点分别取值。
     *
     * @param question 用户问题
     * @return 需要拆焦点返回 true
     */
    boolean shouldExtractStructuredFactFocusTokens(String question) {
        String normalizedQuestion = lowerCase(question);
        if (containsMultiFocusSeparator(normalizedQuestion)) {
            return true;
        }
        return normalizedQuestion.matches("(?s).*[A-Za-z0-9._-]+\\s*(?:,|/|&|\\+)\\s*[A-Za-z0-9._-]+.*")
                && (normalizedQuestion.contains("value")
                || normalizedQuestion.contains("config")
                || normalizedQuestion.contains("parameter"));
    }

    /**
     * 清理结构化问题片段里的疑问词与语气词，保留真正需要回答的配置项/指标名。
     *
     * @param rawSegment 原始问题片段
     * @return 清理后的焦点
     */
    String cleanupStructuredFactQuestionSegment(String rawSegment) {
        String normalizedSegment = lowerCase(rawSegment);
        if (normalizedSegment.isBlank()) {
            return "";
        }
        normalizedSegment = removeLeadingPossessiveScope(normalizedSegment);
        normalizedSegment = normalizedSegment.replace("current", " ");
        normalizedSegment = normalizedSegment.replace("latest", " ");
        normalizedSegment = normalizedSegment.replace("value", " ");
        normalizedSegment = normalizedSegment.replace("parameter", " ");
        normalizedSegment = normalizedSegment.replace("config", " ");
        normalizedSegment = normalizedSegment.replace("what is", " ");
        normalizedSegment = normalizedSegment.replace("分别是什么", " ");
        normalizedSegment = normalizedSegment.replace("分别是多少", " ");
        normalizedSegment = normalizedSegment.replace("分别是", " ");
        normalizedSegment = normalizedSegment.replace("各是什么", " ");
        normalizedSegment = normalizedSegment.replace("是什么", " ");
        normalizedSegment = normalizedSegment.replace("有多少", " ");
        normalizedSegment = normalizedSegment.replace("多少", " ");
        normalizedSegment = normalizedSegment.replace("现在", " ");
        normalizedSegment = normalizedSegment.replace("目前", " ");
        normalizedSegment = normalizedSegment.replaceAll("[？?。！!：:（）()\"\"'`]", " ");
        normalizedSegment = normalizedSegment.replaceAll("\\s+", " ").trim();
        if (normalizedSegment.isBlank()) {
            return "";
        }
        if (answerEvidenceNormalizer.looksLikeConfigFactKey(normalizedSegment) || normalizedSegment.length() >= 2) {
            return normalizedSegment;
        }
        return "";
    }

    /**
     * 去掉"某批次/某系统的 X"这类片段里的前置范围，只保留真正要取值的 X。
     *
     * @param normalizedSegment 已归一化的问题片段
     * @return 去掉前置范围后的片段
     */
    String removeLeadingPossessiveScope(String normalizedSegment) {
        if (normalizedSegment == null || normalizedSegment.isBlank()) {
            return "";
        }
        int possessiveIndex = normalizedSegment.lastIndexOf(" of ");
        if (possessiveIndex > 0 && possessiveIndex < normalizedSegment.length() - 1) {
            String scopePart = normalizedSegment.substring(0, possessiveIndex).trim();
            String focusPart = normalizedSegment.substring(possessiveIndex + 4).trim();
            if (!scopePart.isBlank() && !focusPart.isBlank()) {
                return focusPart;
            }
        }
        possessiveIndex = normalizedSegment.indexOf("的");
        if (possessiveIndex > 0 && possessiveIndex < normalizedSegment.length() - 1) {
            String scopePart = normalizedSegment.substring(0, possessiveIndex).trim();
            String focusPart = normalizedSegment.substring(possessiveIndex + 1).trim();
            if (!scopePart.isBlank() && !focusPart.isBlank()) {
                return focusPart;
            }
        }
        return normalizedSegment;
    }

    /**
     * 当问题已用中文枚举标点列出了多个焦点时，不应优先走批次/序号候选路径，
     * 否则 label-value 行会被"QA_BATCH_01"这类范围标识抢占。
     *
     * @param normalizedQuestion 归一化问题
     * @return 期望批次/序号答案返回 true
     */
    @Override
    boolean expectsBatchOrOrdinalAnswer(String normalizedQuestion) {
        if (normalizedQuestion != null
                && (normalizedQuestion.contains("、") || normalizedQuestion.contains("，"))) {
            return false;
        }
        return super.expectsBatchOrOrdinalAnswer(normalizedQuestion);
    }

    /**
     * 判断问题是否带有多焦点分隔符。
     *
     * @param normalizedQuestion 归一化问题
     * @return 多焦点返回 true
     */
    private boolean containsMultiFocusSeparator(String normalizedQuestion) {
        return normalizedQuestion != null
                && (normalizedQuestion.contains(",")
                || normalizedQuestion.contains("/")
                || normalizedQuestion.contains("&")
                || normalizedQuestion.contains("+")
                || normalizedQuestion.contains("、")
                || normalizedQuestion.contains("，")
                || querySemanticRules.containsAnyMultiFocusSeparator(normalizedQuestion));
    }
}
