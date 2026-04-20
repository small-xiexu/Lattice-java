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
 * 职责：验证知识库管理、知识问答与管理员设置页面入口可直接访问
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
                .andExpect(content().string(containsString("Lattice-java")))
                .andExpect(content().string(containsString("id=\"refresh-page\"")))
                .andExpect(content().string(containsString("data-tab-group=\"knowledge-console\"")))
                .andExpect(content().string(containsString("id=\"knowledge-tab-status\"")))
                .andExpect(content().string(containsString("id=\"knowledge-tab-upload\"")))
                .andExpect(content().string(containsString("id=\"knowledge-tab-runs\"")))
                .andExpect(content().string(containsString("id=\"knowledge-tab-articles\"")))
                .andExpect(content().string(containsString("id=\"summary-cards\"")))
                .andExpect(content().string(containsString("id=\"submit-upload-job\"")))
                .andExpect(content().string(containsString("id=\"compile-file-picker\"")))
                .andExpect(content().string(containsString("id=\"compile-file-trigger\"")))
                .andExpect(content().string(containsString("id=\"compile-file-clear\"")))
                .andExpect(content().string(containsString("id=\"compile-file-summary\"")))
                .andExpect(content().string(containsString("id=\"compile-file-list\"")))
                .andExpect(content().string(containsString("id=\"compile-file-feedback\"")))
                .andExpect(content().string(containsString("id=\"create-git-source\"")))
                .andExpect(content().string(containsString("id=\"git-access-mode-public\"")))
                .andExpect(content().string(containsString("id=\"git-access-mode-private\"")))
                .andExpect(content().string(containsString("id=\"git-source-credential-ref\"")))
                .andExpect(content().string(containsString("id=\"toggle-inline-git-credential\"")))
                .andExpect(content().string(containsString("id=\"save-inline-source-credential\"")))
                .andExpect(content().string(containsString("id=\"inline-source-credential-list\"")))
                .andExpect(content().string(containsString("id=\"recent-run-overview\"")))
                .andExpect(content().string(containsString("id=\"job-list\"")))
                .andExpect(content().string(containsString("class=\"run-board top-gap\"")))
                .andExpect(content().string(containsString("id=\"article-list\"")))
                .andExpect(content().string(containsString("id=\"article-detail-title\"")))
                .andExpect(content().string(containsString("id=\"article-primary-source\"")))
                .andExpect(content().string(containsString("id=\"article-source-overview\"")))
                .andExpect(content().string(containsString("id=\"article-source-note\"")))
                .andExpect(content().string(containsString("id=\"article-sources\"")))
                .andExpect(content().string(containsString("id=\"article-technical-info\"")))
                .andExpect(content().string(containsString("id=\"page-notice\"")))
                .andExpect(content().string(containsString("src=\"/admin/admin-tabs.js?v=20260419-tabs-1\"")))
                .andExpect(content().string(containsString("src=\"/admin/management.js?v=20260420-source-console-")))
                .andExpect(content().string(containsString("href=\"/admin/ask\"")))
                .andExpect(content().string(containsString("href=\"/admin/settings\"")))
                .andExpect(content().string(containsString("href=\"/admin/developer-access\"")))
                .andExpect(content().string(containsString("data-help-faq-key=\"first-steps\"")))
                .andExpect(content().string(not(containsString("id=\"create-server-source\""))))
                .andExpect(content().string(not(containsString("id=\"rebuild-chunks\""))))
                .andExpect(content().string(not(containsString("文档解析连接"))))
                .andExpect(content().string(not(containsString("id=\"test-document-parse-connection\""))))
                .andExpect(content().string(not(containsString("id=\"save-source-credential\""))))
                .andExpect(content().string(not(containsString("LLM 配置"))))
                .andExpect(content().string(not(containsString("id=\"refresh-llm\""))))
                .andExpect(content().string(not(containsString("id=\"global-status\""))))
                .andExpect(content().string(not(containsString("id=\"global-result\""))));

        mockMvc.perform(get("/admin/ask"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/admin/ask.html"));

        mockMvc.perform(get("/admin/ask.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Lattice-java")))
                .andExpect(content().string(containsString("id=\"refresh-qa-status\"")))
                .andExpect(content().string(containsString("id=\"submit-question\"")))
                .andExpect(content().string(containsString("id=\"ask-answer-metrics\"")))
                .andExpect(content().string(containsString("id=\"ask-answer-support\"")))
                .andExpect(content().string(containsString("id=\"ask-answer\"")))
                .andExpect(content().string(containsString("id=\"ask-source-summary\"")))
                .andExpect(content().string(containsString("id=\"ask-sources\"")))
                .andExpect(content().string(containsString("id=\"page-notice\"")))
                .andExpect(content().string(containsString("href=\"/admin/settings\"")))
                .andExpect(content().string(containsString("href=\"/admin/developer-access\"")))
                .andExpect(content().string(containsString("data-help-faq-key=\"no-citation\"")))
                .andExpect(content().string(containsString("src=\"/admin/ask.js?v=20260421-openai-regression-1\"")))
                .andExpect(content().string(not(containsString("文档解析连接"))))
                .andExpect(content().string(not(containsString("id=\"test-document-parse-connection\""))))
                .andExpect(content().string(not(containsString("id=\"global-status\""))))
                .andExpect(content().string(not(containsString("id=\"global-result\""))))
                .andExpect(content().string(containsString("href=\"/admin\"")));

        mockMvc.perform(get("/admin/settings"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/admin/settings.html"));

        mockMvc.perform(get("/admin/developer-access"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/admin/settings.html"));

        mockMvc.perform(get("/admin/ai"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/admin/ai.html"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/admin/settings.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"settings-page-notice\"")))
                .andExpect(content().string(containsString("data-tab-group=\"admin-console\"")))
                .andExpect(content().string(containsString("id=\"settings-tab-overview\"")))
                .andExpect(content().string(containsString("id=\"settings-tab-llm\"")))
                .andExpect(content().string(containsString("id=\"settings-tab-parse\"")))
                .andExpect(content().string(containsString("id=\"settings-tab-sources\"")))
                .andExpect(content().string(not(containsString("id=\"settings-tab-developer-access\""))))
                .andExpect(content().string(containsString("data-tab-panel=\"developer-access-entry\"")))
                .andExpect(content().string(containsString("data-tab-group=\"developer-access-sections\"")))
                .andExpect(content().string(containsString("id=\"developer-base-url\"")))
                .andExpect(content().string(containsString("id=\"developer-service-status\"")))
                .andExpect(content().string(containsString("id=\"create-server-source\"")))
                .andExpect(content().string(containsString("id=\"rebuild-chunks\"")))
                .andExpect(content().string(containsString("id=\"refresh-vector-status\"")))
                .andExpect(content().string(containsString("id=\"vector-status-summary\"")))
                .andExpect(content().string(containsString("id=\"save-vector-config\"")))
                .andExpect(content().string(containsString("id=\"vector-profile-preview\"")))
                .andExpect(content().string(containsString("id=\"load-retrieval-config\"")))
                .andExpect(content().string(containsString("id=\"retrieval-config-summary\"")))
                .andExpect(content().string(containsString("id=\"save-retrieval-config\"")))
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
                .andExpect(content().string(containsString("src=\"/admin/admin-tabs.js?v=20260419-tabs-1\"")))
                .andExpect(content().string(containsString("src=\"/admin/admin-sections.js?v=20260420-admin-sections-2\"")))
                .andExpect(content().string(containsString("src=\"/admin/settings.js?v=20260420-settings-3\"")))
                .andExpect(content().string(containsString("src=\"/admin/settings-page.js?v=20260421-openai-regression-1\"")))
                .andExpect(content().string(containsString("src=\"/admin/developer-access.js?v=20260420-developer-access-3\"")))
                .andExpect(content().string(containsString("href=\"/admin\"")))
                .andExpect(content().string(containsString("href=\"/admin/developer-access\"")))
                .andExpect(content().string(containsString("data-help-faq-key=\"default-warning\"")))
                .andExpect(content().string(not(containsString("id=\"global-status\""))))
                .andExpect(content().string(not(containsString("id=\"global-result\""))))
                .andExpect(content().string(not(containsString("输入单价（美元 / 1k token）"))))
                .andExpect(content().string(not(containsString("扩展参数 JSON"))))
                .andExpect(content().string(not(containsString("Temperature"))))
                .andExpect(content().string(not(containsString("Timeout Seconds"))))
                .andExpect(content().string(not(containsString("备注"))))
                .andExpect(content().string(not(containsString("操作人"))));
    }

    /**
     * 验证内容列表前端资源保留可理解性与窄屏展示所需的关键逻辑。
     *
     * @throws Exception 测试异常
     */
    @Test
    void shouldExposeArticlePresentationAssets() throws Exception {
        mockMvc.perform(get("/admin/management.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("function resolveArticleDisplayTitle")))
                .andExpect(content().string(containsString("function resolveArticleSummary")))
                .andExpect(content().string(containsString("function resolveSourceTypeLabel")))
                .andExpect(content().string(containsString("function buildSourceGranularityNote")))
                .andExpect(content().string(containsString("function buildFileLevelTraceExplanation")))
                .andExpect(content().string(containsString(".xlsx")))
                .andExpect(content().string(containsString(".pdf")))
                .andExpect(content().string(containsString("__LATTICE_ADMIN_TEST__")));

        mockMvc.perform(get("/admin/admin.css"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(".article-toolbar")))
                .andExpect(content().string(containsString(".detail-section-grid")))
                .andExpect(content().string(containsString(".source-reference-list")))
                .andExpect(content().string(containsString(".settings-maintenance-grid")))
                .andExpect(content().string(containsString(".source-section")))
                .andExpect(content().string(containsString(".source-card-primary")))
                .andExpect(content().string(containsString(".answer-markdown")))
                .andExpect(content().string(containsString("@media (max-width: 1180px)")))
                .andExpect(content().string(containsString("@media (max-width: 640px)")));
    }
}
