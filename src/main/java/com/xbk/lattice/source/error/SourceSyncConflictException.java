package com.xbk.lattice.source.error;

import com.xbk.lattice.shared.error.LatticeBusinessException;

/**
 * 资料源同步冲突异常
 *
 * 职责：表示同一资料源存在活动运行时的并发冲突
 *
 * @author xiexu
 */
public class SourceSyncConflictException extends LatticeBusinessException {

    private static final String ERROR_CODE = "SOURCE_SYNC_CONFLICT";

    /**
     * 创建资料源同步冲突异常。
     *
     * @param message 错误信息
     */
    public SourceSyncConflictException(String message) {
        super(ERROR_CODE, message);
    }
}
