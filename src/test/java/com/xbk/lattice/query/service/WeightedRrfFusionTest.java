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
 * 加权 RRF 融合测试
 *
 * 职责：验证不同 channel 权重会影响最终排序
 *
 * @author xiexu
 */
class WeightedRrfFusionTest {

    /**
     * 验证 chunk 向量权重更高时，会把对应候选提升到前面。
     */
    @Test
    void shouldPromoteCandidateWhenChunkVectorWeightHigher() {
        RrfFusionService rrfFusionService = new RrfFusionService();

        QueryArticleHit articleA = new QueryArticleHit("article-a", "Article A", "A", "{}", List.of("a.md"), 0.9D);
        QueryArticleHit articleB = new QueryArticleHit("article-b", "Article B", "B", "{}", List.of("b.md"), 0.8D);
        QueryArticleHit articleBChunk = new QueryArticleHit("article-b", "Article B", "B chunk", "{}", List.of("b.md"), 0.95D);

        List<QueryArticleHit> fusedHits = rrfFusionService.fuse(
                Map.of(
                        "fts", List.of(articleA, articleB),
                        "chunk_vector", List.of(articleBChunk)
                ),
                Map.of(
                        "fts", 1.0D,
                        "chunk_vector", 8.0D
                ),
                5,
                1
        );

        assertThat(fusedHits).hasSize(2);
        assertThat(fusedHits.get(0).getConceptId()).isEqualTo("article-b");
        assertThat(fusedHits.get(1).getConceptId()).isEqualTo("article-a");
    }

    /**
     * 验证结构化题会保护 Fact Card 与 source chunk，不被高权重 article 背景挤出 topK。
     */
    @Test
    void shouldProtectStructuredEvidenceForCompareSequenceAndStatusShapes() {
        RrfFusionService rrfFusionService = new RrfFusionService();
        for (AnswerShape answerShape : List.of(AnswerShape.COMPARE, AnswerShape.SEQUENCE, AnswerShape.STATUS)) {
            RetrievalStrategy retrievalStrategy = structuredRetrievalStrategy(answerShape);

            List<QueryArticleHit> fusedHits = rrfFusionService.fuse(
                    structuredChannelHits(),
                    retrievalStrategy,
                    3
            );

            assertThat(fusedHits)
                    .as("answerShape=%s", answerShape)
                    .extracting(QueryArticleHit::getEvidenceType)
                    .containsExactly(
                            QueryEvidenceType.FACT_CARD,
                            QueryEvidenceType.FACT_CARD,
                            QueryEvidenceType.SOURCE
                    );
            assertThat(fusedHits)
                    .as("answerShape=%s", answerShape)
                    .extracting(QueryArticleHit::getConceptId)
                    .containsExactlyInAnyOrder(
                            "fact-card-fts-hit",
                            "fact-card-vector-hit",
                            "source-chunk-hit"
                    );
        }
    }

    /**
     * 验证结构化证据保护会优先保留贴近问题的主证据，而不是只按通道保护泛化卡片。
     */
    @Test
    void shouldProtectQuestionRelevantStructuredEvidenceBeforeGenericFactCard() {
        RrfFusionService rrfFusionService = new RrfFusionService();
        Map<String, List<QueryArticleHit>> channelHits = new LinkedHashMap<String, List<QueryArticleHit>>();
        channelHits.put(
                RetrievalStrategyResolver.CHANNEL_FTS,
                List.of(articleHit("article-summary-a"))
        );
        channelHits.put(
                RetrievalStrategyResolver.CHANNEL_FACT_CARD_FTS,
                List.of(
                        factCardHit("generic-card", "valid", "结构化列表条目", "服务影响、接口数量、认证方式等范围说明。"),
                        factCardHit("batch-card", "valid", "灰度批次顺序", "第一批：基础链路；第二批：低流量渠道；第三批：后台能力。")
                )
        );
        channelHits.put(
                RetrievalStrategyResolver.CHANNEL_SOURCE_CHUNK_FTS,
                List.of(sourceHit(
                        "batch-source",
                        "灰度批次安排",
                        "第一批：基础链路；第二批：低流量渠道；第三批：后台能力。"
                ))
        );

        List<QueryArticleHit> fusedHits = rrfFusionService.fuse(
                channelHits,
                structuredRetrievalStrategy(AnswerShape.SEQUENCE, "灰度批次顺序是什么？请按第一批到第三批列出"),
                2
        );

        assertThat(fusedHits)
                .extracting(QueryArticleHit::getConceptId)
                .containsExactlyInAnyOrder("batch-card", "batch-source");
    }

    /**
     * 验证普通问题仍沿用纯 RRF 截断，不对背景 article 做结构化保护。
     */
    @Test
    void shouldKeepPlainRrfOrderingForGeneralShape() {
        RrfFusionService rrfFusionService = new RrfFusionService();

        List<QueryArticleHit> fusedHits = rrfFusionService.fuse(
                structuredChannelHits(),
                structuredRetrievalStrategy(AnswerShape.GENERAL, "整体背景解释"),
                3
        );

        assertThat(fusedHits)
                .extracting(QueryArticleHit::getEvidenceType)
                .containsExactly(
                        QueryEvidenceType.ARTICLE,
                        QueryEvidenceType.ARTICLE,
                            QueryEvidenceType.ARTICLE
                );
    }

    /**
     * 验证普通多焦点查值题也会保留直接 source/fact card 证据。
     */
    @Test
    void shouldProtectDirectEvidenceForGeneralMultiFocusFactQuestion() {
        RrfFusionService rrfFusionService = new RrfFusionService();
        Map<String, List<QueryArticleHit>> channelHits = new LinkedHashMap<String, List<QueryArticleHit>>();
        channelHits.put(
                RetrievalStrategyResolver.CHANNEL_FTS,
                List.of(articleHit("article-summary-a"), articleHit("article-summary-b"))
        );
        channelHits.put(
                RetrievalStrategyResolver.CHANNEL_ARTICLE_VECTOR,
                List.of(articleHit("article-summary-c"))
        );
        channelHits.put(
                RetrievalStrategyResolver.CHANNEL_SOURCE_CHUNK_FTS,
                List.of(sourceHit("source-direct-a", "原文片段 A", "metric alpha 的公式为 base * rate。"))
        );
        channelHits.put(
                RetrievalStrategyResolver.CHANNEL_FACT_CARD_FTS,
                List.of(factCardHit("fact-direct-b", "valid", "结构化事实 B", "metric beta 是否启用：否。"))
        );

        RetrievalStrategy retrievalStrategy = structuredRetrievalStrategy(
                AnswerShape.GENERAL,
                "metric alpha 的公式是什么？metric beta 是否启用？"
        );

        List<QueryArticleHit> fusedHits = rrfFusionService.fuse(channelHits, retrievalStrategy, 3);

        assertThat(fusedHits)
                .extracting(QueryArticleHit::getEvidenceType)
                .contains(QueryEvidenceType.SOURCE, QueryEvidenceType.FACT_CARD);
    }

    /**
     * 验证 needs_human_review 的 fact card 不会被结构化证据保护抬为主证据。
     */
    @Test
    void shouldNotProtectHumanReviewFactCardAsPrimaryStructuredEvidence() {
        RrfFusionService rrfFusionService = new RrfFusionService();
        Map<String, List<QueryArticleHit>> channelHits = new LinkedHashMap<String, List<QueryArticleHit>>();
        channelHits.put(
                RetrievalStrategyResolver.CHANNEL_FTS,
                List.of(articleHit("article-summary-a"), articleHit("article-summary-b"))
        );
        channelHits.put(
                RetrievalStrategyResolver.CHANNEL_FACT_CARD_FTS,
                List.of(factCardHit("fact-card-human-review", "needs_human_review"))
        );

        List<QueryArticleHit> fusedHits = rrfFusionService.fuse(
                channelHits,
                structuredRetrievalStrategy(AnswerShape.ENUM),
                1
        );

        assertThat(fusedHits).hasSize(1);
        assertThat(fusedHits.get(0).getEvidenceType()).isEqualTo(QueryEvidenceType.ARTICLE);
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
                List.of(articleHit("article-summary-a"))
        );
        channelHits.put(
                RetrievalStrategyResolver.CHANNEL_ARTICLE_CHUNK_FTS,
                List.of(articleHit("article-summary-b"))
        );
        channelHits.put(
                RetrievalStrategyResolver.CHANNEL_ARTICLE_VECTOR,
                List.of(articleHit("article-summary-c"))
        );
        channelHits.put(
                RetrievalStrategyResolver.CHANNEL_FACT_CARD_FTS,
                List.of(factCardHit("fact-card-fts-hit"))
        );
        channelHits.put(
                RetrievalStrategyResolver.CHANNEL_FACT_CARD_VECTOR,
                List.of(factCardHit("fact-card-vector-hit"))
        );
        channelHits.put(
                RetrievalStrategyResolver.CHANNEL_SOURCE_CHUNK_FTS,
                List.of(sourceHit("source-chunk-hit"))
        );
        return channelHits;
    }

    /**
     * 构造结构化题融合策略。
     *
     * @param answerShape 答案形态
     * @return 检索策略
     */
    private RetrievalStrategy structuredRetrievalStrategy(AnswerShape answerShape) {
        return structuredRetrievalStrategy(answerShape, "对比各步骤状态");
    }

    /**
     * 构造结构化题融合策略。
     *
     * @param answerShape 答案形态
     * @param retrievalQuestion 有效检索问题
     * @return 检索策略
     */
    private RetrievalStrategy structuredRetrievalStrategy(AnswerShape answerShape, String retrievalQuestion) {
        Map<String, Double> weights = new LinkedHashMap<String, Double>();
        weights.put(RetrievalStrategyResolver.CHANNEL_FTS, 20.0D);
        weights.put(RetrievalStrategyResolver.CHANNEL_ARTICLE_CHUNK_FTS, 20.0D);
        weights.put(RetrievalStrategyResolver.CHANNEL_ARTICLE_VECTOR, 20.0D);
        weights.put(RetrievalStrategyResolver.CHANNEL_FACT_CARD_FTS, 1.0D);
        weights.put(RetrievalStrategyResolver.CHANNEL_FACT_CARD_VECTOR, 1.0D);
        weights.put(RetrievalStrategyResolver.CHANNEL_SOURCE_CHUNK_FTS, 1.0D);
        Set<String> enabledChannels = new LinkedHashSet<String>(weights.keySet());
        return new RetrievalStrategy(
                retrievalQuestion,
                QueryIntent.GENERAL,
                answerShape,
                true,
                1,
                weights,
                enabledChannels
        );
    }

    /**
     * 构造 article 命中。
     *
     * @param conceptId 概念标识
     * @return article 命中
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
        return factCardHit(conceptId, "valid");
    }

    /**
     * 构造指定审查状态的 Fact Card 命中。
     *
     * @param conceptId 概念标识
     * @param reviewStatus 审查状态
     * @return Fact Card 命中
     */
    private QueryArticleHit factCardHit(String conceptId, String reviewStatus) {
        return factCardHit(conceptId, reviewStatus, "结构化证据卡", "对比各步骤状态的结构化事实内容");
    }

    /**
     * 构造指定审查状态和内容的 Fact Card 命中。
     *
     * @param conceptId 概念标识
     * @param reviewStatus 审查状态
     * @param title 标题
     * @param content 内容
     * @return Fact Card 命中
     */
    private QueryArticleHit factCardHit(String conceptId, String reviewStatus, String title, String content) {
        return new QueryArticleHit(
                QueryEvidenceType.FACT_CARD,
                conceptId,
                title,
                content,
                "{}",
                reviewStatus,
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
        return sourceHit(conceptId, "原文片段", "对比各步骤状态的原文精确证据内容");
    }

    /**
     * 构造 source chunk 命中。
     *
     * @param conceptId 概念标识
     * @param title 标题
     * @param content 内容
     * @return source chunk 命中
     */
    private QueryArticleHit sourceHit(String conceptId, String title, String content) {
        return new QueryArticleHit(
                QueryEvidenceType.SOURCE,
                conceptId,
                title,
                content,
                "{}",
                List.of("source.md"),
                1.0D
        );
    }
}
