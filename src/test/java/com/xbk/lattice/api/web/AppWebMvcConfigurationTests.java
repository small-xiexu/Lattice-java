package com.xbk.lattice.api.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * App Web MVC 配置测试
 *
 * 职责：验证 App 深链接、静态资源缓存与非 App 路径隔离
 *
 * @author xiexu
 */
@SpringBootTest(properties = {
        "spring.ai.openai.api-key=test-openai-key",
        "spring.ai.anthropic.api-key=test-anthropic-key",
        "lattice.query.cache.store=in-memory"
})
@AutoConfigureMockMvc
class AppWebMvcConfigurationTests {

    private static final String APP_ROOT_MARKER = "<div id=\"root\"></div>";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ResourcePatternResolver resourcePatternResolver;

    /**
     * 验证 App 根路径和无扩展名深链接均返回不缓存的 SPA 入口。
     *
     * @throws Exception 请求或断言异常
     */
    @Test
    void shouldServeSpaEntryForAppRoutesWithoutCaching() throws Exception {
        mockMvc.perform(get("/app"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-cache"))
                .andExpect(content().string(containsString(APP_ROOT_MARKER)));

        mockMvc.perform(get("/app/library/articles/example-article"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-cache"))
                .andExpect(content().string(containsString(APP_ROOT_MARKER)));
    }

    /**
     * 验证带哈希的构建资源使用长期不可变缓存。
     *
     * @throws Exception 资源查找、请求或断言异常
     */
    @Test
    void shouldCacheBuiltAssetsAsImmutableResources() throws Exception {
        String assetName = findBuiltJavaScriptAssetName();
        String assetPath = "/app/assets/" + assetName;

        mockMvc.perform(get(assetPath))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Cache-Control",
                        "max-age=31536000, public, immutable"
                ));
    }

    /**
     * 验证缺失资源和带扩展名路径不会回退到 SPA。
     *
     * @throws Exception 请求或断言异常
     */
    @Test
    void shouldReturnNotFoundForMissingAssetsAndExtensionPaths() throws Exception {
        mockMvc.perform(get("/app/assets/missing.js"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/app/assets/missing"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/app/library/missing.json"))
                .andExpect(status().isNotFound());
    }

    /**
     * 验证旧后台路径已退役，且非 App 命名空间不会被 SPA 接管。
     *
     * @throws Exception 请求或断言异常
     */
    @Test
    void shouldNotFallbackForAdminApiActuatorOrMcpPaths() throws Exception {
        mockMvc.perform(get("/admin/ask"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString(APP_ROOT_MARKER))));
        mockMvc.perform(get("/api/v1/admin/overview"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(APP_ROOT_MARKER))));
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(APP_ROOT_MARKER))));
        mockMvc.perform(get("/mcp"))
                .andExpect(status().is4xxClientError())
                .andExpect(content().string(not(containsString(APP_ROOT_MARKER))));
    }

    /**
     * 查找 Vite 生成的 JavaScript 资源文件名。
     *
     * @return JavaScript 资源文件名
     * @throws Exception 资源查找异常
     */
    private String findBuiltJavaScriptAssetName() throws Exception {
        Resource[] resources = resourcePatternResolver.getResources(
                "classpath*:static/app/assets/*.js"
        );
        return Arrays.stream(resources)
                .map(Resource::getFilename)
                .filter(fileName -> fileName != null && fileName.startsWith("index-"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Vite JavaScript asset not found"));
    }
}
