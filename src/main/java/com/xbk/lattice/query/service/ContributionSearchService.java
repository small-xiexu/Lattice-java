package com.xbk.lattice.query.service;

import com.xbk.lattice.infra.persistence.ContributionJdbcRepository;
import com.xbk.lattice.infra.persistence.ContributionRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

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

    /**
     * 创建 Contribution 检索服务。
     *
     * @param contributionJdbcRepository Contribution 仓储
     */
    public ContributionSearchService(ContributionJdbcRepository contributionJdbcRepository) {
        this.contributionJdbcRepository = contributionJdbcRepository;
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

        List<QueryArticleHit> matchedHits = new ArrayList<QueryArticleHit>();
        for (ContributionRecord contributionRecord : contributionJdbcRepository.findAll()) {
            double score = scoreContribution(contributionRecord, queryTokens);
            if (score <= 0) {
                continue;
            }
            matchedHits.add(new QueryArticleHit(
                    QueryEvidenceType.CONTRIBUTION,
                    "contribution:" + contributionRecord.getId(),
                    "用户反馈：" + contributionRecord.getQuestion(),
                    contributionRecord.getAnswer(),
                    buildMetadataJson(contributionRecord),
                    List.of("[用户反馈]"),
                    score
            ));
        }
        matchedHits.sort(Comparator.comparing(QueryArticleHit::getScore).reversed()
                .thenComparing(QueryArticleHit::getConceptId));
        if (matchedHits.size() <= limit) {
            return matchedHits;
        }
        return matchedHits.subList(0, limit);
    }

    /**
     * 计算 Contribution 命中分数。
     *
     * @param contributionRecord Contribution 记录
     * @param queryTokens 查询 token
     * @return 命中分数
     */
    private double scoreContribution(ContributionRecord contributionRecord, List<String> queryTokens) {
        String normalizedQuestion = contributionRecord.getQuestion().toLowerCase(Locale.ROOT);
        String normalizedAnswer = contributionRecord.getAnswer().toLowerCase(Locale.ROOT);
        String normalizedCorrections = contributionRecord.getCorrectionsJson().toLowerCase(Locale.ROOT);
        double score = 0.0D;
        for (String queryToken : queryTokens) {
            if (normalizedQuestion.contains(queryToken)) {
                score += 3.0D;
            }
            if (normalizedAnswer.contains(queryToken)) {
                score += 4.0D;
            }
            if (normalizedCorrections.contains(queryToken)) {
                score += 1.5D;
            }
        }
        return score;
    }

    /**
     * 构建 Contribution 元数据 JSON。
     *
     * @param contributionRecord Contribution 记录
     * @return 元数据 JSON
     */
    private String buildMetadataJson(ContributionRecord contributionRecord) {
        return "{\"question\":\""
                + contributionRecord.getQuestion().replace("\"", "\\\"")
                + "\",\"confirmedBy\":\""
                + contributionRecord.getConfirmedBy()
                + "\"}";
    }
}
