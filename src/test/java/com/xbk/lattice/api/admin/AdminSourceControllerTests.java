package com.xbk.lattice.api.admin;

import com.xbk.lattice.source.domain.KnowledgeSource;
import com.xbk.lattice.compiler.service.CompileJobService;
import com.xbk.lattice.source.service.SourceService;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge?currentSchema=lattice",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.ai.openai.api-key=test-openai-key",
        "spring.ai.anthropic.api-key=test-anthropic-key",
        "lattice.query.cache.store=in-memory",
        "lattice.compiler.jobs.worker-enabled=false",
        "lattice.source.admin.allowed-server-dirs[0]=${java.io.tmpdir}"
})
@AutoConfigureMockMvc
class AdminSourceControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SourceService sourceService;

    @Autowired
    private CompileJobService compileJobService;

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
     * 验证 Git 资料源可创建并完成配置校验。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldCreateAndValidateGitSource(@TempDir Path tempDir) throws Exception {
        resetTables();
        Path gitRepoDir = tempDir.resolve("git-source");
        Files.createDirectories(gitRepoDir);
        Files.writeString(gitRepoDir.resolve("README.md"), "# Git Source\n", StandardCharsets.UTF_8);
        try (Git git = Git.init().setDirectory(gitRepoDir.toFile()).call()) {
            git.add().addFilepattern(".").call();
            git.commit().setMessage("init").setAuthor("tester", "tester@example.com").call();
        }

        String responseBody = mockMvc.perform(post("/api/v1/admin/sources/git")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceCode": "git-docs",
                                  "name": "Git Docs",
                                  "contentProfile": "DOCUMENT",
                                  "remoteUrl": "%s",
                                  "branch": "master"
                                }
                                """.formatted(gitRepoDir.toUri().toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceCode").value("git-docs"))
                .andExpect(jsonPath("$.sourceType").value("GIT"))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        Long sourceId = readLong(responseBody, "id");

        mockMvc.perform(post("/api/v1/admin/sources/" + sourceId + "/validate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.sourceType").value("GIT"))
                .andExpect(jsonPath("$.branch").value("master"));
    }

    /**
     * 验证服务器目录资料源可同步并写入源文件清单。
     *
     * @param tempDir 临时目录
     * @throws Exception 测试异常
     */
    @Test
    void shouldCreateSyncAndListServerDirSourceFiles(@TempDir Path tempDir) throws Exception {
        resetTables();
        Path serverDir = tempDir.resolve("server-docs");
        Files.createDirectories(serverDir);
        Files.writeString(serverDir.resolve("README.md"), "# Server Docs\n\nretry=3\n", StandardCharsets.UTF_8);

        String responseBody = mockMvc.perform(post("/api/v1/admin/sources/server-dir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceCode": "server-docs",
                                  "name": "Server Docs",
                                  "contentProfile": "DOCUMENT",
                                  "serverDir": "%s"
                                }
                                """.formatted(serverDir.toString().replace("\\", "\\\\"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceType").value("SERVER_DIR"))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        Long sourceId = readLong(responseBody, "id");

        String syncBody = mockMvc.perform(post("/api/v1/admin/sources/" + sourceId + "/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPILE_QUEUED"))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        Long runId = readLong(syncBody, "runId");

        compileJobService.processNextQueuedJob();

        mockMvc.perform(get("/api/v1/admin/sources/" + sourceId + "/runs/" + runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));

        mockMvc.perform(get("/api/v1/admin/sources/" + sourceId + "/files"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].relativePath").value("README.md"))
                .andExpect(jsonPath("$[0].contentPreview", containsString("retry=3")));
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
        jdbcTemplate.execute("TRUNCATE TABLE lattice.source_sync_runs RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.source_snapshots RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.source_file_chunks RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.source_files RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.article_source_refs RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.article_chunks RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.articles RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.compile_jobs RESTART IDENTITY CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE lattice.knowledge_sources RESTART IDENTITY CASCADE");
        jdbcTemplate.update(
                """
                        insert into lattice.knowledge_sources (
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

    private Long readLong(String responseBody, String fieldName) throws Exception {
        com.fasterxml.jackson.databind.JsonNode rootNode = new com.fasterxml.jackson.databind.ObjectMapper().readTree(responseBody);
        return rootNode.path(fieldName).isMissingNode() || rootNode.path(fieldName).isNull()
                ? null
                : rootNode.path(fieldName).asLong();
    }
}
