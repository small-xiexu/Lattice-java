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
        assertThat(recordingLlmClient.getLastSystemPrompt()).contains("精确标识类知识");
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
     * 验证精确查值题会把贴题证据句单独抬到 Prompt，避免模型在长文章里抓错焦点。
     *
     * @throws Exception 反射构造异常
     */
    @Test
    void shouldExposeQuestionFocusedEvidenceForExactLookupQuestion() throws Exception {
        RecordingLlmClient recordingLlmClient = new RecordingLlmClient("""
                {
                  "answerMarkdown":"scenarioCode=42 从无流量修正为有流量，30 天 scenarioCode 命中 248,000 条，channel=upstreamChannel 命中 502,000 条。[[scenario-summary]]",
                  "answerOutcome":"SUCCESS",
                  "answerCacheable":true
                }
                """);
        AnswerGenerationService answerGenerationService = new AnswerGenerationService(
                createGatewayFixture(recordingLlmClient).getLlmGateway()
        );

        QueryAnswerPayload answerPayload = answerGenerationService.generatePayload(
                "scenarioCode=42 在这份方案里的结论是什么？",
                List.of(
                        new QueryArticleHit(
                                QueryEvidenceType.ARTICLE,
                                null,
                                "scenario-summary",
                                "scenario-summary",
                                "渠道修正结论",
                                """
                                        ---
                                        title: "渠道修正结论"
                                        summary: "记录 scenarioCode=42 从无流量修正为有流量的最新结论。"
                                        referential_keywords: ["scenarioCode=42", "upstreamChannel", "248,000", "502,000"]
                                        ---

                                        # 渠道修正结论

                                        - scenarioCode=42（渠道场景）从“无流量”修正为“有流量”。
                                        - 30 天命中数：scenarioCode=42 = 248,000 条；channel=upstreamChannel = 502,000 条。
                                        """,
                                "{\"description\":\"scenarioCode=42 修正结论\"}",
                                List.of("migration-sample.pdf"),
                                3.0D
                        ),
                        new QueryArticleHit(
                                QueryEvidenceType.SOURCE,
                                "channel-plan.pdf#5",
                                "migration-sample.pdf",
                                """
                                        scenarioCode=42（渠道场景）从“无流量”修正为“有流量”。
                                        30 天命中数：scenarioCode=42 = 248,000 条；channel=upstreamChannel = 502,000 条。
                                        """,
                                "{\"filePath\":\"migration-sample.pdf\"}",
                                List.of("migration-sample.pdf"),
                                2.5D
                        )
                )
        );

        assertThat(answerPayload.getAnswerOutcome()).isEqualTo(AnswerOutcome.SUCCESS);
        assertThat(recordingLlmClient.getLastUserPrompt()).contains("QUESTION-FOCUSED EVIDENCE");
        assertThat(recordingLlmClient.getLastUserPrompt()).contains("从“无流量”修正为“有流量”");
        assertThat(recordingLlmClient.getLastUserPrompt()).contains("502,000");
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
        assertThat(answerPayload.getModelExecutionStatus()).isEqualTo(ModelExecutionStatus.DEGRADED);
        assertThat(answerPayload.isAnswerCacheable()).isFalse();
    }

    /**
     * 验证精确查值题在模型声称证据不足时，会回退到带精确值的确定性答案。
     *
     * @throws Exception 反射构造异常
     */
    @Test
    void shouldFallbackForExactLookupWhenModelClaimsEvidenceInsufficient() throws Exception {
        RecordingLlmClient recordingLlmClient = new RecordingLlmClient("""
                {
                  "answerMarkdown":"当前证据不足，暂无法确认子场景 B 的接口路径。[[subscene-check]]",
                  "answerOutcome":"INSUFFICIENT_EVIDENCE",
                  "answerCacheable":false
                }
                """);
        AnswerGenerationService answerGenerationService = new AnswerGenerationService(
                createGatewayFixture(recordingLlmClient).getLlmGateway()
        );

        QueryAnswerPayload answerPayload = answerGenerationService.generatePayload(
                "子场景 B 的接口路径是什么？",
                List.of(
                        new QueryArticleHit(
                                QueryEvidenceType.ARTICLE,
                                null,
                                "subscene-check",
                                "subscene-check",
                                "子场景 B 退款检查接口",
                                """
                                        ---
                                        title: "子场景 B 退款检查接口"
                                        summary: "子场景 B 的退款检查接口。"
                                        referential_keywords: ["/api/v2/fulfillment/request/card/refund/check", "子场景B"]
                                        ---

                                        # 子场景 B 退款检查接口

                                        - 子场景 B：退款检查。
                                        - 接口路径：`/api/v2/fulfillment/request/card/refund/check`。
                                        """,
                                "{\"description\":\"子场景 B 接口\"}",
                                List.of("migration-sample.pdf"),
                                3.0D
                        ),
                        new QueryArticleHit(
                                QueryEvidenceType.SOURCE,
                                "channel-plan.pdf#sub-b",
                                "migration-sample.pdf",
                                "子场景 B：退款检查；接口路径：`/api/v2/fulfillment/request/card/refund/check`。",
                                "{\"filePath\":\"migration-sample.pdf\"}",
                                List.of("migration-sample.pdf"),
                                2.5D
                        )
                )
        );

        assertThat(answerPayload.getGenerationMode()).isEqualTo(GenerationMode.FALLBACK);
        assertThat(answerPayload.getModelExecutionStatus()).isEqualTo(ModelExecutionStatus.DEGRADED);
        assertThat(answerPayload.getAnswerOutcome()).isEqualTo(AnswerOutcome.SUCCESS);
        assertThat(answerPayload.getFallbackReason()).isEqualTo("DETERMINISTIC_EXACT_LOOKUP_PREFERRED");
        assertThat(answerPayload.getAnswerMarkdown()).contains("/api/v2/fulfillment/request/card/refund/check");
    }

    /**
     * 验证精确查值题的结构化答案会优先收敛为直接回答，而不是保留后续大段背景展开。
     *
     * @throws Exception 反射构造异常
     */
    @Test
    void shouldCompressStructuredExactLookupAnswerToDirectConclusion() throws Exception {
        RecordingLlmClient recordingLlmClient = new RecordingLlmClient("""
                {
                  "answerMarkdown":"## 迁移后承接方\\n\\n迁移后，实体星礼包绑定/解绑触发的 CAC 同步机制需由 DPFM 承接。[[carry]]\\n\\n### 两种机制承接情况\\n\\n| 机制 | 承接方 |\\n|---|---|\\n| 机制A | 保持不变 |\\n| 机制B | DPFM |\\n\\n### DPFM 内部承接服务\\n\\n- dpfm-api-service\\n- dpfm-callback-service",
                  "answerOutcome":"SUCCESS",
                  "answerCacheable":true
                }
                """);
        AnswerGenerationService answerGenerationService = new AnswerGenerationService(
                createGatewayFixture(recordingLlmClient).getLlmGateway()
        );

        QueryAnswerPayload answerPayload = answerGenerationService.generatePayload(
                "迁移后此机制需由谁承接？",
                List.of(new QueryArticleHit(
                        QueryEvidenceType.ARTICLE,
                        null,
                        "carry",
                        "carry",
                        "迁移后承接说明",
                        "迁移后，实体星礼包绑定/解绑触发的 CAC 同步机制需由 DPFM 承接。",
                        "{\"description\":\"迁移后由 DPFM 承接\"}",
                        List.of("migration-sample.pdf"),
                        3.0D
                ))
        );

        assertThat(answerPayload.getAnswerOutcome()).isEqualTo(AnswerOutcome.SUCCESS);
        assertThat(answerPayload.getAnswerMarkdown()).contains("需由 DPFM 承接");
        assertThat(answerPayload.getAnswerMarkdown()).doesNotContain("### 两种机制承接情况");
        assertThat(answerPayload.getAnswerMarkdown()).doesNotContain("### DPFM 内部承接服务");
    }

    /**
     * 验证接口路径题在 fallback 下会优先选择真实路径行，而不是表头行。
     *
     * @throws Exception 反射构造异常
     */
    @Test
    void shouldPreferConcretePathLineOverHeaderLineInFallback() throws Exception {
        RecordingLlmClient recordingLlmClient = new RecordingLlmClient("not-json-without-citation");
        AnswerGenerationService answerGenerationService = new AnswerGenerationService(
                createGatewayFixture(recordingLlmClient).getLlmGateway()
        );

        QueryAnswerPayload answerPayload = answerGenerationService.generatePayload(
                "子场景 B 的接口路径是什么？",
                List.of(
                        new QueryArticleHit(
                                QueryEvidenceType.SOURCE,
                                "channel-plan.pdf#sub-b",
                                "migration-sample.pdf",
                                """
                                        === Table ===
                                        table_row: 接口路径 | 功能 | 生产验证 | 耗时
                                        table_row: POST /api/v2/fulfillment/request/card/refund/check | 子场景 B 退款检查
                                        """,
                                "{\"filePath\":\"migration-sample.pdf\"}",
                                List.of("migration-sample.pdf"),
                                2.5D
                        )
                )
        );

        assertThat(answerPayload.getGenerationMode()).isEqualTo(GenerationMode.FALLBACK);
        assertThat(answerPayload.getAnswerMarkdown()).contains("/api/v2/fulfillment/request/card/refund/check");
        assertThat(answerPayload.getAnswerMarkdown()).doesNotContain("接口路径 | 功能 | 生产验证 | 耗时");
    }

    /**
     * 验证 fallback 不会把结构化抽取残留前缀（如 table_row:）直接暴露给用户。
     *
     * @throws Exception 反射构造异常
     */
    @Test
    void shouldStripStructuredLinePrefixFromFallbackAnswer() throws Exception {
        RecordingLlmClient recordingLlmClient = new RecordingLlmClient("not-json-without-citation");
        AnswerGenerationService answerGenerationService = new AnswerGenerationService(
                createGatewayFixture(recordingLlmClient).getLlmGateway()
        );

        QueryAnswerPayload answerPayload = answerGenerationService.generatePayload(
                "储值卡到底有几种业务场景？",
                List.of(
                        new QueryArticleHit(
                                QueryEvidenceType.SOURCE,
                                "channel-plan.pdf#stored-value",
                                "migration-sample.pdf",
                                """
                                        table_row: ■ 明确两种业务场景：1302/3401 = 充值（纯充值，入参必须带 cardNo）；1301 = 开卡并充值（开卡后自动级联充值）
                                        table_row: 1. 场景7（储值卡）修正 | — 只有2种业务场景，不是3种
                                        """,
                                "{\"filePath\":\"migration-sample.pdf\"}",
                                List.of("migration-sample.pdf"),
                                2.5D
                        )
                )
        );

        assertThat(answerPayload.getAnswerMarkdown()).doesNotContain("table_row:");
    }

    /**
     * 验证结构化 JSON 若缺少 citation，会自动补当前最相关证据引用，而不是直接丢掉模型答案。
     *
     * @throws Exception 反射构造异常
     */
    @Test
    void shouldAttachCitationWhenStructuredAnswerOmitsCitations() throws Exception {
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
        assertThat(answerPayload.getGenerationMode()).isEqualTo(GenerationMode.LLM);
        assertThat(answerPayload.getModelExecutionStatus()).isEqualTo(ModelExecutionStatus.SUCCESS);
        assertThat(answerPayload.getAnswerMarkdown()).contains("当前文档存在冲突");
        assertThat(answerPayload.getAnswerMarkdown()).contains("[[readme]]");
    }

    /**
     * 验证差异题已经回答完整时，会去掉“低层接口细节未覆盖”的尾部保守声明，并清理泛化引导句上的错位 citation。
     *
     * @throws Exception 反射构造异常
     */
    @Test
    void shouldRemoveUnsupportedDetailCaveatFromAnsweredDiffQuestion() throws Exception {
        RecordingLlmClient recordingLlmClient = new RecordingLlmClient("""
                {
                  "answerMarkdown":"某渠道实现在代码里不是 3 个独立渠道，而是同一渠道的不同实现路径。[→ variant-diff-notes.md]\\n\\n核心差异在正向流程：旧版接口是“权益核销 → 支付”；V2 接口改为“查券 → 支付 → `coupon` 服务核销”。[→ variant-diff-notes.md]\\n\\n逆向流程也不同：旧版接口是“权益冲正/退款 → 支付冲正/退款”；V2 冲正为“`coupon` 服务冲正 → 支付冲正”，退款为“支付退款 → `coupon` 服务退款”。[→ variant-diff-notes.md]\\n\\n简表如下： [[legacy-default--重试模式说明]]\\n\\n| 对比项 | 旧版接口 | V2 接口 |\\n|---|---|---|\\n| 正向流程 | 权益核销 → 支付 | 查券 → 支付 → `coupon` 服务核销 |\\n\\n补充：证据中只明确了流程、路由和实现类差异，没有提供 V2 与旧接口在报文字段、HTTP/API 参数、返回码等层面的逐项差异，因此这些接口字段级差异无法基于当前材料确认。 [→ field-definitions.xlsx]\\n\\n需要注意：资料只说明了流程与实现类差异，未给出 V2 与旧接口在请求字段、响应字段、接口地址、签名方式、错误码、幂等键等方面的差异；如果你要的是“接口协议/报文字段差异”，目前证据不足。 [[legacy-default--field-definitions]]",
                  "answerOutcome":"SUCCESS",
                  "answerCacheable":true
                }
                """);
        AnswerGenerationService answerGenerationService = new AnswerGenerationService(
                createGatewayFixture(recordingLlmClient).getLlmGateway()
        );

        QueryAnswerPayload answerPayload = answerGenerationService.generatePayload(
                "渠道 V2 接口和旧接口有哪些差异？",
                buildVersionDiffEvidence()
        );

        assertThat(answerPayload.getAnswerOutcome()).isEqualTo(AnswerOutcome.SUCCESS);
        assertThat(answerPayload.getAnswerMarkdown()).contains("核心差异在正向流程");
        assertThat(answerPayload.getAnswerMarkdown()).contains("简表如下：");
        assertThat(answerPayload.getAnswerMarkdown()).doesNotContain("证据中只明确");
        assertThat(answerPayload.getAnswerMarkdown()).doesNotContain("无法基于当前材料确认");
        assertThat(answerPayload.getAnswerMarkdown()).doesNotContain("资料只说明");
        assertThat(answerPayload.getAnswerMarkdown()).doesNotContain("目前证据不足");
        assertThat(answerPayload.getAnswerMarkdown()).doesNotContain("[[legacy-default--重试模式说明]]");
        assertThat(answerPayload.getAnswerMarkdown()).doesNotContain("gateway-field-definitions");
    }

    /**
     * 验证字段定义类表格文档在 fallback 时会解析字段表，而不是只返回概述句。
     */
    @Test
    void shouldExtractSpreadsheetFieldDefinitionsForFallbackAnswer() {
        AnswerGenerationService answerGenerationService = new AnswerGenerationService();

        QueryAnswerPayload answerPayload = answerGenerationService.fallbackPayload(
                "网关字段定义资料有哪些？",
                buildGatewayFieldDefinitionEvidence()
        );

        assertThat(answerPayload.getAnswerOutcome()).isEqualTo(AnswerOutcome.SUCCESS);
        assertThat(answerPayload.getAnswerMarkdown()).contains("入参 `requestData` 共 13 个字段");
        assertThat(answerPayload.getAnswerMarkdown()).contains("`transactionType`（string/3，交易类型");
        assertThat(answerPayload.getAnswerMarkdown()).contains("`goodsTag`（string/16，易百新增字段");
        assertThat(answerPayload.getAnswerMarkdown()).contains("出参 `responseData` 共 15 个字段");
        assertThat(answerPayload.getAnswerMarkdown()).contains("`tenderCode`（string/8，易百新增字段");
        assertThat(answerPayload.getAnswerMarkdown()).contains("`01`=查余额");
        assertThat(answerPayload.getAnswerMarkdown()).doesNotContain("同一份资料还给出");
    }

    /**
     * 验证指定字段题即使只命中原始表格行，也会覆盖用户点名的每个字段。
     */
    @Test
    void shouldAnswerFocusedFieldQuestionFromRawSpreadsheetSource() {
        AnswerGenerationService answerGenerationService = new AnswerGenerationService();

        QueryAnswerPayload answerPayload = answerGenerationService.fallbackPayload(
                "网关 requestData 和 responseData 里 amount、respCode、goodsDetail、transactionType 分别是什么？",
                buildRawGatewayFieldDefinitionEvidence()
        );

        assertThat(answerPayload.getAnswerOutcome()).isEqualTo(AnswerOutcome.SUCCESS);
        assertThat(answerPayload.getAnswerMarkdown()).contains("`amount`：类型 `string`");
        assertThat(answerPayload.getAnswerMarkdown()).contains("交易金额，单位元，小数点2位");
        assertThat(answerPayload.getAnswerMarkdown()).contains("`respCode`：类型 `string`");
        assertThat(answerPayload.getAnswerMarkdown()).contains("返回码2位 00成功");
        assertThat(answerPayload.getAnswerMarkdown()).contains("`goodsDetail`：类型 `json`");
        assertThat(answerPayload.getAnswerMarkdown()).contains("扩展字段，商品明细");
        assertThat(answerPayload.getAnswerMarkdown()).contains("`transactionType`：类型 `string`");
        assertThat(answerPayload.getAnswerMarkdown()).contains("交易类型，传的是编号");
        assertThat(answerPayload.getAnswerMarkdown()).doesNotContain("同一份资料还给出");
    }

    /**
     * 验证指定字段 fallback 不是某个业务样例专用逻辑，其他表格/CSV 字段也能按问题标识逐项回答。
     */
    @Test
    void shouldAnswerFocusedFieldQuestionFromGenericSpreadsheetSource() {
        AnswerGenerationService answerGenerationService = new AnswerGenerationService();

        QueryAnswerPayload answerPayload = answerGenerationService.fallbackPayload(
                "Order API 里 orderStatus、retryCount 分别是什么？",
                buildGenericFieldDefinitionEvidence()
        );

        assertThat(answerPayload.getAnswerOutcome()).isEqualTo(AnswerOutcome.SUCCESS);
        assertThat(answerPayload.getAnswerMarkdown()).contains("`orderStatus`：类型 `string`");
        assertThat(answerPayload.getAnswerMarkdown()).contains("订单状态，取值 PENDING/PAID/CLOSED");
        assertThat(answerPayload.getAnswerMarkdown()).contains("`retryCount`：类型 `int`");
        assertThat(answerPayload.getAnswerMarkdown()).contains("支付重试次数");
        assertThat(answerPayload.getAnswerMarkdown()).doesNotContain("`Order`：");
        assertThat(answerPayload.getAnswerMarkdown()).doesNotContain("`API`：");
    }

    /**
     * 验证结构化表格答案会给数据行补引用，提高字段类答案 citation coverage。
     *
     * @throws Exception 反射构造异常
     */
    @Test
    void shouldAttachCitationsToStructuredMarkdownTableRows() throws Exception {
        RecordingLlmClient recordingLlmClient = new RecordingLlmClient("""
                {
                  "answerMarkdown":"| 字段 | 说明 |\\n|---|---|\\n| amount | 交易金额，单位元，小数点2位 |\\n| respCode | 返回码2位，00成功 |",
                  "answerOutcome":"SUCCESS",
                  "answerCacheable":true
                }
                """);
        AnswerGenerationService answerGenerationService = new AnswerGenerationService(
                createGatewayFixture(recordingLlmClient).getLlmGateway()
        );

        QueryAnswerPayload answerPayload = answerGenerationService.generatePayload(
                "网关 requestData 和 responseData 里 amount、respCode 分别是什么？",
                buildRawGatewayFieldDefinitionEvidence()
        );

        assertThat(answerPayload.getGenerationMode()).isEqualTo(GenerationMode.LLM);
        assertThat(answerPayload.getAnswerMarkdown()).doesNotContain("| 字段 | 说明 [[");
        assertThat(answerPayload.getAnswerMarkdown()).contains("| amount | 交易金额，单位元，小数点2位 [→ field-definitions.xlsx] |");
        assertThat(answerPayload.getAnswerMarkdown()).contains("| respCode | 返回码2位，00成功 [→ field-definitions.xlsx] |");
    }

    /**
     * 验证字段定义题里模型返回非 JSON Markdown 表格时，会保留 LLM 答案并逐行补 citation。
     *
     * @throws Exception 反射构造异常
     */
    @Test
    void shouldReuseUnstructuredFieldMarkdownWithAutoCitation() throws Exception {
        RecordingLlmClient recordingLlmClient = new RecordingLlmClient("""
                | 字段 | 含义 |
                |---|---|
                | orderStatus | 订单状态，取值 PENDING/PAID/CLOSED |
                | retryCount | 支付重试次数 |
                """);
        AnswerGenerationService answerGenerationService = new AnswerGenerationService(
                createGatewayFixture(recordingLlmClient).getLlmGateway()
        );

        QueryAnswerPayload answerPayload = answerGenerationService.generatePayload(
                "Order API 里 orderStatus、retryCount 分别是什么？",
                buildGenericFieldDefinitionEvidence()
        );

        assertThat(answerPayload.getGenerationMode()).isEqualTo(GenerationMode.LLM);
        assertThat(answerPayload.getModelExecutionStatus()).isEqualTo(ModelExecutionStatus.SUCCESS);
        assertThat(answerPayload.getAnswerMarkdown()).contains("| orderStatus | 订单状态，取值 PENDING/PAID/CLOSED [→ order-api-fields.xlsx] |");
        assertThat(answerPayload.getAnswerMarkdown()).contains("| retryCount | 支付重试次数 [→ order-api-fields.xlsx] |");
    }

    /**
     * 验证枚举题里模型返回非 JSON Markdown 表格时，会保留可用答案并补齐引用，而不是降级 fallback。
     *
     * @throws Exception 反射构造异常
     */
    @Test
    void shouldReuseUnstructuredEnumerationMarkdownWithAutoCitation() throws Exception {
        RecordingLlmClient recordingLlmClient = new RecordingLlmClient("""
                | 重试形态 | 代表链路 |
                |---|---|
                | 同步轮询查询 | 支付宝支付、微信支付 |
                | 同步多次重试 | 微信冲正 |
                | MQ 延迟重投 | 线上单券激活 |
                """);
        AnswerGenerationService answerGenerationService = new AnswerGenerationService(
                createGatewayFixture(recordingLlmClient).getLlmGateway()
        );

        QueryAnswerPayload answerPayload = answerGenerationService.generatePayload(
                "当前项目里支付与卡券重试有哪些典型形态？",
                List.of(
                        new QueryArticleHit(
                                QueryEvidenceType.ARTICLE,
                                "legacy-default--重试模式说明",
                                "重试模式说明",
                                """
                                当前系统对支付、退款、冲正等操作采用的自动重试机制可分为六种典型形态：
                                | 类型 | 是否自动 | 典型形态 | 当前项目中的代表链路 |
                                |---|---|---|---|
                                | 同步轮询查询 | 是 | for/while + sleep + query | 支付宝支付、微信支付、银联支付/退款、数币支付/退款 |
                                | 同步多次重试 | 是 | 当前请求内重复调用同一动作 | 微信冲正、农业银行积分兑换/易百积分冲正、PE/CAC 推送 |
                                | MQ 延迟重投 | 是 | MQ + TTL / @Retry | 线上单券激活、批量券激活/核销、POS 星星核销 |
                                | Spring Retry | 是 | @Retryable | 批量单张券冲正 |
                                | 定时补偿 | 是 | trans-job / xxljob | 账户冲正、券冲正、SRKit 随单购 |
                                | 请求重入复用 | 否 | 失败先留状态，后续同一笔退款复用原退款记录再尝试 | 微信退款 REFUND_FAIL_TRY_AGAIN |
                                """,
                                "{\"description\":\"支付与卡券重试现状\"}",
                                List.of("retry-pattern-notes.md"),
                                3.0D
                        ),
                        new QueryArticleHit(
                                QueryEvidenceType.SOURCE,
                                "retry-pattern-notes.md#0",
                                "retry-pattern-notes.md",
                                "重试模式说明源文件也记录了六种典型形态。",
                                "{\"filePath\":\"retry-pattern-notes.md\"}",
                                List.of("retry-pattern-notes.md"),
                                1.0D
                        )
                )
        );

        assertThat(answerPayload.getGenerationMode()).isEqualTo(GenerationMode.LLM);
        assertThat(answerPayload.getModelExecutionStatus()).isEqualTo(ModelExecutionStatus.SUCCESS);
        assertThat(answerPayload.getAnswerOutcome()).isEqualTo(AnswerOutcome.SUCCESS);
        assertThat(answerPayload.getAnswerMarkdown()).contains("| 同步轮询查询 | 支付宝支付、微信支付 [");
        assertThat(answerPayload.getAnswerMarkdown()).contains("| MQ 延迟重投 | 线上单券激活 [");
    }

    /**
     * 验证模型返回单段非 JSON 目标答案时，会按问题锚点复用并补 citation，而不是降级 fallback。
     *
     * @throws Exception 反射构造异常
     */
    @Test
    void shouldReuseUnstructuredMilestoneAnswerWithAutoCitation() throws Exception {
        RecordingLlmClient recordingLlmClient = new RecordingLlmClient(
                "到2027年，完成重点场景标准体系建设并发布30项以上关键标准；到2030年，形成协同完善的标准体系和持续迭代机制。"
        );
        AnswerGenerationService answerGenerationService = new AnswerGenerationService(
                createGatewayFixture(recordingLlmClient).getLlmGateway()
        );

        QueryAnswerPayload answerPayload = answerGenerationService.generatePayload(
                "标准体系建设指南里，到2027年和到2030年的阶段要求分别是什么？",
                List.of(
                        new QueryArticleHit(
                                QueryEvidenceType.ARTICLE,
                                1L,
                                "stage-target-guide",
                                "stage-target-guide",
                                "标准体系建设指南",
                                """
                                到2027年，完成重点场景标准体系建设并发布30项以上关键标准。
                                到2030年，形成协同完善的标准体系和持续迭代机制。
                                """,
                                "{\"description\":\"阶段要求\"}",
                                List.of("standard-guide.pdf"),
                                4.0D
                        ),
                        new QueryArticleHit(
                                QueryEvidenceType.SOURCE,
                                1L,
                                null,
                                "standard-guide.pdf#0",
                                "standard-guide.pdf",
                                """
                                到2027年，完成重点场景标准体系建设并发布30项以上关键标准。
                                到2030年，形成协同完善的标准体系和持续迭代机制。
                                """,
                                "{\"filePath\":\"standard-guide.pdf\"}",
                                List.of("standard-guide.pdf"),
                                3.0D
                        )
                )
        );

        assertThat(answerPayload.getGenerationMode()).isEqualTo(GenerationMode.LLM);
        assertThat(answerPayload.getModelExecutionStatus()).isEqualTo(ModelExecutionStatus.SUCCESS);
        assertThat(answerPayload.getAnswerOutcome()).isEqualTo(AnswerOutcome.SUCCESS);
        assertThat(answerPayload.getAnswerMarkdown()).contains("到2027年");
        assertThat(answerPayload.getAnswerMarkdown()).contains("到2030年");
        assertThat(answerPayload.getAnswerMarkdown()).contains("[[stage-target-guide]]");
        assertThat(answerPayload.getAnswerMarkdown()).doesNotStartWith("# 查询回答");
    }

    /**
     * 验证主体已覆盖问题锚点时，会移除模型追加的未问量化缺口尾注。
     *
     * @throws Exception 反射构造异常
     */
    @Test
    void shouldRemoveUnrequestedNumericCaveatWhenMilestoneAnswerIsCovered() throws Exception {
        RecordingLlmClient recordingLlmClient = new RecordingLlmClient("""
                {
                  "answerMarkdown":"| 时间节点 | 目标 |\\n|---|---|\\n| 到2027年 | 完成重点场景标准体系建设。 [→ standard-guide.pdf] |\\n| 到2030年 | 形成协同完善的标准体系和持续迭代机制。 [→ standard-guide.pdf] |\\n\\n以上为当前可核验原文中的目标表述；关于到2027年是否另有100项以上要求，当前证据不足，暂无法确认。",
                  "answerOutcome":"PARTIAL_ANSWER",
                  "answerCacheable":false
                }
                """);
        AnswerGenerationService answerGenerationService = new AnswerGenerationService(
                createGatewayFixture(recordingLlmClient).getLlmGateway()
        );

        QueryAnswerPayload answerPayload = answerGenerationService.generatePayload(
                "标准体系建设指南里，到2027年和到2030年的阶段要求分别是什么？",
                buildStageTargetEvidence()
        );

        assertThat(answerPayload.getGenerationMode()).isEqualTo(GenerationMode.LLM);
        assertThat(answerPayload.getModelExecutionStatus()).isEqualTo(ModelExecutionStatus.SUCCESS);
        assertThat(answerPayload.getAnswerOutcome()).isEqualTo(AnswerOutcome.SUCCESS);
        assertThat(answerPayload.getAnswerMarkdown()).contains("到2027年");
        assertThat(answerPayload.getAnswerMarkdown()).contains("到2030年");
        assertThat(answerPayload.getAnswerMarkdown()).doesNotContain("100项");
        assertThat(answerPayload.getAnswerMarkdown()).doesNotContain("证据不足");
    }

    /**
     * 验证冲突题 deterministic fallback 会先给结论，再说明证据冲突与倾向。
     *
     * @throws Exception 反射构造异常
     */
    @Test
    void shouldGenerateComparisonConclusionForConflictFallback() {
        AnswerGenerationService answerGenerationService = new AnswerGenerationService();

        QueryAnswerPayload answerPayload = answerGenerationService.fallbackPayload(
                "库存并发到底是乐观锁还是 Redis 锁",
                buildConflictEvidence(),
                AnswerOutcome.PARTIAL_ANSWER
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
        assertThat(answerPayload.getModelExecutionStatus()).isEqualTo(ModelExecutionStatus.DEGRADED);
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
        assertThat(answerPayload.getModelExecutionStatus()).isEqualTo(ModelExecutionStatus.DEGRADED);
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
        assertThat(answerPayload.getModelExecutionStatus()).isEqualTo(ModelExecutionStatus.DEGRADED);
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
     * 验证 fallback 在存在更贴题的 source 级状态证据时，不会再被泛化 article 摘要抢走首要答案位。
     */
    @Test
    void shouldPreferDirectSourceStatusEvidenceOverGenericArticleSummary() {
        AnswerGenerationService answerGenerationService = new AnswerGenerationService();

        QueryAnswerPayload answerPayload = answerGenerationService.fallbackPayload(
                "OCR / 文档识别现在是什么状态？",
                List.of(
                        new QueryArticleHit(
                                QueryEvidenceType.ARTICLE,
                                "readme",
                                "Readme",
                                "README 说明文档提到 Local Extractor / OCR Provider 会影响资料同步与解析。",
                                "{\"description\":\"系统总览里提到 OCR Provider。\"}",
                                List.of("README.md"),
                                0.17D
                        ),
                        new QueryArticleHit(
                                QueryEvidenceType.ARTICLE,
                                "admin",
                                "Admin",
                                "Admin 文章摘要主要描述知识问答、开发接入和后台管理界面。",
                                "{\"description\":\"后台界面总览。\"}",
                                List.of("admin/ask.html", "admin/developer-access.html", "admin/index.html", "admin/settings.html"),
                                0.10D
                        ),
                        new QueryArticleHit(
                                QueryEvidenceType.SOURCE,
                                "admin/settings.html#ocr",
                                "admin/settings.html",
                                """
                                        <h2>OCR / 文档识别</h2>
                                        <p>还没有可用的识别连接</p>
                                        <p>普通文本导入通常不受影响，但扫描 PDF、图片和复杂文档识别会受限。</p>
                                        """,
                                "{\"filePath\":\"admin/settings.html\"}",
                                List.of("admin/settings.html"),
                                0.02D
                        )
                )
        );

        assertThat(answerPayload.getAnswerMarkdown()).contains("OCR / 文档识别");
        assertThat(answerPayload.getAnswerMarkdown()).contains("admin/settings.html");
    }

    /**
     * 验证 source 级 fallback 只保留 source citation，不会再泄漏 article 风格 chunk 引用。
     */
    @Test
    void shouldKeepSourceOnlyCitationForStructuredSourceFallback() {
        AnswerGenerationService answerGenerationService = new AnswerGenerationService();

        QueryAnswerPayload answerPayload = answerGenerationService.fallbackPayload(
                "payment timeout retry 是什么配置",
                List.of(new QueryArticleHit(
                        QueryEvidenceType.SOURCE,
                        "payment/context.md#0",
                        "payment/context.md",
                        """
                                payment.timeout.retry = 3
                                支付超时场景下最多重试 3 次
                                """,
                        "{\"filePath\":\"payment/context.md\"}",
                        List.of("payment/context.md"),
                        2.0D
                )),
                AnswerOutcome.SUCCESS
        );

        assertThat(answerPayload.getAnswerOutcome()).isEqualTo(AnswerOutcome.SUCCESS);
        assertThat(answerPayload.getAnswerMarkdown()).doesNotContain("[[payment/context.md#0]]");
        assertThat(answerPayload.getAnswerMarkdown()).contains("[→ payment/context.md]");
    }

    /**
     * 验证 fallback 选句会跳过纯图片 Markdown 行，优先返回真正的文本事实。
     */
    @Test
    void shouldSkipMarkdownImageLineWhenBuildingFallbackAnswer() {
        AnswerGenerationService answerGenerationService = new AnswerGenerationService();

        QueryAnswerPayload answerPayload = answerGenerationService.fallbackPayload(
                "邪修智库支持哪些开发接入方式？",
                List.of(new QueryArticleHit(
                        QueryEvidenceType.SOURCE,
                        "README.md#0",
                        "README.md",
                        """
                                ![邪修智库系统分层总览架构图](docs/images/readme/system-architecture-overview-optimized-20260423.png)
                                它把 Web、HTTP API、CLI、MCP 四类入口统一落到同一套知识后端，而不是各做各的。
                                """,
                        "{\"filePath\":\"README.md\"}",
                        List.of("README.md"),
                        2.0D
                )),
                AnswerOutcome.SUCCESS
        );

        assertThat(answerPayload.getAnswerMarkdown()).contains("Web、HTTP API、CLI、MCP");
        assertThat(answerPayload.getAnswerMarkdown()).doesNotContain("![邪修智库系统分层总览架构图]");
    }

    /**
     * 验证 fallback 不会把 Markdown 表格表头当成事实答案。
     */
    @Test
    void shouldSkipMarkdownTableHeaderWhenSelectingFallbackSnippet() {
        AnswerGenerationService answerGenerationService = new AnswerGenerationService();

        QueryAnswerPayload answerPayload = answerGenerationService.fallbackPayload(
                "邪修智库支持哪些开发接入方式？",
                List.of(new QueryArticleHit(
                        QueryEvidenceType.SOURCE,
                        "README.md#0",
                        "README.md",
                        """
                                | 维度 | 常见 RAG | 邪修智库 |
                                | --- | --- | --- |
                                | 对外能力 | 单页面或单接口 | Web、HTTP API、CLI、MCP 共用统一后端 |
                                """,
                        "{\"filePath\":\"README.md\"}",
                        List.of("README.md"),
                        2.0D
                )),
                AnswerOutcome.SUCCESS
        );

        assertThat(answerPayload.getAnswerMarkdown()).contains("Web、HTTP API、CLI、MCP 共用统一后端");
        assertThat(answerPayload.getAnswerMarkdown()).doesNotContain("维度；常见 RAG；邪修智库");
    }

    /**
     * 验证能力枚举题会优先命中入口列表句，而不是项目总述句。
     */
    @Test
    void shouldPreferCapabilityEnumerationOverGenericProjectSummary() {
        AnswerGenerationService answerGenerationService = new AnswerGenerationService();

        QueryAnswerPayload answerPayload = answerGenerationService.fallbackPayload(
                "邪修智库支持哪些开发接入方式？",
                List.of(new QueryArticleHit(
                        QueryEvidenceType.SOURCE,
                        "README.md#0",
                        "README.md",
                        """
                                换句话说，邪修智库更像一个长期运行的知识后端，而不是一个临时问答 demo。
                                它把 Web、HTTP API、CLI、MCP 四类入口统一落到同一套知识后端，而不是各做各的。
                                """,
                        "{\"filePath\":\"README.md\"}",
                        List.of("README.md"),
                        2.0D
                )),
                AnswerOutcome.SUCCESS
        );

        assertThat(answerPayload.getAnswerMarkdown()).contains("Web、HTTP API、CLI、MCP");
        assertThat(answerPayload.getAnswerMarkdown()).doesNotContain("更像一个长期运行的知识后端");
    }

    /**
     * 验证枚举题 fallback 会展开多条形态事实，而不是只摘一条任务表细节。
     */
    @Test
    void shouldExpandEnumerationFactsForRetryFormsQuestion() {
        AnswerGenerationService answerGenerationService = new AnswerGenerationService();

        QueryAnswerPayload answerPayload = answerGenerationService.fallbackPayload(
                "当前项目里支付与卡券重试有哪些典型形态？",
                List.of(new QueryArticleHit(
                        QueryEvidenceType.ARTICLE,
                        "retry-status",
                        "重试模式说明",
                        """
                                ## 总览
                                当前系统对支付、退款、冲正等操作采用的自动重试机制可分为六种典型形态：
                                | 类型 | 是否自动 | 典型形态 | 当前项目中的代表链路 |
                                |---|---|---|---|
                                | 同步轮询查询 | 是 | for/while + sleep + query | 支付宝支付、微信支付、银联支付/退款、数币支付/退款 |
                                | 同步多次重试 | 是 | 当前请求内重复调用同一动作 | 微信冲正、农业银行积分兑换/易百积分冲正、PE/CAC 推送 |
                                | MQ 延迟重投 | 是 | MQ + TTL / @Retry | 线上单券激活、批量券激活/核销、POS 星星核销 |
                                | Spring Retry | 是 | @Retryable | 批量单张券冲正 |
                                | 定时补偿 | 是 | trans-job / xxljob | 账户冲正、券冲正、SRKit 随单购 |
                                | 请求重入复用 | 否 | 失败先留状态，后续同一笔退款复用原退款记录再尝试 | 微信退款 REFUND_FAIL_TRY_AGAIN |
                                | PaymentRefundTask | pay_changelog.action = refund | 代码存在但配置未启用 | 补偿任务明细 |
                                """,
                        "{\"description\":\"支付与卡券重试现状\"}",
                        List.of("retry-pattern-notes.md"),
                        2.0D
                )),
                AnswerOutcome.SUCCESS
        );

        assertThat(answerPayload.getAnswerMarkdown()).contains("同步轮询查询");
        assertThat(answerPayload.getAnswerMarkdown()).contains("同步多次重试");
        assertThat(answerPayload.getAnswerMarkdown()).contains("MQ 延迟重投");
        assertThat(answerPayload.getAnswerMarkdown()).contains("Spring Retry");
        assertThat(answerPayload.getAnswerMarkdown()).contains("定时补偿");
        assertThat(answerPayload.getAnswerMarkdown()).contains("请求重入复用");
        assertThat(answerPayload.getAnswerMarkdown()).doesNotContain("PaymentRefundTask；pay_changelog.action = refund");
    }

    /**
     * 验证技巧枚举题 fallback 会展开 PDF 里的项目符号，而不是只返回目录标题。
     */
    @Test
    void shouldExpandPromptTechniqueBulletsForEnumerationQuestion() {
        AnswerGenerationService answerGenerationService = new AnswerGenerationService();

        QueryAnswerPayload answerPayload = answerGenerationService.fallbackPayload(
                "提升 GPT 模型使用效率与质量的技巧有哪些？",
                List.of(new QueryArticleHit(
                        QueryEvidenceType.SOURCE,
                        "prompt-techniques.pdf#0",
                        "prompt-techniques.pdf",
                        """
                                === Page: 2 ===
                                目录
                                • 如何提升 GPT 模型使用效率与质量
                                === Page: 4 ===
                                GPT 模型实战：技巧与原则
                                • 角色设定：擅于使用 System 给GPT设定角色和任务，如“哲学大师”；
                                • 指令注入：在 System 中注入常驻任务指令，如“主题创作”；
                                • 问题拆解：将复杂问题拆解成的子问题，分步骤执行，如：Debug 和多任务；
                                • 分层设计：创作长篇内容，分层提问，先概览再章节，最后补充细节，如：小说生成；
                                • 编程思维：将prompt当做编程语言，主动设计变量、模板和正文，如：评估模型输出质量；
                                • Few-Shot：基于样例的prompt设计，规范推理路径和输出样式，如：构造训练数据；
                                """,
                        "{\"filePath\":\"prompt-techniques.pdf\"}",
                        List.of("prompt-techniques.pdf"),
                        2.0D
                )),
                AnswerOutcome.SUCCESS
        );

        assertThat(answerPayload.getAnswerMarkdown()).contains("角色设定");
        assertThat(answerPayload.getAnswerMarkdown()).contains("指令注入");
        assertThat(answerPayload.getAnswerMarkdown()).contains("问题拆解");
        assertThat(answerPayload.getAnswerMarkdown()).contains("分层设计");
        assertThat(answerPayload.getAnswerMarkdown()).contains("编程思维");
        assertThat(answerPayload.getAnswerMarkdown()).contains("Few-Shot");
        assertThat(answerPayload.getAnswerMarkdown()).doesNotContain("当前可确认的信息是：如何提升 GPT 模型使用效率与质量");
    }

    /**
     * 验证能力题不会把无关的配置事实句塞进结论补充位。
     */
    @Test
    void shouldSkipIrrelevantSecondaryEvidenceForCapabilityQuestion() {
        AnswerGenerationService answerGenerationService = new AnswerGenerationService();

        QueryAnswerPayload answerPayload = answerGenerationService.fallbackPayload(
                "邪修智库支持哪些开发接入方式？",
                List.of(
                        new QueryArticleHit(
                                QueryEvidenceType.SOURCE,
                                "README.md#0",
                                "README.md",
                                "它把 Web、HTTP API、CLI、MCP 四类入口统一落到同一套知识后端，而不是各做各的。",
                                "{\"filePath\":\"README.md\"}",
                                List.of("README.md"),
                                3.0D
                        ),
                        new QueryArticleHit(
                                QueryEvidenceType.SOURCE,
                                "项目启动配置清单.md#0",
                                "项目启动配置清单.md",
                                "SPRING_DATASOURCE_URL = jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge?currentSchema=lattice，你的数据库地址、库名、schema 不是默认值时",
                                "{\"filePath\":\"项目启动配置清单.md\"}",
                                List.of("项目启动配置清单.md"),
                                2.0D
                        )
                ),
                AnswerOutcome.SUCCESS
        );

        assertThat(answerPayload.getAnswerMarkdown()).contains("Web、HTTP API、CLI、MCP");
        assertThat(answerPayload.getAnswerMarkdown()).doesNotContain("SPRING_DATASOURCE_URL = jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge?currentSchema=lattice");
    }

    /**
     * 验证启动前准备题会优先回答前置步骤，而不是单个配置项。
     */
    @Test
    void shouldPreferSetupChecklistStepsOverSingleConfigAssignment() {
        AnswerGenerationService answerGenerationService = new AnswerGenerationService();

        QueryAnswerPayload answerPayload = answerGenerationService.fallbackPayload(
                "这个项目真正启动起来之前，需要先配置什么？",
                List.of(new QueryArticleHit(
                        QueryEvidenceType.SOURCE,
                        "项目启动配置清单.md#0",
                        "项目启动配置清单.md",
                        """
                                如果你按当前仓库默认口径本地启动，这个项目真正需要你处理的事情只有 4 件：
                                1. 准备好 `JDK 21`、`Maven`、`Docker`
                                2. 确认现有 `PostgreSQL` 和 `Redis` 容器可用
                                3. 在数据库里创建业务 schema：`lattice`
                                4. 用 `jdbc` profile 启动 Spring Boot
                                LATTICE_REDIS_HOST = 127.0.0.1，Redis 不在本机时
                                """,
                        "{\"filePath\":\"项目启动配置清单.md\"}",
                        List.of("项目启动配置清单.md"),
                        2.0D
                )),
                AnswerOutcome.SUCCESS
        );

        assertThat(answerPayload.getAnswerMarkdown()).contains("JDK 21");
        assertThat(answerPayload.getAnswerMarkdown()).contains("PostgreSQL");
        assertThat(answerPayload.getAnswerMarkdown()).contains("jdbc");
        assertThat(answerPayload.getAnswerMarkdown()).doesNotContain("LATTICE_REDIS_HOST = 127.0.0.1");
    }

    /**
     * 验证发给 answer LLM 的证据正文会过滤纯图片 Markdown 行，避免模型直接复述媒体占位。
     *
     * @throws Exception 反射构造异常
     */
    @Test
    void shouldStripMarkdownImageLineFromAnswerPromptEvidence() throws Exception {
        RecordingLlmClient recordingLlmClient = new RecordingLlmClient("""
                {
                  "answerMarkdown":"- 支持 Web、HTTP API、CLI、MCP 四类入口 [→ README.md]",
                  "answerOutcome":"SUCCESS",
                  "answerCacheable":true
                }
                """);
        AnswerGenerationService answerGenerationService = new AnswerGenerationService(
                createGatewayFixture(recordingLlmClient).getLlmGateway()
        );

        answerGenerationService.generatePayload(
                "邪修智库支持哪些开发接入方式？",
                List.of(new QueryArticleHit(
                        QueryEvidenceType.SOURCE,
                        "README.md#0",
                        "README.md",
                        """
                                ![邪修智库系统分层总览架构图](docs/images/readme/system-architecture-overview-optimized-20260423.png)
                                它把 Web、HTTP API、CLI、MCP 四类入口统一落到同一套知识后端，而不是各做各的。
                                """,
                        "{\"filePath\":\"README.md\"}",
                        List.of("README.md"),
                        2.0D
                ))
        );

        assertThat(recordingLlmClient.getLastUserPrompt()).contains("Web、HTTP API、CLI、MCP");
        assertThat(recordingLlmClient.getLastUserPrompt()).doesNotContain("![邪修智库系统分层总览架构图]");
    }

    /**
     * 验证发给 answer LLM 的证据会脱敏常见密钥类赋值，避免业务配置原样进入模型上下文。
     *
     * @throws Exception 反射构造异常
     */
    @Test
    void shouldMaskSecretAssignmentsInAnswerPromptEvidence() throws Exception {
        RecordingLlmClient recordingLlmClient = new RecordingLlmClient("""
                {
                  "answerMarkdown":"- external.service.apiKey 当前为 `<masked>` [→ ops/config.md]",
                  "answerOutcome":"SUCCESS",
                  "answerCacheable":true
                }
                """);
        AnswerGenerationService answerGenerationService = new AnswerGenerationService(
                createGatewayFixture(recordingLlmClient).getLlmGateway()
        );

        QueryAnswerPayload answerPayload = answerGenerationService.generatePayload(
                "external.service.apiKey 当前配置是什么？",
                List.of(new QueryArticleHit(
                        QueryEvidenceType.SOURCE,
                        "ops/config.md#0",
                        "ops/config.md",
                        "external.service.apiKey: plain-api-key-123456\nexternal.service.timeout: 3s",
                        "{\"apiKey\":\"json-secret-123456\",\"filePath\":\"ops/config.md\"}",
                        List.of("ops/config.md"),
                        2.0D
                ))
        );

        assertThat(recordingLlmClient.getLastUserPrompt()).contains("external.service.apiKey: <masked>");
        assertThat(recordingLlmClient.getLastUserPrompt()).contains("\"apiKey\":\"<masked>\"");
        assertThat(recordingLlmClient.getLastUserPrompt()).doesNotContain("plain-api-key-123456");
        assertThat(recordingLlmClient.getLastUserPrompt()).doesNotContain("json-secret-123456");
        assertThat(answerPayload.getAnswerMarkdown()).contains("<masked>");
        assertThat(answerPayload.getAnswerMarkdown()).doesNotContain("plain-api-key-123456");
    }

    /**
     * 验证 deterministic fallback 也会脱敏密钥类配置值。
     */
    @Test
    void shouldMaskSecretAssignmentsInFallbackAnswer() {
        AnswerGenerationService answerGenerationService = new AnswerGenerationService();

        QueryAnswerPayload answerPayload = answerGenerationService.fallbackPayload(
                "external.service.apiKey 当前配置是什么？",
                List.of(new QueryArticleHit(
                        QueryEvidenceType.SOURCE,
                        "ops/config.md#0",
                        "ops/config.md",
                        "external.service.apiKey: plain-api-key-123456\nexternal.service.timeout: 3s",
                        "{\"filePath\":\"ops/config.md\"}",
                        List.of("ops/config.md"),
                        2.0D
                ))
        );

        assertThat(answerPayload.getAnswerMarkdown()).contains("external.service.apiKey: <masked>");
        assertThat(answerPayload.getAnswerMarkdown()).doesNotContain("plain-api-key-123456");
    }

    /**
     * 验证 flow 类 fallback 会优先选择真正的主链路句，而不是 README 导读句。
     */
    @Test
    void shouldPreferFlowSignalSentenceOverGuideSentence() {
        AnswerGenerationService answerGenerationService = new AnswerGenerationService();

        QueryAnswerPayload answerPayload = answerGenerationService.fallbackPayload(
                "这个项目的运行流程是什么样的呢",
                List.of(new QueryArticleHit(
                        QueryEvidenceType.SOURCE,
                        "README.md#0",
                        "README.md",
                        """
                                # 邪修智库（Lattice-java）

                                如果你只想先判断这个项目值不值得继续看，先抓住 4 句话：

                                - 它消费的是编译后的知识资产，不是把原始 chunk 直接塞给模型。
                                - 它把 `compile graph` 和 `query graph` 做成了两条正式主链，而不是一条临时 prompt 流。
                                """,
                        "{\"filePath\":\"README.md\"}",
                        List.of("README.md"),
                        2.0D
                )),
                AnswerOutcome.SUCCESS
        );

        assertThat(answerPayload.getAnswerOutcome()).isEqualTo(AnswerOutcome.SUCCESS);
        assertThat(answerPayload.getAnswerMarkdown()).contains("compile graph");
        assertThat(answerPayload.getAnswerMarkdown()).contains("query graph");
        assertThat(answerPayload.getAnswerMarkdown()).doesNotContain("如果你只想先判断这个项目值不值得继续看");
    }

    /**
     * 验证 flow 类问题会优先命中真正主链路句，而不是 README 开头导读语。
     */
    @Test
    void shouldPreferMainFlowSnippetOverGuideSentenceForFlowQuestion() {
        AnswerGenerationService answerGenerationService = new AnswerGenerationService();

        QueryAnswerPayload answerPayload = answerGenerationService.fallbackPayload(
                "这个项目的运行流程是什么样的呢",
                List.of(new QueryArticleHit(
                        QueryEvidenceType.SOURCE,
                        "README.md#0",
                        "README.md",
                        """
                                # 邪修智库（Lattice-java）

                                如果你只想先判断这个项目值不值得继续看，先抓住 4 句话：

                                > 一个把资料编译成知识资产，再统一提供问答、反馈、治理和开发接入能力的 Java 后端。

                                `资料源 -> 知识编译 -> 证据化问答 -> 反馈沉淀 -> 快照治理 -> 多入口复用`

                                - 编译后的知识资产才是问答主链的输入，问答结果还会继续进入反馈和治理链路。
                                """,
                        "{\"filePath\":\"README.md\"}",
                        List.of("README.md"),
                        2.0D
                )),
                AnswerOutcome.SUCCESS
        );

        assertThat(answerPayload.getAnswerOutcome()).isEqualTo(AnswerOutcome.SUCCESS);
        assertThat(answerPayload.getAnswerMarkdown()).contains("资料源 -> 知识编译 -> 证据化问答 -> 反馈沉淀 -> 快照治理 -> 多入口复用");
        assertThat(answerPayload.getAnswerMarkdown()).doesNotContain("如果你只想先判断这个项目值不值得继续看");
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
     * 构造版本差异与旁证样例。
     *
     * @return 版本差异与旁证
     */
    private List<QueryArticleHit> buildVersionDiffEvidence() {
        return List.of(
                new QueryArticleHit(
                        1L,
                        "legacy-default--版本差异说明",
                        "版本差异说明",
                        "版本差异说明",
                        """
                        旧版实现是 GatewayPayLegacy，V2 实现是 GatewayPayV2。
                        routeLegacy(2) 会在旧版/新版实现之间按配置切换，routeV2(11) 走 V2 实现。
                        旧版正向流程是权益核销后走支付，V2 正向流程是查券、支付、coupon 服务核销。
                        旧版逆向流程是权益冲正/退款后走支付冲正/退款；V2 冲正是 coupon 服务冲正后走支付冲正，退款是支付退款后走 coupon 服务退款。
                        """,
                        "{\"description\":\"渠道版本差异\"}",
                        List.of("variant-diff-notes.md"),
                        4.0D
                ),
                new QueryArticleHit(
                        2L,
                        "legacy-default--重试模式说明",
                        "重试模式说明",
                        "重试模式说明",
                        "重试机制文档仅描述定时补偿、MQ 延迟重投、Spring Retry 等机制。",
                        "{\"description\":\"重试机制现状\"}",
                        List.of("retry-pattern-notes.md"),
                        1.0D
                )
        );
    }

    /**
     * 构造网关字段定义表格样例。
     *
     * @return 网关字段定义证据
     */
    private List<QueryArticleHit> buildGatewayFieldDefinitionEvidence() {
        return List.of(new QueryArticleHit(
                1L,
                "legacy-default--field-definitions",
                "gateway-field-definitions",
                "Gateway Field Definitions",
                """
                # Gateway Field Definitions

                本文基于网关透传的交易报文参数定义，说明多个渠道在请求（requestData）和响应（responseData）中各字段的定义、类型、长度、使用情况及取值枚举。

                ## 1. 入参 requestData 字段定义

                ### 1.1 字段通用属性

                | # | 字段名 | 类型 | 长度 | 说明 |
                |---|--------|------|------|------|
                | 1 | transactionType | string | 3 | 交易类型，传的是编号。 |
                | 2 | storeId | string | 8 | 门店号 |
                | 3 | stationId | string | 3 | POS机编号 |
                | 4 | orderId | string | 30 | 商户交易订单号(唯一)，最大30位 |
                | 5 | operatorId | string | 8 | 营业员编号 |
                | 6 | transDate | string | 8 | 交易日期，格式YYYYMMdd |
                | 7 | transTime | string | 6 | 交易时间，格式HHMMSS |
                | 8 | amount | string | 20 | 交易金额，单位元，小数点2位 |
                | 9 | orgTransDate | string | 8 | 原交易日期（退货用） |
                | 10 | orgAuthCode | string | 6 | 原交易授权码（退货用） |
                | 11 | orgTransTrace | string | 24 | 杉德新增字段：原交易流水号6位 |
                | 12 | goodsDetail | json | Text | 易百新增字段，商品明细 |
                | 13 | goodsTag | string | 16 | 易百新增字段：特殊扩展字段 |

                ### 1.2 各渠道字段使用及枚举值对照表

                ## 2. 出参 responseData 字段定义

                出参共15个字段，编号为1-12、14、15、16，缺失13号编号。

                ### 2.1 字段通用属性

                | # | 字段名 | 类型 | 长度 | 说明 |
                |---|--------|------|------|------|
                | 1 | transactionType | string | 3 | 交易类型，与入参枚举值相同 |
                | 2 | orderId | string | 30 | 商户交易订单号(唯一) |
                | 3 | respCode | string | 2 | 返回码2位，00成功 |
                | 4 | message | string | Text | 返回码描述 |
                | 5 | cardNo | string | 20 | 支付卡号 |
                | 6 | cardType | string | 20 | 支付卡类别说明 |
                | 7 | amount | string | 20 | 交易金额，单位元，小数点2位 |
                | 8 | transTrace | string | 24 | 交易流水号6位 |
                | 9 | authCode | string | 6 | 交易授权码 |
                | 10 | refNo | string | 12 | 交易参考号 |
                | 11 | transDate | string | 8 | 交易日期，格式YYYYMMdd |
                | 12 | transTime | string | 6 | 交易时间，格式HHMMSS |
                | 14 | printStr | string | Text | 打印信息 |
                | 15 | rs232Data | string | Text | 杉德新增字段，给POS的响应参数 |
                | 16 | tenderCode | string | 8 | 易百新增字段：表示为某活动，不同银行活动不同，Tender 编号 |

                ### 2.2 各渠道字段使用及枚举值对照表

                ## 3. 交易类型 transactionType 对照表

                | 编码 | 含义 |
                |------|------|
                | 01 | 查余额 |
                | 02 | 消费 |
                | 04 | 退款 |
                | 99 | 管理 |

                ## 4. 各渠道支持的业务类型
                """,
                "{\"description\":\"网关交易报文字段定义\"}",
                List.of("field-definitions.xlsx"),
                4.0D
        ));
    }

    /**
     * 构造更接近真实 source_files.content_text 的原始表格文本证据。
     *
     * @return 原始字段定义证据
     */
    private List<QueryArticleHit> buildRawGatewayFieldDefinitionEvidence() {
        return List.of(new QueryArticleHit(
                QueryEvidenceType.SOURCE,
                1L,
                null,
                "field-definitions.xlsx",
                "Gateway Field Definitions",
                """
                === Sheet: 接口参数 ===
                ,网关透传的交易报文参数定义
                ,,入参requestData,,,,,渠道A,,渠道B,,渠道C,,渠道D,,渠道E,,渠道F,
                ,#,字段,类型,字段长度,举例,说明,是否使用,枚举值
                ,1,transactionType,string,3,31,交易类型，传的是编号。编号请参考右侧各个渠道的定义。,是,01查余额 02消费 04退款
                ,8,amount,string,20,33,交易金额，单位元，小数点2位,是,
                ,12,goodsDetail,json,Text,"goodsDetail":[{"goodsCategory":"","goodsId":"1113134","price":37}],扩展字段，商品明细：用于校验产品信息 goodsCategory：MajorGroup goodsId：POSKEY price：价格，单元：元,否,,否,,是
                ,,出参responseData,,,,,渠道A,,渠道B,,渠道C,
                ,#,字段,类型,,举例,说明,是否使用,枚举值
                ,1,transactionType,string,3,1,01 消费 02 退货 03 结账 04 查询 ...,是,
                ,3,respCode,string,2,0,返回码2位 00成功,是,
                ,7,amount,string,20,33,交易金额，单位元，小数点2位,是,
                ,交易类型transactionType对照表
                ,编码,含义
                ,01,查余额
                ,02,消费
                ,04,退款
                """,
                "{\"description\":\"网关 requestData / responseData 字段定义\"}",
                List.of("field-definitions.xlsx"),
                4.0D
        ));
    }

    /**
     * 构造通用字段定义证据。
     *
     * @return 通用字段定义证据
     */
    private List<QueryArticleHit> buildGenericFieldDefinitionEvidence() {
        return List.of(new QueryArticleHit(
                QueryEvidenceType.SOURCE,
                1L,
                null,
                "order-api-fields.xlsx",
                "Order API 字段定义",
                """
                === Sheet: Order API ===
                ,#,字段,类型,长度,说明
                ,1,orderStatus,string,16,订单状态，取值 PENDING/PAID/CLOSED
                ,2,retryCount,int,4,支付重试次数
                """,
                "{\"description\":\"Order API 字段定义\"}",
                List.of("order-api-fields.xlsx"),
                4.0D
        ));
    }

    /**
     * 构造通用阶段目标证据。
     *
     * @return 阶段目标证据
     */
    private List<QueryArticleHit> buildStageTargetEvidence() {
        return List.of(
                new QueryArticleHit(
                        QueryEvidenceType.ARTICLE,
                        1L,
                        "stage-target-guide",
                        "stage-target-guide",
                        "标准体系建设指南",
                        """
                        到2027年，完成重点场景标准体系建设并发布30项以上关键标准。
                        到2030年，形成协同完善的标准体系和持续迭代机制。
                        """,
                        "{\"description\":\"阶段要求\"}",
                        List.of("standard-guide.pdf"),
                        4.0D
                ),
                new QueryArticleHit(
                        QueryEvidenceType.SOURCE,
                        1L,
                        null,
                        "standard-guide.pdf#0",
                        "standard-guide.pdf",
                        """
                        到2027年，完成重点场景标准体系建设并发布30项以上关键标准。
                        到2030年，形成协同完善的标准体系和持续迭代机制。
                        """,
                        "{\"filePath\":\"standard-guide.pdf\"}",
                        List.of("standard-guide.pdf"),
                        3.0D
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
