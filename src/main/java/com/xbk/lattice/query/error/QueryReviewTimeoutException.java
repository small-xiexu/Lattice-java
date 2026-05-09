package com.xbk.lattice.query.error;

import com.xbk.lattice.shared.error.LatticeIntegrationException;

/**
 * 查询审查超时异常
 *
 * 职责：表示 ReviewerAgent 调用超时并需要走查询审查降级策略
 *
 * @author xiexu
 */
public class QueryReviewTimeoutException extends LatticeIntegrationException {

    private static final String ERROR_CODE = "QUERY_REVIEW_TIMEOUT";

    /**
     * 创建查询审查超时异常。
     *
     * @param message 异常信息
     */
    public QueryReviewTimeoutException(String message) {
        super(ERROR_CODE, message);
    }
}
