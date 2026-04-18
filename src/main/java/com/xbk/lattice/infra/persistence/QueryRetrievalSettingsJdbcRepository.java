package com.xbk.lattice.infra.persistence;

import com.xbk.lattice.query.service.QueryRetrievalSettingsState;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Query 检索配置 JDBC 仓储
 *
 * 职责：提供 query_retrieval_settings 表的读写能力
 *
 * @author xiexu
 */
@Repository
@Profile("jdbc")
public class QueryRetrievalSettingsJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建 Query 检索配置 JDBC 仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public QueryRetrievalSettingsJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 查询默认配置。
     *
     * @return 检索配置
     */
    public Optional<QueryRetrievalSettingsState> findDefault() {
        if (jdbcTemplate == null) {
            return Optional.empty();
        }
        List<QueryRetrievalSettingsState> states = jdbcTemplate.query(
                """
                        select parallel_enabled, fts_weight, source_weight, contribution_weight,
                               article_vector_weight, chunk_vector_weight, rrf_k
                        from query_retrieval_settings
                        where id = 1
                        """,
                (resultSet, rowNum) -> new QueryRetrievalSettingsState(
                        resultSet.getBoolean("parallel_enabled"),
                        resultSet.getDouble("fts_weight"),
                        resultSet.getDouble("source_weight"),
                        resultSet.getDouble("contribution_weight"),
                        resultSet.getDouble("article_vector_weight"),
                        resultSet.getDouble("chunk_vector_weight"),
                        resultSet.getInt("rrf_k")
                )
        );
        if (states.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(states.get(0));
    }

    /**
     * 保存默认配置。
     *
     * @param state 检索配置
     * @return 保存后的检索配置
     */
    public QueryRetrievalSettingsState saveDefault(QueryRetrievalSettingsState state) {
        jdbcTemplate.update(
                """
                        insert into query_retrieval_settings (
                            id, parallel_enabled, fts_weight, source_weight, contribution_weight,
                            article_vector_weight, chunk_vector_weight, rrf_k, updated_at
                        ) values (?, ?, ?, ?, ?, ?, ?, ?, current_timestamp)
                        on conflict (id) do update set
                            parallel_enabled = excluded.parallel_enabled,
                            fts_weight = excluded.fts_weight,
                            source_weight = excluded.source_weight,
                            contribution_weight = excluded.contribution_weight,
                            article_vector_weight = excluded.article_vector_weight,
                            chunk_vector_weight = excluded.chunk_vector_weight,
                            rrf_k = excluded.rrf_k,
                            updated_at = current_timestamp
                        """,
                Integer.valueOf(1),
                Boolean.valueOf(state.isParallelEnabled()),
                Double.valueOf(state.getFtsWeight()),
                Double.valueOf(state.getSourceWeight()),
                Double.valueOf(state.getContributionWeight()),
                Double.valueOf(state.getArticleVectorWeight()),
                Double.valueOf(state.getChunkVectorWeight()),
                Integer.valueOf(state.getRrfK())
        );
        return findDefault().orElse(state);
    }
}
