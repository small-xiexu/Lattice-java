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
 * 答案生成 fallback 对比与展示支持
 *
 * 职责：承载 AnswerGenerationService 拆分出来的同类辅助逻辑
 *
 * @author xiexu
 */
@Slf4j
abstract class AnswerGenerationFallbackComparisonSupport extends AnswerGenerationFallbackSnippetSupport {

    /**
     * 创建无 LLM 的答案生成拆分支持。
     */
    AnswerGenerationFallbackComparisonSupport() {
        super();
    }

    /**
     * 创建答案生成拆分支持。
     *
     * @param llmGateway LLM 网关
     */
    AnswerGenerationFallbackComparisonSupport(LlmGateway llmGateway) {
        super(llmGateway);
    }

List<String> extractSetupChecklistSteps(List<String> snippets) {
        List<String> setupSteps = new ArrayList<String>();
        if (snippets == null || snippets.isEmpty()) {
            return setupSteps;
        }
        for (String snippet : snippets) {
            String normalizedSnippet = stripOrderedListMarker(snippet);
            if (normalizedSnippet.isBlank()
                    || looksLikeLeadInSentence(normalizedSnippet)
                    || !containsSetupSignal(normalizedSnippet)) {
                continue;
            }
            setupSteps.add(trimTrailingFallbackPunctuation(normalizedSnippet));
            if (setupSteps.size() >= 4) {
                break;
            }
        }
        return setupSteps;
    }

    /**
     * 判断第二条 fallback 证据是否值得进入最终结论，避免无关旁证污染主回答。
     *
     * @param question 用户问题
     * @param primaryHit 首条证据
     * @param secondaryHit 第二条证据
     * @param queryTokens 查询 token
     * @return 值得展示返回 true
     */
    boolean shouldIncludeSecondaryFallbackHit(
            String question,
            QueryArticleHit primaryHit,
            QueryArticleHit secondaryHit,
            List<String> queryTokens
    ) {
        if (secondaryHit == null) {
            return false;
        }
        int primaryScore = primaryHit == null ? Integer.MIN_VALUE : scoreQuestionFocusedFallbackHit(question, primaryHit, queryTokens);
        int secondaryScore = scoreQuestionFocusedFallbackHit(question, secondaryHit, queryTokens);
        String secondarySnippet = selectQuestionFocusedFallbackSnippet(question, secondaryHit, queryTokens);
        if (looksLikeCapabilityQuestion(question)) {
            List<String> highSignalTokens = QueryEvidenceRelevanceSupport.extractHighSignalTokens(question);
            return containsCapabilitySignal(secondarySnippet)
                    && matchesStructuredOrTitle(secondaryHit, highSignalTokens)
                    && secondaryScore >= primaryScore - 12;
        }
        if (looksLikeFlowQuestion(question)) {
            return containsFlowSignal(secondarySnippet) && secondaryScore >= primaryScore - 12;
        }
        if (looksLikeStatusQuestion(question)) {
            return containsStatusSignal(lowerCase(secondarySnippet)) && secondaryScore >= primaryScore - 12;
        }
        return secondaryScore >= Math.max(primaryScore - 10, 8);
    }

    /**
     * 停用 answer 阶段的二选一问法拆解。
     *
     * @param question 用户问题
     * @return 空选项列表
     */
    List<String> extractComparisonOptions(String question) {
        return List.of();
    }

    /**
     * 判断命中更偏向哪个对比选项。
     *
     * @param fallbackHit fallback 证据
     * @param leftOption 左选项
     * @param rightOption 右选项
     * @return 命中的选项；若都不匹配返回空字符串
     */
    @Override
    String matchComparisonOption(QueryArticleHit fallbackHit, String leftOption, String rightOption) {
        if (fallbackHit == null) {
            return "";
        }
        String haystack = buildFallbackEvidenceHaystack(fallbackHit);
        boolean matchLeft = matchesComparisonOption(haystack, leftOption);
        boolean matchRight = matchesComparisonOption(haystack, rightOption);
        if (matchLeft && !matchRight) {
            return leftOption;
        }
        if (matchRight && !matchLeft) {
            return rightOption;
        }
        return "";
    }

    /**
     * 拼出 fallback 命中的可检索文本，用于冲突判断与语义分析。
     *
     * @param fallbackHit fallback 命中
     * @return 小写 haystack
     */
    String buildFallbackEvidenceHaystack(QueryArticleHit fallbackHit) {
        if (fallbackHit == null) {
            return "";
        }
        return lowerCase(selectFallbackEvidenceSnippet(fallbackHit, List.of()))
                + " "
                + lowerCase(fallbackHit.getTitle())
                + " "
                + lowerCase(extractDescription(fallbackHit.getMetadataJson()))
                + " "
                + lowerCase(fallbackHit.getContent());
    }

    /**
     * 为对比选项优先挑选更贴近该选项本身的证据句，而不是泛化摘要。
     *
     * @param queryArticleHit 查询命中
     * @param option 当前对比选项
     * @param fallbackTokens 问题级 token
     * @return 证据摘要
     */
    @Override
    String selectOptionSpecificFallbackSnippet(
            QueryArticleHit queryArticleHit,
            String option,
            List<String> fallbackTokens
    ) {
        List<String> optionTokens = extractQueryTokens(option);
        String optionSpecificLine = selectBestFallbackMatchedLine(
                selectMatchedLines(queryArticleHit.getContent(), optionTokens),
                optionTokens
        );
        if (!optionSpecificLine.isBlank()) {
            return optionSpecificLine;
        }
        return selectFallbackEvidenceSnippet(queryArticleHit, fallbackTokens);
    }

    /**
     * 判断证据文本是否命中某个对比选项，兼容中英混合表达。
     *
     * @param haystack 证据文本
     * @param option 对比选项
     * @return 命中返回 true
     */
    boolean matchesComparisonOption(String haystack, String option) {
        String normalizedOption = lowerCase(option).replace(" ", "");
        String normalizedHaystack = lowerCase(haystack).replace(" ", "");
        if (normalizedOption.isBlank() || normalizedHaystack.isBlank()) {
            return false;
        }
        if (normalizedHaystack.contains(normalizedOption)) {
            return true;
        }
        List<String> optionTokens = QueryTokenExtractor.extract(option);
        for (String optionToken : optionTokens) {
            String normalizedToken = lowerCase(optionToken).replace(" ", "");
            if (!normalizedToken.isBlank() && normalizedHaystack.contains(normalizedToken)) {
                return true;
            }
        }
        if (normalizedOption.contains("乐观锁") && normalizedHaystack.contains("optimisticlocking")) {
            return true;
        }
        if (normalizedOption.contains("悲观锁") && normalizedHaystack.contains("pessimisticlocking")) {
            return true;
        }
        if (normalizedOption.contains("锁")) {
            String optionWithoutLock = normalizedOption.replace("锁", "");
            return !optionWithoutLock.isBlank()
                    && normalizedHaystack.contains(optionWithoutLock)
                    && normalizedHaystack.contains("lock");
        }
        return false;
    }

    /**
     * 判断命中中是否显式带有“冲突/不一致/需继续确认”这类总结信号。
     *
     * @param fallbackHit fallback 命中
     * @return 包含冲突信号返回 true
     */
    boolean containsConflictSignal(QueryArticleHit fallbackHit) {
        String haystack = buildFallbackEvidenceHaystack(fallbackHit);
        return haystack.contains("冲突")
                || haystack.contains("不一致")
                || haystack.contains("conflict")
                || haystack.contains("需要继续确认")
                || haystack.contains("继续核对")
                || haystack.contains("无法确认")
                || haystack.contains("不能直接判定");
    }

    /**
     * 组装结论行要展示的 citation，优先使用更稳定的 article citation。
     *
     * @param fallbackHits fallback 证据
     * @return citation 串
     */
    String joinConclusionCitations(List<QueryArticleHit> fallbackHits) {
        if (fallbackHits == null || fallbackHits.isEmpty()) {
            return "";
        }
        List<String> citationLiterals = new ArrayList<String>();
        for (QueryArticleHit fallbackHit : fallbackHits) {
            String citationLiteral = resolveConclusionCitationLiteral(fallbackHit, fallbackHits);
            if (citationLiteral.isBlank() || citationLiterals.contains(citationLiteral)) {
                continue;
            }
            citationLiterals.add(citationLiteral);
        }
        return String.join("", citationLiterals);
    }
}
