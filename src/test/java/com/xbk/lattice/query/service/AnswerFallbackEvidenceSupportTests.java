package com.xbk.lattice.query.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AnswerFallbackEvidenceSupport 测试
 *
 * 职责：验证 fallback 证据身份键与证据类型优先级
 *
 * @author xiexu
 */
class AnswerFallbackEvidenceSupportTests {

    private final AnswerFallbackEvidenceSupport support =
            new AnswerFallbackEvidenceSupport(new AnswerGenerationService());

    /**
     * 验证精确查值题会在去重键中保留证据类型和细粒度身份。
     */
    @Test
    void shouldKeepEvidenceTypeInExactLookupCanonicalKey() {
        QueryArticleHit articleHit = hit(QueryEvidenceType.ARTICLE, "timeout-summary", "docs/timeout.md");
        QueryArticleHit sourceHit = hit(QueryEvidenceType.SOURCE, "docs/timeout.md#0", "docs/timeout.md");

        String articleKey = support.canonicalKey("timeout_seconds 配置是多少", articleHit);
        String sourceKey = support.canonicalKey("timeout_seconds 配置是多少", sourceHit);

        assertThat(articleKey).contains("ARTICLE");
        assertThat(sourceKey).contains("SOURCE");
        assertThat(articleKey).isNotEqualTo(sourceKey);
    }

    /**
     * 验证证据类型优先级保持贡献、事实卡、文章、原文、图谱的顺序。
     */
    @Test
    void shouldRankEvidenceTypesByDisplayPriority() {
        int contributionPriority = support.priority(hit(QueryEvidenceType.CONTRIBUTION, "contribution-1", "[用户反馈]"));
        int factCardPriority = support.priority(hit(QueryEvidenceType.FACT_CARD, "fact-card-1", "docs/fact.md"));
        int articlePriority = support.priority(hit(QueryEvidenceType.ARTICLE, "article-1", "docs/article.md"));
        int sourcePriority = support.priority(hit(QueryEvidenceType.SOURCE, "docs/source.md#0", "docs/source.md"));
        int graphPriority = support.priority(hit(QueryEvidenceType.GRAPH, "graph-1", "docs/graph.md"));

        assertThat(contributionPriority).isGreaterThan(factCardPriority);
        assertThat(factCardPriority).isGreaterThan(articlePriority);
        assertThat(articlePriority).isGreaterThan(sourcePriority);
        assertThat(sourcePriority).isGreaterThan(graphPriority);
    }

    /**
     * 构造查询命中。
     *
     * @param evidenceType 证据类型
     * @param conceptId 概念标识
     * @param sourcePath 来源路径
     * @return 查询命中
     */
    private QueryArticleHit hit(QueryEvidenceType evidenceType, String conceptId, String sourcePath) {
        return new QueryArticleHit(
                evidenceType,
                conceptId,
                "Evidence",
                "timeout_seconds = 30",
                "{}",
                List.of(sourcePath),
                1.0D
        );
    }
}
