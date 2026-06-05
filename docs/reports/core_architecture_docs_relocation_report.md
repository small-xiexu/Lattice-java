# 核心架构文档搬目录收口报告

执行时间：2026-06-05
执行人：agentC（文档/报告治理 Agent）
范围：顶层 `docs/` 下 5 个核心文档迁移至 `docs/核心架构/`

## 1. 处理范围

| # | 旧路径 | 新路径 | 迁移类型 |
|---|---|---|---|
| 1 | `docs/编译流水线.md` | `docs/核心架构/编译流水线.md` | 纯路径移动 |
| 2 | `docs/查询检索流水线.md` | `docs/核心架构/查询检索流水线.md` | 纯路径移动 |
| 3 | `docs/Deep-Research流水线.md` | `docs/核心架构/Deep-Research流水线.md` | 纯路径移动 |
| 4 | `docs/模型中心与执行快照.md` | `docs/核心架构/模型中心与执行快照.md` | 纯路径移动 |
| 5 | `docs/模型绑定配置参考.md` | `docs/核心架构/模型绑定配置参考.md` | 内容有改动（API Key 已脱敏为占位符） |

## 2. 内容对比结果

- 4 个流水线文档（编译、查询检索、Deep-Research、模型中心）：**纯路径移动**，内容与 HEAD 完全一致。
- `模型绑定配置参考.md`：原始 diff 包含 `baseUrl`/`apiKey` 本地化替换；**提交前复核发现 2 组真实 API key（OpenAI `sk-...` + 智谱 `530c...`）共 6 处明文暴露，已全部替换为 `<your-openai-api-key>` / `<your-zhipu-api-key>` 占位符。** 其余配置说明、结构、curl 示例均保持原样。

## 3. 更新了哪些引用

| 文件 | 更新内容 |
|---|---|
| `README.md` | 主线表格 4 处链接：`docs/xxx.md` → `docs/核心架构/xxx.md` |
| `README.md` | 模型与向量配置 1 处：`docs/模型绑定配置参考.md` → `docs/核心架构/模型绑定配置参考.md` |
| `README.md` | 文档导航 1 处：同上 |
| `docs/quality-progress-and-lessons.md` | 2 处：`docs/模型绑定配置参考.md` → `docs/核心架构/模型绑定配置参考.md` |
| `docs/核心架构/` 内部交叉链接 | 无需修改（全部使用 `./文件名.md` 相对路径） |

**未更新的引用**（历史报告，冻结快照，不修改）：
- `docs/plans/`、`docs/reports/` 下 18 处对 `docs/模型绑定配置参考.md` 的引用均保持原样，这些是历史归档报告，不随目录搬迁而更新。

## 4. 明确排除文件

| 文件 | 原因 |
|---|---|
| `special_cases_report.md` | redline 输出，永远不提交 |
| `src/**`、`scripts/**` | 不属文档搬目录范围 |
| `docs/test/knowledge-base-e2e/**` | 不属文档搬目录范围 |
| 历史 `docs/reports/**`、`docs/plans/**` | 冻结快照，不随目录搬迁更新引用 |

## 5. 当前 git status 摘要

```
 D docs/Deep-Research流水线.md           ← 工作区删除
 D docs/查询检索流水线.md                  ← 工作区删除
 D docs/模型中心与执行快照.md              ← 工作区删除
 D docs/模型绑定配置参考.md                ← 工作区删除
 D docs/编译流水线.md                     ← 工作区删除
 M README.md                             ← 路径引用已更新
 M docs/quality-progress-and-lessons.md   ← 路径引用已更新
 M special_cases_report.md               ← 排除
 ?? docs/核心架构/                         ← 新目录（5 个文档）
 ?? docs/reports/core_architecture_docs_relocation_report.md  ← 本报告
```

## 6. 是否建议提交

**建议提交。**

## 7. 建议提交文件列表

| # | 文件 | 说明 |
|---|---|---|
| 1 | `docs/核心架构/编译流水线.md` | 新位置 |
| 2 | `docs/核心架构/查询检索流水线.md` | 新位置 |
| 3 | `docs/核心架构/Deep-Research流水线.md` | 新位置 |
| 4 | `docs/核心架构/模型中心与执行快照.md` | 新位置 |
| 5 | `docs/核心架构/模型绑定配置参考.md` | 新位置（含本地化改动） |
| 6 | `docs/Deep-Research流水线.md` | 删除旧位置 |
| 7 | `docs/查询检索流水线.md` | 删除旧位置 |
| 8 | `docs/模型中心与执行快照.md` | 删除旧位置 |
| 9 | `docs/模型绑定配置参考.md` | 删除旧位置 |
| 10 | `docs/编译流水线.md` | 删除旧位置 |
| 11 | `README.md` | 链接更新 |
| 12 | `docs/quality-progress-and-lessons.md` | 路径更新 |
| 13 | `docs/reports/core_architecture_docs_relocation_report.md` | 本收口报告 |

必须排除：`special_cases_report.md`

## 8. 建议 commit message

```
docs: relocate core architecture docs
```
