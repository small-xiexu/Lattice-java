package com.xbk.lattice.infra.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SourceFileJdbcRepository 测试
 *
 * 职责：验证 source_files 表和最小源文件落盘能力
 *
 * @author xiexu
 */
@SpringBootTest(properties = {
        "spring.profiles.active=jdbc",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge?currentSchema=lattice_b1_source_test",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.flyway.enabled=true",
        "spring.flyway.schemas=lattice_b1_source_test",
        "spring.flyway.default-schema=lattice_b1_source_test",
        "spring.ai.openai.api-key=test-openai-key",
        "spring.ai.anthropic.api-key=test-anthropic-key"
})
class SourceFileJdbcRepositoryTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SourceFileJdbcRepository sourceFileJdbcRepository;

    /**
     * 验证 Flyway 已创建 source_files 表。
     */
    @Test
    void shouldCreateSourceFilesTableByFlyway() {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'lattice_b1_source_test' and table_name = 'source_files'",
                Integer.class
        );

        assertThat(count).isEqualTo(1);
    }

    /**
     * 验证源文件记录可保存并按路径查询。
     */
    @Test
    void shouldSaveAndLoadSourceFileRecord() {
        SourceFileRecord sourceFileRecord = new SourceFileRecord(
                "payment/order.md",
                "order-flow",
                "md",
                10L,
                "order-flow\nretry=3",
                "{\"pageCount\":1}",
                true,
                "payment/order.md"
        );

        sourceFileJdbcRepository.upsert(sourceFileRecord);
        Optional<SourceFileRecord> loaded = sourceFileJdbcRepository.findByPath("payment/order.md");

        assertThat(loaded).isPresent();
        assertThat(loaded.orElseThrow().getContentPreview()).isEqualTo("order-flow");
        assertThat(loaded.orElseThrow().getContentText()).isEqualTo("order-flow\nretry=3");
        assertThat(loaded.orElseThrow().getMetadataJson()).contains("pageCount");
        assertThat(loaded.orElseThrow().isVerbatim()).isTrue();
        assertThat(loaded.orElseThrow().getRawPath()).isEqualTo("payment/order.md");
        assertThat(loaded.orElseThrow().getFormat()).isEqualTo("md");
    }

    /**
     * 验证 source_files 表已扩展为可承载全文、元数据与原始文件路径。
     */
    @Test
    void shouldExtendSourceFilesTableForFullTextStorage() {
        List<String> columnNames = jdbcTemplate.queryForList(
                """
                        select column_name
                        from information_schema.columns
                        where table_schema = 'lattice_b1_source_test'
                          and table_name = 'source_files'
                        order by ordinal_position
                        """,
                String.class
        );

        assertThat(columnNames)
                .contains("content_text")
                .contains("metadata_json")
                .contains("is_verbatim")
                .contains("raw_path")
                .contains("file_path_norm")
                .contains("search_tsv");
    }

    /**
     * 验证 source file 可通过数据库侧 lexical 查询召回。
     */
    @Test
    void shouldSearchSourceFilesByLexicalIndex() {
        jdbcTemplate.execute("TRUNCATE TABLE lattice_b1_source_test.source_files CASCADE");
        sourceFileJdbcRepository.upsert(new SourceFileRecord(
                "payment/order.md",
                "# Payment",
                "md",
                42L,
                "retry interval is 30s",
                "{}",
                false,
                "payment/order.md"
        ));

        List<LexicalSearchRecord> hits = sourceFileJdbcRepository.searchLexical(
                "retry interval",
                List.of("payment", "retry", "interval"),
                5,
                "simple"
        );

        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).getItemKey()).isEqualTo("payment/order.md");
        assertThat(hits.get(0).getContent()).contains("retry interval is 30s");
    }
}
