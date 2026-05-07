package com.xbk.lattice.query.service;

import com.xbk.lattice.infra.persistence.QueryRetrievalSettingsJdbcRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Query 检索配置服务
 *
 * 职责：提供并行召回与加权 RRF 的运行时配置
 *
 * @author xiexu
 */
@Service
public class QueryRetrievalSettingsService {

    private final QueryRetrievalSettingsJdbcRepository queryRetrievalSettingsJdbcRepository;

    /**
     * 创建 Query 检索配置服务。
     *
     * @param queryRetrievalSettingsJdbcRepository 检索配置仓储
     */
    @Autowired
    public QueryRetrievalSettingsService(QueryRetrievalSettingsJdbcRepository queryRetrievalSettingsJdbcRepository) {
        this.queryRetrievalSettingsJdbcRepository = queryRetrievalSettingsJdbcRepository;
    }

    /**
     * 创建默认配置服务。
     */
    public QueryRetrievalSettingsService() {
        this(null);
    }

    /**
     * 返回当前检索配置。
     *
     * @return 检索配置
     */
    public QueryRetrievalSettingsState getCurrentState() {
        if (queryRetrievalSettingsJdbcRepository == null) {
            return defaultState();
        }
        Optional<QueryRetrievalSettingsState> state = queryRetrievalSettingsJdbcRepository.findDefault();
        return state.orElseGet(this::defaultState);
    }

    /**
     * 返回默认配置。
     *
     * @return 默认配置
     */
    public QueryRetrievalSettingsState defaultState() {
        return new QueryRetrievalSettingsState(
                true,
                QueryRetrievalSettingsState.DEFAULT_REWRITE_ENABLED,
                QueryRetrievalSettingsState.DEFAULT_INTENT_AWARE_VECTOR_ENABLED,
                QueryRetrievalSettingsState.DEFAULT_FTS_WEIGHT,
                QueryRetrievalSettingsState.DEFAULT_REFKEY_WEIGHT,
                QueryRetrievalSettingsState.DEFAULT_ARTICLE_CHUNK_WEIGHT,
                QueryRetrievalSettingsState.DEFAULT_SOURCE_WEIGHT,
                QueryRetrievalSettingsState.DEFAULT_SOURCE_CHUNK_WEIGHT,
                QueryRetrievalSettingsState.DEFAULT_FACT_CARD_WEIGHT,
                QueryRetrievalSettingsState.DEFAULT_CONTRIBUTION_WEIGHT,
                QueryRetrievalSettingsState.DEFAULT_GRAPH_WEIGHT,
                QueryRetrievalSettingsState.DEFAULT_ARTICLE_VECTOR_WEIGHT,
                QueryRetrievalSettingsState.DEFAULT_CHUNK_VECTOR_WEIGHT,
                QueryRetrievalSettingsState.DEFAULT_RRF_K
        );
    }

    /**
     * 保存当前检索配置。
     *
     * @param parallelEnabled 是否启用并行召回
     * @param ftsWeight FTS 权重
     * @param sourceWeight Source 权重
     * @param contributionWeight Contribution 权重
     * @param graphWeight Graph 权重
     * @param articleVectorWeight 文章向量权重
     * @param chunkVectorWeight Chunk 向量权重
     * @param rrfK RRF K 值
     * @return 保存后的检索配置
     */
    @Transactional(rollbackFor = Exception.class)
    public QueryRetrievalSettingsState save(
            boolean parallelEnabled,
            double ftsWeight,
            double sourceWeight,
            double contributionWeight,
            double graphWeight,
            double articleVectorWeight,
            double chunkVectorWeight,
            int rrfK
    ) {
        return save(
                parallelEnabled,
                QueryRetrievalSettingsState.DEFAULT_REWRITE_ENABLED,
                QueryRetrievalSettingsState.DEFAULT_INTENT_AWARE_VECTOR_ENABLED,
                ftsWeight,
                QueryRetrievalSettingsState.DEFAULT_REFKEY_WEIGHT,
                QueryRetrievalSettingsState.DEFAULT_ARTICLE_CHUNK_WEIGHT,
                sourceWeight,
                QueryRetrievalSettingsState.DEFAULT_SOURCE_CHUNK_WEIGHT,
                QueryRetrievalSettingsState.DEFAULT_FACT_CARD_WEIGHT,
                contributionWeight,
                graphWeight,
                articleVectorWeight,
                chunkVectorWeight,
                rrfK
        );
    }

    /**
     * 保存当前检索配置。
     *
     * @param parallelEnabled 是否启用并行召回
     * @param rewriteEnabled 是否启用查询改写
     * @param intentAwareVectorEnabled 是否启用意图感知向量通道
     * @param ftsWeight FTS 权重
     * @param refkeyWeight RefKey 权重
     * @param articleChunkWeight Article Chunk lexical 权重
     * @param sourceWeight Source 文件级权重
     * @param sourceChunkWeight Source Chunk lexical 权重
     * @param factCardWeight Fact Card lexical 权重
     * @param contributionWeight Contribution 权重
     * @param graphWeight Graph 权重
     * @param articleVectorWeight 文章向量权重
     * @param chunkVectorWeight Chunk 向量权重
     * @param rrfK RRF K 值
     * @return 保存后的检索配置
     */
    @Transactional(rollbackFor = Exception.class)
    public QueryRetrievalSettingsState save(
            boolean parallelEnabled,
            boolean rewriteEnabled,
            boolean intentAwareVectorEnabled,
            double ftsWeight,
            double refkeyWeight,
            double articleChunkWeight,
            double sourceWeight,
            double sourceChunkWeight,
            double factCardWeight,
            double contributionWeight,
            double graphWeight,
            double articleVectorWeight,
            double chunkVectorWeight,
            int rrfK
    ) {
        if (queryRetrievalSettingsJdbcRepository == null) {
            return new QueryRetrievalSettingsState(
                    parallelEnabled,
                    rewriteEnabled,
                    intentAwareVectorEnabled,
                    ftsWeight,
                    refkeyWeight,
                    articleChunkWeight,
                    sourceWeight,
                    sourceChunkWeight,
                    factCardWeight,
                    contributionWeight,
                    graphWeight,
                    articleVectorWeight,
                    chunkVectorWeight,
                    rrfK
            );
        }
        return queryRetrievalSettingsJdbcRepository.saveDefault(new QueryRetrievalSettingsState(
                parallelEnabled,
                rewriteEnabled,
                intentAwareVectorEnabled,
                ftsWeight,
                refkeyWeight,
                articleChunkWeight,
                sourceWeight,
                sourceChunkWeight,
                factCardWeight,
                contributionWeight,
                graphWeight,
                articleVectorWeight,
                chunkVectorWeight,
                rrfK
        ));
    }
}
