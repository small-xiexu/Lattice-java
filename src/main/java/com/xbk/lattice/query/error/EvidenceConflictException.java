package com.xbk.lattice.query.error;

import com.xbk.lattice.shared.error.LatticeBusinessException;

/**
 * 查询证据冲突异常
 *
 * 职责：表示查询请求与当前证据链路状态存在不可直接合并的业务冲突
 *
 * @author xiexu
 */
public class EvidenceConflictException extends LatticeBusinessException {

    private static final String ERROR_CODE = "QUERY_EVIDENCE_CONFLICT";

    /**
     * 创建查询证据冲突异常。
     *
     * @param message 异常信息
     */
    public EvidenceConflictException(String message) {
        super(ERROR_CODE, message);
    }
}
