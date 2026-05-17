# Oh My Codex 多 Agent 调度操作手册

更新时间：2026-05-17

本手册用于把 Oh My Codex（OMX）接入当前项目，形成“只指挥架构师，架构师再分派 agentA/B/C/D”的工作方式。OMX 负责 team runtime、tmux/worktree、状态、并行 worker 与混合 CLI；项目规则仍以 `AGENTS.md`、`docs/quality-progress-and-lessons.md`、`docs/multi-agent-model-routing-guide.md` 和指定计划文件为准。

## 结论

推荐使用 OMX 作为当前项目的多 Agent 会议室：

```text
用户
  -> 架构师 agent（Codex）
  -> OMX team workers
     -> agentA：代码执行（Codex）
     -> agentB：治理/链路分析（Codex；简单归类可 Claude）
     -> agentC：文档/报告治理（Claude）
     -> agentD：验证/测试（Claude；复杂归因升级 Codex）
  -> 架构师复核 gate
  -> 用户只看结论和关键决策
```

不要把 OMX 当成自动懂项目红线的平台。OMX 是运行时和调度工具；当前项目的红线、模型路由、计划回写和验收顺序必须由架构师提示词强制执行。

## 适用边界

| 场景 | 是否适合 OMX | 使用方式 |
|---|---:|---|
| 架构师读台账并判断下一步 | 适合 | 单入口架构师会话 |
| 多个只读 agent 并行分析 | 适合 | `omx team 2:explore` 或 `omx team N:architect` |
| 单一最小代码修复 | 适合 | 架构师分派 agentA，确保本轮只有一个代码修改者 |
| redline / `mvn test` / eval 验证 | 适合 | 分派 agentD，只跑命令并写验证报告 |
| 文档/报告/台账维护 | 适合 | 分派 agentC，仅允许改文档/报告 |
| Query 主链自由自动调参 | 不适合 | 必须先归因、再授权 agentA 做最小修复 |
| 多 agent 同时改主链 | 禁止 | 违反当前项目不可归因原则 |

## 安装与健康检查

### 1. 安装

```bash
npm install -g @openai/codex oh-my-codex
```

项目要求 Node.js 20+。如果机器已有 Codex CLI，只安装 OMX 也可以：

```bash
npm install -g oh-my-codex
```

### 2. 初始化：只限制在当前项目

当前项目推荐使用 project scope，不使用 user scope。这样 prompts、skills、config、OMX 运行态都落在当前仓库，不写入全局 `~/.codex`：

```bash
cd /Users/sxie/xbk/Lattice-java
omx setup --scope project --dry-run
```

确认 dry-run 输出只会写当前项目下的 `.codex/`、`.omx/` 和必要项目文件后，再执行：

```bash
omx setup --scope project --merge-agents
```

注意：

- 不要直接运行裸 `omx setup`，避免误写全局配置。
- 不要使用 `omx setup --scope user`，除非明确要影响所有项目。
- 当前项目已有强约束 `AGENTS.md`，必须使用 `--merge-agents`，不要用 `--force` 覆盖。
- 如果 shell 中已经设置了全局 `CODEX_HOME`，先 `unset CODEX_HOME`，避免绕过项目级 `.codex/`。

### 3. 检查

```bash
omx doctor
codex login status
omx exec --skip-git-repo-check -C /Users/sxie/xbk/Lattice-java "Reply with exactly OMX-EXEC-OK"
```

通过标准：

- `omx doctor` 无阻断问题。
- `codex login status` 显示已登录。
- `omx exec` 能返回 `OMX-EXEC-OK`。

如果使用 project scope 后 Codex 登录状态丢失，说明当前项目级 `CODEX_HOME=./.codex` 下还没有认证。只在当前项目会话里登录，不要改全局：

```bash
cd /Users/sxie/xbk/Lattice-java
CODEX_HOME="$PWD/.codex" codex login
CODEX_HOME="$PWD/.codex" codex login status
```

### 4. 推荐启动

```bash
cd /Users/sxie/xbk/Lattice-java
unset CODEX_HOME
omx --madmax --high
```

如果不想让 OMX 管 tmux/HUD，只想开一次普通会话：

```bash
omx --direct --yolo
```

## 项目隔离策略

只在当前项目使用 OMX 时，遵守以下规则：

| 项 | 推荐做法 | 禁止/谨慎 |
|---|---|---|
| 安装包 | `npm install -g oh-my-codex` 只安装 `omx` 命令 | 不代表要运行全局 setup |
| setup scope | `omx setup --scope project --merge-agents` | 不跑裸 `omx setup`，不跑 `--scope user` |
| Codex home | 让 project scope 使用 `./.codex` | 不在 shell rc 里长期写 `CODEX_HOME` |
| OMX 状态 | 使用当前项目 `./.omx` | 不把 `.omx` 当成其他项目共享状态 |
| 环境变量 | 在当前终端临时 `export OMX_TEAM_WORKER_CLI_MAP=...` | 不写进 `~/.zshrc` |
| Git | `.codex/`、`.omx/` 默认按本地运行态处理 | 不提交认证文件、运行日志、worker 状态 |

建议在项目 `.gitignore` 中确认以下目录不会被误提交：

```gitignore
.codex/
.omx/
```

如果后续需要提交某些项目级 OMX 文档，只单独 allowlist 文档文件，不提交 auth、logs、state。

## 执行后端与角色映射

当前项目只区分两类执行后端：`Codex` 和 `Claude`。

| 项目角色 | 运行后端 | 默认职责 | 是否允许改生产代码 |
|---|---|---|---:|
| 项目架构师 / 质量推进顾问 | Codex | 审报告、拆任务、选择下一步、派发任务、复核 gate | 否 |
| agentA：代码执行 | Codex | 只按明确任务做一个最小代码修复 | 是 |
| agentB：治理 / 链路分析 | Codex，低风险可 Claude | 只读分析、设计报告、风险判断 | 否 |
| agentC：文档 / 报告治理 | Claude | 清理报告、维护台账、整理文档 | 否，除文档/报告 |
| agentD：验证 / 测试 | Claude，复杂归因升级 Codex | 跑 redline、`mvn test`、baseline、SWIP eval 并输出报告 | 否，除验证报告 |

OMX 的 mixed CLI team 可通过环境变量配置：

```bash
export OMX_TEAM_WORKER_CLI_MAP=codex,claude,claude
```

含义：

- worker 1 使用 Codex，适合 agentA 或高风险 agentB。
- worker 2 使用 Claude，适合 agentC。
- worker 3 使用 Claude，适合 agentD。

如果本轮只需要验证/文档：

```bash
export OMX_TEAM_WORKER_CLI_MAP=claude,claude
```

如果本轮涉及 Query / Answer / Retrieval / Citation / Compiler 主链代码修复：

```bash
export OMX_TEAM_WORKER_CLI_MAP=codex
```

并且只能启动一个代码执行 worker。

## 日常使用方式

### 方式 A：只让架构师判断下一步，不改代码

在 OMX 会话里输入：

```text
/prompts:architect "读取 AGENTS.md、docs/quality-progress-and-lessons.md、docs/multi-agent-model-routing-guide.md。不要改代码。请判断当前 gate、当前禁止事项、下一条最小主线，并给出是否需要分派 agentB/agentC/agentD 的任务包。"
```

适用场景：

- 新会话恢复。
- 不确定是否能继续改代码。
- 刚跑完一轮 eval 或修复报告，需要架构师复核。

### 方式 B：让架构师继续推进

在 OMX 会话里输入：

```text
继续。先读取 AGENTS.md、docs/quality-progress-and-lessons.md、docs/multi-agent-model-routing-guide.md；如本轮引用 docs/**/plans/*.md，先读取计划文件并以计划文件作为唯一进度台账。只选择一个最小下一步。需要分派 agent 时，按项目职责分配 agentA/B/C/D，并明确模型、允许范围、禁止范围、是否允许跑测试、输出报告名。未满足 redline / mvn / baseline gate 时不得跳步。
```

架构师应自动完成：

1. 读取项目规则。
2. 判断当前 gate。
3. 选择一个主变量。
4. 决定是否启动 team worker。
5. 生成任务包并分派。
6. 收集报告。
7. 更新台账或要求 agentC 更新台账。
8. 输出本轮结论和下一步。

### 方式 C：明确让架构师启动 team worker

如果架构师已经给出任务包，可以从终端启动：

```bash
cd /Users/sxie/xbk/Lattice-java
export OMX_TEAM_WORKER_CLI_MAP=codex,claude,claude
omx team 3:executor "按架构师任务包执行。worker1 扮演 agentA，仅在任务包明确授权时改代码；worker2 扮演 agentC，仅改文档/报告；worker3 扮演 agentD，仅跑验证命令并写报告。所有 worker 必须先读取 AGENTS.md、docs/quality-progress-and-lessons.md、docs/multi-agent-model-routing-guide.md。"
```

如果只需要只读分析：

```bash
export OMX_TEAM_WORKER_CLI_MAP=codex,claude
omx team 2:explore "只读分析当前质量台账与下一步候选，不得改代码，不得跑会扰动数据库的 eval。输出 *_analysis_report.md。"
```

如果只需要验证：

```bash
export OMX_TEAM_WORKER_CLI_MAP=claude
omx team 1:executor "作为 agentD 运行验证。先跑 bash scripts/scan-redline.sh special_cases_report.md；BLOCKER=0 后再按任务包决定是否运行 mvn test。不得修改生产代码。输出 *_verification_report.md。"
```

### 方式 D：使用 `$ralplan` 先达成架构共识

高风险修改前使用：

```text
$ralplan "读取 AGENTS.md、docs/quality-progress-and-lessons.md、docs/multi-agent-model-routing-guide.md。针对当前下一步只做方案评审，不改代码。涉及 Query/Answer/Retrieval/Citation/Compiler 主链时，必须先说明失败类型、通用修复点、为什么不是 case 特判、影响哪些测试、如何验证。"
```

通过后再让架构师分派 agentA。

## 架构师单入口提示词

把下面内容作为架构师会话固定开场。后续你只需要说“继续”。

```text
你是本项目唯一调度入口：项目架构师 / 质量推进顾问。

开始任何动作前必须读取：
1. AGENTS.md
2. docs/quality-progress-and-lessons.md
3. docs/multi-agent-model-routing-guide.md
4. 如果用户提到 plan/checklist/继续上次进度，必须读取对应 docs/**/plans/*.md，并以计划文件作为唯一进度台账。

职责：
1. 判断当前项目阶段、gate 状态、禁止事项和下一步。
2. 每轮只允许一个主变量。
3. 需要分派 agent 时，生成任务包并通过 OMX team 或明确 worker 指令分派。
4. 分派时必须写清：agent、执行后端、允许修改范围、禁止范围、是否允许跑测试、是否允许清库/重建、输出报告名。
5. agentA 是唯一代码执行者；同一轮只能有一个 agentA 修改生产代码。
6. agentB 只读分析；agentC 只改文档/报告；agentD 只跑验证并输出报告。
7. Claude 产出的分析不能直接触发高危代码修改，必须经架构师复核。

硬规则：
- redline BLOCKER > 0：所有 agent 停止准确率、baseline、eval 和普通测试修复；下一步只处理 redline BLOCKER。
- mvn test 编译失败：停止 baseline / eval；下一步只处理编译失败。
- baseline 或业务 eval 下降：先判断是否由本轮唯一改动造成，不得扩大修改。
- 当前数据库若是 SWIP clean 库，不得跑主 baseline。
- Query/Answer/Retrieval/Rerank/Citation/Compiler 主链修复，必须先输出失败归因报告；未归因前不得修改 src/main/java。
- 禁止为具体业务域、文档名、文件名、术语、问题问法、样例答案写硬编码、白名单、兜底模板或 case 特判。
- 默认不准修改 src/test/java，除非用户明确确认测试断言过期。
- 默认不准修改 scripts/scan-redline.sh、AGENTS.md、CLAUDE.md、redline allowlist。
- 不自动提交代码。

你每次收到“继续”时：
1. 先复述当前 gate 和禁止事项。
2. 选择一个最小下一步。
3. 如果需要 worker，直接给出可执行的 OMX team 命令或在当前工具环境中启动对应 agent。
4. 等 worker 报告后复核结论。
5. 如 gate、下一步计划、踩坑结论或 agent 分工发生变化，安排 agentC 更新 docs/quality-progress-and-lessons.md；如果有计划文件，按计划文件随做随回写。
6. 最终只向用户汇报：本轮做了什么、产物在哪里、gate 是否通过、下一步是什么。
```

## Agent 任务包模板

### agentA：代码执行

```text
你是 agentA：代码执行 Agent。

执行后端：Codex。
是否允许改代码：是，但本轮只允许一个最小修复点。

开始前必须读取：
1. AGENTS.md
2. docs/quality-progress-and-lessons.md
3. docs/multi-agent-model-routing-guide.md
4. 架构师提供的失败归因/设计报告

本轮目标：
【由架构师填写一个明确根因和一个通用修复点】

允许修改：
【列出精确文件或目录】

禁止修改：
- src/test/java/**，除非用户明确确认测试断言过期
- scripts/scan-redline.sh
- AGENTS.md / CLAUDE.md
- redline allowlist
- 与本轮根因无关的 Query/prompt/fallback/citation/runner/题集/模型配置

验证要求：
【由架构师填写，例如只编译、只跑指定测试、或交给 agentD 验证】

输出：
- 写入 `*_fix_result_report.md`
- 报告必须包含：修改文件、根因、通用修复点、为什么不是 case 特判、验证命令与结果、残余风险
```

### agentB：治理 / 链路分析

```text
你是 agentB：治理 / 链路分析 Agent。

执行后端：默认 Codex；普通日志归类可 Claude。
是否允许改代码：否。

开始前必须读取：
1. AGENTS.md
2. docs/quality-progress-and-lessons.md
3. docs/multi-agent-model-routing-guide.md

本轮目标：
【由架构师填写分析问题】

禁止事项：
- 不修改 src/main/java/**
- 不修改 src/test/java/**
- 不修改 src/main/resources/**
- 不修改 scripts/**
- 不跑会扰动数据库状态的 eval，除非架构师明确授权

输出：
- 写入 `*_analysis_report.md` 或 `*_design_report.md`
- 报告必须包含：证据、失败类型、候选根因、推荐最小下一步、不建议做什么
```

### agentC：文档 / 报告治理

```text
你是 agentC：文档 / 报告治理 Agent。

执行后端：Claude。
是否允许改代码：否。

允许修改：
- docs/**
- 根目录报告类 Markdown

禁止修改：
- src/**
- scripts/**
- 配置文件
- 仍可能被当前修复引用的报告

本轮目标：
【由架构师填写台账更新或报告整理任务】

输出：
- 写入 `*_status.md`、`*_cleanup_report.md` 或直接更新指定台账
- 如果更新 docs/quality-progress-and-lessons.md，必须记录 gate、下一步计划、踩坑结论或 agent 分工变化
- 如果涉及计划文件，必须按计划文件逐项回写状态
```

### agentD：验证 / 测试

```text
你是 agentD：验证 / 测试 Agent。

执行后端：Claude；复杂回归解释升级 Codex。
是否允许改代码：否，除验证报告。

开始前必须读取：
1. AGENTS.md
2. docs/quality-progress-and-lessons.md
3. docs/multi-agent-model-routing-guide.md
4. 架构师提供的验证范围

默认验证顺序：
1. `bash scripts/scan-redline.sh special_cases_report.md`
2. 仅当 redline `BLOCKER=0` 时，运行架构师授权的 `mvn test` 或指定测试
3. 仅当 `mvn test` 通过且数据库前提正确时，运行 baseline / SWIP eval

禁止事项：
- 不修改 src/**
- 不修改 eval 题集
- 不清库、不重建数据库，除非架构师明确授权
- 当前数据库为 SWIP clean 库时，不跑主 baseline

输出：
- 写入 `*_verification_report.md` 或 `*_gate_report.md`
- 报告必须包含：命令、结果、失败摘要、是否阻断下一步、需要升级给架构师判断的风险
```

## 推荐工作流

### 新会话恢复

```text
继续。先不要改代码。读取 AGENTS.md、docs/quality-progress-and-lessons.md、docs/multi-agent-model-routing-guide.md，并判断当前 gate、禁止事项和唯一下一步。
```

架构师输出后，如果下一步只是分析：

```bash
export OMX_TEAM_WORKER_CLI_MAP=codex
omx team 1:explore "作为 agentB 只读分析架构师指定问题，不得改代码，输出 *_analysis_report.md。"
```

### 质量打磨主线

```text
继续推进质量打磨。必须按 redline -> mvn test -> baseline/eval 的顺序判断，不得跳步。每轮只允许一个主变量。
```

推荐顺序：

1. 架构师选主线。
2. agentB 只读归因。
3. 架构师复核归因。
4. agentA 做最小修复。
5. agentD 验证。
6. 架构师复核。
7. agentC 更新台账。

### Query / Answer / Citation 主链修复

```text
继续处理 Query 主链问题，但本轮禁止直接改代码。先让 agentB 输出失败归因报告，必须归入资料缺失、编译抽取缺失、chunk 切分问题、检索未召回、rerank 排序低、证据已召回但回答漏点、引用错误、应拒答但编造、多证据冲突未处理之一。
```

只有架构师确认归因后，才允许：

```bash
export OMX_TEAM_WORKER_CLI_MAP=codex
omx team 1:executor "作为 agentA 执行架构师批准的一个最小通用修复。禁止 case 特判。输出 *_fix_result_report.md。"
```

### 验证门禁

```bash
export OMX_TEAM_WORKER_CLI_MAP=claude
omx team 1:executor "作为 agentD 验证上一轮修复。先跑 redline；BLOCKER=0 后按架构师授权运行 mvn test。不得改代码，输出 *_gate_report.md。"
```

### 文档和台账回写

```bash
export OMX_TEAM_WORKER_CLI_MAP=claude
omx team 1:executor "作为 agentC 更新 docs/quality-progress-and-lessons.md。只根据架构师复核结论更新当前 gate、下一步计划、踩坑结论或 agent 分工，不得改 src/**。"
```

如果用户指定 `docs/**/plans/*.md`，agentC 必须逐项回写：

- `已完成`：必须已有验证结果。
- `进行中`：写明做到哪。
- `阻塞`：写明卡在哪。
- `待验证`：写明下一步验证命令。

## 自动分派的实现方式

OMX 可以启动 worker，但“谁该干什么”必须由架构师决定。推荐让架构师按下面流程自动分派：

```text
收到“继续”
  -> 读取项目规则和台账
  -> 判断 gate
  -> 选择一个主变量
  -> 生成任务包
  -> 选择 worker CLI map
  -> 启动 OMX team
  -> 等待 worker 报告
  -> 复核 gate 与报告
  -> 安排台账回写
  -> 汇报用户
```

架构师启动 worker 时应选择最小并行度：

| 下一步 | worker 数 | CLI map | 命令形态 |
|---|---:|---|---|
| 只读分析 | 1-2 | `codex` 或 `codex,claude` | `omx team 1:explore` |
| 单点代码修复 | 1 | `codex` | `omx team 1:executor` |
| 验证 | 1 | `claude` | `omx team 1:executor` |
| 文档/台账 | 1 | `claude` | `omx team 1:executor` |
| 分析 + 文档整理 | 2 | `codex,claude` | `omx team 2:explore` |

不要为了“看起来并行”启动多个 executor。当前项目最重要的是可归因，不是并发数量。

## 状态查看与恢复

查看 team：

```bash
omx team status <team-name>
```

恢复 team：

```bash
omx team resume <team-name>
```

关闭 team：

```bash
omx team shutdown <team-name>
```

查看 OMX 状态：

```bash
omx status
```

取消当前执行模式：

```bash
omx cancel
```

OMX 状态通常在 `.omx/state/`，日志在 `.omx/logs/`，计划在 `.omx/plans/`。这些文件是 OMX 运行态，不替代当前项目的质量台账和计划文件。

## 失败处理

| 失败 | 处理 |
|---|---|
| `omx` 命令不存在 | 重新安装 `npm install -g oh-my-codex`，确认 npm global bin 在 `PATH` |
| `omx doctor` 失败 | 按 doctor 输出修复；未通过前不要跑 team |
| Codex 未登录 | `codex login` 后重试 |
| team worker 未启动 | 检查 tmux、`OMX_TEAM_WORKER_CLI_MAP`、CLI 登录状态 |
| redline `BLOCKER > 0` | 停止所有 eval/baseline/普通修复，只处理 redline |
| `mvn test` 失败 | 停止 baseline/eval，只处理编译或测试失败 |
| baseline / SWIP eval 下降 | 回到架构师归因，判断是否由本轮唯一改动造成 |
| worker 越权改文件 | 架构师判定本轮失败；不得继续叠加修改；先人工/架构师审 diff |
| Claude 给出高危结论 | 只能作为报告输入，必须由 Codex 架构师复核 |

## 项目内首次接入建议

第一天只做验证，不改业务代码：

1. 安装并运行 `omx doctor`。
2. 启动 `omx --madmax --high`。
3. 输入架构师单入口提示词。
4. 输入“继续，先不要改代码”。
5. 让架构师只输出下一步和任务包。
6. 启动一个只读 worker 做 agentB 分析。
7. 启动一个 agentD 跑 redline。
8. 架构师复核报告。
9. agentC 更新 `docs/quality-progress-and-lessons.md`。

第二天再允许低风险文档/报告自动化。

第三天以后，只有在架构师确认 redline、测试和归因都满足时，才允许 agentA 修改 Java 主链。

## 最小可复制命令组

安装：

```bash
npm install -g @openai/codex oh-my-codex
omx setup --scope user
omx doctor
```

启动架构师：

```bash
cd /Users/sxie/xbk/Lattice-java
omx --madmax --high
```

只读分析 worker：

```bash
export OMX_TEAM_WORKER_CLI_MAP=codex
omx team 1:explore "作为 agentB 只读分析当前项目下一步，不得改代码。必须先读取 AGENTS.md、docs/quality-progress-and-lessons.md、docs/multi-agent-model-routing-guide.md。输出 *_analysis_report.md。"
```

验证 worker：

```bash
export OMX_TEAM_WORKER_CLI_MAP=claude
omx team 1:executor "作为 agentD 跑验证。先执行 bash scripts/scan-redline.sh special_cases_report.md；BLOCKER=0 后等待架构师授权下一步。不得改代码。输出 *_gate_report.md。"
```

代码修复 worker：

```bash
export OMX_TEAM_WORKER_CLI_MAP=codex
omx team 1:executor "作为 agentA 只执行架构师批准的一个最小代码修复。必须遵守 AGENTS.md 和 Query 红线。不得改测试、redline 脚本、allowlist。输出 *_fix_result_report.md。"
```

台账 worker：

```bash
export OMX_TEAM_WORKER_CLI_MAP=claude
omx team 1:executor "作为 agentC 更新质量台账或指定计划文件。不得改 src/**。只有经过验证的事项才能标记完成。"
```

## 参考

- Oh My Codex 文档：https://oh-my-codex.dev/docs.html
- Oh My Codex npm：https://www.npmjs.com/package/oh-my-codex
- Oh My Codex GitHub：https://github.com/Yeachan-Heo/oh-my-codex
- 当前项目模型路由：`docs/multi-agent-model-routing-guide.md`
- 当前项目质量台账：`docs/quality-progress-and-lessons.md`
