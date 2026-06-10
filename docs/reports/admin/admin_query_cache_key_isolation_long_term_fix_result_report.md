# Query Cache Key 隔离长期修复结果报告

## 问题背景

Task C（中文语气词归一化）将 canonicalizer 的输出直接写入 `normalizedQuestion`，这是一次"热修"——短期有效但长期有风险，因为 `normalizedQuestion` 会流入 rewrite、intent classification、retrieval、audit 等多个下游链路，标点/语气词被剥离后的文本对这些链路不利。

## 修复目标

将 cache key 归一化从 `normalizedQuestion` 中隔离出来，建立独立的 `canonicalCacheKey` 字段：

- `normalizedQuestion` 回归为 `question.trim()`
- `canonicalCacheKey` 承载 canonicalizer 的输出
- cache get/put 只用 `canonicalCacheKey`
- rewrite、intent、retrieval、audit 继续使用 `normalizedQuestion` 或原始 `question`

## 修改文件

| 文件 | 操作 | 说明 |
|---|---|---|
| `QueryGraphState.java` | 修改 | 新增 `canonicalCacheKey` 字段（+2 行） |
| `QueryGraphStateKeys.java` | 修改 | 新增 `CANONICAL_CACHE_KEY` 常量（+2 行） |
| `QueryGraphStateMapper.java` | 修改 | fromMap/toMap 增加 `canonicalCacheKey` 读写（+2 行） |
| `QueryGraphAnswerSupport.java` | 修改 | `normalizeQuestion()`: 恢复 `normalizedQuestion=question.trim()` + 独立设置 `canonicalCacheKey`；`checkCache()`: 使用 `canonicalCacheKey` |
| `QueryFinalizationGraphFragment.java` | 修改 | `persistResponse()`: cache put 使用 `canonicalCacheKey` |

## 数据流对比

### 修改前（热修）

```
question.trim() → canonicalizer → normalizedQuestion
                                           ↓
                              ┌────────────┼────────────┐
                              ↓            ↓            ↓
                          cache get    rewrite()    retrieval
                          cache put                 audit
```

`normalizedQuestion` 携带被剥离标点/语气词的文本，污染所有下游链路。

### 修改后（隔离）

```
question.trim()
    ├── normalizedQuestion ──→ rewrite() / intent / retrieval / audit
    └── canonicalizer
            └── canonicalCacheKey ──→ cache get / cache put
```

两条路径完全隔离。`normalizedQuestion` 保留原始语义（仅 trim），供 rewrite、intent、retrieval、audit 使用；`canonicalCacheKey` 仅用于 cache 键匹配。

## 下游链路影响分析

| 链路 | 使用字段 | 修改前 | 修改后 | 影响 |
|---|---|---|---|---|
| cache get (`checkCache`) | `canonicalCacheKey` | 用的是 canonicalized 文本（通过 normalizedQuestion） | 用的是 canonicalized 文本 | 行为不变 |
| cache put (`persistResponse`) | `canonicalCacheKey` | 用的是 canonicalized 文本 | 用的是 canonicalized 文本 | 行为不变 |
| query rewrite (`rewriteQuery`) | `normalizedQuestion` | 收到 canonicalized 文本（标点被剥离） | 收到原始问题 trim | **改善**：LLM rewrite 收到完整语义 |
| intent classify (`classifyIntent`) | `effectiveRetrievalQuestion` | 收到 canonicalized 文本 | 收到原始问题 trim | **改善**：意图分类更准确 |
| retrieval dispatch | `effectiveRetrievalQuestion` | 收到 canonicalized 文本 | 收到原始问题 trim | **改善**：检索召回更准确 |
| rewrite 检测 (`isRewriteApplied`) | `normalizedQuestion` | 比较 canonicalized 文本 | 比较原始问题 trim | **改善**：检测更准确 |
| retrieval audit (`persist`) | `normalizedQuestion` | 记录 canonicalized 文本 | 记录原始问题 trim | **改善**：审计更准确 |
| lifecycle logging | `normalizedQuestion` | 记录 canonicalized 文本 | 记录原始问题 trim | **改善**：日志更准确 |

## 测试结果

### redline 扫描

```bash
bash scripts/scan-redline.sh special_cases_report.md
# exit code: 0，无违规
```

### 编译

```bash
mvn -q -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -DskipTests compile
# exit code: 0，编译通过
```

### 单元测试

```bash
mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository \
    -Dtest='QueryCacheKeyCanonicalizerTests' test
# Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
```

### 关联回归测试

```bash
mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository \
    -Dtest='QueryGraph*' test
# Tests run: 17, Failures: 0, Errors: 0, Skipped: 0
```

## 等价性保证

- **Cache 键等价性不变**：同一问题的不同书写形式（仅标点/语气词不同）仍然归一化为相同的 `canonicalCacheKey` → cache 命中行为不变
- **下游语义完整性恢复**：rewrite、intent、retrieval、audit 现在收到完整的原始问题文本（含标点和语气词），语义信息更丰富

## 是否触碰禁止范围

| 禁止项 | 状态 |
|---|---|
| 修改 `scripts/scan-redline.sh` | 未触碰 |
| 修改 `AGENTS.md` | 未触碰 |
| 修改 redline allowlist | 未触碰 |
| 修改 fresh eval 题集内容 | 未触碰 |
| 修改检索/rerank/prompt/AnswerGeneration 主链 | 未触碰 |
| 清库/重建 schema/删除 Redis key | 未触碰 |

## 是否需要用户操作

### 重启 Docker 应用

**需要。** 修改了 `QueryGraphState`、`QueryGraphStateMapper`、`QueryGraphAnswerSupport`、`QueryFinalizationGraphFragment`，需要部署新 jar 后重启：

```bash
docker cp target/lattice-java-1.0-SNAPSHOT.jar lattice_app:/app/app.jar
docker restart lattice_app
```

### 清理旧 Query Cache

**建议但不强制。** 旧 cache key 格式为 `llm:query:cache:{canonicalizedText}`（canonicalizer 输出），新格式完全相同——因为 `checkCache` 和 `persistResponse` 仍然使用 canonicalizer 输出作为 key。**不需要清理旧缓存，兼容。**

## 未提交 commit 声明

本次修改未做 git commit，所有变更保留在工作区中，等待用户审查后决定是否提交。
