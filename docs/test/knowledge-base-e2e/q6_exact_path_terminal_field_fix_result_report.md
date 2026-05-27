# Q6 Exact Path Terminal Field Fix Result Report

## 本轮修正的红线风险

上一轮 exact path terminal field 修复保留了通用 leaf key 排序能力，但在 Java 主链中直接写入了中文字段语义映射：中文字段 `端口` 被生产代码归一为 leaf key `port`。

这不是 Q6 文件名、答案值或具体题面特判，但仍属于“中文问法识别写在 Java 主链里”的红线风险。本轮唯一目标是移除该 Java 硬编码，把中文字段语义迁移为配置化、可审计、短小的通用语言信号。

## 修改文件

- `src/main/java/com/xbk/lattice/query/service/AnswerGenerationFallbackSnippetSelectionSupport.java`
- `src/main/java/com/xbk/lattice/query/service/TerminalFieldAliasRules.java`
- `src/main/resources/config/lattice-query-semantic.yml`
- `src/test/java/com/xbk/lattice/query/service/AnswerGenerationServiceTests.java`
- `docs/test/knowledge-base-e2e/q6_exact_path_terminal_field_fix_result_report.md`
- `docs/quality-progress-and-lessons.md`

`AnswerGenerationFallbackConclusionSupport.java` 沿用上一轮 leaf key 排序逻辑，本轮未继续扩大修改。`special_cases_report.md` 由 redline 命令刷新，不是人工修改生产逻辑。

## 中文字段语义如何配置化

新增 `TerminalFieldAliasRules` 作为最小配置读取类，默认读取 classpath 下的 `config/lattice-query-semantic.yml`：

```yaml
lattice:
  query:
    semantic:
      terminal-field-aliases:
        - canonical: "port"
          aliases:
            - "端口"
```

生产逻辑的处理边界：

- 英文通用 terminal field token 仍由 Java 集中识别：`port`、`url`、`endpoint`、`image`、`version`、`value`。
- 中文字段 alias 不写入 Java 分支，只通过 `terminal-field-aliases` 映射到 canonical leaf key。
- 配置缺失、格式异常或加载失败时 fail-safe：返回空 alias 规则，不抛异常，不影响英文 token 行为。

## 为什么不再是 Java 主链硬编码

`AnswerGenerationFallbackSnippetSelectionSupport` 现在只做三件事：

- 规范化 token。
- 保留英文通用 terminal field token。
- 调用 `TerminalFieldAliasRules.canonicalForAlias(token)` 查询配置化 alias map。

生产 Java 中已经删除 `if ("端口".equals(token)) return "port";` 这类中文词到字段名的直接映射。中文词只存在于可审计配置文件中；测试也通过注入 alias map 或空配置证明行为来自规则对象，而不是 Java 分支。

## 为什么仍是通用能力，不是 Q6 case 特判

本轮没有修改 retrieval、RRF、rerank、fact card 生成、citation binding、prompt、模型配置或数据库 schema。代码和配置均未写入以下内容：

- Q6 id、真实题面、验收资料文件名。
- `tcp-liveness-readiness`、`readinessProbe`、`livenessProbe`、`tcpSocket`、`periodSeconds`。
- `goproxy`、`registry.k8s`、`8080`。
- Kubernetes / k8s 专属判断。
- 当前资料的具体答案片段。

排序能力仍只依赖通用结构化路径信号：问题字段语义映射到 leaf key 后，优先选择同父级路径中 leaf key 匹配的 structured value；同父级 sibling 数值字段因 leaf key 不匹配被降权。

## 测试覆盖

- `shouldLoadTerminalFieldAliasFromSemanticConfiguration`：验证默认规则从 `lattice-query-semantic.yml` 读取中文 alias。
- `shouldPreferTerminalFieldMatchingQuestionOverSiblingNumericField`：通过注入通用 alias map 验证中文字段语义映射到 leaf `port` 后，抽象 fixture `service.listener.port = 9091` 优先于 sibling `service.listener.refreshSeconds = 10`。
- `shouldKeepEnglishTerminalFieldWhenAliasRulesAreEmpty`：注入空 alias 规则，验证中文字段不再被 Java 硬编码识别，同时英文 `port` token 仍可正常选择 `service.listener.port = 9091`。
- `shouldKeepStructuredEndpointUrlWhenTerminalFieldMatchesEndpointIntent`：endpoint / URL 行为不回归。
- `shouldKeepStructuredImageWhenTerminalFieldMatchesImageIntent` 与 `shouldKeepStructuredVersionWhenTerminalFieldMatchesVersionIntent`：image / version 行为不回归。
- `shouldKeepPlainNumericAssignmentFallbackWithoutStructuredPathMetadata`：普通无结构化路径 exact lookup 行为不回归。

测试 fixture 只使用抽象数据：`service.listener.port = 9091`、`service.listener.refreshSeconds = 10`、`service.endpoint.url = https://example.test/api`、`runtime.image = registry.example/app:1.2.3`、`runtime.version = 1.2.3`。未复刻 Q6 真实资料、字段、文件名、端口值或题面。

## Redline 结果

- `git diff --check`：通过。
- `bash scripts/scan-redline.sh special_cases_report.md`：通过，退出码 0。
- `special_cases_report.md` 汇总：`BLOCKER=0`、`REVIEW=2028`、`ALLOWLIST=259`、总命中 `2287`。

## 禁词扫描结果

命令：

```bash
rg -n '"端口"|端口|readinessProbe|tcpSocket|tcp-liveness-readiness|8080|registry\.k8s|goproxy' src/main/java/com/xbk/lattice/query src/main/resources
```

结果：

- `src/main/resources/config/lattice-query-semantic.yml:81` 命中 `"端口"`，这是本轮允许的通用 alias 配置。
- `src/main/resources/db/schema.sql:46` 命中既有注释 `端口等`，不是 Query Java 主链逻辑，也不是本轮新增语义映射。

生产 Java query 主链无上述禁词命中。

## mvn test 结果

- `mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -Dtest=AnswerGenerationServiceTests test`
  - 结果：通过，`Tests run: 77, Failures: 0, Errors: 0, Skipped: 0`。
- `mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -Dtest=AnswerFallbackEvidenceSelectorTests,FactCardGenerationServiceTests test`
  - 结果：通过，`Tests run: 27, Failures: 0, Errors: 0, Skipped: 0`。
- `mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test`
  - 结果：通过，`Tests run: 915, Failures: 0, Errors: 0, Skipped: 0`。

## 剩余风险

- 本轮只做代码层和本地测试验证，未做真实 Q6 API 端到端复验；Q6 不能在本报告中标记完成。
- 后续如扩展中文 terminal field alias，必须继续走配置化、可审计、通用语言信号，不能在 Java 主链加入中文字段词表、业务域词表、资料词表或题集词表。
- citation coverage 仍不能替代 Answer Accuracy；agentD 需要确认最终 claim 与 citation 是否真实支撑目标 leaf 字段事实。
- 配置文件中的 alias 需要人工审计保持短小通用，避免演变成业务词表或 eval 词表。

## 下一步

交给 agentD 做全面回归，不得只回归 Q6。agentD 需要复验 redline、全量测试、真实 API 端到端、baseline/业务 eval 风险，并确认答案 claim 与 citation 是否真实支撑目标字段事实。
