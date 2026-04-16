package com.xbk.lattice.infra.persistence;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 质量指标历史 JDBC 仓储
 *
 * 职责：提供质量快照历史的写入与按时间窗口查询能力
 *
 * @author xiexu
 */
@Repository
@Profile("jdbc")
public class QualityMetricsHistoryJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建质量指标历史仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public QualityMetricsHistoryJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 保存一条质量历史记录。
     *
     * @param record 历史记录
     */
    public void save(QualityMetricsHistoryRecord record) {
        String sql = """
                insert into quality_metrics_history (
                    measured_at, total_articles, passed_articles, pending_articles, needs_review,
                    contributions, source_count, review_pass_rate, grounding_rate, referential_rate
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.update(
                sql,
                record.getMeasuredAt(),
                record.getTotalArticles(),
                record.getPassedArticles(),
                record.getPendingArticles(),
                record.getNeedsReview(),
                record.getContributions(),
                record.getSourceCount(),
                record.getReviewPassRate(),
                record.getGroundingRate(),
                record.getReferentialRate()
        );
    }

    /**
     * 查询最近 N 天的质量历史。
     *
     * @param days 天数
     * @return 历史记录列表
     */
    public List<QualityMetricsHistoryRecord> findSince(int days) {
        String sql = """
                select id, measured_at, total_articles, passed_articles, pending_articles, needs_review,
                       contributions, source_count, review_pass_rate, grounding_rate, referential_rate
                from quality_metrics_history
                where measured_at > current_timestamp - (? * interval '1 day')
                order by measured_at desc, id desc
                """;
        return jdbcTemplate.query(sql, this::mapRecord, Math.max(days, 0));
    }

    private QualityMetricsHistoryRecord mapRecord(ResultSet resultSet, int rowNum) throws SQLException {
        return new QualityMetricsHistoryRecord(
                resultSet.getLong("id"),
                resultSet.getObject("measured_at", OffsetDateTime.class),
                resultSet.getInt("total_articles"),
                resultSet.getInt("passed_articles"),
                resultSet.getInt("pending_articles"),
                resultSet.getInt("needs_review"),
                resultSet.getInt("contributions"),
                resultSet.getInt("source_count"),
                resultSet.getDouble("review_pass_rate"),
                resultSet.getDouble("grounding_rate"),
                resultSet.getDouble("referential_rate")
        );
    }
}
