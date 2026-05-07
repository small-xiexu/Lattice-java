package com.xbk.lattice.query.service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 检索执行上下文
 *
 * 职责：承载单次 dispatcher 执行所需的问题、策略与返回数量
 *
 * @author xiexu
 */
public class RetrievalExecutionContext {

    private final RetrievalQueryContext retrievalQueryContext;

    private final String retrievalQuestion;

    private final RetrievalStrategy retrievalStrategy;

    private final int limit;

    private final Map<String, float[]> queryEmbeddingByCacheKey = new LinkedHashMap<String, float[]>();

    private final Map<String, RuntimeException> queryEmbeddingFailureByCacheKey =
            new LinkedHashMap<String, RuntimeException>();

    private final Set<String> computedQueryEmbeddingCacheKeys = new LinkedHashSet<String>();

    /**
     * 创建检索执行上下文。
     *
     * @param retrievalQueryContext 检索查询上下文
     * @param limit 返回数量
     */
    public RetrievalExecutionContext(RetrievalQueryContext retrievalQueryContext, int limit) {
        this.retrievalQueryContext = retrievalQueryContext;
        this.retrievalQuestion = retrievalQueryContext == null ? "" : retrievalQueryContext.getRetrievalQuestion();
        this.retrievalStrategy = retrievalQueryContext == null ? null : retrievalQueryContext.getRetrievalStrategy();
        this.limit = limit <= 0 ? 5 : limit;
    }

    /**
     * 返回检索查询上下文。
     *
     * @return 检索查询上下文
     */
    public RetrievalQueryContext getRetrievalQueryContext() {
        return retrievalQueryContext;
    }

    /**
     * 返回有效检索问题。
     *
     * @return 有效检索问题
     */
    public String getRetrievalQuestion() {
        return retrievalQuestion;
    }

    /**
     * 返回检索策略。
     *
     * @return 检索策略
     */
    public RetrievalStrategy getRetrievalStrategy() {
        return retrievalStrategy;
    }

    /**
     * 返回安全返回数量。
     *
     * @return 安全返回数量
     */
    public int getLimit() {
        return limit;
    }

    /**
     * 获取或生成本次查询的共享 embedding。
     *
     * @param configuredVectorEmbeddingService embedding 服务
     * @return 查询 embedding
     */
    public synchronized float[] getOrCreateQueryEmbedding(
            ConfiguredVectorEmbeddingService configuredVectorEmbeddingService
    ) {
        if (configuredVectorEmbeddingService == null) {
            return null;
        }
        String cacheKey = queryEmbeddingCacheKey(configuredVectorEmbeddingService);
        if (queryEmbeddingFailureByCacheKey.containsKey(cacheKey)) {
            throw queryEmbeddingFailureByCacheKey.get(cacheKey);
        }
        if (computedQueryEmbeddingCacheKeys.contains(cacheKey)) {
            return queryEmbeddingByCacheKey.get(cacheKey);
        }
        try {
            float[] embedding = configuredVectorEmbeddingService.embed(retrievalQuestion);
            queryEmbeddingByCacheKey.put(cacheKey, embedding);
            computedQueryEmbeddingCacheKeys.add(cacheKey);
            return embedding;
        }
        catch (RuntimeException ex) {
            queryEmbeddingFailureByCacheKey.put(cacheKey, ex);
            computedQueryEmbeddingCacheKeys.add(cacheKey);
            throw ex;
        }
    }

    /**
     * 构建单次查询内可复用的 embedding 缓存键。
     *
     * @param configuredVectorEmbeddingService embedding 服务
     * @return 缓存键
     */
    private String queryEmbeddingCacheKey(ConfiguredVectorEmbeddingService configuredVectorEmbeddingService) {
        Long profileId = configuredVectorEmbeddingService.getConfiguredProfileId();
        return "profile="
                + (profileId == null ? "" : profileId)
                + "|model="
                + nullToEmpty(configuredVectorEmbeddingService.getConfiguredModelName())
                + "|dimensions="
                + configuredVectorEmbeddingService.getConfiguredExpectedDimensions();
    }

    /**
     * 返回非空字符串。
     *
     * @param value 原始值
     * @return 非空字符串
     */
    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
