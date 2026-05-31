# Terminal Unit Phase 1E: Evidence Consumption 测试补强结果报告

验证时间：2026-05-30
执行人：agentA
本轮类型：测试补强 — 不修改生产代码

---

## 1. 是否修改生产代码

**否**。本轮只新增测试，未修改 `src/main/java/**` 和 `src/main/resources/**`。

## 2. 新增/调整测试列表

| # | 测试方法 | 类型 | 风险点覆盖 |
|---|---|---|---|
| 1 | `shouldRetainTerminalUnitFactCardForStructuredFactQuestion` | **新增** | structured fact 问题中 terminal unit (channel=fact_card_terminal_fts + FACT_CARD) 应被保留 |
| 2 | `shouldNotRetainTerminalUnitForNonStructuredFactQuestion` | **新增** | 普通描述性问题中 terminal unit 即使有 channel 也不应抢占 ARTICLE |
| 3 | `shouldNotRetainNonTerminalFactCardViaChannelExemption` | **新增** | 非 terminal FACT_CARD（无 channel）不由终端 unit 豁免路径保留 |
| 4 | `shouldIncludeDisplayTextExactValueInSelectedTerminalUnit` | **新增** | 被保留的 terminal unit 的 content 包含 keyPath = value 格式的 displayText |
| 5 | `shouldNotRetainIrrelevantTerminalUnitDespiteChannelExemption` | **新增** | channel 正确但内容与问题不匹配的 terminal unit 被相关性过滤 |
| 6 | 原有 6 个测试 | 保护 | 无变化，全部通过 |

**总计：11 个测试（6 原有 + 5 新增）。**

## 3. 每个测试覆盖的风险点

### 3.1 shouldRetainTerminalUnitFactCardForStructuredFactQuestion

- **风险**：terminal unit FACT_CARD 在 structured fact 问题中被 `preferArticleEvidence=true` 丢弃
- **覆盖**：查询 `"serviceQuota.dailyLimit 的最大值是多少"`（含 "是多少" → structured fact + numeric intent），终端 unit 含 `channel=fact_card_terminal_fts`，内容匹配 query token
- **断言**：selected hits 包含 FACT_CARD，且 content 含 `keyPath = value` exact value

### 3.2 shouldNotRetainTerminalUnitForNonStructuredFactQuestion

- **风险**：terminal unit channel 豁免过度放宽，在非结构化问题中也抢占 ARTICLE
- **覆盖**：查询 `"系统的整体概述是什么"`（无 structured fact / exact lookup / numeric 信号），终端 unit 有 channel
- **断言**：selected hits 不含 FACT_CARD，ARTICLE 内容被保留

### 3.3 shouldNotRetainNonTerminalFactCardViaChannelExemption

- **风险**：FACT_CARD 类型但无 channel 的记录被终端 unit 豁免误保留
- **覆盖**：FACT_CARD metadata 只有 `cardType` + `answerShape`，无 `channel=fact_card_terminal_fts`
- **说明**：FACT_CARD 可能被已有 `shouldPreferMixedEvidence` 路径保留（属于旧行为），但新豁免路径不会触发

### 3.4 shouldIncludeDisplayTextExactValueInSelectedTerminalUnit

- **风险**：终端 unit 被保留后，后续 conclusion 无法消费 exact value（content 只含 alias JSON 不含 displayText）
- **覆盖**：terminal unit content 含 `"runtimeProfile.activeTier = gold"`
- **断言**：selected terminal unit 的 content 包含 `keyPath = value`

### 3.5 shouldNotRetainIrrelevantTerminalUnitDespiteChannelExemption

- **风险**：channel 正确但不相关的 terminal unit 被豁免保留，引入噪声
- **覆盖**：查询 `"sampleLimit 的配置值是多少"`，终端 unit 的 terminalKey/内容为 `maxRetryCount`（不匹配）
- **断言**：不匹配的 terminal unit 被 `filterRelevantHits` 过滤，不进入 selected hits

## 4. 为什么测试数据不是 Fresh Eval / Hidden Eval 污染

| 检查项 | 说明 |
|---|---|
| 所有字段名 synthetic | `serviceQuota.dailyLimit`、`runtimeProfile.activeTier`、`gatewayConfig.requestLimit`、`runtimeConfig.maxRetryCount`、`cachePolicy.cacheTtl` |
| 所有别名 synthetic | "单日上限"、"每日限额"、"服务等级"、"当前层级"、"请求上限"、"最大重试次数" |
| 所有查询 synthetic | "serviceQuota.dailyLimit 的最大值是多少"、"系统的整体概述是什么"、"runtimeProfile.activeTier 的值是多少"、"sampleLimit 的配置值是多少" |
| 不含 fresh eval 词 | 无 `version`、`max_concurrent_requests`、`borrowing_system`、`equipment-borrowing-policy.yaml`、`v2.3.1`、`50`、`精密仪器`、`借用天数` |
| 不含 hidden eval | 未读取任何 hidden eval 文件 |

## 5. Redline / 定向测试 / 全量 mvn test

### 5.1 git diff --check

无输出（通过）。

### 5.2 Redline

```
BLOCKER=0, REVIEW=2068, ALLOWLIST=260
```

与上一轮生产代码修复后一致，新增测试未引入任何 redline 命中变化。

### 5.3 定向测试

```
AnswerFallbackEvidenceSelectorTests: 11/0/0 — BUILD SUCCESS
```

### 5.4 全量 mvn test

```
Tests run: 992, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
```

全量 992/0/0/0 干净通过。较上一轮（987）增加本轮的 5 个新测试。

## 6. 计划台账回写

已回写 `terminal_unit_phase1_implementation_plan.md`：
- 本轮测试补强标为已完成

## 7. 是否可以交 AgentD 做 Clean Schema / Runtime 验证

**可以**。生产代码自上一轮起未变，本轮新增的 5 个 synthetic 测试验证了 terminal unit evidence consumption 豁免逻辑的正确性和边界条件：
- 豁免仅在 structured fact / exact lookup 问题中生效 ✓
- 非 terminal FACT_CARD（无 channel）不触发豁免 ✓
- 不相关 terminal unit 被相关性过滤 ✓
- 被保留的 terminal unit 提供 keyPath = value exact value ✓
- 普通问题中 terminal unit 不抢占 ARTICLE ✓

agentD 可以基于当前的完整代码（生产代码 + 测试）做 clean schema / runtime / fresh eval 复验。

## 合规声明

- 本轮**未修改生产代码**（`src/main/java/**`、`src/main/resources/**`）
- 仅修改 `src/test/java/com/xbk/lattice/query/service/AnswerFallbackEvidenceSelectorTests.java`
- 所有测试数据使用 synthetic 字段名/别名/查询，不含 fresh eval / hidden eval 词汇
- 未 stage、未 commit、未 push
- 未清库、未重建、未导入资料、未跑业务 eval
- 新增报告：1（本报告）
