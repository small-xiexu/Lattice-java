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
 * 答案生成核心证据信号支持
 *
 * 职责：识别状态、流程、路径、结构化标签和机器标识符等底层证据信号
 *
 * @author xiexu
 */
@Slf4j
abstract class AnswerGenerationCoreSignalSupport extends AnswerGenerationBaseSupport {

    /**
     * 创建无 LLM 的核心证据信号支持。
     */
    AnswerGenerationCoreSignalSupport() {
        super();
    }

    /**
     * 创建核心证据信号支持。
     *
     * @param llmGateway LLM 网关
     */
    AnswerGenerationCoreSignalSupport(LlmGateway llmGateway) {
        super(llmGateway);
    }

boolean containsStatusSignal(String normalizedLine) {
        return normalizedLine.contains("available")
                || normalizedLine.contains("implemented")
                || normalizedLine.contains("configured")
                || normalizedLine.contains("enabled")
                || normalizedLine.contains("disabled")
                || normalizedLine.contains("ready")
                || normalizedLine.contains("missing")
                || normalizedLine.contains("pending");
    }

    /**
     * 判断候选句是否带有更像“主链路 / 流程说明”的信号词。
     *
     * @param normalizedLine 归一化候选句
     * @return 命中流程信号返回 true
     */
    boolean containsFlowSignal(String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        String lowerCaseLine = lowerCase(normalizedLine);
        return containsFlowTransitionSignal(normalizedLine)
                || (normalizedLine.contains("`") && (lowerCaseLine.contains("主链") || lowerCaseLine.contains("正式")))
                || lowerCaseLine.contains("主链路")
                || lowerCaseLine.contains("链路")
                || lowerCaseLine.contains("流程")
                || lowerCaseLine.contains("步骤")
                || lowerCaseLine.contains("阶段")
                || lowerCaseLine.contains("进入")
                || lowerCaseLine.contains("提交")
                || lowerCaseLine.contains("启动");
    }

    /**
     * 判断候选句是否像“发送方 -> 接收方 : 载荷”的流程转移事实。
     *
     * @param normalizedLine 归一化候选句
     * @return 命中返回 true
     */
    boolean containsFlowTransitionSignal(String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        return normalizedLine.matches("(?s).*([\\p{IsHan}A-Za-z][\\p{IsHan}A-Za-z0-9_ .-]*|\\[[^\\]]+])\\s*(?:->|→)\\s*([\\p{IsHan}A-Za-z][\\p{IsHan}A-Za-z0-9_ .-]*|\\[[^\\]]+])(?:\\s*[:：].*)?.*");
    }

    /**
     * 判断候选句是否包含带分隔符的机器标识符。
     *
     * @param normalizedLine 归一化候选句
     * @return 命中返回 true
     */
    boolean containsMachineIdentifierSignal(String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        return normalizedLine.matches("(?s).*[A-Za-z0-9]+[-_][A-Za-z0-9][A-Za-z0-9_-]*.*");
    }

    /**
     * 判断流程转移句是否覆盖了问题中的高信号词。
     *
     * @param question 用户问题
     * @param normalizedLine 归一化候选句
     * @return 命中返回 true
     */
    boolean containsQuestionTokenInFlowTransition(String question, String normalizedLine) {
        if (!containsFlowTransitionSignal(normalizedLine)) {
            return false;
        }
        String lowerCaseLine = lowerCase(normalizedLine);
        for (String token : QueryEvidenceRelevanceSupport.extractHighSignalTokens(question)) {
            String normalizedToken = lowerCase(token);
            if (!normalizedToken.isBlank() && lowerCaseLine.contains(normalizedToken)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断候选句是否包含类似 8A / 5G / C.10 的结构化标签。
     *
     * @param normalizedLine 归一化候选句
     * @return 命中返回 true
     */
    boolean containsStructuredLabelSignal(String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        return normalizedLine.matches("(?s).*\\b\\d+[A-Za-z]\\b.*");
    }

    /**
     * 判断候选句是否只是时序图声明，而不是一次实际转移动作。
     *
     * @param normalizedLine 归一化候选句
     * @return 声明行返回 true
     */
    boolean looksLikePlantUmlDeclarationLine(String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        String lowerCaseLine = lowerCase(normalizedLine).trim();
        return lowerCaseLine.startsWith("title ")
                || lowerCaseLine.startsWith("actor ")
                || lowerCaseLine.startsWith("participant ")
                || lowerCaseLine.startsWith("queue ")
                || lowerCaseLine.startsWith("note over ")
                || lowerCaseLine.startsWith("activate ")
                || lowerCaseLine.startsWith("deactivate ");
    }

    /**
     * 判断候选句是否包含更像路径题直接答案的信号。
     *
     * @param normalizedLine 归一化候选句
     * @return 命中路径信号返回 true
     */
    boolean containsPathSignal(String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        String lowerCaseLine = lowerCase(normalizedLine);
        return !extractEvidencePaths(List.of(normalizedLine)).isEmpty()
                || lowerCaseLine.contains("post ")
                || lowerCaseLine.contains("get ")
                || lowerCaseLine.contains("put ")
                || lowerCaseLine.contains("delete ")
                || lowerCaseLine.contains("http://")
                || lowerCaseLine.contains("https://");
    }

    /**
     * 判断候选句是否更像“接口路径 | 功能 | …”这类表头，而不是具体路径值。
     *
     * @param normalizedLine 归一化候选句
     * @return 表头行返回 true
     */
    boolean looksLikePathHeaderLine(String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        String lowerCaseLine = lowerCase(normalizedLine);
        return lowerCaseLine.contains("接口路径")
                && lowerCaseLine.contains("功能")
                && !containsPathSignal(lowerCaseLine.replace("接口路径", "").trim());
    }
}
