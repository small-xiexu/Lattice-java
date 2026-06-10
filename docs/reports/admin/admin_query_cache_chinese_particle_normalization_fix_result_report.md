# Query Cache 中文语气词归一化修复结果报告

## 根因

`QueryGraphAnswerSupport.normalizeQuestion()` 只做 `trim()`，不做中文语气词/标点等价归一。导致"发布检查表里还有哪些检查项没有完成呢？分别是哪个责任人呢？"和"发布检查表里还有哪些检查项没有完成？分别是哪个责任人？"产生不同的 `normalizedQuestion` → 不同的 Redis 缓存 key → 命中不同历史答案 → 返回形式不一致。

## 改了哪些文件

| 文件 | 操作 | 说明 |
|---|---|---|
| `src/main/java/com/xbk/lattice/query/graph/QueryCacheKeyCanonicalizer.java` | 新增 | 通用 cache key 归一化工具类 |
| `src/main/java/com/xbk/lattice/query/graph/QueryGraphAnswerSupport.java` | 修改 | `normalizeQuestion()` 接入 canonicalizer（+3 行，-1 行） |
| `src/test/java/com/xbk/lattice/query/graph/QueryCacheKeyCanonicalizerTests.java` | 新增 | 10 个单元测试 |
| `docs/reports/admin/admin_query_cache_chinese_particle_normalization_analysis_report.md` | 新增 | 失败归因报告 |
| `docs/reports/admin/admin_query_cache_chinese_particle_normalization_fix_result_report.md` | 新增 | 本报告 |

## QueryCacheKeyCanonicalizer 归一化规则

```
输入 → trim() → 去无实义标点 → 去句尾语气词 → 合并空格 → trim()
```

1. **无实义标点**（替换为空格保留词边界）：`？?！!。；;，,：:…`
2. **句尾语气词**（仅当后随空白或行尾时删除，避免误删复合词中的同形字）：`呢吧啊呀嘛哦哟咯啦`
3. **故意不归一**：
   - `吗`：yes/no 问句标记，有实义，去掉会改变问句类型
   - 否定词、数值、实体名、字段名、英文 token：保留原样

## 为什么这是通用修复，不是 case 特判

| 检查点 | 说明 |
|---|---|
| 不硬编码文件名 | 不引用 `release-checklist`、`defect-list` 等 |
| 不硬编码标识符 | 不引用 `CHK`、`DEF`、`P0`、`P1` 等 |
| 不硬编码业务词 | 不引用"发布检查表""缺陷清单""检查项" |
| 仅基于语言学特征 | 标点类型和句尾语气词是中文通用特征 |
| 仅用于 cache key | 不影响检索、prompt、审计中的原始 question |
| 安全匹配规则 | 语气词后必须跟空白/行尾（`(?=\s|$)`），避免误匹配如"呢绒" |

## 等价/非等价保护

**等价位归并为同一 cache key**：
- 仅语气词不同：`...完成呢？` ↔ `...完成？`
- 仅标点不同：`...是什么？` ↔ `...是什么?` ↔ `...是什么。`
- 仅空白不同：`  数据库 配置  ` ↔ `数据库配置`

**非等价不会被错误合并**：
- 不同编号：`DEF-001` vs `DEF-002`
- 不同字段名：`超时时间` vs `重试次数`
- 不同状态词：`已完成` vs `未完成`
- yes/no 问句标记保留：`生效了吗` vs `生效了`

## 测试结果

### redline 扫描

```bash
bash scripts/scan-redline.sh special_cases_report.md
# exit code: 0，无违规
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

### 测试覆盖矩阵

| 测试 | 覆盖场景 |
|---|---|
| `shouldMergeEquivalentQuestionsWithParticleDifference` | 核心用例：仅"呢"和标点不同 → 相同 key |
| `shouldMergeWithAndWithoutPeriod` | 句号等价 |
| `shouldMergeFullWidthAndHalfWidthQuestionMarks` | 全角/半角问号等价 |
| `shouldNotMergeYesNoQuestionMarker` | "吗"不可归一 |
| `shouldNotMergeDifferentIdentifiers` | 不同编号不合并 |
| `shouldNotMergeDifferentFieldNames` | 不同字段名不合并 |
| `shouldNotMergeDifferentStatusWords` | 不同状态词不合并 |
| `shouldHandleNullAndEmpty` | null/blank 安全处理 |
| `shouldMergeOnlyWhitespaceDifferences` | 仅空白不同 → 合并 |
| `shouldMergeMultipleParticlesAndMixedPunctuation` | 多语气词+多标点混合归一 |

## 是否触碰禁止范围

| 禁止项 | 状态 |
|---|---|
| 修改 `scripts/scan-redline.sh` | 未触碰 |
| 修改 `AGENTS.md` | 未触碰 |
| 修改 redline allowlist | 未触碰 |
| 修改 fresh eval 题集内容 | 未触碰 |
| 修改特定文件名/业务词/题号相关硬编码 | 未触碰 |
| 修改检索/rerank/prompt/AnswerGeneration 主链 | 未触碰 |
| 清库/重建 schema/删除 Redis key | 未触碰 |

## 是否需要用户操作

### 重启 Docker 应用

**需要。** 修改了 `QueryGraphAnswerSupport`，需要部署新 jar 后重启才生效：

```bash
docker cp target/lattice-java-1.0-SNAPSHOT.jar lattice_app:/app/app.jar
docker restart lattice_app
```

### 清理旧 Query Cache

**建议但不强制。** 修复前，以下两句在 Redis 中有不同的缓存条目：

- `llm:query:cache:发布检查表里还有哪些检查项没有完成呢？分别是哪个责任人呢？`
- `llm:query:cache:发布检查表里还有哪些检查项没有完成？分别是哪个责任人？`

修复后，两个问题归一为相同 key `llm:query:cache:发布检查表里还有哪些检查项没有完成 分别是哪个责任人`。旧 key 上的缓存不会自动迁移。建议：

1. 应用重启后，新的查询会写入新 key
2. 如果希望立即清除旧 key，可在 Docker 内执行：
   ```bash
   docker exec lattice_app redis-cli KEYS "llm:query:cache:*" | xargs -r docker exec -i lattice_app redis-cli DEL
   ```
   或等待 1 小时 TTL 自然过期。

### 清理旧 Pending Query

不需要。Pending query 不依赖 cache key 归一化。

## 未提交 commit 声明

本次修改未做 git commit，所有变更保留在工作区中，等待用户审查后决定是否提交。
