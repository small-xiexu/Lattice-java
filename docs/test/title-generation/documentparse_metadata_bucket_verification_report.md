# documentparse 元数据桶验证报告

- 验证时间：2026-05-27 16:46（Asia/Shanghai）
- 验证性质：只读审计
- 验证 Agent：agentD
- 结论用途：判断 documentparse 元数据桶是否具备独立提交条件
- 约束声明：未 stage、未 commit、未 push，且未修改任何业务代码

## 1. 工作区只读盘点

### 1.1 候选文件（documentparse 元数据桶，共 6 个）

| 文件 | 类型 |
|---|---|
| `src/main/java/com/xbk/lattice/documentparse/application/DocumentParseApplicationService.java` | 生产代码 |
| `src/main/java/com/xbk/lattice/documentparse/extractor/PptTextExtractor.java` | 生产代码 |
| `src/main/java/com/xbk/lattice/documentparse/infra/extractor/TextFileNativeExtractor.java` | 生产代码 |
| `src/main/java/com/xbk/lattice/documentparse/service/DocumentParseResultNormalizer.java` | 生产代码 |
| `src/test/java/com/xbk/lattice/documentparse/service/DocumentParseResultNormalizerTests.java` | 测试代码 |
| `src/test/java/com/xbk/lattice/documentparse/service/DocumentParseRouterIntegrationTests.java` | 测试代码 |

### 1.2 明确排除文件

以下文件虽然在工作区有改动，但不属于 documentparse 元数据桶，必须排除：

- `docs/模型绑定配置参考.md` — 私有配置，永远排除提交
- `docs/项目全流程真实验收手册.md` — 不属于本桶
- `special_cases_report.md` — redline 输出，排除
- `src/main/java/com/xbk/lattice/admin/service/AdminProcessingTaskService.java` — admin 层
- `src/main/java/com/xbk/lattice/api/admin/AdminArticleController.java` — admin API
- `src/main/java/com/xbk/lattice/api/admin/AdminArticleDetailResponse.java` — admin API
- `src/main/java/com/xbk/lattice/api/admin/AdminArticleSummaryResponse.java` — admin API
- `src/main/java/com/xbk/lattice/api/admin/AdminArticleTitleProfile.java`（untracked）— admin API
- `src/main/java/com/xbk/lattice/llm/service/ExecutionLlmSnapshotService.java` — 与 documentparse 无关
- `src/main/resources/static/admin/**` — admin UI 静态资源
- `src/test/java/com/xbk/lattice/api/admin/**` — admin 测试
- `src/test/resources/admin/management-js-runtime-test.js`（untracked）— admin 测试资源
- `docs/plans/*`（untracked）— 计划文档
- `docs/test/knowledge-base-e2e/q6_*`（untracked）— Q6 余波报告

## 2. 候选文件 diff 审核

### 2.1 DocumentParseApplicationService.java

- 新增 `documentParseResultNormalizer.normalizeMetadata(parseOutput)` 调用
- 将 `parseOutput.getMetadataJson()` 替换为 `normalizedMetadataJson`
- 改动极小（+2 行，-1 行），仅作为 metadata 归一化的入口接入点

### 2.2 PptTextExtractor.java

- `normalizeText()` 方法新增 null guard：`if (value == null) { return ""; }`
- 这是一个健壮性修复，防止 PPT 文本提取时 NPE
- 与 documentTitle 元数据无直接关系，但属于 documentparse 模块内的质量加固
- 改动极小（+3 行），可接受并入本桶

### 2.3 TextFileNativeExtractor.java

- 新增 `DocumentTitleSupport` import
- 原始文本文件路径（TXT/MD 等）：调用 `DocumentTitleSupport.resolveTextDocumentTitle()` 从正文 H1 提取标题，再 `upsertDocumentTitle()` 写入 metadata JSON
- 非文本文件路径（CSV 等通过提取器）：调用 `DocumentTitleSupport.resolveDocumentTitle()` 从已有 metadata 候选字段解析标题，再 `upsertDocumentTitle()` 写入
- 两处均从硬编码 `"{}"` 改为实际解析的 `metadataJson`

### 2.4 DocumentParseResultNormalizer.java

- 核心变更：`mergeMetadata()` → `normalizeMetadata()`，可见性从 `private` 改为 `public`
- 新增 `resolveDocumentTitle()` 私有方法：根据 `ParseMode` 分发到不同解析策略
  - `TEXT_READ`：从正文 H1 提取（`resolveTextDocumentTitle`）
  - 其他模式：从 metadata 候选字段解析（如 PPT 的 `slideTitles`）
- 归一化 metadata JSON 中新增 `documentTitle` 字段（仅当有值）

### 2.5 DocumentParseResultNormalizerTests.java

- 原有测试补充 `documentTitle` 断言
- 新增 `shouldResolveTextDocumentTitleFromHeading`：测试 H1 标题提取
- 新增 `shouldReuseDocumentTitleFromMetadataCandidates`：测试 slideTitles 候选复用
- 测试用例使用通用样本数据（`"Delivery Plan"`、`"Weekly Review"`），无业务耦合

### 2.6 DocumentParseRouterIntegrationTests.java

- 新增 markdown 测试文件（含 YAML frontmatter `title: Delivery Playbook`）
- 新增 CSV `documentTitle` 断言（`"rules"` 从文件名 fallback）
- 新增 markdown `documentTitle` 断言（`"Delivery Playbook"` 从 frontmatter 提取）
- 集成测试新增断言验证 documentTitle 在整个解析链路中正确回流

## 3. Redline 结果

```
命令：bash scripts/scan-redline.sh special_cases_report.md
结果：BLOCKER=0, REVIEW=2028, ALLOWLIST=259
```

红线没有阻塞项，通过。

## 4. Maven 全量测试

```
命令：mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test
结果：Tests run: 915, Failures: 0, Errors: 0, Skipped: 0
结论：BUILD SUCCESS
```

全量测试通过。

## 5. documentparse 定向测试

```
命令：mvn ... -Dtest="DocumentParseResultNormalizerTests,DocumentParseRouterIntegrationTests" test
结果：
  DocumentParseRouterIntegrationTests: 6/0/0
  DocumentParseResultNormalizerTests:  3/0/0
  Total: 9/0/0
结论：BUILD SUCCESS
```

定向测试全部通过。

## 6. 硬编码 / 过拟合 / 敏感信息扫描

扫描范围：`src/main/java/com/xbk/lattice/documentparse` 与 `src/test/java/com/xbk/lattice/documentparse`

### 6.1 生产代码

- `documentTitle` 引用出现在 `DocumentParseResultNormalizer.java` 和 `TextFileNativeExtractor.java` 中，均作为**结构化字段名**使用，可接受
- `apiKey` 出现在 `AbstractJsonBodyOcrProviderAdapter.java`，属于**存量 OCR 凭证配置读取逻辑**，不在本次 diff 范围内，且使用配置化读取而非硬编码密钥
- 文件扩展名（`"pptx"`, `"xlsx"` 等）在 `OfficeDocumentNativeExtractor.java` 中用于**格式路由**，属于存量代码
- 未发现任何具体样本文件名、样本标题、题集名、端口值在生产代码中做逻辑特判

### 6.2 测试代码

- 测试断言中出现 `"documentTitle":"Delivery Plan"`、`"documentTitle":"Weekly Review"`、`"documentTitle":"Delivery Playbook"` 等，属于**测试样本数据**，可接受
- 测试文件路径如 `"docs/review.pptx"`、`"playbook.md"` 为测试构造的临时文件，可接受

### 6.3 结论

- 未发现真实 API 密钥或令牌
- 未发现生产代码中对具体样本文件名、标题、题集的逻辑特判
- 未发现 `8080`、`Kubernetes`、`tcp-liveness-readiness`、`下一步计划` 等 Q6 业务域关键词

## 7. 架构边界判断

| 问题 | 结论 |
|---|---|
| 这 6 个文件是否构成独立 documentparse 元数据桶？ | **是**。核心链路围绕 documentTitle 元数据在解析层的提取、归一化与回流 |
| 是否可以在 title-generation 主桶之后独立提交？ | **是**。documentparse 桶是 title-generation 的上游输入，边界清晰 |
| 是否依赖 admin UI/API？ | **否**。6 个文件均在 `documentparse` 包下，与 admin 零耦合 |
| 是否依赖 docs/plans？ | **否** |
| 是否与 ExecutionLlmSnapshotService.java 有关系？ | **否**。两者无任何 import 或逻辑关联 |
| 是否需要同步 docs/quality-progress-and-lessons.md？ | **建议提交后更新**，记录 documentparse 桶已提交 |
| 是否建议进入提交阶段？ | **是** |

### 7.1 注意事项

`PptTextExtractor.java` 的 null guard 改动（+3 行）与 documentTitle 元数据无直接关系，属于 PPT 文本提取的健壮性加固。由于改动极小且在同一模块内，可接受并入本桶一并提交。若想更严格拆分，也可单独作为 `fix(documentparse): ppt normalizeText NPE 防护` 提交，但性价比不高。

## 8. 提交建议

### 8.1 是否建议提交

**建议提交。**

同时满足以下全部条件：

- [x] redline `BLOCKER=0`
- [x] 全量 `mvn test` 通过（915/0/0）
- [x] documentparse 定向测试通过（9/0/0）
- [x] 生产代码未发现样本/文件名/标题/题集特判
- [x] 未发现真实密钥
- [x] 文件边界与 admin、docs/plans、Q6 余波、ExecutionLlmSnapshotService 可独立拆清楚

### 8.2 精确 staged 文件清单

```
src/main/java/com/xbk/lattice/documentparse/application/DocumentParseApplicationService.java
src/main/java/com/xbk/lattice/documentparse/extractor/PptTextExtractor.java
src/main/java/com/xbk/lattice/documentparse/infra/extractor/TextFileNativeExtractor.java
src/main/java/com/xbk/lattice/documentparse/service/DocumentParseResultNormalizer.java
src/test/java/com/xbk/lattice/documentparse/service/DocumentParseResultNormalizerTests.java
src/test/java/com/xbk/lattice/documentparse/service/DocumentParseRouterIntegrationTests.java
```

### 8.3 建议 commit message

```
feat(documentparse): 回流文档标题元数据

DocumentParseResultNormalizer 归一化 metadata 时注入 documentTitle；
TextFileNativeExtractor 在文本/Office 解析路径中解析并回写标题；
DocumentParseApplicationService 入口接入归一化 metadata；
补强单元测试与集成测试覆盖 H1 提取、slideTitles 候选复用与端到端回流。

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
```

## 9. 当前状态

- 未 stage
- 未 commit
- 未 push
- 仅新增本验证报告
