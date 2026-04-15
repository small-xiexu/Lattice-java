package com.xbk.lattice.query.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JDBC 检索能力探测测试
 *
 * 职责：验证 PostgreSQL 检索增强能力可被真实探测
 *
 * @author xiexu
 */
@SpringBootTest(properties = {
        "spring.profiles.active=jdbc",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge?currentSchema=lattice_b8_search_capability_test_v2",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.flyway.enabled=true",
        "spring.flyway.schemas=lattice_b8_search_capability_test_v2",
        "spring.flyway.default-schema=lattice_b8_search_capability_test_v2",
        "spring.ai.openai.api-key=test-openai-key",
        "spring.ai.anthropic.api-key=test-anthropic-key"
})
class JdbcSearchCapabilityServiceTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JdbcSearchCapabilityService jdbcSearchCapabilityService;

    /**
     * 验证可探测到文本搜索配置、vector 类型与向量索引表。
     */
    @Test
    void shouldDetectTextSearchConfigAndVectorCapabilities() {
        jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
        jdbcTemplate.execute("DROP TEXT SEARCH CONFIGURATION IF EXISTS lattice_jieba_copy");
        jdbcTemplate.execute("CREATE TEXT SEARCH CONFIGURATION lattice_jieba_copy (COPY = simple)");
        jdbcTemplate.execute("DROP TABLE IF EXISTS article_vector_index");
        jdbcTemplate.execute("""
                CREATE TABLE article_vector_index (
                    concept_id VARCHAR(128) PRIMARY KEY,
                    model_name VARCHAR(128) NOT NULL,
                    content_hash VARCHAR(64) NOT NULL,
                    embedding public.vector(1536) NOT NULL,
                    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);

        assertThat(jdbcSearchCapabilityService.supportsTextSearchConfig("lattice_jieba_copy")).isTrue();
        assertThat(jdbcSearchCapabilityService.supportsTextSearchConfig("missing_cfg")).isFalse();
        assertThat(jdbcSearchCapabilityService.supportsVectorType()).isTrue();
        assertThat(jdbcSearchCapabilityService.hasArticleVectorIndex()).isTrue();
    }

    /**
     * 清理测试对象，避免污染其他用例。
     */
    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS article_vector_index");
        jdbcTemplate.execute("DROP TEXT SEARCH CONFIGURATION IF EXISTS lattice_jieba_copy");
    }
}
