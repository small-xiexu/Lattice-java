package com.xbk.lattice.api.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 资料源凭据后台接口集成测试。
 *
 * 职责：验证资料源凭据可加密保存，并以脱敏形式返回
 *
 * @author xiexu
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge?currentSchema=lattice",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.ai.openai.api-key=test-openai-key",
        "spring.ai.anthropic.api-key=test-anthropic-key",
        "lattice.query.cache.store=in-memory"
})
@AutoConfigureMockMvc
class AdminSourceCredentialControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 验证资料源凭据可保存并以脱敏形式列出。
     *
     * @throws Exception 测试异常
     */
    @Test
    void shouldSaveAndListMaskedSourceCredentials() throws Exception {
        resetTables();

        mockMvc.perform(post("/api/v1/admin/source-credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "credentialCode": "git-main-token",
                                  "credentialType": "GIT_TOKEN",
                                  "secret": "super-secret-token",
                                  "updatedBy": "tester"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.credentialCode").value("git-main-token"))
                .andExpect(jsonPath("$.credentialType").value("GIT_TOKEN"))
                .andExpect(jsonPath("$.secretMask", not(isEmptyOrNullString())))
                .andExpect(jsonPath("$.secretMask").value(not("super-secret-token")));

        mockMvc.perform(get("/api/v1/admin/source-credentials"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].credentialCode").value("git-main-token"))
                .andExpect(jsonPath("$[0].credentialType").value("GIT_TOKEN"))
                .andExpect(jsonPath("$[0].secretMask", not(isEmptyOrNullString())))
                .andExpect(jsonPath("$[0].enabled").value(true));
    }

    private void resetTables() {
        jdbcTemplate.execute("TRUNCATE TABLE lattice.source_credentials RESTART IDENTITY CASCADE");
    }
}
