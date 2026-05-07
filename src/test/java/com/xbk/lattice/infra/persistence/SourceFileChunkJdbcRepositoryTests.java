package com.xbk.lattice.infra.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SourceFileChunkJdbcRepository 测试
 *
 * 职责：验证 source_file_chunks 可按源文件正文完整重建
 *
 * @author xiexu
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge?currentSchema=lattice",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.ai.openai.api-key=test-openai-key",
        "spring.ai.anthropic.api-key=test-anthropic-key"
})
class SourceFileChunkJdbcRepositoryTests {

    @Autowired
    private SourceFileJdbcRepository sourceFileJdbcRepository;

    @Autowired
    private SourceFileChunkJdbcRepository sourceFileChunkJdbcRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 验证可基于源文件正文执行完整重建。
     */
    @Test
    void shouldRebuildAllChunksFromSourceFileContent() {
        jdbcTemplate.execute("TRUNCATE TABLE lattice.source_files CASCADE");
        SourceFileRecord sourceFileRecord = new SourceFileRecord(
                "payment/order.md",
                "# Payment",
                "md",
                42L,
                """
                        # Payment Source

                        ## Timeout Rules
                        - retry=3
                        - interval=30s
                        """,
                "{}",
                false,
                "payment/order.md"
        );
        sourceFileJdbcRepository.upsert(sourceFileRecord);
        sourceFileChunkJdbcRepository.replaceChunks(
                "payment/order.md",
                List.of(new SourceFileChunkRecord("payment/order.md", 0, "legacy-source-chunk", false))
        );

        int rebuiltCount = sourceFileChunkJdbcRepository.rebuildAll(sourceFileJdbcRepository.findAll());
        List<SourceFileChunkRecord> chunkRecords = sourceFileChunkJdbcRepository.findByFilePaths(List.of("payment/order.md"));

        assertThat(rebuiltCount).isEqualTo(1);
        assertThat(chunkRecords).hasSize(1);
        assertThat(chunkRecords.get(0).getChunkText()).contains("# Payment Source");
        assertThat(chunkRecords.get(0).getChunkText()).contains("## Timeout Rules");
        assertThat(chunkRecords.get(0).getChunkText()).doesNotContain("legacy-source-chunk");
    }

    /**
     * 验证 source chunk 可通过数据库侧 lexical 查询召回。
     */
    @Test
    void shouldSearchSourceChunksByLexicalIndex() {
        jdbcTemplate.execute("TRUNCATE TABLE lattice.source_file_chunks");
        sourceFileChunkJdbcRepository.replaceChunks(
                "payment/order.md",
                List.of(
                        new SourceFileChunkRecord("payment/order.md", 0, "retry interval is 30s", false),
                        new SourceFileChunkRecord("payment/order.md", 1, "unrelated shipping content", false)
                )
        );

        List<LexicalSearchRecord> hits = sourceFileChunkJdbcRepository.searchLexical(
                "retry interval",
                List.of("retry", "interval"),
                5,
                "simple"
        );

        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).getContent()).contains("retry interval");
        assertThat(hits.get(0).getConceptId()).isEqualTo("payment/order.md");
    }

    /**
     * 验证结构化字段值命中会优先于普通正文命中，避免表格行被相同 case 的步骤正文挤下去。
     */
    @Test
    void shouldPreferStructuredAssignmentMatchWhenSearchingSourceChunks() {
        jdbcTemplate.execute("TRUNCATE TABLE lattice.source_file_chunks");
        sourceFileChunkJdbcRepository.replaceChunks(
                "table-source.xlsx",
                List.of(
                        new SourceFileChunkRecord(
                                "table-source.xlsx",
                                0,
                                "case 100814 raw step text without row fields",
                                true
                        ),
                        new SourceFileChunkRecord(
                                "table-source.xlsx",
                                1,
                                "- sheet=sheet-a; row=3; record_key=100814; name=demo; expected=success",
                                true
                        )
                )
        );

        List<LexicalSearchRecord> hits = sourceFileChunkJdbcRepository.searchLexical(
                "case 100814",
                List.of("case", "100814"),
                5,
                "simple"
        );

        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).getItemKey()).isEqualTo("table-source.xlsx#1");
        assertThat(hits.get(0).getContent()).contains("expected=success");
    }

    /**
     * 验证 LIKE token 预算会优先保留高信号 token，避免长问题里的泛词挤掉精确定位。
     */
    @Test
    void shouldSearchSourceChunksWithBoundedHighSignalLikeTokens() {
        jdbcTemplate.execute("TRUNCATE TABLE lattice.source_file_chunks");
        sourceFileChunkJdbcRepository.replaceChunks(
                "manual.md",
                List.of(
                        new SourceFileChunkRecord("manual.md", 0, "alpha beta gamma", false),
                        new SourceFileChunkRecord("manual.md", 1, "the callback path is /alpha/beta", false)
                )
        );

        List<LexicalSearchRecord> hits = sourceFileChunkJdbcRepository.searchLexical(
                "alpha beta gamma delta epsilon zeta eta theta iota kappa /alpha/beta",
                List.of(
                        "alpha",
                        "beta",
                        "gamma",
                        "delta",
                        "epsilon",
                        "zeta",
                        "eta",
                        "theta",
                        "iota",
                        "kappa",
                        "/alpha/beta"
                ),
                5,
                "simple"
        );

        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).getItemKey()).isEqualTo("manual.md#1");
        assertThat(hits.get(0).getContent()).contains("/alpha/beta");
    }

    /**
     * 验证可按文件路径与 chunk 序号查询邻近分块。
     */
    @Test
    void shouldFindNeighborChunksByFilePathAndIndex() {
        jdbcTemplate.execute("TRUNCATE TABLE lattice.source_file_chunks");
        sourceFileChunkJdbcRepository.replaceChunks(
                "manual.pdf",
                List.of(
                        new SourceFileChunkRecord("manual.pdf", 0, "intro", true),
                        new SourceFileChunkRecord("manual.pdf", 1, "table header", true),
                        new SourceFileChunkRecord("manual.pdf", 2, "table row detail", true),
                        new SourceFileChunkRecord("manual.pdf", 3, "appendix", true)
                )
        );

        List<LexicalSearchRecord> neighbors = sourceFileChunkJdbcRepository.findNeighborChunks(
                "manual.pdf",
                1,
                1,
                5
        );

        assertThat(neighbors).extracting(LexicalSearchRecord::getItemKey)
                .containsExactly("manual.pdf#0", "manual.pdf#2");
        assertThat(neighbors).extracting(LexicalSearchRecord::getContent)
                .containsExactly("intro", "table row detail");
    }
}
