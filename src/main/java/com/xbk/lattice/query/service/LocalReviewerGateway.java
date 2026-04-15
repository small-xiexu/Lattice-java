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
        return """
                {"pass":true,"issues":[]}
                """;
    }
}
