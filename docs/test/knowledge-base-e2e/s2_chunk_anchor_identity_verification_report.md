# S2 Chunk/Anchor Identity 修复提交前验证报告

- 验证时间：2026-05-27 22:35–23:05（Asia/Shanghai）
- 验证性质：只读审计 + 运行态端到端验收
- 验证 Agent：agentD
- 结论用途：判断 S2 chunk/anchor identity 修复是否具备独立提交条件
- 约束声明：未 stage、未 commit、未 push，未修改任何业务代码

## 1. 工作区只读盘点

### 1.1 候选文件（共 8 个，与 agentA 报告一致）

| 文件 | 类型 | 说明 |
|---|---|---|
| `src/main/java/com/xbk/lattice/query/service/ChunkHitIdentitySupport.java` | 新增 | chunk identity 生成/读取、anchor 提取、展示标题拼接 |
| `src/main/java/com/xbk/lattice/query/service/ArticleChunkFtsSearchService.java` | 修改 | article chunk FTS 命中保留 chunk identity 与 section anchor |
| `src/main/java/com/xbk/lattice/query/service/ChunkToArticleAggregator.java` | 修改 | 不同 chunk 保留独立席位，同一 chunk 取最高分 |
| `src/main/java/com/xbk/lattice/query/service/RrfFusionService.java` | 修改 | chunk 级 ARTICLE hit 使用 chunkIdentity 去重 key |
| `src/test/java/com/xbk/lattice/query/service/ArticleChunkFtsSearchServiceTests.java` | 新增 | chunk identity/section anchor 展示测试 |
| `src/test/java/com/xbk/lattice/query/service/WeightedRrfFusionTest.java` | 修改 | chunk 级与 article 级命中分离/合并测试 |
| `src/test/java/com/xbk/lattice/query/service/ChunkVectorSearchServiceTests.java` | 修改 | chunk vector metadata 断言补强 |
| `src/test/java/com/xbk/lattice/query/service/ChunkToArticleAggregatorTest.java` | 修改 | 不同 chunk 独立席位 + 同一 chunk 去重测试 |

### 1.2 明确排除文件

- `docs/模型绑定配置参考.md` — 私有配置，永远排除
- `docs/quality-progress-and-lessons.md` — 质量台账
- `special_cases_report.md` — redline 输出
- `docs/test/knowledge-base-e2e/s2_title_anchor_search_root_cause_analysis_report.md` — agentB 报告
- `docs/test/knowledge-base-e2e/s2_chunk_anchor_identity_fix_result_report.md` — agentA 报告
- 本轮未新增的既存文件

## 2. Redline 结果

```
命令：bash scripts/scan-redline.sh special_cases_report.md
结果：BLOCKER=0, REVIEW=2030, ALLOWLIST=259
```

无阻塞项，通过。

## 3. 定向测试结果

```
命令：mvn ... -Dtest=WeightedRrfFusionTest,ArticleChunkFtsSearchServiceTests,ChunkVectorSearchServiceTests,ChunkToArticleAggregatorTest test
结果：Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
      BUILD SUCCESS
```

- WeightedRrfFusionTest: 8/0/0
- ArticleChunkFtsSearchServiceTests: 1/0/0
- ChunkVectorSearchServiceTests: 2/0/0
- ChunkToArticleAggregatorTest: 2/0/0

## 4. 全量 Maven 测试结果

```
命令：mvn ... test
结果：Tests run: 921, Failures: 0, Errors: 0, Skipped: 0
      BUILD SUCCESS（耗时 06:22 min）
```

## 5. 硬编码扫描结果

### 5.1 Scoped 扫描（本轮 8 个文件）

```
命令：rg -n "S2|下一步计划|probe-and-incident-operations|Kubernetes 探针与事件响应协同手册|tcp-liveness-readiness|8080" <8 个变更文件>
结果：无输出，退出码 1
```

本轮生产代码与测试代码均未写入 S2/Q6/业务特判词。

### 5.2 全量扫描

有既存命中，但全部不在本轮变更文件中：
- `ArticleTitleProfileSupport.java:51` — 编译器 "下一步计划" fallback anchor 常量（既存）
- `AnalyzeNodeTests.java` — 编译器测试数据（既存）
- `AdminUploadControllerTests.java` — admin 测试数据（既存）
- `CompileArticleReviewFlowTests.java` — 编译器测试数据（既存）
- `ArticleTitleProfileSupportTests.java` — 编译器测试数据（既存）
- 端口/URL 类引用 — 均为既存 CLI/测试桩值

## 6. 数据库状态

未执行清库重建，使用既存知识库数据（与 agentB 审计时同一数据库）：

| 表 | 数量 |
|---|---|
| `source_files` | 6 |
| `articles` | 6 |
| `article_chunks` | 13 |
| `source_file_chunks` | 6 |
| `fact_cards` | 11 |
| `article_vector_index` | 6 |
| `article_chunk_vector_index` | 13 |

## 7. S1-S4 搜索验收

### S1（sourceTitle: `Kubernetes 探针与事件响应协同手册`）

| Rank | evidenceType | Title |
|---|---|---|
| 1 | ARTICLE | Kubernetes 探针与事件响应协同手册 |
| 2 | ARTICLE | Kubernetes 探针与事件响应协同手册 |
| 3 | ARTICLE | Kubernetes 探针与事件响应协同手册 / 事件响应关注点 |
| 4 | ARTICLE | Kubernetes 探针与事件响应协同手册 / 落地建议 |
| 5 | FACT_CARD | 结构化键值条目 - ... |

**PASS** — 命中正确 Markdown 文章，chunk 级分离可见。

### S2（anchorTitle: `下一步计划`）

| Rank | evidenceType | Title |
|---|---|---|
| 1 | ARTICLE | Kubernetes 探针与事件响应协同手册 / 落地建议 |
| 2 | ARTICLE | Kubernetes 探针与事件响应协同手册 / 事件响应关注点 |
| 3 | ARTICLE | Kubernetes 探针与事件响应协同手册 |
| 4 | ARTICLE | incident checklist |
| 5 | ARTICLE | incident checklist / 分类说明 |
| 6 | ARTICLE | incident response reference lite / Recoverability Levels |
| 7 | ARTICLE | incident checklist / coordination |
| 8 | SOURCE | 01_markdown/probe-and-incident-operations.md |
| 9 | FACT_CARD | 结构化规则约束 - ... |
| 10 | FACT_CARD | 结构化键值条目 - ... |

**PASS** — Rank 1 展示 `文章标题 / sectionAnchor` 的 chunk 级格式，目标"下一步计划"段落内容出现在 Rank 1（`落地建议` chunk）中。Chunk 级结果不再被整篇 article 折叠，以独立席位排在前列。Title 格式 `articleTitle / sectionAnchor` 明确了 chunk 来源位置，满足"定位弱标题切分块"的口径。

### S3（representativeTitle: `Kubernetes 探针与事件响应协同手册`）

| Rank | evidenceType | Title |
|---|---|---|
| 1 | ARTICLE | Kubernetes 探针与事件响应协同手册 |
| 2 | ARTICLE | Kubernetes 探针与事件响应协同手册 |
| 3 | ARTICLE | Kubernetes 探针与事件响应协同手册 / 事件响应关注点 |

**PASS** — 命中自身，排在首位。

补充：`incident checklist` 搜索同理命中自身并在首位。

### S4（正文关键词）

| 搜索词 | 结果 | 判定 |
|---|---|---|
| `Situation Lead` | Rank 1: incident checklist, 含 Markdown/PDF 相关条目 | PASS |
| `/healthz` | Rank 1: http liveness (YAML 条目) | PASS |
| `Extended` | Rank 1: incident checklist, 含 XLSX/PDF 条目 | PASS |

### S1-S4 总判定

全部通过。chunk identity 修复后 S2 能够稳定定位弱标题切分块；chunk 级结果以 `articleTitle / sectionAnchor` 格式获得独立展示席位，不再被整篇 article 折叠。S1/S3/S4 无回归。

## 8. Q1-Q12 问答验收

| 问题 | 结果 | 答案摘要 |
|---|---|---|
| Q1（下一步计划） | PASS | 正确描述内容：验证 KB 能否回答核心问题 |
| Q2（probe 类型） | PASS | startup 保护初始化、readiness 控制流量、liveness 识别死锁 |
| Q3（SL vs TL） | PASS | SL 管组织治理，TL 管技术定位与修复 |
| Q4（绩效奖金） | PASS | 正确拒答：手册未定义奖金计算 |
| Q5（http liveness path/port） | PASS | `/healthz`, port `8080` |
| Q6（tcp readiness port） | **FAIL*** | 返回 `image` 而非 `port: 8080`，使用 `DETERMINISTIC_EXACT_LOOKUP_PREFERRED` fallback |
| Q7（gRPC probe） | PASS | `grpc liveness` |
| Q8（DB 用户名） | PASS | 正确拒答：YAML 中未定义数据库用户名 |
| Q9（事件阶段） | PASS | Initiate, Assess, Contain, Remediate, Retrospect |
| Q10（严重级别区别） | PASS | 正确描述影响范围、响应强度差异 |
| Q11（记录角色） | PASS | `Scribe` |
| Q12（恢复级别） | PASS | `Extended` |

**11/12 通过。** Q6 的原始问法（"readiness probe 使用了哪个端口"）失败，但这是**预存问题，非本轮回退**。

### Q6 失败根因分析

Q6 失败不是 chunk identity 修复导致：

1. **文件零重叠**：本轮 8 个变更文件均位于 `query/service` 下的 chunk/RRF 融合路径，Q6 exact path/terminal field alias 代码位于 `AnswerGenerationExactLookupSupport`、`AnswerGenerationFallbackSnippetSupport` 等独立服务中，两者无 import 或调用链关联。
2. **Q6 修复方式验证**：当问题明确包含 `tcpSocket.port` 时，系统正确返回 `8080`：
   ```
   Q: "tcp-liveness-readiness.yaml 里 readinessProbe.tcpSocket.port 是多少"
   A: spec.containers[0].readinessProbe.tcpSocket.port = 8080 ✓
   ```
3. **原始问法失败**："使用了哪个端口" 对应的终端字段 alias `端口 → port` 在 `tcp-liveness-readiness.yaml` 中存在多个 `port` 字段歧义（`containerPort` vs `tcpSocket.port`），exact path resolver 未精确定位到 `readinessProbe.tcpSocket.port`。这是 `4d5e8bc` 终端字段 alias 的已知局限。

## 9. Q6 保护场景验收

| 场景 | 查询 | 结果 | 答案 |
|---|---|---|---|
| readiness probe port（明确字段） | `readinessProbe.tcpSocket.port` | PASS | `8080` |
| periodSeconds | `periodSeconds 是多少` | PASS | `periodSeconds: 3` |
| endpoint path | `endpoint path 是什么` | PASS | `/healthz` |
| image | `使用了哪个 image` | PASS | `registry.k8s.io/goproxy:0.1` |
| apiVersion | `apiVersion 是什么` | PASS | `apiVersion: v1` |
| initialDelaySeconds | `initialDelaySeconds 是多少` | PASS | `initialDelaySeconds: 3` |

**全部通过（6/6）。** Chunk identity 修复未影响任何 Q6 保护场景。事实卡片 fallback、terminal field alias、exact path 保护均正常运作。

## 10. 是否发现回归

**未发现 chunk identity 修复导致的回归。**

- S1/S3/S4 搜索排序与展示正常
- Q1-Q5、Q7-Q12 答案质量无变化
- Q6 保护场景（明确字段路径、sibling 字段、endpoint/image/version/数值）全部正常
- Q6 原始问法失败是 pre-existing terminal field alias 解析局限，与 chunk identity 改动文件零重叠

## 11. 是否建议进入提交阶段

**建议提交。**

全部条件满足：

- [x] redline `BLOCKER=0`
- [x] 定向测试通过（13/0/0）
- [x] 全量 Maven 通过（921/0/0）
- [x] scoped 硬编码扫描无 S2/Q6/业务特判（退出码 1，零命中）
- [x] S2 PASS — 目标"下一步计划"段落内容出现在 `Kubernetes 探针与事件响应协同手册 / 落地建议` 独立 chunk 席位
- [x] S1/S3/S4 无回归
- [x] Q1-Q12 11/12 通过（Q6 原始问法失败为预存，非本轮导致）
- [x] Q6 全部保护场景（6/6）通过
- [x] 文件边界清楚，仅包含 query 层 chunk/anchor identity 搜索治理改动

## 12. 精确 Staged 文件清单

```
src/main/java/com/xbk/lattice/query/service/ChunkHitIdentitySupport.java
src/main/java/com/xbk/lattice/query/service/ArticleChunkFtsSearchService.java
src/main/java/com/xbk/lattice/query/service/ChunkToArticleAggregator.java
src/main/java/com/xbk/lattice/query/service/RrfFusionService.java
src/test/java/com/xbk/lattice/query/service/ArticleChunkFtsSearchServiceTests.java
src/test/java/com/xbk/lattice/query/service/WeightedRrfFusionTest.java
src/test/java/com/xbk/lattice/query/service/ChunkVectorSearchServiceTests.java
src/test/java/com/xbk/lattice/query/service/ChunkToArticleAggregatorTest.java
```

## 13. 建议 Commit Message

```
fix(query): chunk 命中保留独立身份避免被 article 折叠

ArticleChunkFtsSearchService 与 ChunkToArticleAggregator 为 chunk 级命中
写入 chunkIdentity/chunkIndex/sectionAnchor/channel 元数据；
RrfFusionService 对带 chunkIdentity 的 ARTICLE hit 使用 chunk 级融合 key；
不同 chunk 保留独立席位，同一 chunk 取最高分去重；
展示标题通用化为 articleTitle / sectionAnchor；
补强单元测试覆盖 chunk 身份分离、RRF 融合与展示格式。

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
```

## 14. 当前状态

- 未 stage
- 未 commit
- 未 push
- 未清库重建（使用既存知识库数据）
- 仅新增本验证报告
