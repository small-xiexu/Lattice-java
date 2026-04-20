package com.xbk.lattice.query.service;

import com.xbk.lattice.api.query.QueryResponse;

import java.util.Optional;

/**
 * 查询缓存存储
 *
 * 职责：提供查询结果的最小缓存读写能力
 *
 * @author xiexu
 */
public interface QueryCacheStore {

    /**
     * 按缓存键读取查询结果。
     *
     * @param cacheKey 缓存键
     * @return 查询结果
     */
    Optional<QueryResponse> get(String cacheKey);

    /**
     * 写入查询结果缓存。
     *
     * @param cacheKey 缓存键
     * @param queryResponse 查询结果
     */
    void put(String cacheKey, QueryResponse queryResponse);

    /**
     * 清空当前查询缓存。
     */
    void evictAll();
}
