package com.xbk.lattice.query.service;

import com.xbk.lattice.infra.persistence.LexicalSearchRecord;
import com.xbk.lattice.infra.persistence.ContributionJdbcRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Contribution 检索服务
 *
 * 职责：提供已确认用户反馈的查询能力
 *
 * @author xiexu
 */
@Service
@Profile("jdbc")
public class ContributionSearchService {

    private final ContributionJdbcRepository contributionJdbcRepository;

    private final FtsConfigResolver ftsConfigResolver;

    /**
     * 创建 Contribution 检索服务。
     *
     * @param contributionJdbcRepository Contribution 仓储
     */
    public ContributionSearchService(ContributionJdbcRepository contributionJdbcRepository) {
        this(contributionJdbcRepository, new FtsConfigResolver());
    }

    /**
     * 创建 Contribution 检索服务。
     *
     * @param contributionJdbcRepository Contribution 仓储
     * @param ftsConfigResolver FTS 配置解析器
     */
    @Autowired
    public ContributionSearchService(
            ContributionJdbcRepository contributionJdbcRepository,
            FtsConfigResolver ftsConfigResolver
    ) {
        this.contributionJdbcRepository = contributionJdbcRepository;
        this.ftsConfigResolver = ftsConfigResolver;
    }

    /**
     * 执行 Contribution 检索。
     *
     * @param question 查询问题
     * @param limit 返回数量
     * @return 命中列表
     */
    public List<QueryArticleHit> search(String question, int limit) {
        if (contributionJdbcRepository == null) {
            return List.of();
        }

        List<String> queryTokens = QueryTokenExtractor.extract(question);
        if (queryTokens.isEmpty()) {
            return List.of();
        }

        String tsConfig = ftsConfigResolver.resolveArticleTsConfig();
        List<LexicalSearchRecord> records = contributionJdbcRepository.searchLexical(question, queryTokens, limit, tsConfig);
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
                QueryEvidenceType.CONTRIBUTION,
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
