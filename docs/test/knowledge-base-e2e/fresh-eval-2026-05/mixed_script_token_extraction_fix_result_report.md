# Mixed Script Token Extraction Fix Result Report

## 1. 修改文件

- `src/main/java/com/xbk/lattice/query/service/QueryTokenExtractor.java`
- `src/main/java/com/xbk/lattice/infra/persistence/LexicalSearchTokenBudget.java`
- `src/test/java/com/xbk/lattice/query/service/QueryTokenExtractorTests.java`
- `src/test/java/com/xbk/lattice/infra/persistence/LexicalSearchTokenBudgetTests.java`
- `docs/test/knowledge-base-e2e/fresh-eval-2026-05/mixed_script_token_extraction_fix_result_report.md`

补充说明：按要求执行 `bash scripts/scan-redline.sh special_cases_report.md`，该命令会更新 `special_cases_report.md` 作为扫描输出；本轮未手工修改脚本、allowlist 或该报告内容。

最小 diff 摘要：

- `QueryTokenExtractor.extract(String question)` 在既有 path/config、camel、ASCII、number、Han n-gram 提取之后，追加原文混合脚本 segment 提取。
- `QueryTokenExtractor` 在连续 mixed token 提取基础上，补充纯空白分隔的短 ASCII/数字 + 短 Han 相邻片段合并。
- `LexicalSearchTokenBudget` 给 Han + ASCII Latin/数字的混合脚本 token 正分，避免长度为 2 的混合 token 在 LIKE token 预算中被打成 0 分。
- 单元测试覆盖混合脚本提取、纯 CJK/纯 ASCII 既有行为保留、单字符不被放宽，以及混合短 token 可进入 LIKE tokens。

## 2. 根因说明

FS4b “B级” 搜索 0 结果的本轮根因是 query token 入口缺口，不是资料缺失，也不是 S2 / FS2 所属的 title/anchor 或 ranking 问题。

既有 `QueryTokenExtractor` 只覆盖 ASCII 2+、数字、路径/配置键、Han 2+ n-gram 等规则。对 Latin/数字 + CJK 的短混合脚本片段，原流程容易只留下单 Latin 或单 Han，而单字符不会作为高信号 token 进入检索 token 集合，导致 lexical/LIKE 路径没有可消费 token。

同时，`LexicalSearchTokenBudget` 旧评分对长度为 2 的非纯 ASCII、非纯 CJK 混合 token 可能给 0 分；即使 extractor 新增了混合 token，也可能无法进入 selected LIKE tokens。因此本轮同步补了混合脚本 token 的通用预算正分。

## 3. 通用修复规则

`QueryTokenExtractor` 新增规则：

- 从原始 question 按空白和 Unicode 标点切分 segment。
- 对每个 segment 判断：
  - 长度 >= 2；
  - 至少包含一个 Latin 字母或数字；
  - 至少包含一个 Han 字符；
  - 不是纯空白或纯标点切分残留。
- 满足条件后转小写加入 token 集合。
- 使用 `Character.UnicodeScript.HAN` / `LATIN` 和 `Character.isDigit` 做 Unicode 层面的通用判断。
- 未修改既有 `ASCII_TOKEN_PATTERN`、`HAN_TEXT_PATTERN` 及其最小长度规则。

`LexicalSearchTokenBudget` 新增规则：

- 对同时包含 Han 与 ASCII Latin/数字的 token 给正分。
- 不放宽单 Latin 或单 Han。
- 不改变纯 CJK、纯 ASCII、版本号、路径、配置键等既有 token 行为。

## 4. 为什么不是 case 特判

生产代码没有写入题号、业务词、文档名、文件名、答案片段或 eval 样例字符串；没有绑定特定问法或特定资料。

本轮只基于 Unicode script、数字、空白/标点切分和长度这类文本结构信号实现通用规则。该规则同等适用于任意 Han + Latin/数字短混合脚本片段，不依赖具体业务域。

## 5. 测试结果

针对性测试：

- `mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -Dtest=QueryTokenExtractorTests test`
  - `Tests run: 12, Failures: 0, Errors: 0, Skipped: 0`
- `mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -Dtest=LexicalSearchTokenBudgetTests test`
  - `Tests run: 7, Failures: 0, Errors: 0, Skipped: 0`

覆盖点：

- Latin + Han 组合能被提取。
- 数字 + Han 组合能被提取。
- 纯 CJK 既有 Han n-gram 行为保留。
- 纯 ASCII 既有长度规则保留。
- 单 Latin / 单 CJK 不被单独放宽。
- 空白分隔的短 Latin + Han、数字 + Han 相邻片段可合并为 mixed token。
- 混合脚本短 token 能进入 selected LIKE tokens。

## 6. redline 结果

命令：

```bash
bash scripts/scan-redline.sh special_cases_report.md
```

结果：

- exit code: 0
- `BLOCKER: 0`
- `REVIEW: 2096`
- `ALLOWLIST: 262`
- `总命中: 2358`

本轮没有修改 `scripts/**`、redline allowlist 或扫描规则。

## 7. mvn test 结果

命令：

```bash
mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test
```

结果：

- `Tests run: 1004, Failures: 0, Errors: 0, Skipped: 0`
- `BUILD SUCCESS`
- Finished at: `2026-06-05T08:47:25+08:00`

## 8. 补充修订：支持空白分隔 mixed token

### 8.1 为什么需要修订

上一轮连续 mixed token 修复已覆盖 Latin/数字 + Han 连续片段，但 fresh eval 实际搜索词可能在两种脚本之间带空白。此时原始 query 会被切成单 Latin/数字片段与单 Han 片段；单独 `B` 或单独 `级` 仍按设计不作为高信号 token，导致 lexical/LIKE 路径仍可能没有可消费的 mixed token。

该缺口仍属于 `QueryTokenExtractor` 的通用 token 提取问题，不是资料缺失，也不是 S2 title/anchor 或 FS2 ranking 问题。

### 8.2 新增规则

在原有连续 mixed segment 提取之后，新增保守的相邻短片段合并：

- 只合并相邻 segment，且两个 segment 中间必须只有空白。
- 左侧 segment 必须是短 ASCII/数字片段，当前上限为 4 个 code point，且只包含 Latin 或数字。
- 右侧 segment 必须是短 Han 片段，当前上限为 2 个 code point，且只包含 Han。
- 合并后按 `Locale.ROOT` 转小写加入 token 集合。
- 单独 Latin/数字或单独 Han 仍不被放宽为 token。
- 未修改 `ASCII_TOKEN_PATTERN` / `HAN_TEXT_PATTERN` 的最小长度。
- 本次未继续扩大 `LexicalSearchTokenBudget` 逻辑，仅保留上一轮 mixed short token 正分。

示例覆盖：

- `B 级` -> `b级`
- `A 类` -> `a类`
- `2 项` -> `2项`
- 连续 `B级` 仍产生 `b级`

### 8.3 测试结果

针对性测试：

- `mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -Dtest=QueryTokenExtractorTests test`
  - `Tests run: 12, Failures: 0, Errors: 0, Skipped: 0`
- `mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -Dtest=LexicalSearchTokenBudgetTests test`
  - `Tests run: 7, Failures: 0, Errors: 0, Skipped: 0`

新增/保留覆盖：

- 单独 `B` 不产生 `b`。
- 单独 `级` 不产生 `级`。
- `B 级` 产生 `b级`。
- `A 类` 产生 `a类`。
- `2 项` 产生 `2项`。
- 连续 `B级` 仍产生 `b级`。
- 纯 CJK、纯 ASCII 既有行为不变。
- `LexicalSearchTokenBudget` 混合短 token 入 selected LIKE tokens 测试保留。

### 8.4 redline / mvn test 结果

redline：

- 命令：`bash scripts/scan-redline.sh special_cases_report.md`
- exit code: 0
- `BLOCKER: 0`
- `REVIEW: 2096`
- `ALLOWLIST: 262`
- `总命中: 2358`

全量测试：

- 命令：`mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test`
- `Tests run: 1004, Failures: 0, Errors: 0, Skipped: 0`
- `BUILD SUCCESS`
- Finished at: `2026-06-05T08:47:25+08:00`

本轮未清库、未重建 schema、未运行 runtime gate / Public Eval。

### 8.5 仍未处理 S2 / FS2

- S2 title/anchor 仍为独立问题，本轮未处理。
- FS2 ranking 仍为独立问题，本轮未处理。

## 9. 剩余未处理项

- S2 title/anchor 仍为独立问题，本轮未处理。
- FS2 ranking 仍为独立问题，本轮未处理。
- 本轮未清库、未重建 schema、未运行 runtime gate / Public Eval，避免扩大归因变量。

## 10. 下一步交给 agentD 的验证建议

- 清库导入 Public Eval 2 后验证搜索 “B级” 与 “B 级” 结果数 `> 0`。
- 回归 FS1-FS4。
- 确认纯 CJK 搜索如“精密仪器”、普通中文长词搜索如“化学品分类存储”无回归。
