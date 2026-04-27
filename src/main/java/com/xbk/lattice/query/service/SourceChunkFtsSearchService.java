package com.xbk.lattice.query.service;

import com.xbk.lattice.infra.persistence.LexicalSearchRecord;
import com.xbk.lattice.infra.persistence.SourceFileChunkJdbcRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

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
                limit,
                tsConfig
        );
        List<QueryArticleHit> hits = new ArrayList<QueryArticleHit>();
        for (LexicalSearchRecord record : records) {
            hits.add(toQueryArticleHit(record));
        }
        return hits;
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
