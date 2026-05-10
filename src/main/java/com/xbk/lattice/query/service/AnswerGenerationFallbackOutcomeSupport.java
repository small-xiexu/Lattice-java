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
 * 答案生成 fallback outcome 支持
 *
 * 职责：提取查询 token、构造 deterministic fallback 载荷并推导 fallback 答案语义
 *
 * @author xiexu
 */
@Slf4j
abstract class AnswerGenerationFallbackOutcomeSupport extends AnswerGenerationExactLookupSupport {

    /**
     * 创建无 LLM 的拆分支持。
     */
    AnswerGenerationFallbackOutcomeSupport() {
        super();
    }

    /**
     * 创建拆分支持。
     *
     * @param llmGateway LLM 网关
     */
    AnswerGenerationFallbackOutcomeSupport(LlmGateway llmGateway) {
        super(llmGateway);
    }

    @Override
    List<String> extractQueryTokens(String question) {
        List<String> focusedTokens = QueryEvidenceRelevanceSupport.extractHighSignalTokens(question);
        if (!focusedTokens.isEmpty()) {
            return new ArrayList<String>(focusedTokens);
        }
        return new ArrayList<String>(QueryTokenExtractor.extract(question));
    }

    /**
     * 选出与问题最相关的内容行。
     *
     * @param content 文章内容
     * @param queryTokens 查询 token
     * @return 匹配内容行
     */
    @Override
    List<String> selectMatchedLines(String content, List<String> queryTokens) {
        List<String> matchedLines = new ArrayList<String>();
        String bodyContent = ArticleMarkdownSupport.extractBody(content);
        String[] lines = bodyContent.split("\\R");
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            String normalizedLine = line.trim();
            if (normalizedLine.isEmpty()
                    || normalizedLine.startsWith("#")
                    || normalizedLine.startsWith(">")
                    || answerEvidenceNormalizer.looksLikeTableOfContentsLine(normalizedLine)
                    || answerEvidenceNormalizer.isMarkdownTableHeaderWithDivider(normalizedLine, index + 1 < lines.length ? lines[index + 1] : null)
                    || answerEvidenceNormalizer.isNonTextMediaLine(normalizedLine)) {
                continue;
            }

            String plainLine = normalizedLine.startsWith("- ") ? normalizedLine.substring(2) : normalizedLine;
            String lowercaseLine = plainLine.toLowerCase(Locale.ROOT);
            for (String queryToken : queryTokens) {
                if (lowercaseLine.contains(queryToken)) {
                    matchedLines.add(plainLine);
                    break;
                }
            }
            if (matchedLines.size() >= 6) {
                break;
            }
        }
        return matchedLines;
    }

    /**
     * 从 metadata_json 中提取 description。
     *
     * @param metadataJson 元数据 JSON
     * @return 描述
     */
    @Override
    String extractDescription(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return "";
        }
        String marker = "\"description\":";
        int markerIndex = metadataJson.indexOf(marker);
        if (markerIndex < 0) {
            return "";
        }
        int quoteStart = metadataJson.indexOf('"', markerIndex + marker.length());
        if (quoteStart < 0) {
            return "";
        }
        int quoteEnd = metadataJson.indexOf('"', quoteStart + 1);
        if (quoteEnd < 0) {
            return "";
        }
        return metadataJson.substring(quoteStart + 1, quoteEnd);
    }

    /**
     * 判断当前是否只包含文章层证据。
     *
     * @param queryArticleHits 查询命中
     * @return 是否只包含文章层证据
     */
    boolean containsOnlyArticleEvidence(List<QueryArticleHit> queryArticleHits) {
        if (queryArticleHits.size() != 1) {
            return false;
        }
        return queryArticleHits.get(0).getEvidenceType() == QueryEvidenceType.ARTICLE;
    }

    /**
     * 判断单 article 规则答案是否已经足够直接，可视为成功回答。
     *
     * @param question 查询问题
     * @param queryArticleHit 命中文章
     * @return 答案语义
     */
    AnswerOutcome resolveSingleArticleAnswerOutcome(String question, QueryArticleHit queryArticleHit) {
        if (queryArticleHit == null) {
            return AnswerOutcome.PARTIAL_ANSWER;
        }
        return isDirectFallbackAnswerable(question, queryArticleHit)
                ? AnswerOutcome.SUCCESS
                : AnswerOutcome.PARTIAL_ANSWER;
    }

    /**
     * 构造确定性 fallback 载荷，并根据证据本身推导更准确的答案语义。
     *
     * @param question 查询问题
     * @param queryArticleHits 查询命中
     * @param preferredOutcome 期望保留的答案语义
     * @param generationMode 生成模式
     * @param modelExecutionStatus 模型执行状态
     * @param fallbackReason fallback 原因
     * @return fallback 载荷
     */
    @Override
    QueryAnswerPayload buildEvidencePayload(
            String question,
            List<QueryArticleHit> queryArticleHits,
            AnswerOutcome preferredOutcome,
            GenerationMode generationMode,
            ModelExecutionStatus modelExecutionStatus,
            String fallbackReason
    ) {
        List<QueryArticleHit> fallbackHits = selectFallbackEvidenceHits(question, queryArticleHits);
        return new QueryAnswerPayload(
                SensitiveTextMasker.mask(buildEvidenceMarkdown(question, queryArticleHits)),
                resolveFallbackAnswerOutcome(question, fallbackHits, preferredOutcome),
                generationMode,
                modelExecutionStatus,
                false,
                fallbackReason
        );
    }

    /**
     * 根据 fallback 证据推导最终答案语义。
     *
     * @param question 查询问题
     * @param fallbackHits fallback 证据
     * @param preferredOutcome 调用方期望保留的语义
     * @return 推导后的答案语义
     */
    AnswerOutcome resolveFallbackAnswerOutcome(
            String question,
            List<QueryArticleHit> fallbackHits,
            AnswerOutcome preferredOutcome
    ) {
        if (preferredOutcome == AnswerOutcome.INSUFFICIENT_EVIDENCE
                || preferredOutcome == AnswerOutcome.NO_RELEVANT_KNOWLEDGE
                || preferredOutcome == AnswerOutcome.MODEL_FAILURE) {
            return preferredOutcome;
        }
        AnswerOutcome evidenceOutcome = inferFallbackEvidenceOutcome(question, fallbackHits);
        if (preferredOutcome == null || preferredOutcome == AnswerOutcome.SUCCESS) {
            return evidenceOutcome;
        }
        if (preferredOutcome == AnswerOutcome.PARTIAL_ANSWER) {
            if (evidenceOutcome == AnswerOutcome.NO_RELEVANT_KNOWLEDGE
                    && (looksLikeStrictExactIdentifierQuestion(question) || looksLikeRequiredFacetQuestion(question))) {
                return AnswerOutcome.NO_RELEVANT_KNOWLEDGE;
            }
            return evidenceOutcome;
        }
        return preferredOutcome;
    }

    /**
     * 仅基于 fallback 证据本身判断答案是否可视为成功、部分回答或无相关知识。
     *
     * @param question 查询问题
     * @param fallbackHits fallback 证据
     * @return 证据侧答案语义
     */
    AnswerOutcome inferFallbackEvidenceOutcome(String question, List<QueryArticleHit> fallbackHits) {
        if (fallbackHits == null || fallbackHits.isEmpty()) {
            return AnswerOutcome.NO_RELEVANT_KNOWLEDGE;
        }
        if (looksLikeRequiredFacetQuestion(question) && !coversRequiredQuestionFacets(question, fallbackHits)) {
            return AnswerOutcome.NO_RELEVANT_KNOWLEDGE;
        }
        List<String> comparisonOptions = extractComparisonOptions(question);
        if (comparisonOptions.size() >= 2
                && hasComparisonConflict(comparisonOptions.get(0), comparisonOptions.get(1), fallbackHits)) {
            return AnswerOutcome.PARTIAL_ANSWER;
        }
        return isDirectFallbackAnswerable(question, fallbackHits.get(0))
                ? AnswerOutcome.SUCCESS
                : AnswerOutcome.PARTIAL_ANSWER;
    }

    /**
     * 判断当前 fallback 证据是否已形成冲突口径。
     *
     * @param leftOption 左选项
     * @param rightOption 右选项
     * @param fallbackHits fallback 证据
     * @return 存在双向口径返回 true
     */
    boolean hasComparisonConflict(
            String leftOption,
            String rightOption,
            List<QueryArticleHit> fallbackHits
    ) {
        boolean hasLeftSupport = false;
        boolean hasRightSupport = false;
        for (QueryArticleHit fallbackHit : fallbackHits) {
            String matchedOption = matchComparisonOption(fallbackHit, leftOption, rightOption);
            if (leftOption.equals(matchedOption)) {
                hasLeftSupport = true;
            }
            if (rightOption.equals(matchedOption)) {
                hasRightSupport = true;
            }
            if (hasLeftSupport && hasRightSupport) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断单条证据是否已足够直接，可视为成功回答。
     *
     * @param question 查询问题
     * @param queryArticleHit 查询命中
     * @return 可直接回答返回 true
     */
    @Override
    boolean isDirectFallbackAnswerable(String question, QueryArticleHit queryArticleHit) {
        if (queryArticleHit == null) {
            return false;
        }
        if (looksLikeRequiredFacetQuestion(question)
                && !coversRequiredQuestionFacets(question, List.of(queryArticleHit))) {
            return false;
        }
        List<String> queryTokens = extractQueryTokens(question);
        if (looksLikeStatusQuestion(question)) {
            String statusSnippet = selectQuestionFocusedFallbackSnippet(
                    question,
                    queryArticleHit,
                    queryTokens
            );
            return containsStatusSignal(lowerCase(statusSnippet));
        }
        if (looksLikeFlowQuestion(question)) {
            String flowSnippet = selectQuestionFocusedFallbackSnippet(question, queryArticleHit, queryTokens);
            if (containsFlowSignal(flowSnippet)) {
                return true;
            }
            if (containsFlowSignalInFallbackLines(queryArticleHit)) {
                return true;
            }
            return containsFlowSignal(extractDescription(queryArticleHit.getMetadataJson()));
        }
        if (!selectMatchedLines(queryArticleHit.getContent(), queryTokens).isEmpty()) {
            return true;
        }
        return !extractDescription(queryArticleHit.getMetadataJson()).isEmpty();
    }

    /**
     * 判断命中正文里是否存在通用流程/链路事实句。
     *
     * @param queryArticleHit 查询命中
     * @return 存在返回 true
     */
    boolean containsFlowSignalInFallbackLines(QueryArticleHit queryArticleHit) {
        if (queryArticleHit == null) {
            return false;
        }
        for (String contentLine : selectFallbackContentLines(queryArticleHit.getContent())) {
            String normalizedLine = answerEvidenceNormalizer.normalizeFallbackLineCandidate(contentLine);
            if (!normalizedLine.isBlank() && containsFlowSignal(normalizedLine)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断问题是否含有必须同时覆盖的通用技术焦点。
     *
     * @param question 用户问题
     * @return 需要覆盖返回 true
     */
    boolean looksLikeRequiredFacetQuestion(String question) {
        return extractRequiredQuestionFacets(question).size() >= 2;
    }

    /**
     * 判断 fallback 证据是否覆盖问题中的必要技术焦点。
     *
     * @param question 用户问题
     * @param fallbackHits fallback 证据
     * @return 覆盖返回 true
     */
    boolean coversRequiredQuestionFacets(String question, List<QueryArticleHit> fallbackHits) {
        List<String> requiredFacets = extractRequiredQuestionFacets(question);
        if (requiredFacets.isEmpty()) {
            return true;
        }
        String evidenceText = lowerCase(joinHitTexts(fallbackHits));
        for (String requiredFacet : requiredFacets) {
            if (!evidenceText.contains(requiredFacet)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 提取问题中需要被证据共同覆盖的通用技术焦点。
     *
     * @param question 用户问题
     * @return 技术焦点
     */
    List<String> extractRequiredQuestionFacets(String question) {
        List<String> facets = new ArrayList<String>();
        String normalizedQuestion = lowerCase(question);
        if (!containsMultiFacetQuestionSignal(normalizedQuestion)) {
            return facets;
        }
        for (String token : QueryTokenExtractor.extract(question)) {
            String normalizedToken = lowerCase(token);
            if (!looksLikeRequiredTechnicalFacet(normalizedToken) || facets.contains(normalizedToken)) {
                continue;
            }
            facets.add(normalizedToken);
            if (facets.size() >= 4) {
                break;
            }
        }
        return facets;
    }

    /**
     * 判断问题是否包含多焦点提问信号。
     *
     * @param normalizedQuestion 归一化问题
     * @return 包含返回 true
     */
    boolean containsMultiFacetQuestionSignal(String normalizedQuestion) {
        return normalizedQuestion.contains(",")
                || normalizedQuestion.contains("/")
                || normalizedQuestion.contains("&")
                || normalizedQuestion.contains("+");
    }

    /**
     * 判断 token 是否像必须被证据覆盖的技术焦点。
     *
     * @param token token
     * @return 是技术焦点返回 true
     */
    boolean looksLikeRequiredTechnicalFacet(String token) {
        if (token == null || token.isBlank() || token.length() < 2) {
            return false;
        }
        if (token.matches("\\d+")) {
            return false;
        }
        if (!token.matches("[a-z0-9._/-]+")) {
            return false;
        }
        return !isGenericTechnicalFacet(token);
    }

    /**
     * 判断 token 是否只是泛化技术类型词，不应作为强制焦点。
     *
     * @param token token
     * @return 泛化词返回 true
     */
    boolean isGenericTechnicalFacet(String token) {
        return "api".equals(token)
                || "http".equals(token)
                || "https".equals(token)
                || "path".equals(token)
                || "url".equals(token)
                || "json".equals(token)
                || "xml".equals(token)
                || "yaml".equals(token)
                || "yml".equals(token)
                || "sql".equals(token);
    }
}
