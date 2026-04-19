package com.xbk.lattice.query.service;

import com.xbk.lattice.compiler.config.LlmProperties;
import com.xbk.lattice.compiler.service.LlmGateway;
import com.xbk.lattice.llm.service.LlmCallResult;
import com.xbk.lattice.llm.service.LlmClient;
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
     * 验证答案生成会把 article/source/contribution 多路证据组织进 Prompt，并返回 LLM Markdown。
     *
     * @throws Exception 反射构造异常
     */
    @Test
    void shouldGenerateMarkdownAnswerFromMultiRouteEvidence() throws Exception {
        RecordingLlmClient recordingLlmClient = new RecordingLlmClient("""
                # Payment Answer

                - settle_window=45m
                - refund-manual-review 需要人工复核
                """);
        AnswerGenerationService answerGenerationService = new AnswerGenerationService(
                createGateway(recordingLlmClient)
        );

        String answer = answerGenerationService.generate(
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

        assertThat(answer).startsWith("# Payment Answer");
        assertThat(recordingLlmClient.getLastSystemPrompt()).contains("Markdown");
        assertThat(recordingLlmClient.getLastUserPrompt()).contains("ARTICLE EVIDENCE");
        assertThat(recordingLlmClient.getLastUserPrompt()).contains("Payment Routing");
        assertThat(recordingLlmClient.getLastUserPrompt()).contains("SOURCE EVIDENCE");
        assertThat(recordingLlmClient.getLastUserPrompt()).contains("payment/context.md");
        assertThat(recordingLlmClient.getLastUserPrompt()).contains("CONTRIBUTION EVIDENCE");
        assertThat(recordingLlmClient.getLastUserPrompt()).contains("refund-manual-review 表示退款请求进入人工复核队列");
    }

    /**
     * 通过反射构造可控的 LLM 网关。
     *
     * @param recordingLlmClient 记录 Prompt 的客户端
     * @return LLM 网关
     * @throws Exception 反射构造异常
     */
    private LlmGateway createGateway(RecordingLlmClient recordingLlmClient) throws Exception {
        Constructor<LlmGateway> constructor = LlmGateway.class.getDeclaredConstructor(
                LlmClient.class,
                LlmClient.class,
                RedisKeyValueStore.class,
                LlmProperties.class
        );
        constructor.setAccessible(true);
        return constructor.newInstance(
                recordingLlmClient,
                recordingLlmClient,
                new FakeRedisKeyValueStore(),
                createProperties()
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

        private RecordingLlmClient(String content) {
            this.content = content;
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
    }

}
