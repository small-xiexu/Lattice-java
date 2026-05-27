# fresh-eval-2026-05 资料包说明

## 性质

本目录是 knowledge-base-e2e 的 public fresh eval 资料包，用于验证知识库导入、检索、结构化字段提取、跨文档组合、拒答与 citation 支撑能力。

本资料包不是 hidden eval。题目、标准答案与预期来源可以被 AI 读取，用于调试、失败归因和公开回归。

## 内容

```text
fresh-eval-2026-05/
├── README.md
├── sources/
│   ├── 01_markdown/
│   │   └── lab-safety-management-handbook.md
│   ├── 02_structured/
│   │   └── equipment-borrowing-policy.yaml
│   ├── 03_xlsx/
│   │   └── chemical-storage-grading.xlsx
│   ├── 04_pdf/
│   │   └── lab-emergency-response-procedures.pdf
│   └── 05_csv/
│       └── equipment-maintenance-schedule.csv
└── eval/
    └── question-set.md
```

## 红线

禁止把本资料包中的题目、标准答案、关键词、文件名、case id、expected source 或 expected citation 写入以下位置：

- `src/main/java/**`
- prompt 模板
- 配置文件
- 回归脚本
- redline allowlist
- SQL 初始化数据

后续修复必须基于通用失败类型归因，不得为 FQ1-FQ12、FS1-FS4、FG1-FG3 写任何 case 特判。

## 执行边界

本轮只生成资料包，不执行导入、清库、schema 重建、模型调用、API 调用或 query 验收。

资料包导入、端到端验收和验收报告应交给 agentD 执行。

