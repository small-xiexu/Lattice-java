package com.xbk.lattice.compiler.service;

import com.xbk.lattice.compiler.config.LlmProperties;
import com.xbk.lattice.llm.service.LlmCallResult;
import com.xbk.lattice.llm.service.LlmClient;
import com.xbk.lattice.observability.StructuredEventLogger;
import com.xbk.lattice.query.service.RedisKeyValueStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import java.time.Duration;

/**
 * 测试 LLM Stub 配置
 *
 * 职责：为 SpringBoot 集成测试提供不会出网的稳定 LLM 网关替身
 *
 * @author xiexu
 */
@Configuration(proxyBeanMethods = false)
@Profile("jdbc")
public class TestLlmStubConfiguration {

    /**
     * 提供测试专用 LLM 网关。
     *
     * @param structuredEventLogger 结构化事件日志器
     * @return 测试专用 LLM 网关
     */
    @Bean
    @Primary
    public LlmGateway testLlmGateway(StructuredEventLogger structuredEventLogger) {
        LlmProperties llmProperties = new LlmProperties();
        llmProperties.setCompileModel("stub-compile");
        llmProperties.setReviewerModel("stub-review");
        llmProperties.setBudgetUsd(1_000_000D);
        llmProperties.setCacheTtlSeconds(60L);
        llmProperties.setCacheKeyPrefix("test:llm:");
        return new StubLlmGateway(llmProperties, structuredEventLogger);
    }

    /**
     * 测试专用 LLM 网关。
     *
     * 职责：按用途返回确定性结果，避免集成测试触发真实模型调用
     *
     * @author xiexu
     */
    private static class StubLlmGateway extends LlmGateway {

        /**
         * 创建测试专用网关。
         *
         * @param llmProperties LLM 配置
         * @param structuredEventLogger 结构化事件日志器
         */
        private StubLlmGateway(LlmProperties llmProperties, StructuredEventLogger structuredEventLogger) {
            super(new NoOpLlmClient(), new NoOpLlmClient(), new NoOpRedisKeyValueStore(), llmProperties, structuredEventLogger);
        }

        /**
         * 执行测试专用编译调用。
         *
         * @param purpose 调用用途
         * @param systemPrompt 系统提示词
         * @param userPrompt 用户提示词
         * @return 固定测试结果
         */
        @Override
        public String compile(String purpose, String systemPrompt, String userPrompt) {
            if ("cross-validate".equals(purpose)) {
                return "{\"supported\":false,\"evidence\":\"test stub\"}";
            }
            if ("check-propagation".equals(purpose)) {
                return "{\"affected\":false,\"reason\":\"test stub\"}";
            }
            if ("apply-correction".equals(purpose)) {
                return extractAfter(userPrompt, "\n原始文章：\n");
            }
            if ("apply-propagation".equals(purpose)) {
                return extractAfter(userPrompt, "\n下游文章原文：\n");
            }
            if ("review-fix".equals(purpose)) {
                return extractBetween(userPrompt, "\n原始文章:\n", "\n\n源文件参考:\n");
            }
            return "";
        }

        /**
         * 执行测试专用审查调用。
         *
         * @param purpose 调用用途
         * @param systemPrompt 系统提示词
         * @param userPrompt 用户提示词
         * @return 固定通过结果
         */
        @Override
        public String review(String purpose, String systemPrompt, String userPrompt) {
            return "{\"passed\":true,\"issues\":[]}";
        }

        /**
         * 提取指定标记之后的内容。
         *
         * @param content 原始文本
         * @param marker 标记
         * @return 标记后的内容
         */
        private String extractAfter(String content, String marker) {
            int markerIndex = content.indexOf(marker);
            if (markerIndex < 0) {
                return "";
            }
            return content.substring(markerIndex + marker.length()).trim();
        }

        /**
         * 提取两个标记之间的内容。
         *
         * @param content 原始文本
         * @param startMarker 起始标记
         * @param endMarker 结束标记
         * @return 区间内容
         */
        private String extractBetween(String content, String startMarker, String endMarker) {
            int startIndex = content.indexOf(startMarker);
            if (startIndex < 0) {
                return "";
            }
            String tail = content.substring(startIndex + startMarker.length());
            int endIndex = tail.indexOf(endMarker);
            if (endIndex < 0) {
                return tail.trim();
            }
            return tail.substring(0, endIndex).trim();
        }
    }

    /**
     * 空操作 LLM 客户端。
     *
     * 职责：满足父类构造器签名，不参与真实调用
     *
     * @author xiexu
     */
    private static class NoOpLlmClient implements LlmClient {

        /**
         * 返回固定空结果。
         *
         * @param systemPrompt 系统提示词
         * @param userPrompt 用户提示词
         * @return 空调用结果
         */
        @Override
        public LlmCallResult call(String systemPrompt, String userPrompt) {
            return new LlmCallResult("", 0, 0);
        }
    }

    /**
     * 空操作 Redis 键值存储。
     *
     * 职责：避免测试 LLM 网关依赖真实 Redis
     *
     * @author xiexu
     */
    private static class NoOpRedisKeyValueStore implements RedisKeyValueStore {

        /**
         * 返回空缓存值。
         *
         * @param key 缓存键
         * @return 空缓存值
         */
        @Override
        public String get(String key) {
            return null;
        }

        /**
         * 忽略缓存写入。
         *
         * @param key 缓存键
         * @param value 缓存值
         * @param ttl 过期时间
         */
        @Override
        public void set(String key, String value, Duration ttl) {
            // 测试环境无需缓存写入。
        }

        /**
         * 返回空 TTL。
         *
         * @param key 缓存键
         * @return 空 TTL
         */
        @Override
        public Long getExpire(String key) {
            return null;
        }

        @Override
        public void deleteByPrefix(String keyPrefix) {
        }
    }
}
