# CODE_LIGHT contentProfile 规范化 hyphen 修复结果报告

时间：2026-06-07
执行人：agentA（代码执行 Agent）
依据：`code_light_content_profile_pre_d_gate_review_report.md` 第 7.1 节

---

## 1. 修改摘要

### 问题

`normalizeContentProfile()` 不支持 hyphen 变体。`"CODE-LIGHT"` / `"code-light"` 经 `toUpperCase` 后仍为 `"CODE-LIGHT"`，不等于常量 `"CODE_LIGHT"`，静默回退到 `DOCUMENT`。

### 修复

`CompileExecutionRequest.java` 第 164 行，在 `toUpperCase(Locale.ROOT)` 之后增加 `.replace('-', '_')`。

```java
// 修复前
String normalized = contentProfile.trim().toUpperCase(Locale.ROOT);

// 修复后
String normalized = contentProfile.trim().toUpperCase(Locale.ROOT).replace('-', '_');
```

## 2. normalize 前后行为对比

| 输入 | 修复前 | 修复后 |
|------|------|------|
| `"CODE_LIGHT"` | `CODE_LIGHT` ✅ | `CODE_LIGHT` ✅ |
| `"code_light"` | `CODE_LIGHT` ✅ | `CODE_LIGHT` ✅ |
| `"CODE-LIGHT"` | `CODE-LIGHT` → 回退 `DOCUMENT` ❌ | `CODE_LIGHT` ✅ |
| `"code-light"` | `CODE-LIGHT` → 回退 `DOCUMENT` ❌ | `CODE_LIGHT` ✅ |
| `null` | `DOCUMENT` ✅ | `DOCUMENT` ✅ |
| `""` | `DOCUMENT` ✅ | `DOCUMENT` ✅ |
| `"   "` | `DOCUMENT` ✅ | `DOCUMENT` ✅ |
| `"SOMETHING_ELSE"` | `DOCUMENT` ✅ | `DOCUMENT` ✅ |

## 3. 是否触碰禁止范围

| 禁止项 | 是否触碰 |
|--------|:---:|
| query/answer/rerank/fallback/citation/deepresearch 主链 | **否** |
| CompileGraph 路由 | **否** |
| BuildLightweightArticlesNode | **否** |
| schema.sql | **否** |
| prompt / scripts / 题集 | **否** |
| 清库 / 导入资料 | **否** |
| 提交 commit | **否** |
| 业务词 / 题集词 / 类名 / 方法名 / 文件名特判 | **否** |

修改仅涉及 `CompileExecutionRequest.java` 一行变更（`.replace('-', '_')`），未触及任何禁止范围。

## 4. redline 结果

| 指标 | 值 |
|------|-----|
| BLOCKER | **0** |
| REVIEW | 2139 |
| ALLOWLIST | 276 |
| 结论 | **PASS** |

## 5. 测试结果

### 5.1 mvn 编译

| 阶段 | 结果 |
|------|------|
| `mvn test-compile` | BUILD SUCCESS |

### 5.2 全量 mvn test

| 指标 | 值 |
|------|-----|
| Tests run | **1018** |
| Failures | **0** |
| Errors | **0** |
| Skipped | **0** |
| 耗时 | 07:09 min |
| 结论 | **BUILD SUCCESS** |

全部 1018 个测试通过，0 失败 0 错误。上一轮的 13 个环境 errors（vault/snapshot 等）本轮也全部清除。

## 6. 是否建议进入 agentD runtime gate

**是**。本轮修复为最小的常规化修正（一行 `.replace('-', '_')`），与 agentB 的 pre-D gate 结论完全一致：
- `contentProfile="CODE_LIGHT"` 精确值不受影响（原本就通过）
- `contentProfile="CODE-LIGHT"` / `"code-light"` 变体现在正确识别
- 不改变图路由、节点行为、article 构建逻辑
- 与 agentD fresh full compile gate 无冲突，可并行执行

agentD 执行时无需等待本轮修复完成后重跑——本轮修复仅影响 hyphen 变体输入的规范化行为，对 agentD 使用的精确值 `"CODE_LIGHT"` 是零影响变更。

## 7. 修改清单

| 文件 | 变更 |
|------|------|
| `CompileExecutionRequest.java` | `normalizeContentProfile()` 第 164 行增加 `.replace('-', '_')` |

## 8. 明确声明

- [x] 未修改 query/answer/rerank/fallback/citation/deepresearch 主链
- [x] 未修改 CompileGraph 路由
- [x] 未修改 BuildLightweightArticlesNode
- [x] 未修改 schema.sql
- [x] 未修改 prompt / scripts / 题集
- [x] 未引入业务词 / 题集词 / 类名 / 方法名 / 文件名特判
- [x] 未清库 / 导入资料
- [x] 未提交 commit
- [x] redline BLOCKER=0
- [x] 修复仅一行 `.replace('-', '_')`
