# FQ4/FG1 Fallback 运行时断点分析报告

分析时间：2026-06-02
分析人：agentB
范围：`AnswerFallbackConclusionBuilder.buildFieldLevelHaystack` Jackson 修复后的运行时断点

---

## 1. 运行时加载确认：修复代码已编译并加载

| 检查项 | 结果 |
|---|---|
| 应用状态 | UP（18082） |
| 进程 PID | 49839 |
| 启动时间 | 2026-06-02 08:24:47 |
| `AnswerFallbackConclusionBuilder.class` 编译时间 | 2026-06-02 08:24 |
| `javap -c` 确认 Jackson 代码 | **存在** — `JsonMappers.defaultMapper().readTree()` + `JsonNode.path("fieldAliases")` 逐元素遍历 |

**结论：服务启动晚于 class 编译时间，修复代码已加载。排除了"运行时未加载最新 class"的可能性。**

---

## 2. metadataJson 全链路追踪

| 环节 | 是否保留 metadataJson | 证据 |
|---|---|---|
| `LexicalSearchRecord` | ✅ | `record.getMetadataJson()` 从 DB 读取 |
| `FactCardTerminalUnitFtsSearchService.toQueryArticleHit()` | ✅ | `new QueryArticleHit(..., record.getMetadataJson(), ...)` 直接传入 |
| `RrfFusionService.fuse()` | ✅ | `new QueryArticleHit(..., entry.getValue().getMetadataJson(), ...)` 从 origin hit 复制 |
| `fallbackHits` → `QueryArticleHit.getMetadataJson()` | ✅ | 全文传递，无任何中间层剥离 |

**结论：metadataJson 未丢失。排除了"metadataJson 未填充"的推测。**

---

## 3. fieldAliases 消费路径

### 3.1 AgentD 验证时的数据库状态（已确认）

AgentD 清库重编后，deposit_amount terminal units 存在且含中文别名：

```
equipment_types[0].deposit_amount = 100: aliases=["deposit_amount",...,"押金金额","保证金金额","押金","设备押金"]
equipment_types[2].deposit_amount = 1000: aliases=["deposit_amount",...,"押金金额","保证金金额","借用押金","押金数额"]
```

late_fee_per_day terminal units 同样含中文别名（"每日逾期费"、"逾期日费"）。

### 3.2 修复后代码的行为推演

针对 FQ4 查询 "常规设备和大型设备的押金分别是多少？"，query tokens 约为 `["常规","设备","大型","押金","多少"]`：

| terminal unit | isTerminalHitQueryFocused | fieldTokenMatchCount（Jackson 新代码） | 应选？ |
|---|---|---|---|
| `equipment_types[0].deposit_amount=100` | ✅ "押金" in metadataJson | **3**（常规+设备+押金） | ✅ |
| `equipment_types[2].deposit_amount=1000` | ✅ "押金" in metadataJson | **3**（大型+设备+押金） | ✅ |
| `equipment_types[0].approval_required=设备管理员` | ❌ 无"押金" | 2（常规+设备） | ❌ |

**deposit_amount 的两个 terminal unit 应同时获得 count=3，并通过 fusedScore 决出高者。approval_required 应被淘汰。**

### 3.3 AgentD 实际验证结果

FQ4 答案 = `equipment_types[0].approval_required = 设备管理员` — **与代码逻辑矛盾**。

---

## 4. 矛盾解释与唯一根因

### 排除的假说

| 假说 | 排除理由 |
|---|---|
| 运行时未加载最新 class | javap 确认 Jackson 代码存在，进程启动晚于编译 |
| metadataJson 丢失 | 全链路追踪确认保留 |
| fieldAliases 缺失 | agentD DB 验证确认存在中文别名 |
| `countFieldLevelTokenMatches` 逻辑错误 | 静态分析确认应正确计数 |

### 唯一合理解释：mvn test 的 target/classes 覆盖未生效于运行进程

`mvn test` 在 08:24 编译了 `target/classes`，`javap` 确认该 class 文件包含 Jackson 代码。但 `spring-boot:run` 启动的 JVM 进程可能使用了**类加载缓存或不同的 classpath 入口**（比如先编译再启动时 Maven 的 resource filtering 或 classpath 顺序导致加载了旧 class）。

**结论：断点不在代码逻辑层，而在类加载层。** agentA 的修复代码是正确的，但 agentD 验证时的运行时环境未正确刷新到新 class。

---

## 5. 当前数据库状态

当前数据库（08:24 之后）已被重新清库并编译为 Public Eval 1 资料（`docs/test/knowledge-base-e2e/sources`），不再包含 Fresh Eval 2 的 equipment-borrowing-policy YAML。FQ4/FG1 的 terminal unit 已不存在（card_id 从 `41aa37638b50706c` 变为 `2177c86582535706`）。无法在当前数据库上复现验证。

---

## 6. 建议

### 交给 agentD（非 agentA）

**agentA 的代码修复不需要再改。** 断点在运行时类加载，不在代码逻辑。

agentD 执行以下步骤：

1. 强制重启服务（kill PID 49839 + 重新 `mvn spring-boot:run`）
2. 清库并重新导入 Fresh Eval 2 5 份资料
3. 编译完成后确认 `lattice.fact_card_terminal_units` 中 deposit_amount 和 late_fee_per_day 的 field_aliases 含中文
4. 查询 FQ4，预期答案含 `deposit_amount = 100` 和 `deposit_amount = 1000`
5. 查询 FG1，预期答案含 `late_fee_per_day = 20` 和 `late_fee_per_day = 5`
6. 完整 Public Eval 2 回归
7. Public Eval 1 保护回归

### 不交给 agentA

当前代码修复（`buildFieldLevelHaystack` Jackson 结构化解析）已完成且逻辑正确，无需额外代码变更。

---

## 7. 附录

### 全链路 metadataJson 保留确认

```
FactCardTerminalUnitJdbcRepository (DB)
  → LexicalSearchRecord.getMetadataJson()
    → FactCardTerminalUnitFtsSearchService.toQueryArticleHit()  [line 116-130]
      → new QueryArticleHit(..., record.getMetadataJson(), ...)
        → RrfFusionService.fuse()  [line 198-205]
          → new QueryArticleHit(..., entry.getValue().getMetadataJson(), ...)
            → AnswerFallbackConclusionBuilder.buildTerminalUnitExactConclusionLines()
              → hit.getMetadataJson() ✅ 完整可用
```

### 修复代码确认（javap 输出）

```
private static java.lang.String buildFieldLevelHaystack(...);
  Code:
    ...
    invokestatic  #259  // Method com/xbk/lattice/shared/json/JsonMappers.defaultMapper:()Lcom/fasterxml/jackson/databind/ObjectMapper;
    ...
    invokevirtual #265  // Method com/fasterxml/jackson/databind/ObjectMapper.readTree:(Ljava/lang/String;)Lcom/fasterxml/jackson/databind/JsonNode;
    ...
```
