package com.xbk.lattice.mcp;

import com.xbk.lattice.api.query.QueryResponse;
import com.xbk.lattice.compiler.service.DocumentSectionSelector;
import com.xbk.lattice.infra.persistence.PendingQueryRecord;
import com.xbk.lattice.infra.persistence.SourceFileJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileRecord;
import com.xbk.lattice.query.service.PendingQueryManager;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LatticeMcpTools doc-read 测试
 *
 * 职责：验证源文件目录与章节读取工具
 *
 * @author xiexu
 */
class LatticeMcpToolsDocReadTests {

    /**
     * 验证 lattice_doc_toc 会返回标题层级与行号。
     */
    @Test
    void docTocShouldReturnHeadingLevelsAndLineNumbers() {
        LatticeMcpTools tools = new LatticeMcpTools(null, new UnsupportedPendingQueryManager());
        tools.setSourceFileJdbcRepository(new FixedSourceFileJdbcRepository(markdownSource()));
        tools.setDocumentSectionSelector(new DocumentSectionSelector());

        String result = tools.docToc("docs/payment-timeout.md");

        assertThat(result).contains("\"path\":\"docs/payment-timeout.md\"");
        assertThat(result).contains("\"heading\":\"Payment Timeout\"");
        assertThat(result).contains("\"level\":1");
        assertThat(result).contains("\"line\":1");
        assertThat(result).contains("\"heading\":\"Timeout Rules\"");
    }

    /**
     * 验证 lattice_doc_read 会返回指定章节正文。
     */
    @Test
    void docReadShouldReturnRequestedSectionContent() {
        LatticeMcpTools tools = new LatticeMcpTools(null, new UnsupportedPendingQueryManager());
        tools.setSourceFileJdbcRepository(new FixedSourceFileJdbcRepository(markdownSource()));
        tools.setDocumentSectionSelector(new DocumentSectionSelector());

        String result = tools.docRead("docs/payment-timeout.md", "Timeout Rules");

        assertThat(result).contains("\"heading\":\"Timeout Rules\"");
        assertThat(result).contains("retry=3");
        assertThat(result).contains("interval=30s");
    }

    private SourceFileRecord markdownSource() {
        return new SourceFileRecord(
                "docs/payment-timeout.md",
                "# Payment Timeout",
                "md",
                120L,
                "# Payment Timeout\n"
                        + "summary\n"
                        + "## Timeout Rules\n"
                        + "- retry=3\n"
                        + "- interval=30s\n"
                        + "## Fallback\n"
                        + "- manual-review\n",
                "{}",
                false,
                "docs/payment-timeout.md"
        );
    }

    private static class FixedSourceFileJdbcRepository extends SourceFileJdbcRepository {

        private final SourceFileRecord record;

        private FixedSourceFileJdbcRepository(SourceFileRecord record) {
            super(new JdbcTemplate());
            this.record = record;
        }

        @Override
        public Optional<SourceFileRecord> findByPath(String filePath) {
            if (record.getFilePath().equals(filePath)) {
                return Optional.of(record);
            }
            return Optional.empty();
        }
    }

    private static class UnsupportedPendingQueryManager implements PendingQueryManager {

        @Override
        public PendingQueryRecord createPendingQuery(String question, QueryResponse queryResponse) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PendingQueryRecord correct(String queryId, String correction) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void confirm(String queryId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void discard(String queryId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PendingQueryRecord findPendingQuery(String queryId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<PendingQueryRecord> listPendingQueries() {
            throw new UnsupportedOperationException();
        }
    }
}
