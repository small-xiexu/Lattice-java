package com.xbk.lattice.infra.persistence;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    public SourceFileRecord upsert(SourceFileRecord sourceFileRecord) {
        if (jdbcTemplate == null) {
            return sourceFileRecord;
        }

        if (sourceFileRecord.getSourceId() == null) {
            SourceFileRecord legacyBoundRecord = bindLegacyDefaultSource(sourceFileRecord);
            return upsertSourceAwareRecord(legacyBoundRecord);
        }
        return upsertSourceAwareRecord(sourceFileRecord);
    }

    /**
     * 按路径查询源文件记录。
     *
     * @param filePath 文件路径
     * @return 源文件记录
     */
    public Optional<SourceFileRecord> findByPath(String filePath) {
        if (jdbcTemplate == null) {
            return Optional.empty();
        }

        String sql = """
                select id, source_id, file_path, relative_path, source_sync_run_id, content_preview,
                       format, file_size, content_text, metadata_json::text as metadata_json,
                       is_verbatim, raw_path
                from source_files
                where file_path = ? or relative_path = ?
                order by indexed_at desc, id desc
                limit 1
                """;
        List<SourceFileRecord> records = jdbcTemplate.query(sql, this::mapSourceFileRecord, filePath, filePath);
        if (records.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(records.get(0));
    }

    /**
     * 按资料源和相对路径查询源文件记录。
     *
     * @param sourceId 资料源主键
     * @param relativePath 相对路径
     * @return 源文件记录
     */
    public Optional<SourceFileRecord> findBySourceIdAndRelativePath(Long sourceId, String relativePath) {
        if (jdbcTemplate == null || sourceId == null) {
            return Optional.empty();
        }

        String sql = """
                select id, source_id, file_path, relative_path, source_sync_run_id, content_preview,
                       format, file_size, content_text, metadata_json::text as metadata_json,
                       is_verbatim, raw_path
                from source_files
                where source_id = ?
                  and relative_path = ?
                """;
        List<SourceFileRecord> records = jdbcTemplate.query(sql, this::mapSourceFileRecord, sourceId, relativePath);
        if (records.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(records.get(0));
    }

    /**
     * 查询全部源文件记录。
     *
     * @return 源文件记录列表
     */
    public List<SourceFileRecord> findAll() {
        if (jdbcTemplate == null) {
            return List.of();
        }

        String sql = """
                select id, source_id, file_path, relative_path, source_sync_run_id, content_preview,
                       format, file_size, content_text, metadata_json::text as metadata_json,
                       is_verbatim, raw_path
                from source_files
                order by source_id asc nulls first, relative_path asc nulls first, file_path asc
                """;
        return jdbcTemplate.query(sql, this::mapSourceFileRecord);
    }

    /**
     * 查询指定资料源下的全部源文件记录。
     *
     * @param sourceId 资料源主键
     * @return 源文件记录列表
     */
    public List<SourceFileRecord> findBySourceId(Long sourceId) {
        if (jdbcTemplate == null || sourceId == null) {
            return List.of();
        }

        String sql = """
                select id, source_id, file_path, relative_path, source_sync_run_id, content_preview,
                       format, file_size, content_text, metadata_json::text as metadata_json,
                       is_verbatim, raw_path
                from source_files
                where source_id = ?
                order by relative_path asc, id asc
                """;
        return jdbcTemplate.query(sql, this::mapSourceFileRecord, sourceId);
    }

    /**
     * 按主键批量查询源文件记录。
     *
     * @param sourceFileIds 源文件主键列表
     * @return 主键到源文件记录的映射
     */
    public Map<Long, SourceFileRecord> findByIds(List<Long> sourceFileIds) {
        Map<Long, SourceFileRecord> sourceFileMap = new LinkedHashMap<Long, SourceFileRecord>();
        if (jdbcTemplate == null || sourceFileIds == null || sourceFileIds.isEmpty()) {
            return sourceFileMap;
        }
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("""
                select id, source_id, file_path, relative_path, source_sync_run_id, content_preview,
                       format, file_size, content_text, metadata_json::text as metadata_json,
                       is_verbatim, raw_path
                from source_files
                where id in (
                """);
        for (int index = 0; index < sourceFileIds.size(); index++) {
            if (index > 0) {
                sqlBuilder.append(", ");
            }
            sqlBuilder.append("?");
        }
        sqlBuilder.append(")");
        List<SourceFileRecord> sourceFileRecords = jdbcTemplate.query(
                sqlBuilder.toString(),
                this::mapSourceFileRecord,
                sourceFileIds.toArray()
        );
        for (SourceFileRecord sourceFileRecord : sourceFileRecords) {
            sourceFileMap.put(sourceFileRecord.getId(), sourceFileRecord);
        }
        return sourceFileMap;
    }

    /**
     * 执行 source file 数据库侧 lexical 检索。
     *
     * @param question 查询问题
     * @param queryTokens 查询 token
     * @param limit 返回数量
     * @param tsConfig FTS 配置
     * @return lexical 命中记录
     */
    public List<LexicalSearchRecord> searchLexical(
            String question,
            List<String> queryTokens,
            int limit,
            String tsConfig
    ) {
        if (jdbcTemplate == null) {
            return List.of();
        }
        List<String> normalizedTokens = normalizeTokens(queryTokens);
        if (!hasText(question) && normalizedTokens.isEmpty()) {
            return List.of();
        }

        List<Object> parameters = new ArrayList<Object>();
        parameters.add(normalizeTsConfig(tsConfig));
        parameters.add(question == null ? "" : question);
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("""
                with query as (
                    select plainto_tsquery(cast(? as regconfig), ?) as tsq
                )
                select sf.id,
                       sf.source_id,
                       sf.file_path,
                       sf.relative_path,
                       sf.content_preview,
                       sf.content_text,
                       sf.metadata_json::text as metadata_json,
                       sf.is_verbatim,
                       ts_rank_cd(sf.search_tsv, query.tsq)
                """);
        appendTokenScore(
                sqlBuilder,
                parameters,
                normalizedTokens,
                List.of("sf.file_path_norm", "lower(sf.content_preview)", "lower(sf.content_text)"),
                List.of(Double.valueOf(4.0D), Double.valueOf(1.5D), Double.valueOf(1.0D))
        );
        sqlBuilder.append("""
                       as score
                from source_files sf
                cross join query
                where sf.search_tsv @@ query.tsq
                """);
        appendTokenWhere(
                sqlBuilder,
                parameters,
                normalizedTokens,
                List.of("sf.file_path_norm", "lower(sf.content_preview)", "lower(sf.content_text)")
        );
        sqlBuilder.append("""
                order by score desc, sf.indexed_at desc, sf.relative_path asc, sf.file_path asc
                limit ?
                """);
        parameters.add(Integer.valueOf(safeLimit(limit)));
        return jdbcTemplate.query(sqlBuilder.toString(), this::mapLexicalSearchRecord, parameters.toArray());
    }

    private SourceFileRecord upsertSourceAwareRecord(SourceFileRecord sourceFileRecord) {
        String filePathNorm = buildFilePathNorm(sourceFileRecord);
        String searchText = buildSearchText(sourceFileRecord);
        String sql = """
                insert into source_files (
                    source_id, file_path, relative_path, source_sync_run_id, content_preview,
                    format, file_size, content_text, metadata_json, is_verbatim, raw_path,
                    file_path_norm, search_tsv
                )
                values (?, ?, ?, ?, ?, ?, ?, ?::text, ?::jsonb, ?, ?, ?, to_tsvector('simple'::regconfig, ?))
                on conflict (source_id, relative_path) do update
                set file_path = excluded.file_path,
                    source_sync_run_id = excluded.source_sync_run_id,
                    content_preview = excluded.content_preview,
                    format = excluded.format,
                    file_size = excluded.file_size,
                    content_text = excluded.content_text,
                    metadata_json = excluded.metadata_json,
                    is_verbatim = excluded.is_verbatim,
                    raw_path = excluded.raw_path,
                    file_path_norm = excluded.file_path_norm,
                    search_tsv = excluded.search_tsv,
                    indexed_at = CURRENT_TIMESTAMP
                returning id, source_id, file_path, relative_path, source_sync_run_id, content_preview,
                          format, file_size, content_text, metadata_json::text as metadata_json,
                          is_verbatim, raw_path
                """;
        return jdbcTemplate.queryForObject(
                sql,
                this::mapSourceFileRecord,
                sourceFileRecord.getSourceId(),
                sourceFileRecord.getFilePath(),
                sourceFileRecord.getRelativePath(),
                sourceFileRecord.getSourceSyncRunId(),
                sourceFileRecord.getContentPreview(),
                sourceFileRecord.getFormat(),
                sourceFileRecord.getFileSize(),
                sourceFileRecord.getContentText(),
                sourceFileRecord.getMetadataJson(),
                sourceFileRecord.isVerbatim(),
                sourceFileRecord.getRawPath(),
                filePathNorm,
                searchText
        );
    }

    private SourceFileRecord bindLegacyDefaultSource(SourceFileRecord sourceFileRecord) {
        Long legacyDefaultSourceId = resolveLegacyDefaultSourceId();
        String relativePath = sourceFileRecord.getRelativePath();
        if (relativePath == null || relativePath.isBlank()) {
            relativePath = sourceFileRecord.getFilePath();
        }
        return new SourceFileRecord(
                sourceFileRecord.getId(),
                legacyDefaultSourceId,
                sourceFileRecord.getFilePath(),
                relativePath,
                sourceFileRecord.getSourceSyncRunId(),
                sourceFileRecord.getContentPreview(),
                sourceFileRecord.getFormat(),
                sourceFileRecord.getFileSize(),
                sourceFileRecord.getContentText(),
                sourceFileRecord.getMetadataJson(),
                sourceFileRecord.isVerbatim(),
                sourceFileRecord.getRawPath()
        );
    }

    private Long resolveLegacyDefaultSourceId() {
        List<Long> sourceIds = jdbcTemplate.queryForList(
                "select id from knowledge_sources where source_code = 'legacy-default'",
                Long.class
        );
        if (!sourceIds.isEmpty()) {
            return sourceIds.get(0);
        }

        jdbcTemplate.update(
                """
                        insert into knowledge_sources (
                            source_code,
                            name,
                            source_type,
                            content_profile,
                            status,
                            visibility,
                            default_sync_mode,
                            config_json,
                            metadata_json
                        )
                        values (
                            'legacy-default',
                            'Legacy Default Source',
                            'UPLOAD',
                            'DOCUMENT',
                            'ACTIVE',
                            'NORMAL',
                            'FULL',
                            '{}'::jsonb,
                            '{"legacyDefault":true}'::jsonb
                        )
                        on conflict (source_code) do nothing
                        """
        );

        List<Long> ensuredSourceIds = jdbcTemplate.queryForList(
                "select id from knowledge_sources where source_code = 'legacy-default'",
                Long.class
        );
        if (ensuredSourceIds.isEmpty()) {
            throw new IllegalStateException("legacy-default knowledge source is missing");
        }
        return ensuredSourceIds.get(0);
    }

    /**
     * 映射 lexical 搜索记录。
     *
     * @param resultSet 结果集
     * @param rowNum 行号
     * @return lexical 搜索记录
     * @throws SQLException SQL 异常
     */
    private LexicalSearchRecord mapLexicalSearchRecord(ResultSet resultSet, int rowNum) throws SQLException {
        String filePath = resultSet.getString("file_path");
        return new LexicalSearchRecord(
                readLong(resultSet, "source_id"),
                filePath,
                filePath,
                resultSet.getString("relative_path"),
                resultSet.getString("content_text"),
                resultSet.getString("metadata_json"),
                List.of(filePath),
                null,
                Boolean.valueOf(resultSet.getBoolean("is_verbatim")),
                resultSet.getDouble("score")
        );
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
        Object sourceId = resultSet.getObject("source_id");
        Object sourceSyncRunId = resultSet.getObject("source_sync_run_id");
        return new SourceFileRecord(
                resultSet.getLong("id"),
                sourceId == null ? null : resultSet.getLong("source_id"),
                resultSet.getString("file_path"),
                resultSet.getString("relative_path"),
                sourceSyncRunId == null ? null : resultSet.getLong("source_sync_run_id"),
                resultSet.getString("content_preview"),
                resultSet.getString("format"),
                resultSet.getLong("file_size"),
                resultSet.getString("content_text"),
                resultSet.getString("metadata_json"),
                resultSet.getBoolean("is_verbatim"),
                resultSet.getString("raw_path")
        );
    }

    /**
     * 拼接 token 评分表达式。
     *
     * @param sqlBuilder SQL 构造器
     * @param parameters SQL 参数
     * @param queryTokens 查询 token
     * @param columns 参与匹配的列
     * @param weights 列对应权重
     */
    private void appendTokenScore(
            StringBuilder sqlBuilder,
            List<Object> parameters,
            List<String> queryTokens,
            List<String> columns,
            List<Double> weights
    ) {
        for (String queryToken : queryTokens) {
            String pattern = likePattern(queryToken);
            for (int index = 0; index < columns.size(); index++) {
                sqlBuilder.append(" + case when ")
                        .append(columns.get(index))
                        .append(" like ? then ")
                        .append(weights.get(index).doubleValue())
                        .append(" else 0 end\n");
                parameters.add(pattern);
            }
        }
    }

    /**
     * 拼接 token 过滤条件。
     *
     * @param sqlBuilder SQL 构造器
     * @param parameters SQL 参数
     * @param queryTokens 查询 token
     * @param columns 参与匹配的列
     */
    private void appendTokenWhere(
            StringBuilder sqlBuilder,
            List<Object> parameters,
            List<String> queryTokens,
            List<String> columns
    ) {
        for (String queryToken : queryTokens) {
            String pattern = likePattern(queryToken);
            for (String column : columns) {
                sqlBuilder.append("                  or ").append(column).append(" like ?\n");
                parameters.add(pattern);
            }
        }
    }

    /**
     * 规范化查询 token。
     *
     * @param queryTokens 原始 token
     * @return 规范化 token
     */
    private List<String> normalizeTokens(List<String> queryTokens) {
        if (queryTokens == null || queryTokens.isEmpty()) {
            return List.of();
        }
        List<String> normalizedTokens = new ArrayList<String>();
        for (String queryToken : queryTokens) {
            if (hasText(queryToken)) {
                normalizedTokens.add(queryToken.toLowerCase());
            }
        }
        return normalizedTokens;
    }

    /**
     * 规范化 FTS 配置。
     *
     * @param tsConfig FTS 配置
     * @return 规范化配置
     */
    private String normalizeTsConfig(String tsConfig) {
        return hasText(tsConfig) ? tsConfig.trim() : "simple";
    }

    /**
     * 计算安全返回数量。
     *
     * @param limit 原始数量
     * @return 安全数量
     */
    private int safeLimit(int limit) {
        return limit <= 0 ? 5 : limit;
    }

    /**
     * 构造 LIKE 匹配模式。
     *
     * @param queryToken 查询 token
     * @return LIKE 模式
     */
    private String likePattern(String queryToken) {
        return "%" + queryToken + "%";
    }

    /**
     * 构建路径归一化文本。
     *
     * @param sourceFileRecord 源文件记录
     * @return 路径归一化文本
     */
    private String buildFilePathNorm(SourceFileRecord sourceFileRecord) {
        return String.join(
                " ",
                safeText(sourceFileRecord.getFilePath()),
                safeText(sourceFileRecord.getRelativePath()),
                safeText(sourceFileRecord.getRawPath())
        ).toLowerCase();
    }

    /**
     * 构建源文件检索文本。
     *
     * @param sourceFileRecord 源文件记录
     * @return 检索文本
     */
    private String buildSearchText(SourceFileRecord sourceFileRecord) {
        return String.join(
                " ",
                safeText(sourceFileRecord.getFilePath()),
                safeText(sourceFileRecord.getRelativePath()),
                safeText(sourceFileRecord.getContentPreview()),
                safeText(sourceFileRecord.getContentText()),
                safeText(sourceFileRecord.getMetadataJson())
        ).trim();
    }

    /**
     * 返回非空文本。
     *
     * @param value 原始文本
     * @return 非空文本
     */
    private String safeText(String value) {
        return value == null ? "" : value;
    }

    /**
     * 判断文本是否有值。
     *
     * @param value 文本
     * @return 是否有值
     */
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * 读取可空长整型列。
     *
     * @param resultSet 结果集
     * @param columnName 列名
     * @return 长整型值
     * @throws SQLException SQL 异常
     */
    private Long readLong(ResultSet resultSet, String columnName) throws SQLException {
        Object value = resultSet.getObject(columnName);
        return value == null ? null : resultSet.getLong(columnName);
    }
}
