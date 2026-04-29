package com.xbk.lattice.query.service;

import com.xbk.lattice.infra.persistence.LexicalSearchRecord;
import com.xbk.lattice.infra.persistence.SourceFileChunkJdbcRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Source Chunk FTS 检索服务
 *
 * 职责：提供 source_file_chunks 的数据库侧 lexical 召回能力
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class SourceChunkFtsSearchService {

    private static final int NEIGHBOR_CHUNK_RADIUS = 1;

    private static final int MAX_EXTRA_CONTEXT_CHUNKS = 5;

    private final SourceFileChunkJdbcRepository sourceFileChunkJdbcRepository;

    private final FtsConfigResolver ftsConfigResolver;

    /**
     * 创建 Source Chunk FTS 检索服务。
     *
     * @param sourceFileChunkJdbcRepository 源文件分块仓储
     */
    public SourceChunkFtsSearchService(SourceFileChunkJdbcRepository sourceFileChunkJdbcRepository) {
        this(sourceFileChunkJdbcRepository, new FtsConfigResolver());
    }

    /**
     * 创建 Source Chunk FTS 检索服务。
     *
     * @param sourceFileChunkJdbcRepository 源文件分块仓储
     * @param ftsConfigResolver FTS 配置解析器
     */
    @Autowired
    public SourceChunkFtsSearchService(
            SourceFileChunkJdbcRepository sourceFileChunkJdbcRepository,
            FtsConfigResolver ftsConfigResolver
    ) {
        this.sourceFileChunkJdbcRepository = sourceFileChunkJdbcRepository;
        this.ftsConfigResolver = ftsConfigResolver;
    }

    /**
     * 执行 source chunk lexical 检索。
     *
     * @param question 查询问题
     * @param limit 返回数量
     * @return 查询命中
     */
    public List<QueryArticleHit> search(String question, int limit) {
        if (sourceFileChunkJdbcRepository == null) {
            return List.of();
        }
        List<String> queryTokens = QueryTokenExtractor.extract(question);
        if (queryTokens.isEmpty()) {
            return List.of();
        }

        String tsConfig = ftsConfigResolver.resolveArticleTsConfig();
        List<LexicalSearchRecord> records = sourceFileChunkJdbcRepository.searchLexical(
                question,
                queryTokens,
                safeLimit(limit),
                tsConfig
        );
        List<LexicalSearchRecord> expandedRecords = expandNeighborRecords(records, safeLimit(limit));
        List<QueryArticleHit> hits = new ArrayList<QueryArticleHit>();
        for (LexicalSearchRecord record : expandedRecords) {
            hits.add(toQueryArticleHit(record));
        }
        return hits;
    }

    /**
     * 为 source chunk 命中补充邻近 chunk，避免表格或长段落跨 chunk 时证据被截断。
     *
     * @param records 原始命中
     * @param requestedLimit 原始请求数量
     * @return 补充上下文后的命中
     */
    private List<LexicalSearchRecord> expandNeighborRecords(List<LexicalSearchRecord> records, int requestedLimit) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        int expandedLimit = expandedLimit(requestedLimit);
        List<LexicalSearchRecord> expandedRecords = new ArrayList<LexicalSearchRecord>();
        Set<String> seenKeys = new LinkedHashSet<String>();
        for (LexicalSearchRecord record : records) {
            addDistinctRecord(expandedRecords, seenKeys, record);
        }
        for (LexicalSearchRecord record : records) {
            if (expandedRecords.size() >= expandedLimit) {
                break;
            }
            String filePath = resolveFilePath(record);
            Integer chunkIndex = record.getChunkIndex();
            if (filePath.isBlank() || chunkIndex == null) {
                continue;
            }
            int remainingLimit = expandedLimit - expandedRecords.size();
            List<LexicalSearchRecord> neighborRecords = sourceFileChunkJdbcRepository.findNeighborChunks(
                    filePath,
                    chunkIndex.intValue(),
                    NEIGHBOR_CHUNK_RADIUS,
                    remainingLimit
            );
            for (LexicalSearchRecord neighborRecord : neighborRecords) {
                addDistinctRecord(expandedRecords, seenKeys, neighborRecord);
                if (expandedRecords.size() >= expandedLimit) {
                    break;
                }
            }
        }
        return expandedRecords;
    }

    /**
     * 计算补充上下文后的安全上限。
     *
     * @param requestedLimit 原始请求数量
     * @return 扩展上限
     */
    private int expandedLimit(int requestedLimit) {
        int safeRequestedLimit = safeLimit(requestedLimit);
        int doubleLimit = safeRequestedLimit * 2;
        int cappedLimit = safeRequestedLimit + MAX_EXTRA_CONTEXT_CHUNKS;
        return Math.max(safeRequestedLimit, Math.min(doubleLimit, cappedLimit));
    }

    /**
     * 去重追加检索记录。
     *
     * @param records 目标记录
     * @param seenKeys 已见记录键
     * @param record 待追加记录
     */
    private void addDistinctRecord(
            List<LexicalSearchRecord> records,
            Set<String> seenKeys,
            LexicalSearchRecord record
    ) {
        if (record == null) {
            return;
        }
        String recordKey = recordKey(record);
        if (seenKeys.add(recordKey)) {
            records.add(record);
        }
    }

    /**
     * 构建记录去重键。
     *
     * @param record 检索记录
     * @return 去重键
     */
    private String recordKey(LexicalSearchRecord record) {
        if (record.getItemKey() != null && !record.getItemKey().isBlank()) {
            return record.getItemKey();
        }
        String filePath = resolveFilePath(record);
        Integer chunkIndex = record.getChunkIndex();
        return filePath + "#" + (chunkIndex == null ? "" : chunkIndex);
    }

    /**
     * 解析源文件路径。
     *
     * @param record 检索记录
     * @return 文件路径
     */
    private String resolveFilePath(LexicalSearchRecord record) {
        if (record.getConceptId() != null && !record.getConceptId().isBlank()) {
            return record.getConceptId();
        }
        if (record.getSourcePaths() != null && !record.getSourcePaths().isEmpty()) {
            String sourcePath = record.getSourcePaths().get(0);
            return sourcePath == null ? "" : sourcePath;
        }
        return "";
    }

    /**
     * 返回安全检索数量。
     *
     * @param limit 原始数量
     * @return 安全数量
     */
    private int safeLimit(int limit) {
        return limit <= 0 ? 5 : limit;
    }

    /**
     * 转换为查询命中。
     *
     * @param record lexical 命中记录
     * @return 查询命中
     */
    private QueryArticleHit toQueryArticleHit(LexicalSearchRecord record) {
        return new QueryArticleHit(
                QueryEvidenceType.SOURCE,
                record.getSourceId(),
                record.getItemKey(),
                record.getConceptId(),
                record.getTitle(),
                record.getContent(),
                record.getMetadataJson(),
                record.getSourcePaths(),
                record.getScore()
        );
    }
}
