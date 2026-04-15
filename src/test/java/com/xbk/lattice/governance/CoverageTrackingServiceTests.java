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
 * CoverageTrackingService 测试
 *
 * 职责：验证覆盖率会基于文章引用的 source_paths 汇总已覆盖与未覆盖源文件
 *
 * @author xiexu
 */
class CoverageTrackingServiceTests {

    /**
     * 验证覆盖率会按 source_files 与 articles.source_paths 计算。
     */
    @Test
    void shouldSummarizeCoveredSources() {
        CoverageTrackingService coverageTrackingService = new CoverageTrackingService(
                new FakeSourceFileJdbcRepository(List.of(
                        new SourceFileRecord("payment/a.md", "a", "md", 1),
                        new SourceFileRecord("payment/b.md", "b", "md", 1),
                        new SourceFileRecord("payment/c.md", "c", "md", 1),
                        new SourceFileRecord("payment/d.md", "d", "md", 1)
                )),
                new FakeArticleJdbcRepository(List.of(
                        article("payment-timeout", List.of("payment/a.md", "payment/b.md#Timeout Rules")),
                        article("refund-manual-review", List.of("payment/c.md", "payment/b.md", "payment/missing.md"))
                ))
        );

        CoverageReport report = coverageTrackingService.measure();

        assertThat(report.getTotalSourceFileCount()).isEqualTo(4);
        assertThat(report.getCoveredSourceFileCount()).isEqualTo(3);
        assertThat(report.getUncoveredSourceFileCount()).isEqualTo(1);
        assertThat(report.getCoverageRatio()).isEqualTo(0.75D);
        assertThat(report.getCoveredSourcePaths()).containsExactly("payment/a.md", "payment/b.md", "payment/c.md");
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
