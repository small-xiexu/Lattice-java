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
 * 职责：验证管理后台页面入口可直接访问
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
     * 验证 `/admin` 会返回管理后台页面。
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
                .andExpect(content().string(containsString("Lattice Admin")))
                .andExpect(content().string(containsString("id=\"refresh-all\"")))
                .andExpect(content().string(containsString("id=\"job-list\"")))
                .andExpect(content().string(containsString("data-tab=\"governance\"")))
                .andExpect(content().string(containsString("data-governance-tab=\"lint\"")))
                .andExpect(content().string(containsString("data-governance-tab=\"quality\"")))
                .andExpect(content().string(containsString("data-governance-tab=\"coverage\"")))
                .andExpect(content().string(containsString("data-governance-tab=\"snapshot\"")))
                .andExpect(content().string(containsString("data-governance-tab=\"link\"")))
                .andExpect(content().string(containsString("id=\"admin-vault-sync\"")))
                .andExpect(content().string(containsString("id=\"rollback-article\"")))
                .andExpect(content().string(containsString("id=\"global-result\"")));
    }
}
