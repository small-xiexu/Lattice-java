package com.xbk.lattice.infra.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ContributionJdbcRepository 测试
 *
 * 职责：验证 contribution 数据库侧 lexical 检索能力
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
class ContributionJdbcRepositoryTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ContributionJdbcRepository contributionJdbcRepository;

    /**
     * 验证 contribution 可通过数据库侧 lexical 查询召回。
     */
    @Test
    void shouldSearchContributionsByLexicalIndex() {
        jdbcTemplate.execute("TRUNCATE TABLE lattice.contributions");
        contributionJdbcRepository.save(new ContributionRecord(
                UUID.randomUUID(),
                "retry=3 是什么配置",
                "用户确认的运维口径：retry interval is 30s",
                "[]",
                "system",
                OffsetDateTime.now()
        ));

        List<LexicalSearchRecord> hits = contributionJdbcRepository.searchLexical(
                "retry interval",
                List.of("retry", "interval"),
                5,
                "simple"
        );

        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).getTitle()).contains("retry=3");
        assertThat(hits.get(0).getContent()).contains("30s");
    }
}
