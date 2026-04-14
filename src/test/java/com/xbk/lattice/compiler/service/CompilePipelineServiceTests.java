package com.xbk.lattice.compiler.service;

import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CompilePipelineService 测试
 *
 * 职责：验证最小编译链路可将源目录内容编译并落入 articles 表
 *
 * @author xiexu
 */
@SpringBootTest(properties = {
        "spring.profiles.active=jdbc",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge?currentSchema=lattice_b1_compile_test",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.flyway.enabled=true",
        "spring.flyway.schemas=lattice_b1_compile_test",
        "spring.flyway.default-schema=lattice_b1_compile_test",
        "spring.ai.openai.api-key=test-openai-key",
        "spring.ai.anthropic.api-key=test-anthropic-key",
        "lattice.compiler.ingest-max-chars=100",
        "lattice.compiler.batch-max-chars=10"
})
class CompilePipelineServiceTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CompilePipelineService compilePipelineService;

    @Autowired
    private ArticleJdbcRepository articleJdbcRepository;

    /**
     * 验证源目录可按 groupKey 编译成 article 并落表。
     *
     * @param tempDir 临时目录
     * @throws IOException IO 异常
     */
    @Test
    void shouldCompileSourcesIntoArticlesAndPersistThem(@TempDir Path tempDir) throws IOException {
        jdbcTemplate.execute("TRUNCATE TABLE lattice_b1_compile_test.articles CASCADE");

        Path paymentDir = Files.createDirectories(tempDir.resolve("payment"));
        Path fulfillmentDir = Files.createDirectories(tempDir.resolve("fulfillment"));
        Files.writeString(paymentDir.resolve("order.md"), "order-flow", StandardCharsets.UTF_8);
        Files.writeString(paymentDir.resolve("refund.md"), "refund-flow", StandardCharsets.UTF_8);
        Files.writeString(fulfillmentDir.resolve("fc.md"), "fc-routing", StandardCharsets.UTF_8);

        CompileResult compileResult = compilePipelineService.compile(tempDir);

        assertThat(compileResult.getPersistedCount()).isEqualTo(2);
        Integer sourceFileCount = jdbcTemplate.queryForObject(
                "select count(*) from lattice_b1_compile_test.source_files",
                Integer.class
        );
        Integer articleChunkCount = jdbcTemplate.queryForObject(
                "select count(*) from lattice_b1_compile_test.article_chunks",
                Integer.class
        );

        Optional<ArticleRecord> paymentArticle = articleJdbcRepository.findByConceptId("payment");
        Optional<ArticleRecord> fulfillmentArticle = articleJdbcRepository.findByConceptId("fulfillment");

        assertThat(sourceFileCount).isEqualTo(3);
        assertThat(articleChunkCount).isEqualTo(3);
        assertThat(paymentArticle).isPresent();
        assertThat(paymentArticle.orElseThrow().getTitle()).isEqualTo("Payment");
        assertThat(paymentArticle.orElseThrow().getContent()).contains("payment/order.md");
        assertThat(paymentArticle.orElseThrow().getContent()).contains("payment/refund.md");

        assertThat(fulfillmentArticle).isPresent();
        assertThat(fulfillmentArticle.orElseThrow().getTitle()).isEqualTo("Fulfillment");
        assertThat(fulfillmentArticle.orElseThrow().getContent()).contains("fulfillment/fc.md");
    }
}
