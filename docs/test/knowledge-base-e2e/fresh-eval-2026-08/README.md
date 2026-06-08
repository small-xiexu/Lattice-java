# fresh-eval-2026-08 资料包说明

第五套 public eval 资料包，领域为**供应链来料质量管理 / 供应商管理 / 批次追踪 / 不合格品处理**。

## 性质

本资料包是 PE5 public eval，不是 hidden eval 的影子题集。题目、标准答案与预期来源可以被 AI 读取，用于调试、失败归因和公开回归。

## 内容

```
fresh-eval-2026-08/
├── README.md
├── sources/
│   ├── 01_markdown/supply-chain-quality-policy.md       # 来料质量管理总则
│   ├── 02_structured/supplier-registry.yaml              # 供应商台账（8 家）
│   ├── 03_xlsx/incoming-inspection-records.xlsx          # 来料检验记录（20 条）
│   ├── 04_csv/batch-tracking-log.csv                     # 批次追踪记录（17 条）
│   └── 05_pdf/nonconformance-handling-sop.pdf            # 不合格品处理 SOP
└── eval/
    └── question-set.md                                   # 12 FQ + 6 FS + 3 FG
```

## 与 PE1-PE4 的差异

PE5 是泛化压力测试，验证系统在完全陌生的供应链/质检领域上的检索召回和问答质量。

| 维度 | PE1-PE4 | PE5 |
|---|---|---|
| 核心术语 | probe/安全员/SLA/维护周期 | AQL/批次/来料检验/不合格品/供应商评级/CAPA |
| 缩写格式 | SL/TL/IM（2 字符） | AQL（3 字符）、LOT-0601（混合格式） |
| 数值类型 | 端口号/金额/百分比/周期 | AQL 阈值/缺陷数/供应商评分/检验频率 |

## 红线

禁止把本资料包中的题目、标准答案、关键词、文件名写入生产代码、prompt、配置、SQL、脚本、allowlist。

## 执行

agentD 导入后执行全量验收。先跑 PE1-PE4 保护回归。
