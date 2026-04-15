package com.xbk.lattice.infra.persistence;

import org.postgresql.util.PGobject;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Contribution JDBC 仓储
 *
 * 职责：提供确认后贡献的最小写入与查询能力
 *
 * @author xiexu
 */
@Repository
@Profile("jdbc")
public class ContributionJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建 Contribution JDBC 仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public ContributionJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 保存贡献记录。
     *
     * @param contributionRecord 贡献记录
     */
    public void save(ContributionRecord contributionRecord) {
        String sql = """
                insert into contributions (id, question, answer, corrections, confirmed_by, confirmed_at)
                values (?, ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.update(connection -> {
            PGobject correctionsObject = new PGobject();
            correctionsObject.setType("jsonb");
            correctionsObject.setValue(contributionRecord.getCorrectionsJson());

            java.sql.PreparedStatement preparedStatement = connection.prepareStatement(sql);
            preparedStatement.setObject(1, contributionRecord.getId());
            preparedStatement.setString(2, contributionRecord.getQuestion());
            preparedStatement.setString(3, contributionRecord.getAnswer());
            preparedStatement.setObject(4, correctionsObject);
            preparedStatement.setString(5, contributionRecord.getConfirmedBy());
            preparedStatement.setObject(6, contributionRecord.getConfirmedAt());
            return preparedStatement;
        });
    }

    /**
     * 查询全部贡献记录。
     *
     * @return 贡献记录列表
     */
    public List<ContributionRecord> findAll() {
        String sql = """
                select id, question, answer, corrections::text as corrections_json, confirmed_by, confirmed_at
                from contributions
                order by confirmed_at desc, id desc
                """;
        return jdbcTemplate.query(sql, this::mapContributionRecord);
    }

    /**
     * 映射贡献记录。
     *
     * @param resultSet 结果集
     * @param rowNum 行号
     * @return 贡献记录
     * @throws SQLException SQL 异常
     */
    private ContributionRecord mapContributionRecord(ResultSet resultSet, int rowNum) throws SQLException {
        return new ContributionRecord(
                resultSet.getObject("id", java.util.UUID.class),
                resultSet.getString("question"),
                resultSet.getString("answer"),
                resultSet.getString("corrections_json"),
                resultSet.getString("confirmed_by"),
                resultSet.getObject("confirmed_at", java.time.OffsetDateTime.class)
        );
    }
}
