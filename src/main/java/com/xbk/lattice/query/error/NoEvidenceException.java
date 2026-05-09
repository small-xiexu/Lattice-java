package com.xbk.lattice.query.error;

import com.xbk.lattice.shared.error.LatticeBusinessException;

/**
 * 查询无证据异常
 *
 * 职责：表示查询链路未产出可用于组装回答的有效证据或终态
 *
 * @author xiexu
 */
public class NoEvidenceException extends LatticeBusinessException {

    private static final String ERROR_CODE = "QUERY_NO_EVIDENCE";

    /**
     * 创建查询无证据异常。
     *
     * @param message 异常信息
     */
    public NoEvidenceException(String message) {
        super(ERROR_CODE, message);
    }
}
