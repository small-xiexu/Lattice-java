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
        if (jdbcTemplate == null) {
            return;
        }

        jdbcTemplate.update("delete from source_file_chunks where file_path = ?", filePath);
        String sql = """
                insert into source_file_chunks (file_path, chunk_index, chunk_text, is_verbatim)
                values (?, ?, ?, ?)
                """;
        for (SourceFileChunkRecord sourceFileChunkRecord : sourceFileChunkRecords) {
            jdbcTemplate.update(
                    sql,
                    sourceFileChunkRecord.getFilePath(),
                    sourceFileChunkRecord.getChunkIndex(),
                    sourceFileChunkRecord.getChunkText(),
                    sourceFileChunkRecord.isVerbatim()
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
        List<TextChunk> textChunks = semanticChunker.chunk(content, DEFAULT_MAX_CHARS, DEFAULT_OVERLAP_RATIO);
        List<SourceFileChunkRecord> records = new ArrayList<SourceFileChunkRecord>();
        for (TextChunk textChunk : textChunks) {
            records.add(new SourceFileChunkRecord(
                    filePath,
                    textChunk.chunkIndex(),
                    textChunk.text(),
                    verbatim
            ));
        }
        replaceChunks(filePath, records);
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
                select file_path, chunk_index, chunk_text, is_verbatim
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
                select file_path, chunk_index, chunk_text, is_verbatim
                from source_file_chunks
                where file_path in (%s)
                order by file_path, chunk_index
                """.formatted(placeholders);
        return jdbcTemplate.query(sql, this::mapSourceFileChunkRecord, filePaths.toArray());
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
                resultSet.getString("file_path"),
                resultSet.getInt("chunk_index"),
                resultSet.getString("chunk_text"),
                resultSet.getBoolean("is_verbatim")
        );
    }
}
