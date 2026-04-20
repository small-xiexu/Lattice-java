package com.xbk.lattice.infra.persistence;

import org.postgresql.util.PGobject;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * PendingQuery JDBC 仓储
 *
 * 职责：提供待确认查询的最小持久化能力
 *
 * @author xiexu
 */
@Repository
@Profile("jdbc")
public class PendingQueryJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建 PendingQuery JDBC 仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public PendingQueryJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 保存或更新待确认查询。
     *
     * @param pendingQueryRecord 待确认查询记录
     */
    public void upsert(PendingQueryRecord pendingQueryRecord) {
        if (jdbcTemplate == null) {
            return;
        }

        String sql = """
                insert into pending_queries (
                    query_id, question, answer, selected_concept_ids, selected_article_keys, source_file_paths,
                    corrections, review_status, created_at, expires_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (query_id) do update
                set answer = excluded.answer,
                    selected_concept_ids = excluded.selected_concept_ids,
                    selected_article_keys = excluded.selected_article_keys,
                    source_file_paths = excluded.source_file_paths,
                    corrections = excluded.corrections,
                    review_status = excluded.review_status,
                    expires_at = excluded.expires_at
                """;
        jdbcTemplate.update(connection -> {
            Array conceptIdsArray = connection.createArrayOf(
                    "text",
                    pendingQueryRecord.getSelectedConceptIds().toArray(new String[0])
            );
            Array articleKeysArray = connection.createArrayOf(
                    "text",
                    pendingQueryRecord.getSelectedArticleKeys().toArray(new String[0])
            );
            Array sourcePathsArray = connection.createArrayOf(
                    "text",
                    pendingQueryRecord.getSourceFilePaths().toArray(new String[0])
            );
            PGobject correctionsObject = new PGobject();
            correctionsObject.setType("jsonb");
            correctionsObject.setValue(pendingQueryRecord.getCorrectionsJson());

            java.sql.PreparedStatement preparedStatement = connection.prepareStatement(sql);
            preparedStatement.setString(1, pendingQueryRecord.getQueryId());
            preparedStatement.setString(2, pendingQueryRecord.getQuestion());
            preparedStatement.setString(3, pendingQueryRecord.getAnswer());
            preparedStatement.setArray(4, conceptIdsArray);
            preparedStatement.setArray(5, articleKeysArray);
            preparedStatement.setArray(6, sourcePathsArray);
            preparedStatement.setObject(7, correctionsObject);
            preparedStatement.setString(8, pendingQueryRecord.getReviewStatus());
            preparedStatement.setObject(9, pendingQueryRecord.getCreatedAt());
            preparedStatement.setObject(10, pendingQueryRecord.getExpiresAt());
            return preparedStatement;
        });
    }

    /**
     * 按 queryId 查询待确认记录。
     *
     * @param queryId 查询标识
     * @return 待确认记录
     */
    public Optional<PendingQueryRecord> findByQueryId(String queryId) {
        if (jdbcTemplate == null) {
            return Optional.empty();
        }

        String sql = """
                select query_id, question, answer, selected_concept_ids, selected_article_keys, source_file_paths,
                       corrections::text as corrections, review_status, created_at, expires_at
                from pending_queries
                where query_id = ?
                """;
        List<PendingQueryRecord> records = jdbcTemplate.query(sql, this::mapPendingQueryRecord, queryId);
        if (records.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(records.get(0));
    }

    /**
     * 查询全部未过期的待确认记录。
     *
     * @return 待确认记录列表
     */
    public List<PendingQueryRecord> findAllActive() {
        if (jdbcTemplate == null) {
            return List.of();
        }

        String sql = """
                select query_id, question, answer, selected_concept_ids, selected_article_keys, source_file_paths,
                       corrections::text as corrections, review_status, created_at, expires_at
                from pending_queries
                where expires_at >= CURRENT_TIMESTAMP
                order by created_at desc, query_id desc
                """;
        return jdbcTemplate.query(sql, this::mapPendingQueryRecord);
    }

    /**
     * 删除待确认记录。
     *
     * @param queryId 查询标识
     */
    public void deleteByQueryId(String queryId) {
        if (jdbcTemplate == null) {
            return;
        }
        jdbcTemplate.update("delete from pending_queries where query_id = ?", queryId);
    }

    /**
     * 映射待确认查询记录。
     *
     * @param resultSet 结果集
     * @param rowNum 行号
     * @return 待确认查询记录
     * @throws SQLException SQL 异常
     */
    private PendingQueryRecord mapPendingQueryRecord(ResultSet resultSet, int rowNum) throws SQLException {
        return new PendingQueryRecord(
                resultSet.getString("query_id"),
                resultSet.getString("question"),
                resultSet.getString("answer"),
                readTextArray(resultSet, "selected_concept_ids"),
                readTextArray(resultSet, "selected_article_keys"),
                readTextArray(resultSet, "source_file_paths"),
                resultSet.getString("corrections"),
                resultSet.getString("review_status"),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("expires_at", OffsetDateTime.class)
        );
    }

    /**
     * 读取文本数组。
     *
     * @param resultSet 结果集
     * @param fieldName 字段名
     * @return 文本数组
     * @throws SQLException SQL 异常
     */
    private List<String> readTextArray(ResultSet resultSet, String fieldName) throws SQLException {
        Array array = resultSet.getArray(fieldName);
        if (array == null) {
            return List.of();
        }
        Object[] values = (Object[]) array.getArray();
        List<String> items = new ArrayList<String>();
        for (Object value : values) {
            items.add(String.valueOf(value));
        }
        return items;
    }
}
