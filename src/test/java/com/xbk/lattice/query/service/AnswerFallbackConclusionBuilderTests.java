package com.xbk.lattice.query.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AnswerFallbackConclusionBuilder 测试
 *
 * 职责：验证确定性 fallback 结论行的基础编排
 *
 * @author xiexu
 */
class AnswerFallbackConclusionBuilderTests {

    private final AnswerGenerationService support = new AnswerGenerationService();

    private final AnswerFallbackConclusionBuilder conclusionBuilder = new AnswerFallbackConclusionBuilder(support);

    /**
     * 验证无证据时结论列表为空，由 Markdown 构建器负责缺省文案。
     */
    @Test
    void shouldReturnEmptyConclusionWhenEvidenceMissing() {
        List<String> conclusionLines = conclusionBuilder.buildEvidenceConclusionLines(
                "timeout 配置是多少",
                List.<QueryArticleHit>of(),
                support.extractQueryTokens("timeout 配置是多少")
        );

        assertThat(conclusionLines).isEmpty();
    }

    /**
     * 验证普通证据会生成带 citation 的结论行。
     */
    @Test
    void shouldBuildConclusionLineWithCitation() {
        QueryArticleHit sourceHit = new QueryArticleHit(
                QueryEvidenceType.SOURCE,
                "timeout-rule",
                "Timeout Rule",
                "content: timeout_seconds = 30",
                "",
                List.of("docs/rule.md"),
                0.8
        );

        List<String> conclusionLines = conclusionBuilder.buildEvidenceConclusionLines(
                "timeout 配置是多少",
                List.of(sourceHit),
                support.extractQueryTokens("timeout 配置是多少")
        );

        assertThat(conclusionLines).hasSize(1);
        assertThat(conclusionLines.get(0)).contains("timeout_seconds = 30");
        assertThat(conclusionLines.get(0)).contains("[→ docs/rule.md]");
    }

    /**
     * 验证 structured fact 问题中，terminal unit 的 displayText exact line
     * 被优先作为结论输出，而不是 ARTICLE 的冗长段落。
     */
    @Test
    void shouldOutputTerminalUnitDisplayTextAsConclusionForStructuredFactQuestion() {
        QueryArticleHit articleHit = new QueryArticleHit(
                QueryEvidenceType.ARTICLE,
                "daily-limit-summary",
                "Daily Limit Summary",
                "服务配额说明：每日请求上限相关配置，建议根据业务量动态调整。",
                "{\"description\":\"服务配额摘要\"}",
                List.of("docs/service-quota.md"),
                2.5D
        );
        QueryArticleHit terminalUnitHit = new QueryArticleHit(
                QueryEvidenceType.FACT_CARD,
                "terminal-unit:quota:daily-limit",
                "serviceQuota.dailyLimit",
                "serviceQuota.dailyLimit = 128\nparentPath: serviceQuota; field: dailyLimit; valueType: number\n[\"dailyLimit\",\"daily limit\",\"单日上限\"]",
                "{\"terminalUnitIdentity\":\"terminal-unit:quota:daily-limit\",\"channel\":\"fact_card_terminal_fts\",\"keyPath\":\"serviceQuota.dailyLimit\",\"terminalKey\":\"dailyLimit\",\"value\":\"128\",\"valueType\":\"number\",\"displayText\":\"serviceQuota.dailyLimit = 128\"}",
                List.of("docs/service-quota.yaml"),
                3.0D
        );

        List<String> lines = conclusionBuilder.buildEvidenceConclusionLines(
                "serviceQuota.dailyLimit 的最大值是多少",
                List.of(articleHit, terminalUnitHit),
                support.extractQueryTokens("serviceQuota.dailyLimit 的最大值是多少")
        );

        assertThat(lines).isNotEmpty();
        assertThat(lines.get(0))
                .as("conclusion should output displayText exact value, not alias JSON")
                .contains("serviceQuota.dailyLimit = 128");
        assertThat(lines.get(0))
                .as("conclusion should not output raw alias JSON bracket")
                .doesNotContain("[\"")
                .doesNotContain("[\"daily");
        assertThat(lines.get(0))
                .contains("[→ docs/service-quota.yaml]");
    }

    /**
     * 验证 terminal unit 不是 primary hit（排在 ARTICLE 之后）时也能被消费。
     */
    @Test
    void shouldConsumeNonPrimaryTerminalUnit() {
        QueryArticleHit articleHit = new QueryArticleHit(
                QueryEvidenceType.ARTICLE,
                "tier-summary",
                "Tier Summary",
                "服务等级配置说明。",
                "{\"description\":\"等级摘要\"}",
                List.of("docs/tier-config.md"),
                3.0D
        );
        QueryArticleHit terminalUnitHit = new QueryArticleHit(
                QueryEvidenceType.FACT_CARD,
                "terminal-unit:tier:active",
                "runtimeProfile.activeTier",
                "runtimeProfile.activeTier = gold\n[\"activeTier\",\"active tier\",\"服务等级\"]",
                "{\"terminalUnitIdentity\":\"terminal-unit:tier:active\",\"channel\":\"fact_card_terminal_fts\",\"keyPath\":\"runtimeProfile.activeTier\",\"terminalKey\":\"activeTier\",\"value\":\"gold\",\"valueType\":\"string\",\"displayText\":\"runtimeProfile.activeTier = gold\"}",
                List.of("docs/tier-config.yaml"),
                2.0D
        );

        List<String> lines = conclusionBuilder.buildEvidenceConclusionLines(
                "runtimeProfile.activeTier 的值是多少",
                List.of(articleHit, terminalUnitHit),
                support.extractQueryTokens("runtimeProfile.activeTier 的值是多少")
        );

        assertThat(lines).isNotEmpty();
        assertThat(lines.get(0))
                .as("non-primary terminal unit should still be consumed")
                .contains("runtimeProfile.activeTier = gold");
    }

    /**
     * 验证普通描述性问题中 terminal unit 不抢占 ARTICLE 结论。
     */
    @Test
    void shouldNotOutputTerminalUnitForDescriptiveQuestion() {
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
                "gatewayConfig.requestLimit = 5000\n[\"requestLimit\",\"request limit\"]",
                "{\"terminalUnitIdentity\":\"terminal-unit:gateway:request-limit\",\"channel\":\"fact_card_terminal_fts\",\"keyPath\":\"gatewayConfig.requestLimit\",\"terminalKey\":\"requestLimit\",\"value\":\"5000\",\"valueType\":\"number\",\"displayText\":\"gatewayConfig.requestLimit = 5000\"}",
                List.of("docs/gateway-config.yaml"),
                3.0D
        );

        List<String> lines = conclusionBuilder.buildEvidenceConclusionLines(
                "系统的整体概述是什么",
                List.of(articleHit, terminalUnitHit),
                support.extractQueryTokens("系统的整体概述是什么")
        );

        assertThat(lines).isNotEmpty();
        assertThat(lines.get(0))
                .as("descriptive question should not output terminal unit exact line")
                .doesNotContain("gatewayConfig.requestLimit = 5000");
    }

    /**
     * 验证不相关的 terminal unit 不被错误消费。
     */
    @Test
    void shouldNotConsumeIrrelevantTerminalUnit() {
        QueryArticleHit articleHit = new QueryArticleHit(
                QueryEvidenceType.ARTICLE,
                "cache-policy-summary",
                "Cache Policy Summary",
                "缓存策略默认生存时间为三百秒。",
                "{\"description\":\"缓存策略摘要\"}",
                List.of("docs/cache-policy.md"),
                2.5D
        );
        QueryArticleHit terminalUnitHit = new QueryArticleHit(
                QueryEvidenceType.FACT_CARD,
                "terminal-unit:runtime:retry",
                "runtimeConfig.maxRetryCount",
                "runtimeConfig.maxRetryCount = 3\n[\"maxRetryCount\",\"max retry count\"]",
                "{\"terminalUnitIdentity\":\"terminal-unit:runtime:retry\",\"channel\":\"fact_card_terminal_fts\",\"keyPath\":\"runtimeConfig.maxRetryCount\",\"terminalKey\":\"maxRetryCount\",\"value\":\"3\",\"valueType\":\"number\",\"displayText\":\"runtimeConfig.maxRetryCount = 3\"}",
                List.of("docs/runtime-config.yaml"),
                3.0D
        );

        List<String> lines = conclusionBuilder.buildEvidenceConclusionLines(
                "缓存策略默认的生存时间是多久",
                List.of(articleHit, terminalUnitHit),
                support.extractQueryTokens("缓存策略默认的生存时间是多久")
        );

        assertThat(lines).isNotEmpty();
        assertThat(lines.get(0))
                .as("irrelevant terminal unit should not be consumed")
                .doesNotContain("maxRetryCount");
    }

    /**
     * 验证没有 terminal unit 时，现有 SOURCE 结论行为保持不变。
     */
    @Test
    void shouldPreserveExistingConclusionBehaviorWithoutTerminalUnit() {
        QueryArticleHit sourceHit = new QueryArticleHit(
                QueryEvidenceType.SOURCE,
                "sample-config",
                "Sample Config",
                "sample_limit = 64\nmax_batch_size = 32",
                "",
                List.of("docs/sample-config.md"),
                0.8D
        );

        List<String> lines = conclusionBuilder.buildEvidenceConclusionLines(
                "sample_limit 的配置值是多少",
                List.of(sourceHit),
                support.extractQueryTokens("sample_limit 的配置值是多少")
        );

        assertThat(lines).isNotEmpty();
        assertThat(lines.get(0)).contains("sample_limit = 64");
        assertThat(lines.get(0)).contains("[→ docs/sample-config.md]");
    }
}
