package com.xbk.lattice.compiler.service;

import com.xbk.lattice.compiler.config.LlmProperties;
import com.xbk.lattice.compiler.domain.AnalyzedConcept;
import com.xbk.lattice.compiler.domain.ConceptSection;
import com.xbk.lattice.compiler.domain.RawSource;
import com.xbk.lattice.compiler.domain.SourceBatch;
import com.xbk.lattice.compiler.node.AnalyzeNode;
import com.xbk.lattice.llm.service.LlmCallResult;
import com.xbk.lattice.llm.service.LlmClient;
import com.xbk.lattice.query.service.RedisKeyValueStore;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AnalyzeNode 测试
 *
 * 职责：验证最小分析节点输出稳定的标题、来源和摘要
 *
 * @author xiexu
 */
class AnalyzeNodeTests {

    /**
     * 验证分析节点会把带噪音的分组键收敛为稳定 conceptId。
     */
    @Test
    void shouldNormalizeNoisyGroupKeyIntoStableConceptId() {
        AnalyzeNode analyzeNode = new AnalyzeNode();
        List<SourceBatch> sourceBatches = Arrays.asList(
                new SourceBatch("batch-1", "  Payment__Service---Core  ", Arrays.asList(
                        RawSource.text("payment/a.md", "snippet-a", "md", 9L)
                ))
        );

        List<AnalyzedConcept> analyzedConcepts = analyzeNode.analyze("  Payment__Service---Core  ", sourceBatches);

        assertThat(analyzedConcepts).hasSize(1);
        assertThat(analyzedConcepts.get(0).getConceptId()).isEqualTo("payment-service-core");
        assertThat(analyzedConcepts.get(0).getTitle()).isEqualTo("Payment Service Core");
    }

    /**
     * 验证分析节点会清理空白片段，避免把噪音写入后续链路。
     */
    @Test
    void shouldTrimSnippetsAndDropBlankEntries() {
        AnalyzeNode analyzeNode = new AnalyzeNode();
        List<SourceBatch> sourceBatches = Arrays.asList(
                new SourceBatch("batch-1", "payment-service", Arrays.asList(
                        RawSource.text("payment/c.md", " refund-flow ", "md", 11L),
                        RawSource.text("payment/a.md", "   ", "md", 3L),
                        RawSource.text("payment/b.md", "\norder-flow\n", "md", 12L)
                ))
        );

        List<AnalyzedConcept> analyzedConcepts = analyzeNode.analyze("payment-service", sourceBatches);

        assertThat(analyzedConcepts).hasSize(1);
        assertThat(analyzedConcepts.get(0).getSnippets()).containsExactly("order-flow", "refund-flow");
    }

    /**
     * 验证分析节点会优先采用结构化 JSON 中的概念结果，而不是退回分组级单概念。
     */
    @Test
    void shouldPreferStructuredJsonConceptsWhenPayloadIsValid() {
        AnalyzeNode analyzeNode = new AnalyzeNode();
        String jsonPayload = "{"
                + "\"concepts\":["
                + "{\"id\":\"payment_timeout\",\"title\":\"Payment Timeout\",\"description\":\" Handles payment timeout recovery \","
                + "\"snippets\":[\" order timeout \",\"refund timeout\"],"
                + "\"sections\":[{\"heading\":\" Timeout Rules \",\"content\":[\" retry=3 \",\"interval=30s\"],\"sources\":[\"payment/rules.md#timeout-rules\"]}]},"
                + "{\"id\":\"payment-channel\",\"title\":\"Payment Channel\",\"description\":\"Routes payment through enabled channels\","
                + "\"snippets\":[\"wechat-pay\",\"   \"],"
                + "\"sections\":[{\"heading\":\"Available Channels\",\"content\":[\"wechat\",\"alipay\"]}]}"
                + "]"
                + "}";
        List<SourceBatch> sourceBatches = Arrays.asList(
                new SourceBatch("batch-1", "payment-service", Arrays.asList(
                        RawSource.text("payment/analyze.json", jsonPayload, "json", 128L)
                ))
        );

        List<AnalyzedConcept> analyzedConcepts = analyzeNode.analyze("payment-service", sourceBatches);

        assertThat(analyzedConcepts).hasSize(2);
        assertThat(analyzedConcepts.get(0).getConceptId()).isEqualTo("payment-timeout");
        assertThat(analyzedConcepts.get(0).getTitle()).isEqualTo("Payment Timeout");
        assertThat(analyzedConcepts.get(0).getDescription()).isEqualTo("Handles payment timeout recovery");
        assertThat(analyzedConcepts.get(0).getSourcePaths()).containsExactly("payment/analyze.json");
        assertThat(analyzedConcepts.get(0).getSnippets()).containsExactly("order timeout", "refund timeout");
        assertThat(analyzedConcepts.get(0).getSections()).containsExactly(
                new ConceptSection(
                        "Timeout Rules",
                        Arrays.asList("retry=3", "interval=30s"),
                        Arrays.asList("payment/rules.md#timeout-rules")
                )
        );
        assertThat(analyzedConcepts.get(1).getConceptId()).isEqualTo("payment-channel");
        assertThat(analyzedConcepts.get(1).getTitle()).isEqualTo("Payment Channel");
        assertThat(analyzedConcepts.get(1).getDescription()).isEqualTo("Routes payment through enabled channels");
        assertThat(analyzedConcepts.get(1).getSourcePaths()).containsExactly("payment/analyze.json");
        assertThat(analyzedConcepts.get(1).getSnippets()).containsExactly("wechat-pay");
        assertThat(analyzedConcepts.get(1).getSections()).containsExactly(
                new ConceptSection(
                        "Available Channels",
                        Arrays.asList("wechat", "alipay"),
                        Arrays.asList("payment/analyze.json#Available Channels")
                )
        );
    }

    /**
     * 验证分析节点会从截断 JSON 中抢救出已完整输出的概念。
     */
    @Test
    void shouldSalvageCompletedConceptsFromTruncatedJsonPayload() {
        AnalyzeNode analyzeNode = new AnalyzeNode();
        String truncatedJsonPayload = "{"
                + "\"concepts\":["
                + "{\"id\":\"payment-timeout\",\"title\":\"Payment Timeout\",\"description\":\"Timeout description\",\"snippets\":[\"timeout-a\"]},"
                + "{\"id\":\"refund-status\",\"title\":\"Refund Status\",\"description\":\"Refund state machine\",\"snippets\":[\"refund-created\",\"refund-paid\"]},"
                + "{\"id\":\"broken\",\"title\":\"Broken";
        List<SourceBatch> sourceBatches = Arrays.asList(
                new SourceBatch("batch-1", "payment-service", Arrays.asList(
                        RawSource.text("payment/truncated.json", truncatedJsonPayload, "json", 128L)
                ))
        );

        List<AnalyzedConcept> analyzedConcepts = analyzeNode.analyze("payment-service", sourceBatches);

        assertThat(analyzedConcepts).hasSize(2);
        assertThat(analyzedConcepts.get(0).getConceptId()).isEqualTo("payment-timeout");
        assertThat(analyzedConcepts.get(0).getTitle()).isEqualTo("Payment Timeout");
        assertThat(analyzedConcepts.get(0).getDescription()).isEqualTo("Timeout description");
        assertThat(analyzedConcepts.get(0).getSnippets()).containsExactly("timeout-a");
        assertThat(analyzedConcepts.get(1).getConceptId()).isEqualTo("refund-status");
        assertThat(analyzedConcepts.get(1).getTitle()).isEqualTo("Refund Status");
        assertThat(analyzedConcepts.get(1).getDescription()).isEqualTo("Refund state machine");
        assertThat(analyzedConcepts.get(1).getSnippets()).containsExactly("refund-created", "refund-paid");
    }

    /**
     * 验证分析节点会为每个批次生成概念，并保留来源与片段。
     */
    @Test
    void shouldAnalyzeBatchesIntoStableConcepts() {
        AnalyzeNode analyzeNode = new AnalyzeNode();
        List<SourceBatch> sourceBatches = Arrays.asList(
                new SourceBatch("batch-1", "payment-service", Arrays.asList(
                        RawSource.text("payment/a.md", "snippet-a", "md", 9L),
                        RawSource.text("payment/b.md", "snippet-b", "md", 9L)
                ))
        );

        List<AnalyzedConcept> analyzedConcepts = analyzeNode.analyze("payment-service", sourceBatches);

        assertThat(analyzedConcepts).hasSize(1);
        assertThat(analyzedConcepts.get(0).getConceptId()).isEqualTo("payment-service");
        assertThat(analyzedConcepts.get(0).getTitle()).isEqualTo("Payment Service");
        assertThat(analyzedConcepts.get(0).getSourcePaths()).containsExactly("payment/a.md", "payment/b.md");
        assertThat(analyzedConcepts.get(0).getSnippets()).containsExactly("snippet-a", "snippet-b");
        assertThat(analyzedConcepts.get(0).getSections()).isEmpty();
    }

    /**
     * 验证分析节点在缺少结构化 JSON 时会调用 LLM，并解析代码块中的概念结果。
     */
    @Test
    void shouldUseLlmGatewayWhenStructuredPayloadIsAbsent() {
        LlmGateway llmGateway = new LlmGateway(
                new StaticLlmClient("""
                        ```json
                        {
                          "concepts":[
                            {
                              "id":"payment-timeout",
                              "title":"支付超时",
                              "description":"处理支付超时后的恢复策略",
                              "sources":[{"path":"payment/order.md","location":"Page 1"}],
                              "relationships":[]
                            }
                          ],
                          "controversies":[],
                          "gaps":[]
                        }
                        ```
                        """),
                new StaticLlmClient("{}"),
                new NoopRedisKeyValueStore(),
                createLlmProperties()
        );
        AnalyzeNode analyzeNode = new AnalyzeNode(llmGateway);
        List<SourceBatch> sourceBatches = Arrays.asList(
                new SourceBatch("batch-1", "payment-service", Arrays.asList(
                        RawSource.text("payment/order.md", "timeout retry rule", "md", 18L)
                ))
        );

        List<AnalyzedConcept> analyzedConcepts = analyzeNode.analyze("payment-service", sourceBatches);

        assertThat(analyzedConcepts).hasSize(1);
        assertThat(analyzedConcepts.get(0).getConceptId()).isEqualTo("payment-timeout");
        assertThat(analyzedConcepts.get(0).getTitle()).isEqualTo("支付超时");
        assertThat(analyzedConcepts.get(0).getDescription()).isEqualTo("处理支付超时后的恢复策略");
        assertThat(analyzedConcepts.get(0).getSourcePaths()).containsExactly("payment/order.md");
    }

    /**
     * 验证 LLM 返回无法解析时，分析节点会回退为最小概念。
     */
    @Test
    void shouldFallbackToMinimalConceptWhenLlmResultIsUnparseable() {
        LlmGateway llmGateway = new LlmGateway(
                new StaticLlmClient("not-json-response"),
                new StaticLlmClient("{}"),
                new NoopRedisKeyValueStore(),
                createLlmProperties()
        );
        AnalyzeNode analyzeNode = new AnalyzeNode(llmGateway);
        List<SourceBatch> sourceBatches = Arrays.asList(
                new SourceBatch("batch-1", "payment-service", Arrays.asList(
                        RawSource.text("payment/a.md", "snippet-a", "md", 9L),
                        RawSource.text("payment/b.md", "snippet-b", "md", 9L)
                ))
        );

        List<AnalyzedConcept> analyzedConcepts = analyzeNode.analyze("payment-service", sourceBatches);

        assertThat(analyzedConcepts).hasSize(1);
        assertThat(analyzedConcepts.get(0).getConceptId()).isEqualTo("payment-service");
        assertThat(analyzedConcepts.get(0).getSnippets()).containsExactly("snippet-a", "snippet-b");
    }

    /**
     * 创建测试用 LLM 配置。
     *
     * @return LLM 配置
     */
    private LlmProperties createLlmProperties() {
        LlmProperties llmProperties = new LlmProperties();
        llmProperties.setCompileModel("openai");
        llmProperties.setReviewerModel("anthropic");
        llmProperties.setBudgetUsd(10.0D);
        llmProperties.setCacheTtlSeconds(3600L);
        llmProperties.setCacheKeyPrefix("llm:test:");
        return llmProperties;
    }

    /**
     * 固定返回结果的 LLM 客户端。
     *
     * @author xiexu
     */
    private static class StaticLlmClient implements LlmClient {

        private final String content;

        private StaticLlmClient(String content) {
            this.content = content;
        }

        @Override
        public LlmCallResult call(String systemPrompt, String userPrompt) {
            return new LlmCallResult(content, 100, 50);
        }
    }

    /**
     * 空操作 Redis 存储。
     *
     * @author xiexu
     */
    private static class NoopRedisKeyValueStore implements RedisKeyValueStore {

        @Override
        public String get(String key) {
            return null;
        }

        @Override
        public void set(String key, String value, java.time.Duration ttl) {
        }

        @Override
        public Long getExpire(String key) {
            return null;
        }
    }

}
