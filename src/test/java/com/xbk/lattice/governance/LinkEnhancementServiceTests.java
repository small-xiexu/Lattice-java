package com.xbk.lattice.governance;

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
 * LinkEnhancementService 测试
 *
 * 职责：验证 broken wiki-links 修复与关系区块同步
 *
 * @author xiexu
 */
class LinkEnhancementServiceTests {

    /**
     * 验证预览模式会给出修复建议，但不会回写文章内容。
     */
    @Test
    void shouldPreviewEnhancementsWithoutPersistingChanges() {
        FakeMutableArticleJdbcRepository articleJdbcRepository = new FakeMutableArticleJdbcRepository(seedArticles());
        LinkEnhancementService linkEnhancementService = new LinkEnhancementService(articleJdbcRepository);

        LinkEnhancementReport report = linkEnhancementService.enhance(false);

        ArticleRecord unchanged = articleJdbcRepository.findByConceptId("refund-manual-review").orElseThrow();
        assertThat(report.getProcessedArticleCount()).isEqualTo(4);
        assertThat(report.getUpdatedArticleCount()).isEqualTo(1);
        assertThat(report.getFixedLinkCount()).isEqualTo(1);
        assertThat(report.getSyncedSectionCount()).isEqualTo(2);
        assertThat(report.getUnresolvedLinkCount()).isEqualTo(1);
        assertThat(report.getItems())
                .extracting(LinkEnhancementItem::getConceptId, LinkEnhancementItem::getFixedLinkCount)
                .contains(org.assertj.core.groups.Tuple.tuple("refund-manual-review", 1));
        assertThat(unchanged.getContent()).doesNotContain("lattice:auto-related:start");
        assertThat(unchanged.getContent()).contains("[[Payment Timeout]]");
    }

    /**
     * 验证执行模式会修复标题型 wiki-link，并同步 managed relation 区块。
     */
    @Test
    void shouldPersistFixedLinksAndManagedRelationBlocks() {
        FakeMutableArticleJdbcRepository articleJdbcRepository = new FakeMutableArticleJdbcRepository(seedArticles());
        LinkEnhancementService linkEnhancementService = new LinkEnhancementService(articleJdbcRepository);

        LinkEnhancementReport report = linkEnhancementService.enhance(true);

        ArticleRecord updated = articleJdbcRepository.findByConceptId("refund-manual-review").orElseThrow();
        assertThat(report.getUpdatedArticleCount()).isEqualTo(1);
        assertThat(updated.getContent()).contains("[[payment-timeout|Payment Timeout]]");
        assertThat(updated.getContent()).contains("<!-- lattice:auto-depends-on:start -->");
        assertThat(updated.getContent()).contains("- [[payment-config|Payment Config]]");
        assertThat(updated.getContent()).contains("<!-- lattice:auto-related:start -->");
        assertThat(updated.getContent()).contains("- [[retry-policy|Retry Policy]]");
        assertThat(updated.getContent()).contains("[[Unknown Legacy Doc]]");
    }

    private List<ArticleRecord> seedArticles() {
        return List.of(
                article("payment-timeout", "Payment Timeout", List.of(), List.of(), "# Payment Timeout"),
                article("payment-config", "Payment Config", List.of(), List.of(), "# Payment Config"),
                article("retry-policy", "Retry Policy", List.of(), List.of(), "# Retry Policy"),
                article(
                        "refund-manual-review",
                        "Refund Manual Review",
                        List.of("payment-config"),
                        List.of("retry-policy"),
                        "# Refund Manual Review\nSee [[Payment Timeout]] and [[Unknown Legacy Doc]]"
                )
        );
    }

    /**
     * 构造最小文章记录。
     *
     * @param conceptId 概念标识
     * @param title 标题
     * @param dependsOn depends_on
     * @param related related
     * @param content 正文
     * @return 文章记录
     */
    private ArticleRecord article(
            String conceptId,
            String title,
            List<String> dependsOn,
            List<String> related,
            String content
    ) {
        return new ArticleRecord(
                conceptId,
                title,
                content,
                "active",
                OffsetDateTime.now(),
                List.of(conceptId + ".md"),
                "{}",
                "summary",
                List.of("keyword"),
                dependsOn,
                related,
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
