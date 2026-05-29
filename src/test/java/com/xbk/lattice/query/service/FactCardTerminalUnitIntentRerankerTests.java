package com.xbk.lattice.query.service;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FactCardTerminalUnitIntentReranker 测试
 *
 * 职责：验证 terminal unit FTS 命中在字段意图重排后，同 parentPath sibling 内
 * 字段命中优先于 value 命中，数值 valueType 只小幅加权，metadata 缺失时安全降级。
 *
 * @author xiexu
 */
class FactCardTerminalUnitIntentRerankerTests {

    private final FactCardTerminalUnitIntentReranker reranker = new FactCardTerminalUnitIntentReranker(new QuerySemanticRules());

    /**
     * 同 parent sibling 中，一个 hit 的中文 value 命中 query，另一个 hit 的
     * terminalKey 更符合字段意图，rerank 后字段意图 hit 在前。
     */
    @Test
    void shouldPrioritizeFieldIntentHitOverValueOnlyHitWithinSameParent() {
        QueryArticleHit valueOnlyHit = hit(
                "alpha_limit_group", "alpha_limit_group",
                "group_name", "root.settings",
                "配置名称示例文本", "string",
                "root.settings.group_name = 配置名称示例文本",
                8.0);

        QueryArticleHit fieldIntentHit = hit(
                "alpha_limit_value", "alpha_limit_value",
                "alpha_limit", "root.settings",
                "31", "number",
                "root.settings.alpha_limit = 31",
                3.0);

        List<QueryArticleHit> original = Arrays.asList(valueOnlyHit, fieldIntentHit);
        List<QueryArticleHit> result = reranker.rerank(original,
                "alpha_limit 的配置名称示例文本最大值是多少");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getArticleKey())
                .as("field-intent hit (alpha_limit) should rank first")
                .isEqualTo("alpha_limit_value");
        assertThat(result.get(1).getArticleKey())
                .isEqualTo("alpha_limit_group");
    }

    /**
     * valueType=number 只小幅加权，不能单独压过明确字段 token。
     */
    @Test
    void shouldNotLetNumericValueTypeOverrideExplicitFieldTokenMatch() {
        QueryArticleHit numericButNoFieldMatch = hit(
                "beta_count", "beta_count",
                "beta_count", "root.config",
                "42", "number",
                "root.config.beta_count = 42",
                5.0);

        QueryArticleHit stringButFieldMatch = hit(
                "renewal_period", "renewal_period",
                "renewal_period", "root.config",
                "monthly", "string",
                "root.config.renewal_period = monthly",
                3.0);

        List<QueryArticleHit> original = Arrays.asList(numericButNoFieldMatch, stringButFieldMatch);
        List<QueryArticleHit> result = reranker.rerank(original,
                "renewal_period 的配置值是什么");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getArticleKey())
                .as("explicit field token match (renewal_period) should outrank numeric valueType bonus alone")
                .isEqualTo("renewal_period");
        assertThat(result.get(1).getArticleKey())
                .isEqualTo("beta_count");
    }

    /**
     * 当 query 有数值问法且无字段 token 时，valueType=number/version 可获得小幅加权，
     * 在 FTS score 接近时可以翻盘。
     */
    @Test
    void shouldGiveSmallNumericBonusWhenQueryHasNumericIntentNoFieldTokens() {
        QueryArticleHit stringSibling = hit(
                "gamma_app_name", "gamma_app_name",
                "app_name", "root.service",
                "示例应用名称", "string",
                "root.service.app_name = 示例应用名称",
                3.2);

        QueryArticleHit numberSibling = hit(
                "gamma_retry_limit", "gamma_retry_limit",
                "retry_limit", "root.service",
                "5", "number",
                "root.service.retry_limit = 5",
                3.0);

        List<QueryArticleHit> original = Arrays.asList(stringSibling, numberSibling);
        List<QueryArticleHit> result = reranker.rerank(original,
                "最大重试次数是多少");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getArticleKey())
                .as("number type with numeric query intent should get small boost")
                .isEqualTo("gamma_retry_limit");
    }

    /**
     * metadata 缺失时保持原始顺序。
     */
    @Test
    void shouldPreserveOriginalOrderWhenMetadataIsMissing() {
        QueryArticleHit hitWithMetadata = hit(
                "delta_mode", "delta_mode",
                "delta_mode", "root",
                "enabled", "string",
                "root.delta_mode = enabled",
                7.0);

        QueryArticleHit hitWithoutMetadata = new QueryArticleHit(
                QueryEvidenceType.FACT_CARD,
                99L,
                "no_metadata_key",
                "no_metadata_concept",
                "No Metadata",
                "no content",
                null,
                "valid",
                List.of("path/to/file.md"),
                9.0);

        List<QueryArticleHit> original = Arrays.asList(hitWithMetadata, hitWithoutMetadata);
        List<QueryArticleHit> result = reranker.rerank(original, "delta_mode 是什么");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getArticleKey())
                .as("original order preserved when any metadata is missing")
                .isEqualTo("delta_mode");
        assertThat(result.get(1).getArticleKey())
                .isEqualTo("no_metadata_key");
    }

    /**
     * 当所有 hit 都没有字段意图信号且 query 无数值意图时，不强行重排。
     */
    @Test
    void shouldNotRerankWhenNoFieldIntentSignalAndNoNumericIntent() {
        QueryArticleHit first = hit(
                "epsilon_type", "epsilon_type",
                "epsilon_type", "root",
                "A类配置项", "string",
                "root.epsilon_type = A类配置项",
                9.0);

        QueryArticleHit second = hit(
                "zeta_label", "zeta_label",
                "zeta_label", "root",
                "B类配置项", "string",
                "root.zeta_label = B类配置项",
                7.0);

        List<QueryArticleHit> original = Arrays.asList(first, second);
        List<QueryArticleHit> result = reranker.rerank(original,
                "A类配置项 B类配置项");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getArticleKey())
                .as("no field-intent signal, keep original order")
                .isEqualTo("epsilon_type");
        assertThat(result.get(1).getArticleKey())
                .isEqualTo("zeta_label");
    }

    /**
     * 单 hit 无需重排，原样返回。
     */
    @Test
    void shouldReturnSingleHitUnchanged() {
        QueryArticleHit single = hit(
                "eta_single", "eta_single",
                "eta_single", "root",
                "1", "number",
                "root.eta_single = 1",
                5.0);

        List<QueryArticleHit> result = reranker.rerank(List.of(single), "eta_single 是多少");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getArticleKey()).isEqualTo("eta_single");
    }

    /**
     * 空列表和 null 安全。
     */
    @Test
    void shouldHandleEmptyAndNullInputs() {
        assertThat(reranker.rerank(List.of(), "query"))
                .isEmpty();
        assertThat(reranker.rerank(null, "query"))
                .isNull();
    }

    /**
     * 同 parent sibling 中，fieldAliases 命中 query token 也视为字段意图命中。
     */
    @Test
    void shouldTreatFieldAliasesMatchAsFieldIntent() {
        QueryArticleHit valueSibling = hit(
                "theta_label", "theta_label",
                "theta_label", "root.node",
                "示例标签名称", "string",
                "root.node.theta_label = 示例标签名称",
                6.0);

        QueryArticleHit aliasSibling = hitWithAliases(
                "theta_size", "theta_size",
                "theta_size", "root.node",
                "128", "number",
                "root.node.theta_size = 128",
                List.of("size_limit", "max_size", "capacity"),
                4.0);

        List<QueryArticleHit> original = Arrays.asList(valueSibling, aliasSibling);
        List<QueryArticleHit> result = reranker.rerank(original,
                "capacity 的上限是多少");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getArticleKey())
                .as("alias 'capacity' match should count as field intent and rank first")
                .isEqualTo("theta_size");
    }

    /**
     * keyPath 命中 query token 也视为字段意图命中。
     */
    @Test
    void shouldTreatKeyPathMatchAsFieldIntent() {
        QueryArticleHit valueSibling = hit(
                "iota_name", "iota_name",
                "iota_name", "root.v1.config",
                "配置项名称", "string",
                "root.v1.config.iota_name = 配置项名称",
                5.0);

        QueryArticleHit keyPathSibling = hit(
                "iota_limit", "iota_limit",
                "iota_limit", "root.v1.config",
                "10", "number",
                "root.v1.config.iota_limit = 10",
                3.0);

        List<QueryArticleHit> original = Arrays.asList(valueSibling, keyPathSibling);
        List<QueryArticleHit> result = reranker.rerank(original,
                "root.v1.config 的 iota_limit 是多少");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getArticleKey())
                .as("keyPath + terminalKey match should rank above value-only match")
                .isEqualTo("iota_limit");
    }

    /**
     * valueType=version 与 number 享有相同小幅加权。
     */
    @Test
    void shouldGiveVersionSameNumericBonusAsNumber() {
        QueryArticleHit stringSibling = hit(
                "kappa_name", "kappa_name",
                "app_name", "root.system",
                "服务名称描述", "string",
                "root.system.app_name = 服务名称描述",
                3.3);

        QueryArticleHit versionSibling = hit(
                "kappa_version", "kappa_version",
                "app_version", "root.system",
                "v3.1.0", "version",
                "root.system.app_version = v3.1.0",
                3.0);

        List<QueryArticleHit> original = Arrays.asList(stringSibling, versionSibling);
        List<QueryArticleHit> result = reranker.rerank(original,
                "最大版本号是多少");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getArticleKey())
                .as("version type with numeric query intent should get small boost")
                .isEqualTo("kappa_version");
    }

    /**
     * 构造 QueryArticleHit（无 aliases）。
     */
    private QueryArticleHit hit(
            String articleKey, String conceptId,
            String terminalKey, String parentPath,
            String value, String valueType, String displayText,
            double score) {
        return hitWithAliases(articleKey, conceptId,
                terminalKey, parentPath, value, valueType, displayText,
                List.of(), score);
    }

    private QueryArticleHit hitWithAliases(
            String articleKey, String conceptId,
            String terminalKey, String parentPath,
            String value, String valueType, String displayText,
            List<String> aliases, double score) {
        StringBuilder aliasesJson = new StringBuilder("[");
        for (int i = 0; i < aliases.size(); i++) {
            if (i > 0) {
                aliasesJson.append(",");
            }
            aliasesJson.append("\"").append(aliases.get(i)).append("\"");
        }
        aliasesJson.append("]");

        String keyPath = parentPath + "." + terminalKey;
        String metadataJson = "{"
                + "\"terminalUnitId\":1,"
                + "\"unitId\":\"ut:" + articleKey + "\","
                + "\"terminalUnitIdentity\":\"terminal-unit:" + articleKey + "\","
                + "\"factCardId\":1,"
                + "\"cardId\":\"fc:synthetic\","
                + "\"terminalKey\":\"" + terminalKey + "\","
                + "\"fieldLabel\":\"" + terminalKey + "\","
                + "\"fieldAliases\":" + aliasesJson + ","
                + "\"keyPath\":\"" + keyPath + "\","
                + "\"parentPath\":\"" + parentPath + "\","
                + "\"value\":\"" + escapeJson(value) + "\","
                + "\"valueType\":\"" + valueType + "\","
                + "\"displayText\":\"" + escapeJson(displayText) + "\""
                + "}";

        return new QueryArticleHit(
                QueryEvidenceType.FACT_CARD,
                1L,
                articleKey,
                conceptId,
                "Synthetic: " + terminalKey,
                displayText + "\nfield: " + terminalKey + "; valueType: " + valueType,
                metadataJson,
                "valid",
                List.of("synthetic/path.md"),
                score);
    }

    private String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
