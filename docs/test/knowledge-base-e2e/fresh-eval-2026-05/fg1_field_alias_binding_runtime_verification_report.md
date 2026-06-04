# FG1 Field-Alias-Enricher 运行时绑定修复与验证报告

验证时间：2026-06-01
执行人：agent
批次：FG1 运行时修复

---

## 1. 根因

数据库 `agent_model_bindings` 中缺少 `scene=compile, agent_role=field-alias-enricher` 的绑定，导致 `LlmFactCardTerminalUnitFieldAliasEnricher.isLlmRouteAvailable(scopeId)` 返回 `false`，terminal unit 的 `field_aliases_json` 全部没有中文语义别名。FG1 查询时无法通过别名匹配 `late_fee_per_day` → 逾期罚金。

## 2. 修复方式

通过后台 API 创建绑定：

```
POST /api/v1/admin/llm/bindings
{
  "scene": "compile",
  "agentRole": "field-alias-enricher",
  "primaryModelProfileId": 1,
  "enabled": true,
  "operator": "b19-fix"
}
```

**绑定结果**：
| 字段 | 值 |
|---|---|
| id | 11 |
| scene | compile |
| agentRole | field-alias-enricher |
| primaryModelProfileId | 1 (gpt-5.5, CHAT) |
| routeLabel | compile.field-alias-enricher.gpt-5-5 |
| enabled | true |

未使用 SQL 直接写入——后台 API 可用。

## 3. 重新编译

| 项目 | 值 |
|---|---|
| jobId | `eaaadb7f-aef2-44bb-9461-62d71421daa2` |
| sourceDir | `docs/test/knowledge-base-e2e/fresh-eval-2026-05/sources/02_structured` |
| status | **SUCCEEDED** |
| derivedStatus | SUCCEEDED |

编译成功，状态正常。

## 4. FG1 验证

### 查询
"设备借用政策中，精密仪器和常规设备的逾期罚金分别是多少？"

### 答案
> 精密仪器的逾期罚金为 20，常规设备的逾期罚金为 5。

**同时包含两个 terminal unit 值，citation 支撑完整。**

## 5. 结论

- FG1 通过，不需要进入代码修复轮
- 修复方式：创建 `compile/field-alias-enricher` 模型绑定（API 方式）
- 编译后 `field_aliases_json` 已正确生成中文别名
- `late_fee_per_day` 的中文别名（逾期罚金）通过 FTS 索引可被检索命中

## 6. 残留风险

无。绑定创建后 `field-alias-enricher` 随后的所有编译都会生效。
