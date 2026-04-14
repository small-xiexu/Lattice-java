package com.xbk.lattice.infra.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

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
                10L
        );

        sourceFileJdbcRepository.upsert(sourceFileRecord);
        Optional<SourceFileRecord> loaded = sourceFileJdbcRepository.findByPath("payment/order.md");

        assertThat(loaded).isPresent();
        assertThat(loaded.orElseThrow().getContentPreview()).isEqualTo("order-flow");
        assertThat(loaded.orElseThrow().getFormat()).isEqualTo("md");
    }
}
