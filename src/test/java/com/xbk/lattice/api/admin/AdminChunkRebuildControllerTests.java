package com.xbk.lattice.api.admin;

import com.xbk.lattice.infra.persistence.ArticleChunkJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.SourceFileChunkJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileChunkRecord;
import com.xbk.lattice.infra.persistence.SourceFileJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AdminChunkRebuildController 测试
 *
 * 职责：验证管理侧可触发 article/source chunks 的全量重建
 *
 * @author xiexu
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge?currentSchema=lattice",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.ai.openai.api-key=test-openai-key",
        "spring.ai.anthropic.api-key=test-anthropic-key",
        "lattice.query.cache.store=in-memory",
        "lattice.compiler.jobs.worker-enabled=false"
})
@AutoConfigureMockMvc
class AdminChunkRebuildControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ArticleJdbcRepository articleJdbcRepository;

    @Autowired
    private ArticleChunkJdbcRepository articleChunkJdbcRepository;

    @Autowired
    private SourceFileJdbcRepository sourceFileJdbcRepository;

    @Autowired
    private SourceFileChunkJdbcRepository sourceFileChunkJdbcRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 验证管理侧接口可按正文完整重建文章与源文件 chunks。
     *
     * @throws Exception 测试异常
     */
    @Test
    void shouldRebuildAllChunksViaAdminApi() throws Exception {
        resetTables();
        articleJdbcRepository.upsert(new ArticleRecord(
                "payment-timeout",
                "Payment Timeout",
                """
                        # Payment Timeout

                        ## Timeout Rules
                        - retry=3
                        - interval=30s
                        """,
                "ACTIVE",
                OffsetDateTime.now(),
                List.of("payment/order.md"),
                "{\"description\":\"payment summary\"}"
        ));
        sourceFileJdbcRepository.upsert(new SourceFileRecord(
                "payment/order.md",
                "# Payment Timeout",
                "md",
                64L,
                """
                        # Payment Source

                        ## Timeout Rules
                        - retry=3
                        - interval=30s
                        """,
                "{}",
                false,
                "payment/order.md"
        ));
        articleChunkJdbcRepository.replaceChunks("payment-timeout", List.of("legacy-article-chunk"));
        sourceFileChunkJdbcRepository.replaceChunks(
                "payment/order.md",
                List.of(new SourceFileChunkRecord("payment/order.md", 0, "legacy-source-chunk", false))
        );

        mockMvc.perform(post("/api/v1/admin/compile/rebuild-chunks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rebuiltArticleCount").value(1))
                .andExpect(jsonPath("$.rebuiltSourceFileCount").value(1))
                .andExpect(jsonPath("$.articleChunkCount").value(1))
                .andExpect(jsonPath("$.sourceFileChunkCount").value(1))
                .andExpect(jsonPath("$.rebuiltAt").isNotEmpty());

        List<String> articleChunks = articleChunkJdbcRepository.findChunkTexts("payment-timeout");
        List<SourceFileChunkRecord> sourceChunks = sourceFileChunkJdbcRepository.findByFilePaths(List.of("payment/order.md"));

        assertThat(articleChunks).hasSize(1);
        assertThat(articleChunks.get(0)).contains("# Payment Timeout");
        assertThat(articleChunks.get(0)).doesNotContain("legacy-article-chunk");
        assertThat(sourceChunks).hasSize(1);
        assertThat(sourceChunks.get(0).getChunkText()).contains("# Payment Source");
        assertThat(sourceChunks.get(0).getChunkText()).doesNotContain("legacy-source-chunk");
    }

    /**
     * 重置测试表。
     */
    private void resetTables() {
        jdbcTemplate.execute("TRUNCATE TABLE lattice.source_files CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.articles CASCADE");
    }
}
