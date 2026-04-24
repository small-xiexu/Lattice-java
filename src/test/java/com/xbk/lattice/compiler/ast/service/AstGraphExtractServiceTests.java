package com.xbk.lattice.compiler.ast.service;

import com.xbk.lattice.compiler.ast.domain.AstEntity;
import com.xbk.lattice.compiler.ast.domain.AstFact;
import com.xbk.lattice.compiler.ast.domain.AstGraphExtractReport;
import com.xbk.lattice.compiler.ast.domain.AstRelation;
import com.xbk.lattice.compiler.ast.domain.AstSourceFile;
import com.xbk.lattice.infra.persistence.GraphEntityJdbcRepository;
import com.xbk.lattice.infra.persistence.GraphFactJdbcRepository;
import com.xbk.lattice.infra.persistence.GraphRelationJdbcRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AstGraphExtractService 测试
 *
 * 职责：验证 JavaParser 抽取链会产出实体、事实、关系并更新统计报告
 *
 * @author xiexu
 */
class AstGraphExtractServiceTests {

    /**
     * 验证单文件抽取会落出类、方法、注解与调用关系。
     *
     * @throws Exception 临时目录异常
     */
    @Test
    void shouldExtractEntitiesFactsAndRelationsFromJavaSource() throws Exception {
        RecordingGraphEntityJdbcRepository graphEntityJdbcRepository = new RecordingGraphEntityJdbcRepository();
        RecordingGraphFactJdbcRepository graphFactJdbcRepository = new RecordingGraphFactJdbcRepository();
        RecordingGraphRelationJdbcRepository graphRelationJdbcRepository = new RecordingGraphRelationJdbcRepository();
        AstGraphExtractService astGraphExtractService = new AstGraphExtractService(
                graphEntityJdbcRepository,
                graphFactJdbcRepository,
                graphRelationJdbcRepository,
                new NoOpPlatformTransactionManager()
        );
        Path sourceDir = Files.createTempDirectory("ast-graph-test");
        AstSourceFile sourceFile = new AstSourceFile();
        sourceFile.setSourceFileId(11L);
        sourceFile.setRelativePath("src/main/java/payment/RoutePlanner.java");
        sourceFile.setSystemLabel("payment");
        sourceFile.setContent("""
                package payment;

                @Deprecated
                public class RoutePlanner extends BasePlanner implements Planner {

                    @GetMapping("/payments")
                    public String plan() {
                        return helper();
                    }

                    private String helper() {
                        return PaymentService.plan();
                    }
                }
                """);

        AstGraphExtractReport report = astGraphExtractService.extract(sourceDir.toString(), List.of(sourceFile));

        assertThat(report.getEntityUpsertCount()).isGreaterThanOrEqualTo(2);
        assertThat(report.getFactUpsertCount()).isGreaterThanOrEqualTo(3);
        assertThat(report.getRelationUpsertCount()).isGreaterThanOrEqualTo(3);
        assertThat(graphEntityJdbcRepository.entities).extracting(AstEntity::getSimpleName)
                .contains("RoutePlanner", "plan");
        assertThat(graphFactJdbcRepository.facts).extracting(AstFact::getPredicate)
                .contains("package", "annotation", "http_mapping");
        assertThat(graphRelationJdbcRepository.relations).extracting(AstRelation::getEdgeType)
                .contains("extends", "implements", "declares_method", "calls");
    }

    private static class RecordingGraphEntityJdbcRepository extends GraphEntityJdbcRepository {

        private final List<AstEntity> entities = new ArrayList<AstEntity>();

        private RecordingGraphEntityJdbcRepository() {
            super(null);
        }

        @Override
        public void deleteBySourceFileId(Long sourceFileId) {
        }

        @Override
        public void upsert(AstEntity entity) {
            entities.add(entity);
        }
    }

    private static class RecordingGraphFactJdbcRepository extends GraphFactJdbcRepository {

        private final List<AstFact> facts = new ArrayList<AstFact>();

        private RecordingGraphFactJdbcRepository() {
            super(null);
        }

        @Override
        public void deleteBySourceRef(String sourceRef) {
        }

        @Override
        public void insert(AstFact fact) {
            facts.add(fact);
        }
    }

    private static class RecordingGraphRelationJdbcRepository extends GraphRelationJdbcRepository {

        private final List<AstRelation> relations = new ArrayList<AstRelation>();

        private RecordingGraphRelationJdbcRepository() {
            super(null);
        }

        @Override
        public void deleteBySourceRef(String sourceRef) {
        }

        @Override
        public void insert(AstRelation relation) {
            relations.add(relation);
        }
    }

    private static class NoOpPlatformTransactionManager implements PlatformTransactionManager {

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    }
}
