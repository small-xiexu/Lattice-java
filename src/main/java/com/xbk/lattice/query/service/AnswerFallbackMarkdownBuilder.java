package com.xbk.lattice.query.service;

import java.util.List;
import java.util.Map;

/**
 * 答案 fallback Markdown 构建器
 *
 * 职责：组装模型不可用或输出不可复用时的确定性 Markdown 答案与修订答案
 *
 * 不属于本类的事：不选择 fallback 证据、不计算证据分值、不判断问题类型
 *
 * @author xiexu
 */
final class AnswerFallbackMarkdownBuilder {

    private final AnswerGenerationService support;

    /**
     * 创建 fallback Markdown 构建器。
     *
     * @param support 答案生成支撑逻辑
     */
    AnswerFallbackMarkdownBuilder(AnswerGenerationService support) {
        this.support = support;
    }

    /**
     * 构建模型失败时的确定性 Markdown 兜底答案。
     *
     * @param question 查询问题
     * @param queryArticleHits 查询命中
     * @return Markdown 答案
     */
    String buildFallbackMarkdown(String question, List<QueryArticleHit> queryArticleHits) {
        List<QueryArticleHit> fallbackHits = support.selectFallbackEvidenceHits(question, queryArticleHits);
        List<String> queryTokens = support.extractQueryTokens(question);
        StringBuilder markdownBuilder = new StringBuilder();
        markdownBuilder.append("# 查询回答").append("\n\n");
        markdownBuilder.append("## 问题").append("\n");
        markdownBuilder.append(question.trim()).append("\n\n");
        appendFallbackConclusion(markdownBuilder, question, fallbackHits, queryTokens);
        appendFallbackReferenceSection(markdownBuilder, question, fallbackHits, queryTokens);
        return markdownBuilder.toString().trim();
    }

    /**
     * 构建模型失败时的确定性修订 Markdown。
     *
     * @param question 查询问题
     * @param currentAnswer 当前答案
     * @param correction 用户纠正
     * @param queryArticleHits 修订证据
     * @return 修订 Markdown
     */
    String buildFallbackRevisionMarkdown(
            String question,
            String currentAnswer,
            String correction,
            List<QueryArticleHit> queryArticleHits
    ) {
        List<String> queryTokens = support.extractQueryTokens(question);
        Map<QueryEvidenceType, List<QueryArticleHit>> groupedHits = support.groupHitsByEvidenceType(queryArticleHits);
        StringBuilder markdownBuilder = new StringBuilder();
        markdownBuilder.append("# 修订答案").append("\n\n");
        markdownBuilder.append("## 问题").append("\n");
        markdownBuilder.append(question.trim()).append("\n\n");
        markdownBuilder.append("## 修订结论").append("\n");
        markdownBuilder.append("- ").append(SensitiveTextMasker.mask(correction == null ? "" : correction.trim())).append("\n\n");
        markdownBuilder.append("## 修订说明").append("\n");
        markdownBuilder.append("- 原答案摘要：").append(support.extractEvidenceSnippet(currentAnswer)).append("\n");
        markdownBuilder.append("- 纠正输入：").append(SensitiveTextMasker.mask(correction == null ? "" : correction.trim())).append("\n\n");
        appendFallbackSection(markdownBuilder, "用户反馈证据", groupedHits.get(QueryEvidenceType.CONTRIBUTION), queryArticleHits, queryTokens);
        appendFallbackSection(markdownBuilder, "结构化证据卡", groupedHits.get(QueryEvidenceType.FACT_CARD), queryArticleHits, queryTokens);
        appendFallbackSection(markdownBuilder, "源文件证据", groupedHits.get(QueryEvidenceType.SOURCE), queryArticleHits, queryTokens);
        appendFallbackSection(markdownBuilder, "图谱证据", groupedHits.get(QueryEvidenceType.GRAPH), queryArticleHits, queryTokens);
        appendFallbackSection(markdownBuilder, "文章背景证据", groupedHits.get(QueryEvidenceType.ARTICLE), queryArticleHits, queryTokens);
        return markdownBuilder.toString().trim();
    }

    /**
     * 追加 Markdown 兜底答案中的证据分组。
     *
     * @param markdownBuilder Markdown 构建器
     * @param title 标题
     * @param queryArticleHits 证据列表
     * @param citationCandidateHits citation 候选
     * @param queryTokens 查询 token
     */
    private void appendFallbackSection(
            StringBuilder markdownBuilder,
            String title,
            List<QueryArticleHit> queryArticleHits,
            List<QueryArticleHit> citationCandidateHits,
            List<String> queryTokens
    ) {
        if (queryArticleHits == null || queryArticleHits.isEmpty()) {
            return;
        }
        markdownBuilder.append("## ").append(title).append("\n");
        for (QueryArticleHit queryArticleHit : queryArticleHits) {
            markdownBuilder.append("- **").append(queryArticleHit.getTitle()).append("**");
            if (!queryArticleHit.getSourcePaths().isEmpty()) {
                markdownBuilder.append(" (").append(String.join(", ", queryArticleHit.getSourcePaths())).append(")");
            }
            markdownBuilder.append("：")
                    .append(SensitiveTextMasker.mask(support.selectFallbackEvidenceSnippet(queryArticleHit, queryTokens)))
                    .append(" ")
                    .append(support.resolveCitationLiteral(queryArticleHit, citationCandidateHits))
                    .append("\n");
        }
        markdownBuilder.append("\n");
    }

    /**
     * 追加 deterministic fallback 的结论段，优先先回答问题，再附证据说明。
     *
     * @param markdownBuilder Markdown 构建器
     * @param question 用户问题
     * @param fallbackHits fallback 证据
     * @param queryTokens 查询 token
     */
    private void appendFallbackConclusion(
            StringBuilder markdownBuilder,
            String question,
            List<QueryArticleHit> fallbackHits,
            List<String> queryTokens
    ) {
        markdownBuilder.append("## 结论").append("\n");
        List<String> conclusionLines = support.buildFallbackConclusionLines(question, fallbackHits, queryTokens);
        if (conclusionLines.isEmpty()) {
            markdownBuilder.append("- 当前未找到与该问题直接相关的知识。").append("\n\n");
            return;
        }
        for (String conclusionLine : conclusionLines) {
            markdownBuilder.append("- ").append(SensitiveTextMasker.mask(conclusionLine)).append("\n");
        }
        markdownBuilder.append("\n");
    }

    /**
     * 追加 deterministic fallback 的参考说明，避免正文只有证据罗列。
     *
     * @param markdownBuilder Markdown 构建器
     * @param question 用户问题
     * @param fallbackHits fallback 证据
     * @param queryTokens 查询 token
     */
    private void appendFallbackReferenceSection(
            StringBuilder markdownBuilder,
            String question,
            List<QueryArticleHit> fallbackHits,
            List<String> queryTokens
    ) {
        if (fallbackHits == null || fallbackHits.isEmpty()) {
            return;
        }
        List<String> comparisonOptions = support.extractComparisonOptions(question);
        QueryArticleHit primaryHit = fallbackHits.get(0);
        markdownBuilder.append("## 参考说明").append("\n");
        for (int index = 0; index < fallbackHits.size(); index++) {
            QueryArticleHit fallbackHit = fallbackHits.get(index);
            if (index > 0
                    && comparisonOptions.size() < 2
                    && !support.shouldIncludeSecondaryFallbackHit(question, primaryHit, fallbackHit, queryTokens)) {
                continue;
            }
            String snippet = support.selectReferenceFallbackSnippet(question, fallbackHit, comparisonOptions, queryTokens);
            markdownBuilder.append("- **").append(fallbackHit.getTitle()).append("**");
            if (!fallbackHit.getSourcePaths().isEmpty()) {
                markdownBuilder.append(" (").append(String.join(", ", fallbackHit.getSourcePaths())).append(")");
            }
            markdownBuilder.append("：")
                    .append(SensitiveTextMasker.mask(snippet))
                    .append(" ")
                    .append(support.resolveCitationLiteral(fallbackHit, fallbackHits))
                    .append("\n");
        }
        markdownBuilder.append("\n");
    }
}
