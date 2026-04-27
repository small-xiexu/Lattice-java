package com.xbk.lattice.infra.persistence;

import com.xbk.lattice.infra.chunking.SemanticChunker;
import com.xbk.lattice.infra.chunking.TextChunk;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * SourceFileChunk JDBC 仓储
 *
 * 职责：提供源文件分块的落盘与查询能力
 *
 * @author xiexu
 */
@Repository
@Profile("jdbc")
public class SourceFileChunkJdbcRepository {

    private static final int DEFAULT_MAX_CHARS = 3600;

    private static final float DEFAULT_OVERLAP_RATIO = 0.15f;

    private final JdbcTemplate jdbcTemplate;

    private final SemanticChunker semanticChunker;

    /**
     * 创建 SourceFileChunk JDBC 仓储。
     *
     * @param jdbcTemplate JDBC 模板
     */
    public SourceFileChunkJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.semanticChunker = new SemanticChunker();
    }

    /**
     * 替换指定文件的全部分块。
     *
     * @param filePath 文件路径
     * @param sourceFileChunkRecords 分块记录
     */
    public void replaceChunks(String filePath, List<SourceFileChunkRecord> sourceFileChunkRecords) {
        replaceChunks(null, filePath, sourceFileChunkRecords);
    }

    /**
     * 替换指定文件的全部分块。
     *
     * @param sourceFileId 源文件主键
     * @param filePath 文件路径
     * @param sourceFileChunkRecords 分块记录
     */
    public void replaceChunks(Long sourceFileId, String filePath, List<SourceFileChunkRecord> sourceFileChunkRecords) {
        if (jdbcTemplate == null) {
            return;
        }

        if (sourceFileId == null) {
            jdbcTemplate.update("delete from source_file_chunks where file_path = ?", filePath);
        }
        else {
            jdbcTemplate.update("delete from source_file_chunks where source_file_id = ?", sourceFileId);
        }
        String sql = """
                insert into source_file_chunks (
                    source_file_id, file_path, chunk_index, chunk_text, is_verbatim,
                    file_path_norm, search_tsv
                )
                values (?, ?, ?, ?, ?, ?, to_tsvector('simple'::regconfig, ?))
                """;
        for (SourceFileChunkRecord sourceFileChunkRecord : sourceFileChunkRecords) {
            String filePathNorm = safeText(sourceFileChunkRecord.getFilePath()).toLowerCase();
            String searchText = buildSearchText(sourceFileChunkRecord);
            jdbcTemplate.update(
                    sql,
                    sourceFileChunkRecord.getSourceFileId(),
                    sourceFileChunkRecord.getFilePath(),
                    sourceFileChunkRecord.getChunkIndex(),
                    sourceFileChunkRecord.getChunkText(),
                    sourceFileChunkRecord.isVerbatim(),
                    filePathNorm,
                    searchText
            );
        }
    }

    /**
     * 按原始正文替换语义分块。
     *
     * @param filePath 文件路径
     * @param content 原始正文
     * @param verbatim 是否按原文保留
     */
    public void replaceChunksFromContent(String filePath, String content, boolean verbatim) {
        replaceChunksFromContent(null, filePath, content, verbatim);
    }

    /**
     * 按原始正文替换语义分块。
     *
     * @param sourceFileId 源文件主键
     * @param filePath 文件路径
     * @param content 原始正文
     * @param verbatim 是否按原文保留
     */
    public void replaceChunksFromContent(Long sourceFileId, String filePath, String content, boolean verbatim) {
        List<TextChunk> textChunks = semanticChunker.chunk(content, DEFAULT_MAX_CHARS, DEFAULT_OVERLAP_RATIO);
        List<SourceFileChunkRecord> records = new ArrayList<SourceFileChunkRecord>();
        for (TextChunk textChunk : textChunks) {
            records.add(new SourceFileChunkRecord(
                    sourceFileId,
                    filePath,
                    textChunk.getChunkIndex(),
                    textChunk.getText(),
                    verbatim
            ));
        }
        replaceChunks(sourceFileId, filePath, records);
    }

    /**
     * 按当前源文件正文全量重建全部 source file chunks。
     *
     * @param sourceFileRecords 源文件记录列表
     * @return 重建的源文件数量
     */
    public int rebuildAll(List<SourceFileRecord> sourceFileRecords) {
        if (jdbcTemplate == null) {
            return 0;
        }

        jdbcTemplate.execute("TRUNCATE TABLE source_file_chunks RESTART IDENTITY");
        int rebuiltCount = 0;
        for (SourceFileRecord sourceFileRecord : sourceFileRecords) {
            replaceChunksFromContent(
                    sourceFileRecord.getId(),
                    sourceFileRecord.getFilePath(),
                    sourceFileRecord.getContentText(),
                    sourceFileRecord.isVerbatim()
            );
            rebuiltCount++;
        }
        return rebuiltCount;
    }

    /**
     * 统计全部 source file chunk 数量。
     *
     * @return chunk 数量
     */
    public int countAll() {
        if (jdbcTemplate == null) {
            return 0;
        }

        Integer count = jdbcTemplate.queryForObject("select count(*) from source_file_chunks", Integer.class);
        return count == null ? 0 : count;
    }

    /**
     * 查询全部源文件分块。
     *
     * @return 分块记录列表
     */
    public List<SourceFileChunkRecord> findAll() {
        if (jdbcTemplate == null) {
            return List.of();
        }

        String sql = """
                select source_file_id, file_path, chunk_index, chunk_text, is_verbatim
                from source_file_chunks
                order by file_path, chunk_index
                """;
        return jdbcTemplate.query(sql, this::mapSourceFileChunkRecord);
    }

    /**
     * 按文件路径列表查询源文件分块。
     *
     * @param filePaths 文件路径列表
     * @return 分块记录列表
     */
    public List<SourceFileChunkRecord> findByFilePaths(List<String> filePaths) {
        if (jdbcTemplate == null || filePaths == null || filePaths.isEmpty()) {
            return List.of();
        }

        String placeholders = String.join(", ", java.util.Collections.nCopies(filePaths.size(), "?"));
        String sql = """
                select source_file_id, file_path, chunk_index, chunk_text, is_verbatim
                from source_file_chunks
                where file_path in (%s)
                order by file_path, chunk_index
                """.formatted(placeholders);
        return jdbcTemplate.query(sql, this::mapSourceFileChunkRecord, filePaths.toArray());
    }

    /**
     * 执行 source chunk 数据库侧 lexical 检索。
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
                select sfc.source_file_id,
                       sfc.file_path,
                       sfc.chunk_index,
                       sfc.chunk_text,
                       sfc.is_verbatim,
                       ts_rank_cd(sfc.search_tsv, query.tsq)
                """);
        appendTokenScore(
                sqlBuilder,
                parameters,
                normalizedTokens,
                List.of("sfc.file_path_norm", "lower(sfc.chunk_text)"),
                List.of(Double.valueOf(1.5D), Double.valueOf(3.0D))
        );
        sqlBuilder.append("""
                       as score
                from source_file_chunks sfc
                cross join query
                where sfc.search_tsv @@ query.tsq
                """);
        appendTokenWhere(
                sqlBuilder,
                parameters,
                normalizedTokens,
                List.of("sfc.file_path_norm", "lower(sfc.chunk_text)")
        );
        sqlBuilder.append("""
                order by score desc, sfc.indexed_at desc, sfc.file_path asc, sfc.chunk_index asc
                limit ?
                """);
        parameters.add(Integer.valueOf(safeLimit(limit)));
        return jdbcTemplate.query(sqlBuilder.toString(), this::mapLexicalSearchRecord, parameters.toArray());
    }

    /**
     * 映射单条分块记录。
     *
     * @param resultSet 结果集
     * @param rowNum 行号
     * @return 分块记录
     * @throws SQLException SQL 异常
     */
    private SourceFileChunkRecord mapSourceFileChunkRecord(ResultSet resultSet, int rowNum) throws SQLException {
        return new SourceFileChunkRecord(
                readLong(resultSet, "source_file_id"),
                resultSet.getString("file_path"),
                resultSet.getInt("chunk_index"),
                resultSet.getString("chunk_text"),
                resultSet.getBoolean("is_verbatim")
        );
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
        int chunkIndex = resultSet.getInt("chunk_index");
        return new LexicalSearchRecord(
                null,
                filePath + "#" + chunkIndex,
                filePath,
                filePath,
                resultSet.getString("chunk_text"),
                buildMetadataJson(filePath, chunkIndex, resultSet.getBoolean("is_verbatim")),
                List.of(filePath),
                Integer.valueOf(chunkIndex),
                Boolean.valueOf(resultSet.getBoolean("is_verbatim")),
                resultSet.getDouble("score")
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
     * 构建源文件分块元数据 JSON。
     *
     * @param filePath 文件路径
     * @param chunkIndex 分块序号
     * @param verbatim 是否逐字内容
     * @return 元数据 JSON
     */
    private String buildMetadataJson(String filePath, int chunkIndex, boolean verbatim) {
        return "{\"filePath\":\""
                + filePath.replace("\"", "\\\"")
                + "\",\"chunkIndex\":"
                + chunkIndex
                + ",\"verbatim\":"
                + verbatim
                + "}";
    }

    /**
     * 构建源文件分块检索文本。
     *
     * @param sourceFileChunkRecord 源文件分块
     * @return 检索文本
     */
    private String buildSearchText(SourceFileChunkRecord sourceFileChunkRecord) {
        return String.join(
                " ",
                safeText(sourceFileChunkRecord.getFilePath()),
                safeText(sourceFileChunkRecord.getChunkText())
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
