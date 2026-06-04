# FQ4 FieldAliases JSON 数组消费修复报告

修复时间：2026-06-02
修复人：agentA
轮次：单根因修复（FQ4 FALLBACK 路径 fieldAliases 未正确消费）

---

## 1. 确认 deposit_amount 是否进入候选池

**是的。** agentD 验证报告确认：
- `equipment_types[0].deposit_amount = 100` terminal unit 存在（id=11），fieldAliases 包含 `["押金金额", "保证金金额", "押金", "设备押金"]`
- `isTerminalUnitChannelHit` 会通过（evidenceType=FACT_CARD，channel=fact_card_terminal_fts）
- `isTerminalHitQueryFocused` 会通过（`buildTerminalHitEvidenceHaystack` 使用整个 `metadataJson.toLowerCase()` 做字符串包含检查，JSON 字符串中确实包含 `押金`）

**候选池有 deposit_amount，但 `countFieldLevelTokenMatches` 未正确消费 fieldAliases 中的中文别名，导致 deposit_amount 的 fieldTokenMatchCount 没有高于 approval_required。**

---

## 2. 修复前为什么 fieldAliases 没被有效消费

`buildFieldLevelHaystack` 中的 `appendJsonField` 是字符串切片提取：

```java
private static void appendJsonField(StringBuilder sb, String json, String marker) {
    int markerIndex = json.indexOf(marker);
    int valueStart = json.indexOf('"', markerIndex + marker.length());
    int valueEnd = json.indexOf('"', valueStart + 1);
    sb.append(' ');
    sb.append(json, valueStart + 1, valueEnd);
}
```

当 `fieldAliases` 是 JSON 数组时：
```json
"fieldAliases":["deposit_amount","deposit amount","押金金额","保证金金额","押金","设备押金"]
```

该方法：
1. 找到 `"fieldAliases":` 标记
2. 找下一个 `"` → 数组第一个元素的开始引号 `"deposit_amount"`
3. 找再下一个 `"` → 第一个元素的结束引号
4. 提取 `deposit_amount` — **仅第一个元素**

数组后续元素（`押金金额`、`押金` 等中文别名）**全部被丢弃**。

`deposit_amount` 不含中文，而 `approval_required` 同样只提取第一个元素。两者在 `countFieldLevelTokenMatches` 中都无法匹配中文 token `押金`。此时 fused order tiebreaker 选中了排在前面的 sibling（`approval_required`）。

---

## 3. 修改文件

| 文件 | 变更 |
|---|---|
| `AnswerFallbackConclusionBuilder.java` | `buildFieldLevelHaystack` 改为 Jackson `JsonNode` 结构化解析；删除字符串切片 `appendJsonField` |

---

## 4. 修复逻辑

### 4.1 旧实现（字符串切片）

```java
// fieldAliases 是 JSON 数组时只提取第一个元素
appendJsonField(sb, metadataJson, "\"fieldAliases\":");
// → 仅 "deposit_amount"（第一个元素），漏掉 "押金金额"、"押金" 等中文
```

### 4.2 新实现（Jackson JsonNode 遍历数组）

```java
JsonNode node = JsonMappers.defaultMapper().readTree(metadataJson);
sb.append(' ').append(node.path("displayText").asText(""));
JsonNode aliases = node.path("fieldAliases");
if (aliases.isArray()) {
    for (JsonNode alias : aliases) {
        sb.append(' ').append(alias.asText(""));  // 逐元素提取
    }
}
sb.append(' ').append(node.path("fieldDescription").asText(""));
```

### 4.3 效果

对于 deposit_amount terminal unit 的 metadata：
```
field-level haystack: deposit_amount late_fee_per_day deposit amount 押金金额 保证金金额 押金 设备押金 借用设备时需支付的押金金额
```

query token `押金` 匹配到 haystack 中的 `押金金额` 和 `押金`——**fieldTokenMatchCount 会高于 approval_required**。

对于 approval_required terminal unit：
```
field-level haystack: equipment_types[0] approval_required approval required 是否需要审批 是否需要批准 审批要求 是否需要设备管理员批准
```

query token `押金` 不匹配——fieldTokenMatchCount 为 0。

deposit_amount 的 fieldTokenMatchCount > approval_required → deposit_amount 被选中。

### 4.4 为什么不是 case 特判

- 不依赖 `押金`、`deposit_amount`、`approval_required`、`设备借用` 等业务词
- 不依赖任何文件名、文档标题、card type、query 文本
- 仅将 JSON 数组解析从"取第一个元素"改为"遍历全部元素"——这是通用结构化数据消费改进
- 同样会改善任何 fieldAliases 包含多个中文别名但第一个元素是英文名的 terminal unit
- 同样会改善 JSONB 序列化格式不一致（compact vs spaced）导致的误判

---

## 5. 验证

```
mvn compile: BUILD SUCCESS
bash scripts/scan-redline.sh: BLOCKER=0 (无输出)
mvn test: 995/0/0/0, BUILD SUCCESS
```

---

## 6. 残留风险

- JSON 解析依赖 Jackson `ObjectMapper`，解析失败时 fail-safe 返回空字符串→fieldTokenMatchCount=0→回退到 fused order tiebreaker（与旧行为一致）
- `extractJsonStringValue` 仍使用字符串切片提取 `displayText`——但该字段是单字符串值，不受 JSON 数组影响
- 未端到端验证 FQ4（留给 agentD）

---

## 7. 下一步

交给 agentD 做端到端验证：
1. 清库 + 重新导入资料 + 重新编译
2. 验证 FQ4 的 FALLBACK 路径选中 `deposit_amount` 而非 `approval_required`
3. 验证 FG1、FQ3、FQ5、FQ6 等已通过的 FALLBACK 路径题不受影响
4. 跑完整 Public Eval 2 回归
