package com.xbk.lattice.compiler.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.xbk.lattice.infra.persistence.FactCardRecord;
import com.xbk.lattice.infra.persistence.FactCardTerminalUnitRecord;
import com.xbk.lattice.query.evidence.domain.AnswerShape;
import com.xbk.lattice.query.evidence.domain.FactCardReviewStatus;
import com.xbk.lattice.query.evidence.domain.FactCardType;
import com.xbk.lattice.shared.json.JsonMappers;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FactCardTerminalUnitMaterializer 测试
 *
 * 职责：验证结构化 fact card 可展开为独立 terminal unit
 *
 * @author xiexu
 */
class FactCardTerminalUnitMaterializerTests {

    private final FactCardTerminalUnitMaterializer materializer = new FactCardTerminalUnitMaterializer();

    /**
     * 验证 key_value_list 中的 scalar assignment 会展开为 terminal unit。
     *
     * @throws Exception JSON 解析异常
     */
    @Test
    void shouldMaterializeKeyValueListItems() throws Exception {
        FactCardRecord factCardRecord = factCardRecord(
                """
                        {
                          "structure": "key_value_list",
                          "pathAware": false,
                          "items": [
                            {
                              "key": "alpha_limit",
                              "value": "31",
                              "raw": "alpha_limit=31",
                              "keyPath": "alpha_limit",
                              "displayText": "alpha_limit = 31",
                              "pathSegments": ["alpha_limit"]
                            },
                            {
                              "key": "beta_mode",
                              "value": "enabled",
                              "raw": "beta_mode=enabled",
                              "keyPath": "beta_mode",
                              "displayText": "beta_mode = enabled",
                              "pathSegments": ["beta_mode"]
                            }
                          ]
                        }
                        """
        );

        List<FactCardTerminalUnitRecord> records = materializer.materialize(factCardRecord);

        assertThat(records).hasSize(2);
        assertThat(records)
                .extracting(FactCardTerminalUnitRecord::getTerminalKey)
                .containsExactly("alpha_limit", "beta_mode");
        assertThat(records.get(0).getArticleIds()).containsExactly(Long.valueOf(8L));
        assertThat(records.get(0).getSourceChunkIds()).containsExactly(Long.valueOf(7L));
        assertThat(records.get(0).getFtsText()).contains("alpha_limit").contains("31");
        JsonNode metadataNode = JsonMappers.defaultMapper().readTree(records.get(0).getMetadataJson());
        assertThat(metadataNode.path("unitId").asText()).isEqualTo(records.get(0).getUnitId());
        assertThat(metadataNode.path("terminalUnitIdentity").asText())
                .isEqualTo(records.get(0).getTerminalUnitIdentity());
        assertThat(metadataNode.has("terminalUnitId")).isTrue();
        assertThat(metadataNode.path("keyPath").asText()).isEqualTo("alpha_limit");
        assertThat(metadataNode.path("value").asText()).isEqualTo("31");
    }

    /**
     * 验证 path-aware item 会保留 parentPath、keyPath 与 pathSegments。
     */
    @Test
    void shouldMaterializePathAwareItems() {
        FactCardRecord factCardRecord = factCardRecord(
                """
                        {
                          "structure": "key_value_list",
                          "pathAware": true,
                          "items": [
                            {
                              "key": "threshold",
                              "value": "active",
                              "raw": "threshold: active",
                              "parentPath": "root.sections[0].settings",
                              "keyPath": "root.sections[0].settings.threshold",
                              "displayText": "root.sections[0].settings.threshold = active",
                              "pathSegments": ["root", "sections[0]", "settings", "threshold"]
                            }
                          ]
                        }
                        """
        );

        List<FactCardTerminalUnitRecord> records = materializer.materialize(factCardRecord);

        assertThat(records).hasSize(1);
        FactCardTerminalUnitRecord record = records.get(0);
        assertThat(record.getParentPath()).isEqualTo("root.sections[0].settings");
        assertThat(record.getKeyPath()).isEqualTo("root.sections[0].settings.threshold");
        assertThat(record.getPathSegmentsJson()).contains("sections[0]");
        assertThat(record.getFieldDescription()).contains("parentPath: root.sections[0].settings");
        assertThat(record.getMetadataJson()).contains("\"terminalKey\":\"threshold\"");
    }

    /**
     * 验证仅保留 scalar，跳过空值、容器值和过长文本。
     */
    @Test
    void shouldFilterEmptyContainerAndLongTextItems() {
        FactCardRecord factCardRecord = factCardRecord(
                """
                        {
                          "structure": "key_value_list",
                          "pathAware": false,
                          "items": [
                            {"key": "small_flag", "value": "true", "keyPath": "small_flag"},
                            {"key": "blank_flag", "value": "   ", "keyPath": "blank_flag"},
                            {"key": "container_flag", "value": {"nested": "value"}, "keyPath": "container_flag"},
                            {
                              "key": "long_note",
                              "value": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                              "keyPath": "long_note"
                            }
                          ]
                        }
                        """
        );

        List<FactCardTerminalUnitRecord> records = materializer.materialize(factCardRecord);

        assertThat(records).hasSize(1);
        assertThat(records.get(0).getTerminalKey()).isEqualTo("small_flag");
        assertThat(records.get(0).getValueType()).isEqualTo("boolean");
    }

    /**
     * 验证别名只由字段名、路径片段和通用拆词得到。
     */
    @Test
    void shouldBuildAliasesFromSourcePathAndGenericSplittingOnly() {
        FactCardRecord factCardRecord = factCardRecord(
                """
                        {
                          "structure": "key_value_list",
                          "pathAware": true,
                          "items": [
                            {
                              "key": "maxAttempts",
                              "value": "32",
                              "parentPath": "service.retryGroup",
                              "keyPath": "service.retryGroup.maxAttempts",
                              "pathSegments": ["service", "retryGroup", "maxAttempts"]
                            }
                          ]
                        }
                        """
        );

        List<FactCardTerminalUnitRecord> records = materializer.materialize(factCardRecord);

        assertThat(records).hasSize(1);
        String aliasesJson = records.get(0).getFieldAliasesJson();
        assertThat(aliasesJson).contains("maxAttempts");
        assertThat(aliasesJson).contains("retryGroup");
        assertThat(aliasesJson).contains("attempts");
        assertThat(aliasesJson).doesNotContain("manual_hint");
        assertThat(records.get(0).getMetadataJson()).contains("\"valueType\":\"number\"");
    }

    /**
     * 验证中文 fieldLabel "维护周期(天)" 生成 N-gram 别名。
     */
    @Test
    void shouldGenerateChineseNgramAliasesFromChineseFieldLabel() {
        FactCardRecord factCardRecord = factCardRecord(
                """
                        {
                          "structure": "key_value_list",
                          "pathAware": false,
                          "items": [
                            {
                              "key": "维护周期(天)",
                              "value": "30",
                              "raw": "维护周期(天)=30",
                              "keyPath": "维护周期(天)",
                              "displayText": "维护周期(天) = 30",
                              "pathSegments": ["维护周期(天)"]
                            }
                          ]
                        }
                        """
        );

        List<FactCardTerminalUnitRecord> records = materializer.materialize(factCardRecord);

        assertThat(records).hasSize(1);
        String aliasesJson = records.get(0).getFieldAliasesJson();
        assertThat(aliasesJson).as("full segment").contains("\"维护周期\"");
        assertThat(aliasesJson).as("bigram 维护").contains("\"维护\"");
        assertThat(aliasesJson).as("bigram 周期").contains("\"周期\"");
        assertThat(aliasesJson).as("trigram 维护周").contains("\"维护周\"");
        assertThat(aliasesJson).as("trigram 护周期").contains("\"护周期\"");
        assertThat(aliasesJson).as("bracket content excluded").doesNotContain("\"天\"");
    }

    /**
     * 验证中文 N-gram 逻辑不会从单字中文字符生成 bigram/trigram。
     * fieldLabel 本身通过 addAlias 进入别名集合，但 addChineseNgramAliases 应跳过单字。
     */
    @Test
    void shouldNotGenerateNgramAliasesFromSingleCjkChar() {
        FactCardRecord factCardRecord = factCardRecord(
                """
                        {
                          "structure": "key_value_list",
                          "pathAware": false,
                          "items": [
                            {
                              "key": "x_单_y",
                              "value": "ok",
                              "raw": "x_单_y=ok",
                              "keyPath": "x_单_y",
                              "displayText": "x_单_y = ok",
                              "pathSegments": ["x_单_y"]
                            }
                          ]
                        }
                        """
        );

        List<FactCardTerminalUnitRecord> records = materializer.materialize(factCardRecord);

        assertThat(records).hasSize(1);
        String aliasesJson = records.get(0).getFieldAliasesJson();
        assertThat(aliasesJson).as("fieldLabel itself is preserved by addAlias")
                .contains("\"x_单_y\"");
        assertThat(aliasesJson).as("CJK_RUN_PATTERN requires 2+ chars, single CJK '单' generates no N-gram")
                .doesNotContain("\"单\"");
    }

    /**
     * 验证英文 fieldLabel / camelCase 的原有别名逻辑不退化。
     */
    @Test
    void shouldNotDegradeEnglishFieldLabelAliases() {
        FactCardRecord factCardRecord = factCardRecord(
                """
                        {
                          "structure": "key_value_list",
                          "pathAware": true,
                          "items": [
                            {
                              "key": "maxRetryCount",
                              "value": "5",
                              "parentPath": "service.policy",
                              "keyPath": "service.policy.maxRetryCount",
                              "pathSegments": ["service", "policy", "maxRetryCount"]
                            }
                          ]
                        }
                        """
        );

        List<FactCardTerminalUnitRecord> records = materializer.materialize(factCardRecord);

        assertThat(records).hasSize(1);
        String aliasesJson = records.get(0).getFieldAliasesJson();
        assertThat(aliasesJson).contains("maxRetryCount");
        assertThat(aliasesJson).contains("maxretrycount");
        assertThat(aliasesJson).contains("Retry");
        assertThat(aliasesJson).contains("retry");
        assertThat(aliasesJson).contains("Count");
        assertThat(aliasesJson).as("no Chinese N-gram pollution on English fieldLabel")
                .doesNotContain("一");
    }

    /**
     * 验证超长中文文本（>8 字）不会通过 addChineseNgramAliases 生成 N-gram 子串。
     * fieldLabel 本身仍由 addAlias 加入，但不应有子串别名。
     */
    @Test
    void shouldNotGenerateNgramAliasesForVeryLongChineseText() {
        FactCardRecord factCardRecord = factCardRecord(
                """
                        {
                          "structure": "key_value_list",
                          "pathAware": false,
                          "items": [
                            {
                              "key": "这是一个非常长的中文字段名称不适合做检索别名",
                              "value": "ignored",
                              "raw": "这是一个非常长的中文字段名称不适合做检索别名=ignored",
                              "keyPath": "这是一个非常长的中文字段名称不适合做检索别名",
                              "displayText": "这是一个非常长的中文字段名称不适合做检索别名 = ignored",
                              "pathSegments": ["这是一个非常长的中文字段名称不适合做检索别名"]
                            }
                          ]
                        }
                        """
        );

        List<FactCardTerminalUnitRecord> records = materializer.materialize(factCardRecord);

        assertThat(records).hasSize(1);
        String aliasesJson = records.get(0).getFieldAliasesJson();
        assertThat(aliasesJson).as("fieldLabel itself preserved by addAlias")
                .contains("\"这是一个非常长的中文字段名称不适合做检索别名\"");
        assertThat(aliasesJson).as("CJK run > 8 chars: no bigram '这是' from N-gram")
                .doesNotContain("\"这是\"");
        assertThat(aliasesJson).as("CJK run > 8 chars: no bigram '中文' from N-gram")
                .doesNotContain("\"中文\"");
    }

    /**
     * 验证同 parentPath 的 CJK sibling value 进入其他 terminal unit 的 fieldDescription。
     */
    @Test
    void shouldAddSiblingContextToFieldDescription() {
        FactCardRecord factCardRecord = factCardRecord(
                """
                        {
                          "structure": "key_value_list",
                          "pathAware": true,
                          "items": [
                            {
                              "key": "type",
                              "value": "精密仪器",
                              "parentPath": "equipment_types[1]",
                              "keyPath": "equipment_types[1].type",
                              "pathSegments": ["equipment_types[1]", "type"]
                            },
                            {
                              "key": "max_borrow_days",
                              "value": "7",
                              "parentPath": "equipment_types[1]",
                              "keyPath": "equipment_types[1].max_borrow_days",
                              "pathSegments": ["equipment_types[1]", "max_borrow_days"]
                            },
                            {
                              "key": "approval_required",
                              "value": "实验室主任",
                              "parentPath": "equipment_types[1]",
                              "keyPath": "equipment_types[1].approval_required",
                              "pathSegments": ["equipment_types[1]", "approval_required"]
                            }
                          ]
                        }
                        """
        );

        List<FactCardTerminalUnitRecord> records = materializer.materialize(factCardRecord);

        assertThat(records).hasSize(3);
        FactCardTerminalUnitRecord numberRecord = records.stream()
                .filter(r -> "max_borrow_days".equals(r.getTerminalKey()))
                .findFirst().orElseThrow();
        assertThat(numberRecord.getFieldDescription())
                .as("number type unit should receive CJK sibling context")
                .contains("context: 精密仪器, 实验室主任");
        assertThat(numberRecord.getFtsText())
                .as("ftsText should contain sibling context for LIKE matching")
                .contains("精密仪器")
                .contains("实验室主任");
        assertThat(records.get(0).getFieldDescription())
                .as("first sibling should get other sibling as context but not self")
                .contains("context: 实验室主任")
                .doesNotContain("精密仪器, 实验室主任");
    }

    /**
     * 验证自身 value 不会作为自己的 context。
     */
    @Test
    void shouldExcludeOwnValueFromSelfContext() {
        FactCardRecord factCardRecord = factCardRecord(
                """
                        {
                          "structure": "key_value_list",
                          "pathAware": true,
                          "items": [
                            {
                              "key": "name",
                              "value": "校园预约系统",
                              "parentPath": "borrowing_system",
                              "keyPath": "borrowing_system.name",
                              "pathSegments": ["borrowing_system", "name"]
                            },
                            {
                              "key": "version",
                              "value": "v2.3.1",
                              "parentPath": "borrowing_system",
                              "keyPath": "borrowing_system.version",
                              "pathSegments": ["borrowing_system", "version"]
                            }
                          ]
                        }
                        """
        );

        List<FactCardTerminalUnitRecord> records = materializer.materialize(factCardRecord);

        assertThat(records).hasSize(2);
        FactCardTerminalUnitRecord nameRecord = records.stream()
                .filter(r -> "name".equals(r.getTerminalKey()))
                .findFirst().orElseThrow();
        assertThat(nameRecord.getFieldDescription())
                .as("CJK string item should not contain its own value as context")
                .doesNotContain("校园预约系统");
        assertThat(nameRecord.getFtsText())
                .as("ftsText should still contain own valueText directly")
                .contains("校园预约系统");
    }

    /**
     * 验证非 CJK、过长、空值不会进入 context。
     */
    @Test
    void shouldFilterNonCjkLongAndEmptyFromContext() {
        FactCardRecord factCardRecord = factCardRecord(
                """
                        {
                          "structure": "key_value_list",
                          "pathAware": true,
                          "items": [
                            {
                              "key": "config_name",
                              "value": "有效配置",
                              "parentPath": "settings",
                              "keyPath": "settings.config_name",
                              "pathSegments": ["settings", "config_name"]
                            },
                            {
                              "key": "max_connections",
                              "value": "1000",
                              "parentPath": "settings",
                              "keyPath": "settings.max_connections",
                              "pathSegments": ["settings", "max_connections"]
                            },
                            {
                              "key": "api_endpoint",
                              "value": "https://api.example.com/v5",
                              "parentPath": "settings",
                              "keyPath": "settings.api_endpoint",
                              "pathSegments": ["settings", "api_endpoint"]
                            },
                            {
                              "key": "enabled",
                              "value": "true",
                              "parentPath": "settings",
                              "keyPath": "settings.enabled",
                              "pathSegments": ["settings", "enabled"]
                            },
                            {
                              "key": "empty_field",
                              "value": "   ",
                              "parentPath": "settings",
                              "keyPath": "settings.empty_field",
                              "pathSegments": ["settings", "empty_field"]
                            }
                          ]
                        }
                        """
        );

        List<FactCardTerminalUnitRecord> records = materializer.materialize(factCardRecord);

        assertThat(records).hasSize(4); // empty_field is skipped
        for (FactCardTerminalUnitRecord record : records) {
            String description = record.getFieldDescription();
            assertThat(description)
                    .as("context must not contain number value")
                    .doesNotContain("1000");
            assertThat(description)
                    .as("context must not contain URL")
                    .doesNotContain("api.example.com");
            assertThat(description)
                    .as("context must not contain boolean")
                    .doesNotContain("true");
            if (!"有效配置".equals(record.getValueText())) {
                assertThat(description)
                        .as("non-CJK-sibling should get CJK sibling context")
                        .contains("context: 有效配置");
            }
        }
    }

    /**
     * 验证每个 parentPath descriptor 数量上限为 2。
     */
    @Test
    void shouldLimitDescriptorsPerParentPath() {
        FactCardRecord factCardRecord = factCardRecord(
                """
                        {
                          "structure": "key_value_list",
                          "pathAware": true,
                          "items": [
                            {
                              "key": "label_a",
                              "value": "第一个",
                              "parentPath": "group_x",
                              "keyPath": "group_x.label_a",
                              "pathSegments": ["group_x", "label_a"]
                            },
                            {
                              "key": "label_b",
                              "value": "第二个",
                              "parentPath": "group_x",
                              "keyPath": "group_x.label_b",
                              "pathSegments": ["group_x", "label_b"]
                            },
                            {
                              "key": "label_c",
                              "value": "第三个",
                              "parentPath": "group_x",
                              "keyPath": "group_x.label_c",
                              "pathSegments": ["group_x", "label_c"]
                            },
                            {
                              "key": "target_metric",
                              "value": "42",
                              "parentPath": "group_x",
                              "keyPath": "group_x.target_metric",
                              "pathSegments": ["group_x", "target_metric"]
                            }
                          ]
                        }
                        """
        );

        List<FactCardTerminalUnitRecord> records = materializer.materialize(factCardRecord);

        assertThat(records).hasSize(4);
        FactCardTerminalUnitRecord target = records.stream()
                .filter(r -> "target_metric".equals(r.getTerminalKey()))
                .findFirst().orElseThrow();
        String description = target.getFieldDescription();
        assertThat(description).contains("context:");
        // 最多 2 个 descriptor: "第一个", "第二个" (保持原始顺序)
        assertThat(description).contains("第一个").contains("第二个");
        assertThat(description).doesNotContain("第三个");
        // 验证 description 中只出现一次 "context:"
        int contextCount = 0;
        int idx = 0;
        while ((idx = description.indexOf("context:", idx)) != -1) {
            contextCount++;
            idx++;
        }
        assertThat(contextCount).as("only one context entry").isEqualTo(1);
    }

    /**
     * 验证 fieldAliases 不包含 sibling descriptor。
     */
    @Test
    void shouldNotAddSiblingDescriptorsToFieldAliases() {
        FactCardRecord factCardRecord = factCardRecord(
                """
                        {
                          "structure": "key_value_list",
                          "pathAware": true,
                          "items": [
                            {
                              "key": "display_name",
                              "value": "中文显示名",
                              "parentPath": "group_y",
                              "keyPath": "group_y.display_name",
                              "pathSegments": ["group_y", "display_name"]
                            },
                            {
                              "key": "timeout_seconds",
                              "value": "30",
                              "parentPath": "group_y",
                              "keyPath": "group_y.timeout_seconds",
                              "pathSegments": ["group_y", "timeout_seconds"]
                            }
                          ]
                        }
                        """
        );

        List<FactCardTerminalUnitRecord> records = materializer.materialize(factCardRecord);

        assertThat(records).hasSize(2);
        FactCardTerminalUnitRecord numberRecord = records.stream()
                .filter(r -> "timeout_seconds".equals(r.getTerminalKey()))
                .findFirst().orElseThrow();
        String aliasesJson = numberRecord.getFieldAliasesJson();
        assertThat(aliasesJson)
                .as("fieldAliases must not contain sibling descriptor value")
                .doesNotContain("中文显示名");
        assertThat(numberRecord.getFieldDescription())
                .as("fieldDescription should contain sibling context")
                .contains("context: 中文显示名");
    }

    /**
     * 验证无 parentPath 或只有一个 item 时不产生 context。
     */
    @Test
    void shouldNotAddContextWhenNoSharedParentPathOrSingleItem() {
        FactCardRecord factCardRecord = factCardRecord(
                """
                        {
                          "structure": "key_value_list",
                          "pathAware": false,
                          "items": [
                            {
                              "key": "isolated_field",
                              "value": "孤立值",
                              "keyPath": "isolated_field",
                              "pathSegments": ["isolated_field"]
                            }
                          ]
                        }
                        """
        );

        List<FactCardTerminalUnitRecord> records = materializer.materialize(factCardRecord);

        assertThat(records).hasSize(1);
        assertThat(records.get(0).getFieldDescription())
                .as("single CJK value with no parentPath should not get sibling context")
                .doesNotContain("context:");
        assertThat(records.get(0).getFieldAliasesJson())
                .as("fieldAliases should contain English fieldLabel aliases")
                .contains("isolated_field");
    }

    /**
     * 构造测试事实卡。
     *
     * @param itemsJson 结构化条目 JSON
     * @return 事实卡记录
     */
    private FactCardRecord factCardRecord(String itemsJson) {
        return new FactCardRecord(
                Long.valueOf(42L),
                "fc:terminal-unit:test",
                Long.valueOf(5L),
                Long.valueOf(6L),
                FactCardType.FACT_ENUM,
                AnswerShape.ENUM,
                "Synthetic Settings",
                "Synthetic scalar assignments.",
                itemsJson,
                "alpha_limit=31\nbeta_mode=enabled",
                List.of(Long.valueOf(7L)),
                List.of(Long.valueOf(8L)),
                0.91D,
                FactCardReviewStatus.VALID,
                "hash-terminal-unit-test",
                OffsetDateTime.now().minusSeconds(30L),
                OffsetDateTime.now()
        );
    }
}
