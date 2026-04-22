package com.xbk.lattice.governance;

import com.xbk.lattice.compiler.config.LlmProperties;
import com.xbk.lattice.compiler.service.LlmGateway;
import com.xbk.lattice.governance.repo.RepoSnapshotService;
import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.RepoSnapshotRecord;
import com.xbk.lattice.infra.persistence.ArticleSnapshotJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleSnapshotRecord;
import com.xbk.lattice.infra.persistence.SourceFileJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileRecord;
import com.xbk.lattice.llm.service.LlmCallResult;
import com.xbk.lattice.llm.service.LlmClient;
import com.xbk.lattice.llm.service.ExecutionLlmSnapshotService;
import com.xbk.lattice.llm.service.LlmClientFactory;
import com.xbk.lattice.llm.service.LlmInvocationExecutor;
import com.xbk.lattice.llm.service.LlmRouteResolution;
import com.xbk.lattice.observability.StructuredEventLogger;
import com.xbk.lattice.query.service.RedisKeyValueStore;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Constructor;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ArticleCorrectionService 测试
 *
 * 职责：验证真实纠错会重写文章、写入 correction snapshot，并返回 downstream 影响范围
 *
 * @author xiexu
 */
class ArticleCorrectionServiceTests {

    /**
     * 验证纠错会执行源文件交叉验证、重写文章并生成 correction snapshot。
     */
    @Test
    void shouldRewriteArticleCaptureCorrectionSnapshotAndReturnDownstreamIds() throws Exception {
        FakeArticleJdbcRepository articleJdbcRepository = new FakeArticleJdbcRepository(List.of(
                article(
                        "payment-config",
                        "# Payment Config\n\n当前文档写的是 retry=3。",
                        List.of("sources/payment-config.md"),
                        List.of(),
                        List.of()
                ),
                article(
                        "payment-timeout",
                        "# Payment Timeout\n\n依赖 [[payment-config]]。",
                        List.of("sources/payment-timeout.md"),
                        List.of("payment-config"),
                        List.of()
                ),
                article(
                        "refund-manual-review",
                        "# Refund Manual Review\n\nSee [[payment-timeout]]",
                        List.of("sources/refund-manual-review.md"),
                        List.of(),
                        List.of()
                )
        ));
        FakeSourceFileJdbcRepository sourceFileJdbcRepository = new FakeSourceFileJdbcRepository(List.of(
                new SourceFileRecord(
                        "sources/payment-config.md",
                        "retry=5",
                        "md",
                        120,
                        "配置原文明确写的是 retry=5，且支付配置已同步。",
                        "{}",
                        false,
                        "sources/payment-config.md"
                )
        ));
        FakeArticleSnapshotJdbcRepository articleSnapshotJdbcRepository = new FakeArticleSnapshotJdbcRepository();
        RecordingRepoSnapshotService repoSnapshotService = new RecordingRepoSnapshotService();
        CapturingLlmClient compileClient = new CapturingLlmClient(
                "{\"supported\":true,\"evidence\":\"源文件明确写的是 retry=5\"}",
                "# Payment Config\n\n已按源文件修正为 retry=5。"
        );
        ArticleCorrectionService articleCorrectionService = new ArticleCorrectionService(
                articleJdbcRepository,
                sourceFileJdbcRepository,
                articleSnapshotJdbcRepository,
                new DependencyGraphService(articleJdbcRepository),
                newLlmGateway(compileClient)
        );
        articleCorrectionService.setRepoSnapshotService(repoSnapshotService);

        ArticleCorrectionResult result = articleCorrectionService.correct("payment-config", "重试次数应为 5");

        assertThat(result.getConceptId()).isEqualTo("payment-config");
        assertThat(result.getRevisedContent()).isEqualTo("# Payment Config\n\n已按源文件修正为 retry=5。");
        assertThat(result.getDownstreamIds()).containsExactly("payment-timeout", "refund-manual-review");
        assertThat(result.isValidationSupported()).isTrue();
        assertThat(articleJdbcRepository.getLastUpserted()).isNotNull();
        assertThat(articleJdbcRepository.getLastUpserted().getContent()).isEqualTo(result.getRevisedContent());
        assertThat(articleJdbcRepository.getLastUpserted().getReviewStatus()).isEqualTo("needs_review");
        assertThat(articleSnapshotJdbcRepository.getSavedRecords()).hasSize(1);
        assertThat(articleSnapshotJdbcRepository.getSavedRecords().get(0).getSnapshotReason()).isEqualTo("correction");
        assertThat(articleSnapshotJdbcRepository.getSavedRecords().get(0).getContent()).isEqualTo(result.getRevisedContent());
        assertThat(repoSnapshotService.getSnapshotCount()).isEqualTo(1);
        assertThat(repoSnapshotService.getLastTriggerEvent()).isEqualTo("governance.correct");
        assertThat(compileClient.getSystemPrompts()).hasSize(2);
        assertThat(compileClient.getUserPrompts().get(0))
                .contains("重试次数应为 5")
                .contains("配置原文明确写的是 retry=5");
        assertThat(compileClient.getUserPrompts().get(1)).contains("源文件明确写的是 retry=5");
    }

    /**
     * 验证当纠错结果返回带 frontmatter 的 Markdown 时，会同步刷新结构化字段，并把 review_status 统一收口为 needs_review。
     */
    @Test
    void shouldSyncStructuredFieldsFromCorrectedMarkdown() throws Exception {
        FakeArticleJdbcRepository articleJdbcRepository = new FakeArticleJdbcRepository(List.of(
                article(
                        "payment-config",
                        """
                                ---
                                title: "Payment Config"
                                summary: "旧摘要"
                                referential_keywords: ["retry=3"]
                                sources: ["sources/payment-config.md"]
                                depends_on: []
                                related: []
                                confidence: medium
                                compiled_at: "2026-04-16T10:10:00+08:00"
                                review_status: passed
                                ---

                                # Payment Config

                                当前文档写的是 retry=3。
                                """,
                        List.of("sources/payment-config.md"),
                        List.of(),
                        List.of()
                )
        ));
        FakeSourceFileJdbcRepository sourceFileJdbcRepository = new FakeSourceFileJdbcRepository(List.of(
                new SourceFileRecord(
                        "sources/payment-config.md",
                        "retry=5",
                        "md",
                        120,
                        "配置原文明确写的是 retry=5，且支付配置已同步。",
                        "{}",
                        false,
                        "sources/payment-config.md"
                )
        ));
        FakeArticleSnapshotJdbcRepository articleSnapshotJdbcRepository = new FakeArticleSnapshotJdbcRepository();
        CapturingLlmClient compileClient = new CapturingLlmClient(
                "{\"supported\":true,\"evidence\":\"源文件明确写的是 retry=5\"}",
                """
                        ---
                        title: "Payment Config Updated"
                        summary: "已按源文件修正到 retry=5。"
                        referential_keywords: ["retry=5", "payment"]
                        sources: ["sources/payment-config.md", "sources/retry-policy.md"]
                        depends_on: ["retry-policy"]
                        related: ["payment-timeout"]
                        confidence: "high"
                        compiled_at: "2026-04-22T18:10:00+08:00"
                        review_status: passed
                        ---

                        # Payment Config Updated

                        已按源文件修正为 retry=5。
                        """
        );
        ArticleCorrectionService articleCorrectionService = new ArticleCorrectionService(
                articleJdbcRepository,
                sourceFileJdbcRepository,
                articleSnapshotJdbcRepository,
                new DependencyGraphService(articleJdbcRepository),
                newLlmGateway(compileClient)
        );

        ArticleCorrectionResult result = articleCorrectionService.correct("payment-config", "重试次数应为 5");
        ArticleRecord updatedRecord = articleJdbcRepository.getLastUpserted();

        assertThat(result.getRevisedContent()).contains("review_status: needs_review");
        assertThat(updatedRecord.getTitle()).isEqualTo("Payment Config Updated");
        assertThat(updatedRecord.getSummary()).isEqualTo("已按源文件修正到 retry=5。");
        assertThat(updatedRecord.getReferentialKeywords()).containsExactly("retry=5", "payment");
        assertThat(updatedRecord.getSourcePaths()).containsExactly("sources/payment-config.md", "sources/retry-policy.md");
        assertThat(updatedRecord.getDependsOn()).containsExactly("retry-policy");
        assertThat(updatedRecord.getRelated()).containsExactly("payment-timeout");
        assertThat(updatedRecord.getConfidence()).isEqualTo("high");
        assertThat(updatedRecord.getCompiledAt()).isEqualTo(OffsetDateTime.parse("2026-04-22T18:10:00+08:00"));
        assertThat(updatedRecord.getReviewStatus()).isEqualTo("needs_review");
        assertThat(updatedRecord.getContent())
                .contains("review_status: needs_review")
                .contains("referential_keywords: [\"retry=5\", \"payment\"]");
        assertThat(updatedRecord.getMetadataJson())
                .contains("\"summary\":\"已按源文件修正到 retry=5。\"")
                .contains("\"description\":\"已按源文件修正到 retry=5。\"")
                .contains("\"sourceCount\":2");
        assertThat(articleSnapshotJdbcRepository.getSavedRecords()).hasSize(1);
        assertThat(articleSnapshotJdbcRepository.getSavedRecords().get(0).getContent()).contains("review_status: needs_review");
    }

    /**
     * 验证交叉验证载荷不可解析时，会回退为“无证据支持”，但仍继续执行纠错重写。
     */
    @Test
    void shouldFallbackToUnsupportedPayloadWhenCrossValidateJsonIsInvalid() throws Exception {
        FakeArticleJdbcRepository articleJdbcRepository = new FakeArticleJdbcRepository(List.of(
                article(
                        "payment-config",
                        "# Payment Config\n\n当前文档写的是 retry=3。",
                        List.of("sources/payment-config.md"),
                        List.of(),
                        List.of()
                )
        ));
        FakeSourceFileJdbcRepository sourceFileJdbcRepository = new FakeSourceFileJdbcRepository(List.of(
                new SourceFileRecord(
                        "sources/payment-config.md",
                        "retry=5",
                        "md",
                        120,
                        "配置原文明确写的是 retry=5，且支付配置已同步。",
                        "{}",
                        false,
                        "sources/payment-config.md"
                )
        ));
        FakeArticleSnapshotJdbcRepository articleSnapshotJdbcRepository = new FakeArticleSnapshotJdbcRepository();
        CapturingLlmClient compileClient = new CapturingLlmClient(
                "not-json-response",
                "# Payment Config\n\n保留纠错结果，但当前没有交叉验证证据。"
        );
        ArticleCorrectionService articleCorrectionService = new ArticleCorrectionService(
                articleJdbcRepository,
                sourceFileJdbcRepository,
                articleSnapshotJdbcRepository,
                new DependencyGraphService(articleJdbcRepository),
                newLlmGateway(compileClient)
        );

        ArticleCorrectionResult result = articleCorrectionService.correct("payment-config", "重试次数应为 5");

        assertThat(result.isValidationSupported()).isFalse();
        assertThat(compileClient.getUserPrompts().get(1))
                .contains("源文件是否支持：false")
                .doesNotContain("证据摘要：");
        assertThat(articleSnapshotJdbcRepository.getSavedRecords()).hasSize(1);
    }

    /**
     * 验证当运行时快照服务可用时，Admin 文章纠错会先冻结一个专用 writer 作用域，再执行两次纠错调用。
     */
    @Test
    void shouldFreezeScopedWriterRouteBeforeArticleCorrection() throws Exception {
        FakeArticleJdbcRepository articleJdbcRepository = new FakeArticleJdbcRepository(List.of(
                article(
                        "payment-config",
                        "# Payment Config\n\n当前文档写的是 retry=3。",
                        List.of("sources/payment-config.md"),
                        List.of(),
                        List.of()
                )
        ));
        FakeSourceFileJdbcRepository sourceFileJdbcRepository = new FakeSourceFileJdbcRepository(List.of(
                new SourceFileRecord(
                        "sources/payment-config.md",
                        "retry=5",
                        "md",
                        120,
                        "配置原文明确写的是 retry=5，且支付配置已同步。",
                        "{}",
                        false,
                        "sources/payment-config.md"
                )
        ));
        RecordingExecutionLlmSnapshotService snapshotService = new RecordingExecutionLlmSnapshotService();
        ArticleCorrectionService articleCorrectionService = new ArticleCorrectionService(
                articleJdbcRepository,
                sourceFileJdbcRepository,
                new FakeArticleSnapshotJdbcRepository(),
                new DependencyGraphService(articleJdbcRepository),
                newLlmGateway(new CapturingLlmClient(
                        "{\"supported\":true,\"evidence\":\"源文件明确写的是 retry=5\"}",
                        "# Payment Config\n\n已按源文件修正为 retry=5。"
                ))
        );
        articleCorrectionService.setExecutionLlmSnapshotService(snapshotService);

        articleCorrectionService.correct("payment-config", "重试次数应为 5");

        assertThat(snapshotService.freezeCount).isEqualTo(2);
        assertThat(snapshotService.lastScopeType).isEqualTo(ExecutionLlmSnapshotService.COMPILE_SCOPE_TYPE);
        assertThat(snapshotService.lastScene).isEqualTo(ExecutionLlmSnapshotService.COMPILE_SCENE);
        assertThat(snapshotService.lastScopeId).isEqualTo("admin-correction:no-source:payment-config");
    }

    /**
     * 验证当 Admin 纠错命中 README 演示连接时，会在真正调用模型前给出明确错误提示。
     */
    @Test
    void shouldRejectReadmeDemoRouteBeforeCallingCorrectionLlm() throws Exception {
        FakeArticleJdbcRepository articleJdbcRepository = new FakeArticleJdbcRepository(List.of(
                article(
                        "payment-config",
                        "# Payment Config\n\n当前文档写的是 retry=3。",
                        List.of("sources/payment-config.md"),
                        List.of(),
                        List.of()
                )
        ));
        FakeSourceFileJdbcRepository sourceFileJdbcRepository = new FakeSourceFileJdbcRepository(List.of(
                new SourceFileRecord(
                        "sources/payment-config.md",
                        "retry=5",
                        "md",
                        120,
                        "配置原文明确写的是 retry=5，且支付配置已同步。",
                        "{}",
                        false,
                        "sources/payment-config.md"
                )
        ));
        RecordingExecutionLlmSnapshotService snapshotService = new RecordingExecutionLlmSnapshotService();
        snapshotService.setResolvedRoute(new LlmRouteResolution(
                ExecutionLlmSnapshotService.COMPILE_SCOPE_TYPE,
                "admin-correction:no-source:payment-config",
                ExecutionLlmSnapshotService.COMPILE_SCENE,
                ExecutionLlmSnapshotService.ROLE_WRITER,
                Long.valueOf(1L),
                Long.valueOf(2L),
                Integer.valueOf(1),
                "readme-demo-openai",
                "openai",
                "http://127.0.0.1:19999",
                "demo-key",
                "gpt-5.4-demo",
                null,
                null,
                null,
                "{}",
                null,
                null,
                true
        ));
        CapturingLlmClient compileClient = new CapturingLlmClient(
                "{\"supported\":true,\"evidence\":\"源文件明确写的是 retry=5\"}",
                "# Payment Config\n\n已按源文件修正为 retry=5。"
        );
        ArticleCorrectionService articleCorrectionService = new ArticleCorrectionService(
                articleJdbcRepository,
                sourceFileJdbcRepository,
                new FakeArticleSnapshotJdbcRepository(),
                new DependencyGraphService(articleJdbcRepository),
                newScopedLlmGateway(compileClient, snapshotService)
        );
        articleCorrectionService.setExecutionLlmSnapshotService(snapshotService);

        assertThatThrownBy(() -> articleCorrectionService.correct("payment-config", "重试次数应为 5"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("README 演示 LLM 连接")
                .hasMessageContaining("/admin/settings")
                .hasMessageContaining("compile.writer");
        assertThat(snapshotService.freezeCount).isEqualTo(1);
        assertThat(compileClient.getSystemPrompts()).isEmpty();
        assertThat(compileClient.getUserPrompts()).isEmpty();
    }

    private ArticleRecord article(
            String conceptId,
            String content,
            List<String> sourcePaths,
            List<String> dependsOn,
            List<String> related
    ) {
        return new ArticleRecord(
                conceptId,
                conceptId,
                content,
                "active",
                OffsetDateTime.parse("2026-04-16T10:10:00+08:00"),
                sourcePaths,
                "{}",
                "summary",
                List.of(),
                dependsOn,
                related,
                "medium",
                "passed"
        );
    }

    private LlmGateway newLlmGateway(CapturingLlmClient compileClient) throws Exception {
        Constructor<LlmGateway> constructor = LlmGateway.class.getDeclaredConstructor(
                LlmClient.class,
                LlmClient.class,
                RedisKeyValueStore.class,
                LlmProperties.class
        );
        constructor.setAccessible(true);
        return constructor.newInstance(
                compileClient,
                new CapturingLlmClient("{\"passed\":true}"),
                new FakeRedisKeyValueStore(),
                createProperties()
        );
    }

    private LlmGateway newScopedLlmGateway(
            CapturingLlmClient compileClient,
            ExecutionLlmSnapshotService executionLlmSnapshotService
    ) throws Exception {
        Constructor<LlmGateway> constructor = LlmGateway.class.getDeclaredConstructor(
                LlmClient.class,
                LlmClient.class,
                RedisKeyValueStore.class,
                LlmProperties.class,
                LlmClientFactory.class,
                ExecutionLlmSnapshotService.class,
                LlmInvocationExecutor.class,
                String.class,
                String.class,
                String.class,
                String.class,
                StructuredEventLogger.class
        );
        constructor.setAccessible(true);
        return constructor.newInstance(
                compileClient,
                new CapturingLlmClient("{\"passed\":true}"),
                new FakeRedisKeyValueStore(),
                createProperties(),
                null,
                executionLlmSnapshotService,
                null,
                "http://127.0.0.1:18080",
                "test-compile-key",
                "http://127.0.0.1:18081",
                "test-review-key",
                null
        );
    }

    private LlmProperties createProperties() {
        LlmProperties llmProperties = new LlmProperties();
        llmProperties.setCompileModel("openai");
        llmProperties.setReviewerModel("anthropic");
        llmProperties.setBudgetUsd(20.0D);
        llmProperties.setCacheTtlSeconds(3600L);
        llmProperties.setCacheKeyPrefix("llm:test:");
        return llmProperties;
    }

    /**
     * 捕获型 LLM 客户端。
     *
     * 职责：按顺序返回预置输出，并记录实际传入的提示词
     *
     * @author xiexu
     */
    private static class CapturingLlmClient implements LlmClient {

        private final Queue<String> responses = new ArrayDeque<String>();

        private final List<String> systemPrompts = new ArrayList<String>();

        private final List<String> userPrompts = new ArrayList<String>();

        private CapturingLlmClient(String... responses) {
            for (String response : responses) {
                this.responses.add(response);
            }
        }

        @Override
        public LlmCallResult call(String systemPrompt, String userPrompt) {
            systemPrompts.add(systemPrompt);
            userPrompts.add(userPrompt);
            return new LlmCallResult(responses.remove(), 120, 40);
        }

        private List<String> getSystemPrompts() {
            return systemPrompts;
        }

        private List<String> getUserPrompts() {
            return userPrompts;
        }
    }

    /**
     * 记录 Admin 纠错冻结作用域的运行时快照服务替身。
     *
     * @author xiexu
     */
    private static class RecordingExecutionLlmSnapshotService extends ExecutionLlmSnapshotService {

        private int freezeCount;

        private String lastScopeType;

        private String lastScopeId;

        private String lastScene;

        private Optional<LlmRouteResolution> resolvedRoute = Optional.empty();

        private RecordingExecutionLlmSnapshotService() {
            super(
                    properties(),
                    null,
                    null,
                    null,
                    null,
                    new com.xbk.lattice.llm.service.LlmSecretCryptoService(properties())
            );
        }

        @Override
        public List<com.xbk.lattice.llm.domain.ExecutionLlmSnapshot> freezeSnapshots(
                String scopeType,
                String scopeId,
                String scene
        ) {
            freezeCount++;
            lastScopeType = scopeType;
            lastScopeId = scopeId;
            lastScene = scene;
            return List.of();
        }

        @Override
        public Optional<LlmRouteResolution> resolveRoute(
                String scopeType,
                String scopeId,
                String scene,
                String agentRole
        ) {
            return resolvedRoute;
        }

        private void setResolvedRoute(LlmRouteResolution routeResolution) {
            this.resolvedRoute = Optional.ofNullable(routeResolution);
        }

        private static LlmProperties properties() {
            LlmProperties llmProperties = new LlmProperties();
            llmProperties.setSecretEncryptionKey("test-phase8-key-0123456789abcdef");
            return llmProperties;
        }
    }

    /**
     * 文章仓储替身。
     *
     * 职责：提供内存态 article 查找与更新能力
     *
     * @author xiexu
     */
    private static class FakeArticleJdbcRepository extends ArticleJdbcRepository {

        private final Map<String, ArticleRecord> records = new LinkedHashMap<String, ArticleRecord>();

        private ArticleRecord lastUpserted;

        private FakeArticleJdbcRepository(List<ArticleRecord> records) {
            super(new JdbcTemplate());
            for (ArticleRecord record : records) {
                this.records.put(record.getConceptId(), record);
            }
        }

        @Override
        public Optional<ArticleRecord> findByConceptId(String conceptId) {
            return Optional.ofNullable(records.get(conceptId));
        }

        @Override
        public List<ArticleRecord> findAll() {
            return new ArrayList<ArticleRecord>(records.values());
        }

        @Override
        public void upsert(ArticleRecord articleRecord) {
            records.put(articleRecord.getConceptId(), articleRecord);
            lastUpserted = articleRecord;
        }

        private ArticleRecord getLastUpserted() {
            return lastUpserted;
        }
    }

    /**
     * 源文件仓储替身。
     *
     * 职责：按路径返回预置源文件正文
     *
     * @author xiexu
     */
    private static class FakeSourceFileJdbcRepository extends SourceFileJdbcRepository {

        private final Map<String, SourceFileRecord> records = new LinkedHashMap<String, SourceFileRecord>();

        private FakeSourceFileJdbcRepository(List<SourceFileRecord> records) {
            super(new JdbcTemplate());
            for (SourceFileRecord record : records) {
                this.records.put(record.getFilePath(), record);
            }
        }

        @Override
        public Optional<SourceFileRecord> findByPath(String filePath) {
            return Optional.ofNullable(records.get(filePath));
        }
    }

    /**
     * 文章快照仓储替身。
     *
     * 职责：记录 correction snapshot 保存请求
     *
     * @author xiexu
     */
    private static class FakeArticleSnapshotJdbcRepository extends ArticleSnapshotJdbcRepository {

        private final List<ArticleSnapshotRecord> savedRecords = new ArrayList<ArticleSnapshotRecord>();

        private FakeArticleSnapshotJdbcRepository() {
            super(new JdbcTemplate());
        }

        @Override
        public void save(ArticleSnapshotRecord articleSnapshotRecord) {
            savedRecords.add(articleSnapshotRecord);
        }

        private List<ArticleSnapshotRecord> getSavedRecords() {
            return savedRecords;
        }
    }

    /**
     * 整库快照服务替身。
     *
     * 职责：记录纠错流程是否触发 repo snapshot
     *
     * @author xiexu
     */
    private static class RecordingRepoSnapshotService extends RepoSnapshotService {

        private int snapshotCount;

        private String lastTriggerEvent;

        private RecordingRepoSnapshotService() {
            super(null, null, null, null);
        }

        @Override
        public RepoSnapshotRecord snapshot(String triggerEvent, String description, String gitCommit) {
            snapshotCount++;
            lastTriggerEvent = triggerEvent;
            return new RepoSnapshotRecord(1L, null, triggerEvent, gitCommit, description, 0);
        }

        private int getSnapshotCount() {
            return snapshotCount;
        }

        private String getLastTriggerEvent() {
            return lastTriggerEvent;
        }
    }

    /**
     * Redis 键值存储替身。
     *
     * 职责：为 LlmGateway 提供最小缓存实现
     *
     * @author xiexu
     */
    private static class FakeRedisKeyValueStore implements RedisKeyValueStore {

        private final Map<String, String> values = new LinkedHashMap<String, String>();

        @Override
        public String get(String key) {
            return values.get(key);
        }

        @Override
        public void set(String key, String value, Duration ttl) {
            values.put(key, value);
        }

        @Override
        public Long getExpire(String key) {
            return null;
        }

        @Override
        public void deleteByPrefix(String keyPrefix) {
        }
    }

}
