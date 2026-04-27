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
        "spring.profiles.active=jdbc",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge?currentSchema=lattice_b1_source_chunk_test",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.flyway.enabled=true",
        "spring.flyway.schemas=lattice_b1_source_chunk_test",
        "spring.flyway.default-schema=lattice_b1_source_chunk_test",
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
        jdbcTemplate.execute("TRUNCATE TABLE lattice_b1_source_chunk_test.source_files CASCADE");
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
        jdbcTemplate.execute("TRUNCATE TABLE lattice_b1_source_chunk_test.source_file_chunks");
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
}
