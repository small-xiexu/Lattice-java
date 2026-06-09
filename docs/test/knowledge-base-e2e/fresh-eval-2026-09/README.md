# fresh-eval-2026-09 资料包说明

第六套 public eval。验证"代码 + 文档 + 配置 + SQL + 数据表"混合知识库场景下的跨 source 问答能力。虚构项目：**图书馆借阅管理系统**。

## 内容

```
fresh-eval-2026-09/
├── README.md
├── sources/
│   ├── 01_java/  — 26 文件（Java×15 + XML×3 + YML×3 + pom.xml + 其他）
│   ├── 02_docs/  — 7 个 Markdown（ADR×3 + API spec + Feature Flags + 排障 + README）
│   ├── 03_config/ — 1 个配置参考文档
│   ├── 04_sql/   — 2 个 SQL（schema + migration）
│   ├── 05_data/  — defect-list.csv（15条）+ release-checklist.xlsx（15项）
│   └── 06_pdf/   — release-acceptance-sop.pdf
└── eval/question-set.md  — 16 FQ + 8 FS + 4 FG
```

## 与前几套的差异

| 维度 | PE1-PE5 / Java Eval | PE6 |
|---|---|---|
| 资料类型 | 纯文档或纯代码 | 代码+文档+配置+SQL+数据表混合 |
| 核心验证 | 单 source 问答 | 跨 source 复合取证（ADR↔代码、文档↔配置、SQL↔文档） |

## 红线

虚构项目，不涉及真实公司/数据。不参考 hidden eval。
