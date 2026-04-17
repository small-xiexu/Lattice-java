package com.xbk.lattice.query.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * 本地审查网关
 *
 * 职责：在未接入真实审查模型前，提供可预测的单轮审查输出
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class LocalReviewerGateway implements ReviewerGateway {

    /**
     * 执行本地规则审查。
     *
     * @param reviewPrompt 审查提示词
     * @return 原始审查输出
     */
    @Override
    public String review(String reviewPrompt) {
        if (reviewPrompt == null || reviewPrompt.isBlank()) {
            return """
                    {"pass":false,"issues":[{"severity":"HIGH","category":"EMPTY_PROMPT","description":"审查输入为空"}]}
                    """;
        }
        String normalizedPrompt = reviewPrompt.trim();
        if (!normalizedPrompt.contains("sources=") || normalizedPrompt.endsWith("sources=")) {
            return """
                    {"pass":false,"issues":[{"severity":"HIGH","category":"EMPTY_SOURCES","description":"答案缺少来源路径，无法验证"}]}
                    """;
        }
        if (normalizedPrompt.contains("answer=未找到相关知识")
                || normalizedPrompt.contains("answer=TODO")
                || normalizedPrompt.contains("answer=TBD")) {
            return """
                    {"pass":false,"issues":[{"severity":"HIGH","category":"WEAK_ANSWER","description":"答案为空洞、占位或未提供可验证结论"}]}
                    """;
        }
        return """
                {"pass":true,"issues":[]}
                """;
    }
}
