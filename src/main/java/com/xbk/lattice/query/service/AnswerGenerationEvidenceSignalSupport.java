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
 * 答案生成 证据信号支持
 *
 * 职责：承载 AnswerGenerationService 拆分出来的同类辅助逻辑
 *
 * @author xiexu
 */
@Slf4j
abstract class AnswerGenerationEvidenceSignalSupport extends AnswerGenerationCoreSignalSupport {

    /**
     * 创建无 LLM 的答案生成拆分支持。
     */
    AnswerGenerationEvidenceSignalSupport() {
        super();
    }

    /**
     * 创建答案生成拆分支持。
     *
     * @param llmGateway LLM 网关
     */
    AnswerGenerationEvidenceSignalSupport(LlmGateway llmGateway) {
        super(llmGateway);
    }

    /**
     * 判断候选句是否更像“能力枚举 / 入口列表”这类直接回答。
     *
     * @param normalizedLine 归一化候选句
     * @return 能力信号返回 true
     */
    boolean containsCapabilitySignal(String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        String lowerCaseLine = lowerCase(normalizedLine);
        int listSeparatorCount = countOccurrences(normalizedLine, "、")
                + countOccurrences(normalizedLine, " / ")
                + countOccurrences(normalizedLine, "·");
        int backtickCount = countOccurrences(normalizedLine, "`");
        return lowerCaseLine.contains("api")
                || lowerCaseLine.contains("cli")
                || lowerCaseLine.contains("mcp")
                || lowerCaseLine.contains("http")
                || lowerCaseLine.contains("web")
                || lowerCaseLine.contains("sdk")
                || lowerCaseLine.contains("入口")
                || lowerCaseLine.contains("接入")
                || listSeparatorCount >= 2
                || backtickCount >= 4;
    }

    /**
     * 判断候选句是否包含“不同/差异/不一致/而非”这类对比结论信号。
     *
     * @param normalizedLine 归一化候选句
     * @return 对比信号返回 true
     */
    boolean containsComparisonSignal(String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        String lowerCaseLine = lowerCase(normalizedLine);
        return lowerCaseLine.contains("不同")
                || lowerCaseLine.contains("差异")
                || lowerCaseLine.contains("不一致")
                || lowerCaseLine.contains("而非")
                || lowerCaseLine.contains("不是")
                || lowerCaseLine.contains("仅")
                || lowerCaseLine.contains("但是")
                || lowerCaseLine.contains("区别");
    }

    /**
     * 判断候选句是否像“指标/字段 = 数值”的数值事实。
     *
     * @param normalizedLine 归一化候选句
     * @return 命中返回 true
     */
    boolean containsNumericAssignmentSignal(String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        int delimiterIndex = answerEvidenceNormalizer.structuredAssignmentDelimiterIndex(normalizedLine);
        if (delimiterIndex <= 0) {
            return normalizedLine.matches("(?s).*[:：=]\\s*[`*\"“”']*\\d{1,3}(?:,\\d{3})+.*");
        }
        String assignmentValue = structuredAssignmentValue(normalizedLine, delimiterIndex);
        return assignmentValue.matches("(?s).*\\d.*");
    }

    /**
     * 判断候选句是否同时覆盖了问题里的多个高信号标识。
     *
     * @param question 用户问题
     * @param normalizedLine 归一化候选句
     * @return 同时覆盖多个标识返回 true
     */
    boolean containsMultipleHighSignalQuestionTokens(String question, String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        int matchedCount = 0;
        String lowerCaseLine = lowerCase(normalizedLine);
        for (String highSignalToken : QueryEvidenceRelevanceSupport.extractHighSignalTokens(question)) {
            if (highSignalToken == null || highSignalToken.isBlank()) {
                continue;
            }
            if (lowerCaseLine.contains(lowerCase(highSignalToken))) {
                matchedCount++;
                if (matchedCount >= 2) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 判断若干贴题证据句里是否出现指定 token。
     *
     * @param snippets 证据句
     * @param token 待匹配 token
     * @return 任一证据句命中返回 true
     */
    boolean containsAnySnippetToken(List<String> snippets, String token) {
        if (snippets == null || snippets.isEmpty() || token == null || token.isBlank()) {
            return false;
        }
        for (String snippet : snippets) {
            if (snippet != null && snippet.contains(token)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断若干贴题证据句里是否至少包含一个数字。
     *
     * @param snippets 证据句
     * @return 任一证据句含数字返回 true
     */
    boolean containsAnySnippetDigit(List<String> snippets) {
        if (snippets == null || snippets.isEmpty()) {
            return false;
        }
        for (String snippet : snippets) {
            if (snippet != null && snippet.matches("(?s).*\\d.*")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断问题是否期待批次或序号类答案。
     *
     * @param normalizedQuestion 归一化问题
     * @return 期待返回 true
     */
    boolean expectsBatchOrOrdinalAnswer(String normalizedQuestion) {
        if (normalizedQuestion == null || normalizedQuestion.isBlank()) {
            return false;
        }
        return normalizedQuestion.contains("批")
                || normalizedQuestion.contains("第几")
                || normalizedQuestion.contains("哪一")
                || normalizedQuestion.contains("顺序");
    }

    /**
     * 判断若干证据句是否包含批次或序号信号。
     *
     * @param snippets 证据句
     * @return 命中返回 true
     */
    boolean containsAnyBatchOrOrdinalSignal(List<String> snippets) {
        if (snippets == null || snippets.isEmpty()) {
            return false;
        }
        for (String snippet : snippets) {
            if (containsBatchOrOrdinalSignal(snippet)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断文本是否包含批次或序号信号。
     *
     * @param value 文本
     * @return 命中返回 true
     */
    boolean containsBatchOrOrdinalSignal(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalizedValue = lowerCase(value);
        return normalizedValue.matches("(?s).*(?:第[一二三四五六七八九十0-9]+[批阶段步项条个]?|[一二三四五六七八九十0-9]+[批阶段步项条个]?).*")
                || normalizedValue.contains("批次")
                || normalizedValue.contains("顺序");
    }

    /**
     * 判断若干证据句是否包含状态信号。
     *
     * @param snippets 证据句
     * @return 命中返回 true
     */
    boolean containsAnyStatusSignal(List<String> snippets) {
        if (snippets == null || snippets.isEmpty()) {
            return false;
        }
        for (String snippet : snippets) {
            if (containsStatusSignal(lowerCase(snippet))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断若干证据句是否包含流程转移信号。
     *
     * @param snippets 证据句
     * @return 命中返回 true
     */
    boolean containsAnyFlowTransitionSignal(List<String> snippets) {
        if (snippets == null || snippets.isEmpty()) {
            return false;
        }
        for (String snippet : snippets) {
            if (containsFlowTransitionSignal(snippet)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断贴题证据句里是否出现“修正/确认/生效状态”这类结论信号。
     *
     * @param snippets 证据句
     * @return 命中结论信号返回 true
     */
    boolean containsAnyCorrectionOrStatusSignal(List<String> snippets) {
        if (snippets == null || snippets.isEmpty()) {
            return false;
        }
        for (String snippet : snippets) {
            if (containsCorrectionOrStatusSignal(lowerCase(snippet))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断单段文本是否包含“修正/确认/生效状态”这类结论信号。
     *
     * @param normalizedValue 归一化文本
     * @return 命中结论信号返回 true
     */
    boolean containsCorrectionOrStatusSignal(String normalizedValue) {
        if (normalizedValue == null || normalizedValue.isBlank()) {
            return false;
        }
        return normalizedValue.contains("修正为")
                || normalizedValue.contains("改为")
                || normalizedValue.contains("确认")
                || normalizedValue.contains("生效")
                || normalizedValue.contains("启用")
                || normalizedValue.contains("禁用")
                || normalizedValue.contains("结论");
    }

    /**
     * 判断候选句是否更像 setup/checklist 类前置步骤说明。
     *
     * @param normalizedLine 归一化候选句
     * @return setup 信号返回 true
     */
    boolean containsSetupSignal(String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        String lowerCaseLine = lowerCase(normalizedLine);
        return normalizedLine.matches("^\\d+\\.\\s+.*")
                || lowerCaseLine.contains("准备好")
                || lowerCaseLine.contains("确认")
                || lowerCaseLine.contains("创建")
                || lowerCaseLine.contains("schema")
                || lowerCaseLine.contains("profile")
                || lowerCaseLine.contains("环境变量")
                || lowerCaseLine.contains("容器")
                || lowerCaseLine.contains("启动")
                || lowerCaseLine.contains("顺序")
                || lowerCaseLine.contains("步骤")
                || lowerCaseLine.contains("前置")
                || lowerCaseLine.contains("依赖");
    }

    /**
     * 判断候选句是否更像“引导后续列表/说明”的 lead-in，而不是直接结论。
     *
     * @param normalizedLine 归一化候选句
     * @return 导入句返回 true
     */
    boolean looksLikeLeadInSentence(String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        String trimmedLine = normalizedLine.trim();
        if (!(trimmedLine.endsWith("：") || trimmedLine.endsWith(":"))) {
            return false;
        }
        String lowerCaseLine = lowerCase(trimmedLine);
        if (lowerCaseLine.contains("如下")
                || lowerCaseLine.contains("包括")
                || lowerCaseLine.contains("这些")
                || lowerCaseLine.contains("事情")
                || lowerCaseLine.contains("步骤")) {
            return true;
        }
        return !trimmedLine.contains("->")
                && !trimmedLine.matches(".*\\d.*")
                && !trimmedLine.contains("`");
    }

    /**
     * 判断候选句是否更像枚举项，而不是章节标题或导语。
     *
     * @param rawLine 原始行
     * @param normalizedLine 归一化候选句
     * @return 枚举事实项返回 true
     */
    boolean looksLikeEnumerationFactLine(String rawLine, String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        String normalizedRawLine = rawLine == null ? "" : rawLine.trim();
        if (normalizedRawLine.startsWith("|")
                || normalizedRawLine.startsWith("- ")
                || normalizedRawLine.startsWith("* ")
                || normalizedRawLine.startsWith("• ")
                || normalizedRawLine.matches("^\\d+\\.\\s+.*")) {
            return true;
        }
        if (answerEvidenceNormalizer.startsWithDirectStructuredFactAssignment(normalizedLine)) {
            return true;
        }
        if (countOccurrences(normalizedLine, "；") >= 2 || countOccurrences(normalizedLine, "、") >= 2) {
            return true;
        }
        return normalizedLine.contains("：") && !looksLikeLeadInSentence(normalizedLine);
    }

    /**
     * 判断候选句是否只是复述用户问题或章节目录标题。
     *
     * @param question 用户问题
     * @param normalizedLine 归一化候选句
     * @return 问题回声句返回 true
     */
    boolean looksLikeQuestionEchoLine(String question, String normalizedLine) {
        String questionEcho = normalizeQuestionEchoText(question);
        String lineEcho = normalizeQuestionEchoText(normalizedLine);
        if (questionEcho.length() < 6 || lineEcho.length() < 6) {
            return false;
        }
        return questionEcho.contains(lineEcho) || lineEcho.contains(questionEcho);
    }

    /**
     * 归一化文本用于判断问题回声。
     *
     * @param value 原始文本
     * @return 归一后的紧凑文本
     */
    String normalizeQuestionEchoText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return lowerCase(value)
                .replaceAll("[#*`|\\[\\]（）()“”\"'：:；;，,。！？?\\s\\t\\r\\n-]+", "")
                .replaceAll("\\d+$", "")
                .trim();
    }

    /**
     * 去掉有序列表前缀，便于把步骤项拼成自然语言。
     *
     * @param snippet 原始片段
     * @return 去前缀后的片段
     */
    String stripOrderedListMarker(String snippet) {
        if (snippet == null || snippet.isBlank()) {
            return "";
        }
        return snippet.replaceFirst("^\\d+\\.\\s*", "").trim();
    }

    /**
     * 判断候选句是否更像项目总述 / 价值判断，而不是直接答案。
     *
     * @param normalizedLine 归一化候选句
     * @return 总述句返回 true
     */
    boolean looksLikeGenericSummarySentence(String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        return normalizedLine.startsWith("换句话说")
                || normalizedLine.startsWith("本质上")
                || normalizedLine.contains("更像一个")
                || normalizedLine.contains("而不是一个");
    }

    /**
     * 统计子串出现次数。
     *
     * @param value 原始字符串
     * @param token 待统计子串
     * @return 出现次数
     */
    int countOccurrences(String value, String token) {
        if (value == null || value.isBlank() || token == null || token.isBlank()) {
            return 0;
        }
        int count = 0;
        int fromIndex = 0;
        while (fromIndex >= 0) {
            fromIndex = value.indexOf(token, fromIndex);
            if (fromIndex < 0) {
                break;
            }
            count++;
            fromIndex += token.length();
        }
        return count;
    }

    /**
     * 判断候选句是否更像章节标题，而不是可直接给用户的状态结论。
     *
     * @param rawLine 原始候选句
     * @return 标题类候选返回 true
     */
    boolean looksLikeHeadingOnlyFallbackLine(String rawLine) {
        if (rawLine == null) {
            return false;
        }
        String trimmedLine = rawLine.trim().toLowerCase(Locale.ROOT);
        return trimmedLine.startsWith("#")
                || trimmedLine.startsWith("<h1")
                || trimmedLine.startsWith("<h2")
                || trimmedLine.startsWith("<h3")
                || trimmedLine.startsWith("<h4");
    }

    String structuredAssignmentValue(String normalizedLine, int delimiterIndex) {
        if (normalizedLine == null || normalizedLine.isBlank() || delimiterIndex < 0) {
            return "";
        }
        if (normalizedLine.startsWith(" = ", delimiterIndex)) {
            return normalizedLine.substring(delimiterIndex + 3).trim();
        }
        if (normalizedLine.startsWith(": ", delimiterIndex)) {
            return normalizedLine.substring(delimiterIndex + 2).trim();
        }
        return normalizedLine.substring(delimiterIndex + 1).trim();
    }
}
