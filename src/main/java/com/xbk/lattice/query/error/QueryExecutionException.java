package com.xbk.lattice.query.error;

import com.xbk.lattice.shared.error.LatticeIntegrationException;

/**
 * 查询执行异常
 *
 * 职责：表示查询图或深研图执行过程中发生的外部编排失败
 *
 * @author xiexu
 */
public class QueryExecutionException extends LatticeIntegrationException {

    private static final String ERROR_CODE = "QUERY_EXECUTION_FAILED";

    /**
     * 创建查询执行异常。
     *
     * @param message 异常信息
     * @param cause 根因
     */
    public QueryExecutionException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }
}
