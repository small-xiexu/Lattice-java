package com.xbk.lattice.query.service;

import com.xbk.lattice.compiler.config.LlmProperties;
import com.xbk.lattice.compiler.service.LlmGateway;
import com.xbk.lattice.llm.service.LlmCallResult;
import com.xbk.lattice.llm.service.LlmClient;
import com.xbk.lattice.query.domain.AnswerOutcome;
import com.xbk.lattice.query.domain.GenerationMode;
import com.xbk.lattice.query.domain.ModelExecutionStatus;
import com.xbk.lattice.query.domain.QueryAnswerPayload;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AnswerGenerationService 测试
 *
 * 职责：验证查询答案会基于多路证据调用 LLM 生成 Markdown
 *
 * @author xiexu
 */
class AnswerGenerationServiceTests {

    /**
     * 验证答案生成会把 article/source/contribution 多路证据组织进 Prompt，并解析结构化结果。
     *
     * @throws Exception 反射构造异常
     */
    @Test
    void shouldGenerateStructuredAnswerPayloadFromMultiRouteEvidence() throws Exception {
        RecordingLlmClient recordingLlmClient = new RecordingLlmClient("""
                {
                  "answerMarkdown":"# Payment Answer\\n\\n- settle_window=45m\\n- refund-manual-review 需要人工复核",
                  "answerOutcome":"SUCCESS",
                  "answerCacheable":true
                }
                """);
        AnswerGenerationService answerGenerationService = new AnswerGenerationService(
                createGatewayFixture(recordingLlmClient).getLlmGateway()
        );

        QueryAnswerPayload answerPayload = answerGenerationService.generatePayload(
                "settle_window=45m 和 refund-manual-review 分别代表什么",
                List.of(
                        new QueryArticleHit(
                                QueryEvidenceType.ARTICLE,
                                "payment-routing",
                                "Payment Routing",
                                "route=standard",
                                "{\"description\":\"支付路由总览\"}",
                                List.of("payment/analyze.json"),
                                3.0
                        ),
                        new QueryArticleHit(
                                QueryEvidenceType.SOURCE,
                                "payment/context.md#0",
                                "payment/context.md",
                                "settle_window=45m",
                                "{\"filePath\":\"payment/context.md\"}",
                                List.of("payment/context.md"),
                                2.0
                        ),
                        new QueryArticleHit(
                                QueryEvidenceType.CONTRIBUTION,
                                "contribution-1",
                                "Contribution",
                                "refund-manual-review 表示退款请求进入人工复核队列。",
                                "{\"question\":\"refund-manual-review 是什么\"}",
                                List.of("[用户反馈]"),
                                1.0
                        )
                )
        );

        assertThat(answerPayload.getAnswerMarkdown()).startsWith("# Payment Answer");
        assertThat(answerPayload.getAnswerOutcome()).isEqualTo(AnswerOutcome.SUCCESS);
        assertThat(answerPayload.getGenerationMode()).isEqualTo(GenerationMode.LLM);
        assertThat(answerPayload.getModelExecutionStatus()).isEqualTo(ModelExecutionStatus.SUCCESS);
        assertThat(answerPayload.isAnswerCacheable()).isTrue();
        assertThat(recordingLlmClient.getLastSystemPrompt()).contains("answerMarkdown");
        assertThat(recordingLlmClient.getLastSystemPrompt()).contains("只能输出 JSON");
        assertThat(recordingLlmClient.getLastUserPrompt()).contains("ARTICLE EVIDENCE");
        assertThat(recordingLlmClient.getLastUserPrompt()).contains("Payment Routing");
        assertThat(recordingLlmClient.getLastUserPrompt()).contains("SOURCE EVIDENCE");
        assertThat(recordingLlmClient.getLastUserPrompt()).contains("payment/context.md");
        assertThat(recordingLlmClient.getLastUserPrompt()).contains("CONTRIBUTION EVIDENCE");
        assertThat(recordingLlmClient.getLastUserPrompt()).contains("refund-manual-review 表示退款请求进入人工复核队列");
    }

    /**
     * 验证结构化输出失败时，会 fail-open 到旧文本路径并收敛为 fallback 语义。
     *
     * @throws Exception 反射构造异常
     */
    @Test
    void shouldFailOpenToLegacyTextWhenStructuredPayloadParsingFails() throws Exception {
        RecordingLlmClient recordingLlmClient = new RecordingLlmClient("""
                # Legacy Answer

                - settle_window=45m
                """);
        AnswerGenerationService answerGenerationService = new AnswerGenerationService(
                createGatewayFixture(recordingLlmClient).getLlmGateway()
        );

        QueryAnswerPayload answerPayload = answerGenerationService.generatePayload(
                "settle_window=45m 是什么",
                List.of(
                        new QueryArticleHit(
                                QueryEvidenceType.ARTICLE,
                                "payment-routing",
                                "Payment Routing",
                                "route=standard",
                                "{\"description\":\"支付路由总览\"}",
                                List.of("payment/analyze.json"),
                                3.0
                        ),
                        new QueryArticleHit(
                                QueryEvidenceType.SOURCE,
                                "payment/context.md#0",
                                "payment/context.md",
                                "settle_window=45m",
                                "{\"filePath\":\"payment/context.md\"}",
                                List.of("payment/context.md"),
                                2.0
                        )
                )
        );

        assertThat(answerPayload.getAnswerMarkdown()).startsWith("# Legacy Answer");
        assertThat(answerPayload.getAnswerOutcome()).isEqualTo(AnswerOutcome.PARTIAL_ANSWER);
        assertThat(answerPayload.getGenerationMode()).isEqualTo(GenerationMode.FALLBACK);
        assertThat(answerPayload.getModelExecutionStatus()).isEqualTo(ModelExecutionStatus.FAILED);
        assertThat(answerPayload.isAnswerCacheable()).isFalse();
    }

    /**
     * 验证稳定结构化答案会写入 L1 prompt cache，并在下一次请求复用缓存。
     *
     * @throws Exception 反射构造异常
     */
    @Test
    void shouldWritePromptCacheForStableStructuredAnswerAndReuseCachedRawPayload() throws Exception {
        RecordingLlmClient recordingLlmClient = new RecordingLlmClient("""
                {
                  "answerMarkdown":"# Stable Answer\\n\\n- settle_window=45m",
                  "answerOutcome":"SUCCESS",
                  "answerCacheable":true
                }
                """);
        GatewayFixture gatewayFixture = createGatewayFixture(recordingLlmClient);
        AnswerGenerationService answerGenerationService = new AnswerGenerationService(gatewayFixture.getLlmGateway());

        QueryAnswerPayload firstPayload = answerGenerationService.generatePayload(
                "settle_window=45m 是什么",
                buildMultiRouteEvidence()
        );
        QueryAnswerPayload secondPayload = answerGenerationService.generatePayload(
                "settle_window=45m 是什么",
                buildMultiRouteEvidence()
        );

        assertThat(firstPayload.getAnswerOutcome()).isEqualTo(AnswerOutcome.SUCCESS);
        assertThat(secondPayload.getAnswerOutcome()).isEqualTo(AnswerOutcome.SUCCESS);
        assertThat(recordingLlmClient.getCallCount()).isEqualTo(1);
        assertThat(gatewayFixture.getRedisKeyValueStore().values).hasSize(1);
    }

    /**
     * 验证非结构化旧文本结果不会写入 L1 prompt cache。
     *
     * @throws Exception 反射构造异常
     */
    @Test
    void shouldSkipPromptCacheWhenStructuredAnswerFallsBackToLegacyText() throws Exception {
        RecordingLlmClient recordingLlmClient = new RecordingLlmClient("""
                # Legacy Answer

                - settle_window=45m
                """);
        GatewayFixture gatewayFixture = createGatewayFixture(recordingLlmClient);
        AnswerGenerationService answerGenerationService = new AnswerGenerationService(gatewayFixture.getLlmGateway());

        QueryAnswerPayload firstPayload = answerGenerationService.generatePayload(
                "settle_window=45m 是什么",
                buildMultiRouteEvidence()
        );
        QueryAnswerPayload secondPayload = answerGenerationService.generatePayload(
                "settle_window=45m 是什么",
                buildMultiRouteEvidence()
        );

        assertThat(firstPayload.getGenerationMode()).isEqualTo(GenerationMode.FALLBACK);
        assertThat(secondPayload.getGenerationMode()).isEqualTo(GenerationMode.FALLBACK);
        assertThat(recordingLlmClient.getCallCount()).isEqualTo(2);
        assertThat(gatewayFixture.getRedisKeyValueStore().values).isEmpty();
    }

    /**
     * 验证 review rewrite 的结构化成功结果也会写入 L1 prompt cache。
     *
     * @throws Exception 反射构造异常
     */
    @Test
    void shouldWritePromptCacheForStructuredRewriteFromReview() throws Exception {
        RecordingLlmClient recordingLlmClient = new RecordingLlmClient("""
                {
                  "answerMarkdown":"# Rewritten Answer\\n\\n- timeout=30s",
                  "answerOutcome":"SUCCESS",
                  "answerCacheable":true
                }
                """);
        GatewayFixture gatewayFixture = createGatewayFixture(recordingLlmClient);
        AnswerGenerationService answerGenerationService = new AnswerGenerationService(gatewayFixture.getLlmGateway());

        QueryAnswerPayload firstPayload = answerGenerationService.rewriteFromReviewPayload(
                "query-1",
                "query",
                "rewrite",
                "timeout 配置是多少",
                "旧答案",
                "请补齐 timeout 的明确值",
                buildMultiRouteEvidence()
        );
        QueryAnswerPayload secondPayload = answerGenerationService.rewriteFromReviewPayload(
                "query-1",
                "query",
                "rewrite",
                "timeout 配置是多少",
                "旧答案",
                "请补齐 timeout 的明确值",
                buildMultiRouteEvidence()
        );

        assertThat(firstPayload.getAnswerOutcome()).isEqualTo(AnswerOutcome.SUCCESS);
        assertThat(secondPayload.getAnswerOutcome()).isEqualTo(AnswerOutcome.SUCCESS);
        assertThat(recordingLlmClient.getCallCount()).isEqualTo(1);
        assertThat(gatewayFixture.getRedisKeyValueStore().values).hasSize(1);
    }

    /**
     * 验证 query revise 会走统一文本生成入口，并复用 L1 prompt cache。
     *
     * @throws Exception 反射构造异常
     */
    @Test
    void shouldWritePromptCacheForQueryReviseMarkdown() throws Exception {
        RecordingLlmClient recordingLlmClient = new RecordingLlmClient("""
                # Revised Answer

                - settle_window=45m 以用户纠正为准
                """);
        GatewayFixture gatewayFixture = createGatewayFixture(recordingLlmClient);
        AnswerGenerationService answerGenerationService = new AnswerGenerationService(gatewayFixture.getLlmGateway());

        String firstAnswer = answerGenerationService.revise(
                "query-1",
                "query",
                "rewrite",
                "settle_window=45m 是什么",
                "旧答案",
                "用户补充：settle_window=45m 代表结算窗口",
                buildMultiRouteEvidence()
        );
        String secondAnswer = answerGenerationService.revise(
                "query-1",
                "query",
                "rewrite",
                "settle_window=45m 是什么",
                "旧答案",
                "用户补充：settle_window=45m 代表结算窗口",
                buildMultiRouteEvidence()
        );

        assertThat(firstAnswer).startsWith("# Revised Answer");
        assertThat(secondAnswer).startsWith("# Revised Answer");
        assertThat(recordingLlmClient.getCallCount()).isEqualTo(1);
        assertThat(gatewayFixture.getRedisKeyValueStore().values).hasSize(1);
    }

    /**
     * 验证无证据时会返回 no knowledge 语义，而不是误标为模型失败。
     */
    @Test
    void shouldReturnNoRelevantKnowledgePayloadWhenEvidenceMissing() {
        AnswerGenerationService answerGenerationService = new AnswerGenerationService();

        QueryAnswerPayload answerPayload = answerGenerationService.generatePayload("不存在的问题", List.of());

        assertThat(answerPayload.getAnswerMarkdown()).isEqualTo("未找到相关知识");
        assertThat(answerPayload.getAnswerOutcome()).isEqualTo(AnswerOutcome.NO_RELEVANT_KNOWLEDGE);
        assertThat(answerPayload.getGenerationMode()).isEqualTo(GenerationMode.RULE_BASED);
        assertThat(answerPayload.getModelExecutionStatus()).isEqualTo(ModelExecutionStatus.SKIPPED);
        assertThat(answerPayload.isAnswerCacheable()).isFalse();
    }

    /**
     * 通过反射构造可控的 LLM 网关。
     *
     * @param recordingLlmClient 记录 Prompt 的客户端
     * @return LLM 网关
     * @throws Exception 反射构造异常
     */
    private GatewayFixture createGatewayFixture(RecordingLlmClient recordingLlmClient) throws Exception {
        Constructor<LlmGateway> constructor = LlmGateway.class.getDeclaredConstructor(
                LlmClient.class,
                LlmClient.class,
                RedisKeyValueStore.class,
                LlmProperties.class
        );
        constructor.setAccessible(true);
        FakeRedisKeyValueStore redisKeyValueStore = new FakeRedisKeyValueStore();
        LlmGateway llmGateway = constructor.newInstance(
                recordingLlmClient,
                recordingLlmClient,
                redisKeyValueStore,
                createProperties()
        );
        return new GatewayFixture(llmGateway, redisKeyValueStore);
    }

    /**
     * 构造多路证据样例。
     *
     * @return 多路证据样例
     */
    private List<QueryArticleHit> buildMultiRouteEvidence() {
        return List.of(
                new QueryArticleHit(
                        QueryEvidenceType.ARTICLE,
                        "payment-routing",
                        "Payment Routing",
                        "route=standard",
                        "{\"description\":\"支付路由总览\"}",
                        List.of("payment/analyze.json"),
                        3.0
                ),
                new QueryArticleHit(
                        QueryEvidenceType.SOURCE,
                        "payment/context.md#0",
                        "payment/context.md",
                        "settle_window=45m",
                        "{\"filePath\":\"payment/context.md\"}",
                        List.of("payment/context.md"),
                        2.0
                ),
                new QueryArticleHit(
                        QueryEvidenceType.CONTRIBUTION,
                        "contribution-1",
                        "Contribution",
                        "refund-manual-review 表示退款请求进入人工复核队列。",
                        "{\"question\":\"refund-manual-review 是什么\"}",
                        List.of("[用户反馈]"),
                        1.0
                )
        );
    }

    /**
     * 创建默认 LLM 配置。
     *
     * @return LLM 配置
     */
    private LlmProperties createProperties() {
        LlmProperties llmProperties = new LlmProperties();
        llmProperties.setCompileModel("openai");
        llmProperties.setReviewerModel("anthropic");
        llmProperties.setBudgetUsd(10.0D);
        llmProperties.setCacheTtlSeconds(3600L);
        llmProperties.setCacheKeyPrefix("llm:cache:");
        return llmProperties;
    }

    /**
     * 记录 Prompt 的 LLM 客户端替身。
     *
     * @author xiexu
     */
    private static class RecordingLlmClient implements LlmClient {

        private final String content;

        private String lastSystemPrompt;

        private String lastUserPrompt;

        private int callCount;

        private RecordingLlmClient(String content) {
            this.content = content;
            this.callCount = 0;
        }

        /**
         * 记录本次调用的 Prompt，并返回固定内容。
         *
         * @param systemPrompt 系统提示词
         * @param userPrompt 用户提示词
         * @return 模型调用结果
         */
        @Override
        public LlmCallResult call(String systemPrompt, String userPrompt) {
            this.lastSystemPrompt = systemPrompt;
            this.lastUserPrompt = userPrompt;
            callCount++;
            return new LlmCallResult(content, 128, 64);
        }

        /**
         * 获取最后一次系统提示词。
         *
         * @return 系统提示词
         */
        private String getLastSystemPrompt() {
            return lastSystemPrompt;
        }

        /**
         * 获取最后一次用户提示词。
         *
         * @return 用户提示词
         */
        private String getLastUserPrompt() {
            return lastUserPrompt;
        }

        /**
         * 获取模型调用次数。
         *
         * @return 模型调用次数
         */
        private int getCallCount() {
            return callCount;
        }
    }

    /**
     * Redis 键值存储替身。
     *
     * @author xiexu
     */
    private static class FakeRedisKeyValueStore implements RedisKeyValueStore {

        private final Map<String, String> values = new LinkedHashMap<String, String>();

        /**
         * 读取缓存值。
         *
         * @param key 缓存键
         * @return 缓存值
         */
        @Override
        public String get(String key) {
            return values.get(key);
        }

        /**
         * 写入缓存值。
         *
         * @param key 缓存键
         * @param value 缓存值
         * @param ttl 生存时间
         */
        @Override
        public void set(String key, String value, Duration ttl) {
            values.put(key, value);
        }

        /**
         * 返回固定过期时间占位值。
         *
         * @param key 缓存键
         * @return 过期时间
         */
        @Override
        public Long getExpire(String key) {
            return 1L;
        }

        @Override
        public void deleteByPrefix(String keyPrefix) {
            values.keySet().removeIf(key -> key.startsWith(keyPrefix));
        }
    }

    /**
     * 网关测试夹具。
     *
     * 职责：统一暴露网关与缓存替身，便于断言 L1 prompt cache 行为
     *
     * @author xiexu
     */
    private static class GatewayFixture {

        private final LlmGateway llmGateway;

        private final FakeRedisKeyValueStore redisKeyValueStore;

        private GatewayFixture(LlmGateway llmGateway, FakeRedisKeyValueStore redisKeyValueStore) {
            this.llmGateway = llmGateway;
            this.redisKeyValueStore = redisKeyValueStore;
        }

        /**
         * 返回 LLM 网关。
         *
         * @return LLM 网关
         */
        private LlmGateway getLlmGateway() {
            return llmGateway;
        }

        /**
         * 返回 Redis 替身。
         *
         * @return Redis 替身
         */
        private FakeRedisKeyValueStore getRedisKeyValueStore() {
            return redisKeyValueStore;
        }
    }

}
