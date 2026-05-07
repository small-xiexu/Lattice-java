package com.xbk.lattice.infra.persistence;

import com.xbk.lattice.infra.persistence.mapper.SourceFileMapper;
import org.springframework.stereotype.Repository;

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
public class SourceFileJdbcRepository {

    private final SourceFileMapper sourceFileMapper;

    /**
     * 创建 SourceFile JDBC 仓储。
     *
     * @param sourceFileMapper 源文件 Mapper
     */
    public SourceFileJdbcRepository(SourceFileMapper sourceFileMapper) {
        this.sourceFileMapper = sourceFileMapper;
    }

    /**
     * 保存或更新源文件记录。
     *
     * @param sourceFileRecord 源文件记录
     */
    public SourceFileRecord upsert(SourceFileRecord sourceFileRecord) {
        if (sourceFileMapper == null) {
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
        if (sourceFileMapper == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(sourceFileMapper.findByPath(filePath));
    }

    /**
     * 按资料源和相对路径查询源文件记录。
     *
     * @param sourceId 资料源主键
     * @param relativePath 相对路径
     * @return 源文件记录
     */
    public Optional<SourceFileRecord> findBySourceIdAndRelativePath(Long sourceId, String relativePath) {
        if (sourceFileMapper == null || sourceId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(sourceFileMapper.findBySourceIdAndRelativePath(sourceId, relativePath));
    }

    /**
     * 查询全部源文件记录。
     *
     * @return 源文件记录列表
     */
    public List<SourceFileRecord> findAll() {
        if (sourceFileMapper == null) {
            return List.of();
        }
        return sourceFileMapper.findAll();
    }

    /**
     * 查询指定资料源下的全部源文件记录。
     *
     * @param sourceId 资料源主键
     * @return 源文件记录列表
     */
    public List<SourceFileRecord> findBySourceId(Long sourceId) {
        if (sourceFileMapper == null || sourceId == null) {
            return List.of();
        }
        return sourceFileMapper.findBySourceId(sourceId);
    }

    /**
     * 按主键批量查询源文件记录。
     *
     * @param sourceFileIds 源文件主键列表
     * @return 主键到源文件记录的映射
     */
    public Map<Long, SourceFileRecord> findByIds(List<Long> sourceFileIds) {
        Map<Long, SourceFileRecord> sourceFileMap = new LinkedHashMap<Long, SourceFileRecord>();
        if (sourceFileMapper == null || sourceFileIds == null || sourceFileIds.isEmpty()) {
            return sourceFileMap;
        }
        List<SourceFileRecord> sourceFileRecords = sourceFileMapper.findByIds(sourceFileIds);
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
        if (sourceFileMapper == null) {
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
        return sourceFileMapper.searchLexical(
                normalizeTsConfig(tsConfig),
                question == null ? "" : question,
                likePatterns,
                safeLimit(limit)
        );
    }

    private SourceFileRecord upsertSourceAwareRecord(SourceFileRecord sourceFileRecord) {
        String filePathNorm = buildFilePathNorm(sourceFileRecord);
        String searchText = buildSearchText(sourceFileRecord);
        return sourceFileMapper.upsert(sourceFileRecord, filePathNorm, searchText);
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
        Long sourceId = sourceFileMapper.findLegacyDefaultSourceId();
        if (sourceId != null) {
            return sourceId;
        }

        sourceFileMapper.insertLegacyDefaultSource();

        Long ensuredSourceId = sourceFileMapper.findLegacyDefaultSourceId();
        if (ensuredSourceId == null) {
            throw new IllegalStateException("legacy-default knowledge source is missing");
        }
        return ensuredSourceId;
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

}
