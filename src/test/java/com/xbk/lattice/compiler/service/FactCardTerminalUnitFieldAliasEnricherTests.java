package com.xbk.lattice.compiler.service;

import com.xbk.lattice.compiler.config.LlmProperties;
import com.xbk.lattice.compiler.prompt.CompilerPromptProvider;
import com.xbk.lattice.infra.persistence.FactCardRecord;
import com.xbk.lattice.infra.persistence.FactCardTerminalUnitRecord;
import com.xbk.lattice.llm.service.LlmCallResult;
import com.xbk.lattice.llm.service.LlmClient;
import com.xbk.lattice.llm.service.LlmRouteResolution;
import com.xbk.lattice.query.evidence.domain.AnswerShape;
import com.xbk.lattice.query.evidence.domain.FactCardReviewStatus;
import com.xbk.lattice.query.evidence.domain.FactCardType;
import com.xbk.lattice.query.service.RedisKeyValueStore;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Service;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FactCardTerminalUnitFieldAliasEnricher 测试
 *
 * 职责：验证接口、NoOp、集成骨架和 ftsText 重建的正确性。
 * 使用 test-only fake enricher 和中性 synthetic 数据。
 *
 * @author xiexu
 */
class FactCardTerminalUnitFieldAliasEnricherTests {

    private final FactCardTerminalUnitMaterializer materializer = new FactCardTerminalUnitMaterializer();

    /**
     * 验证 NoOp Enricher 原样返回 records。
     */
    @Test
    void shouldReturnRecordsUnchangedWithNoOpEnricher() {
        FactCardTerminalUnitFieldAliasEnricher enricher = new FactCardTerminalUnitFieldAliasEnricher.NoOp();
        FactCardRecord factCard = syntheticFactCard();
        List<FactCardTerminalUnitRecord> records = materializer.materialize(factCard);

        List<FactCardTerminalUnitRecord> result = enricher.enrich(records, factCard);

        assertThat(result).isSameAs(records);
    }

    /**
     * 验证 test-only fake enricher 对非 CJK fieldLabel 追加 alias 后，
     * fieldAliasesJson 更新、ftsText 包含新 alias。
     */
    @Test
    void shouldAppendAliasAndRebuildFtsTextWithFakeEnricher() {
        FactCardRecord factCard = syntheticFactCard();
        List<FactCardTerminalUnitRecord> records = materializer.materialize(factCard);

        // 仅对 terminalKey="target_metric" 的 record 追加 alias
        List<FactCardTerminalUnitRecord> enriched = new ArrayList<>();
        for (FactCardTerminalUnitRecord record : records) {
            if ("target_metric".equals(record.getTerminalKey())) {
                List<String> existing = parseAliases(record.getFieldAliasesJson());
                List<String> merged = new ArrayList<>(existing);
                merged.add("指标参数");
                String newAliasesJson = writeAliasesJson(merged);
                String newFtsText = materializer.rebuildFtsText(record, merged);
                enriched.add(record.withFieldAliasesAndFtsText(newAliasesJson, newFtsText));
            } else {
                enriched.add(record);
            }
        }

        assertThat(enriched).hasSize(records.size());
        FactCardTerminalUnitRecord target = enriched.stream()
                .filter(r -> "target_metric".equals(r.getTerminalKey()))
                .findFirst().orElseThrow();
        assertThat(target.getFieldAliasesJson())
                .as("aliases should include fake alias")
                .contains("指标参数");
        assertThat(target.getFtsText())
                .as("ftsText should include fake alias")
                .contains("指标参数");
        // 原有 alias 不被覆盖
        assertThat(target.getFieldAliasesJson())
                .as("original aliases preserved")
                .contains("target_metric")
                .contains("target metric");
    }

    /**
     * 验证 ftsText 重建后，alias 段被精确替换，其他字段内容不变。
     */
    @Test
    void shouldReplaceOnlyAliasSegmentInFtsText() {
        FactCardRecord factCard = syntheticFactCard();
        List<FactCardTerminalUnitRecord> records = materializer.materialize(factCard);
        FactCardTerminalUnitRecord record = records.stream()
                .filter(r -> "target_metric".equals(r.getTerminalKey()))
                .findFirst().orElseThrow();

        String oldFtsText = record.getFtsText();
        List<String> oldAliases = parseAliases(record.getFieldAliasesJson());
        List<String> newAliases = new ArrayList<>(oldAliases);
        newAliases.add("指标参数");

        String newFtsText = materializer.rebuildFtsText(record, newAliases);

        assertThat(newFtsText)
                .as("ftsText should contain new alias")
                .contains("指标参数");
        assertThat(newFtsText)
                .as("ftsText should retain keyPath")
                .contains(record.getKeyPath());
        assertThat(newFtsText)
                .as("ftsText should retain valueText")
                .contains(record.getValueText());
    }

    /**
     * 验证 copy-with 后除 fieldAliasesJson 和 ftsText 外，其余字段完全透传。
     */
    @Test
    void shouldPreserveAllFieldsExceptAliasesAndFtsTextInCopyWith() {
        FactCardRecord factCard = syntheticFactCard();
        List<FactCardTerminalUnitRecord> records = materializer.materialize(factCard);
        FactCardTerminalUnitRecord original = records.get(0);

        String newAliasesJson = writeAliasesJson(List.of("alpha_limit", "alpha limit", "测试别名"));
        String newFtsText = "custom fts text";
        FactCardTerminalUnitRecord copy = original.withFieldAliasesAndFtsText(newAliasesJson, newFtsText);

        assertThat(copy.getFieldAliasesJson()).isEqualTo(newAliasesJson);
        assertThat(copy.getFtsText()).isEqualTo(newFtsText);
        assertThat(copy.getId()).isEqualTo(original.getId());
        assertThat(copy.getUnitId()).isEqualTo(original.getUnitId());
        assertThat(copy.getTerminalUnitIdentity()).isEqualTo(original.getTerminalUnitIdentity());
        assertThat(copy.getFactCardId()).isEqualTo(original.getFactCardId());
        assertThat(copy.getCardId()).isEqualTo(original.getCardId());
        assertThat(copy.getSourceId()).isEqualTo(original.getSourceId());
        assertThat(copy.getSourceFileId()).isEqualTo(original.getSourceFileId());
        assertThat(copy.getSourceChunkIds()).isEqualTo(original.getSourceChunkIds());
        assertThat(copy.getArticleIds()).isEqualTo(original.getArticleIds());
        assertThat(copy.getCardType()).isEqualTo(original.getCardType());
        assertThat(copy.getAnswerShape()).isEqualTo(original.getAnswerShape());
        assertThat(copy.getStructure()).isEqualTo(original.getStructure());
        assertThat(copy.getItemIndex()).isEqualTo(original.getItemIndex());
        assertThat(copy.getKeyPath()).isEqualTo(original.getKeyPath());
        assertThat(copy.getParentPath()).isEqualTo(original.getParentPath());
        assertThat(copy.getTerminalKey()).isEqualTo(original.getTerminalKey());
        assertThat(copy.getPathSegmentsJson()).isEqualTo(original.getPathSegmentsJson());
        assertThat(copy.getFieldLabel()).isEqualTo(original.getFieldLabel());
        assertThat(copy.getFieldDescription()).isEqualTo(original.getFieldDescription());
        assertThat(copy.getDisplayText()).isEqualTo(original.getDisplayText());
        assertThat(copy.getValueText()).isEqualTo(original.getValueText());
        assertThat(copy.getNormalizedValue()).isEqualTo(original.getNormalizedValue());
        assertThat(copy.getValueType()).isEqualTo(original.getValueType());
        assertThat(copy.getSourceRefsJson()).isEqualTo(original.getSourceRefsJson());
        assertThat(copy.getMetadataJson()).isEqualTo(original.getMetadataJson());
        assertThat(copy.getReviewStatus()).isEqualTo(original.getReviewStatus());
        assertThat(copy.getConfidence()).isEqualTo(original.getConfidence());
        assertThat(copy.getContentHash()).isEqualTo(original.getContentHash());
    }

    /**
     * 验证 rebuildFtsText 在空别名场景安全处理。
     */
    @Test
    void shouldHandleEmptyAliasesInRebuildFtsText() {
        FactCardRecord factCard = syntheticFactCard();
        List<FactCardTerminalUnitRecord> records = materializer.materialize(factCard);
        FactCardTerminalUnitRecord record = records.get(0);

        // 用空列表重建
        String result = materializer.rebuildFtsText(record, List.of());
        assertThat(result)
                .as("rebuild with empty aliases should still produce valid text")
                .isNotBlank();
    }

    /**
     * 验证 rebuildFtsText 在 ftsText 为 null 时不抛异常。
     */
    @Test
    void shouldNotThrowOnNullFtsTextInRebuild() {
        FactCardTerminalUnitRecord record = new FactCardTerminalUnitRecord(
                "ut:test", "terminal-unit:test", 1L, "fc:test",
                1L, 1L, List.of(1L), List.of(2L),
                FactCardType.FACT_ENUM, AnswerShape.ENUM,
                "key_value_list", 0, "root.field", "root",
                "field", "[]", "field", "[]", "",
                "root.field = val", "val", "val", "string",
                "{}", null, "{}",
                FactCardReviewStatus.VALID, 1.0, "hash"
        );

        String result = materializer.rebuildFtsText(record, List.of("alias_a"));
        assertThat(result)
                .as("null ftsText with new aliases should produce valid text with alias")
                .contains("alias_a");
    }

    /**
     * 验证 LLM Enricher 作为公开 Spring 组件暴露，避免 runtime 组件扫描漏注册。
     *
     * @throws Exception 反射异常
     */
    @Test
    void shouldExposeLlmEnricherAsPublicSpringService() throws Exception {
        Class<LlmFactCardTerminalUnitFieldAliasEnricher> enricherClass =
                LlmFactCardTerminalUnitFieldAliasEnricher.class;
        Constructor<LlmFactCardTerminalUnitFieldAliasEnricher> constructor = enricherClass.getConstructor(
                LlmGateway.class,
                CompilerPromptProvider.class,
                FactCardTerminalUnitMaterializer.class
        );

        assertThat(Modifier.isPublic(enricherClass.getModifiers())).isTrue();
        assertThat(Modifier.isPublic(constructor.getModifiers())).isTrue();
        assertThat(enricherClass).hasAnnotation(Service.class);
        assertThat(FactCardTerminalUnitFieldAliasEnricher.class).isAssignableFrom(enricherClass);
    }

    /**
     * 验证 LLM 成功返回时会合并、去重、限长、限量并重建 ftsText。
     */
    @Test
    void shouldMergeSyntheticLlmAliasesAndRebuildFtsText() {
        FactCardRecord factCard = syntheticFactCard();
        List<FactCardTerminalUnitRecord> records = materializer.materialize(factCard);
        RecordingLlmGateway llmGateway = new RecordingLlmGateway("""
                {
                  "aliases": {
                    "target_metric": [
                      "指标参数",
                      "指标参数",
                      "样本上限",
                      "target_metric",
                      "这是一段明确超过二十个字符的中文别名会被过滤",
                      "42"
                    ]
                  }
                }
                """);
        FactCardTerminalUnitFieldAliasEnricher enricher = newLlmEnricher(llmGateway);

        List<FactCardTerminalUnitRecord> enriched = enricher.enrich(records, factCard);

        assertThat(llmGateway.generateTextCallCount).isEqualTo(1);
        assertThat(llmGateway.lastScene).isEqualTo("compile");
        assertThat(llmGateway.lastAgentRole).isEqualTo("field-alias-enricher");
        assertThat(llmGateway.lastPurpose).isEqualTo("enrich-field-aliases");
        assertThat(llmGateway.lastSystemPrompt).contains("英文字段");
        assertThat(llmGateway.lastUserPrompt)
                .contains("target_metric")
                .contains("group_alpha")
                .contains("sample_limit")
                .contains("甲类对象");
        assertThat(llmGateway.lastUserPrompt)
                .doesNotContain("case")
                .doesNotContain("expected");

        FactCardTerminalUnitRecord target = enriched.stream()
                .filter(r -> "target_metric".equals(r.getTerminalKey()))
                .findFirst().orElseThrow();
        assertThat(target.getFieldAliasesJson())
                .contains("指标参数")
                .contains("样本上限")
                .doesNotContain("这是一段明确超过二十个字符的中文别名会被过滤")
                .doesNotContain("\"42\"");
        assertThat(countOccurrences(target.getFieldAliasesJson(), "指标参数")).isEqualTo(1);
        assertThat(target.getFtsText())
                .contains("指标参数")
                .contains("样本上限");
    }

    /**
     * 验证 LLM 调用异常时原 records 不变。
     */
    @Test
    void shouldKeepOriginalRecordsWhenLlmThrowsException() {
        FactCardRecord factCard = syntheticFactCard();
        List<FactCardTerminalUnitRecord> records = materializer.materialize(factCard);
        RecordingLlmGateway llmGateway = new RecordingLlmGateway("{\"aliases\":{}}");
        llmGateway.throwOnGenerate = true;
        FactCardTerminalUnitFieldAliasEnricher enricher = newLlmEnricher(llmGateway);

        List<FactCardTerminalUnitRecord> enriched = enricher.enrich(records, factCard);

        assertThat(enriched).isSameAs(records);
        assertThat(llmGateway.generateTextCallCount).isEqualTo(1);
    }

    /**
     * 验证 LLM 返回非 JSON 时原 records 不变。
     */
    @Test
    void shouldKeepOriginalRecordsWhenLlmReturnsNonJson() {
        FactCardRecord factCard = syntheticFactCard();
        List<FactCardTerminalUnitRecord> records = materializer.materialize(factCard);
        FactCardTerminalUnitFieldAliasEnricher enricher = newLlmEnricher(new RecordingLlmGateway("not-json"));

        List<FactCardTerminalUnitRecord> enriched = enricher.enrich(records, factCard);

        assertThat(enriched).isSameAs(records);
    }

    /**
     * 验证 LLM 返回空响应时原 records 不变。
     */
    @Test
    void shouldKeepOriginalRecordsWhenLlmReturnsBlankResponse() {
        FactCardRecord factCard = syntheticFactCard();
        List<FactCardTerminalUnitRecord> records = materializer.materialize(factCard);
        FactCardTerminalUnitFieldAliasEnricher enricher = newLlmEnricher(new RecordingLlmGateway("   "));

        List<FactCardTerminalUnitRecord> enriched = enricher.enrich(records, factCard);

        assertThat(enriched).isSameAs(records);
    }

    /**
     * 验证已有 CJK alias 的记录不触发 LLM 且不被改写。
     */
    @Test
    void shouldSkipRecordsThatAlreadyHaveCjkAliases() {
        FactCardRecord factCard = syntheticCjkFieldFactCard();
        List<FactCardTerminalUnitRecord> records = materializer.materialize(factCard);
        RecordingLlmGateway llmGateway = new RecordingLlmGateway("""
                {"aliases":{"指标参数":["样本上限"]}}
                """);
        FactCardTerminalUnitFieldAliasEnricher enricher = newLlmEnricher(llmGateway);

        List<FactCardTerminalUnitRecord> enriched = enricher.enrich(records, factCard);

        assertThat(enriched).isSameAs(records);
        assertThat(llmGateway.routeResolutionCallCount).isZero();
        assertThat(llmGateway.generateTextCallCount).isZero();
    }

    /**
     * 验证路由不可用时 fail-closed，不调用 LLM。
     */
    @Test
    void shouldKeepOriginalRecordsWhenRouteIsUnavailable() {
        FactCardRecord factCard = syntheticFactCard();
        List<FactCardTerminalUnitRecord> records = materializer.materialize(factCard);
        RecordingLlmGateway llmGateway = new RecordingLlmGateway("""
                {"aliases":{"target_metric":["指标参数"]}}
                """);
        llmGateway.routeAvailable = false;
        FactCardTerminalUnitFieldAliasEnricher enricher = newLlmEnricher(llmGateway);

        List<FactCardTerminalUnitRecord> enriched = enricher.enrich(records, factCard);

        assertThat(enriched).isSameAs(records);
        assertThat(llmGateway.routeResolutionCallCount).isEqualTo(1);
        assertThat(llmGateway.generateTextCallCount).isZero();
    }

    /**
     * 验证有 scope 的 enrich 使用 routeResolutionFor + generateTextWithScope，
     * 而非无 scope 的 routeResolution + generateText。
     */
    @Test
    void shouldUseScopedRouteResolutionAndGenerateTextWhenScopeProvided() {
        FactCardRecord factCard = syntheticFactCard();
        List<FactCardTerminalUnitRecord> records = materializer.materialize(factCard);
        RecordingLlmGateway llmGateway = new RecordingLlmGateway("""
                {"aliases":{"target_metric":["指标参数"]}}
                """);
        FactCardTerminalUnitFieldAliasEnricher enricher = newLlmEnricher(llmGateway);

        List<FactCardTerminalUnitRecord> enriched = enricher.enrich(records, factCard,
                "compile-job-scope-001");

        assertThat(enriched).isNotSameAs(records);
        assertThat(llmGateway.routeResolutionForCallCount)
                .as("scoped enrich must use routeResolutionFor")
                .isEqualTo(1);
        assertThat(llmGateway.routeResolutionCallCount)
                .as("scoped enrich must not use non-scoped routeResolution")
                .isZero();
        assertThat(llmGateway.generateTextWithScopeCallCount)
                .as("scoped enrich must use generateTextWithScope")
                .isEqualTo(1);
        assertThat(llmGateway.generateTextCallCount)
                .as("scoped enrich must not use non-scoped generateText")
                .isZero();
    }

    /**
     * 验证无 scope 的 enrich 仍使用 routeResolution + generateText（保持旧路径兼容）。
     */
    @Test
    void shouldUseNonScopedRouteWhenNoScopeProvided() {
        FactCardRecord factCard = syntheticFactCard();
        List<FactCardTerminalUnitRecord> records = materializer.materialize(factCard);
        RecordingLlmGateway llmGateway = new RecordingLlmGateway("""
                {"aliases":{"target_metric":["指标参数"]}}
                """);
        FactCardTerminalUnitFieldAliasEnricher enricher = newLlmEnricher(llmGateway);

        List<FactCardTerminalUnitRecord> enriched = enricher.enrich(records, factCard);

        assertThat(enriched).isNotSameAs(records);
        assertThat(llmGateway.routeResolutionCallCount)
                .as("non-scoped enrich must use routeResolution")
                .isEqualTo(1);
        assertThat(llmGateway.routeResolutionForCallCount)
                .as("non-scoped enrich must not use routeResolutionFor")
                .isZero();
        assertThat(llmGateway.generateTextCallCount)
                .as("non-scoped enrich must use generateText")
                .isEqualTo(1);
        assertThat(llmGateway.generateTextWithScopeCallCount)
                .as("non-scoped enrich must not use generateTextWithScope")
                .isZero();
    }

    /**
     * 构造 synthetic fact card，含 CJK 和 non-CJK fieldLabel。
     */
    private FactCardRecord syntheticFactCard() {
        String itemsJson = """
                {
                  "structure": "key_value_list",
                  "pathAware": true,
                  "items": [
                    {
                      "key": "target_metric",
                      "value": "42",
                      "parentPath": "group_alpha",
                      "keyPath": "group_alpha.target_metric",
                      "pathSegments": ["group_alpha", "target_metric"]
                    },
                    {
                      "key": "display_name",
                      "value": "甲类对象",
                      "parentPath": "group_alpha",
                      "keyPath": "group_alpha.display_name",
                      "pathSegments": ["group_alpha", "display_name"]
                    },
                    {
                      "key": "sample_limit",
                      "value": "10",
                      "parentPath": "group_alpha",
                      "keyPath": "group_alpha.sample_limit",
                      "pathSegments": ["group_alpha", "sample_limit"]
                    }
                  ]
                }
                """;
        return new FactCardRecord(
                1L,
                "fc:synthetic:test",
                2L,
                3L,
                FactCardType.FACT_ENUM,
                AnswerShape.ENUM,
                "Synthetic Fact Card",
                "Synthetic claim text.",
                itemsJson,
                "target_metric=42\ndisplay_name=甲类对象",
                List.of(4L),
                List.of(5L),
                0.95,
                FactCardReviewStatus.VALID,
                "hash-synthetic",
                OffsetDateTime.now().minusSeconds(30L),
                OffsetDateTime.now()
        );
    }

    /**
     * 构造字段名本身已含 CJK 的 synthetic fact card。
     */
    private FactCardRecord syntheticCjkFieldFactCard() {
        String itemsJson = """
                {
                  "structure": "key_value_list",
                  "pathAware": true,
                  "items": [
                    {
                      "key": "指标参数",
                      "value": "42",
                      "parentPath": "group_alpha",
                      "keyPath": "group_alpha.指标参数",
                      "pathSegments": ["group_alpha", "指标参数"]
                    }
                  ]
                }
                """;
        return new FactCardRecord(
                1L,
                "fc:synthetic:cjk",
                2L,
                3L,
                FactCardType.FACT_ENUM,
                AnswerShape.ENUM,
                "Synthetic Fact Card",
                "Synthetic claim text.",
                itemsJson,
                "指标参数=42",
                List.of(4L),
                List.of(5L),
                0.95,
                FactCardReviewStatus.VALID,
                "hash-synthetic-cjk",
                OffsetDateTime.now().minusSeconds(30L),
                OffsetDateTime.now()
        );
    }

    private FactCardTerminalUnitFieldAliasEnricher newLlmEnricher(RecordingLlmGateway llmGateway) {
        return new LlmFactCardTerminalUnitFieldAliasEnricher(
                llmGateway,
                new CompilerPromptProvider(),
                materializer
        );
    }

    /**
     * 统计子串出现次数。
     *
     * @param text 原始文本
     * @param part 子串
     * @return 出现次数
     */
    private int countOccurrences(String text, String part) {
        int count = 0;
        int index = text.indexOf(part);
        while (index >= 0) {
            count++;
            index = text.indexOf(part, index + part.length());
        }
        return count;
    }

    /**
     * 解析测试用 alias JSON。
     *
     * @param aliasesJson alias JSON
     * @return alias 列表
     */
    private List<String> parseAliases(String aliasesJson) {
        if (aliasesJson == null || aliasesJson.isBlank() || "[]".equals(aliasesJson.trim())) {
            return List.of();
        }
        // simple JSON array parse for test
        String inner = aliasesJson.trim();
        if (inner.startsWith("[") && inner.endsWith("]")) {
            inner = inner.substring(1, inner.length() - 1).trim();
        }
        if (inner.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(inner.split(","))
                .map(s -> s.replaceAll("^\\s*\"|\"\\s*$", "").trim())
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * 写出测试用 alias JSON。
     *
     * @param aliases alias 列表
     * @return alias JSON
     */
    private String writeAliasesJson(List<String> aliases) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < aliases.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(aliases.get(i).replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * 记录 LLM 调用参数的网关替身。
     *
     * 职责：为字段别名增强器测试提供可控 LLM 响应
     *
     * @author xiexu
     */
    private static class RecordingLlmGateway extends LlmGateway {

        private final String response;

        private int routeResolutionCallCount;

        private int routeResolutionForCallCount;

        private int generateTextCallCount;

        private int generateTextWithScopeCallCount;

        private boolean routeAvailable = true;

        private boolean throwOnGenerate;

        private String lastScene;

        private String lastAgentRole;

        private String lastPurpose;

        private String lastSystemPrompt;

        private String lastUserPrompt;

        private RecordingLlmGateway(String response) {
            super(new NoOpLlmClient(), new NoOpLlmClient(), new NoOpRedisKeyValueStore(), createProperties());
            this.response = response;
        }

        @Override
        public LlmRouteResolution routeResolution(String scene, String agentRole) {
            routeResolutionCallCount++;
            if (!routeAvailable) {
                throw new IllegalStateException("route unavailable");
            }
            return syntheticRouteResolution(scene, agentRole);
        }

        @Override
        public LlmRouteResolution routeResolutionFor(String scopeId, String scene, String agentRole) {
            routeResolutionForCallCount++;
            if (!routeAvailable) {
                throw new IllegalStateException("route unavailable");
            }
            return syntheticRouteResolution(scene, agentRole);
        }

        @Override
        public String generateTextWithScope(
                String scopeId,
                String scene,
                String agentRole,
                String purpose,
                String systemPrompt,
                String userPrompt
        ) {
            generateTextWithScopeCallCount++;
            return doGenerateText(scene, agentRole, purpose, systemPrompt, userPrompt);
        }

        private LlmRouteResolution syntheticRouteResolution(String scene, String agentRole) {
            return new LlmRouteResolution(
                    "compile_job",
                    null,
                    scene,
                    agentRole,
                    Long.valueOf(7L),
                    null,
                    Integer.valueOf(1),
                    "compile.field-alias-enricher.synthetic",
                    "openai",
                    "http://127.0.0.1",
                    "",
                    "synthetic-chat-model",
                    BigDecimal.ZERO,
                    Integer.valueOf(512),
                    Integer.valueOf(30),
                    "{}",
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    true
            );
        }

        @Override
        public String generateText(
                String scene,
                String agentRole,
                String purpose,
                String systemPrompt,
                String userPrompt
        ) {
            generateTextCallCount++;
            return doGenerateText(scene, agentRole, purpose, systemPrompt, userPrompt);
        }

        private String doGenerateText(
                String scene,
                String agentRole,
                String purpose,
                String systemPrompt,
                String userPrompt
        ) {
            lastScene = scene;
            lastAgentRole = agentRole;
            lastPurpose = purpose;
            lastSystemPrompt = systemPrompt;
            lastUserPrompt = userPrompt;
            if (throwOnGenerate) {
                throw new IllegalStateException("synthetic LLM failure");
            }
            return response;
        }
    }

    /**
     * 空操作客户端。
     *
     * 职责：满足 LlmGateway 测试构造器签名
     *
     * @author xiexu
     */
    private static class NoOpLlmClient implements LlmClient {

        @Override
        public LlmCallResult call(String systemPrompt, String userPrompt) {
            return new LlmCallResult("", 0, 0);
        }
    }

    /**
     * 空操作 Redis 存储。
     *
     * 职责：满足 LlmGateway 测试构造器签名
     *
     * @author xiexu
     */
    private static class NoOpRedisKeyValueStore implements RedisKeyValueStore {

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

        @Override
        public void deleteByPrefix(String keyPrefix) {
            values.keySet().removeIf(key -> key.startsWith(keyPrefix));
        }
    }

    /**
     * 创建测试 LLM 配置。
     *
     * @return LLM 配置
     */
    private static LlmProperties createProperties() {
        LlmProperties llmProperties = new LlmProperties();
        llmProperties.setCompileModel("synthetic-chat-model");
        llmProperties.setReviewerModel("synthetic-review-model");
        llmProperties.setBudgetUsd(10.0D);
        llmProperties.setCacheTtlSeconds(3600L);
        llmProperties.setCacheKeyPrefix("llm:cache:");
        return llmProperties;
    }
}
