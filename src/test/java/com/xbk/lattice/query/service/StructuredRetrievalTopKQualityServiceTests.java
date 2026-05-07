package com.xbk.lattice.query.service;

import com.xbk.lattice.query.evidence.domain.AnswerShape;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 结构化检索 topK 质量服务测试
 *
 * 职责：验证目标证据进入 topK 的可复算指标
 *
 * @author xiexu
 */
class StructuredRetrievalTopKQualityServiceTests {

    private final StructuredRetrievalTopKQualityService qualityService = new StructuredRetrievalTopKQualityService();

    /**
     * 验证结构化融合结果中的目标证据可通过 topK 门槛。
     */
    @Test
    void shouldPassTopKMetricGateWhenStructuredEvidenceIsProtected() {
        RrfFusionService rrfFusionService = new RrfFusionService();
        List<QueryArticleHit> fusedHits = rrfFusionService.fuse(
                structuredChannelHits(),
                structuredRetrievalStrategy(),
                3
        );
        StructuredRetrievalTopKSample sample = new StructuredRetrievalTopKSample(
                "sample-structured-topk-pass",
                List.of(
                        StructuredRetrievalTopKTarget.forConceptId(QueryEvidenceType.FACT_CARD, "fact-card-primary"),
                        StructuredRetrievalTopKTarget.forConceptId(QueryEvidenceType.FACT_CARD, "fact-card-secondary"),
                        StructuredRetrievalTopKTarget.forConceptId(QueryEvidenceType.SOURCE, "source-primary")
                ),
                fusedHits
        );

        StructuredRetrievalTopKReport report = qualityService.measure(List.of(sample));

        assertThat(report.getSampleCount()).isEqualTo(1);
        assertThat(report.getTargetCount()).isEqualTo(3);
        assertThat(report.getTargetHitRate()).isEqualTo(1.0D);
        assertThat(report.getFullyCoveredSampleRate()).isEqualTo(1.0D);
        assertThat(report.passesGate(0.80D)).isTrue();
    }

    /**
     * 验证目标证据缺失时不能通过 topK 门槛。
     */
    @Test
    void shouldFailTopKMetricGateWhenTargetsAreMissing() {
        StructuredRetrievalTopKSample sample = new StructuredRetrievalTopKSample(
                "sample-structured-topk-fail",
                List.of(
                        StructuredRetrievalTopKTarget.forConceptId(QueryEvidenceType.FACT_CARD, "fact-card-primary"),
                        StructuredRetrievalTopKTarget.forConceptId(QueryEvidenceType.SOURCE, "source-primary")
                ),
                List.of(factCardHit("fact-card-primary"), articleHit("background-primary"))
        );

        StructuredRetrievalTopKReport report = qualityService.measure(List.of(sample));

        assertThat(report.getTargetCount()).isEqualTo(2);
        assertThat(report.getTargetHitCount()).isEqualTo(1);
        assertThat(report.getTargetHitRate()).isEqualTo(0.5D);
        assertThat(report.getFullyCoveredSampleRate()).isEqualTo(0.0D);
        assertThat(report.passesGate(0.80D)).isFalse();
    }

    /**
     * 验证目标证据既可按 articleKey 匹配，也可按 conceptId 匹配。
     */
    @Test
    void shouldMatchTargetByArticleKeyOrConceptId() {
        QueryArticleHit sourceHit = new QueryArticleHit(
                QueryEvidenceType.SOURCE,
                1L,
                "source-key-primary",
                "source-concept-primary",
                "原文片段",
                "结构化证据内容",
                "{}",
                List.of("source.md"),
                1.0D
        );
        StructuredRetrievalTopKSample sample = new StructuredRetrievalTopKSample(
                "sample-structured-topk-identity",
                List.of(
                        StructuredRetrievalTopKTarget.forArticleKey(QueryEvidenceType.SOURCE, "source-key-primary"),
                        StructuredRetrievalTopKTarget.forConceptId(QueryEvidenceType.FACT_CARD, "fact-card-primary")
                ),
                List.of(sourceHit, factCardHit("fact-card-primary"))
        );

        StructuredRetrievalTopKReport report = qualityService.measure(List.of(sample));

        assertThat(report.getTargetCount()).isEqualTo(2);
        assertThat(report.getTargetHitCount()).isEqualTo(2);
        assertThat(report.passesGate(0.80D)).isTrue();
    }

    /**
     * 构造结构化题融合测试的通道命中。
     *
     * @return 通道命中
     */
    private Map<String, List<QueryArticleHit>> structuredChannelHits() {
        Map<String, List<QueryArticleHit>> channelHits = new LinkedHashMap<String, List<QueryArticleHit>>();
        channelHits.put(
                RetrievalStrategyResolver.CHANNEL_FTS,
                List.of(articleHit("background-primary"))
        );
        channelHits.put(
                RetrievalStrategyResolver.CHANNEL_ARTICLE_CHUNK_FTS,
                List.of(articleHit("background-secondary"))
        );
        channelHits.put(
                RetrievalStrategyResolver.CHANNEL_ARTICLE_VECTOR,
                List.of(articleHit("background-tertiary"))
        );
        channelHits.put(
                RetrievalStrategyResolver.CHANNEL_FACT_CARD_FTS,
                List.of(factCardHit("fact-card-primary"))
        );
        channelHits.put(
                RetrievalStrategyResolver.CHANNEL_FACT_CARD_VECTOR,
                List.of(factCardHit("fact-card-secondary"))
        );
        channelHits.put(
                RetrievalStrategyResolver.CHANNEL_SOURCE_CHUNK_FTS,
                List.of(sourceHit("source-primary"))
        );
        return channelHits;
    }

    /**
     * 构造结构化检索策略。
     *
     * @return 检索策略
     */
    private RetrievalStrategy structuredRetrievalStrategy() {
        Map<String, Double> weights = new LinkedHashMap<String, Double>();
        weights.put(RetrievalStrategyResolver.CHANNEL_FTS, 20.0D);
        weights.put(RetrievalStrategyResolver.CHANNEL_ARTICLE_CHUNK_FTS, 20.0D);
        weights.put(RetrievalStrategyResolver.CHANNEL_ARTICLE_VECTOR, 20.0D);
        weights.put(RetrievalStrategyResolver.CHANNEL_FACT_CARD_FTS, 1.0D);
        weights.put(RetrievalStrategyResolver.CHANNEL_FACT_CARD_VECTOR, 1.0D);
        weights.put(RetrievalStrategyResolver.CHANNEL_SOURCE_CHUNK_FTS, 1.0D);
        Set<String> enabledChannels = new LinkedHashSet<String>(weights.keySet());
        return new RetrievalStrategy(
                "对比 alpha 与 beta 的状态",
                QueryIntent.GENERAL,
                AnswerShape.COMPARE,
                true,
                1,
                weights,
                enabledChannels
        );
    }

    /**
     * 构造背景文章命中。
     *
     * @param conceptId 概念标识
     * @return 背景文章命中
     */
    private QueryArticleHit articleHit(String conceptId) {
        return new QueryArticleHit(
                QueryEvidenceType.ARTICLE,
                conceptId,
                "背景文章",
                "背景解释内容",
                "{}",
                List.of("background.md"),
                1.0D
        );
    }

    /**
     * 构造 Fact Card 命中。
     *
     * @param conceptId 概念标识
     * @return Fact Card 命中
     */
    private QueryArticleHit factCardHit(String conceptId) {
        return new QueryArticleHit(
                QueryEvidenceType.FACT_CARD,
                conceptId,
                "结构化证据卡",
                "alpha 与 beta 的结构化事实内容",
                "{}",
                "valid",
                List.of("source.md"),
                1.0D
        );
    }

    /**
     * 构造 source chunk 命中。
     *
     * @param conceptId 概念标识
     * @return source chunk 命中
     */
    private QueryArticleHit sourceHit(String conceptId) {
        return new QueryArticleHit(
                QueryEvidenceType.SOURCE,
                conceptId,
                "原文片段",
                "alpha 与 beta 的原文精确证据内容",
                "{}",
                List.of("source.md"),
                1.0D
        );
    }
}
