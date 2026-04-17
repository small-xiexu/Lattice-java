package com.xbk.lattice.governance;

import com.xbk.lattice.compiler.config.LlmProperties;
import com.xbk.lattice.compiler.service.LlmCallResult;
import com.xbk.lattice.compiler.service.LlmClient;
import com.xbk.lattice.compiler.service.LlmGateway;
import com.xbk.lattice.compiler.service.LlmUsageRecord;
import com.xbk.lattice.compiler.service.LlmUsageStore;
import com.xbk.lattice.governance.repo.RepoSnapshotService;
import com.xbk.lattice.infra.persistence.ArticleJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleRecord;
import com.xbk.lattice.infra.persistence.RepoSnapshotRecord;
import com.xbk.lattice.infra.persistence.ArticleSnapshotJdbcRepository;
import com.xbk.lattice.infra.persistence.ArticleSnapshotRecord;
import com.xbk.lattice.infra.persistence.SourceFileJdbcRepository;
import com.xbk.lattice.infra.persistence.SourceFileRecord;
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
                LlmUsageStore.class,
                LlmProperties.class
        );
        constructor.setAccessible(true);
        return constructor.newInstance(
                compileClient,
                new CapturingLlmClient("{\"passed\":true}"),
                new FakeRedisKeyValueStore(),
                new FakeLlmUsageStore(),
                createProperties()
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
    }

    /**
     * LLM usage 存储替身。
     *
     * 职责：提供空实现，避免测试依赖真实持久化
     *
     * @author xiexu
     */
    private static class FakeLlmUsageStore implements LlmUsageStore {

        @Override
        public void save(LlmUsageRecord llmUsageRecord) {
            // 无操作：当前测试只关心纠错行为本身
        }
    }
}
