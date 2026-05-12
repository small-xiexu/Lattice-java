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
 * 答案生成基础支持
 *
 * 职责：承载 AnswerGenerationService 拆分出来的同类辅助逻辑
 *
 * @author xiexu
 */
@Slf4j
abstract class AnswerGenerationBaseSupport extends AnswerGenerationSupportContract {

    protected static final Pattern EXPLICIT_IDENTIFIER_PATTERN =
            Pattern.compile("`([^`]+)`|(?<![A-Za-z0-9_.=-])([A-Za-z][A-Za-z0-9_.=-]{2,})(?![A-Za-z0-9_.=-])");

    protected static final String FALLBACK_REASON_LLM_CALL_FAILED = "LLM_CALL_FAILED";

    protected static final String FALLBACK_REASON_LLM_OUTPUT_INVALID = "LLM_OUTPUT_INVALID";

    protected static final String FALLBACK_REASON_LLM_UNSTRUCTURED_FALLBACK = "LLM_UNSTRUCTURED_FALLBACK";

    protected static final String FALLBACK_REASON_REWRITE_FAILED = "REWRITE_FAILED";

    protected static final String FALLBACK_REASON_DETERMINISTIC_EXACT_LOOKUP_PREFERRED = "DETERMINISTIC_EXACT_LOOKUP_PREFERRED";

    protected static final int PROMPT_USER_PROMPT_CHAR_LIMIT = 48000;

    protected static final int PROMPT_EVIDENCE_SECTION_HIT_LIMIT = 6;

    protected static final int PROMPT_EVIDENCE_CONTENT_CHAR_LIMIT = 1200;

    protected static final int PROMPT_EVIDENCE_METADATA_CHAR_LIMIT = 800;

    protected static final String PROMPT_TRUNCATED_SUFFIX = "... [truncated]";

    protected final AnswerPromptBuilder answerPromptBuilder;

    protected final AnswerPayloadParser answerPayloadParser;

    protected final AnswerLlmInvoker answerLlmInvoker;

    protected final AnswerPostProcessor answerPostProcessor;

    protected final AnswerRewriteService answerRewriteService;

    protected final AnswerFallbackMarkdownBuilder answerFallbackMarkdownBuilder;

    protected final AnswerFallbackConclusionBuilder answerFallbackConclusionBuilder;

    protected final AnswerFallbackEvidenceSelector answerFallbackEvidenceSelector;

    protected final AnswerCitationResolver answerCitationResolver = new AnswerCitationResolver();

    protected final AnswerMarkdownEvidenceNormalizer answerEvidenceNormalizer = new AnswerMarkdownEvidenceNormalizer();

    protected final QuerySemanticRules querySemanticRules = new QuerySemanticRules();

    /**
     * 创建无 LLM 网关的答案生成服务。
     */
    AnswerGenerationBaseSupport() {
        this.answerPromptBuilder = new AnswerPromptBuilder((AnswerGenerationService) this);
        this.answerPostProcessor = new AnswerPostProcessor((AnswerGenerationService) this);
        this.answerPayloadParser = new AnswerPayloadParser((AnswerGenerationService) this, answerPostProcessor);
        this.answerLlmInvoker = new AnswerLlmInvoker(null);
        this.answerFallbackMarkdownBuilder = new AnswerFallbackMarkdownBuilder((AnswerGenerationService) this);
        this.answerFallbackConclusionBuilder = new AnswerFallbackConclusionBuilder((AnswerGenerationService) this);
        this.answerFallbackEvidenceSelector = new AnswerFallbackEvidenceSelector((AnswerGenerationService) this);
        this.answerRewriteService = new AnswerRewriteService(
                (AnswerGenerationService) this,
                answerPromptBuilder,
                answerPayloadParser,
                answerLlmInvoker
        );
    }

    /**
     * 创建答案生成服务。
     *
     * @param llmGateway LLM 网关
     */
    AnswerGenerationBaseSupport(LlmGateway llmGateway) {
        this.answerPromptBuilder = new AnswerPromptBuilder((AnswerGenerationService) this);
        this.answerPostProcessor = new AnswerPostProcessor((AnswerGenerationService) this);
        this.answerPayloadParser = new AnswerPayloadParser((AnswerGenerationService) this, answerPostProcessor);
        this.answerLlmInvoker = new AnswerLlmInvoker(llmGateway);
        this.answerFallbackMarkdownBuilder = new AnswerFallbackMarkdownBuilder((AnswerGenerationService) this);
        this.answerFallbackConclusionBuilder = new AnswerFallbackConclusionBuilder((AnswerGenerationService) this);
        this.answerFallbackEvidenceSelector = new AnswerFallbackEvidenceSelector((AnswerGenerationService) this);
        this.answerRewriteService = new AnswerRewriteService(
                (AnswerGenerationService) this,
                answerPromptBuilder,
                answerPayloadParser,
                answerLlmInvoker
        );
    }

    String truncatePromptText(String text, int limit) {
        if (text == null || text.isBlank() || limit <= 0) {
            return "";
        }
        if (text.length() <= limit) {
            return text;
        }
        if (limit <= PROMPT_TRUNCATED_SUFFIX.length()) {
            return text.substring(0, limit);
        }
        return text.substring(0, limit - PROMPT_TRUNCATED_SUFFIX.length()) + PROMPT_TRUNCATED_SUFFIX;
    }

    String selectBestFallbackMatchedLine(List<String> candidateLines, List<String> preferredTokens) {
        if (candidateLines == null || candidateLines.isEmpty()) {
            return "";
        }
        String bestLine = "";
        int bestScore = Integer.MIN_VALUE;
        for (String candidateLine : candidateLines) {
            String normalizedLine = answerEvidenceNormalizer.normalizeFallbackLineCandidate(candidateLine);
            if (normalizedLine.isEmpty()) {
                continue;
            }
            int candidateScore = scoreFallbackLineCandidate(candidateLine, normalizedLine, preferredTokens);
            if (candidateScore > bestScore) {
                bestScore = candidateScore;
                bestLine = normalizedLine;
            }
        }
        return stripEmbeddedCitationLiterals(bestLine);
    }

    /**
     * 为参考说明挑选更贴近当前 comparison 选项的证据句。
     *
     * @param queryArticleHit 查询命中
     * @param comparisonOptions comparison 选项
     * @param queryTokens 问题 token
     * @return 参考说明片段
     */
    String selectReferenceFallbackSnippet(
            String question,
            QueryArticleHit queryArticleHit,
            List<String> comparisonOptions,
            List<String> queryTokens
    ) {
        if (comparisonOptions != null && comparisonOptions.size() >= 2) {
            String matchedOption = matchComparisonOption(
                    queryArticleHit,
                    comparisonOptions.get(0),
                    comparisonOptions.get(1)
            );
            if (!matchedOption.isBlank()) {
                return selectOptionSpecificFallbackSnippet(queryArticleHit, matchedOption, queryTokens);
            }
        }
        return selectQuestionFocusedFallbackSnippet(question, queryArticleHit, queryTokens);
    }

    /**
     * 去掉 snippet 尾部多余句号/分号，避免嵌入模板句后出现双标点。
     *
     * @param snippet 原始片段
     * @return 去尾标点后的片段
     */
    String trimTrailingFallbackPunctuation(String snippet) {
        if (snippet == null || snippet.isBlank()) {
            return "";
        }
        return snippet.replaceAll("[。；;，,：:]+$", "").trim();
    }

    /**
     * 计算 fallback 候选句的优先级，优先保留更直接、更事实化的句子。
     *
     * @param rawLine 原始候选行
     * @param normalizedLine 归一化后的候选行
     * @param preferredTokens 当前优先 token
     * @return 候选分值
     */
    int scoreFallbackLineCandidate(String rawLine, String normalizedLine, List<String> preferredTokens) {
        String lowerCaseLine = lowerCase(normalizedLine);
        int score = 0;
        if (preferredTokens != null) {
            for (String preferredToken : preferredTokens) {
                if (preferredToken == null || preferredToken.isBlank()) {
                    continue;
                }
                if (lowerCaseLine.contains(lowerCase(preferredToken))) {
                    score += 10;
                }
            }
        }
        if (normalizedLine.contains("`")) {
            score += 6;
        } else if (rawLine != null && rawLine.contains("`")) {
            score += 6;
        }
        if (lowerCaseLine.contains("采用")
                || lowerCaseLine.contains("通过")
                || lowerCaseLine.contains("使用字段")
                || lowerCaseLine.contains("默认值")
                || lowerCaseLine.contains("配置项")) {
            score += 4;
        }
        if (normalizedLine.contains(" = ")) {
            score += 12;
            int equalsIndex = normalizedLine.indexOf(" = ");
            if (equalsIndex > 0 && answerEvidenceNormalizer.looksLikeConfigFactKey(normalizedLine.substring(0, equalsIndex).trim())) {
                score += 10;
            }
        }
        if (normalizedLine.matches(".*\\d.*")) {
            score += 3;
        }
        if (lowerCaseLine.contains("本条目汇总")
                || lowerCaseLine.contains("主要记录了")
                || lowerCaseLine.contains("记录了若干")
                || lowerCaseLine.contains("回答时需要")
                || lowerCaseLine.contains("当前资料")
                || lowerCaseLine.contains("文档规则")
                || lowerCaseLine.contains("现有资料主要包含")
                || lowerCaseLine.contains("在当前资料中")
                || lowerCaseLine.contains("主要聚焦于")) {
            score -= 8;
        }
        if (lowerCaseLine.contains("汇总")
                || lowerCaseLine.contains("概述")
                || lowerCaseLine.contains("概要")) {
            score -= 3;
        }
        if (lowerCaseLine.contains("应视为")
                || lowerCaseLine.contains("而非")
                || lowerCaseLine.contains("来源未展开")
                || lowerCaseLine.contains("适用条件")
                || lowerCaseLine.contains("不能进一步断言")
                || lowerCaseLine.contains("未提供校准依据")) {
            score -= 8;
        }
        String lowerCaseRawLine = lowerCase(rawLine);
        if (lowerCaseRawLine.startsWith("summary:")
                || lowerCaseRawLine.startsWith("description:")
                || lowerCaseRawLine.startsWith("content:")) {
            score -= 2;
        }
        score -= Math.max(0, normalizedLine.length() - 120) / 24;
        return score;
    }

    /**
     * 从正文中选出首条可读内容，避开 frontmatter 与结构行。
     *
     * @param content 证据正文
     * @return 可读内容行
     */
    List<String> selectFallbackContentLines(String content) {
        List<String> contentLines = new ArrayList<String>();
        if (content == null || content.isBlank()) {
            return contentLines;
        }
        String bodyContent = ArticleMarkdownSupport.extractBody(content);
        String[] rawLines = bodyContent.split("\\R");
        for (int index = 0; index < rawLines.length; index++) {
            String rawLine = rawLines[index];
            String normalizedLine = rawLine == null ? "" : rawLine.trim();
            String lowerCaseLine = normalizedLine.toLowerCase(Locale.ROOT);
            if (normalizedLine.isEmpty()
                    || normalizedLine.startsWith("#")
                    || normalizedLine.startsWith(">")
                    || answerEvidenceNormalizer.looksLikeTableOfContentsLine(normalizedLine)
                    || answerEvidenceNormalizer.isMarkdownTableHeaderWithDivider(normalizedLine, index + 1 < rawLines.length ? rawLines[index + 1] : null)
                    || answerEvidenceNormalizer.isNonTextMediaLine(normalizedLine)
                    || lowerCaseLine.startsWith("<h1")
                    || lowerCaseLine.startsWith("<h2")
                    || lowerCaseLine.startsWith("<h3")
                    || lowerCaseLine.startsWith("<h4")) {
                continue;
            }
            String plainLine = normalizedLine.startsWith("- ") ? normalizedLine.substring(2) : normalizedLine;
            contentLines.add(plainLine);
        }
        return answerEvidenceNormalizer.filterFallbackMatchedLines(contentLines);
    }

    /**
     * 判断命中是否在标题、标识符或描述层直接命中了问题高信号 token。
     *
     * @param queryArticleHit 查询命中
     * @param highSignalTokens 高信号 token
     * @return 直接命中返回 true
     */
    boolean matchesStructuredOrTitle(QueryArticleHit queryArticleHit, List<String> highSignalTokens) {
        if (queryArticleHit == null || highSignalTokens == null || highSignalTokens.isEmpty()) {
            return false;
        }
        String structuredHaystack = String.join(
                " ",
                lowerCase(queryArticleHit.getArticleKey()),
                lowerCase(queryArticleHit.getConceptId()),
                lowerCase(queryArticleHit.getTitle()),
                lowerCase(extractDescription(queryArticleHit.getMetadataJson()))
        );
        for (String highSignalToken : highSignalTokens) {
            String normalizedToken = lowerCase(highSignalToken);
            if (!normalizedToken.isBlank() && structuredHaystack.contains(normalizedToken)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 提取证据摘要，避免兜底答案过长。
     *
     * @param content 证据正文
     * @return 摘要文本
     */
    String extractEvidenceSnippet(String content) {
        String normalizedContent = sanitizeEvidenceContentForPrompt(content, 180 + PROMPT_TRUNCATED_SUFFIX.length());
        if (normalizedContent.length() <= 180) {
            return normalizedContent;
        }
        return normalizedContent.substring(0, 180) + "...";
    }

    /**
     * 清理证据正文中的纯媒体行，避免图片/HTML embed 被当成答案语义或直接塞进 prompt。
     *
     * @param content 原始正文
     * @return 清理后的正文
     */
    String sanitizeEvidenceContentForPrompt(String content) {
        return sanitizeEvidenceContentForPrompt(content, 0);
    }

    /**
     * 清理证据正文中的纯媒体行，并按需限制累计字符数。
     *
     * @param content 原始正文
     * @param maxChars 最大字符数；小于等于 0 时不限制
     * @return 清理后的正文
     */
    String sanitizeEvidenceContentForPrompt(String content, int maxChars) {
        String bodyContent = ArticleMarkdownSupport.extractBody(content);
        if (bodyContent == null || bodyContent.isBlank()) {
            return "";
        }
        String[] rawLines = bodyContent.split("\\R");
        List<String> keptLines = new ArrayList<String>();
        for (String rawLine : rawLines) {
            String normalizedLine = rawLine == null ? "" : rawLine.trim();
            if (answerEvidenceNormalizer.isNonTextMediaLine(normalizedLine)) {
                continue;
            }
            if (maxChars <= 0) {
                keptLines.add(rawLine);
                continue;
            }
            String currentText = String.join("\n", keptLines);
            int separatorLength = currentText.isBlank() ? 0 : 1;
            int remainingChars = maxChars - currentText.length() - separatorLength;
            if (remainingChars <= 0) {
                break;
            }
            if (rawLine.length() <= remainingChars) {
                keptLines.add(rawLine);
            } else {
                keptLines.add(truncatePromptText(rawLine, remainingChars));
                break;
            }
        }
        return SensitiveTextMasker.mask(String.join("\n", keptLines).trim());
    }

    /**
     * 去掉片段里原本夹带的 citation，避免 fallback 再追加标准引用时重复。
     *
     * @param snippet 原始片段
     * @return 去除内嵌 citation 后的片段
     */
    String stripEmbeddedCitationLiterals(String snippet) {
        return answerCitationResolver.stripEmbeddedCitationLiterals(snippet);
    }

    /**
     * 把文本转成小写字符串，便于 fallback 相关性判断。
     *
     * @param value 原始文本
     * @return 小写文本
     */
    String lowerCase(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT);
    }

    /**
     * 判断文本中是否包含中文字符。
     *
     * @param value 原始文本
     * @return 包含中文返回 true
     */
    boolean containsHanText(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            Character.UnicodeScript script = Character.UnicodeScript.of(value.charAt(index));
            if (script == Character.UnicodeScript.HAN) {
                return true;
            }
        }
        return false;
    }

    /**
     * 解析单条证据对应的标准引用文本。
     *
     * @param queryArticleHit 证据命中
     * @return 引用文本
     */
    String resolveCitationLiteral(QueryArticleHit queryArticleHit) {
        return answerCitationResolver.resolveCitationLiteral(queryArticleHit);
    }

    /**
     * 解析证据对应的引用文本，并让结构化卡片优先回落到同源 source chunk。
     *
     * @param queryArticleHit 证据命中
     * @param candidateHits 同批候选命中
     * @return 引用文本
     */
    String resolveCitationLiteral(QueryArticleHit queryArticleHit, List<QueryArticleHit> candidateHits) {
        return answerCitationResolver.resolveCitationLiteral(queryArticleHit, candidateHits);
    }

    /**
     * 为结论段挑选更稳定的 citation 形式。
     *
     * @param queryArticleHit 证据命中
     * @return citation 文本
     */
    String resolveConclusionCitationLiteral(QueryArticleHit queryArticleHit) {
        return answerCitationResolver.resolveConclusionCitationLiteral(queryArticleHit);
    }

    /**
     * 为结论段挑选更稳定的 citation 形式，fact card 优先引用同源 source chunk。
     *
     * @param queryArticleHit 证据命中
     * @param candidateHits 同批候选命中
     * @return citation 文本
     */
    String resolveConclusionCitationLiteral(QueryArticleHit queryArticleHit, List<QueryArticleHit> candidateHits) {
        return answerCitationResolver.resolveConclusionCitationLiteral(queryArticleHit, candidateHits);
    }

    /**
     * 拼接多条命中的标题、正文与来源路径。
     *
     * @param queryArticleHits 查询命中
     * @return 可用于匹配的文本
     */
    String joinHitTexts(List<QueryArticleHit> queryArticleHits) {
        if (queryArticleHits == null || queryArticleHits.isEmpty()) {
            return "";
        }
        StringBuilder textBuilder = new StringBuilder();
        for (QueryArticleHit queryArticleHit : queryArticleHits) {
            if (queryArticleHit == null) {
                continue;
            }
            textBuilder.append(' ')
                    .append(queryArticleHit.getTitle())
                    .append(' ')
                    .append(queryArticleHit.getContent())
                    .append(' ')
                    .append(queryArticleHit.getMetadataJson());
            if (queryArticleHit.getSourcePaths() != null) {
                textBuilder.append(' ')
                        .append(String.join(" ", queryArticleHit.getSourcePaths()));
            }
        }
        return textBuilder.toString();
    }

    /**
     * 判断答案中是否仍保留至少一个可解析 citation。
     *
     * @param answerMarkdown 答案正文
     * @return 含 citation 返回 true
     */
    boolean containsCitationLiteral(String answerMarkdown) {
        return answerCitationResolver.containsCitationLiteral(answerMarkdown);
    }

}
