package com.xbk.lattice.infra.persistence;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * SourceFile JDBC 仓储
 *
 * 职责：提供最小源文件落盘与读取能力
 *
 * @author xiexu
 */
@Repository
@Profile("jdbc")
public class SourceFileJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建 SourceFile JDBC 仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public SourceFileJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 保存或更新源文件记录。
     *
     * @param sourceFileRecord 源文件记录
     */
    public void upsert(SourceFileRecord sourceFileRecord) {
        String sql = """
                insert into source_files (file_path, content_preview, format, file_size)
                values (?, ?, ?, ?)
                on conflict (file_path) do update
                set content_preview = excluded.content_preview,
                    format = excluded.format,
                    file_size = excluded.file_size,
                    indexed_at = CURRENT_TIMESTAMP
                """;
        jdbcTemplate.update(
                sql,
                sourceFileRecord.getFilePath(),
                sourceFileRecord.getContentPreview(),
                sourceFileRecord.getFormat(),
                sourceFileRecord.getFileSize()
        );
    }

    /**
     * 按路径查询源文件记录。
     *
     * @param filePath 文件路径
     * @return 源文件记录
     */
    public Optional<SourceFileRecord> findByPath(String filePath) {
        String sql = """
                select file_path, content_preview, format, file_size
                from source_files
                where file_path = ?
                """;
        List<SourceFileRecord> records = jdbcTemplate.query(sql, this::mapSourceFileRecord, filePath);
        if (records.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(records.get(0));
    }

    /**
     * 映射单行源文件记录。
     *
     * @param resultSet 结果集
     * @param rowNum 行号
     * @return 源文件记录
     * @throws SQLException SQL 异常
     */
    private SourceFileRecord mapSourceFileRecord(ResultSet resultSet, int rowNum) throws SQLException {
        return new SourceFileRecord(
                resultSet.getString("file_path"),
                resultSet.getString("content_preview"),
                resultSet.getString("format"),
                resultSet.getLong("file_size")
        );
    }
}
