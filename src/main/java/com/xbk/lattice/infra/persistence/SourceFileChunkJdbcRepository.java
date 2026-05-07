package com.xbk.lattice.infra.persistence;

import com.xbk.lattice.infra.chunking.SemanticChunker;
import com.xbk.lattice.infra.chunking.TextChunk;
import com.xbk.lattice.infra.persistence.mapper.SourceFileChunkMapper;
import org.springframework.stereotype.Repository;

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
public class SourceFileChunkJdbcRepository {

    private static final int DEFAULT_MAX_CHARS = 3600;

    private static final float DEFAULT_OVERLAP_RATIO = 0.15f;

    private final SourceFileChunkMapper sourceFileChunkMapper;

    private final SemanticChunker semanticChunker;

    /**
     * 创建 SourceFileChunk JDBC 仓储。
     *
     * @param sourceFileChunkMapper 源文件分块 Mapper
     */
    public SourceFileChunkJdbcRepository(SourceFileChunkMapper sourceFileChunkMapper) {
        this.sourceFileChunkMapper = sourceFileChunkMapper;
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
        if (sourceFileChunkMapper == null) {
            return;
        }

        if (sourceFileId == null) {
            sourceFileChunkMapper.deleteByFilePath(filePath);
        }
        else {
            sourceFileChunkMapper.deleteBySourceFileId(sourceFileId);
        }
        for (SourceFileChunkRecord sourceFileChunkRecord : sourceFileChunkRecords) {
            String filePathNorm = safeText(sourceFileChunkRecord.getFilePath()).toLowerCase();
            String searchText = buildSearchText(sourceFileChunkRecord);
            sourceFileChunkMapper.insert(sourceFileChunkRecord, filePathNorm, searchText);
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
        if (sourceFileChunkMapper == null) {
            return 0;
        }

        sourceFileChunkMapper.truncateAll();
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
        if (sourceFileChunkMapper == null) {
            return 0;
        }
        return sourceFileChunkMapper.countAll();
    }

    /**
     * 查询全部源文件分块。
     *
     * @return 分块记录列表
     */
    public List<SourceFileChunkRecord> findAll() {
        if (sourceFileChunkMapper == null) {
            return List.of();
        }
        return sourceFileChunkMapper.findAll();
    }

    /**
     * 按文件路径列表查询源文件分块。
     *
     * @param filePaths 文件路径列表
     * @return 分块记录列表
     */
    public List<SourceFileChunkRecord> findByFilePaths(List<String> filePaths) {
        if (sourceFileChunkMapper == null || filePaths == null || filePaths.isEmpty()) {
            return List.of();
        }
        return sourceFileChunkMapper.findByFilePaths(filePaths);
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
        if (sourceFileChunkMapper == null) {
            return List.of();
        }
        List<String> normalizedTokens = LexicalSearchTokenBudget.normalize(queryTokens);
        if (!hasText(question) && normalizedTokens.isEmpty()) {
            return List.of();
        }
        List<String> likeTokens = LexicalSearchTokenBudget.selectLikeTokens(normalizedTokens);
        List<String> likePatterns = likeTokens.stream()
                .map(this::likePattern)
                .toList();
        List<String> assignmentPatterns = likeTokens.stream()
                .filter(this::shouldScoreStructuredAssignment)
                .map(this::structuredAssignmentPattern)
                .toList();
        return sourceFileChunkMapper.searchLexical(
                normalizeTsConfig(tsConfig),
                question == null ? "" : question,
                likePatterns,
                assignmentPatterns,
                safeLimit(limit)
        );
    }

    /**
     * 查询同一源文件中指定 chunk 的邻近分块。
     *
     * @param filePath 文件路径
     * @param chunkIndex 当前 chunk 序号
     * @param radius 邻近半径
     * @param limit 返回数量
     * @return 邻近分块记录
     */
    public List<LexicalSearchRecord> findNeighborChunks(String filePath, int chunkIndex, int radius, int limit) {
        if (sourceFileChunkMapper == null || !hasText(filePath) || radius <= 0) {
            return List.of();
        }

        int startIndex = Math.max(0, chunkIndex - radius);
        int endIndex = chunkIndex + radius;
        return sourceFileChunkMapper.findNeighborChunks(
                filePath,
                chunkIndex,
                startIndex,
                endIndex,
                safeLimit(limit)
        );
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
        return "%" + escapeLikePattern(queryToken) + "%";
    }

    /**
     * 转义 LIKE 模式中的通配符。
     *
     * @param value 原始值
     * @return 转义后的 LIKE 片段
     */
    private String escapeLikePattern(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    /**
     * 判断 token 是否适合做结构化字段值加权。
     *
     * @param queryToken 查询 token
     * @return 是否适合加权
     */
    private boolean shouldScoreStructuredAssignment(String queryToken) {
        if (!hasText(queryToken)) {
            return false;
        }
        if (queryToken.length() < 2 && !Character.isDigit(queryToken.charAt(0))) {
            return false;
        }
        for (int index = 0; index < queryToken.length(); index++) {
            char ch = queryToken.charAt(index);
            if (!Character.isLetterOrDigit(ch) && ch != '_' && ch != '-' && ch != '.') {
                return false;
            }
        }
        return true;
    }

    /**
     * 构建结构化字段值匹配模式。
     *
     * @param queryToken 查询 token
     * @return LIKE 模式
     */
    private String structuredAssignmentPattern(String queryToken) {
        return "%=" + escapeLikePattern(queryToken) + "%";
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

}
