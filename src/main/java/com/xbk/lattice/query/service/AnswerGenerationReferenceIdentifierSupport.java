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
 * 答案生成引用标识支持
 *
 * 职责：识别路径契约、精确标识、引用型问题与问题中的目标标识符
 *
 * @author xiexu
 */
@Slf4j
abstract class AnswerGenerationReferenceIdentifierSupport extends AnswerGenerationQuestionTypeSupport {

    /**
     * 创建无 LLM 的拆分支持。
     */
    AnswerGenerationReferenceIdentifierSupport() {
        super();
    }

    /**
     * 创建拆分支持。
     *
     * @param llmGateway LLM 网关
     */
    AnswerGenerationReferenceIdentifierSupport(LlmGateway llmGateway) {
        super(llmGateway);
    }

    /**
     * 判断问题是否显式点名了需要解释的路径标识。
     *
     * @param question 用户问题
     * @return 点名路径标识返回 true
     */
    boolean containsRequestedExactPathIdentifier(String question) {
        return !extractRequestedPathIdentifiers(question).isEmpty();
    }

    /**
     * 提取问题中显式点名的路径标识。
     *
     * @param question 用户问题
     * @return 路径标识
     */
    List<String> extractRequestedPathIdentifiers(String question) {
        List<String> requestedPaths = new ArrayList<String>();
        for (String requestedIdentifier : extractRequestedReferentialIdentifiers(question)) {
            if (requestedIdentifier.contains("/")
                    && containsExactIdentifierSignal(requestedIdentifier)
                    && !requestedPaths.contains(requestedIdentifier)) {
                requestedPaths.add(requestedIdentifier);
            }
        }
        return requestedPaths;
    }

    boolean containsPathContractSignal(String normalizedLine) {
        if (normalizedLine == null || normalizedLine.isBlank()) {
            return false;
        }
        String lowerCaseLine = lowerCase(normalizedLine);
        boolean containsPathWord = lowerCaseLine.contains("path")
                || lowerCaseLine.contains("路径")
                || lowerCaseLine.contains("url")
                || lowerCaseLine.contains("endpoint")
                || lowerCaseLine.contains("接口契约")
                || lowerCaseLine.contains("接口路径");
        if (!containsPathWord) {
            return false;
        }
        return lowerCaseLine.contains("一致")
                || lowerCaseLine.contains("兼容")
                || lowerCaseLine.contains("保持")
                || lowerCaseLine.contains("不变")
                || lowerCaseLine.contains("原路径")
                || lowerCaseLine.contains("旧路径")
                || lowerCaseLine.contains("沿用")
                || lowerCaseLine.contains("对齐")
                || lowerCaseLine.contains("不得")
                || lowerCaseLine.contains("不能")
                || lowerCaseLine.contains("不允许")
                || lowerCaseLine.contains("不可")
                || lowerCaseLine.contains("必须")
                || lowerCaseLine.contains("契约")
                || lowerCaseLine.contains("字节级");
    }

    /**
     * 判断补充行是否引入了与用户点名路径无关的其他路径。
     *
     * @param question 用户问题
     * @param normalizedLine 归一化候选行
     * @return 引入无关路径返回 true
     */
    boolean introducesUnrequestedPathForExactPathQuestion(String question, String normalizedLine) {
        List<String> requestedPaths = extractRequestedPathIdentifiers(question);
        if (requestedPaths.isEmpty()) {
            return false;
        }
        List<String> evidencePaths = extractEvidencePaths(List.of(normalizedLine));
        if (evidencePaths.isEmpty()) {
            return false;
        }
        for (String evidencePath : evidencePaths) {
            if (!containsIdentifierIgnoreCase(requestedPaths, evidencePath)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从候选证据中查找命中任一关键词的记录。
     *
     * @param fallbackHits 候选证据
     * @param keywords 关键词
     * @return 命中的证据；没有则返回 null
     */
    QueryArticleHit findHitContainingAny(List<QueryArticleHit> fallbackHits, List<String> keywords) {
        if (fallbackHits == null || fallbackHits.isEmpty() || keywords == null || keywords.isEmpty()) {
            return null;
        }
        for (QueryArticleHit fallbackHit : fallbackHits) {
            String haystack = lowerCase(fallbackHit.getTitle())
                    + " "
                    + lowerCase(extractDescription(fallbackHit.getMetadataJson()))
                    + " "
                    + lowerCase(fallbackHit.getContent());
            for (String keyword : keywords) {
                String normalizedKeyword = lowerCase(keyword);
                if (!normalizedKeyword.isBlank() && haystack.contains(normalizedKeyword)) {
                    return fallbackHit;
                }
            }
        }
        return null;
    }

    /**
     * 判断文本是否包含所有关键词。
     *
     * @param value 文本
     * @param keywords 关键词
     * @return 全部包含返回 true
     */
    boolean containsAll(String value, List<String> keywords) {
        if (value == null || value.isBlank() || keywords == null || keywords.isEmpty()) {
            return false;
        }
        String haystack = lowerCase(value);
        for (String keyword : keywords) {
            String normalizedKeyword = lowerCase(keyword);
            if (normalizedKeyword.isBlank() || !haystack.contains(normalizedKeyword)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断问题是否属于字段名、状态码、枚举值、配置键等精确标识知识题。
     *
     * @param question 用户问题
     * @return 精确标识知识题返回 true
     */
    boolean looksLikeReferentialKnowledgeQuestion(String question) {
        String normalizedQuestion = lowerCase(question);
        List<String> requestedIdentifiers = extractRequestedReferentialIdentifiers(question);
        if (requestedIdentifiers.isEmpty()) {
            return false;
        }
        return normalizedQuestion.contains("字段")
                || normalizedQuestion.contains("状态码")
                || normalizedQuestion.contains("枚举")
                || normalizedQuestion.contains("配置")
                || normalizedQuestion.contains("参数")
                || normalizedQuestion.contains("报文")
                || normalizedQuestion.contains("接口")
                || normalizedQuestion.contains("分别")
                || normalizedQuestion.contains("表示")
                || normalizedQuestion.contains("含义")
                || normalizedQuestion.contains("定义");
    }

    /**
     * 判断问题是否带有必须命中的精确标识。
     *
     * @param question 用户问题
     * @return 严格精确标识题返回 true
     */
    boolean looksLikeStrictExactIdentifierQuestion(String question) {
        List<String> requestedIdentifiers = extractRequestedReferentialIdentifiers(question);
        for (String requestedIdentifier : requestedIdentifiers) {
            if (containsExactIdentifierSignal(requestedIdentifier)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断是否为显式点名多个标识并询问定义/含义的题目。
     *
     * @param question 用户问题
     * @return 聚焦精确标识定义题返回 true
     */
    boolean looksLikeFocusedReferentialDefinitionQuestion(String question) {
        if (!looksLikeReferentialKnowledgeQuestion(question)) {
            return false;
        }
        List<String> requestedIdentifiers = extractRequestedReferentialIdentifiers(question);
        if (requestedIdentifiers.size() < 2) {
            return false;
        }
        String normalizedQuestion = lowerCase(question);
        return normalizedQuestion.contains("分别")
                || normalizedQuestion.contains("表示")
                || normalizedQuestion.contains("含义")
                || normalizedQuestion.contains("定义")
                || normalizedQuestion.contains("字段")
                || normalizedQuestion.contains("状态码")
                || normalizedQuestion.contains("枚举");
    }

    /**
     * 提取问题中显式点名、需要逐项覆盖的精确标识。
     *
     * @param question 用户问题
     * @return 标识列表，保持用户问题中的出现顺序
     */
    List<String> extractRequestedReferentialIdentifiers(String question) {
        List<String> requestedIdentifiers = new ArrayList<String>();
        if (question == null || question.isBlank()) {
            return requestedIdentifiers;
        }
        for (String requestedPath : extractEvidencePaths(List.of(question))) {
            if (!containsIdentifierIgnoreCase(requestedIdentifiers, requestedPath)) {
                requestedIdentifiers.add(requestedPath);
            }
        }
        Matcher identifierMatcher = EXPLICIT_IDENTIFIER_PATTERN.matcher(question);
        while (identifierMatcher.find()) {
            String matchedIdentifier = identifierMatcher.group(1) == null
                    ? identifierMatcher.group(2)
                    : identifierMatcher.group(1);
            String rawIdentifier = matchedIdentifier == null
                    ? identifierMatcher.group()
                    : matchedIdentifier;
            String identifier = cleanupReferentialIdentifier(rawIdentifier);
            if (identifier.isBlank() || isGenericReferentialIdentifier(identifier)) {
                continue;
            }
            if (!containsIdentifierIgnoreCase(requestedIdentifiers, identifier)) {
                requestedIdentifiers.add(identifier);
            }
        }
        removeContextIdentifiersBeforeScopeMarker(question, requestedIdentifiers);
        removeContainerIdentifiersWhenSpecificFieldsExist(requestedIdentifiers);
        return requestedIdentifiers;
    }

    /**
     * 清理问题中提取出的标识文本。
     *
     * @param rawIdentifier 原始标识
     * @return 清理后的标识
     */
    String cleanupReferentialIdentifier(String rawIdentifier) {
        if (rawIdentifier == null || rawIdentifier.isBlank()) {
            return "";
        }
        return rawIdentifier
                .replaceAll("^[`'\"“”‘’]+", "")
                .replaceAll("[`'\"“”‘’？?。；;，,、:：]+$", "")
                .trim();
    }

    /**
     * 判断标识是否只是问题中的通用英文词，而不是需要回答的业务标识。
     *
     * @param identifier 标识
     * @return 通用词返回 true
     */
    boolean isGenericReferentialIdentifier(String identifier) {
        String normalizedIdentifier = lowerCase(identifier);
        return List.of(
                "api",
                "http",
                "json",
                "xml",
                "excel",
                "xlsx",
                "docx",
                "pdf",
                "markdown",
                "md"
        ).contains(normalizedIdentifier);
    }

    /**
     * 判断标识是否包含路径、配置键、字段键等精确信号。
     *
     * @param identifier 标识
     * @return 包含精确信号返回 true
     */
    boolean containsExactIdentifierSignal(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return false;
        }
        return identifier.contains("_")
                || identifier.contains("-")
                || identifier.contains("=")
                || identifier.contains("/")
                || identifier.contains(".");
    }

    /**
     * 当问题里同时有 request/response 容器和更具体字段时，只保留具体字段。
     *
     * @param requestedIdentifiers 标识列表
     */
    void removeContainerIdentifiersWhenSpecificFieldsExist(List<String> requestedIdentifiers) {
        if (requestedIdentifiers == null || requestedIdentifiers.size() <= 2) {
            return;
        }
        boolean hasSpecificIdentifier = false;
        for (String requestedIdentifier : requestedIdentifiers) {
            if (!isPayloadContainerIdentifier(requestedIdentifier)) {
                hasSpecificIdentifier = true;
                break;
            }
        }
        if (!hasSpecificIdentifier) {
            return;
        }
        requestedIdentifiers.removeIf(this::isPayloadContainerIdentifier);
    }

    /**
     * 移除“某系统/某 API 里 A、B 分别是什么”中范围标记前的上下文标识。
     *
     * @param question 用户问题
     * @param requestedIdentifiers 标识列表
     */
    void removeContextIdentifiersBeforeScopeMarker(String question, List<String> requestedIdentifiers) {
        if (question == null || question.isBlank() || requestedIdentifiers == null || requestedIdentifiers.size() <= 1) {
            return;
        }
        int markerIndex = scopeMarkerIndex(question);
        if (markerIndex < 0) {
            return;
        }
        boolean hasIdentifierAfterMarker = false;
        for (String requestedIdentifier : requestedIdentifiers) {
            int identifierIndex = lowerCase(question).indexOf(lowerCase(requestedIdentifier));
            if (identifierIndex > markerIndex) {
                hasIdentifierAfterMarker = true;
                break;
            }
        }
        if (!hasIdentifierAfterMarker) {
            return;
        }
        requestedIdentifiers.removeIf(identifier -> {
            int identifierIndex = lowerCase(question).indexOf(lowerCase(identifier));
            return identifierIndex >= 0 && identifierIndex < markerIndex;
        });
    }

    /**
     * 查找“里/中”这类范围标记位置。
     *
     * @param question 用户问题
     * @return 标记下标；没有返回 -1
     */
    int scopeMarkerIndex(String question) {
        int insideIndex = question.indexOf("里");
        if (insideIndex >= 0) {
            return insideIndex;
        }
        return question.indexOf("中");
    }

    /**
     * 判断标识是否更像请求/响应容器，而不是具体字段。
     *
     * @param identifier 标识
     * @return 容器标识返回 true
     */
    boolean isPayloadContainerIdentifier(String identifier) {
        String normalizedIdentifier = lowerCase(identifier);
        return normalizedIdentifier.equals("request")
                || normalizedIdentifier.equals("response")
                || normalizedIdentifier.equals("requestdata")
                || normalizedIdentifier.equals("responsedata")
                || normalizedIdentifier.equals("requestbody")
                || normalizedIdentifier.equals("responsebody")
                || normalizedIdentifier.equals("payload")
                || normalizedIdentifier.equals("body")
                || normalizedIdentifier.equals("params")
                || normalizedIdentifier.equals("parameters")
                || normalizedIdentifier.equals("headers");
    }

    /**
     * 判断列表是否已包含大小写无关的同一标识。
     *
     * @param identifiers 已有标识
     * @param candidate 候选标识
     * @return 已存在返回 true
     */
    boolean containsIdentifierIgnoreCase(List<String> identifiers, String candidate) {
        for (String identifier : identifiers) {
            if (lowerCase(identifier).equals(lowerCase(candidate))) {
                return true;
            }
        }
        return false;
    }
}
