package com.xbk.lattice.api.admin;

import com.xbk.lattice.compiler.config.CompileReviewProperties;
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
 * AdminCompileReviewConfigController 测试
 *
 * 职责：验证后台可查看、保存并即时应用 compile review 配置
 *
 * @author xiexu
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge?currentSchema=lattice",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.ai.openai.api-key=test-openai-key",
        "spring.ai.anthropic.api-key=test-anthropic-key",
        "lattice.compiler.review.auto-fix-enabled=true",
        "lattice.compiler.review.max-fix-rounds=1",
        "lattice.compiler.review.allow-persist-needs-human-review=false",
        "lattice.compiler.review.human-review-severity-threshold=HIGH"
})
@AutoConfigureMockMvc
class AdminCompileReviewConfigControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CompileReviewProperties compileReviewProperties;

    /**
     * 验证未落库时会回退到 properties 默认值。
     *
     * @throws Exception 测试异常
     */
    @Test
    void shouldExposePropertyBackedCompileReviewConfigWhenNoDatabaseOverride() throws Exception {
        resetTable();

        mockMvc.perform(get("/api/v1/admin/compile/review/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autoFixEnabled").value(true))
                .andExpect(jsonPath("$.maxFixRounds").value(1))
                .andExpect(jsonPath("$.allowPersistNeedsHumanReview").value(false))
                .andExpect(jsonPath("$.humanReviewSeverityThreshold").value("HIGH"))
                .andExpect(jsonPath("$.configSource").value("properties"));
    }

    /**
     * 验证保存配置后会立即作用于运行时并持久化。
     *
     * @throws Exception 测试异常
     */
    @Test
    void shouldPersistCompileReviewConfigAndApplyToRuntimeImmediately() throws Exception {
        resetTable();

        mockMvc.perform(put("/api/v1/admin/compile/review/config")
                        .contentType(APPLICATION_JSON)
                        .content("{"
                                + "\"autoFixEnabled\":true,"
                                + "\"maxFixRounds\":3,"
                                + "\"allowPersistNeedsHumanReview\":false,"
                                + "\"humanReviewSeverityThreshold\":\"MEDIUM\","
                                + "\"operator\":\"tester\""
                                + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autoFixEnabled").value(true))
                .andExpect(jsonPath("$.maxFixRounds").value(3))
                .andExpect(jsonPath("$.allowPersistNeedsHumanReview").value(false))
                .andExpect(jsonPath("$.humanReviewSeverityThreshold").value("MEDIUM"))
                .andExpect(jsonPath("$.configSource").value("database"))
                .andExpect(jsonPath("$.updatedBy").value("tester"));

        assertThat(compileReviewProperties.isAutoFixEnabled()).isTrue();
        assertThat(compileReviewProperties.getMaxFixRounds()).isEqualTo(3);
        assertThat(compileReviewProperties.isAllowPersistNeedsHumanReview()).isFalse();
        assertThat(compileReviewProperties.getHumanReviewSeverityThreshold()).isEqualTo("MEDIUM");

        Boolean autoFixEnabled = jdbcTemplate.queryForObject(
                "select auto_fix_enabled from lattice.compile_review_settings where config_scope = 'default'",
                Boolean.class
        );
        Integer maxFixRounds = jdbcTemplate.queryForObject(
                "select max_fix_rounds from lattice.compile_review_settings where config_scope = 'default'",
                Integer.class
        );
        Boolean allowPersistNeedsHumanReview = jdbcTemplate.queryForObject(
                """
                        select allow_persist_needs_human_review
                        from lattice.compile_review_settings
                        where config_scope = 'default'
                        """,
                Boolean.class
        );
        String humanReviewSeverityThreshold = jdbcTemplate.queryForObject(
                """
                        select human_review_severity_threshold
                        from lattice.compile_review_settings
                        where config_scope = 'default'
                        """,
                String.class
        );
        assertThat(autoFixEnabled).isTrue();
        assertThat(maxFixRounds).isEqualTo(Integer.valueOf(3));
        assertThat(allowPersistNeedsHumanReview).isFalse();
        assertThat(humanReviewSeverityThreshold).isEqualTo("MEDIUM");
    }

    private void resetTable() {
        jdbcTemplate.execute("TRUNCATE TABLE lattice.compile_review_settings");
        compileReviewProperties.setAutoFixEnabled(true);
        compileReviewProperties.setMaxFixRounds(1);
        compileReviewProperties.setAllowPersistNeedsHumanReview(false);
        compileReviewProperties.setHumanReviewSeverityThreshold("HIGH");
    }
}
