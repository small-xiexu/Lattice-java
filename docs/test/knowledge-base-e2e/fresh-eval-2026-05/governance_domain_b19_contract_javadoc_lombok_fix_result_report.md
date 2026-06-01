# B19: Governance Domain 领域对象契约治理报告

改造时间：2026-06-01
改造人：agentA
批次：B19（5 类，一轮完成）

---

## 1. 修改文件清单

| 文件 | 字段 | @Getter | 删除 getter | 保留 |
|---|---|---|---|---|
| `CrossValidatePayload.java` | 2 | 1 | 2 | @JsonCreator, unsupported(), null-coalescing |
| `PropagationCheckPayload.java` | 2 | 1 | 2 | @JsonCreator, unaffected(), null-coalescing |
| `LifecycleItem.java` | 9 | 1 | 9 | 双构造器（9P + 7P, articleKey→conceptId） |
| `LifecycleTransitionResult.java` | 8 | 1 | 8 | 双构造器（8P + 6P, articleKey→conceptId） |
| `LifecycleReport.java` | 6 | 1 | 6 | 构造器，items 可变 List 风险已知不修复 |

**合计**：5 @Getter，27 getter 删除。

---

## 2. 关键保留

| 类 | 保留项 | 说明 |
|---|---|---|
| `CrossValidatePayload` | `@JsonCreator` + `unsupported()` | Boolean→boolean null-coalescing，evidence trim-or-empty |
| `PropagationCheckPayload` | `@JsonCreator` + `unaffected()` | 同上 null-coalescing 防御 |
| `LifecycleItem` | 双构造器 | 7P 构造器 articleKey 回退为 conceptId |
| `LifecycleTransitionResult` | 双构造器 | 6P 构造器 articleKey 回退为 conceptId |
| `LifecycleReport` | items 可变 List | 已知风险，本轮不修复 |

---

## 3. 治理语义标注

| 字段 | 类 | 标注 |
|---|---|---|
| `supported` | CrossValidatePayload | false 时纠错被拒绝进入主链 |
| `affected` | PropagationCheckPayload | true 时触发下游重编译 |
| `lifecycle` | LifecycleItem/TransitionResult | active/deprecated/archived，驱动前端和治理 |
| `articleKey` | LifecycleItem/TransitionResult | 7P/6P 构造器中回退为 conceptId |
| `otherCount` | LifecycleReport | 未归入标准桶的数量 |
| `items` | LifecycleReport | 可变 List 风险已知，不修复 |

---

## 4. 验证

```
mvn compile: BUILD SUCCESS
@Getter: 5/5 ✓
@Data/@Setter/@Builder: 0 ✓
unsupported()/unaffected() 保留 ✓
双构造器保留 ✓
LifecycleReport.items 未加防御性拷贝 ✓
```

---

## 5. 合规确认

| 约束 | 状态 |
|---|---|
| 修改 5 个目标文件 | 通过 |
| 仅 @Getter，无 @Data/@Setter | 通过 |
| @JsonCreator/static factory 保留 | 通过 |
| 双构造器保留 | 通过 |
| items 可变 List 未修复 | 通过 |
| 未触碰 B17/B18 | 通过 |
| 未 stage/commit/push | 通过 |
