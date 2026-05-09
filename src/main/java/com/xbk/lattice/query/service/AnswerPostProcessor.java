package com.xbk.lattice.query.service;

import java.util.List;

/**
 * 答案后处理器
 *
 * 职责：编排结构化答案的通用 Markdown 后处理、引用归位与证据贴合修正
 *
 * @author xiexu
 */
public class AnswerPostProcessor {

    private final AnswerGenerationService support;

    private final AnswerCitationPostProcessor citationPostProcessor;

    private final AnswerParagraphPostProcessor paragraphPostProcessor;

    /**
     * 创建答案后处理器。
     *
     * @param support 答案生成支撑规则
     */
    public AnswerPostProcessor(AnswerGenerationService support) {
        this.support = support;
        this.citationPostProcessor = new AnswerCitationPostProcessor(support, new AnswerCitationResolver());
        this.paragraphPostProcessor = new AnswerParagraphPostProcessor(support);
    }

    /**
     * 规范化结构化模型答案。
     *
     * @param answerMarkdown 模型答案
     * @param question 用户问题
     * @param queryArticleHits 查询命中
     * @return 规范化后的答案
     */
    public String normalizeStructuredAnswerMarkdown(
            String answerMarkdown,
            String question,
            List<QueryArticleHit> queryArticleHits
    ) {
        String normalizedAnswer = answerMarkdown;
        normalizedAnswer = support.removeUnrequestedPathExamples(normalizedAnswer, question);
        return attachDefaultCitationWhenMissing(normalizedAnswer, question, queryArticleHits);
    }

    /**
     * 对精确查值题进行段落级压缩。
     *
     * @param answerMarkdown 答案正文
     * @param question 用户问题
     * @return 压缩后的答案
     */
    String compressStructuredExactLookupAnswer(String answerMarkdown, String question) {
        return paragraphPostProcessor.compressStructuredExactLookupAnswer(answerMarkdown, question);
    }

    /**
     * 为缺少 citation 的正文行补齐默认引用。
     *
     * @param answerMarkdown 答案正文
     * @param question 用户问题
     * @param queryArticleHits 查询命中
     * @return 补齐引用后的答案
     */
    String attachDefaultCitationWhenMissing(
            String answerMarkdown,
            String question,
            List<QueryArticleHit> queryArticleHits
    ) {
        return citationPostProcessor.attachDefaultCitationWhenMissing(answerMarkdown, question, queryArticleHits);
    }
}
