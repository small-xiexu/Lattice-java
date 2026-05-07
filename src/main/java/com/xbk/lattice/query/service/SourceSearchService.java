package com.xbk.lattice.query.service;

import com.xbk.lattice.infra.persistence.LexicalSearchRecord;
import com.xbk.lattice.infra.persistence.SourceFileJdbcRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 源文件检索服务
 *
 * 职责：基于 source_file_chunks 提供源文件层查询能力
 *
 * @author xiexu
 */
@Service
public class SourceSearchService {

    private final SourceFileJdbcRepository sourceFileJdbcRepository;

    private final FtsConfigResolver ftsConfigResolver;

    /**
     * 创建源文件检索服务。
     *
     * @param sourceFileJdbcRepository 源文件仓储
     */
    public SourceSearchService(SourceFileJdbcRepository sourceFileJdbcRepository) {
        this(sourceFileJdbcRepository, new FtsConfigResolver());
    }

    /**
     * 创建源文件检索服务。
     *
     * @param sourceFileJdbcRepository 源文件仓储
     * @param ftsConfigResolver FTS 配置解析器
     */
    @Autowired
    public SourceSearchService(SourceFileJdbcRepository sourceFileJdbcRepository, FtsConfigResolver ftsConfigResolver) {
        this.sourceFileJdbcRepository = sourceFileJdbcRepository;
        this.ftsConfigResolver = ftsConfigResolver;
    }

    /**
     * 执行源文件层检索。
     *
     * @param question 查询问题
     * @param limit 返回数量
     * @return 命中列表
     */
    public List<QueryArticleHit> search(String question, int limit) {
        if (sourceFileJdbcRepository == null) {
            return List.of();
        }

        List<String> queryTokens = QueryTokenExtractor.extract(question);
        if (queryTokens.isEmpty()) {
            return List.of();
        }

        String tsConfig = ftsConfigResolver.resolveArticleTsConfig();
        List<LexicalSearchRecord> records = sourceFileJdbcRepository.searchLexical(
                question,
                queryTokens,
                limit,
                tsConfig
        );
        List<QueryArticleHit> matchedHits = new ArrayList<QueryArticleHit>();
        for (LexicalSearchRecord record : records) {
            matchedHits.add(toQueryArticleHit(record));
        }
        return matchedHits;
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
                null,
                record.getSourcePaths(),
                record.getScore()
        );
    }
}
