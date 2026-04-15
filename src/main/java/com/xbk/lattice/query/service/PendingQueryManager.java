package com.xbk.lattice.query.service;

import com.xbk.lattice.infra.persistence.PendingQueryRecord;

/**
 * PendingQuery 管理器
 *
 * 职责：抽象待确认查询的创建、纠错、确认与丢弃行为
 *
 * @author xiexu
 */
public interface PendingQueryManager {

    /**
     * 创建待确认查询。
     *
     * @param question 问题
     * @param queryResponse 查询结果
     * @return 待确认查询记录
     */
    PendingQueryRecord createPendingQuery(String question, com.xbk.lattice.api.query.QueryResponse queryResponse);

    /**
     * 修订待确认查询答案。
     *
     * @param queryId 查询标识
     * @param correction 纠正内容
     * @return 更新后的待确认查询记录
     */
    PendingQueryRecord correct(String queryId, String correction);

    /**
     * 确认待确认查询并沉淀贡献。
     *
     * @param queryId 查询标识
     */
    void confirm(String queryId);

    /**
     * 丢弃待确认查询。
     *
     * @param queryId 查询标识
     */
    void discard(String queryId);

    /**
     * 查询待确认记录，若不存在则抛出异常。
     *
     * @param queryId 查询标识
     * @return 待确认查询记录
     */
    PendingQueryRecord findPendingQuery(String queryId);

    /**
     * 列出当前全部待确认记录。
     *
     * @return 待确认查询记录列表
     */
    java.util.List<PendingQueryRecord> listPendingQueries();
}
