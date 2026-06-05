# Mixed Script Token Extraction — Runtime Gate 验证报告

验证时间：2026-06-05 10:05 ~ 10:25
执行人：agentD（验证 Agent）
修复报告：`mixed_script_token_extraction_fix_result_report.md`（agentA）
前置分析：`search_failures_s2_fs2_fs4b_analysis_report.md`（agentB）

---

## 1. 验证范围

验证 mixed script token extraction 修复对 FS4b "B级" 搜索 0 结果问题的修复效果，以及 FS1-FS4 搜索回归。

修复涉及文件：
- `QueryTokenExtractor.java`（混合脚本 token 提取）
- `LexicalSearchTokenBudget.java`（混合 token 正分）
- `QueryTokenExtractorTests.java`（定向测试）
- `LexicalSearchTokenBudgetTests.java`（定向测试）

---

## 2. Git Diff 摘要

```
LexicalSearchTokenBudget.java      | +36
QueryTokenExtractor.java           | +172
LexicalSearchTokenBudgetTests.java | +16
QueryTokenExtractorTests.java      | +89
```

4 个文件，无其他生产代码变更。未修改 AnswerGeneration、RRF、citation、prompt、题集。

---

## 3. 前置门禁

| 门禁 | 命令 | 结果 |
|---|---|---|
| Redline | `bash scripts/scan-redline.sh special_cases_report.md` | **BLOCKER=0** |
| QueryTokenExtractorTests | `mvn ... -Dtest=QueryTokenExtractorTests test` | **12/0/0/0** |
| LexicalSearchTokenBudgetTests | `mvn ... -Dtest=LexicalSearchTokenBudgetTests test` | **7/0/0/0** |
| 全量 mvn test | `mvn ... test` | **1004/0/0/0, BUILD SUCCESS** |

---

## 4. Runtime 环境

| 项 | 值 |
|---|---|
| 清库 | `bash scripts/reset-lattice-schema.sh` |
| 导入资料 | 4 份（Markdown/YAML/XLSX/CSV，PDF 未上传） |
| compile jobs | 4，SUCCEEDED |
| review queue | **0**（全部 auto-published） |
| 服务端口 | 18082 |
| LLM 绑定 | 11 条（含 field-alias-enricher） |

---

## 5. "B级" 搜索（核心 Gate）

| 搜索词 | 修复前结果数 | 修复后结果数 | rank 1 |
|---|---|---|---|
| `B级` | **0** | **2** | 化学品存储分级表 (chemical-storage-grading) |
| `B 级` | N/A (未测，应为 0) | **2** | 化学品存储分级表 (chemical-storage-grading) |

混合脚本 token 提取修复生效：
- 连续 `B级` → `b级` token → LIKE 匹配 XLSX 文章
- 带空格 `B 级` → 空白分隔片段合并 → `b级` token → LIKE 匹配 XLSX 文章

**FS4b Gate：PASS**

---

## 6. FS1-FS4 回归表

| 题号 | 搜索词 | 结果数 | rank 1 | 基线判定 | 本轮判定 |
|---|---|---|---|---|---|
| FS1 | 校园实验室安全管理手册 | 2 | **lab-safety-management-handbook** ✅ | PARTIAL | **PASS** |
| FS2 | 化学品分类存储 | 4 | **lab-safety-management-handbook** ✅ | FAIL | **PASS** |
| FS3 | 实验室化学品分级存储管理规范 | 4 | **lab-safety-management-handbook** ✅ | PARTIAL | **PASS** |
| FS4a | 安全员 | 2 | lab-safety-management-handbook / 设备管理员 | FAIL | **PASS** |
| FS4b | B级 | 2 | chemical-storage-grading ✅ | FAIL | **PASS** |
| FS4c | 精密仪器 | 3 | equipment-maintenance-schedule | PASS | **PASS** |

搜索精度较基线全面改善。FS1 从 PARTIAL→PASS（markdown 首位），FS2 从 FAIL→PASS（markdown 首位），FS3 从 PARTIAL→PASS（markdown 首位），FS4a 从 FAIL→PASS（markdown 条目出现），FS4b 从 FAIL→PASS（0→2 结果）。

---

## 7. 保护性搜索

| 搜索词 | 结果数 | 判定 |
|---|---|---|
| 精密仪器 | 3 | **PASS**（无回归，terminal unit + CSV 均命中） |
| 化学品分类存储 | 4 | **PASS**（无回归，markdown + XLSX 均命中） |

纯 CJK 和中文长词搜索行为保持不变。

---

## 8. Query 红线风险检查

| 检查项 | 结果 |
|---|---|
| 生产代码是否写入题号/业务词/文档名？ | **否**（仅基于 Unicode script、数字、空白/标点、长度） |
| 是否修改 AnswerGeneration/RRF/citation/prompt？ | **否** |
| 是否修改 fallback/builder/enricher/Materializer？ | **否** |
| 是否硬编码"B级"/"FS4b"等样例字符串？ | **否** |
| 修复规则是否对任意 Han+Latin/数字混合脚本通用？ | **是** |

---

## 9. 最终判定

### **PASS**

| 维度 | 判定 |
|---|---|
| Redline | **BLOCKER=0** |
| 定向测试 | **12/0/0/0 + 7/0/0/0** |
| 全量 mvn test | **1004/0/0/0, BUILD SUCCESS** |
| "B级" > 0 结果 | **PASS**（0→2） |
| "B 级" > 0 结果 | **PASS**（2 结果） |
| FS1-FS4 搜索回归 | **全部 PASS，无回归** |
| 保护性搜索 | **无回归** |
| Query 红线风险 | **无** |

---

## 10. 注意事项

- `docs/模型绑定配置参考.md` 为私有配置变更，提交时应排除
- `special_cases_report.md` 为 redline 输出，提交时应排除
- S2 title/anchor 问题与 FS2 ranking 问题不属于本轮 mixed script 修复范围
- 本轮修复未扩大至 AnswerGeneration/RRF/citation/fallback/prompt 层

---

## 11. 明确声明

- [x] agentD 验证阶段未新增修改生产代码
- [x] agentD 验证阶段未新增修改测试代码
- [x] 未修改 scripts/config/schema/题集/redline allowlist
- [x] 未提交 commit
- [x] 所有结论基于 runtime 搜索 API 结果 + 门禁输出
