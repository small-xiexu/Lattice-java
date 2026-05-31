# Terminal Unit Phase 1G Candidate Precision Clean Runtime 验证报告

验证时间：2026-05-31
验证人：agentD

## 1. 验证结论

**FAIL** — best-score 修复逻辑正确但选错了信号源。`name` (score=105) > `version` (score=54)，best-score 按设计选中了 name。FG2 持续 PASS。

## 2. 根因

| terminalKey | valueText | score | fusedRank | 为什么被选中 |
|---|---|---|---|---|
| name | 校园实验室设备预约系统 | **105.0** | 5 | FTS score 最高（"预约系统" 直接匹配 valueText） |
| version | v2.3.1 | 54.0 | 1 | FTS score 低（alias "版本号" 间接匹配） |

best-score 修复按 `getScore()` 选择，而 `getScore()` 返回 FTS/LIKE 原始分数。name 的 valueText "校园实验室设备预约系统" 与 query "预约系统" 直接 LIKE 匹配（+3.0），score 远高于 version（alias "版本号" 在 ftsText 中匹配仅 +2.0）。

**best-score 修复本身逻辑正确——问题在 score 信号不适合区分 "字段意图精确度"。** FTS score 反映的是词面匹配强度，而终端单元选择需要的是字段意图对齐度（version 比 name 更贴 "版本号是什么" 的问题意图）。

## 3. 环境

| 项目 | 值 |
|---|---|
| Schema | 完整重建 |
| compile jobId | `3b8c2131-b773-4915-8547-b579ef1bf077` |
| 状态 | SUCCEEDED，acceptedCount=3, needsHumanReview=2 |
| approve 后 | 5/5 articles 入库 |

## 4. 门禁

| 检查项 | 结果 |
|---|---|
| git diff --check | 通过 |
| redline | BLOCKER=0 |
| ConclusionBuilderTests | 7/0/0 |
| EvidenceSelectorTests | 11/0/0 |
| 定向组合 | 18/0/0 |

## 5. Runtime 结果

| 题目 | 结果 | 答案 |
|---|---|---|
| FQ6 | **FAIL** | `borrowing_system.name = 校园实验室设备预约系统` (name score=105 > version score=54) |
| FG2 | **PASS** | `borrowing_system.max_concurrent_requests = 50` |

## 6. FQ6 失败归因

**类别**：conclusion builder 未消费正确的 terminal unit — 信号源不匹配。

best-score 修复使用 `getScore()` (FTS 原始分) 做候选选择。但终端单元选择需要的不是 "哪个终端单元词面匹配更强"，而是 "哪个终端单元的字段语义更贴问题意图"。FTS score 无法区分这两者。

## 7. 下一轮唯一建议

**改用 `fusedRank` 而非 `getScore()` 做 terminal unit 候选选择。**

version 终端单元在 fused_rank=1（RRF 融合后的全局排名），name 在 fused_rank=5。RRF 融合排名比原始 FTS 分数更能反映终端单元的综合相关性。

修改范围：`AnswerFallbackConclusionBuilder.buildTerminalUnitExactConclusionLines()` — 将 `getScore()` 比较改为按 fused_rank（hitRank 或 `fallbackHits.indexOf` + 排序后的位置）选择。

改动量：~5 行。不改 selector、Reranker、metadata sync、channel parse。

## 合规声明

- 本轮未修改代码、测试、配置、脚本、题集
- 未读取 hidden eval
- 未 stage、未 commit、未 push
- 报告未输出 API key/token/password
