# Public Eval 3 资料包补完报告

完成时间：2026-06-07
执行人：agentC（文档/题集落地 Agent）
设计依据：`fresh-eval-2026-06_design_report.md`（agentB）

## 1. 最终目录结构

```
fresh-eval-2026-06/
├── README.md
├── _drafts/                                    # 源稿存档（不导入系统）
│   ├── procurement-contract.md
│   ├── supplementary-agreement.md
│   └── after-sales-sla-metrics.md
├── sources/
│   ├── 01_pdf/
│   │   ├── procurement-contract.pdf            # 408 KB，2-3 页
│   │   └── supplementary-agreement.pdf         # 283 KB，1 页
│   ├── 02_structured/
│   │   └── payment-terms.yaml
│   ├── 03_xlsx/
│   │   └── after-sales-sla-metrics.xlsx        # 2.5 KB
│   └── 04_csv/
│       └── payment-schedule.csv
└── eval/
    └── question-set.md
```

## 2. 实际生成的 PDF/XLSX

| 文件 | 格式 | 大小 | 生成方式 |
|---|---|---|---|
| `procurement-contract.pdf` | PDF | 408 KB | Markdown → HTML → Chrome headless `--print-to-pdf` |
| `supplementary-agreement.pdf` | PDF | 283 KB | 同上 |
| `after-sales-sla-metrics.xlsx` | XLSX | 2.5 KB | Python `zipfile` + 原生 XML 构造 |

## 3. 源稿处置

| 文件 | 处置 | 原因 |
|---|---|---|
| `sources/01_pdf/procurement-contract.md` | → `_drafts/` | 避免与最终 PDF 重复入库 |
| `sources/01_pdf/supplementary-agreement.md` | → `_drafts/` | 同上 |
| `sources/03_xlsx/after-sales-sla-metrics.md` | → `_drafts/` | 避免与最终 XLSX 重复入库 |
| 临时 HTML 文件 | 已删除 | 仅 PDF 生成中间产物 |

## 4. README / question-set 文件名对齐

| 检查项 | 结果 |
|---|---|
| README.md 目录树中的文件名 | `.pdf` / `.xlsx` / `.yaml` / `.csv` 均为最终格式 |
| README.md 已移除"PDF/XLSX 生成说明"章节 | ✅ 替换为 `_drafts/` 说明 |
| question-set.md 中 expected source 名称 | 全部与 `sources/` 最终文件名一致 |
| question-set.md 题意/答案口径 | 未修改 |

## 5. 最终 sources/ 文件清单（可直接导入）

```
sources/01_pdf/procurement-contract.pdf
sources/01_pdf/supplementary-agreement.pdf
sources/02_structured/payment-terms.yaml
sources/03_xlsx/after-sales-sla-metrics.xlsx
sources/04_csv/payment-schedule.csv
```

5 个文件，与设计报告中的 5 个 source 类型一一对应。

## 6. redline 结果

未修改生产代码/prompt/config/schema/scripts/allowlist，无新 redline 扫描需要。资料包内容与初始落地版一致，仅格式从 Markdown 转为 PDF/XLSX。

## 7. 是否可直接交给 agentD

**是。** agentD 可直接：

1. 清库 → 将 `sources/` 下 5 个文件导入系统
2. 编译（预期 5 个 compile job）
3. 按 `eval/question-set.md` 执行 FQ1-FQ12、FS1-FS4、FG1-FG3
4. 采集 Answer Accuracy、Search Accuracy、Recall@5/10、Citation、Hallucination、Abstain
5. 先跑 PE1+PE2 保护回归

注意：`_drafts/` 目录不要导入系统。

## 8. 明确声明

- [x] 未修改生产代码、测试、prompt、config、schema、scripts
- [x] 未修改题目含义或答案口径
- [x] 未读取 hidden eval
- [x] 未写入任何 case 特判
- [x] 未修改 redline allowlist
- [x] 未提交 commit
- [x] `sources/` 中仅含最终可导入文件，无重复
