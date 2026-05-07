package com.xbk.lattice.infra.persistence;

import com.xbk.lattice.infra.persistence.mapper.QueryVectorConfigMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Query 向量配置 JDBC 仓储
 *
 * 职责：提供 query_vector_settings 表的读取与保存能力
 *
 * @author xiexu
 */
@Repository
public class QueryVectorConfigJdbcRepository {

    private final QueryVectorConfigMapper queryVectorConfigMapper;

    /**
     * 创建 Query 向量配置 JDBC 仓储。
     *
     * @param queryVectorConfigMapper Query 向量配置 Mapper
     */
    public QueryVectorConfigJdbcRepository(QueryVectorConfigMapper queryVectorConfigMapper) {
        this.queryVectorConfigMapper = queryVectorConfigMapper;
    }

    /**
     * 查询默认作用域的向量配置。
     *
     * @return 向量配置
     */
    public Optional<QueryVectorConfigRecord> findDefault() {
        return findByScope("default");
    }

    /**
     * 保存向量配置。
     *
     * @param record 向量配置记录
     * @return 保存后的记录
     */
    public QueryVectorConfigRecord save(QueryVectorConfigRecord record) {
        Optional<QueryVectorConfigRecord> existing = findByScope(record.getConfigScope());
        if (existing.isPresent()) {
            update(record);
        }
        else {
            insert(record);
        }
        return findByScope(record.getConfigScope()).orElseThrow();
    }

    /**
     * 查询指定作用域的向量配置。
     *
     * @param configScope 配置作用域
     * @return 向量配置
     */
    private Optional<QueryVectorConfigRecord> findByScope(String configScope) {
        return Optional.ofNullable(queryVectorConfigMapper.findByScope(configScope));
    }

    /**
     * 插入向量配置。
     *
     * @param record 向量配置记录
     */
    private void insert(QueryVectorConfigRecord record) {
        queryVectorConfigMapper.insert(record);
    }

    /**
     * 更新向量配置。
     *
     * @param record 向量配置记录
     */
    private void update(QueryVectorConfigRecord record) {
        queryVectorConfigMapper.update(record);
    }
}
