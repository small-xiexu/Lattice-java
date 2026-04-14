package com.xbk.lattice.api.compiler;

import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CompileController 测试
 *
 * 职责：验证最小编译入口 API 可触发编译并落表
 *
 * @author xiexu
 */
@SpringBootTest(properties = {
        "spring.profiles.active=jdbc",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge?currentSchema=lattice_b1_api_test",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.flyway.enabled=true",
        "spring.flyway.schemas=lattice_b1_api_test",
        "spring.flyway.default-schema=lattice_b1_api_test",
        "spring.ai.openai.api-key=test-openai-key",
        "spring.ai.anthropic.api-key=test-anthropic-key"
})
@AutoConfigureMockMvc
class CompileControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ArticleJdbcRepository articleJdbcRepository;

    /**
     * 验证编译接口可触发最小编译链路。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldCompileSourceDirectoryViaHttpApi(@TempDir Path tempDir) throws Exception {
        jdbcTemplate.execute("TRUNCATE TABLE lattice_b1_api_test.source_files");
        jdbcTemplate.execute("TRUNCATE TABLE lattice_b1_api_test.articles CASCADE");

        Path paymentDir = Files.createDirectories(tempDir.resolve("payment"));
        Files.writeString(paymentDir.resolve("order.md"), "order-flow", StandardCharsets.UTF_8);

        String requestBody = "{\"sourceDir\":\"" + escapeJson(tempDir.toString()) + "\"}";

        mockMvc.perform(post("/api/v1/compile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.persistedCount").value(1));

        Optional<ArticleRecord> articleRecord = articleJdbcRepository.findByConceptId("payment");
        assertThat(articleRecord).isPresent();
    }

    /**
     * 转义 JSON 字符串。
     *
     * @param value 原始值
     * @return 转义后的值
     */
    private String escapeJson(String value) {
        return value.replace("\\", "\\\\");
    }
}
