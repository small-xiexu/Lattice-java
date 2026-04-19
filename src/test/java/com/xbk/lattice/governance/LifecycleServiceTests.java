package com.xbk.lattice.governance;

import com.xbk.lattice.governance.domain.LifecycleItem;
import com.xbk.lattice.governance.domain.LifecycleReport;
import com.xbk.lattice.governance.domain.LifecycleTransitionResult;
import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LifecycleService 测试
 *
 * 职责：验证知识文章生命周期汇总与状态切换留痕
 *
 * @author xiexu
 */
class LifecycleServiceTests {

    /**
     * 验证生命周期报告会汇总 active、deprecated、archived 分布。
     */
    @Test
    void shouldSummarizeLifecycleDistribution() {
        LifecycleService lifecycleService = new LifecycleService(
                new FakeMutableArticleJdbcRepository(List.of(
                        article("payment-timeout", "active", "{}"),
                        article(
                                "payment-timeout-v1",
                                "deprecated",
                                "{\"lifecycle\":{\"status\":\"deprecated\",\"reason\":\"由 v2 取代\",\"updatedBy\":\"architect\"}}"
                        ),
                        article(
                                "legacy-refund",
                                "archived",
                                "{\"lifecycle\":{\"status\":\"archived\",\"reason\":\"历史方案归档\",\"updatedBy\":\"ops\"}}"
                        )
                ))
        );

        LifecycleReport report = lifecycleService.report();

        assertThat(report.getTotalArticles()).isEqualTo(3);
        assertThat(report.getActiveCount()).isEqualTo(1);
        assertThat(report.getDeprecatedCount()).isEqualTo(1);
        assertThat(report.getArchivedCount()).isEqualTo(1);
        assertThat(report.getItems())
                .extracting(LifecycleItem::getConceptId, LifecycleItem::getLifecycle, LifecycleItem::getReason)
                .contains(
                        org.assertj.core.groups.Tuple.tuple("payment-timeout-v1", "deprecated", "由 v2 取代"),
                        org.assertj.core.groups.Tuple.tuple("legacy-refund", "archived", "历史方案归档")
                );
    }

    /**
     * 验证 deprecated 操作会更新 lifecycle 并保留元数据中的原有字段。
     */
    @Test
    void shouldDeprecateArticleAndPersistLifecycleMetadata() {
        FakeMutableArticleJdbcRepository articleJdbcRepository = new FakeMutableArticleJdbcRepository(List.of(
                article("payment-timeout", "active", "{\"description\":\"当前重试策略\"}")
        ));
        LifecycleService lifecycleService = new LifecycleService(articleJdbcRepository);

        LifecycleTransitionResult result = lifecycleService.deprecate(
                "payment-timeout",
                "由 payment-timeout-v2 取代",
                "architect"
        );

        ArticleRecord updated = articleJdbcRepository.findByConceptId("payment-timeout").orElseThrow();
        assertThat(result.getLifecycle()).isEqualTo("deprecated");
        assertThat(updated.getLifecycle()).isEqualTo("deprecated");
        assertThat(updated.getMetadataJson()).contains("\"description\":\"当前重试策略\"");
        assertThat(updated.getMetadataJson()).contains("\"status\":\"deprecated\"");
        assertThat(updated.getMetadataJson()).contains("\"reason\":\"由 payment-timeout-v2 取代\"");
        assertThat(updated.getMetadataJson()).contains("\"updatedBy\":\"architect\"");
    }

    /**
     * 验证 archive 与 activate 操作会按顺序切换生命周期状态。
     */
    @Test
    void shouldArchiveThenReactivateArticle() {
        FakeMutableArticleJdbcRepository articleJdbcRepository = new FakeMutableArticleJdbcRepository(List.of(
                article("legacy-refund", "deprecated", "{\"description\":\"退款旧流程\"}")
        ));
        LifecycleService lifecycleService = new LifecycleService(articleJdbcRepository);

        LifecycleTransitionResult archived = lifecycleService.archive(
                "legacy-refund",
                "旧流程停止维护",
                "ops"
        );
        LifecycleTransitionResult reactivated = lifecycleService.activate(
                "legacy-refund",
                "历史问题排查需要恢复可见",
                "ops"
        );

        ArticleRecord updated = articleJdbcRepository.findByConceptId("legacy-refund").orElseThrow();
        assertThat(archived.getLifecycle()).isEqualTo("archived");
        assertThat(reactivated.getLifecycle()).isEqualTo("active");
        assertThat(updated.getLifecycle()).isEqualTo("active");
        assertThat(updated.getMetadataJson()).contains("\"status\":\"active\"");
        assertThat(updated.getMetadataJson()).contains("\"reason\":\"历史问题排查需要恢复可见\"");
    }

    /**
     * 构造最小文章记录。
     *
     * @param conceptId 概念标识
     * @param lifecycle 生命周期
     * @param metadataJson 元数据 JSON
     * @return 文章记录
     */
    private ArticleRecord article(String conceptId, String lifecycle, String metadataJson) {
        return new ArticleRecord(
                conceptId,
                conceptId,
                "# " + conceptId,
                lifecycle,
                OffsetDateTime.now(),
                List.of(conceptId + ".md"),
                metadataJson,
                "summary",
                List.of("keyword"),
                List.of(),
                List.of(),
                "medium",
                "passed"
        );
    }

    private static class FakeMutableArticleJdbcRepository extends ArticleJdbcRepository {

        private final Map<String, ArticleRecord> recordsByConceptId = new LinkedHashMap<String, ArticleRecord>();

        private FakeMutableArticleJdbcRepository(List<ArticleRecord> records) {
            super(new JdbcTemplate());
            for (ArticleRecord record : records) {
                recordsByConceptId.put(record.getConceptId(), record);
            }
        }

        @Override
        public void upsert(ArticleRecord articleRecord) {
            recordsByConceptId.put(articleRecord.getConceptId(), articleRecord);
        }

        @Override
        public Optional<ArticleRecord> findByConceptId(String conceptId) {
            return Optional.ofNullable(recordsByConceptId.get(conceptId));
        }

        @Override
        public List<ArticleRecord> findAll() {
            return List.copyOf(recordsByConceptId.values());
        }
    }
}
