package com.xbk.lattice.query.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * ReviewerAgent
 *
 * 职责：发起单轮审查，并对超时与解析失败做确定性收敛
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class ReviewerAgent {

    private final ReviewerGateway reviewerGateway;

    private final ReviewResultParser reviewResultParser;

    /**
     * 创建 ReviewerAgent。
     *
     * @param reviewerGateway 审查网关
     * @param reviewResultParser 审查结果解析器
     */
    public ReviewerAgent(ReviewerGateway reviewerGateway, ReviewResultParser reviewResultParser) {
        this.reviewerGateway = reviewerGateway;
        this.reviewResultParser = reviewResultParser;
    }

    /**
     * 对答案执行单轮审查。
     *
     * @param question 问题
     * @param answer 答案
     * @param sourcePaths 来源路径
     * @return 审查结果
     */
    public ReviewResult review(String question, String answer, List<String> sourcePaths) {
        String reviewPrompt = buildPrompt(question, answer, sourcePaths);
        try {
            String rawResult = reviewerGateway.review(reviewPrompt);
            return reviewResultParser.parse(rawResult);
        }
        catch (ReviewTimeoutException ex) {
            return ReviewResult.timeoutFallback();
        }
    }

    /**
     * 构建最小审查提示词。
     *
     * @param question 问题
     * @param answer 答案
     * @param sourcePaths 来源路径
     * @return 审查提示词
     */
    private String buildPrompt(String question, String answer, List<String> sourcePaths) {
        return "question=" + question + "\n"
                + "answer=" + answer + "\n"
                + "sources=" + String.join(", ", sourcePaths);
    }
}
