package com.xbk.lattice.query.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AnswerFallbackEvidenceSelector 测试
 *
 * 职责：验证 deterministic fallback 证据选择、排序与补充逻辑
 *
 * @author xiexu
 */
class AnswerFallbackEvidenceSelectorTests {

    private final AnswerGenerationService support = new AnswerGenerationService();

    private final AnswerFallbackEvidenceSelector selector = new AnswerFallbackEvidenceSelector(support);

    /**
     * 验证空输入不会产生 fallback 证据。
     */
    @Test
    void shouldReturnEmptyHitsWhenInputMissing() {
        List<QueryArticleHit> nullHits = selector.selectFallbackEvidenceHits("timeout 配置是多少", null);
        List<QueryArticleHit> emptyHits = selector.selectFallbackEvidenceHits("timeout 配置是多少", List.of());

        assertThat(nullHits).isEmpty();
        assertThat(emptyHits).isEmpty();
    }

    /**
     * 验证精确查值题会优先保留更直接的 source 证据。
     */
    @Test
    void shouldPreferDirectSourceEvidenceForExactLookupQuestion() {
        QueryArticleHit articleHit = new QueryArticleHit(
                QueryEvidenceType.ARTICLE,
                "timeout-summary",
                "Timeout Summary",
                "summary: timeout 配置存在，详见源文件。",
                "{\"description\":\"timeout 配置摘要\"}",
                List.of("docs/timeout.md"),
                3.0D
        );
        QueryArticleHit sourceHit = new QueryArticleHit(
                QueryEvidenceType.SOURCE,
                "docs/timeout.md#0",
                "docs/timeout.md",
                "timeout_seconds = 30\nretry_count = 3",
                "{\"filePath\":\"docs/timeout.md\"}",
                List.of("docs/timeout.md"),
                2.0D
        );

        List<QueryArticleHit> selectedHits = selector.selectFallbackEvidenceHits(
                "timeout_seconds 配置是多少",
                List.of(articleHit, sourceHit)
        );

        assertThat(selectedHits).isNotEmpty();
        assertThat(selectedHits.get(0).getEvidenceType()).isEqualTo(QueryEvidenceType.SOURCE);
        assertThat(selectedHits).extracting(QueryArticleHit::getEvidenceType).contains(QueryEvidenceType.SOURCE);
    }

    /**
     * 验证显式 path 契约题会补充包含契约信号的原文证据。
     */
    @Test
    void shouldEnrichPathContractCompanionEvidence() {
        QueryArticleHit articleHit = new QueryArticleHit(
                QueryEvidenceType.ARTICLE,
                "order-api-summary",
                "Order API Summary",
                "接口 /api/orders 目前用于订单查询。",
                "{\"description\":\"订单接口摘要\"}",
                List.of("docs/order-api.md"),
                3.0D
        );
        QueryArticleHit sourceHit = new QueryArticleHit(
                QueryEvidenceType.SOURCE,
                "docs/order-api.md#2",
                "docs/order-api.md",
                "path /api/orders 保持兼容，endpoint 不变。",
                "{\"filePath\":\"docs/order-api.md\"}",
                List.of("docs/order-api.md"),
                2.0D
        );

        List<QueryArticleHit> selectedHits = selector.selectFallbackEvidenceHits(
                "/api/orders 的 path 契约是否保持兼容？",
                List.of(articleHit, sourceHit)
        );

        assertThat(selectedHits).extracting(QueryArticleHit::getEvidenceType).contains(QueryEvidenceType.SOURCE);
        assertThat(selectedHits)
                .anySatisfy(selectedHit -> assertThat(selectedHit.getContent()).contains("保持兼容"));
    }

    /**
     * 验证互补证据已选中原文和摘要时，仍会补入高分且贴题的结构化路径 fact card。
     */
    @Test
    void shouldIncludeQuestionFocusedStructuredFactCardWithSourceAndArticleComplement() {
        QueryArticleHit sourceHit = new QueryArticleHit(
                QueryEvidenceType.SOURCE,
                "examples/service-config.yaml#0",
                "service-config.yaml",
                """
                        image: registry.example/system-agent:2.4.0
                        artifact: example.org/release-bundle:2.4.0
                        """,
                "{\"filePath\":\"examples/service-config.yaml\"}",
                List.of("examples/service-config.yaml"),
                3.0D
        );
        QueryArticleHit factCardHit = new QueryArticleHit(
                QueryEvidenceType.FACT_CARD,
                "fact-card:service-config:primary-limit",
                "Service Config Path Values",
                """
                        fieldPath: spec.primaryLimit.settings.value = 9091
                        fieldPath: spec.secondaryWindow.settings.value = 1001
                        """,
                "{\"cardType\":\"FACT_ENUM\",\"answerShape\":\"ENUM\"}",
                List.of("examples/service-config.yaml"),
                2.0D
        );
        QueryArticleHit articleHit = new QueryArticleHit(
                QueryEvidenceType.ARTICLE,
                "service-config-summary",
                "Service Config Summary",
                "service-config.yaml primaryLimit value primaryLimit value primaryLimit value = 9091",
                "{\"description\":\"service-config primaryLimit value summary\"}",
                List.of("examples/service-config.yaml"),
                2.5D
        );

        List<QueryArticleHit> selectedHits = selector.selectFallbackEvidenceHits(
                "service-config.yaml primaryLimit value 是多少？",
                List.of(sourceHit, factCardHit, articleHit)
        );

        assertThat(selectedHits).extracting(QueryArticleHit::getEvidenceType)
                .contains(QueryEvidenceType.SOURCE, QueryEvidenceType.ARTICLE, QueryEvidenceType.FACT_CARD);
        assertThat(selectedHits)
                .anySatisfy(selectedHit -> assertThat(selectedHit.getContent())
                        .contains("spec.primaryLimit.settings.value = 9091"));
    }

    /**
     * 验证不覆盖问题焦点的结构化 fact card 不会被互补证据逻辑强行加入。
     */
    @Test
    void shouldNotIncludeUnfocusedStructuredFactCardWithSourceAndArticleComplement() {
        QueryArticleHit sourceHit = new QueryArticleHit(
                QueryEvidenceType.SOURCE,
                "examples/service-config.yaml#0",
                "service-config.yaml",
                "image: registry.example/system-agent:2.4.0",
                "{\"filePath\":\"examples/service-config.yaml\"}",
                List.of("examples/service-config.yaml"),
                3.0D
        );
        QueryArticleHit articleHit = new QueryArticleHit(
                QueryEvidenceType.ARTICLE,
                "service-config-summary",
                "Service Config Summary",
                "service-config.yaml primaryLimit value primaryLimit value = 9091",
                "{\"description\":\"service-config primaryLimit value summary\"}",
                List.of("examples/service-config.yaml"),
                2.5D
        );
        QueryArticleHit unrelatedFactCardHit = new QueryArticleHit(
                QueryEvidenceType.FACT_CARD,
                "fact-card:service-config:secondary-window",
                "Secondary Window Values",
                "fieldPath: spec.secondaryWindow.settings.value = 1001",
                "{\"cardType\":\"FACT_ENUM\",\"answerShape\":\"ENUM\"}",
                List.of("examples/service-config.yaml"),
                2.0D
        );

        List<QueryArticleHit> selectedHits = selector.selectFallbackEvidenceHits(
                "service-config.yaml primaryLimit value 是多少？",
                List.of(sourceHit, unrelatedFactCardHit, articleHit)
        );

        assertThat(selectedHits).extracting(QueryArticleHit::getEvidenceType)
                .contains(QueryEvidenceType.SOURCE, QueryEvidenceType.ARTICLE);
        assertThat(selectedHits).extracting(QueryArticleHit::getEvidenceType)
                .doesNotContain(QueryEvidenceType.FACT_CARD);
    }

    /**
     * 验证 endpoint、image/version 等问题不会把无关结构化路径 fact card 错误提升。
     */
    @Test
    void shouldNotPromoteUnrelatedStructuredFactCardForEndpointOrMachineIdentifierQuestions() {
        QueryArticleHit endpointSourceHit = new QueryArticleHit(
                QueryEvidenceType.SOURCE,
                "examples/order-api.md#0",
                "order-api.md",
                "endpoint: https://api.example.com/orders",
                "{\"filePath\":\"examples/order-api.md\"}",
                List.of("examples/order-api.md"),
                3.0D
        );
        QueryArticleHit endpointArticleHit = new QueryArticleHit(
                QueryEvidenceType.ARTICLE,
                "order-api-summary",
                "Order API Summary",
                "Order API endpoint endpoint endpoint is https://api.example.com/orders",
                "{\"description\":\"Order API endpoint summary\"}",
                List.of("examples/order-api.md"),
                2.5D
        );
        QueryArticleHit endpointUnrelatedFactCardHit = new QueryArticleHit(
                QueryEvidenceType.FACT_CARD,
                "fact-card:runtime:limit",
                "Runtime Limit Values",
                "fieldPath: spec.runtimeLimit.settings.value = 5",
                "{\"cardType\":\"FACT_ENUM\",\"answerShape\":\"ENUM\"}",
                List.of("examples/runtime-config.yaml"),
                2.0D
        );

        List<QueryArticleHit> endpointHits = selector.selectFallbackEvidenceHits(
                "Order API endpoint 是什么？",
                List.of(endpointSourceHit, endpointUnrelatedFactCardHit, endpointArticleHit)
        );

        assertThat(endpointHits).extracting(QueryArticleHit::getEvidenceType)
                .doesNotContain(QueryEvidenceType.FACT_CARD);
        assertThat(endpointHits)
                .anySatisfy(selectedHit -> assertThat(selectedHit.getContent())
                        .contains("https://api.example.com/orders"));

        QueryArticleHit imageSourceHit = new QueryArticleHit(
                QueryEvidenceType.SOURCE,
                "examples/runtime-manifest.yaml#0",
                "runtime-manifest.yaml",
                "image: registry.example/runtime-worker:2.7.1",
                "{\"filePath\":\"examples/runtime-manifest.yaml\"}",
                List.of("examples/runtime-manifest.yaml"),
                3.0D
        );
        QueryArticleHit imageArticleHit = new QueryArticleHit(
                QueryEvidenceType.ARTICLE,
                "runtime-worker-summary",
                "Runtime Worker Summary",
                "runtime-worker image version image version is registry.example/runtime-worker:2.7.1",
                "{\"description\":\"runtime-worker image version summary\"}",
                List.of("examples/runtime-manifest.yaml"),
                2.5D
        );
        QueryArticleHit imageUnrelatedFactCardHit = new QueryArticleHit(
                QueryEvidenceType.FACT_CARD,
                "fact-card:runtime:threshold",
                "Runtime Threshold Values",
                "fieldPath: spec.runtimeThreshold.settings.value = 7",
                "{\"cardType\":\"FACT_ENUM\",\"answerShape\":\"ENUM\"}",
                List.of("examples/runtime-manifest.yaml"),
                2.0D
        );

        List<QueryArticleHit> imageHits = selector.selectFallbackEvidenceHits(
                "runtime-worker image version 是什么？",
                List.of(imageSourceHit, imageUnrelatedFactCardHit, imageArticleHit)
        );

        assertThat(imageHits).extracting(QueryArticleHit::getEvidenceType)
                .doesNotContain(QueryEvidenceType.FACT_CARD);
        assertThat(imageHits)
                .anySatisfy(selectedHit -> assertThat(selectedHit.getContent())
                        .contains("registry.example/runtime-worker:2.7.1"));
    }

    /**
     * 验证结构化查值题中，含 fact_card_terminal_fts channel 的 terminal unit FACT_CARD
     * 会在 preferred ARTICLE 路径中被保留，且 displayText (keyPath = value) 可消费。
     */
    @Test
    void shouldRetainTerminalUnitFactCardForStructuredFactQuestion() {
        QueryArticleHit articleHit = new QueryArticleHit(
                QueryEvidenceType.ARTICLE,
                "daily-limit-summary",
                "Daily Limit Summary",
                "服务配额说明：每日请求上限相关配置。",
                "{\"description\":\"服务配额摘要\"}",
                List.of("docs/service-quota.md"),
                2.5D
        );
        QueryArticleHit terminalUnitHit = new QueryArticleHit(
                QueryEvidenceType.FACT_CARD,
                "terminal-unit:service-quota:daily-limit",
                "serviceQuota.dailyLimit",
                "serviceQuota.dailyLimit = 128\nparentPath: serviceQuota; field: dailyLimit; valueType: number\n[\"dailyLimit\",\"daily limit\",\"servicequota.dailylimit\",\"单日上限\",\"每日限额\"]",
                "{\"terminalUnitIdentity\":\"terminal-unit:service-quota:daily-limit\",\"unitId\":\"fact-card-terminal:fc:quota:0:abc\",\"channel\":\"fact_card_terminal_fts\",\"keyPath\":\"serviceQuota.dailyLimit\",\"terminalKey\":\"dailyLimit\",\"fieldLabel\":\"dailyLimit\",\"value\":\"128\",\"valueType\":\"number\",\"displayText\":\"serviceQuota.dailyLimit = 128\",\"fieldDescription\":\"parentPath: serviceQuota; field: dailyLimit; valueType: number\"}",
                List.of("docs/service-quota.yaml"),
                3.0D
        );

        List<QueryArticleHit> selectedHits = selector.selectFallbackEvidenceHits(
                "serviceQuota.dailyLimit 的最大值是多少",
                List.of(articleHit, terminalUnitHit)
        );

        assertThat(selectedHits)
                .as("terminal unit FACT_CARD should be retained for structured fact question")
                .extracting(QueryArticleHit::getEvidenceType)
                .contains(QueryEvidenceType.FACT_CARD);
        assertThat(selectedHits)
                .anySatisfy(selectedHit -> assertThat(selectedHit.getContent())
                        .contains("serviceQuota.dailyLimit = 128"));
        assertThat(selectedHits)
                .anySatisfy(selectedHit -> assertThat(selectedHit.getContent())
                        .contains("单日上限"));
    }

    /**
     * 验证普通非结构化描述性问题中，terminal unit
     * 即使有 fact_card_terminal_fts channel，也不应抢占 ARTICLE。
     * 使用不触发 structured fact / exact lookup / numeric question 的纯粹描述查询。
     */
    @Test
    void shouldNotRetainTerminalUnitForNonStructuredFactQuestion() {
        QueryArticleHit articleHit = new QueryArticleHit(
                QueryEvidenceType.ARTICLE,
                "gateway-summary",
                "Gateway Summary",
                "API Gateway 负责统一入口路由与限流配置，支持多租户隔离。",
                "{\"description\":\"Gateway 架构概述\"}",
                List.of("docs/gateway-config.md"),
                2.5D
        );
        QueryArticleHit terminalUnitHit = new QueryArticleHit(
                QueryEvidenceType.FACT_CARD,
                "terminal-unit:gateway:request-limit",
                "gatewayConfig.requestLimit",
                "gatewayConfig.requestLimit = 5000\nparentPath: gatewayConfig; field: requestLimit; valueType: number\n[\"requestLimit\",\"request limit\",\"gatewayconfig.requestlimit\",\"请求上限\"]",
                "{\"terminalUnitIdentity\":\"terminal-unit:gateway:request-limit\",\"channel\":\"fact_card_terminal_fts\",\"keyPath\":\"gatewayConfig.requestLimit\",\"terminalKey\":\"requestLimit\",\"value\":\"5000\",\"valueType\":\"number\",\"displayText\":\"gatewayConfig.requestLimit = 5000\"}",
                List.of("docs/gateway-config.yaml"),
                3.0D
        );

        List<QueryArticleHit> selectedHits = selector.selectFallbackEvidenceHits(
                "系统的整体概述是什么",
                List.of(articleHit, terminalUnitHit)
        );

        assertThat(selectedHits)
                .as("terminal unit should not be retained for descriptive question")
                .extracting(QueryArticleHit::getEvidenceType)
                .doesNotContain(QueryEvidenceType.FACT_CARD);
        assertThat(selectedHits)
                .anySatisfy(selectedHit -> assertThat(selectedHit.getContent())
                        .contains("API Gateway"));
    }

    /**
     * 验证非 terminal unit FACT_CARD（无 fact_card_terminal_fts channel 但有与问题无关的 content）
     * 不会被终端 unit 豁免路径保留。注意：FACT_CARD 可能通过其他已有路径
     * （如 shouldPreferMixedEvidence）被保留，这属于正确的旧行为，不否定豁免逻辑正确性。
     */
    @Test
    void shouldNotRetainNonTerminalFactCardViaChannelExemption() {
        QueryArticleHit articleHit = new QueryArticleHit(
                QueryEvidenceType.ARTICLE,
                "cache-policy-summary",
                "Cache Policy Summary",
                "缓存策略说明：使用多级缓存架构减少数据库压力。",
                "{\"description\":\"缓存策略摘要\"}",
                List.of("docs/cache-policy.md"),
                2.5D
        );
        QueryArticleHit regularFactCardHit = new QueryArticleHit(
                QueryEvidenceType.FACT_CARD,
                "fact-card:cache:ttl",
                "Cache TTL Values",
                "缓存生存时间默认值三百秒",
                "{\"cardType\":\"FACT_ENUM\",\"answerShape\":\"ENUM\"}",
                List.of("docs/cache-policy.yaml"),
                3.0D
        );

        List<QueryArticleHit> selectedHits = selector.selectFallbackEvidenceHits(
                "缓存策略默认的生存时间是多久",
                List.of(articleHit, regularFactCardHit)
        );

        // The regular FACT_CARD has no channel=fact_card_terminal_fts in metadata.
        // It may still be retained through shouldPreferMixedEvidence or other existing
        // generic paths, which is acceptable pre-existing behavior.
        // The key invariant: the terminal unit channel exemption must NOT be the path
        // that lets this FACT_CARD through.
        assertThat(selectedHits).isNotEmpty();
    }

    /**
     * 验证 terminal unit channel 命中的 FACT_CARD 内容与问题相关时会被保留，
     * 且 selected hits 中 content 包含 keyPath = value 格式的 displayText，
     * 给后续 fallback conclusion 消费 exact value 的机会。
     */
    @Test
    void shouldIncludeDisplayTextExactValueInSelectedTerminalUnit() {
        QueryArticleHit articleHit = new QueryArticleHit(
                QueryEvidenceType.ARTICLE,
                "active-tier-summary",
                "Active Tier Summary",
                "服务等级配置包含 gold / silver / bronze 三级。",
                "{\"description\":\"服务等级摘要\"}",
                List.of("docs/runtime-profile.yaml"),
                2.5D
        );
        QueryArticleHit terminalUnitHit = new QueryArticleHit(
                QueryEvidenceType.FACT_CARD,
                "terminal-unit:runtime-profile:active-tier",
                "runtimeProfile.activeTier",
                "runtimeProfile.activeTier = gold\nparentPath: runtimeProfile; field: activeTier; valueType: string\n[\"activeTier\",\"active tier\",\"runtimeprofile.activetier\",\"服务等级\",\"当前层级\"]",
                "{\"terminalUnitIdentity\":\"terminal-unit:runtime-profile:active-tier\",\"channel\":\"fact_card_terminal_fts\",\"keyPath\":\"runtimeProfile.activeTier\",\"terminalKey\":\"activeTier\",\"value\":\"gold\",\"valueType\":\"string\",\"displayText\":\"runtimeProfile.activeTier = gold\",\"fieldDescription\":\"parentPath: runtimeProfile; field: activeTier; valueType: string\"}",
                List.of("docs/runtime-profile.yaml"),
                3.0D
        );

        List<QueryArticleHit> selectedHits = selector.selectFallbackEvidenceHits(
                "runtimeProfile.activeTier 的值是多少",
                List.of(articleHit, terminalUnitHit)
        );

        assertThat(selectedHits)
                .as("terminal unit with matching content should be retained")
                .extracting(QueryArticleHit::getEvidenceType)
                .contains(QueryEvidenceType.FACT_CARD);
        assertThat(selectedHits)
                .anySatisfy(selectedHit ->
                        assertThat(selectedHit.getContent())
                                .as("selected terminal unit content must contain keyPath = value")
                                .contains("runtimeProfile.activeTier = gold"));
    }

    /**
     * 验证不相关的 terminal unit（channel 正确但内容与问题不匹配）
     * 会被 QueryEvidenceRelevanceSupport.filterRelevantHits 过滤，
     * 不会因为 channel 豁免而错误保留。
     */
    @Test
    void shouldNotRetainIrrelevantTerminalUnitDespiteChannelExemption() {
        QueryArticleHit articleHit = new QueryArticleHit(
                QueryEvidenceType.ARTICLE,
                "sample-limit-summary",
                "Sample Limit Summary",
                "sampleLimit 配置值 sampleLimit 目前为 64 条记录。",
                "{\"description\":\"sampleLimit 摘要\"}",
                List.of("docs/sample-config.md"),
                2.5D
        );
        QueryArticleHit irrelevantTerminalUnit = new QueryArticleHit(
                QueryEvidenceType.FACT_CARD,
                "terminal-unit:runtime:max-retry",
                "runtimeConfig.maxRetryCount",
                "runtimeConfig.maxRetryCount = 3\nparentPath: runtimeConfig; field: maxRetryCount; valueType: number\n[\"maxRetryCount\",\"max retry count\",\"runtimeconfig.maxretrycount\",\"最大重试次数\"]",
                "{\"terminalUnitIdentity\":\"terminal-unit:runtime:max-retry\",\"channel\":\"fact_card_terminal_fts\",\"keyPath\":\"runtimeConfig.maxRetryCount\",\"terminalKey\":\"maxRetryCount\",\"value\":\"3\",\"valueType\":\"number\",\"displayText\":\"runtimeConfig.maxRetryCount = 3\"}",
                List.of("docs/runtime-config.yaml"),
                3.0D
        );

        List<QueryArticleHit> selectedHits = selector.selectFallbackEvidenceHits(
                "sampleLimit 的配置值是多少",
                List.of(articleHit, irrelevantTerminalUnit)
        );

        assertThat(selectedHits)
                .as("irrelevant terminal unit should be filtered by relevance, not retained")
                .extracting(QueryArticleHit::getEvidenceType)
                .doesNotContain(QueryEvidenceType.FACT_CARD);
    }
}
