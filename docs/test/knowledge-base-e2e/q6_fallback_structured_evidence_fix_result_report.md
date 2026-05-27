# Q6 Fallback Structured Evidence Fix Result Report

更新时间：2026-05-26

## 1. 修改前根因

Q6 失败不是资料缺失、fact card 生成缺失、检索召回缺失或模型配置问题。前序验证显示：干净库已导入完整知识库验收资料，目标 YAML 的 path-aware fact card 已生成并被检索召回；失败发生在 Answer deterministic fallback 组装答案阶段。

具体表现是：`DETERMINISTIC_EXACT_LOOKUP_PREFERRED` fallback 没有优先消费已召回 fact card 中携带 `fieldPath` / 路径字段 / 取值的结构化证据，而是从同一 YAML 片段中选中了无关机器标识符行，导致答案 grounding 偏离问题焦点。

## 2. 修改文件

- `src/main/java/com/xbk/lattice/query/service/AnswerGenerationFallbackSnippetSelectionSupport.java`
- `src/main/java/com/xbk/lattice/query/service/AnswerGenerationFallbackConclusionSupport.java`
- `src/test/java/com/xbk/lattice/query/service/AnswerGenerationServiceTests.java`

## 3. 通用修复点

- fallback snippet scoring 新增通用结构化路径取值候选识别：当候选来自 path-aware 元数据字段，并同时包含结构化字段路径和值形态时，提升其优先级。
- 结构化路径候选必须覆盖问题中的字段焦点 token，避免仅因存在路径形态就抢占答案。
- 对结构化查值问题中不覆盖字段焦点、且仅具备机器标识符形态的行做轻量降权，防止无关镜像、URL、ID 类行压过更贴题的结构化字段证据。
- exact path fallback 也接收贴合问题焦点的结构化字段路径取值行，避免精确文件/路径问题只认可 URL 或 slash path 行。

## 4. 为什么不是 case 特判

本轮规则只使用通用信号：结构化路径元数据字段、字段路径形态、赋值形态、问题 token 与路径/文本 token 的重叠，以及机器标识符形态的通用降权。

生产代码没有根据具体文件名、字段名、端口值、题号、题面、Kubernetes 概念或当前验收资料做分支、白名单、boost、模板或 fallback 文案。

## 5. 生产代码红线自查结果

- 本轮修改的生产代码未出现 Q6 文件名。
- 本轮修改的生产代码未出现 Q6 字段名。
- 本轮修改的生产代码未出现 Q6 端口值。
- 本轮修改的生产代码未出现 Q6 题号或题面。
- 对本轮修改的生产文件执行禁词扫描，结果为空。
- 对全量 `src/main/java` 与 `src/main/resources` 执行禁词扫描时，仅发现两个既存 CLI help 中的通用 `localhost:8080` 示例；该内容不是本轮改动，也不是 Q6 绑定逻辑。

## 6. Redline 结果

- 前置 redline：`BLOCKER=0 / REVIEW=1977 / ALLOWLIST=259`
- 修复后 redline：`BLOCKER=0 / REVIEW=1996 / ALLOWLIST=259`

## 7. 测试结果

- `mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -Dtest=AnswerGenerationServiceTests test`
  - 结果：`Tests run: 67, Failures: 0, Errors: 0, Skipped: 0`
- `mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -Dtest=FactCardGenerationServiceTests test`
  - 结果：`Tests run: 21, Failures: 0, Errors: 0, Skipped: 0`
- `mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test`
  - 结果：`Tests run: 902, Failures: 0, Errors: 0, Skipped: 0`
- `git diff --check`
  - 结果：通过，无 whitespace error。

## 8. 是否跑了端到端 Q6

本轮未跑端到端 Q6。原因是本轮职责限定为 agentA 最小代码修复与通用测试验证；端到端 Q6 更适合交由 agentD 在代码修复完成后独立复验，避免把代码修复与业务 eval 归因混在同一轮。

## 9. 未覆盖风险

- 新增测试覆盖了通用 YAML/结构化 fact card 中“字段路径取值行 vs 机器标识符行”的 fallback 选择，但未覆盖所有 JSON/properties/XML 变体。
- 本轮只改变已召回证据的 fallback snippet/evidence selection，不保证检索未召回、fact card 未生成或字段路径缺失的场景。
- 端到端 Q6 尚未由独立验证 agent 复验，仍需真实 API 路径确认 fallback 输出是否已从无关行切回结构化字段路径事实。

## 10. 下一步是否需要 agentD 复验

需要。建议 agentD 执行独立复验：

- 重新运行 redline；
- 运行相关 AnswerGeneration fallback 测试与 `FactCardGenerationServiceTests`；
- 在干净知识库验收库上跑端到端 Q6；
- 输出独立 verification report，确认 API 最终答案与 citation 是否由 path-aware fact card / source chunk 支撑。
