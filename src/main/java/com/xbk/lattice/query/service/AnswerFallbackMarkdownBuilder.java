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
    String buildEvidenceMarkdown(String question, List<QueryArticleHit> queryArticleHits) {
        List<QueryArticleHit> fallbackHits = support.selectFallbackEvidenceHits(question, queryArticleHits);
        List<String> queryTokens = support.extractQueryTokens(question);
        StringBuilder builder = new StringBuilder();
        builder.append("# Query Answer").append("\n\n");
        builder.append("## Question").append("\n");
        builder.append(question.trim()).append("\n\n");
        appendEvidenceConclusion(builder, question, fallbackHits, queryTokens);
        appendEvidenceReferenceSection(builder, question, fallbackHits, queryTokens);
        return builder.toString().trim();
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
    String buildRevisionEvidenceMarkdown(
            String question,
            String currentAnswer,
            String correction,
            List<QueryArticleHit> queryArticleHits
    ) {
        List<String> queryTokens = support.extractQueryTokens(question);
        Map<QueryEvidenceType, List<QueryArticleHit>> groupedHits = support.groupHitsByEvidenceType(queryArticleHits);
        StringBuilder builder = new StringBuilder();
        builder.append("# Revised Answer").append("\n\n");
        builder.append("## Question").append("\n");
        builder.append(question.trim()).append("\n\n");
        builder.append("## Revision").append("\n");
        builder.append("- ").append(SensitiveTextMasker.mask(correction == null ? "" : correction.trim())).append("\n\n");
        builder.append("## Inputs").append("\n");
        builder.append("- Previous answer: ").append(support.extractEvidenceSnippet(currentAnswer)).append("\n");
        builder.append("- Correction: ").append(SensitiveTextMasker.mask(correction == null ? "" : correction.trim())).append("\n\n");
        appendEvidenceSection(builder, "Contribution Evidence", groupedHits.get(QueryEvidenceType.CONTRIBUTION), queryArticleHits, queryTokens);
        appendEvidenceSection(builder, "Fact Card Evidence", groupedHits.get(QueryEvidenceType.FACT_CARD), queryArticleHits, queryTokens);
        appendEvidenceSection(builder, "Source Evidence", groupedHits.get(QueryEvidenceType.SOURCE), queryArticleHits, queryTokens);
        appendEvidenceSection(builder, "Graph Evidence", groupedHits.get(QueryEvidenceType.GRAPH), queryArticleHits, queryTokens);
        appendEvidenceSection(builder, "Article Evidence", groupedHits.get(QueryEvidenceType.ARTICLE), queryArticleHits, queryTokens);
        return builder.toString().trim();
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
    private void appendEvidenceSection(
            StringBuilder builder,
            String title,
            List<QueryArticleHit> queryArticleHits,
            List<QueryArticleHit> citationCandidateHits,
            List<String> queryTokens
    ) {
        if (queryArticleHits == null || queryArticleHits.isEmpty()) {
            return;
        }
        builder.append("## ").append(title).append("\n");
        for (QueryArticleHit queryArticleHit : queryArticleHits) {
            builder.append("- **").append(queryArticleHit.getTitle()).append("**");
            if (!queryArticleHit.getSourcePaths().isEmpty()) {
                builder.append(" (").append(String.join(", ", queryArticleHit.getSourcePaths())).append(")");
            }
            builder.append(": ")
                    .append(SensitiveTextMasker.mask(support.selectFallbackEvidenceSnippet(queryArticleHit, queryTokens)))
                    .append(" ")
                    .append(support.resolveCitationLiteral(queryArticleHit, citationCandidateHits))
                    .append("\n");
        }
        builder.append("\n");
    }

    /**
     * 追加 deterministic fallback 的结论段，优先先回答问题，再附证据说明。
     *
     * @param markdownBuilder Markdown 构建器
     * @param question 用户问题
     * @param fallbackHits fallback 证据
     * @param queryTokens 查询 token
     */
    private void appendEvidenceConclusion(
            StringBuilder builder,
            String question,
            List<QueryArticleHit> fallbackHits,
            List<String> queryTokens
    ) {
        builder.append("## Evidence").append("\n");
        List<String> conclusionLines = support.buildEvidenceConclusionLines(question, fallbackHits, queryTokens);
        if (conclusionLines.isEmpty()) {
            builder.append("- NO_RELEVANT_KNOWLEDGE").append("\n\n");
            return;
        }
        for (String conclusionLine : conclusionLines) {
            builder.append("- ").append(SensitiveTextMasker.mask(conclusionLine)).append("\n");
        }
        builder.append("\n");
    }

    /**
     * 追加 deterministic fallback 的参考说明，避免正文只有证据罗列。
     *
     * @param markdownBuilder Markdown 构建器
     * @param question 用户问题
     * @param fallbackHits fallback 证据
     * @param queryTokens 查询 token
     */
    private void appendEvidenceReferenceSection(
            StringBuilder builder,
            String question,
            List<QueryArticleHit> fallbackHits,
            List<String> queryTokens
    ) {
        if (fallbackHits == null || fallbackHits.isEmpty()) {
            return;
        }
        List<String> comparisonOptions = support.extractComparisonOptions(question);
        QueryArticleHit primaryHit = fallbackHits.get(0);
        builder.append("## References").append("\n");
        for (int index = 0; index < fallbackHits.size(); index++) {
            QueryArticleHit fallbackHit = fallbackHits.get(index);
            if (index > 0
                    && comparisonOptions.size() < 2
                    && !support.shouldIncludeSecondaryFallbackHit(question, primaryHit, fallbackHit, queryTokens)) {
                continue;
            }
            String snippet = support.selectReferenceFallbackSnippet(question, fallbackHit, comparisonOptions, queryTokens);
            builder.append("- **").append(fallbackHit.getTitle()).append("**");
            if (!fallbackHit.getSourcePaths().isEmpty()) {
                builder.append(" (").append(String.join(", ", fallbackHit.getSourcePaths())).append(")");
            }
            builder.append(": ")
                    .append(SensitiveTextMasker.mask(snippet))
                    .append(" ")
                    .append(support.resolveCitationLiteral(fallbackHit, fallbackHits))
                    .append("\n");
        }
        builder.append("\n");
    }
}
