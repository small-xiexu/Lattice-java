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
                  "answerMarkdown":"# Payment Answer\\n\\n- settle_window=45m [→ payment/context.md]\\n- refund-manual-review 需要人工复核 [→ [用户反馈]]",
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
                                QueryEvidenceType.GRAPH,
                                "payment-routing#RoutePlanner",
                                "图谱实体：payment.routing.RoutePlanner",
                                "实体=payment.routing.RoutePlanner；annotation=@RequestMapping；calls->PaymentService.plan",
                                "{\"entityId\":\"payment-routing#RoutePlanner\"}",
                                List.of("src/main/java/payment/RoutePlanner.java, lines 12-24"),
                                1.5
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
        assertThat(recordingLlmClient.getLastUserPrompt()).contains("GRAPH EVIDENCE");
        assertThat(recordingLlmClient.getLastUserPrompt()).contains("RoutePlanner");
        assertThat(recordingLlmClient.getLastUserPrompt()).contains("CONTRIBUTION EVIDENCE");
        assertThat(recordingLlmClient.getLastUserPrompt()).contains("refund-manual-review 表示退款请求进入人工复核队列");
    }

    /**
     * 验证无 citation 的旧式自由文本不会直接透传，而是回落到带引用的确定性 fallback。
     *
     * @throws Exception 反射构造异常
     */
    @Test
    void shouldFallbackToDeterministicMarkdownWhenLegacyTextOmitsCitations() throws Exception {
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

        assertThat(answerPayload.getAnswerMarkdown()).startsWith("# 查询回答");
        assertThat(answerPayload.getAnswerMarkdown()).contains("[→ payment/context.md]");
        assertThat(answerPayload.getAnswerOutcome()).isEqualTo(AnswerOutcome.SUCCESS);
        assertThat(answerPayload.getGenerationMode()).isEqualTo(GenerationMode.FALLBACK);
        assertThat(answerPayload.getModelExecutionStatus()).isEqualTo(ModelExecutionStatus.FAILED);
        assertThat(answerPayload.isAnswerCacheable()).isFalse();
    }

    /**
     * 验证结构化 JSON 若缺少 citation，会被判为无效并回落到带引用的 deterministic fallback。
     *
     * @throws Exception 反射构造异常
     */
    @Test
    void shouldFallbackWhenStructuredAnswerOmitsCitations() throws Exception {
        RecordingLlmClient recordingLlmClient = new RecordingLlmClient("""
                {
                  "answerMarkdown":"当前文档存在冲突，更倾向于乐观锁，但暂不能排除 Redis 锁。",
                  "answerOutcome":"PARTIAL_ANSWER",
                  "answerCacheable":false
                }
                """);
        AnswerGenerationService answerGenerationService = new AnswerGenerationService(
                createGatewayFixture(recordingLlmClient).getLlmGateway()
        );

        QueryAnswerPayload answerPayload = answerGenerationService.generatePayload(
                "库存并发到底是乐观锁还是 Redis 锁",
                buildConflictEvidence()
        );

        assertThat(answerPayload.getAnswerOutcome()).isEqualTo(AnswerOutcome.PARTIAL_ANSWER);
        assertThat(answerPayload.getGenerationMode()).isEqualTo(GenerationMode.FALLBACK);
        assertThat(answerPayload.getModelExecutionStatus()).isEqualTo(ModelExecutionStatus.FAILED);
        assertThat(answerPayload.getAnswerMarkdown()).contains("## 结论");
        assertThat(answerPayload.getAnswerMarkdown()).contains("## 参考说明");
        assertThat(answerPayload.getAnswerMarkdown()).contains("[→ conflict-lock.md]");
    }

    /**
     * 验证冲突题 deterministic fallback 会先给结论，再说明证据冲突与倾向。
     *
     * @throws Exception 反射构造异常
     */
    @Test
    void shouldGenerateComparisonConclusionForConflictFallback() throws Exception {
        RecordingLlmClient recordingLlmClient = new RecordingLlmClient("""
                {
                  "answerMarkdown":"当前文档存在冲突，更倾向于乐观锁，但暂不能排除 Redis 锁。",
                  "answerOutcome":"PARTIAL_ANSWER",
                  "answerCacheable":false
                }
                """);
        AnswerGenerationService answerGenerationService = new AnswerGenerationService(
                createGatewayFixture(recordingLlmClient).getLlmGateway()
        );

        QueryAnswerPayload answerPayload = answerGenerationService.generatePayload(
                "库存并发到底是乐观锁还是 Redis 锁",
                buildConflictEvidence()
        );

        assertThat(answerPayload.getGenerationMode()).isEqualTo(GenerationMode.FALLBACK);
        assertThat(answerPayload.getAnswerMarkdown()).contains("支持“乐观锁”的材料提到：");
        assertThat(answerPayload.getAnswerMarkdown()).contains("支持“Redis 锁”的材料提到：");
        assertThat(answerPayload.getAnswerMarkdown()).contains("更偏向“乐观锁”");
        assertThat(answerPayload.getAnswerMarkdown()).contains("inventory_lock_version");
        assertThat(answerPayload.getAnswerMarkdown()).contains("Redis 分布式锁");
        assertThat(answerPayload.getAnswerMarkdown()).contains("口径还没有完全收敛");
        assertThat(answerPayload.getAnswerMarkdown()).doesNotContain("本条目汇总");
        assertThat(answerPayload.getAnswerMarkdown()).doesNotContain("主要记录了");
        assertThat(answerPayload.getAnswerMarkdown()).doesNotContain("显式提示冲突");
        assertThat(answerPayload.getAnswerMarkdown()).doesNotContain("。；");
        assertThat(answerPayload.getAnswerMarkdown()).doesNotContain("。。");
        assertThat(answerPayload.getAnswerMarkdown()).contains("[[readme]]");
        assertThat(answerPayload.getAnswerMarkdown()).contains("[[conflict-lock]]");
        assertThat(answerPayload.getAnswerMarkdown()).contains("## 参考说明");
    }

    /**
     * 验证单 article 规则路径在能直接命中配置结论时，也会返回 SUCCESS 而不是固定 partial。
     */
    @Test
    void shouldMarkSingleArticleDirectAnswerAsSuccess() {
        AnswerGenerationService answerGenerationService = new AnswerGenerationService();

        QueryAnswerPayload answerPayload = answerGenerationService.generatePayload(
                "payment timeout retry 是什么配置",
                List.of(new QueryArticleHit(
                        QueryEvidenceType.ARTICLE,
                        "payment-timeout",
                        "Payment Timeout",
                        "payment.timeout.retry=5\nretry.queue=payment_retry_queue",
                        "{\"description\":\"支付超时重试配置\"}",
                        List.of("payments/gateway-config.yaml"),
                        3.0D
                ))
        );

        assertThat(answerPayload.getAnswerOutcome()).isEqualTo(AnswerOutcome.SUCCESS);
        assertThat(answerPayload.getGenerationMode()).isEqualTo(GenerationMode.RULE_BASED);
        assertThat(answerPayload.getModelExecutionStatus()).isEqualTo(ModelExecutionStatus.SKIPPED);
        assertThat(answerPayload.getAnswerMarkdown()).contains("payment.timeout.retry=5");
    }

    /**
     * 验证模型失败时，配置题 deterministic fallback 会忽略 frontmatter 元数据，并优先回答正文表格里的精确配置值。
     *
     * @throws Exception 反射构造异常
     */
    @Test
    void shouldPreferBodyConfigFactsWhenFallbackAnsweringConfigurationQuestion() throws Exception {
        RecordingLlmClient recordingLlmClient = new RecordingLlmClient("not-json-without-citation");
        AnswerGenerationService answerGenerationService = new AnswerGenerationService(
                createGatewayFixture(recordingLlmClient).getLlmGateway()
        );

        QueryAnswerPayload answerPayload = answerGenerationService.generatePayload(
                "payment timeout retry 是什么配置",
                List.of(
                        new QueryArticleHit(
                                QueryEvidenceType.ARTICLE,
                                "runbooks",
                                "Runbooks",
                                """
                                        ---
                                        title: "Runbooks"
                                        related: ["payments", "ops"]
                                        review_status: passed
                                        ---

                                        # Runbooks

                                        | 序号 | 检查项 | 说明 |
                                        |---|---|---|
                                        | 1 | `payment.timeout.retry=3` | 确认该配置值。 |
                                        | 2 | PSP 响应时间是否持续高于 `2` 秒 | 查看上游 RT。 |
                                        """,
                                "{\"description\":\"支付排障 runbook\"}",
                                List.of("runbooks/retry-troubleshooting.md"),
                                3.0D
                        ),
                        new QueryArticleHit(
                                QueryEvidenceType.ARTICLE,
                                "payments",
                                "Payments",
                                """
                                        ---
                                        title: "Payments"
                                        referential_keywords: ["payment.timeout.retry", "3"]
                                        ---

                                        # Payments

                                        | 配置键 | 精确值 | 说明 |
                                        |---|---:|---|
                                        | `payment.timeout.retry` | `3` | 超时场景下的重试次数配置。 |
                                        """,
                                "{\"description\":\"支付配置\"}",
                                List.of("payments/gateway-config.yaml"),
                                2.5D
                        )
                )
        );

        assertThat(answerPayload.getGenerationMode()).isEqualTo(GenerationMode.FALLBACK);
        assertThat(answerPayload.getModelExecutionStatus()).isEqualTo(ModelExecutionStatus.FAILED);
        assertThat(answerPayload.getAnswerMarkdown()).contains("payment.timeout.retry = 3");
        assertThat(answerPayload.getAnswerMarkdown()).contains("超时场景下的重试次数配置");
        assertThat(answerPayload.getAnswerMarkdown()).doesNotContain("related: [\"payments\", \"ops\"]");
        assertThat(answerPayload.getAnswerMarkdown()).doesNotContain("review_status: passed");
    }

    /**
     * 验证真实风格 Payments 文章在 fallback 里也会优先回答配置键和值，而不是返回概述句。
     *
     * @throws Exception 反射构造异常
     */
    @Test
    void shouldPreferExactConfigValueOverOverviewWhenOnlyPaymentsArticleMatches() throws Exception {
        RecordingLlmClient recordingLlmClient = new RecordingLlmClient("not-json-without-citation");
        AnswerGenerationService answerGenerationService = new AnswerGenerationService(
                createGatewayFixture(recordingLlmClient).getLlmGateway()
        );

        QueryAnswerPayload answerPayload = answerGenerationService.generatePayload(
                "payment timeout retry 是什么配置",
                List.of(
                        new QueryArticleHit(
                                QueryEvidenceType.ARTICLE,
                                null,
                                "legacy-default--payments",
                                "payments",
                                "Payments",
                                """
                                        ---
                                        title: "Payments"
                                        summary: "[编译] 现有资料主要包含两部分：`PaymentRetryPolicy` Java 类，以及 `payments/gateway-config.yaml` 配置文件。"
                                        referential_keywords: ["payment.timeout.retry", "3"]
                                        ---

                                        # Payments

                                        [编译] 现有资料主要包含两部分：`PaymentRetryPolicy` Java 类，以及 `payments/gateway-config.yaml` 配置文件。

                                        | 配置键 | 精确值 | 说明 |
                                        |---|---:|---|
                                        | `payment.timeout.retry` | `3` | 支付超时相关的重试次数配置。 |
                                        | `payment.circuit-breaker.failure-rate-threshold` | `55` | 断路器失败率阈值。 |

                                        ## 代码常量与配置的对应关系

                                        代码中的 `MAX_RETRY = 3` 与配置中的 `payment.timeout.retry = 3` 数值一致。
                                        """,
                                "{\"description\":\"支付配置\"}",
                                List.of("payments/gateway-config.yaml"),
                                3.0D
                        )
                )
        );

        assertThat(answerPayload.getGenerationMode()).isEqualTo(GenerationMode.RULE_BASED);
        assertThat(answerPayload.getModelExecutionStatus()).isEqualTo(ModelExecutionStatus.SKIPPED);
        assertThat(answerPayload.getAnswerMarkdown()).contains("payment.timeout.retry = 3");
        assertThat(answerPayload.getAnswerMarkdown()).contains("支付超时相关的重试次数配置");
        assertThat(answerPayload.getAnswerMarkdown()).doesNotContain("现有资料主要包含两部分");
        assertThat(answerPayload.getAnswerMarkdown()).doesNotContain("MAX_RETRY");
    }

    /**
     * 验证“分别是多少”这类题目会优先返回同一篇文章里的多个精确值，而不是被旧配置概述带偏。
     *
     * @throws Exception 反射构造异常
     */
    @Test
    void shouldReturnMultipleCurrentValuesForStructuredFactQuestion() throws Exception {
        RecordingLlmClient recordingLlmClient = new RecordingLlmClient("not-json-without-citation");
        AnswerGenerationService answerGenerationService = new AnswerGenerationService(
                createGatewayFixture(recordingLlmClient).getLlmGateway()
        );

        QueryAnswerPayload answerPayload = answerGenerationService.generatePayload(
                "failure-rate-threshold 和观察窗口现在分别是多少",
                List.of(
                        new QueryArticleHit(
                                QueryEvidenceType.ARTICLE,
                                null,
                                "legacy-default--payments",
                                "payments",
                                "Payments",
                                """
                                        ---
                                        title: "Payments"
                                        summary: "旧网关配置仍保留在 payments 文档中。"
                                        ---

                                        # Payments

                                        | 配置键 | 精确值 | 说明 |
                                        |---|---:|---|
                                        | `payment.circuit-breaker.failure-rate-threshold` | `55` | 断路器失败率阈值。 |
                                        | `payment.circuit-breaker.observation-window-minutes` | `15` | 断路器观察窗口。 |
                                        """,
                                "{\"description\":\"旧支付配置\"}",
                                List.of("payments/gateway-config.yaml"),
                                2.0D
                        ),
                        new QueryArticleHit(
                                QueryEvidenceType.ARTICLE,
                                null,
                                "legacy-default--ops",
                                "ops",
                                "Ops",
                                """
                                        ---
                                        title: "Ops"
                                        summary: "Ops 在这里主要指围绕支付超时问题的运行时观察与事故复盘知识，重点关注熔断阈值、观察窗口以及异常现象的联动关系。"
                                        referential_keywords: ["failure-rate-threshold", "50", "20 分钟"]
                                        ---

                                        # Ops

                                        ## 概述

                                        Ops 主要聚焦于支付超时相关的运行时观察与事故复盘知识。

                                        | 标识符 | 值 | 含义 |
                                        |---|---:|---|
                                        | `failure-rate-threshold` | `50` | 当前熔断阈值建议值 |
                                        | 观察窗口 | `20 分钟` | 当前建议的观察窗口长度 |

                                        ## 局限与注意事项

                                        - `failure-rate-threshold = 50` 应视为当前建议值。
                                        - `20 分钟` 观察窗口是建议配置，但来源未展开其适用条件。
                                        """,
                                "{\"description\":\"运行时观察与事故复盘\"}",
                                List.of("ops/postmortem.md"),
                                3.0D
                        )
                )
        );

        assertThat(answerPayload.getGenerationMode()).isEqualTo(GenerationMode.FALLBACK);
        assertThat(answerPayload.getAnswerMarkdown()).contains("failure-rate-threshold = 50");
        assertThat(answerPayload.getAnswerMarkdown()).contains("观察窗口 = 20 分钟");
        assertThat(answerPayload.getAnswerMarkdown()).doesNotContain("应视为");
        assertThat(answerPayload.getAnswerMarkdown()).doesNotContain("15");
        assertThat(answerPayload.getAnswerMarkdown()).doesNotContain("55");
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
                  "answerMarkdown":"# Stable Answer\\n\\n- settle_window=45m [→ payment/context.md]",
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
                  "answerMarkdown":"# Rewritten Answer\\n\\n- timeout=30s [→ payment/context.md]",
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
     * 验证 review rewrite 若返回无 citation 的普通 Markdown，也会回落到带引用的确定性 fallback。
     *
     * @throws Exception 反射构造异常
     */
    @Test
    void shouldFallbackRewriteWhenLegacyMarkdownOmitsCitations() throws Exception {
        RecordingLlmClient recordingLlmClient = new RecordingLlmClient("""
                # Rewritten Answer

                - timeout=30s
                """);
        AnswerGenerationService answerGenerationService = new AnswerGenerationService(
                createGatewayFixture(recordingLlmClient).getLlmGateway()
        );

        QueryAnswerPayload answerPayload = answerGenerationService.rewriteFromReviewPayload(
                "query-1",
                "query",
                "rewrite",
                "timeout 配置是多少",
                "旧答案",
                "请补齐 timeout 的明确值",
                buildMultiRouteEvidence()
        );

        assertThat(answerPayload.getAnswerMarkdown()).startsWith("# 查询回答");
        assertThat(answerPayload.getAnswerMarkdown()).contains("当前未找到与该问题直接相关的知识。");
        assertThat(answerPayload.getAnswerMarkdown()).doesNotContain("[→");
        assertThat(answerPayload.getAnswerOutcome()).isEqualTo(AnswerOutcome.PARTIAL_ANSWER);
        assertThat(answerPayload.getGenerationMode()).isEqualTo(GenerationMode.FALLBACK);
        assertThat(answerPayload.getModelExecutionStatus()).isEqualTo(ModelExecutionStatus.FAILED);
        assertThat(answerPayload.isAnswerCacheable()).isFalse();
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
     * 验证 fallback 会优先保留直接命中的 RoutePlanner 资料，而不是把仅在正文顺带提及的笔记也混进来。
     */
    @Test
    void shouldPreferDirectRoutePlannerEvidenceInFallback() {
        AnswerGenerationService answerGenerationService = new AnswerGenerationService();

        QueryAnswerPayload answerPayload = answerGenerationService.generatePayload(
                "RoutePlanner 暴露了什么路径",
                buildRoutePlannerEvidence()
        );

        assertThat(answerPayload.getAnswerOutcome()).isEqualTo(AnswerOutcome.SUCCESS);
        assertThat(answerPayload.getGenerationMode()).isEqualTo(GenerationMode.RULE_BASED);
        assertThat(answerPayload.getModelExecutionStatus()).isEqualTo(ModelExecutionStatus.SKIPPED);
        assertThat(answerPayload.getAnswerMarkdown()).contains("RoutePlanner 负责暴露 `/payments` 路径");
        assertThat(answerPayload.getAnswerMarkdown()).contains("[[routeplanner]]");
        assertThat(answerPayload.getAnswerMarkdown()).doesNotContain("Research Notes");
        assertThat(answerPayload.getAnswerMarkdown()).doesNotContain("[[research-notes]]");
    }

    /**
     * 验证模型失败后，deterministic fallback 仍会按证据充分度返回 SUCCESS，而不是固定 partial。
     *
     * @throws Exception 反射构造异常
     */
    @Test
    void shouldReturnSuccessWhenModelFailsButFallbackEvidenceIsDirect() throws Exception {
        RecordingLlmClient recordingLlmClient = new RecordingLlmClient("""
                RoutePlanner 暴露了 `/payments` 路径
                """);
        AnswerGenerationService answerGenerationService = new AnswerGenerationService(
                createGatewayFixture(recordingLlmClient).getLlmGateway()
        );

        QueryAnswerPayload answerPayload = answerGenerationService.generatePayload(
                "RoutePlanner 暴露了什么路径",
                buildRoutePlannerEvidence()
        );

        assertThat(answerPayload.getAnswerOutcome()).isEqualTo(AnswerOutcome.SUCCESS);
        assertThat(answerPayload.getGenerationMode()).isEqualTo(GenerationMode.FALLBACK);
        assertThat(answerPayload.getModelExecutionStatus()).isEqualTo(ModelExecutionStatus.FAILED);
        assertThat(answerPayload.getAnswerMarkdown()).contains("RoutePlanner 负责暴露 `/payments` 路径");
        assertThat(answerPayload.getAnswerMarkdown()).contains("[[routeplanner]]");
    }

    /**
     * 验证 no-knowledge fallback 在没有直接相关命中时，不会再夹带无关引用或参考区块。
     */
    @Test
    void shouldBuildCleanNoKnowledgeFallbackWhenHitsAreIrrelevant() {
        AnswerGenerationService answerGenerationService = new AnswerGenerationService();

        QueryAnswerPayload answerPayload = answerGenerationService.fallbackPayload(
                "这个项目支持 SAML 单点登录吗",
                buildIrrelevantFallbackEvidence(),
                AnswerOutcome.NO_RELEVANT_KNOWLEDGE
        );

        assertThat(answerPayload.getAnswerOutcome()).isEqualTo(AnswerOutcome.NO_RELEVANT_KNOWLEDGE);
        assertThat(answerPayload.getAnswerMarkdown()).contains("当前未找到与该问题直接相关的知识。");
        assertThat(answerPayload.getAnswerMarkdown()).doesNotContain("## 参考说明");
        assertThat(answerPayload.getAnswerMarkdown()).doesNotContain("[[");
        assertThat(answerPayload.getAnswerMarkdown()).doesNotContain("[→");
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
                        QueryEvidenceType.GRAPH,
                        "payment-routing#RoutePlanner",
                        "图谱实体：payment.routing.RoutePlanner",
                        "实体=payment.routing.RoutePlanner；annotation=@RequestMapping；calls->PaymentService.plan",
                        "{\"entityId\":\"payment-routing#RoutePlanner\"}",
                        List.of("src/main/java/payment/RoutePlanner.java, lines 12-24"),
                        1.5
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
     * 构造库存并发冲突证据样例。
     *
     * @return 冲突证据样例
     */
    private List<QueryArticleHit> buildConflictEvidence() {
        return List.of(
                new QueryArticleHit(
                        1L,
                        "readme",
                        "readme",
                        "Readme",
                        """
                        ---
                        title: "Readme"
                        summary: "本条目汇总 README 中关于支付超时重试、库存扣减并发控制以及补偿失败后的异步重试机制。它同时指出了一个文档规则：如果库存并发策略相关文档之间存在冲突，回答时需要显式提示冲突。"
                        sources: ["README.md"]
                        depends_on: []
                        ---

                        # Readme

                        ## 概要

                        README 记录了支付域中的几项关键实现约定：支付超时重试通过配置项 `payment.timeout.retry` 控制，默认值为 `5`。[→ README.md, section: Payment Knowledge]
                        在库存扣减场景中，系统采用基于版本号校验的乐观锁（optimistic locking，乐观锁）策略，使用字段 `inventory_lock_version`。[→ README.md, section: Payment Knowledge]
                        """,
                        "{\"description\":\"\"}",
                        List.of("README.md"),
                        3.0D
                ),
                new QueryArticleHit(
                        2L,
                        "conflict-lock",
                        "conflict-lock",
                        "Conflict Lock",
                        """
                        ---
                        title: "Conflict Lock"
                        summary: "Conflict Lock 指库存并发控制中的一种冲突规避机制：通过 Redis 分布式锁将相关操作串行化处理，以避免并发扣减产生冲突。它本质上是一种面向库存修改场景的并发控制手段，用于保证同一资源在高并发下的更新一致性。"
                        sources: ["conflict-lock.md"]
                        depends_on: []
                        ---

                        # Conflict Lock

                        ## 概述

                        Conflict Lock（冲突锁）在该上下文中指库存场景下的并发冲突控制机制，其实现方式是使用 Redis distributed lock（Redis 分布式锁）对操作进行串行化处理。[→ conflict-lock.md, Inventory Lock Conflict]
                        """,
                        "{\"description\":\"\"}",
                        List.of("conflict-lock.md"),
                        2.0D
                ),
                new QueryArticleHit(
                        3L,
                        "research-notes",
                        "research-notes",
                        "Research Notes",
                        """
                        ---
                        title: "Research Notes"
                        summary: "Research Notes 记录了若干与库存扣减、补偿重试及文档冲突处理相关的关键规则。其中包括库存乐观锁字段、失败补偿后的异步重试机制，以及在锁策略文档不一致时的回答要求。"
                        sources: ["research-notes.md"]
                        depends_on: []
                        ---

                        # Research Notes

                        ## 概述

                        这份 Research Notes 主要记录了三个关键点：库存扣减使用基于版本号校验的乐观锁、补偿失败后进入指定队列进行异步重试、以及当库存锁策略文档彼此不一致时，回答必须明确指出存在冲突。[→ research-notes.md, Research Notes]

                        ## 库存扣减中的乐观锁

                        库存扣减采用基于版本号校验的乐观锁（optimistic locking）机制，使用的字段是 `inventory_lock_version`。[→ research-notes.md, Inventory Locking]
                        """,
                        "{\"description\":\"\"}",
                        List.of("research-notes.md"),
                        1.5D
                )
        );
    }

    /**
     * 构造 RoutePlanner fallback 证据样例。
     *
     * @return RoutePlanner 证据样例
     */
    private List<QueryArticleHit> buildRoutePlannerEvidence() {
        return List.of(
                new QueryArticleHit(
                        1L,
                        "routeplanner",
                        "routeplanner",
                        "RoutePlanner",
                        """
                        ---
                        title: "RoutePlanner"
                        summary: "RoutePlanner 是支付入口控制器，负责对外暴露 `/payments` 路径。"
                        ---

                        RoutePlanner 负责暴露 `/payments` 路径，并把请求委托给 PaymentService。
                        """,
                        "{\"description\":\"RoutePlanner 是支付入口控制器\"}",
                        List.of("src/main/java/payment/RoutePlanner.java"),
                        3.0D
                ),
                new QueryArticleHit(
                        2L,
                        "research-notes",
                        "research-notes",
                        "Research Notes",
                        """
                        研究笔记顺带提到：RoutePlanner 会把支付请求转给 PaymentService，再由后者决定后续流程。
                        """,
                        "{\"description\":\"与支付实现相关的补充笔记\"}",
                        List.of("research-notes.md"),
                        1.0D
                )
        );
    }

    /**
     * 构造与 SAML 无关的 fallback 证据样例。
     *
     * @return 无关证据样例
     */
    private List<QueryArticleHit> buildIrrelevantFallbackEvidence() {
        return List.of(
                new QueryArticleHit(
                        1L,
                        "readme",
                        "readme",
                        "Readme",
                        "项目支持库存并发控制、支付超时重试与补偿任务重放。",
                        "{\"description\":\"项目能力概览\"}",
                        List.of("README.md"),
                        2.0D
                ),
                new QueryArticleHit(
                        2L,
                        "routeplanner",
                        "routeplanner",
                        "RoutePlanner",
                        "RoutePlanner 暴露 `/payments` 路径。",
                        "{\"description\":\"支付入口控制器\"}",
                        List.of("src/main/java/payment/RoutePlanner.java"),
                        1.0D
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
