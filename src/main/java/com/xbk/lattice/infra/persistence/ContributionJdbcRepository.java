package com.xbk.lattice.infra.persistence;

import org.postgresql.util.PGobject;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Contribution JDBC 仓储
 *
 * 职责：提供确认后贡献的最小写入能力
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
}
