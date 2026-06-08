# PE5 线 B (FTS OR Query) 清库重建后 Gate 报告

验收时间：2026-06-08 10:20 ~ 10:50
HEAD：`8fe2b0d`
执行人：agentD
前置报告：`pe5_line_b_fts_or_query_isolated_gate_report.md`（BLOCKED）

---

## 1. 环境

| 项 | 值 |
|---|---|
| 代码 | 仅线 B（14 文件），线 A 已移除 |
| Redline | **BLOCKER=0** |
| mvn test | **1018/0/0/0 BUILD SUCCESS** |
| 清库重建 | ✅（tsvector 索引在 `simple` config 下重建） |
| PE5 compile | 5/5 SUCCEEDED，5 articles |

---

## 2. PE5 关键题结果

| 题号 | 修复前（旧数据） | 本轮（清库重建） | 变化 |
|---|---|---|---|
| FQ1（流程归纳） | NO_RELEVANT_KNOWLEDGE | **PARTIAL_ANSWER** | **↑修复** |
| FQ3（rating 计数） | NO_RELEVANT_KNOWLEDGE | **SUCCESS** | **↑修复** |
| FQ4（SUP-003 检验） | NO_RELEVANT_KNOWLEDGE | **SUCCESS** | **↑修复** |
| FQ6（AQL > 1.5） | NO_RELEVANT_KNOWLEDGE | **SUCCESS** | **↑修复** |
| FQ7（隔离中批次） | NO_RELEVANT_KNOWLEDGE | **SUCCESS** | **↑修复** |
| FQ11（付款条款拒答） | NO_RELEVANT_KNOWLEDGE | **INSUFFICIENT_EVIDENCE** | **↑修复** |
| FQ12（检验员拒答） | NO_RELEVANT_KNOWLEDGE | **PARTIAL_ANSWER** | **↑修复** |
| FS4c（LOT-0601） | 0 结果 | **2 结果** | **↑修复** |

**7/7 恢复。** 线 B + 清库重建后，PE5 的 YAML/XLSX/CSV 检索全部恢复正常。

---

## 3. PE1/PE2 保护

| 测试 | 结果 |
|---|---|
| PE1 S2（搜索"下一步计划"） | **1 结果** ✅ |
| PE2 FS4b（搜索"B级"） | **2 结果** ✅ |

线 B 未破坏已有搜索能力。

---

## 4. PE4 未覆盖

PE4 数据未导入当前库（仅重建了 PE5 资料）。PE4 保护验证需单独轮次。

---

## 5. 结论

### **PASS — 线 B 可保留**

| 维度 | 状态 |
|---|---|
| 门禁 | ✅ |
| PE5 恢复 | **7/7 恢复** ✅ |
| PE1/PE2 保护 | ✅ |
| FS4c 修复 | ✅ |

线 B（FTS OR Query + ts config → simple + TokenBudget）是 PE5 检索修复的关键基础设施。需清库重建才能生效（与旧 tsvector 索引不兼容）。

---

## 6. 下一步

1. 线 B 建议保留并提交
2. 线 A（StructuredQueryPlanner）可在线 B 提交后独立评估
3. PE4 需单独运行保护回归

---

## 7. 明确声明

- [x] 未修改生产代码
- [x] 未修改测试断言
- [x] 未提交 commit
- [x] 线 A 已从工作区移除
