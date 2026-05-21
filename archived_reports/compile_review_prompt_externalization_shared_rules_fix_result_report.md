# compile review prompt shared rules 接入修复结果报告

## 1. 修改了哪些文件

- `src/main/resources/prompts/compiler/writer.md`
  - 将文件头部重复内联的 shared grounding rules 替换为 `{{shared-grounding-rules}}`。
- `src/main/resources/prompts/compiler/writer-image.md`
  - 将文件头部重复内联的 shared grounding rules 替换为 `{{shared-grounding-rules}}`。
- `src/main/resources/prompts/compiler/reviewer.md`
  - 将文件头部重复内联的 shared grounding rules 替换为 `{{shared-grounding-rules}}`。
- `src/main/resources/prompts/compiler/reviewer-image.md`
  - 将文件头部重复内联的 shared grounding rules 替换为 `{{shared-grounding-rules}}`。
- `src/main/java/com/xbk/lattice/compiler/prompt/CompilerPromptProvider.java`
  - 保持默认 classpath 加载行为不变。
  - 增加包内可见的测试构造入口与 `PromptResourceLoader`，用于覆盖缺失/空 prompt fail-fast。
- `src/test/java/com/xbk/lattice/compiler/prompt/CompilerPromptProviderTests.java`
  - 补充四个 role prompt 均包含 shared grounding rules 内容且无未解析 `{{` 的断言。
  - 补充缺失 prompt / 空 prompt 的 fail-fast 断言。
- `special_cases_report.md`
  - 由 redline 扫描刷新。

## 2. 是否只做 shared rules 占位符接入

是。

本轮仅把四个 role prompt 的重复内联 shared grounding rules 收敛为 `{{shared-grounding-rules}}`，并补充 provider 加载测试。没有改 compile review loop、persist gate、query、answer、citation 或 baseline / eval 脚本。

## 3. 最终四个 role prompt 是否都引用 `{{shared-grounding-rules}}`

是。

确认命令：

```bash
rg -n "\\{\\{shared-grounding-rules\\}\\}" src/main/resources/prompts/compiler
```

命中：

- `writer.md`
- `writer-image.md`
- `reviewer.md`
- `reviewer-image.md`

`TRUTH LEVEL ANNOTATIONS` 只保留在 `shared-grounding-rules.md` 中。

## 4. provider 输出是否无未解析占位符

是。

`CompilerPromptProviderTests` 已覆盖：

- `writerPrompt`
- `writerImagePrompt`
- `reviewerPrompt`
- `reviewerImagePrompt`
- `fixerPrompt`

上述输出均不包含未解析 `{{`。四个 role prompt 均包含 shared grounding rules 内容。

## 5. 是否改 prompt 语义

否。

本轮只把相同 shared rules 文本从 role prompt 内联改为 provider 注入，规则含义保持等价。

## 6. 是否新增业务硬编码

否。

没有新增业务词、文档名、case id、expected answer 或 hidden eval 内容。

## 7. redline BLOCKER 是否为 0

是。

- 命令：`bash scripts/scan-redline.sh special_cases_report.md`
- BLOCKER：0
- REVIEW：1859
- ALLOWLIST：242

## 8. CompilerPromptProviderTests 是否通过

通过。

- 命令：`mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -Dtest=CompilerPromptProviderTests test`
- 结果：`Tests run: 13, Failures: 0, Errors: 0, Skipped: 0`

## 9. 全量 mvn test 是否为 822 / 0 / 0

不是，实际为 `824 / 0 / 0`。

- 命令：`mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test`
- 结果：`Tests run: 824, Failures: 0, Errors: 0, Skipped: 0`
- 说明：全量测试通过，但当前测试总数与任务中预期的 `822 / 0 / 0` 不一致，应以当前 surefire 汇总为准。

## 10. Anthropic flaky error 结果

本轮全量测试中 `AnthropicMessageApiLlmClientTests` 通过，未复现 flaky error，因此未执行额外两次重跑。
