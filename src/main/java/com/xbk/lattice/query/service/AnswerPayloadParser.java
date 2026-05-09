package com.xbk.lattice.query.service;

import com.xbk.lattice.shared.json.JsonMappers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xbk.lattice.query.domain.AnswerOutcome;
import com.xbk.lattice.query.domain.QueryAnswerPayload;

import java.util.List;

/**
 * 答案载荷解析器
 *
 * 职责：解析结构化 LLM 输出，并把 answerMarkdown / outcome / cacheable 收敛为领域载荷
 *
 * @author xiexu
 */
public class AnswerPayloadParser {

    private static final ObjectMapper OBJECT_MAPPER = JsonMappers.defaultMapper();

    private final AnswerGenerationService support;

    private final AnswerPostProcessor answerPostProcessor;

    /**
     * 创建答案载荷解析器。
     *
     * @param support 答案生成支撑逻辑
     * @param answerPostProcessor 答案后处理器
     */
    public AnswerPayloadParser(AnswerGenerationService support, AnswerPostProcessor answerPostProcessor) {
        this.support = support;
        this.answerPostProcessor = answerPostProcessor;
    }

    /**
     * 解析结构化问答输出，并收敛为最小答案载荷。
     *
     * @param rawPayload 原始输出
     * @param question 用户问题
     * @param queryArticleHits 查询命中
     * @return 结构化答案载荷；若无法解析则返回 null
     */
    public QueryAnswerPayload parseStructuredAnswerPayload(
            String rawPayload,
            String question,
            List<QueryArticleHit> queryArticleHits
    ) {
        JsonNode payloadNode = tryReadStructuredPayload(rawPayload);
        if (payloadNode == null) {
            return null;
        }
        String answerMarkdown = readText(payloadNode, "answerMarkdown");
        AnswerOutcome answerOutcome = readAnswerOutcome(payloadNode, "answerOutcome");
        if (answerMarkdown == null || answerMarkdown.isBlank() || answerOutcome == null) {
            return null;
        }
        answerMarkdown = answerPostProcessor.normalizeStructuredAnswerMarkdown(answerMarkdown, question, queryArticleHits);
        answerMarkdown = answerPostProcessor.compressStructuredExactLookupAnswer(answerMarkdown, question);
        answerOutcome = support.normalizeStructuredAnswerOutcome(answerOutcome, answerMarkdown, question, queryArticleHits);
        if (!support.containsCitationLiteral(answerMarkdown)) {
            return null;
        }
        boolean answerCacheable = readBoolean(payloadNode, "answerCacheable");
        if (answerOutcome != AnswerOutcome.SUCCESS) {
            answerCacheable = false;
        }
        QueryAnswerPayload answerPayload = QueryAnswerPayload.llm(
                SensitiveTextMasker.mask(answerMarkdown.trim()),
                answerOutcome,
                answerCacheable
        );
        return support.preferDeterministicExactLookupPayload(question, queryArticleHits, answerPayload);
    }

    /**
     * 尝试把原始输出解析成 JSON 节点。
     *
     * @param rawPayload 原始输出
     * @return JSON 节点
     */
    private JsonNode tryReadStructuredPayload(String rawPayload) {
        if (rawPayload == null || rawPayload.isBlank()) {
            return null;
        }
        String trimmedPayload = rawPayload.trim();
        JsonNode payloadNode = readJsonNode(trimmedPayload);
        if (payloadNode != null) {
            return payloadNode;
        }
        String normalizedPayload = stripMarkdownCodeFence(trimmedPayload);
        if (!normalizedPayload.equals(trimmedPayload)) {
            payloadNode = readJsonNode(normalizedPayload);
            if (payloadNode != null) {
                return payloadNode;
            }
        }
        String jsonSlice = extractJsonObject(rawPayload);
        if (jsonSlice == null || jsonSlice.isBlank()) {
            return null;
        }
        return readJsonNode(jsonSlice);
    }

    /**
     * 把文本解析为 JSON 节点。
     *
     * @param content 文本内容
     * @return JSON 节点
     */
    private JsonNode readJsonNode(String content) {
        try {
            return OBJECT_MAPPER.readTree(content);
        }
        catch (Exception ex) {
            return null;
        }
    }

    /**
     * 去掉 Markdown 代码块包裹。
     *
     * @param content 文本内容
     * @return 归一化后的文本
     */
    private String stripMarkdownCodeFence(String content) {
        String normalizedContent = content;
        if (normalizedContent.startsWith("```json")) {
            normalizedContent = normalizedContent.substring("```json".length()).trim();
        }
        else if (normalizedContent.startsWith("```")) {
            normalizedContent = normalizedContent.substring("```".length()).trim();
        }
        if (normalizedContent.endsWith("```")) {
            normalizedContent = normalizedContent.substring(0, normalizedContent.length() - 3).trim();
        }
        return normalizedContent;
    }

    /**
     * 从混合文本中提取最外层 JSON 对象。
     *
     * @param content 原始内容
     * @return JSON 文本
     */
    private String extractJsonObject(String content) {
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        return content.substring(start, end + 1);
    }

    /**
     * 读取文本字段。
     *
     * @param payloadNode JSON 节点
     * @param fieldName 字段名
     * @return 文本字段
     */
    private String readText(JsonNode payloadNode, String fieldName) {
        JsonNode fieldNode = payloadNode.get(fieldName);
        if (fieldNode == null || fieldNode.isNull()) {
            return null;
        }
        return fieldNode.asText();
    }

    /**
     * 读取布尔字段。
     *
     * @param payloadNode JSON 节点
     * @param fieldName 字段名
     * @return 布尔值
     */
    private boolean readBoolean(JsonNode payloadNode, String fieldName) {
        JsonNode fieldNode = payloadNode.get(fieldName);
        if (fieldNode == null || fieldNode.isNull()) {
            return false;
        }
        return fieldNode.asBoolean(false);
    }

    /**
     * 读取答案语义字段。
     *
     * @param payloadNode JSON 节点
     * @param fieldName 字段名
     * @return 答案语义
     */
    private AnswerOutcome readAnswerOutcome(JsonNode payloadNode, String fieldName) {
        String fieldValue = readText(payloadNode, fieldName);
        if (fieldValue == null) {
            return null;
        }
        try {
            return AnswerOutcome.valueOf(fieldValue.trim());
        }
        catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
