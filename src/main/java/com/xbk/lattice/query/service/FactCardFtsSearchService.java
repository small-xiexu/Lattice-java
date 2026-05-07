package com.xbk.lattice.query.service;

import com.xbk.lattice.infra.persistence.FactCardJdbcRepository;
import com.xbk.lattice.infra.persistence.LexicalSearchRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Fact Card FTS 检索服务
 *
 * 职责：提供 fact_cards 的数据库侧 lexical 召回能力
 *
 * @author xiexu
 */
@Service
public class FactCardFtsSearchService {

    private final FactCardJdbcRepository factCardJdbcRepository;

    private final FtsConfigResolver ftsConfigResolver;

    /**
     * 创建 Fact Card FTS 检索服务。
     *
     * @param factCardJdbcRepository 事实证据卡仓储
     */
    public FactCardFtsSearchService(FactCardJdbcRepository factCardJdbcRepository) {
        this(factCardJdbcRepository, new FtsConfigResolver());
    }

    /**
     * 创建 Fact Card FTS 检索服务。
     *
     * @param factCardJdbcRepository 事实证据卡仓储
     * @param ftsConfigResolver FTS 配置解析器
     */
    @Autowired
    public FactCardFtsSearchService(
            FactCardJdbcRepository factCardJdbcRepository,
            FtsConfigResolver ftsConfigResolver
    ) {
        this.factCardJdbcRepository = factCardJdbcRepository;
        this.ftsConfigResolver = ftsConfigResolver;
    }

    /**
     * 执行 fact card lexical 检索。
     *
     * @param question 查询问题
     * @param limit 返回数量
     * @return 查询命中
     */
    public List<QueryArticleHit> search(String question, int limit) {
        if (factCardJdbcRepository == null) {
            return List.of();
        }
        List<String> queryTokens = QueryTokenExtractor.extract(question);
        if (queryTokens.isEmpty()) {
            return List.of();
        }

        String tsConfig = ftsConfigResolver.resolveArticleTsConfig();
        int safeLimit = safeLimit(limit);
        List<LexicalSearchRecord> records = factCardJdbcRepository.searchLexical(
                question,
                queryTokens,
                safeLimit,
                tsConfig
        );
        List<QueryArticleHit> hits = new ArrayList<QueryArticleHit>();
        for (LexicalSearchRecord record : records) {
            QueryArticleHit hit = toQueryArticleHit(record);
            if (FactCardReviewUsagePolicy.allowsQueryCandidate(hit.getReviewStatus())) {
                hits.add(hit);
            }
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
        double adjustedScore = FactCardReviewUsagePolicy.adjustScore(record.getScore(), record.getReviewStatus());
        return new QueryArticleHit(
                QueryEvidenceType.FACT_CARD,
                record.getSourceId(),
                record.getItemKey(),
                record.getConceptId(),
                record.getTitle(),
                record.getContent(),
                record.getMetadataJson(),
                record.getReviewStatus(),
                record.getSourcePaths(),
                adjustedScore
        );
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
}
