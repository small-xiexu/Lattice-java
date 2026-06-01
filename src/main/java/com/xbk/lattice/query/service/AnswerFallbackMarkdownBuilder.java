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

    private static final String H1_QUERY_ANSWER = "# 查询回答";
    private static final String H1_REVISION_ANSWER = "# 修订答案";
    private static final String H2_QUESTION = "## 问题";
    private static final String H2_REVISION = "## 修订";
    private static final String H2_INPUTS = "## 输入";
    private static final String H2_EVIDENCE = "## 证据";
    private static final String H2_REFERENCES = "## 参考说明";
    private static final String LABEL_HISTORY_ANSWER = "- 历史答案: ";
    private static final String LABEL_USER_CORRECTION = "- 用户修正: ";
    private static final String LABEL_NO_KNOWLEDGE = "- 当前未找到与该问题直接相关的知识。";
    private static final String SECTION_CONTRIBUTION = "贡献证据";
    private static final String SECTION_FACT_CARD = "事实卡证据";
    private static final String SECTION_SOURCE = "源文件证据";
    private static final String SECTION_GRAPH = "图谱证据";
    private static final String SECTION_ARTICLE = "文章证据";

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
        builder.append(H1_QUERY_ANSWER).append("\n\n");
        builder.append(H2_QUESTION).append("\n");
        builder.append(question.trim()).append("\n\n");
        appendEvidenceConclusion(builder, question, fallbackHits, queryTokens, queryArticleHits);
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
        builder.append(H1_REVISION_ANSWER).append("\n\n");
        builder.append(H2_QUESTION).append("\n");
        builder.append(question.trim()).append("\n\n");
        builder.append(H2_REVISION).append("\n");
        builder.append("- ").append(SensitiveTextMasker.mask(correction == null ? "" : correction.trim())).append("\n\n");
        builder.append(H2_INPUTS).append("\n");
        builder.append(LABEL_HISTORY_ANSWER).append(support.extractEvidenceSnippet(currentAnswer)).append("\n");
        builder.append(LABEL_USER_CORRECTION).append(SensitiveTextMasker.mask(correction == null ? "" : correction.trim())).append("\n\n");
        appendEvidenceSection(builder, SECTION_CONTRIBUTION, groupedHits.get(QueryEvidenceType.CONTRIBUTION), queryArticleHits, queryTokens);
        appendEvidenceSection(builder, SECTION_FACT_CARD, groupedHits.get(QueryEvidenceType.FACT_CARD), queryArticleHits, queryTokens);
        appendEvidenceSection(builder, SECTION_SOURCE, groupedHits.get(QueryEvidenceType.SOURCE), queryArticleHits, queryTokens);
        appendEvidenceSection(builder, SECTION_GRAPH, groupedHits.get(QueryEvidenceType.GRAPH), queryArticleHits, queryTokens);
        appendEvidenceSection(builder, SECTION_ARTICLE, groupedHits.get(QueryEvidenceType.ARTICLE), queryArticleHits, queryTokens);
        return builder.toString().trim();
    }

    /**
     * 追加 Markdown 兜底答案中的证据分组。
     *
     * @param builder Markdown 构建器
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
     * @param builder Markdown 构建器
     * @param question 用户问题
     * @param fallbackHits fallback 证据
     * @param queryTokens 查询 token
     */
    private void appendEvidenceConclusion(
            StringBuilder builder,
            String question,
            List<QueryArticleHit> fallbackHits,
            List<String> queryTokens,
            List<QueryArticleHit> queryArticleHits
    ) {
        builder.append(H2_EVIDENCE).append("\n");
        List<String> conclusionLines = support.buildEvidenceConclusionLines(question, fallbackHits, queryTokens, queryArticleHits);
        if (conclusionLines.isEmpty()) {
            builder.append(LABEL_NO_KNOWLEDGE).append("\n\n");
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
     * @param builder Markdown 构建器
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
        builder.append(H2_REFERENCES).append("\n");
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
