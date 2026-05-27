# title-generation 桶验证报告

- 验证时间：2026-05-27 15:27（Asia/Shanghai）
- 验证性质：只读审计
- 结论用途：判断 title-generation / 标题画像主线是否具备独立提交条件
- 约束声明：未 stage、未 commit、未 push，且未修改任何业务代码

## 1. Q6 commit 审计结论

已审计 `4d5e8bc05b7d81a2c43d7c047a793a68782be0b7`。

结论：该 commit 范围干净，确实只包含 Q6 terminal field alias / exact path 修复及其验证报告、质量台账更新，没有误混入以下内容：

- `docs/模型绑定配置参考.md`
- admin 管理端改动
- title-generation 改动
- documentparse 改动
- compiler 非 Q6 改动
- `special_cases_report.md`
- 临时文件或私密配置

## 2. 工作区总览

当前未提交改动是混合工作区，不是单一 title-generation 桶。

### 2.1 title-generation 候选文件

这些文件构成 title-generation 主线的核心候选：

- `/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/compiler/config/CompilerProperties.java`
- `/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/compiler/domain/AnalyzedConcept.java`
- `/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/compiler/domain/MergedConcept.java`
- `/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/compiler/node/AnalyzeNode.java`
- `/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/compiler/node/CompileArticleNode.java`
- `/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/compiler/node/CrossGroupMergeNode.java`
- `/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/compiler/node/DocumentTopicConceptExtractor.java`
- `/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/compiler/node/IngestNode.java`
- `/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/compiler/prompt/LatticePrompts.java`
- `/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/compiler/prompt/SchemaAwarePrompts.java`
- `/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/compiler/service/ArticleCompileSupport.java`
- `/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/compiler/service/ArticleTitleProfileSupport.java`
- `/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/compiler/service/IncrementalCompileBaseSupport.java`
- `/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/compiler/service/IncrementalCompileEnhancementSupport.java`
- `/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/compiler/service/IncrementalCompilePlanningSupport.java`
- `/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/compiler/service/IncrementalCompileService.java`
- `/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/compiler/service/IncrementalCompileWritebackSupport.java`
- `/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/compiler/service/SourceIngestSupport.java`
- `/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/source/service/BundleFeatureExtractor.java`
- `/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/infra/persistence/ArticleJdbcRepository.java`
- `/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/shared/text/DocumentTitleSupport.java`
- `/Users/sxie/xbk/Lattice-java/src/test/java/com/xbk/lattice/compiler/service/ArticleTitleProfileSupportTests.java`
- `/Users/sxie/xbk/Lattice-java/src/test/resources/title-generation/**`
- `/Users/sxie/xbk/Lattice-java/docs/test/title-generation/**`

### 2.2 明确排除文件

这些文件不应并入 title-generation 主桶：

- `/Users/sxie/xbk/Lattice-java/docs/模型绑定配置参考.md`，本地私有配置，必须排除提交
- `/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/api/admin/AdminArticleController.java`
- `/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/api/admin/AdminArticleDetailResponse.java`
- `/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/api/admin/AdminArticleSummaryResponse.java`
- `/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/api/admin/AdminArticleTitleProfile.java`
- `/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/admin/service/AdminProcessingTaskService.java`
- `/Users/sxie/xbk/Lattice-java/src/main/resources/static/admin/**`
- `/Users/sxie/xbk/Lattice-java/src/test/java/com/xbk/lattice/api/admin/**`
- `/Users/sxie/xbk/Lattice-java/src/test/resources/admin/management-js-runtime-test.js`
- `/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/documentparse/application/DocumentParseApplicationService.java`
- `/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/documentparse/extractor/PptTextExtractor.java`
- `/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/documentparse/infra/extractor/TextFileNativeExtractor.java`
- `/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/documentparse/service/DocumentParseResultNormalizer.java`
- `/Users/sxie/xbk/Lattice-java/docs/plans/2026-05-24-知识条目标题生成优化实施计划.md`
- `/Users/sxie/xbk/Lattice-java/docs/plans/2026-05-25-知识库验收阻塞修复实施方案.md`
- `/Users/sxie/xbk/Lattice-java/docs/test/knowledge-base-e2e/q6_exact_path_sibling_root_cause_analysis_report.md`
- `/Users/sxie/xbk/Lattice-java/docs/test/knowledge-base-e2e/q6_exact_path_terminal_field_verification_report.md`
- `/Users/sxie/xbk/Lattice-java/special_cases_report.md`
- `/Users/sxie/xbk/Lattice-java/src/main/java/com/xbk/lattice/llm/service/ExecutionLlmSnapshotService.java`

## 3. 红线结果

- `bash scripts/scan-redline.sh special_cases_report.md`
- 结果：`BLOCKER=0`

结论：红线没有阻塞，但存在若干 REVIEW 命中，不能据此把混合工作区直接当成单桶完成。

## 4. 测试结果

### 4.1 全量 Maven

- 命令：`mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test`
- 结果：`Tests run: 915, Failures: 0, Errors: 0, Skipped: 0`
- 结论：`BUILD SUCCESS`

### 4.2 title-generation / 主链定向测试

- `AnalyzeNodeTests, CompileArticleReviewFlowTests, CrossGroupMergeNodeTests, IngestNodeTests, StateGraphCompileOrchestratorTests`
  - 结果：`43/0/0`
- `AdminManagementControllerTests, AdminUploadControllerTests`
  - 结果：`15/0/0`
- `ArticleTitleProfileSupportTests`
  - 结果：`3/0/0`

补充：`AnalyzeNodeTests` 过程中有 LLM 重试日志，但最终通过，没有形成测试失败。

## 5. 硬编码 / 过拟合 / 敏感信息扫描

扫描范围覆盖 `src/main/java`、`src/main/resources`、`src/test/java`、`src/test/resources` 与 `docs/test/title-generation`。

结论：

- 生产代码没有发现把样本文件名、题集名、anchor、expected heading 写成 case 特判的明显分支
- `sourceTitle`、`anchorTitle`、`representativeTitle`、`titleGenerationMode` 属于结构化字段，不是样本答案硬编码
- `docs/test/title-generation` 与 `src/test/resources/title-generation` 中出现样本标题、anchor、expected 仅属于测试资料，可接受
- 未发现真实接口密钥或令牌

## 6. 架构边界判断

### 6.1 title-generation 桶本身是否成立

成立。核心链路围绕：

- 标题画像生成与回写
- 编译/分析/合并节点的标题信号传播
- bundle 级标题候选提取
- 文章仓储搜索文本的标题画像索引
- title-generation 专项测试与样本资源

### 6.2 需要拆开的相邻桶

#### documentparse 桶

`DocumentParseApplicationService`、`PptTextExtractor`、`TextFileNativeExtractor`、`DocumentParseResultNormalizer` 这组改动更像文档解析元数据传播层。

如果它们只是为了把 `documentTitle/sourceTitle` 回流给标题画像，可以视为依赖；但从边界上看，仍建议拆成单独 documentparse 桶，避免把解析增强和 title-generation 混成一个提交。

#### admin UI / admin API 桶

`AdminArticleController`、`AdminArticleDetailResponse`、`AdminArticleSummaryResponse`、`AdminArticleTitleProfile` 以及静态 admin 资源和 admin 测试资源，都更像 admin 展示与 API 层。

这部分不应并入 title-generation 主桶，除非只是最小展示字段透传；当前工作区里还有静态 UI 和 admin 测试资源，因此更适合单独拆桶。

#### docs / plans 桶

`docs/plans/*`、`docs/test/knowledge-base-e2e/*`、`docs/test/title-generation/*`、`docs/quality-progress-and-lessons.md` 都属于文档、计划、验证台账，不应和生产代码混提。

#### 私有配置桶

`docs/模型绑定配置参考.md` 必须永远排除提交。

#### 混合 / 来源不明文件

`ExecutionLlmSnapshotService.java` 与 title-generation 主线没有清晰边界，建议先单独分析再决定去向。

## 7. 是否建议现在进入提交阶段

不建议现在直接进入提交阶段。

原因：

1. 当前工作区明显混合了 title-generation、documentparse、admin UI/API、计划文档、验证报告、私有配置与少量来源不明改动
2. title-generation 主线本身是可成桶的，但现在还没拆干净
3. 如果此时直接提交，容易把非主线内容一并带进去，后续回溯成本高

## 8. 建议的下一步顺序

1. 先把 title-generation 主线与 documentparse / admin UI / docs / 私有配置分桶隔离
2. 单独处理 `docs/模型绑定配置参考.md`，保持绝不提交
3. title-generation 主桶完成后，再看是否需要单独提交 documentparse 元数据桶
4. admin API / admin UI 改动另起一桶
5. 所有桶都拆干净后，再进入 S2 分析

## 9. 建议的后续提交命名

- title-generation 主桶：`feat(compiler): 配置化标题画像与文档标题回流`
- documentparse 桶：`feat(documentparse): 回流文档标题元数据`
- admin API / UI 桶：`feat(admin): 展示文章标题画像`

## 10. 当前状态

- 未 stage
- 未 commit
- 未 push
