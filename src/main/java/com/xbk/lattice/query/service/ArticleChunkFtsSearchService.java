package com.xbk.lattice.query.service;

import com.xbk.lattice.infra.persistence.ArticleChunkJdbcRepository;
import com.xbk.lattice.infra.persistence.LexicalSearchRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Article Chunk FTS 检索服务
 *
 * 职责：提供 article_chunks 的数据库侧 lexical 召回能力
 *
 * @author xiexu
 */
@Service
public class ArticleChunkFtsSearchService {

    private final ArticleChunkJdbcRepository articleChunkJdbcRepository;

    private final FtsConfigResolver ftsConfigResolver;

    /**
     * 创建 Article Chunk FTS 检索服务。
     *
     * @param articleChunkJdbcRepository 文章分块仓储
     */
    public ArticleChunkFtsSearchService(ArticleChunkJdbcRepository articleChunkJdbcRepository) {
        this(articleChunkJdbcRepository, new FtsConfigResolver());
    }

    /**
     * 创建 Article Chunk FTS 检索服务。
     *
     * @param articleChunkJdbcRepository 文章分块仓储
     * @param ftsConfigResolver FTS 配置解析器
     */
    @Autowired
    public ArticleChunkFtsSearchService(
            ArticleChunkJdbcRepository articleChunkJdbcRepository,
            FtsConfigResolver ftsConfigResolver
    ) {
        this.articleChunkJdbcRepository = articleChunkJdbcRepository;
        this.ftsConfigResolver = ftsConfigResolver;
    }

    /**
     * 执行 article chunk lexical 检索。
     *
     * @param question 查询问题
     * @param limit 返回数量
     * @return 查询命中
     */
    public List<QueryArticleHit> search(String question, int limit) {
        if (articleChunkJdbcRepository == null) {
            return List.of();
        }
        List<String> queryTokens = QueryTokenExtractor.extract(question);
        if (queryTokens.isEmpty()) {
            return List.of();
        }

        String tsConfig = ftsConfigResolver.resolveArticleTsConfig();
        List<LexicalSearchRecord> records = articleChunkJdbcRepository.searchLexical(
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
                QueryEvidenceType.ARTICLE,
                record.getSourceId(),
                record.getItemKey(),
                record.getConceptId(),
                record.getTitle(),
                record.getContent(),
                enrichMetadata(record),
                record.getReviewStatus(),
                record.getSourcePaths(),
                record.getScore()
        );
    }

    /**
     * 补充 chunk 元数据。
     *
     * @param record lexical 命中记录
     * @return 元数据 JSON
     */
    private String enrichMetadata(LexicalSearchRecord record) {
        return "{\"channel\":\"article_chunk_fts\",\"chunkIndex\":"
                + record.getChunkIndex()
                + ",\"articleMetadata\":"
                + record.getMetadataJson()
                + "}";
    }
}
