# B17b: Evidence 可变对象 @Data 降级 + 字段契约 Javadoc 报告

改造时间：2026-06-01
改造人：agentA
批次：B17b（B17 第 2/最后子批次，5/23 类）

---

## 1. 修改文件清单

| 文件 | 字段 | @Data→@Getter @Setter | 保留领域方法 |
|---|---|---|---|
| `AnswerProjection.java` | 8 | ✓ | projectionStatus 默认 ACTIVE |
| `AnswerProjectionBundle.java` | 2 | ✓ | answerMarkdown 大文本标注 |
| `EvidenceAnchor.java` | 11 | ✓ | identitySignature()/hasReusableIdentity()+3 normalize |
| `FactFinding.java` | 12 | ✓ | expectedFactKey/matchesFrozenFactKey/mergeIdentity/canEnterLedger/isBlank |
| `ProjectionCandidate.java` | 8 | ✓ | priority/verified/retrievalScore 排序语义 |

---

## 2. @Data 降级汇总

| 文件 | 降级前 | 降级后 | @NoArgsConstructor/@AllArgsConstructor |
|---|---|---|---|
| `AnswerProjection` | @Data | @Getter @Setter | 保留 |
| `AnswerProjectionBundle` | @Data | @Getter @Setter | 保留 |
| `EvidenceAnchor` | @Data | @Getter @Setter | 保留 |
| `FactFinding` | @Data | @Getter @Setter | 保留 |
| `ProjectionCandidate` | @Data | @Getter @Setter | 保留 |

**5 个 @Data 全部降级为 @Getter @Setter。0 @Data 残留。**

---

## 3. 保留领域方法

| 类 | 方法 | 用途 |
|---|---|---|
| `EvidenceAnchor` | `identitySignature()` | 冻结锚点身份串，用于 content hash 和去重 |
| `EvidenceAnchor` | `hasReusableIdentity()` | 判断最小 identity 前提 |
| `EvidenceAnchor` | `normalize()`/`normalizeChunk()`/`normalizeLine()` | 3 个 private 规范化方法 |
| `FactFinding` | `expectedFactKey()` | 构造 factKey 公式 |
| `FactFinding` | `matchesFrozenFactKey()` | factKey 匹配校验 |
| `FactFinding` | `mergeIdentity()` | run 内 merge/conflict 判定键 |
| `FactFinding` | `canEnterLedger()` | 最小可入账条件 |
| `FactFinding` | `isBlank()` | private 空白判断 |

---

## 4. B17 完整汇总（23 类）

| 子批次 | 类数 | @Data 降级 | getter 删除 | Javadoc |
|---|---|---|---|---|
| B17a | 18 | — | 24 | 43 enum + 24 field |
| B17b | 5 | 5 → 0 | — | 41 field |
| **合计** | **23** | **5** | **24** | **108** |

---

## 5. 验证

```
mvn compile: BUILD SUCCESS
@Data: 0/5 ✓
identitySignature/canEnterLedger/mergeIdentity/expectedFactKey: 保留 ✓
new ArrayList/ACTIVE/RAW: 默认值保留 ✓
```

---

## 6. 合规确认

| 约束 | 状态 |
|---|---|
| 5 个 @Data 全量降级 | 通过 |
| @NoArgsConstructor/@AllArgsConstructor 保留 | 通过 |
| EvidenceAnchor 领域方法保留 | 通过（5 个） |
| FactFinding 领域方法保留 | 通过（5 个） |
| 默认值保留 | 通过 |
| 未修改 B17a | 通过 |
| 未 stage/commit/push | 通过 |
