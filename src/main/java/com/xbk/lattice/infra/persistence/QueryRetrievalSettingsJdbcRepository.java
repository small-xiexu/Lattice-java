package com.xbk.lattice.infra.persistence;

import com.xbk.lattice.infra.persistence.mapper.QueryRetrievalSettingsMapper;
import com.xbk.lattice.query.service.QueryRetrievalSettingsState;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Query 检索配置 JDBC 仓储
 *
 * 职责：提供 query_retrieval_settings 表的读写能力
 *
 * @author xiexu
 */
@Repository
public class QueryRetrievalSettingsJdbcRepository {

    private final QueryRetrievalSettingsMapper queryRetrievalSettingsMapper;

    /**
     * 创建 Query 检索配置 JDBC 仓储。
     *
     * @param queryRetrievalSettingsMapper Query 检索配置 Mapper
     */
    public QueryRetrievalSettingsJdbcRepository(QueryRetrievalSettingsMapper queryRetrievalSettingsMapper) {
        this.queryRetrievalSettingsMapper = queryRetrievalSettingsMapper;
    }

    /**
     * 查询默认配置。
     *
     * @return 检索配置
     */
    public Optional<QueryRetrievalSettingsState> findDefault() {
        return Optional.ofNullable(queryRetrievalSettingsMapper.findDefault());
    }

    /**
     * 保存默认配置。
     *
     * @param state 检索配置
     * @return 保存后的检索配置
     */
    public QueryRetrievalSettingsState saveDefault(QueryRetrievalSettingsState state) {
        queryRetrievalSettingsMapper.saveDefault(state);
        return findDefault().orElse(state);
    }
}
