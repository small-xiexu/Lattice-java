# S2 标题边界 Chunk 切分 — 修复结果报告

修复时间：2026-06-05
执行人：agentA（代码执行 Agent）
前置分析：`s2_subheading_anchor_chunking_analysis_report.md`（agentB）
前置 gate：`s2_title_anchor_runtime_gate_report.md`（agentD）

---

## 1. 根因复述

`SemanticChunker` 将 Markdown 标题视为语义单元边界（unit boundary），但不视为 chunk 边界。当一节内容超过 maxChars 时，chunk 在正文中间断开——标题留在前一个 chunk，后续正文溢入下一个 chunk。导致 `extractSectionAnchor()` 在下一个 chunk 中提取到的是后续节的标题（如"设计取舍与常见风险"），而非当前内容所属节的标题（"下一步计划"）。

本修复在 chunk 切分时迫使 ATX 标题（`#` 至 `######`）成为新 chunk 的首行，使 sectionAnchor 能正确定位到对应标题。

---

## 2. 修改文件

| 文件 | 修改类型 |
|------|----------|
| `src/main/java/com/xbk/lattice/infra/chunking/SemanticChunker.java` | 生产代码 |
| `src/test/java/com/xbk/lattice/infra/chunking/SemanticChunkerTests.java` | 新增测试 |
| `src/test/java/com/xbk/lattice/infra/persistence/ArticleChunkJdbcRepositoryTests.java` | 断言更新 |
| `src/test/java/com/xbk/lattice/infra/persistence/SourceFileChunkJdbcRepositoryTests.java` | 断言更新 |
| `src/test/java/com/xbk/lattice/api/admin/AdminChunkRebuildControllerTests.java` | 断言更新 |

---

## 3. 最小 diff 摘要

### 3.1 `SemanticChunker.java` — 生产代码变更

**新增常量**：
```java
private static final Pattern ATX_HEADING_PATTERN = Pattern.compile("^#{1,6}\\s.*");
```

**`chunk()` 方法** — 在单元累积循环中，maxChars 检查之前插入 ATX 标题边界检查：
```java
if (builder.length() > 0 && isAtxHeading(unit.getText())) {
    break;
}
```

**新增方法 `isAtxHeading`**：
- 只取文本单元的首行进行匹配（因为 parseUnits 可能将 heading 与后续内容合并到同一 TextUnit）
- 匹配规则：首行 trim 后匹配 `^#{1,6}\s.*`
- 不识别水平线（`---`）、列表项（`- `、`* `）、有序列表

### 3.2 测试变更

**新增 5 个测试**（SemanticChunkerTests，从 2 个增加到 7 个）：
1. `shouldStartNewChunkAtAtxHeadingWhenBuilderHasContent` — ATX 标题强制新 chunk
2. `shouldTreatAnyAtxHeadingAsBoundaryRegardlessOfText` — 依赖通用格式，不依赖具体标题文本
3. `shouldNotForceChunkBoundaryOnNonAtxMarkers` — 列表项/水平线不触发 chunk 边界
4. `shouldStillRespectMaxCharsWithAtxHeadingBoundary` — maxChars 仍然生效
5. `shouldNotAffectPlainTextChunking` — 纯文本无标题时行为不变

**更新 3 个已有测试断言**：chunk 数从 1 → 2（因为标题边界现在产生额外 chunk），其余断言语义不变。

---

## 4. 为什么不是 S2 / "下一步计划" case 特判

| 检查项 | 判定 |
|--------|:---:|
| 是否检查文件名？ | 否 |
| 是否检查 article title？ | 否 |
| 是否检查具体标题文本（如"下一步计划"）？ | 否 |
| 是否检查 eval 题号？ | 否 |
| 规则是否对所有 `#` 标题通用？ | **是** |
| 是否对纯文本/PDF/CSV 产生差异？ | **否**（仅处理 `#` 开头的行） |

`isAtxHeading` 使用纯文本结构规则 `^#{1,6}\s.*`，对所有 Markdown ATX 标题生效，不限于特定标题文本、文件名或题号。

---

## 5. 测试结果

### 定向测试

```
Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
```

### 全量 mvn test

```
Tests run: 1009, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

注：`ChatClientRegistryTests` 的预存 404 错误不在本轮修改范围内，且本次重跑未复现。

---

## 6. redline 结果

`BLOCKER=0`

---

## 7. 需要 agentD 重新清库/编译验证的说明

本修复是编译期 chunk 切分变更。修改后需要：

1. 清库（`bash scripts/reset-lattice-schema.sh`）
2. 重新导入 Public Eval 1 资料
3. 重新 compile
4. 验证 S2 搜索"下一步计划"的 section anchor 是否变为"下一步计划"（而非"设计取舍与常见风险"）
5. 回归 S1-S4、Q1-Q12、Q6 保护场景

---

## 8. 潜在副作用

| 维度 | 影响 | 缓解 |
|------|------|------|
| Chunk 数量增加 | Markdown 文档中每个 `##`/`###` 标题产生额外 chunk | 15% 重叠保证相邻 chunk 提供上下文 |
| 需重建向量索引 | chunk 文本变化 | compile 时自动重建 `article_chunk_vector_index` |
| 长 Markdown 文档 chunk 更细 | 单 chunk 上下文减少 | 重叠机制缓解；小 chunk 对搜索精度有利 |
| 纯文本文档 | **无影响** | 没有 `#` 标题行 |
| 已有 eval | 需重新编译资料验证 | 见上节 |

---

## 9. 下一步验证建议（交给 agentD）

1. 清库 + 导入 Public Eval 1 资料 + compile
2. S2 搜索"下一步计划" → section anchor 应为"下一步计划"
3. S1-S4 全部回归
4. Q1-Q12 全部回归
5. Q6 保护验证（tcpSocket.port=8080）
6. 如可能，验证 Public Eval 2 FS2 markdown chunk 标题精度

---

## 10. 明确声明

- [x] 只修改了 `SemanticChunker.java`（生产）和 4 个测试文件
- [x] 未修改 `ChunkHitIdentitySupport.java` / `RrfFusionService` / `ArticleChunkFtsSearchService`
- [x] 未修改 `AnswerGenerationService` / `AnswerFallbackConclusionBuilder`
- [x] 未修改 `schema.sql` / `Mapper XML`
- [x] 未修改 `scripts` / `prompt` / `config` / `题集`
- [x] 未写入 S2 / "下一步计划" / 具体文件名 / 具体标题文本 等任何样例字符串
- [x] `isAtxHeading` 使用通用正则 `^#{1,6}\\s.*`
- [x] 未提交 commit
- [x] redline `BLOCKER=0`
- [x] mvn test `1009/0/0/0, BUILD SUCCESS`

---

## 11. 补充修订：heading boundary 不应用 overlap rewind

**修订时间**：2026-06-05

### 为什么需要修订

上一轮实现中，`chunk()` 的内层循环在 ATX heading 触发 `break` 后，外层统一执行：

```java
unitIndex = rewindForOverlap(units, startIndex, unitIndex, overlapChars);
```

如果当前 chunk 中有多个 TextUnit 且 `overlapRatio > 0`，`rewindForOverlap()` 可能把下一轮起点回退到 heading 前的 TextUnit。这会导致 heading 被重复纳入前一个 chunk 的尾部（作为 overlap），而下一个 chunk 的首行不再是 heading，破坏了"下一 chunk 以 heading 开始"的设计目标。

### 修改摘要

在 `chunk()` 中新增 `headingBreak` 布尔标志。当 ATX heading 边界触发 break 时设为 true，封存 chunk 后跳过 `rewindForOverlap()`，保证 `unitIndex` 直接指向 heading 单元，下一 chunk 严格以 heading 开始。

### 新增测试

`shouldNotApplyOverlapWhenBreakingAtAtxHeadingBoundary`（补强版）：

- **原测试覆盖不足**：heading 前只有一个 TextUnit（三行未空行断开的文本在 parseUnits 中合并为一个单元）。单前置单元时，`rewindForOverlap` 返回的 `rewindIndex` 经 `Math.max(startIndex+1, rewindIndex)` 归一化后可能恰好等于 heading 索引，无法暴露风险。
- **补强后**：构造两个独立段落（第一段 + 空行 + 第二段长段落 + 空行），第二段长度 ~105 字符，超过 `overlapChars=100`（maxChars=200×0.5）。旧逻辑执行 rewind 后会回退到第二段，下一 chunk 以 `"this is a longer..."` 开头而非 `"## Target Section"`。
- 断言：`chunks.get(1).startsWith("## Target Section")` + `chunks.get(1).doesNotStartWith("this is a longer")`，在移除 `headingBreak` 保护时必然失败。

### 最新定向测试结果（以本修订为准）

```
Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
```

### 最新全量 mvn test 结果（以本修订为准）

```
Tests run: 1010, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### redline 结果

`BLOCKER=0`

### 仍需 agentD runtime gate 验证

本修订属于编译期 chunk 切分逻辑变更，与上一轮相同，需要清库→重建→runtime 验证。验证目标与第 7、9 节一致。
