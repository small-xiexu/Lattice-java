# B19 Governance Domain 只读边界审查报告

生成时间：2026-06-01
审查人：agentB（只读边界审查）
状态：完成
目标批次：B19 — `governance/domain`（5 个类）

---

## 一、结论：5 个类无需拆分，一轮完成

全部 5 个类均为不可变 final-field 领域对象，0 个 @Data，0 个 Lombok，风险极低。可安全加 `@Getter` 删除 27 个手写 getter。

---

## 二、候选文件总表

| # | 类 | 类型 | 字段 | 构造器 | 特殊方法 | Lombok | 处置 |
|---|---|---|---|---|---|---|---|
| 1 | `CrossValidatePayload` | 不可变 @JsonCreator | 2 final | 1（含 Boolean→boolean 防御转换） | `unsupported()` factory | 无 | @Getter + Javadoc |
| 2 | `LifecycleItem` | 不可变 | 9 final | 2（9P/7P telescoping，articleKey=conceptId fallback） | — | 无 | @Getter + Javadoc |
| 3 | `LifecycleReport` | 不可变 | 6 final | 1 | — | 无 | @Getter + Javadoc |
| 4 | `LifecycleTransitionResult` | 不可变 | 8 final | 2（8P/6P telescoping） | — | 无 | @Getter + Javadoc |
| 5 | `PropagationCheckPayload` | 不可变 @JsonCreator | 2 final | 1（含 Boolean→boolean 防御转换） | `unaffected()` factory | 无 | @Getter + Javadoc |

---

## 三、每类详细分析

### 3.1 CrossValidatePayload

- **领域语义**：纠错交叉验证的结构化输出。当用户提交文章纠错时，系统查询源文件验证纠错是否有证据支持
- **构造器防御逻辑**：`supported = supported != null && supported`（Boolean 包装类型 null→false 转换），`evidence = evidence == null ? "" : evidence.trim()`
- **static factory**：`unsupported()` 创建默认不支持结果
- **2 getter**：`isSupported()`（boolean，与 Lombok 一致）、`getEvidence()`
- **处置**：@Getter + 字段 Javadoc，保留 @JsonCreator 构造器和 unsupported() factory

### 3.2 PropagationCheckPayload

- **领域语义**：传播影响检查的结构化输出。当文章内容变更时，检查是否影响依赖/引用该文章的其他文章
- **构造器防御逻辑**：与 CrossValidatePayload 完全相同的模式（Boolean→boolean、String→trim-or-empty）
- **static factory**：`unaffected()` 创建默认不受影响结果
- **2 getter**：`isAffected()`（与 Lombok 一致）、`getReason()`
- **处置**：@Getter + 字段 Javadoc，保留 @JsonCreator 构造器和 unaffected() factory

### 3.3 LifecycleItem

- **领域语义**：单篇文章的生命周期状态条目（active/deprecated/archived），含审查状态和变更原因
- **双构造器**：9-param（全参）和 7-param（省略 sourceId/articleKey，内部传递 `conceptId` 作为两者）
- **9 getter**：部分无 Javadoc（getSourceId、getArticleKey），其余有基本 Javadoc
- **处置**：@Getter + 字段 Javadoc，保留双构造器

### 3.4 LifecycleTransitionResult

- **领域语义**：单篇文章生命周期变更后的最小返回结果
- **结构**：与 LifecycleItem 高度相似（8 字段 vs 9 字段），但语义为"变更结果"而非"状态条目"
- **双构造器**：8-param（全参）和 6-param（省略 sourceId/articleKey）
- **8 getter**：部分无 Javadoc（getSourceId、getArticleKey）
- **处置**：@Getter + 字段 Javadoc，保留双构造器

### 3.5 LifecycleReport

- **领域语义**：生命周期分布汇总报告（总数 + 各状态的计数 + 条目列表）
- **6 final 字段**：5 个 int 计数 + List\<LifecycleItem\>
- **无防御性拷贝**：items List 是 final 但引用的是可变 List，getItems() 返回原始引用
- 这是本次审查发现的唯一潜在风险点，但按计划范围不在此轮修复
- **处置**：@Getter + 字段 Javadoc，保留构造器

---

## 四、必须保留的方法清单

| 类 | 方法 | 保留原因 |
|---|---|---|
| `CrossValidatePayload` | @JsonCreator 构造器 | Boolean→boolean null-coalescing 防御逻辑 |
| `CrossValidatePayload` | `unsupported()` | static factory，调用点依赖 |
| `PropagationCheckPayload` | @JsonCreator 构造器 | 同上 Boolean→boolean 防御逻辑 |
| `PropagationCheckPayload` | `unaffected()` | static factory，调用点依赖 |
| `LifecycleItem` | 双构造器（9P/7P） | 7P 构造器 articleKey=conceptId fallback 逻辑 |
| `LifecycleTransitionResult` | 双构造器（8P/6P） | 6P 构造器 articleKey=conceptId fallback 逻辑 |
| `LifecycleReport` | 构造器 | 构造入口 |

---

## 五、@Data/@Getter/@Setter 当前情况

| 状态 | 数量 |
|---|---|
| @Data | **0** |
| @Getter | **0** |
| @Setter | **0** |
| 任何 Lombok | **0** |
| 手写简单 getter（可用 @Getter 替代） | **27** |
| 手写非简单 getter（必须保留） | **0** |

**B19 是全部批次中最干净的**：0 个 @Data，0 个 Lombok，全部不可变，全部 getter 为简单字段访问。

---

## 六、字段 Javadoc 缺口

所有类仅有类级 Javadoc，**全部字段无 Javadoc**。需补充：

| 类 | 字段级注释需回答的问题 |
|---|---|
| `CrossValidatePayload` | supported 为 false 时如何影响纠错流程；evidence 为空代表什么 |
| `PropagationCheckPayload` | affected 为 true 时哪些下游文章需要重新审查；reason 为空代表什么 |
| `LifecycleItem` | lifecycle 取值（active/deprecated/archived）及对应的前端展示行为；updatedBy 审计追踪；articleKey vs conceptId 的区别 |
| `LifecycleTransitionResult` | 与 LifecycleItem 的区别（变更结果 vs 状态条目）；reason 为什么可为空 |
| `LifecycleReport` | totalArticles 与 activeCount+deprecatedCount+archivedCount+otherCount 的等式关系；items 是否全量还是截断 |

---

## 七、给 agentA 的下一轮提示词草案

```
交给 agentA。

本轮任务：对 B19 的 5 个 governance/domain 不可变对象做 @Getter + 领域语义 Javadoc 升级。

唯一进度台账：docs/plans/2026-05-31-模型契约注释与Lombok治理计划.md
边界审查报告：docs/test/knowledge-base-e2e/fresh-eval-2026-05/governance_domain_contract_analysis_report.md

## 修改范围（5 个文件，全部不可变）

### 含 @JsonCreator + factory（2 个）
1. CrossValidatePayload.java
   - @Getter + 2 字段 Javadoc
   - 删除 2 手写 getter（isSupported/getEvidence）
   - 保留 @JsonCreator 构造器（Boolean→boolean 防御转换）
   - 保留 unsupported() static factory

2. PropagationCheckPayload.java
   - @Getter + 2 字段 Javadoc
   - 删除 2 手写 getter（isAffected/getReason）
   - 保留 @JsonCreator 构造器（Boolean→boolean 防御转换）
   - 保留 unaffected() static factory

### 双构造器（2 个）
3. LifecycleItem.java
   - @Getter + 9 字段 Javadoc
   - 删除 9 手写 getter
   - 保留双构造器（7P→9P articleKey=conceptId fallback）

4. LifecycleTransitionResult.java
   - @Getter + 8 字段 Javadoc
   - 删除 8 手写 getter
   - 保留双构造器（6P→8P articleKey=conceptId fallback）

### 简单不可变（1 个）
5. LifecycleReport.java
   - @Getter + 6 字段 Javadoc
   - 删除 6 手写 getter
   - 保留构造器

## 禁止事项
- 禁止修改任何构造器（含 Boolean→boolean 防御转换逻辑）
- 禁止修改 static factory（unsupported/unaffected）
- 禁止修改 @JsonCreator/@JsonProperty/@JsonIgnoreProperties 注解
- 禁止修改字段类型、名称、final 修饰符
- 禁止引入 @Data/@Setter
- 禁止给 LifecycleReport.items 添加防御性拷贝（已知风险，不在此轮修复）

## 验收门槛
- mvn compile -pl . -q 通过

## 完成后：回写 B19 → "已完成"，输出 B19_fix_result_report.md
```

---

## 八、审查结论

- B19 仅 5 个类，**无需拆分**，一轮完成。
- **0 个 @Data，0 个 Lombok，全部不可变** — B19 是全部批次中最干净的。
- 可安全加 `@Getter` 删除 **27 个手写 getter**（全部简单字段访问）。
- **2 个 @JsonCreator 构造器含防御逻辑**（CrossValidatePayload/PropagationCheckPayload：Boolean→boolean null-coalescing），不可被 Lombok 覆盖。
- **2 个 static factory**（unsupported/unaffected）必须保留。
- **2 个双构造器**（LifecycleItem/LifecycleTransitionResult：articleKey=conceptId fallback）必须保留。
- `LifecycleReport.getItems()` 返回原始 List 引用（无防御性拷贝），是已知风险，但不在此轮修复。
