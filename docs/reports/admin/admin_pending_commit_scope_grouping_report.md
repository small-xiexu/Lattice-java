# 当前工作区提交范围分组报告

分析时间：2026-06-10
执行人：agentC
HEAD：`27400a5`

## 一、当前工作区全景（12 个文件 + 9 个报告）

### 已修改跟踪文件（6 个）

| 文件 | 行变更 | 归属线 |
|---|---|---|
| `QueryCacheKeyCanonicalizer.java` | 新增 | **A: Cache Key** |
| `QueryCacheKeyCanonicalizerTests.java` | 新增 | **A: Cache Key** |
| `QueryFinalizationGraphFragment.java` | +8/-2 | **A: Cache Key** |
| `QueryGraphAnswerSupport.java` | +12/-1 | **A: Cache Key** |
| `QueryGraphState.java` | +2 | **A: Cache Key** |
| `QueryGraphStateKeys.java` | +2 | **A: Cache Key** |
| `QueryGraphStateMapper.java` | +2 | **A: Cache Key** |
| `AnswerGenerationPromptEvidenceSupport.java` | +87/-1 | **B: FQ9/Evidence** |

### 未跟踪报告（9 个）

| 文件 | 归属线 |
|---|---|
| `admin_query_cache_chinese_particle_normalization_analysis_report.md` | A |
| `admin_query_cache_chinese_particle_normalization_fix_result_report.md` | A |
| `admin_query_cache_key_isolation_long_term_fix_result_report.md` | A |
| `admin_query_cache_key_isolation_guard_fix_result_report.md` | A |
| `admin_query_cache_key_isolation_guard_docker_runtime_gate_report.md` | A |
| `admin_result_feedback_effectiveness_docker_verification_report.md` | A（缓存相关） |
| `fresh-eval-2026-09_fq9_csv_aggregation_failure_analysis_report.md` | B |
| `fresh-eval-2026-09_fq9_docker_runtime_root_cause_report.md` | B |
| `fresh-eval-2026-09_fq9_prompt_evidence_focus_fix_result_report.md` | B |

### 必须排除

| 文件 | 原因 |
|---|---|
| `special_cases_report.md` | redline 输出 |

## 二、两条修复线的独立性判断

| 维度 | 线 A: Cache Key | 线 B: AnswerGeneration |
|---|---|---|
| 修改文件 | 7 生产 + 1 测试 | 1 生产 |
| 影响范围 | Query cache key 生成（中文语气词归一化 + 多参数隔离） | AnswerGeneration prompt evidence focus |
| 是否依赖对方 | **否** — 线 A 修改 `query/graph/` 包，线 B 修改 `query/service/` 包 | **否** |
| 是否有共享文件 | **否** — 无交集 | **否** |
| 是否可以独立提交 | **是** | **是** |

## 三、推荐提交分组

### Commit 1：Query Cache Key 中文语气词归一化 + 隔离

**生产代码（7 个）**：

| 文件 | 说明 |
|---|---|
| `QueryCacheKeyCanonicalizer.java` | 新增：中文语气词（吗/呢/吧/啊）归一化 + 多参数隔离 |
| `QueryFinalizationGraphFragment.java` | 集成 Canonicalizer 到 cache key 生成 |
| `QueryGraphAnswerSupport.java` | 集成 Canonicalizer |
| `QueryGraphState.java` | 新增 cache key 状态字段 |
| `QueryGraphStateKeys.java` | 新增 state key |
| `QueryGraphStateMapper.java` | 状态映射 |

**测试代码（1 个）**：

| 文件 | 说明 |
|---|---|
| `QueryCacheKeyCanonicalizerTests.java` | Canonicalizer 单元测试 |

**报告（可随提交，也可后续单独提交）**：

| 文件 | 建议 |
|---|---|
| `admin_query_cache_chinese_particle_normalization_analysis_report.md` | 随代码提交 |
| `admin_query_cache_chinese_particle_normalization_fix_result_report.md` | 随代码提交 |
| `admin_query_cache_key_isolation_long_term_fix_result_report.md` | 随代码提交 |
| `admin_query_cache_key_isolation_guard_fix_result_report.md` | 随代码提交 |
| `admin_query_cache_key_isolation_guard_docker_runtime_gate_report.md` | 随代码提交 |

**建议 commit message**：
```
fix(query): normalize Chinese particles in cache key and isolate multi-param keys
```

### Commit 2：FQ9 / AnswerGeneration Prompt Evidence Focus（暂不提交）

| 文件 | 状态 |
|---|---|
| `AnswerGenerationPromptEvidenceSupport.java` | 当前工作区，**不建议与 Commit 1 混交** |
| `fresh-eval-2026-09_fq9_*` 报告（3 个） | 待线 B 门禁通过后再提交 |

**理由**：线 B 修改量较大（+87 行），且可能影响 AnswerGeneration 行为。需要独立的 runtime gate 验证，不应与 Cache Key 修复混在同一个 commit。

### Commit 3：剩余未跟踪报告（后续处理）

| 文件 | 建议 |
|---|---|
| `admin_result_feedback_effectiveness_docker_verification_report.md` | 与 admin result feedback 修复关联，确认提交状态后单独处理 |

## 四、明确不应进入 Cache Key Commit 的文件

| 文件 | 原因 |
|---|---|
| `AnswerGenerationPromptEvidenceSupport.java` | 归属线 B，不同根因 |
| `fresh-eval-2026-09_fq9_*`（3 个） | 归属线 B |
| `special_cases_report.md` | redline 输出，永远排除 |
| `admin_result_feedback_effectiveness_docker_verification_report.md` | 归属 admin result feedback，不同主题 |

## 五、提交建议

1. **立即**：提交 Commit 1（7 生产 + 1 测试 + 5 报告）
2. **后续**：线 B 通过独立 runtime gate 后单独提交
3. **后续**：`admin_result_feedback_effectiveness_docker_verification_report.md` 随对应修复提交

## 六、明确声明

- [x] 本轮未修改任何代码
- [x] 仅输出分组建议
- [x] 未读取 hidden eval
- [x] 未提交 commit
