package com.xbk.lattice.query.graph;

import com.xbk.lattice.query.citation.CitationExtractor;
import com.xbk.lattice.query.service.AnswerGenerationService;
import com.xbk.lattice.query.service.AnswerShapeClassifier;
import com.xbk.lattice.query.service.ArticleChunkFtsSearchService;
import com.xbk.lattice.query.service.ChunkVectorSearchService;
import com.xbk.lattice.query.service.ContributionSearchService;
import com.xbk.lattice.query.service.FactCardFtsSearchService;
import com.xbk.lattice.query.service.FactCardTerminalUnitFtsSearchService;
import com.xbk.lattice.query.service.FactCardVectorSearchService;
import com.xbk.lattice.query.service.FtsSearchService;
import com.xbk.lattice.query.service.GraphSearchService;
import com.xbk.lattice.query.service.QueryArticleHit;
import com.xbk.lattice.query.service.QueryEvidenceType;
import com.xbk.lattice.query.service.QueryIntentClassifier;
import com.xbk.lattice.query.service.QueryRetrievalSettingsService;
import com.xbk.lattice.query.service.QueryRewriteService;
import com.xbk.lattice.query.service.QuerySearchProperties;
import com.xbk.lattice.query.service.RefKeySearchService;
import com.xbk.lattice.query.service.RetrievalChannel;
import com.xbk.lattice.query.service.RetrievalDispatchPlan;
import com.xbk.lattice.query.service.RetrievalStrategyResolver;
import com.xbk.lattice.query.service.ReviewerAgent;
import com.xbk.lattice.query.service.RrfFusionService;
import com.xbk.lattice.query.service.SourceChunkFtsSearchService;
import com.xbk.lattice.query.service.SourceSearchService;
import com.xbk.lattice.query.service.VectorSearchService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QueryGraphDefinitionBaseSupport 测试
 *
 * 验证 dispatch plan 包含 terminal unit FTS channel，以及 state save/load 正确。
 *
 * @author xiexu
 */
class QueryGraphDefinitionBaseSupportTest {

    private static QueryGraphDefinitionBaseSupport createSupport() {
        InMemoryQueryWorkingSetStore workingSetStore = new InMemoryQueryWorkingSetStore();
        return new QueryGraphDefinitionBaseSupport(
                new FtsSearchService(null),
                new ArticleChunkFtsSearchService(null),
                new RefKeySearchService(null),
                new SourceSearchService(null),
                new SourceChunkFtsSearchService(null),
                new FactCardFtsSearchService(null),
                new FactCardVectorSearchService(),
                new FactCardTerminalUnitFtsSearchService(null),
                new ContributionSearchService(null),
                new GraphSearchService(),
                new VectorSearchService(),
                new ChunkVectorSearchService(),
                new RrfFusionService(),
                new QueryRetrievalSettingsService(),
                new QuerySearchProperties(),
                new QueryRewriteService(),
                new QueryIntentClassifier(),
                new AnswerShapeClassifier(),
                new RetrievalStrategyResolver(),
                null,
                new AnswerGenerationService(),
                null,
                new ReviewerAgent(null, null),
                workingSetStore,
                null,
                null,
                new QueryGraphStateMapper(),
                new QueryGraphConditions(null),
                new QueryAnswerProjectionBuilder(new CitationExtractor())
        ) {
        };
    }

    /**
     * 验证 dispatch plan 包含 fact_card_terminal_fts channel。
     */
    @Test
    void shouldIncludeFactCardTerminalFtsChannelInDispatchPlan() {
        QueryGraphDefinitionBaseSupport support = createSupport();
        RetrievalDispatchPlan plan = support.buildDispatchPlan(null);

        List<String> channelNames = plan.getChannels().stream()
                .map(RetrievalChannel::getChannelName)
                .toList();

        assertThat(channelNames)
                .contains(RetrievalStrategyResolver.CHANNEL_FACT_CARD_TERMINAL_FTS);
    }

    /**
     * 验证 terminal unit channel 在 fact_card 分组中，排在 fact_card_fts 之后。
     */
    @Test
    void shouldPlaceTerminalUnitFtsAfterFactCardFts() {
        QueryGraphDefinitionBaseSupport support = createSupport();
        RetrievalDispatchPlan plan = support.buildDispatchPlan(null);

        int factCardIndex = indexOf(plan, RetrievalStrategyResolver.CHANNEL_FACT_CARD_FTS);
        int terminalIndex = indexOf(plan, RetrievalStrategyResolver.CHANNEL_FACT_CARD_TERMINAL_FTS);

        assertThat(factCardIndex).isGreaterThanOrEqualTo(0);
        assertThat(terminalIndex).isGreaterThanOrEqualTo(0);
        assertThat(terminalIndex).isEqualTo(factCardIndex + 1);
    }

    /**
     * 验证 terminal unit channel 映射到正确的 state key。
     */
    @Test
    void shouldMapTerminalUnitHitsToCorrectStateKey() {
        QueryGraphDefinitionBaseSupport support = createSupport();
        QueryGraphState state = new QueryGraphState();
        state.setQueryId("tu-query-1");

        List<QueryArticleHit> hits = List.of(new QueryArticleHit(
                QueryEvidenceType.FACT_CARD,
                100L,
                "terminal-unit:test:1",
                "terminal-unit:test:1",
                "test-title",
                "test-content",
                "{\"unitId\":\"test:1\"}",
                "approved",
                List.of("test/path.json"),
                8.5D
        ));

        Map<String, Object> delta = support.saveDispatchedChannelHits(
                state,
                RetrievalStrategyResolver.CHANNEL_FACT_CARD_TERMINAL_FTS,
                hits
        );

        assertThat(delta).containsKey(QueryGraphStateKeys.FACT_CARD_TERMINAL_UNIT_HITS_REF);
    }

    private static int indexOf(RetrievalDispatchPlan plan, String channelName) {
        List<RetrievalChannel> channels = plan.getChannels();
        for (int i = 0; i < channels.size(); i++) {
            if (channelName.equals(channels.get(i).getChannelName())) {
                return i;
            }
        }
        return -1;
    }
}
