package com.xbk.lattice.query.service;

/**
 * 检索通道运行状态
 *
 * 职责：描述单个召回通道在一次 dispatcher 执行中的结果状态
 *
 * @author xiexu
 */
public enum RetrievalChannelRunStatus {

    SUCCESS,

    SKIPPED,

    FAILED,

    TIMEOUT
}
