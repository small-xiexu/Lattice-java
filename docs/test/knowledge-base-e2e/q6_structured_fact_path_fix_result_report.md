# Q6 结构化 fact card 层级路径修复结果报告

生成时间：2026-05-25 16:45
复验时间：2026-05-26 11:45 CST

## 1. 本轮范围

本轮只处理结构化资料 fact card 生成时丢失层级路径的问题。修复点限定在 fact card 生成层，未修改 Query、RRF、chunk fusion、搜索排序、AnswerGeneration 或 fallback 主链。

## 2. 修改前根因

YAML / JSON / 缩进式结构化文本生成 `key_value_list` fact card 时，只保留扁平 `key / value / raw`。当多个同名字段位于不同父级节点或数组项下时，fact card 无法区分字段上下文，后续 evidence binding / fallback 更容易把已召回证据绑定到错误语义位置。

该问题属于“证据已进入结构化处理，但字段上下文丢失”，不是检索未召回、query rewrite、RRF 或回答成文主链的首要问题。

## 3. 修改文件

- `src/main/java/com/xbk/lattice/compiler/service/FactCardGenerationBaseSupport.java`
- `src/main/java/com/xbk/lattice/compiler/service/FactCardGenerationListSupport.java`
- `src/main/java/com/xbk/lattice/compiler/service/FactCardGenerationModels.java`
- `src/test/java/com/xbk/lattice/compiler/service/FactCardGenerationServiceTests.java`
- `docs/plans/2026-05-25-知识库验收阻塞修复实施方案.md`

## 4. 通用修复点

- 保留旧字段：`key / value / raw` 继续写入 `itemsJson`，兼容现有消费者。
- 新增路径字段：`keyPath / parentPath / pathSegments / contextPath / displayText / lineIndex`。
- JSON 对象、数组和缩进式键值结构统一提取叶子值路径。
- `itemsJson` 增加 `pathAware` 标记，用于识别当前 fact card 是否携带结构化路径。
- `evidenceText` 增加通用 `fieldPath: 完整路径 = 值` 行，同时保留原始行，便于后续证据绑定使用。
- review 校验对生成的 `fieldPath` 行做通用处理：生成行不要求原文连续包含，但原始证据行必须可回指到 chunk。

## 5. 为什么不是 case 特判

- 生产代码没有写入当前验收样例的文件名、题号、业务词、固定答案或问题问法。
- 规则只依赖 JSON 结构、缩进层级、数组序号、键值分隔符和原始行回指等通用文本结构信号。
- 测试使用通用 `nested-config.yaml/json` 样例，仅验证层级路径、数组项路径和旧字段兼容。
- 未改主链 prompt、fallback outcome、AnswerGeneration、rerank、检索排序或 redline allowlist。

## 6. 验证结果

- 修改后红线复扫：`bash scripts/scan-redline.sh special_cases_report.md`，退出码 `0`，`BLOCKER=0`。
- 定向测试：`mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -Dtest=FactCardGenerationServiceTests test`，`21/0/0` 通过。
- 全量测试：`mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test`，执行 `900` 个测试，`1` 个失败。
- 全量失败项：`AdminPageControllerTests.shouldServeKnowledgePages`，断言页面应包含 `id="refresh-hotspots"`，但当前工作台 HTML 已无该节点。
- 失败归因：该失败位于管理端页面静态资源/测试契约，不涉及 fact card 生成层；本轮未修改 `AdminPageControllerTests`，也未触碰管理端页面 HTML。

### 6.1 2026-05-26 复验结果

- 红线复扫：`bash scripts/scan-redline.sh special_cases_report.md`，退出码 `0`，`BLOCKER=0`（报告汇总：`REVIEW=1974`、`ALLOWLIST=259`）。
- Q6 fact card 定向测试：`mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -Dtest=FactCardGenerationServiceTests test`，`21/0/0` 通过。
- 按用户指定命令重新跑全量测试：`mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test`，退出码 `1`，`Tests run: 900, Failures: 1, Errors: 0, Skipped: 0`，总耗时 `06:56 min`。
- 全量失败项仍为：`AdminPageControllerTests.shouldServeKnowledgePages`。Surefire 明细：`AdminPageControllerTests` 执行 `2` 个测试，`1` 个失败。
- 当前失败断言仍为：`Response content Expected: a string containing "id=\"refresh-hotspots\""`，失败位置为 `AdminPageControllerTests.java:84`。
- 复验结论：全量 Maven 门禁仍未恢复；失败仍是管理端页面静态资源/测试契约问题，不涉及 Q6 fact card 生成层。

### 6.2 2026-05-26 11:45 再复验

- 红线复扫：`bash scripts/scan-redline.sh special_cases_report.md`，退出码 `0`，`BLOCKER=0`。
- 管理端定向测试：`mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -Dtest=AdminPageControllerTests test`，退出码 `1`，`Tests run: 2, Failures: 1, Errors: 0, Skipped: 0`。
- 管理端失败项仍为：`AdminPageControllerTests.shouldServeKnowledgePages`。失败断言仍为 `Response content Expected: a string containing "id=\"refresh-hotspots\""`，失败位置仍是 `AdminPageControllerTests.java:84`。
- Q6 fact card 定向测试：`mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -Dtest=FactCardGenerationServiceTests test`，退出码 `0`，`21/0/0` 通过。
- 全量 `mvn test`：未继续执行。原因是前置管理端定向测试未通过，按门禁已止步。
- 复验结论：Q6 定向仍通过，管理端页面契约问题仍未恢复，全量 Maven gate 仍未恢复。

### 6.3 2026-05-26 管理端页面契约复验（测试契约更新）

- 红线复扫：`bash scripts/scan-redline.sh special_cases_report.md`，退出码 `0`，`BLOCKER=0`。
- 管理端定向测试：`mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -Dtest=AdminPageControllerTests test`，退出码 `0`，`Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`。
- 管理端运行时测试：`mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -Dtest=ManagementJsRuntimeTests test`，退出码 `0`，`Tests run: 8, Failures: 0, Errors: 0, Skipped: 0`。
- Q6 fact card 定向测试：`mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -Dtest=FactCardGenerationServiceTests test`，退出码 `0`，`21/0/0` 通过。
- 全量 `mvn test`：`exit 0`，`Tests run: 901, Failures: 0, Errors: 0, Skipped: 0`，总耗时 `06:39 min`。
- 结论：这次恢复 gate 的原因是把 `AdminPageControllerTests.shouldServeKnowledgePages` 的过期 DOM 契约对齐到当前页面结构，并补上旧治理 UI 的负向断言；这是测试契约更新，不是功能代码修复，也不是为了 Q6 放宽测试。`ManagementJsRuntimeTests` 仍明确要求旧治理 UI 不出现在 `index.html` 中，产品方向保持一致。

## 7. 未覆盖风险

- 本轮只修复 fact card 中结构化字段路径丢失，不保证 Q6 最终回答一定通过。
- 若后续仍出现 Q6 失败，应作为下一轮独立根因分析，优先检查 evidence binding / fallback 选择是否消费了新增路径字段。
- S2、搜索排序、RRF、chunk fusion、AnswerGeneration 与 fallback 主链均未在本轮处理。

## 8. 结论

结构化 fact card 层级路径保留已完成并通过红线与定向测试验证。2026-05-26 11:45 复验时，管理端定向测试仍失败于 `AdminPageControllerTests.shouldServeKnowledgePages` 的 `id="refresh-hotspots"` 断言，因此当时全量 Maven 门禁未恢复；随后已将该过期页面契约对齐到当前管理端页面结构，并补充旧治理 UI 的负向断言。最新复验中，红线、`AdminPageControllerTests`、`ManagementJsRuntimeTests`、`FactCardGenerationServiceTests` 与全量 `mvn test` 均已通过，因此 Maven gate 已恢复。该收口是测试契约更新，不是功能代码修复，也不是为了 Q6 放宽测试。
