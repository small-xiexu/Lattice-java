package com.xbk.lattice.api.admin;

import com.xbk.lattice.query.service.QueryRetrievalSettingsService;
import com.xbk.lattice.query.service.QueryRetrievalSettingsState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AdminQueryRetrievalConfigController 测试
 *
 * 职责：验证管理侧可查看、保存并即时生效 Query 检索配置
 *
 * @author xiexu
 */
@SpringBootTest(properties = {
        "spring.profiles.active=jdbc",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge?currentSchema=lattice_b8_retrieval_config_test",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.flyway.enabled=true",
        "spring.flyway.schemas=lattice_b8_retrieval_config_test",
        "spring.flyway.default-schema=lattice_b8_retrieval_config_test",
        "spring.ai.openai.api-key=test-openai-key",
        "spring.ai.anthropic.api-key=test-anthropic-key",
        "lattice.query.cache.store=in-memory"
})
@AutoConfigureMockMvc
class AdminQueryRetrievalConfigControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private QueryRetrievalSettingsService queryRetrievalSettingsService;

    /**
     * 验证默认返回数据库中的 Query 检索配置。
     *
     * @throws Exception 测试异常
     */
    @Test
    void shouldExposeDefaultRetrievalConfig() throws Exception {
        resetTable();

        mockMvc.perform(get("/api/v1/admin/query/retrieval/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parallelEnabled").value(true))
                .andExpect(jsonPath("$.rewriteEnabled").value(true))
                .andExpect(jsonPath("$.intentAwareVectorEnabled").value(true))
                .andExpect(jsonPath("$.ftsWeight").value(1.0D))
                .andExpect(jsonPath("$.refkeyWeight").value(1.45D))
                .andExpect(jsonPath("$.articleChunkWeight").value(1.25D))
                .andExpect(jsonPath("$.sourceWeight").value(1.0D))
                .andExpect(jsonPath("$.sourceChunkWeight").value(1.3D))
                .andExpect(jsonPath("$.contributionWeight").value(1.0D))
                .andExpect(jsonPath("$.graphWeight").value(1.2D))
                .andExpect(jsonPath("$.articleVectorWeight").value(1.0D))
                .andExpect(jsonPath("$.chunkVectorWeight").value(1.35D))
                .andExpect(jsonPath("$.rrfK").value(60));
    }

    /**
     * 验证保存后的 Query 检索配置会落库并立即作用于运行时读取。
     *
     * @throws Exception 测试异常
     */
    @Test
    void shouldPersistRetrievalConfigAndApplyImmediately() throws Exception {
        resetTable();

        mockMvc.perform(put("/api/v1/admin/query/retrieval/config")
                        .contentType(APPLICATION_JSON)
                        .content("{"
                                + "\"parallelEnabled\":false,"
                                + "\"rewriteEnabled\":false,"
                                + "\"intentAwareVectorEnabled\":false,"
                                + "\"ftsWeight\":1.5,"
                                + "\"refkeyWeight\":1.9,"
                                + "\"articleChunkWeight\":1.7,"
                                + "\"sourceWeight\":0.8,"
                                + "\"sourceChunkWeight\":1.4,"
                                + "\"contributionWeight\":1.1,"
                                + "\"graphWeight\":1.3,"
                                + "\"articleVectorWeight\":0.7,"
                                + "\"chunkVectorWeight\":1.6,"
                                + "\"rrfK\":48"
                                + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parallelEnabled").value(false))
                .andExpect(jsonPath("$.rewriteEnabled").value(false))
                .andExpect(jsonPath("$.intentAwareVectorEnabled").value(false))
                .andExpect(jsonPath("$.ftsWeight").value(1.5D))
                .andExpect(jsonPath("$.refkeyWeight").value(1.9D))
                .andExpect(jsonPath("$.articleChunkWeight").value(1.7D))
                .andExpect(jsonPath("$.sourceWeight").value(0.8D))
                .andExpect(jsonPath("$.sourceChunkWeight").value(1.4D))
                .andExpect(jsonPath("$.contributionWeight").value(1.1D))
                .andExpect(jsonPath("$.graphWeight").value(1.3D))
                .andExpect(jsonPath("$.articleVectorWeight").value(0.7D))
                .andExpect(jsonPath("$.chunkVectorWeight").value(1.6D))
                .andExpect(jsonPath("$.rrfK").value(48));

        QueryRetrievalSettingsState state = queryRetrievalSettingsService.getCurrentState();
        assertThat(state.isParallelEnabled()).isFalse();
        assertThat(state.isRewriteEnabled()).isFalse();
        assertThat(state.isIntentAwareVectorEnabled()).isFalse();
        assertThat(state.getFtsWeight()).isEqualTo(1.5D);
        assertThat(state.getRefkeyWeight()).isEqualTo(1.9D);
        assertThat(state.getArticleChunkWeight()).isEqualTo(1.7D);
        assertThat(state.getSourceWeight()).isEqualTo(0.8D);
        assertThat(state.getSourceChunkWeight()).isEqualTo(1.4D);
        assertThat(state.getContributionWeight()).isEqualTo(1.1D);
        assertThat(state.getGraphWeight()).isEqualTo(1.3D);
        assertThat(state.getArticleVectorWeight()).isEqualTo(0.7D);
        assertThat(state.getChunkVectorWeight()).isEqualTo(1.6D);
        assertThat(state.getRrfK()).isEqualTo(48);

        Double ftsWeight = jdbcTemplate.queryForObject(
                "select fts_weight from lattice_b8_retrieval_config_test.query_retrieval_settings where id = 1",
                Double.class
        );
        Integer rrfK = jdbcTemplate.queryForObject(
                "select rrf_k from lattice_b8_retrieval_config_test.query_retrieval_settings where id = 1",
                Integer.class
        );
        Boolean parallelEnabled = jdbcTemplate.queryForObject(
                "select parallel_enabled from lattice_b8_retrieval_config_test.query_retrieval_settings where id = 1",
                Boolean.class
        );
        Boolean rewriteEnabled = jdbcTemplate.queryForObject(
                "select rewrite_enabled from lattice_b8_retrieval_config_test.query_retrieval_settings where id = 1",
                Boolean.class
        );
        assertThat(ftsWeight).isEqualTo(Double.valueOf(1.5D));
        assertThat(rrfK).isEqualTo(Integer.valueOf(48));
        assertThat(parallelEnabled).isFalse();
        assertThat(rewriteEnabled).isFalse();
    }

    /**
     * 重置 Query 检索配置表。
     */
    private void resetTable() {
        jdbcTemplate.execute("TRUNCATE TABLE lattice_b8_retrieval_config_test.query_retrieval_settings");
        jdbcTemplate.execute("INSERT INTO lattice_b8_retrieval_config_test.query_retrieval_settings DEFAULT VALUES");
    }
}
