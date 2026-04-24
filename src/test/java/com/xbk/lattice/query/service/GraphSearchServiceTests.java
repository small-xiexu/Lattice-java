package com.xbk.lattice.query.service;

import com.xbk.lattice.compiler.ast.domain.AstEntity;
import com.xbk.lattice.compiler.ast.domain.AstEntityType;
import com.xbk.lattice.compiler.ast.domain.AstFact;
import com.xbk.lattice.compiler.ast.domain.AstRelation;
import com.xbk.lattice.infra.persistence.ArticleSourceRefJdbcRepository;
import com.xbk.lattice.infra.persistence.GraphEntityJdbcRepository;
import com.xbk.lattice.infra.persistence.GraphFactJdbcRepository;
import com.xbk.lattice.infra.persistence.GraphRelationJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileRecord;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GraphSearchService 测试
 *
 * 职责：验证 graph channel 会把实体、事实、关系组织成可融合命中
 *
 * @author xiexu
 */
class GraphSearchServiceTests {

    /**
     * 验证图谱检索会返回 GRAPH 类型命中，并附带 facts block 与源码引用。
     */
    @Test
    void shouldReturnGraphHitsWithFactsBlockAndSourceCitation() {
        GraphSearchService graphSearchService = new GraphSearchService(
                new FixedGraphEntityJdbcRepository(),
                new FixedGraphFactJdbcRepository(),
                new FixedGraphRelationJdbcRepository(),
                new FixedArticleSourceRefJdbcRepository(),
                new FixedSourceFileJdbcRepository()
        );

        List<QueryArticleHit> hits = graphSearchService.search("RoutePlanner 调用链", 5);

        assertThat(hits).hasSize(1);
        QueryArticleHit hit = hits.get(0);
        assertThat(hit.getEvidenceType()).isEqualTo(QueryEvidenceType.GRAPH);
        assertThat(hit.getTitle()).contains("RoutePlanner");
        assertThat(hit.getContent()).contains("annotation=@RequestMapping");
        assertThat(hit.getContent()).contains("calls->payment.PaymentService.plan");
        assertThat(hit.getSourcePaths().get(0)).contains("RoutePlanner.java");
        assertThat(hit.getArticleKey()).isEqualTo("payment-routing");
        assertThat(graphSearchService.buildFactsBlock(hits)).contains("图谱实体：payment.routing.RoutePlanner");
    }

    private static class FixedGraphEntityJdbcRepository extends GraphEntityJdbcRepository {

        private FixedGraphEntityJdbcRepository() {
            super(null);
        }

        @Override
        public List<AstEntity> searchByMentions(List<String> mentions, int limit) {
            AstEntity entity = new AstEntity();
            entity.setId("payment-routing#RoutePlanner");
            entity.setCanonicalName("payment.routing.RoutePlanner");
            entity.setSimpleName("RoutePlanner");
            entity.setEntityType(AstEntityType.CLASS);
            entity.setSystemLabel("payment");
            entity.setSourceFileId(101L);
            entity.setAnchorRef("src/main/java/payment/RoutePlanner.java:12-24");
            entity.setResolutionStatus("RESOLVED");
            entity.setMetadataJson("{\"package\":\"payment.routing\"}");
            return List.of(entity);
        }
    }

    private static class FixedGraphFactJdbcRepository extends GraphFactJdbcRepository {

        private FixedGraphFactJdbcRepository() {
            super(null);
        }

        @Override
        public List<AstFact> findActiveFactsByEntityIds(List<String> entityIds, int limit) {
            AstFact fact = new AstFact();
            fact.setEntityId(entityIds.get(0));
            fact.setPredicate("annotation");
            fact.setValue("@RequestMapping");
            fact.setSourceRef("src/main/java/payment/RoutePlanner.java");
            fact.setSourceStartLine(12);
            fact.setSourceEndLine(12);
            fact.setEvidenceExcerpt("@RequestMapping(\"/payments\")");
            fact.setConfidence(0.95D);
            fact.setExtractor("annotation_extractor");
            return List.of(fact);
        }
    }

    private static class FixedGraphRelationJdbcRepository extends GraphRelationJdbcRepository {

        private FixedGraphRelationJdbcRepository() {
            super(null);
        }

        @Override
        public List<AstRelation> findActiveRelationsByEntityIds(List<String> entityIds, int limit) {
            AstRelation relation = new AstRelation();
            relation.setSrcId(entityIds.get(0));
            relation.setEdgeType("calls");
            relation.setDstId("payment.PaymentService.plan");
            relation.setSourceRef("src/main/java/payment/RoutePlanner.java");
            relation.setSourceStartLine(18);
            relation.setSourceEndLine(18);
            relation.setConfidence(0.8D);
            relation.setExtractor("call_extractor");
            return List.of(relation);
        }
    }

    private static class FixedArticleSourceRefJdbcRepository extends ArticleSourceRefJdbcRepository {

        private FixedArticleSourceRefJdbcRepository() {
            super(null);
        }

        @Override
        public Map<Long, List<String>> findArticleKeysBySourceFileIds(List<Long> sourceFileIds) {
            return Map.of(101L, List.of("payment-routing"));
        }
    }

    private static class FixedSourceFileJdbcRepository extends SourceFileJdbcRepository {

        private FixedSourceFileJdbcRepository() {
            super(null);
        }

        @Override
        public Map<Long, SourceFileRecord> findByIds(List<Long> sourceFileIds) {
            Map<Long, SourceFileRecord> sourceFileMap = new LinkedHashMap<Long, SourceFileRecord>();
            sourceFileMap.put(101L, new SourceFileRecord(
                    101L,
                    1L,
                    "src/main/java/payment/RoutePlanner.java",
                    "src/main/java/payment/RoutePlanner.java",
                    null,
                    "RoutePlanner",
                    "JAVA",
                    256L,
                    "class RoutePlanner {}",
                    "{}",
                    false,
                    "src/main/java/payment/RoutePlanner.java"
            ));
            return sourceFileMap;
        }
    }
}
