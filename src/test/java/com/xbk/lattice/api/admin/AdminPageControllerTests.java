package com.xbk.lattice.api.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AdminPageController 测试
 *
 * 职责：验证知识库管理、知识问答与 AI 接入页面入口可直接访问
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
     * 验证 `/admin` 会返回知识库管理页面。
     *
     * @throws Exception 测试异常
     */
    @Test
    void shouldServeKnowledgePages() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/admin/index.html"));

        mockMvc.perform(get("/admin/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Lattice Knowledge Console")))
                .andExpect(content().string(containsString("id=\"refresh-page\"")))
                .andExpect(content().string(containsString("id=\"summary-cards\"")))
                .andExpect(content().string(containsString("id=\"submit-upload-job\"")))
                .andExpect(content().string(containsString("id=\"submit-compile-job\"")))
                .andExpect(content().string(containsString("id=\"rebuild-chunks\"")))
                .andExpect(content().string(containsString("id=\"job-list\"")))
                .andExpect(content().string(containsString("id=\"article-list\"")))
                .andExpect(content().string(containsString("id=\"article-detail-title\"")))
                .andExpect(content().string(containsString("id=\"page-notice\"")))
                .andExpect(content().string(containsString("href=\"/admin/ask\"")))
                .andExpect(content().string(not(containsString("文档解析连接"))))
                .andExpect(content().string(not(containsString("id=\"test-document-parse-connection\""))))
                .andExpect(content().string(not(containsString("LLM 配置"))))
                .andExpect(content().string(not(containsString("id=\"refresh-llm\""))))
                .andExpect(content().string(not(containsString("id=\"global-status\""))))
                .andExpect(content().string(not(containsString("id=\"global-result\""))));

        mockMvc.perform(get("/admin/ask"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/admin/ask.html"));

        mockMvc.perform(get("/admin/ask.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Lattice Ask Console")))
                .andExpect(content().string(containsString("id=\"refresh-qa-status\"")))
                .andExpect(content().string(containsString("id=\"submit-question\"")))
                .andExpect(content().string(containsString("id=\"ask-answer\"")))
                .andExpect(content().string(containsString("id=\"ask-sources\"")))
                .andExpect(content().string(containsString("id=\"page-notice\"")))
                .andExpect(content().string(not(containsString("文档解析连接"))))
                .andExpect(content().string(not(containsString("id=\"test-document-parse-connection\""))))
                .andExpect(content().string(not(containsString("id=\"global-status\""))))
                .andExpect(content().string(not(containsString("id=\"global-result\""))))
                .andExpect(content().string(containsString("href=\"/admin\"")));

        mockMvc.perform(get("/admin/ai"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/admin/ai.html"));

        mockMvc.perform(get("/admin/ai.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Lattice Internal AI Console")))
                .andExpect(content().string(containsString("id=\"refresh-ai\"")))
                .andExpect(content().string(containsString("id=\"test-llm-connection\"")))
                .andExpect(content().string(containsString("id=\"test-llm-model\"")))
                .andExpect(content().string(containsString("id=\"save-llm-connection\"")))
                .andExpect(content().string(containsString("id=\"save-llm-model\"")))
                .andExpect(content().string(containsString("id=\"save-llm-binding\"")))
                .andExpect(content().string(containsString("id=\"test-document-parse-connection\"")))
                .andExpect(content().string(containsString("id=\"save-document-parse-connection\"")))
                .andExpect(content().string(containsString("id=\"save-document-parse-settings\"")))
                .andExpect(content().string(containsString("id=\"llm-connection-list\"")))
                .andExpect(content().string(containsString("id=\"llm-model-list\"")))
                .andExpect(content().string(containsString("id=\"llm-binding-list\"")))
                .andExpect(content().string(containsString("id=\"document-parse-connection-list\"")))
                .andExpect(content().string(containsString("id=\"document-parse-settings-summary\"")))
                .andExpect(content().string(containsString("id=\"document-parse-default-connection-id\"")))
                .andExpect(content().string(containsString("id=\"document-parse-cleanup-model-profile-id\"")))
                .andExpect(content().string(containsString("id=\"llm-binding-primary-model-id\"")))
                .andExpect(content().string(containsString("id=\"llm-binding-scene\"")))
                .andExpect(content().string(containsString("id=\"llm-binding-agent-role\"")))
                .andExpect(content().string(containsString("id=\"llm-binding-role-guide\"")))
                .andExpect(content().string(not(containsString("id=\"global-status\""))))
                .andExpect(content().string(not(containsString("id=\"global-result\""))))
                .andExpect(content().string(not(containsString("输入单价（美元 / 1k token）"))))
                .andExpect(content().string(not(containsString("扩展参数 JSON"))))
                .andExpect(content().string(not(containsString("Temperature"))))
                .andExpect(content().string(not(containsString("Timeout Seconds"))))
                .andExpect(content().string(not(containsString("备注"))))
                .andExpect(content().string(not(containsString("操作人"))));
    }
}
