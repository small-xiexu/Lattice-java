# Public Eval 6 资料包构建报告

生成时间：2026-06-08
执行人：agentC
设计依据：`fresh-eval-2026-09_design_report.md`（agentB）

## 1. 资产目录

```
fresh-eval-2026-09/
├── README.md
├── sources/
│   ├── 01_java/library-lending-system/          # 26 files
│   │   ├── pom.xml
│   │   └── src/main/... (Java×19, XML×3, YML×3, pom.xml)
│   ├── 02_docs/                                   # 7 Markdown
│   ├── 03_config/                                 # 1 config reference
│   ├── 04_sql/                                    # 2 SQL
│   ├── 05_data/                                   # CSV + XLSX
│   └── 06_pdf/                                    # 1 PDF
└── eval/question-set.md                           # 16 FQ + 8 FS（含 FS5）+ 4 FG
```

## 2. 文件清单与规模

| 类别 | 数量 | 说明 |
|---|---|---|
| Java 源码 | 19 | 3 Controller + 3 Service + 3 Impl + 3 Domain + 4 DTO + 3 Mapper |
| Mapper XML | 3 | LendingRecord + CreditRecord + FineRecord |
| pom.xml | 1 | Maven 项目配置 |
| 应用配置 | 3 | application.yml + dev + prod |
| 技术文档 | 7 | README + 3 ADR + API spec + Feature Flags + 排障 |
| 配置参考 | 1 | application-config-reference.md |
| SQL | 2 | schema + migration |
| 数据文件 | 2 | defect-list.csv（15条）+ release-checklist.xlsx（15项） |
| PDF | 1 | release-acceptance-sop.pdf |
| **合计** | **39** | 覆盖 6 种格式 |

## 3. FQ/FS/FG 覆盖矩阵

| 能力 | FQ | FS | FG |
|---|---|---|---|
| endpoint + DTO 校验 | FQ1 | FS3 | — |
| Service 阶梯逻辑 | FQ2, FQ15 | — | FG1 |
| ADR↔代码一致性 | FQ4, FQ5, FQ6 | — | — |
| Feature Flag 影响 | FQ3, FQ7 | — | — |
| dev/prod 配置差异 | FQ8 | FS4d | FG4 |
| 接口文档↔代码对齐 | FQ14 | FS3 | — |
| CSV 聚合 | FQ9 | FS4b | — |
| XLSX 条件查询 | FQ10 | — | — |
| PDF 条款 | FQ11 | FS5 | FG2 |
| SQL 表结构 | FQ12 | — | — |
| 排障流程 | FQ13 | — | — |
| 拒答 | FQ16 | — | FG3 |
| 边界值/配置保护 | — | — | FG1, FG4 |
| 搜索 | — | FS1-FS5 | — |

## 4. 与 PE1-PE5 / Java Codebase Eval 的差异

| 维度 | 已有评测 | PE6 |
|---|---|---|
| 资料类型 | 纯文档或纯代码 | 代码+文档+配置+SQL+数据表混合 |
| 问答模式 | 单 source | 跨 source 复合取证 |
| 新能力 | — | ADR↔代码一致性、配置差异、Feature Flag 影响分析 |

## 5. 明确声明

- [x] 虚构项目 Library Lending System，不涉及真实公司/数据
- [x] 未修改生产代码、测试、prompt、config、schema、scripts
- [x] 未读取 hidden eval
- [x] 未提交 commit
