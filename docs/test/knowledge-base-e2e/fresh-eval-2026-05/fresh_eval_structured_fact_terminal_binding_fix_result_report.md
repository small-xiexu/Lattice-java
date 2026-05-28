# fresh eval structured fact terminal binding 最小修复结果报告

## 1. 修改前逻辑链路分析

### 1.1 fallback 证据选择链路

- 入口：`AnswerFallbackEvidenceSelector.selectFallbackEvidenceHits`
- 现状：
  - 先走 `filterFallbackEvidenceHits + sortFallbackEvidenceHits`
  - 再根据 `preferredArticleHits` 与 `shouldPreferMixedEvidence` 决定是否保留 article-only 结果
  - exact `FACT_ENUM` 虽已召回，但在 file-anchored exact lookup 场景下，容易被同源 article/source 摘要压到后位
- 结果：
  - 已召回的 direct structured fact card 没有稳定成为 fallback 主证据

### 1.2 fallback 结论构造链路

- 入口：`AnswerGenerationFallbackConclusionSupport.buildEvidenceConclusionLines`
- 现状：
  - 先尝试 `buildTerminalFieldExactPathConclusionLines`
  - 未命中后进入 `AnswerFallbackConclusionBuilder.buildGeneralFallbackConclusionLines`
  - `buildGeneralFallbackConclusionLines` 里，`buildFocusedSpreadsheetFieldDefinitionConclusionLines` 会在 file-anchored structured exact lookup 题上过早抢答
  - 同时，后续的 `buildExactPathConclusionLines` 对“文件名 + 自然语言字段”的问题过于宽松，容易把 file-like identifier 当成 path question 处理
- 结果：
  - 结论段更容易输出 summary / path-like companion / 无关 sibling，而不是 terminal field value

### 1.3 多焦点 sibling 对齐链路

- 入口：`AnswerGenerationQuestionTypeSupport.extractStructuredFactFocusTokens`
- 现状：
  - 对 `A 和 B 的 X 分别是多少` 这类问题，`removeLeadingPossessiveScope` 会把 `A 和 B` 误裁掉，只剩 `X`
  - `selectCoverageAwareStructuredFactSnippets` 随后无法按 sibling group 对齐多个 focus
  - 即使已抽到一部分 direct assignment，也会继续把无关 sibling 塞回结果
- 结果：
  - multi-focus 结构化题经常退回 summary，或在命中后仍混入无关 sibling

## 2. 实际修改文件清单

- `src/main/java/com/xbk/lattice/query/service/AnswerFallbackEvidenceSelector.java`
- `src/main/java/com/xbk/lattice/query/service/AnswerFallbackConclusionBuilder.java`
- `src/main/java/com/xbk/lattice/query/service/AnswerGenerationFallbackSnippetSelectionSupport.java`
- `src/main/java/com/xbk/lattice/query/service/AnswerGenerationQuestionTypeSupport.java`
- `src/test/java/com/xbk/lattice/query/service/AnswerGenerationServiceTests.java`

## 3. 通用修复点说明

### 3.1 direct structured fact card 提升

- 在 `AnswerFallbackEvidenceSelector` 中增加 direct structured fact card 识别：
  - 识别 `fieldPath` / direct structured assignment 证据
  - 在 exact lookup 场景下，如果 article-only 主证据不含 direct structured evidence，则允许竞争性的 fact card 前置

### 3.2 exact path 误判收窄

- 在 `AnswerFallbackConclusionBuilder` 中：
  - `exact path` 分支只对真正的 path/url/endpoint/config-identifier 问题启用
  - 不再因为“题面里出现了文件名/精确标识”就进入 path 结论分支

### 3.3 field definition 抢答收窄

- 仅在 file-like structured exact lookup 且具备 direct structured evidence 时，跳过 definition-style fallback
- 避免：
  - `A/B 分别是什么含义` 这类正常字段释义题被误伤
  - 同时避免 `xxx.yaml 里，A 和 B 的 X 分别是多少` 被字段释义分支提前截走

### 3.4 multi-focus sibling family 对齐

- 在 `AnswerGenerationQuestionTypeSupport` 中修正多焦点提取：
  - `A 和 B 的 X` 不再丢失 `A/B`
  - 子焦点片段会优先保留并列实体本身
- 在 `AnswerGenerationFallbackSnippetSelectionSupport` 中：
  - 为 multi-focus structured fact 增加 dominant terminal-field family 对齐
  - 当多个 focus 已被同一 terminal-field family 覆盖时，提前收口，不再继续追加无关 sibling

### 3.5 direct assignment value-shape 约束

- 对 direct structured assignment 增加通用 shape 优先级：
  - 非 path 问题压低 URL/endpoint 候选
  - 非 status 问题压低布尔值候选
- 目的：
  - 避免 `api_endpoint` / `damage_report_required` 这类 sibling 抢占 terminal field

## 4. 为什么不是 case 特判

- 本轮没有写入任何 fresh eval 题面、case id、文件名专用分支、答案值或业务词白名单
- 修复全部基于通用结构信号：
  - file-like referential identifier
  - direct structured assignment
  - fieldPath / terminal field family
  - multi-focus sibling group
  - URL / boolean / numeric 等 value shape
- 没有新增：
  - `实验室` / `精密仪器` / `押金` / `逾期罚金` / `最大并发请求数` 等业务词映射
  - `equipment-borrowing-policy.yaml` 专门逻辑
  - prompt / config / allowlist / rerank / compile 特判

## 5. 红线扫描结果

- 命令：`bash scripts/scan-redline.sh special_cases_report.md`
- 结果：
  - `BLOCKER=0`
  - `REVIEW=2078`
  - `ALLOWLIST=259`
- 说明：
  - `special_cases_report.md` 本轮仅由 redline 脚本覆盖输出，未人工编辑

## 6. 单测 / mvn test 结果

### 6.1 定向测试

- 命令：
  - `mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -Dtest=AnswerGenerationServiceTests#shouldPreferStructuredFactCardForNaturalLanguageMultiFocusQuestionAgainstArticleSummary+shouldAnswerFocusedFieldQuestionFromGenericSpreadsheetSource+shouldKeepExpandedMultiPointStructuredAnswerForFocusedQuestion+shouldExposeStructuredJsonValuesInFallbackAnswer test`
  - `mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -Dtest=AnswerGenerationServiceTests,AnswerFallbackEvidenceSelectorTests,AnswerFallbackConclusionBuilderTests test`
- 结果：
  - 两轮定向测试均通过
  - `AnswerGenerationServiceTests` / `AnswerFallbackEvidenceSelectorTests` / `AnswerFallbackConclusionBuilderTests` 全绿

### 6.2 全量测试

- 命令：`mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test`
- 结果：
  - `BUILD SUCCESS`
  - `Tests run: 923, Failures: 0, Errors: 0, Skipped: 0`

## 7. FQ3 / FQ4 / FQ6 / FG1 / FG2 复验结果

- 未执行服务级复验
- 原因：
  - `curl http://127.0.0.1:18082/actuator/health` 连接失败
  - 本地 `18082` 没有运行中的服务
- 结论：
  - 本轮仅完成代码层与测试层验证，未擅自启动复杂验收

## 8. 残余风险

- `reference` 段仍可能保留同源补充说明，其收敛力度弱于 `evidence` 主结论段
- 对单实体、单字段、且 sibling 全为同形态数值的极端场景，最终命中仍依赖：
  - fact card 自身排序
  - direct structured assignment 的相对分值
- 本轮没有动 compile / indexing / rerank / title 搜索，因此：
  - `FS1-FS3`
  - compile / chunk / title materialization
  仍是后续独立轮次问题

## 9. 合规声明

- 未修改：
  - `src/main/java/com/xbk/lattice/compiler/**`
  - `src/main/java/com/xbk/lattice/infra/persistence/**`
  - `src/main/java/com/xbk/lattice/query/service/RrfFusionService.java`
  - `src/main/java/com/xbk/lattice/query/service/ArticleChunkFtsSearchService.java`
  - `src/main/resources/**`
  - `scripts/**`
  - `docs/模型绑定配置参考.md`
- 未修改 title/search/rerank 主链实现
- 未修改 private docs / prompt / allowlist / redline 扫描规则
- 未 stage、未 commit、未 push
