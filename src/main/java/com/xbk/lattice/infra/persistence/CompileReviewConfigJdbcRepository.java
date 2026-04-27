package com.xbk.lattice.infra.persistence;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Compile 审查配置 JDBC 仓储
 *
 * 职责：提供 compile_review_settings 表的读取与保存能力
 *
 * @author xiexu
 */
@Repository
@Profile("jdbc")
public class CompileReviewConfigJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建 Compile 审查配置 JDBC 仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public CompileReviewConfigJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 查询默认配置。
     *
     * @return 默认配置
     */
    public Optional<CompileReviewConfigRecord> findDefault() {
        if (jdbcTemplate == null) {
            return Optional.empty();
        }
        List<CompileReviewConfigRecord> records = jdbcTemplate.query(
                """
                        select config_scope, auto_fix_enabled, max_fix_rounds,
                               allow_persist_needs_human_review, human_review_severity_threshold,
                               created_by, updated_by, created_at, updated_at
                        from compile_review_settings
                        where config_scope = 'default'
                        """,
                (resultSet, rowNum) -> new CompileReviewConfigRecord(
                        resultSet.getString("config_scope"),
                        resultSet.getBoolean("auto_fix_enabled"),
                        resultSet.getInt("max_fix_rounds"),
                        resultSet.getBoolean("allow_persist_needs_human_review"),
                        resultSet.getString("human_review_severity_threshold"),
                        resultSet.getString("created_by"),
                        resultSet.getString("updated_by"),
                        resultSet.getObject("created_at", OffsetDateTime.class),
                        resultSet.getObject("updated_at", OffsetDateTime.class)
                )
        );
        if (records.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(records.get(0));
    }

    /**
     * 保存默认配置。
     *
     * @param record 配置记录
     * @return 保存后的配置记录
     */
    public CompileReviewConfigRecord saveDefault(CompileReviewConfigRecord record) {
        jdbcTemplate.update(
                """
                        insert into compile_review_settings (
                            config_scope, auto_fix_enabled, max_fix_rounds,
                            allow_persist_needs_human_review, human_review_severity_threshold,
                            created_by, updated_by, created_at, updated_at
                        ) values (?, ?, ?, ?, ?, ?, ?, coalesce(?, current_timestamp), current_timestamp)
                        on conflict (config_scope) do update set
                            auto_fix_enabled = excluded.auto_fix_enabled,
                            max_fix_rounds = excluded.max_fix_rounds,
                            allow_persist_needs_human_review = excluded.allow_persist_needs_human_review,
                            human_review_severity_threshold = excluded.human_review_severity_threshold,
                            updated_by = excluded.updated_by,
                            updated_at = current_timestamp
                        """,
                record.getConfigScope(),
                Boolean.valueOf(record.isAutoFixEnabled()),
                Integer.valueOf(record.getMaxFixRounds()),
                Boolean.valueOf(record.isAllowPersistNeedsHumanReview()),
                record.getHumanReviewSeverityThreshold(),
                record.getCreatedBy(),
                record.getUpdatedBy(),
                record.getCreatedAt()
        );
        return findDefault().orElse(record);
    }
}
