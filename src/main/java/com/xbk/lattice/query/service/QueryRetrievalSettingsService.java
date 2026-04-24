package com.xbk.lattice.query.service;

import com.xbk.lattice.infra.persistence.QueryRetrievalSettingsJdbcRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
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
@Profile("jdbc")
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
                1.0D,
                1.0D,
                1.0D,
                0.9D,
                0.6D,
                1.2D,
                60
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
        if (queryRetrievalSettingsJdbcRepository == null) {
            return new QueryRetrievalSettingsState(
                    parallelEnabled,
                    ftsWeight,
                    sourceWeight,
                    contributionWeight,
                    graphWeight,
                    articleVectorWeight,
                    chunkVectorWeight,
                    rrfK
            );
        }
        return queryRetrievalSettingsJdbcRepository.saveDefault(new QueryRetrievalSettingsState(
                parallelEnabled,
                ftsWeight,
                sourceWeight,
                contributionWeight,
                graphWeight,
                articleVectorWeight,
                chunkVectorWeight,
                rrfK
        ));
    }
}
