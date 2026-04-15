package com.xbk.lattice.governance;

import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.SourceFileJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileRecord;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OmissionTrackingService 测试
 *
 * 职责：验证遗漏清单会列出未被任何文章引用的源文件
 *
 * @author xiexu
 */
class OmissionTrackingServiceTests {

    /**
     * 验证遗漏清单会稳定输出未覆盖源文件列表。
     */
    @Test
    void shouldListOmittedSourceFiles() {
        OmissionTrackingService omissionTrackingService = new OmissionTrackingService(
                new FakeSourceFileJdbcRepository(List.of(
                        new SourceFileRecord("payment/a.md", "a", "md", 1),
                        new SourceFileRecord("payment/b.md", "b", "md", 1),
                        new SourceFileRecord("payment/c.md", "c", "md", 1),
                        new SourceFileRecord("payment/d.md", "d", "md", 1),
                        new SourceFileRecord("payment/e.md", "e", "md", 1)
                )),
                new FakeArticleJdbcRepository(List.of(
                        article("payment-timeout", List.of("payment/a.md", "payment/b.md#Timeout Rules")),
                        article("refund-manual-review", List.of("payment/c.md", "payment/missing.md"))
                ))
        );

        OmissionReport report = omissionTrackingService.track();

        assertThat(report.getTotalSourceFileCount()).isEqualTo(5);
        assertThat(report.getOmittedSourceFileCount()).isEqualTo(2);
        assertThat(report.getItems()).containsExactly("payment/d.md", "payment/e.md");
    }

    /**
     * 构造最小文章记录。
     *
     * @param conceptId 概念标识
     * @param sourcePaths 来源路径
     * @return 文章记录
     */
    private ArticleRecord article(String conceptId, List<String> sourcePaths) {
        return new ArticleRecord(
                conceptId,
                conceptId,
                "# " + conceptId,
                "active",
                OffsetDateTime.now(),
                sourcePaths,
                "{}",
                "summary",
                List.of("keyword"),
                List.of(),
                List.of(),
                "high",
                "passed"
        );
    }

    private static class FakeSourceFileJdbcRepository extends SourceFileJdbcRepository {

        private final List<SourceFileRecord> records;

        private FakeSourceFileJdbcRepository(List<SourceFileRecord> records) {
            super(new JdbcTemplate());
            this.records = records;
        }

        @Override
        public List<SourceFileRecord> findAll() {
            return records;
        }
    }

    private static class FakeArticleJdbcRepository extends ArticleJdbcRepository {

        private final List<ArticleRecord> records;

        private FakeArticleJdbcRepository(List<ArticleRecord> records) {
            super(new JdbcTemplate());
            this.records = records;
        }

        @Override
        public List<ArticleRecord> findAll() {
            return records;
        }
    }
}
