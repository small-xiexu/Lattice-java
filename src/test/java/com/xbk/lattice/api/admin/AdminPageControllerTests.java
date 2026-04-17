package com.xbk.lattice.api.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AdminPageController 测试
 *
 * 职责：验证知识库控制台页面入口可直接访问
 *
 * @author xiexu
 */
@SpringBootTest(properties = {
        "spring.profiles.active=jdbc",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge?currentSchema=lattice_b8_admin_page_test",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.flyway.enabled=true",
        "spring.flyway.schemas=lattice_b8_admin_page_test",
        "spring.flyway.default-schema=lattice_b8_admin_page_test",
        "spring.ai.openai.api-key=test-openai-key",
        "spring.ai.anthropic.api-key=test-anthropic-key",
        "lattice.query.cache.store=in-memory"
})
@AutoConfigureMockMvc
class AdminPageControllerTests {

    @Autowired
    private MockMvc mockMvc;

    /**
     * 验证 `/admin` 会返回知识库控制台页面。
     *
     * @throws Exception 测试异常
     */
    @Test
    void shouldServeAdminPage() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/admin/index.html"));

        mockMvc.perform(get("/admin/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Lattice Knowledge Console")))
                .andExpect(content().string(containsString("id=\"refresh-all\"")))
                .andExpect(content().string(containsString("id=\"refresh-overview\"")))
                .andExpect(content().string(containsString("id=\"overview-story-title\"")))
                .andExpect(content().string(containsString("id=\"overview-story-copy\"")))
                .andExpect(content().string(containsString("id=\"overview-cards\"")))
                .andExpect(content().string(containsString("id=\"overview-focus\"")))
                .andExpect(content().string(containsString("id=\"health-cards\"")))
                .andExpect(content().string(containsString("data-jump-tab=\"pending\"")))
                .andExpect(content().string(containsString("id=\"job-list\"")))
                .andExpect(content().string(containsString("data-tab=\"llm\"")))
                .andExpect(content().string(containsString("id=\"refresh-llm\"")))
                .andExpect(content().string(containsString("id=\"llm-connection-list\"")))
                .andExpect(content().string(containsString("id=\"llm-model-list\"")))
                .andExpect(content().string(containsString("id=\"llm-binding-list\"")))
                .andExpect(content().string(containsString("data-tab=\"governance\"")))
                .andExpect(content().string(containsString("data-governance-tab=\"lint\"")))
                .andExpect(content().string(containsString("data-governance-tab=\"quality\"")))
                .andExpect(content().string(containsString("data-governance-tab=\"coverage\"")))
                .andExpect(content().string(containsString("data-governance-tab=\"inspect\"")))
                .andExpect(content().string(containsString("data-governance-tab=\"snapshot\"")))
                .andExpect(content().string(containsString("data-governance-tab=\"link\"")))
                .andExpect(content().string(containsString("id=\"admin-vault-sync\"")))
                .andExpect(content().string(containsString("id=\"rollback-article\"")))
                .andExpect(content().string(containsString("id=\"global-result\"")));
    }
}
