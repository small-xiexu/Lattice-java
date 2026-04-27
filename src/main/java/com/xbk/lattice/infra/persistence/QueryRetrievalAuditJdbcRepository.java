package com.xbk.lattice.infra.persistence;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Query 检索审计 JDBC 仓储
 *
 * 职责：负责 query_retrieval_runs 与 query_retrieval_channel_hits 的读写
 *
 * @author xiexu
 */
@Repository
@Profile("jdbc")
public class QueryRetrievalAuditJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建 Query 检索审计 JDBC 仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public QueryRetrievalAuditJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 写入检索审计主记录。
     *
     * @param record 审计主记录
     * @return 主键
     */
    public Long insertRun(QueryRetrievalRunRecord record) {
        return jdbcTemplate.queryForObject(
                """
                        insert into query_retrieval_runs (
                            query_id, question, normalized_question, retrieval_question,
                            version_tag, strategy_tag, question_type_tag, retrieval_mode,
                            rewrite_applied, rewrite_audit_ref, retrieval_strategy_ref,
                            fused_hit_count, channel_count
                        )
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        returning run_id
                        """,
                Long.class,
                record.getQueryId(),
                record.getQuestion(),
                record.getNormalizedQuestion(),
                record.getRetrievalQuestion(),
                record.getVersionTag(),
                record.getStrategyTag(),
                record.getQuestionTypeTag(),
                record.getRetrievalMode(),
                Boolean.valueOf(record.isRewriteApplied()),
                record.getRewriteAuditRef(),
                record.getRetrievalStrategyRef(),
                Integer.valueOf(record.getFusedHitCount()),
                Integer.valueOf(record.getChannelCount())
        );
    }

    /**
     * 写入通道命中明细。
     *
     * @param record 通道命中记录
     */
    public void insertChannelHit(QueryRetrievalChannelHitRecord record) {
        jdbcTemplate.update(
                """
                        insert into query_retrieval_channel_hits (
                            run_id, channel_name, hit_rank, fused_rank, included_in_fused,
                            channel_weight, evidence_type, article_key, concept_id,
                            title, score, source_paths_json, metadata_json
                        )
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb)
                        """,
                record.getRunId(),
                record.getChannelName(),
                Integer.valueOf(record.getHitRank()),
                record.getFusedRank(),
                Boolean.valueOf(record.isIncludedInFused()),
                Double.valueOf(record.getChannelWeight()),
                record.getEvidenceType(),
                record.getArticleKey(),
                record.getConceptId(),
                record.getTitle(),
                Double.valueOf(record.getScore()),
                record.getSourcePathsJson(),
                record.getMetadataJson()
        );
    }

    /**
     * 查询指定 queryId 的最近一次检索审计。
     *
     * @param queryId 查询标识
     * @return 最近一次检索审计
     */
    public Optional<QueryRetrievalRunView> findLatestRunByQueryId(String queryId) {
        List<QueryRetrievalRunView> runs = jdbcTemplate.query(
                """
                        select run_id, query_id, question, normalized_question, retrieval_question,
                               version_tag, strategy_tag, question_type_tag, retrieval_mode,
                               rewrite_applied, rewrite_audit_ref, retrieval_strategy_ref,
                               fused_hit_count, channel_count, created_at
                        from query_retrieval_runs
                        where query_id = ?
                        order by created_at desc, run_id desc
                        limit 1
                        """,
                this::mapRunView,
                queryId
        );
        if (runs.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(runs.get(0));
    }

    /**
     * 查询指定 queryId 的最近若干次检索审计。
     *
     * @param queryId 查询标识
     * @param limit 返回数量
     * @return 检索审计列表
     */
    public List<QueryRetrievalRunView> findRunsByQueryId(String queryId, int limit) {
        return jdbcTemplate.query(
                """
                        select run_id, query_id, question, normalized_question, retrieval_question,
                               version_tag, strategy_tag, question_type_tag, retrieval_mode,
                               rewrite_applied, rewrite_audit_ref, retrieval_strategy_ref,
                               fused_hit_count, channel_count, created_at
                        from query_retrieval_runs
                        where query_id = ?
                        order by created_at desc, run_id desc
                        limit ?
                        """,
                this::mapRunView,
                queryId,
                Integer.valueOf(limit)
        );
    }

    /**
     * 查询最近若干次检索审计。
     *
     * @param limit 返回数量
     * @return 最近检索审计列表
     */
    public List<QueryRetrievalRunView> findRecentRuns(int limit) {
        return jdbcTemplate.query(
                """
                        select run_id, query_id, question, normalized_question, retrieval_question,
                               version_tag, strategy_tag, question_type_tag, retrieval_mode,
                               rewrite_applied, rewrite_audit_ref, retrieval_strategy_ref,
                               fused_hit_count, channel_count, created_at
                        from query_retrieval_runs
                        order by created_at desc, run_id desc
                        limit ?
                        """,
                this::mapRunView,
                Integer.valueOf(limit)
        );
    }

    /**
     * 查询指定 runId 的全部通道命中明细。
     *
     * @param runId 审计主键
     * @return 通道命中明细
     */
    public List<QueryRetrievalChannelHitView> findChannelHitsByRunId(Long runId) {
        return jdbcTemplate.query(
                """
                        select hit_id, run_id, channel_name, hit_rank, fused_rank, included_in_fused,
                               channel_weight, evidence_type, article_key, concept_id, title, score,
                               source_paths_json::text as source_paths_json,
                               metadata_json::text as metadata_json,
                               created_at
                        from query_retrieval_channel_hits
                        where run_id = ?
                        order by channel_name asc, hit_rank asc, hit_id asc
                        """,
                this::mapChannelHitView,
                runId
        );
    }

    /**
     * 映射检索审计主记录视图。
     *
     * @param resultSet 结果集
     * @param rowNum 行号
     * @return 检索审计主记录视图
     * @throws SQLException SQL 异常
     */
    private QueryRetrievalRunView mapRunView(ResultSet resultSet, int rowNum) throws SQLException {
        return new QueryRetrievalRunView(
                resultSet.getLong("run_id"),
                resultSet.getString("query_id"),
                resultSet.getString("question"),
                resultSet.getString("normalized_question"),
                resultSet.getString("retrieval_question"),
                resultSet.getString("version_tag"),
                resultSet.getString("strategy_tag"),
                resultSet.getString("question_type_tag"),
                resultSet.getString("retrieval_mode"),
                resultSet.getBoolean("rewrite_applied"),
                resultSet.getString("rewrite_audit_ref"),
                resultSet.getString("retrieval_strategy_ref"),
                resultSet.getInt("fused_hit_count"),
                resultSet.getInt("channel_count"),
                resultSet.getObject("created_at", OffsetDateTime.class)
        );
    }

    /**
     * 映射通道命中明细视图。
     *
     * @param resultSet 结果集
     * @param rowNum 行号
     * @return 通道命中明细视图
     * @throws SQLException SQL 异常
     */
    private QueryRetrievalChannelHitView mapChannelHitView(ResultSet resultSet, int rowNum) throws SQLException {
        Integer fusedRank = (Integer) resultSet.getObject("fused_rank");
        return new QueryRetrievalChannelHitView(
                resultSet.getLong("hit_id"),
                resultSet.getLong("run_id"),
                resultSet.getString("channel_name"),
                resultSet.getInt("hit_rank"),
                fusedRank,
                resultSet.getBoolean("included_in_fused"),
                resultSet.getDouble("channel_weight"),
                resultSet.getString("evidence_type"),
                resultSet.getString("article_key"),
                resultSet.getString("concept_id"),
                resultSet.getString("title"),
                resultSet.getDouble("score"),
                resultSet.getString("source_paths_json"),
                resultSet.getString("metadata_json"),
                resultSet.getObject("created_at", OffsetDateTime.class)
        );
    }
}
