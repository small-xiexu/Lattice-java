package com.xbk.lattice.api.admin;

import com.xbk.lattice.source.domain.KnowledgeSource;
import com.xbk.lattice.source.service.SourceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 资料源后台接口集成测试。
 *
 * 职责：验证资料源分页查询、详情查询与基础更新能力
 *
 * @author xiexu
 */
@SpringBootTest(properties = {
        "spring.profiles.active=jdbc",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge?currentSchema=lattice_phase_e_source_admin_test",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.flyway.enabled=true",
        "spring.flyway.schemas=lattice_phase_e_source_admin_test",
        "spring.flyway.default-schema=lattice_phase_e_source_admin_test",
        "spring.ai.openai.api-key=test-openai-key",
        "spring.ai.anthropic.api-key=test-anthropic-key",
        "lattice.query.cache.store=in-memory"
})
@AutoConfigureMockMvc
class AdminSourceControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SourceService sourceService;

    /**
     * 验证资料源列表支持分页与过滤。
     *
     * @throws Exception 测试异常
     */
    @Test
    void shouldListSourcesWithPaginationAndFilters() throws Exception {
        resetTables();
        createSource("payments-docs", "Payments Docs", "UPLOAD", "DOCUMENT", "ACTIVE", "NORMAL", "AUTO");
        createSource("repo-git", "Repo Git", "GIT", "CODE", "DISABLED", "ADMIN_ONLY", "INCREMENTAL");
        createSource("ops-report", "Ops Report", "UPLOAD", "REPORT", "ACTIVE", "NORMAL", "FULL");

        mockMvc.perform(get("/api/v1/admin/sources?page=1&size=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items.length()").value(2));

        mockMvc.perform(get("/api/v1/admin/sources?status=DISABLED&sourceType=GIT&page=1&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].sourceCode").value("repo-git"))
                .andExpect(jsonPath("$.items[0].status").value("DISABLED"))
                .andExpect(jsonPath("$.items[0].sourceType").value("GIT"));
    }

    /**
     * 验证资料源详情可查询且支持 PATCH 更新。
     *
     * @throws Exception 测试异常
     */
    @Test
    void shouldGetAndPatchSource() throws Exception {
        resetTables();
        KnowledgeSource source = createSource("payments-docs", "Payments Docs", "UPLOAD", "DOCUMENT", "ACTIVE", "NORMAL", "AUTO");

        mockMvc.perform(get("/api/v1/admin/sources/" + source.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceCode").value("payments-docs"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(patch("/api/v1/admin/sources/" + source.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Payments Docs V2",
                                  "status": "DISABLED",
                                  "visibility": "ADMIN_ONLY",
                                  "defaultSyncMode": "FULL",
                                  "configJson": {"branch":"release"}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Payments Docs V2"))
                .andExpect(jsonPath("$.status").value("DISABLED"))
                .andExpect(jsonPath("$.visibility").value("ADMIN_ONLY"))
                .andExpect(jsonPath("$.defaultSyncMode").value("FULL"))
                .andExpect(jsonPath("$.configJson", containsString("\"branch\"")))
                .andExpect(jsonPath("$.configJson", containsString("release")));
    }

    /**
     * 验证归档态资料源不能直接恢复为 ACTIVE。
     *
     * @throws Exception 测试异常
     */
    @Test
    void shouldRejectUnsupportedStatusTransition() throws Exception {
        resetTables();
        KnowledgeSource source = createSource("payments-archive", "Payments Archive", "UPLOAD", "DOCUMENT", "ARCHIVED", "NORMAL", "AUTO");

        mockMvc.perform(patch("/api/v1/admin/sources/" + source.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "ACTIVE"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMPILE_REQUEST_INVALID"))
                .andExpect(jsonPath("$.message").value("unsupported source status transition: ARCHIVED -> ACTIVE"));
    }

    /**
     * 创建测试资料源。
     *
     * @param sourceCode 资料源编码
     * @param name 名称
     * @param sourceType 类型
     * @param contentProfile 内容画像
     * @param status 状态
     * @param visibility 可见性
     * @param defaultSyncMode 默认同步模式
     * @return 已保存资料源
     */
    private KnowledgeSource createSource(
            String sourceCode,
            String name,
            String sourceType,
            String contentProfile,
            String status,
            String visibility,
            String defaultSyncMode
    ) {
        return sourceService.save(new KnowledgeSource(
                null,
                sourceCode,
                name,
                sourceType,
                contentProfile,
                status,
                visibility,
                defaultSyncMode,
                "{}",
                "{}",
                null,
                null,
                null,
                null,
                null,
                null
        ));
    }

    /**
     * 重置测试表。
     */
    private void resetTables() {
        jdbcTemplate.execute("TRUNCATE TABLE lattice_phase_e_source_admin_test.knowledge_sources RESTART IDENTITY CASCADE");
        jdbcTemplate.update(
                """
                        insert into lattice_phase_e_source_admin_test.knowledge_sources (
                            source_code,
                            name,
                            source_type,
                            content_profile,
                            status,
                            visibility,
                            default_sync_mode,
                            config_json,
                            metadata_json
                        )
                        values (
                            'legacy-default',
                            'Legacy Default Source',
                            'UPLOAD',
                            'DOCUMENT',
                            'ACTIVE',
                            'NORMAL',
                            'FULL',
                            '{}'::jsonb,
                            '{"legacyDefault":true}'::jsonb
                        )
                        """
        );
    }
}
