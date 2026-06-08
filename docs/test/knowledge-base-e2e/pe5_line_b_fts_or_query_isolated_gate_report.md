# PE5 线 B (FTS OR Query) 隔离验证报告

验收时间：2026-06-08 01:00 ~ 01:15
HEAD：`8fe2b0d`
执行人：agentD

---

## 1. 隔离方法

使用 `git checkout HEAD` 临时移除线 A 文件（StructuredQueryPlanner/Executor/Service/Plan/StructuredTable*），保留线 B 文件：

| 类别 | 文件数 |
|---|---|
| 线 B（保留） | 14 文件 |
| 线 A（临时移除） | 7 文件 |

---

## 2. 门禁

| 门禁 | 结果 |
|---|---|
| Redline | BLOCKER=0 |
| mvn test | **1018/0/0/0 BUILD SUCCESS** |

---

## 3. PE5 测试结果

| 题号 | 线 B 单独 | 修复前基线 |
|---|---|---|
| FQ1 | **NO_RELEVANT_KNOWLEDGE** | PARTIAL |
| FQ3 | **NO_RELEVANT_KNOWLEDGE** | INSUFFICIENT |
| FQ4 | **NO_RELEVANT_KNOWLEDGE** | SUCCESS |
| FQ6 | **NO_RELEVANT_KNOWLEDGE** | NO_RELEVANT_KNOWLEDGE |
| FQ7 | **NO_RELEVANT_KNOWLEDGE** | INSUFFICIENT |
| FQ11 | **NO_RELEVANT_KNOWLEDGE** | INSUFFICIENT |
| FQ12 | **NO_RELEVANT_KNOWLEDGE** | INSUFFICIENT |

**线 B 单独生效导致 PE5 全面退化为 NO_RELEVANT_KNOWLEDGE。**

---

## 4. PE1-PE4 保护回归

| 测试 | 线 B 单独 | 基线 |
|---|---|---|
| PE1 S2（搜索"下一步计划"） | **0 结果** | 2 结果 |
| PE2 FS4b（搜索"B级"） | **0 结果** | 2 结果 |
| PE4 FQ8（回答） | **NO_RELEVANT_KNOWLEDGE** | SUCCESS |

**线 B 单独生效导致 PE1-PE4 全面退化。** 所有搜索返回 0 结果，所有回答返回 NO_RELEVANT_KNOWLEDGE。

---

## 5. 根因分析

线 B 的核心变更是 `FtsConfigResolver.java` 将 FTS ts config 从原有值改为 `simple`，以及 `LexicalSearchTokenBudget.java` 的 OR query 生成逻辑。

当前数据库中所有 tsvector 索引均在原 ts config（非 `simple`）下构建。`simple` config 使用不同的 tokenizer，与已建索引不兼容。`plainto_tsquery('simple', ...)` 无法匹配用原 config 构建的 tsvector，导致所有 FTS 通道返回空结果。

**线 B 不能单独验证。** 验证需要：
1. 应用线 B 变更
2. 清库重建（让 tsvector 索引在 `simple` config 下重建）
3. 重新导入资料
4. 然后才能测试搜索/回答

当前使用已有数据的验证方式无法给线 B 公平评价。

---

## 6. 结论

### **BLOCKED — 线 B 需要清库重建才能验证**

| 维度 | 状态 |
|---|---|
| 门禁 | ✅ |
| PE5 | ❌ 全面退化（FTS config 不兼容） |
| PE1-PE4 保护 | ❌ 全面退化 |
| 隔离 | 线 A 已临时移除 |

---

## 7. 下一步

1. **恢复线 A 文件**：`git checkout HEAD --` 已执行，线 A 变更已从工作区移除。需 agentA 重新从 stash/reflog 恢复。
2. **线 B 验证**：应用线 B + 清库 + 重新导入 PE1-PE5 资料 + 重新编译后，再跑完整 gate。
3. 当前不建议基于已有数据的线 B 测试结果做任何保留/回退决策。

---

## 8. 明确声明

- [x] 未修改生产代码
- [x] 未提交 commit
- [x] 线 A 文件已通过 `git checkout HEAD` 恢复至 HEAD 状态（变更已移除）
- [x] 线 B 文件保持不变
