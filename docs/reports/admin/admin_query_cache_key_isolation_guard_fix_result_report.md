# Query Cache Key 隔离防御性兜底修复结果报告

## 背景

上轮（`admin_query_cache_key_isolation_long_term_fix_result_report.md`）已完成 `canonicalCacheKey` 与 `normalizedQuestion` 的字段级分离。但存在两个缺口：

1. **防御性缺口**：`checkCache` / `persistResponse` 直接使用 `state.getCanonicalCacheKey()`，正常图顺序下没问题，但异常路径或旧状态缺字段时可能读写 `llm:query:cache:null` 等脏 key
2. **测试缺口**：没有锁住"normalizedQuestion 保持原始 trim 文本、canonicalCacheKey 仅用于 cache get/put"这个长期合同

## 本轮修了什么

### 1. `QueryCacheKeyCanonicalizer.resolveSafe()` — 安全缓存键解析

新增静态工具方法：

```java
public static String resolveSafe(String canonicalCacheKey, String fallbackText)
```

优先级：
1. `canonicalCacheKey` 非空 → 直接使用
2. `canonicalCacheKey` 为空 → 用 `fallbackText` 现场 `canonicalize()`
3. 两者均为空 → 返回 `""`

### 2. `checkCache()` — 防御性 cache get

```java
String cacheKey = QueryCacheKeyCanonicalizer.resolveSafe(
    state.getCanonicalCacheKey(), /* fallback: normalizedQuestion ?? question */);
if (cacheKey.isEmpty()) {
    state.setCacheHit(false);  // 安全 miss，不访问 cache store
    return ...;
}
```

### 3. `persistResponse()` — 防御性 cache put

```java
String cacheKey = QueryCacheKeyCanonicalizer.resolveSafe(
    state.getCanonicalCacheKey(), /* fallback: normalizedQuestion ?? question */);
if (!cacheKey.isEmpty()) {
    queryCacheStore.put(cacheKey, withoutQueryId(queryResponse));
}
```

### 4. 补测试 — 6 个新测试

| 测试 | 覆盖 |
|---|---|
| `shouldPreferCanonicalCacheKeyOverFallback` | resolveSafe 优先使用已设置的 key |
| `shouldFallbackToCanonicalizedFallbackText` | canonicalCacheKey 为 null 时现场归一化 fallback |
| `shouldFallbackWhenCanonicalCacheKeyIsBlank` | canonicalCacheKey 为空白字符串时也能回退 |
| `shouldReturnEmptyWhenAllSourcesAreBlank` | 所有来源为空 → 返回 "" |
| `shouldKeepNormalizedQuestionSeparateFromCanonicalCacheKey` | **核心合同**：canonicalCacheKey 剥离语气词/标点，normalizedQuestion 保留原始语义 |
| `shouldNotProduceCacheKeyForNullInput` | null 输入不产出可用的 cache key |

## 为什么这是防御性兜底，不是改变 query 行为

| 场景 | 修改前 | 修改后 | 行为变化 |
|---|---|---|---|
| 正常图顺序（`canonicalCacheKey` 已设置） | 直接使用 `canonicalCacheKey` | 优先使用 `canonicalCacheKey` | **无变化** |
| 异常路径（旧状态无 `canonicalCacheKey`） | 可能传递 `null` 到 `queryCacheStore.get/put` | 回退到 `normalizedQuestion` 现场归一化 | **从不安全变为安全降级** |
| 极端情况（所有来源为空） | 可能读写 `"null"` 或空字符串 | 跳过 cache get/put，不影响后续流程 | **从不安全变为安全跳过** |

核心原则：cache miss 是安全的（走正常 retrieval + LLM 路径），cache 脏 key 是不安全的（污染 Redis 或抛异常）。防御逻辑宁可 false negative（cache miss）也不产生脏数据。

## cache key 缺失时的行为

- `checkCache`：cache key 为空 → `cacheHit=false` → 继续走 `dispatch_retrieval` → LLM 答案生成 → 正常返回
- `persistResponse`：cache key 为空 → 跳过 `queryCacheStore.put()` → 正常保存 `finalResponseRef` → 不影响最终响应

## normalizedQuestion / canonicalCacheKey 最终分工

| 字段 | 内容 | 用途 |
|---|---|---|
| `question` | 用户原始输入 | 审计原始记录 |
| `normalizedQuestion` | `question.trim()` | rewrite、intent、retrieval、audit、logging |
| `canonicalCacheKey` | `canonicalize(question)` | 仅 cache get/put |

合同断言（`shouldKeepNormalizedQuestionSeparateFromCanonicalCacheKey`）：
- 两个仅语气词/标点不同的问题 → **相同 canonicalCacheKey**（等价归并）
- 两个 normalizedQuestion → **保留各自原始标点/语气词**（不被 canonicalizer 污染）
- normalizedQuestion ≠ canonicalCacheKey（路径分离）

## 修改文件清单

| 文件 | 操作 | 说明 |
|---|---|---|
| `QueryCacheKeyCanonicalizer.java` | 修改 | 新增 `resolveSafe()` 静态方法（+20 行） |
| `QueryGraphAnswerSupport.java` | 修改 | `checkCache()` 接入 `resolveSafe`，空 key 安全降级（+5 行，-1 行） |
| `QueryFinalizationGraphFragment.java` | 修改 | `persistResponse()` 接入 `resolveSafe`，空 key 跳过 cache put（+5 行，-1 行） |
| `QueryCacheKeyCanonicalizerTests.java` | 修改 | 新增 6 个测试（+62 行） |

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
# Tests run: 16, Failures: 0, Errors: 0, Skipped: 0
```

新增 6 个（10 → 16），全部通过。

### 关联回归测试

```bash
mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository \
    -Dtest='QueryGraph*' test
# Tests run: 17, Failures: 0, Errors: 0, Skipped: 0
```

零回归。

## 是否触碰禁止范围

| 禁止项 | 状态 |
|---|---|
| 修改 `AnswerGenerationPromptEvidenceSupport.java` | 未触碰 |
| 修改 retrieval/rerank/citation/AnswerGeneration 主链 | 未触碰 |
| 修改 fresh eval 题集 | 未触碰 |
| 修改 `scripts/scan-redline.sh` | 未触碰 |
| 修改 redline allowlist | 未触碰 |
| 修改 `AGENTS.md` / `CLAUDE.md` | 未触碰 |
| 清库/重建 schema/删除 Redis key | 未触碰 |
| 硬编码业务词/文件名/题号/样例答案 | 未触碰 |

## 是否需要 Docker 重新打包重启

**需要。** 修改了 `QueryCacheKeyCanonicalizer`、`QueryGraphAnswerSupport`、`QueryFinalizationGraphFragment`，需要部署新 jar 后重启：

```bash
docker cp target/lattice-java-1.0-SNAPSHOT.jar lattice_app:/app/app.jar
docker restart lattice_app
```

## 是否需要清理 Redis query cache

**不需要。** cache key 格式完全相同（canonicalizer 输出），不存在格式不兼容问题。`resolveSafe` 的回退逻辑也产出相同的 canonicalized 格式。

## 未提交 commit 声明

本次修改未做 git commit，所有变更保留在工作区中，等待用户审查后决定是否提交。
