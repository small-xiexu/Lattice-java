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
     * 验证 POLICY 形态的结构化 guardrail 也会保留多样性，而不是被单一 article 占满。
     */
    @Test
    void shouldApplyDiversityInPolicyStructuredGuardrail() {
        RrfFusionService rrfFusionService = new RrfFusionService();
        Map<String, List<QueryArticleHit>> channelHits = new LinkedHashMap<String, List<QueryArticleHit>>();
        channelHits.put(
                RetrievalStrategyResolver.CHANNEL_ARTICLE_VECTOR,
                List.of(
                        articleChunkHit("policy-article", "配置上限说明 / 背景 1", 1),
                        articleChunkHit("policy-article", "配置上限说明 / 背景 2", 2),
                        articleChunkHit("policy-article", "配置上限说明 / 背景 3", 3),
                        articleChunkHit("policy-article", "配置上限说明 / 背景 4", 4)
                )
        );
        channelHits.put(
                RetrievalStrategyResolver.CHANNEL_FACT_CARD_FTS,
                List.of(factCardHit(
                        "policy-limit-card",
                        "valid",
                        "配置上限说明",
                        "最大配置上限为 60，超过后不再继续增加。"
                ))
        );
        channelHits.put(
                RetrievalStrategyResolver.CHANNEL_SOURCE_CHUNK_FTS,
                List.of(sourceHit(
                        "policy-limit-source",
                        "application.yml",
                        "limit.max-days: 60"
                ))
        );

        List<QueryArticleHit> fusedHits = rrfFusionService.fuse(
                channelHits,
                structuredRetrievalStrategy(AnswerShape.POLICY, "配置上限是多少？"),
                4
        );

        assertThat(fusedHits).hasSize(4);
        assertThat(fusedHits)
                .extracting(QueryArticleHit::getEvidenceType)
                .contains(QueryEvidenceType.FACT_CARD, QueryEvidenceType.SOURCE);
        assertThat(fusedHits)
                .extracting(QueryArticleHit::getConceptId)
                .contains("policy-limit-card", "policy-limit-source");
        assertThat(fusedHits)
                .extracting(QueryArticleHit::getArticleKey)
                .filteredOn(articleKey -> "policy-article".equals(articleKey))
                .hasSizeLessThanOrEqualTo(2);
    }

    /**
     * 验证普通问题中 vector 通道文章作为主证据与 fact_card/source 平等竞争。
     *
     * <p>article_vector / chunk_vector 现纳入主证据白名单，在 GENERAL 形态下
     * 若触发 hasRelevantDirectEvidence，vector 文章应凭高 relevance 排到 tier-0 前列。</p>
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
                .contains(
                        QueryEvidenceType.ARTICLE
                );
        assertThat(fusedHits.get(0).getEvidenceType())
                .isEqualTo(QueryEvidenceType.ARTICLE);
    }

    /**
     * 验证 chunk 级命中使用 chunk identity，不会与同一 article 的整篇命中折叠。
     */
    @Test
    void shouldKeepArticleChunkHitSeparateFromArticleHit() {
        RrfFusionService rrfFusionService = new RrfFusionService();
        QueryArticleHit articleHit = new QueryArticleHit(
                QueryEvidenceType.ARTICLE,
                1L,
                "article-alpha",
                "concept-alpha",
                "Article Alpha",
                "Article background",
                "{}",
                "passed",
                List.of("alpha.md"),
                1.0D
        );
        QueryArticleHit chunkHit = new QueryArticleHit(
                QueryEvidenceType.ARTICLE,
                1L,
                "article-alpha",
                "concept-alpha",
                "Article Alpha / Deployment Plan",
                "## Deployment Plan\nStep A",
                "{\"chunkIdentity\":\"ARTICLE_CHUNK:article-alpha#2\",\"chunkIndex\":2}",
                "passed",
                List.of("alpha.md"),
                1.0D
        );

        List<QueryArticleHit> fusedHits = rrfFusionService.fuse(
                Map.of(
                        RetrievalStrategyResolver.CHANNEL_FTS,
                        List.of(articleHit),
                        RetrievalStrategyResolver.CHANNEL_ARTICLE_CHUNK_FTS,
                        List.of(chunkHit)
                ),
                Map.of(
                        RetrievalStrategyResolver.CHANNEL_FTS,
                        1.0D,
                        RetrievalStrategyResolver.CHANNEL_ARTICLE_CHUNK_FTS,
                        1.0D
                ),
                5,
                60
        );

        assertThat(fusedHits).hasSize(2);
        assertThat(fusedHits)
                .extracting(QueryArticleHit::getTitle)
                .contains("Article Alpha", "Article Alpha / Deployment Plan");
    }

    /**
     * 验证没有 chunk identity 的普通 article 命中仍按 articleKey 融合。
     */
    @Test
    void shouldStillMergePlainArticleHitsByArticleKey() {
        RrfFusionService rrfFusionService = new RrfFusionService();
        QueryArticleHit articleHit = new QueryArticleHit(
                QueryEvidenceType.ARTICLE,
                1L,
                "article-alpha",
                "concept-alpha",
                "Article Alpha",
                "Article background",
                "{}",
                "passed",
                List.of("alpha.md"),
                1.0D
        );
        QueryArticleHit articleVectorHit = new QueryArticleHit(
                QueryEvidenceType.ARTICLE,
                1L,
                "article-alpha",
                "concept-alpha",
                "Article Alpha",
                "Vector background",
                "{}",
                "passed",
                List.of("alpha.md"),
                1.0D
        );

        List<QueryArticleHit> fusedHits = rrfFusionService.fuse(
                Map.of(
                        RetrievalStrategyResolver.CHANNEL_FTS,
                        List.of(articleHit),
                        RetrievalStrategyResolver.CHANNEL_ARTICLE_VECTOR,
                        List.of(articleVectorHit)
                ),
                Map.of(
                        RetrievalStrategyResolver.CHANNEL_FTS,
                        1.0D,
                        RetrievalStrategyResolver.CHANNEL_ARTICLE_VECTOR,
                        1.0D
                ),
                5,
                60
        );

        assertThat(fusedHits).hasSize(1);
        assertThat(fusedHits.get(0).getArticleKey()).isEqualTo("article-alpha");
    }

    /**
     * 验证同一 fact card 下的两个 terminal unit 不会被 RRF 折叠。
     */
    @Test
    void shouldKeepSiblingTerminalUnitsSeparateByUnitIdentity() {
        RrfFusionService rrfFusionService = new RrfFusionService();

        List<QueryArticleHit> fusedHits = rrfFusionService.fuse(
                Map.of(
                        RetrievalStrategyResolver.CHANNEL_FACT_CARD_TERMINAL_FTS,
                        List.of(
                                terminalUnitHit("terminal-unit:alpha", "alpha_limit = 31"),
                                terminalUnitHit("terminal-unit:beta", "beta_mode = enabled")
                        )
                ),
                Map.of(RetrievalStrategyResolver.CHANNEL_FACT_CARD_TERMINAL_FTS, 1.0D),
                5,
                60
        );

        assertThat(fusedHits).hasSize(2);
        assertThat(fusedHits)
                .extracting(QueryArticleHit::getArticleKey)
                .containsExactlyInAnyOrder("fc:shared-card", "fc:shared-card");
        assertThat(fusedHits)
                .extracting(QueryArticleHit::getContent)
                .containsExactlyInAnyOrder("alpha_limit = 31", "beta_mode = enabled");
    }

    /**
     * 验证 terminalUnitIdentity 优先于 articleKey 作为 RRF hit identity。
     */
    @Test
    void shouldPreferTerminalUnitIdentityOverFactCardKey() {
        RrfFusionService rrfFusionService = new RrfFusionService();

        List<QueryArticleHit> fusedHits = rrfFusionService.fuse(
                Map.of(
                        RetrievalStrategyResolver.CHANNEL_FACT_CARD_TERMINAL_FTS,
                        List.of(terminalUnitHit("terminal-unit:shared", "first")),
                        RetrievalStrategyResolver.CHANNEL_FACT_CARD_FTS,
                        List.of(terminalUnitHit("terminal-unit:shared", "second"))
                ),
                Map.of(
                        RetrievalStrategyResolver.CHANNEL_FACT_CARD_TERMINAL_FTS, 1.0D,
                        RetrievalStrategyResolver.CHANNEL_FACT_CARD_FTS, 1.0D
                ),
                5,
                60
        );

        assertThat(fusedHits).hasSize(1);
        assertThat(fusedHits.get(0).getArticleKey()).isEqualTo("fc:shared-card");
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
        weights.put(RetrievalStrategyResolver.CHANNEL_FACT_CARD_TERMINAL_FTS, 1.0D);
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
     * 构造 terminal unit 命中。
     *
     * @param terminalUnitIdentity terminal unit 身份
     * @param content 内容
     * @return Fact Card terminal unit 命中
     */
    private QueryArticleHit terminalUnitHit(String terminalUnitIdentity, String content) {
        return new QueryArticleHit(
                QueryEvidenceType.FACT_CARD,
                12L,
                "fc:shared-card",
                "fc:shared-card",
                "Synthetic Terminal Unit",
                content,
                "{\"terminalUnitIdentity\":\"" + terminalUnitIdentity
                        + "\",\"unitId\":\"" + terminalUnitIdentity
                        + "\",\"cardId\":\"fc:shared-card\"}",
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

    /**
     * 构造带 articleKey 与 chunkIdentity 的 article chunk 命中。
     *
     * @param articleKey article 身份
     * @param title 标题
     * @param chunkIndex chunk 序号
     * @return article chunk 命中
     */
    private QueryArticleHit articleChunkHit(String articleKey, String title, int chunkIndex) {
        String metadataJson = "{\"chunkIdentity\":\"ARTICLE_CHUNK:"
                + articleKey + "#" + chunkIndex
                + "\",\"chunkIndex\":" + chunkIndex + "}";
        return new QueryArticleHit(
                QueryEvidenceType.ARTICLE,
                null,
                articleKey,
                "concept-" + articleKey,
                title,
                "Content of " + title,
                metadataJson,
                null,
                List.of("source-" + articleKey + ".md"),
                0.0D
        );
    }

    /**
     * 验证同一 article 的多个 chunk 不会占满全部 topK，为其他来源留出位置。
     */
    @Test
    void shouldLimitSameArticleChunksInTopK() {
        RrfFusionService rrfFusionService = new RrfFusionService();

        List<QueryArticleHit> hits = List.of(
                articleChunkHit("article-a", "A Chunk 1", 1),
                articleChunkHit("article-a", "A Chunk 2", 2),
                articleChunkHit("article-a", "A Chunk 3", 3),
                articleChunkHit("article-a", "A Chunk 4", 4),
                articleChunkHit("article-a", "A Chunk 5", 5),
                articleChunkHit("article-b", "B Chunk 1", 1),
                articleChunkHit("article-b", "B Chunk 2", 2),
                articleChunkHit("article-c", "C Chunk 1", 1)
        );

        List<QueryArticleHit> fused = rrfFusionService.fuse(
                Map.of("fts", hits),
                Map.of("fts", 1.0),
                5,
                60
        );

        assertThat(fused).hasSize(5);
        long countA = fused.stream().filter(h -> "article-a".equals(h.getArticleKey())).count();
        long countB = fused.stream().filter(h -> "article-b".equals(h.getArticleKey())).count();
        long countC = fused.stream().filter(h -> "article-c".equals(h.getArticleKey())).count();

        assertThat(countA)
                .as("article-a should not monopolize topK")
                .isLessThanOrEqualTo(2);
        assertThat(countB)
                .as("article-b should enter topK")
                .isGreaterThan(0);
        assertThat(countC)
                .as("article-c should enter topK")
                .isGreaterThan(0);
    }

    /**
     * 验证 limit 较小时仍按分数优先，且不丢失结果。
     */
    @Test
    void shouldKeepScoreOrderWithSmallLimit() {
        RrfFusionService rrfFusionService = new RrfFusionService();

        List<QueryArticleHit> hits = List.of(
                articleChunkHit("article-a", "A Chunk 1", 1),
                articleChunkHit("article-a", "A Chunk 2", 2),
                articleChunkHit("article-a", "A Chunk 3", 3),
                articleChunkHit("article-b", "B Chunk 1", 1)
        );

        List<QueryArticleHit> fused = rrfFusionService.fuse(
                Map.of("fts", hits),
                Map.of("fts", 1.0),
                2,
                60
        );

        assertThat(fused).hasSize(2);
        long countA = fused.stream().filter(h -> "article-a".equals(h.getArticleKey())).count();
        assertThat(countA)
                .as("small limit should still allow same-article hits up to diversity cap")
                .isEqualTo(2);
        assertThat(fused.get(0).getTitle()).isEqualTo("A Chunk 1");
        assertThat(fused.get(1).getTitle()).isEqualTo("A Chunk 2");
    }

    /**
     * 验证不同 article 的命中不会被错误合并到同一 diversity group。
     */
    @Test
    void shouldNotMergeDifferentArticleGroups() {
        RrfFusionService rrfFusionService = new RrfFusionService();

        List<QueryArticleHit> hits = List.of(
                articleChunkHit("article-a", "A Chunk 1", 1),
                articleChunkHit("article-a", "A Chunk 2", 2),
                articleChunkHit("article-a", "A Chunk 3", 3),
                articleChunkHit("article-b", "B Chunk 1", 1),
                articleChunkHit("article-b", "B Chunk 2", 2),
                articleChunkHit("article-b", "B Chunk 3", 3)
        );

        List<QueryArticleHit> fused = rrfFusionService.fuse(
                Map.of("fts", hits),
                Map.of("fts", 1.0),
                4,
                60
        );

        assertThat(fused).hasSize(4);
        long countA = fused.stream().filter(h -> "article-a".equals(h.getArticleKey())).count();
        long countB = fused.stream().filter(h -> "article-b".equals(h.getArticleKey())).count();
        assertThat(countA)
                .as("different articles should each get their own diversity quota")
                .isEqualTo(2);
        assertThat(countB)
                .as("different articles should each get their own diversity quota")
                .isEqualTo(2);
    }

    /**
     * 验证不足 limit 时回填剩余高分命中。
     */
    @Test
    void shouldBackfillWhenNotEnoughDiversityHits() {
        RrfFusionService rrfFusionService = new RrfFusionService();

        List<QueryArticleHit> hits = List.of(
                articleChunkHit("article-a", "A Chunk 1", 1),
                articleChunkHit("article-a", "A Chunk 2", 2),
                articleChunkHit("article-a", "A Chunk 3", 3),
                articleChunkHit("article-a", "A Chunk 4", 4),
                articleChunkHit("article-b", "B Chunk 1", 1)
        );

        List<QueryArticleHit> fused = rrfFusionService.fuse(
                Map.of("fts", hits),
                Map.of("fts", 1.0),
                5,
                60
        );

        assertThat(fused).hasSize(5);
        long countA = fused.stream().filter(h -> "article-a".equals(h.getArticleKey())).count();
        long countB = fused.stream().filter(h -> "article-b".equals(h.getArticleKey())).count();
        assertThat(countA)
                .as("after diversity cap, should backfill remaining article-a hits")
                .isGreaterThan(2);
        assertThat(countB)
                .as("article-b should still be present")
                .isEqualTo(1);
    }
}
