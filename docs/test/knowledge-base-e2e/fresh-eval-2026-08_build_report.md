# Public Eval 5 资料包构建报告

生成时间：2026-06-07
执行人：agentC
设计依据：`fresh-eval-2026-08_design_report.md`（agentB）

## 1. 资产目录结构

```
fresh-eval-2026-08/
├── README.md
├── sources/
│   ├── 01_markdown/supply-chain-quality-policy.md       # 质量管理制度（6 section）
│   ├── 02_structured/supplier-registry.yaml              # 供应商台账（8 家，A/B/C/D 四级）
│   ├── 03_xlsx/incoming-inspection-records.xlsx          # 来料检验记录（20 条，12 列）
│   ├── 04_csv/batch-tracking-log.csv                     # 批次追踪记录（17 条，8 列）
│   └── 05_pdf/nonconformance-handling-sop.pdf            # 不合格品处理 SOP（4 section）
└── eval/
    └── question-set.md                                   # 12 FQ + 6 FS + 3 FG
```

## 2. 文件清单与规模

| 文件 | 格式 | 规模 |
|---|---|---|
| `supply-chain-quality-policy.md` | Markdown | 6 section，含 4 个表格 |
| `supplier-registry.yaml` | YAML | 8 个供应商，每供应商 10 字段 |
| `incoming-inspection-records.xlsx` | XLSX | 20 行 × 12 列 |
| `batch-tracking-log.csv` | CSV | 17 条 × 8 列 |
| `nonconformance-handling-sop.pdf` | PDF | 1 页，4 section |

## 3. 每类文件验证的能力

| 文件 | 验证能力 |
|---|---|
| Markdown 制度 | 流程归纳、供应商评级标准、不合格品分级、角色职责、AQL 术语 |
| YAML 台账 | 评级过滤、评分排序、计数聚合、日期判断（审核过期） |
| XLSX 检验记录 | 供应商条件查询、日期范围过滤、AQL 阈值过滤、合格率计算 |
| CSV 批次追踪 | 状态过滤、供应商聚合、时间窗口逾期、批次号搜索 |
| PDF SOP | 缺陷分级处置、CAPA 流程、时限提取、责任人识别 |

## 4. FQ/FS/FG 覆盖矩阵

### FQ（12 题）

| 题号 | 能力 | 新领域验证点 |
|---|---|---|
| FQ1 | 流程归纳 | 新术语（AQL/报检/隔离） |
| FQ2 | 条件过滤 + 多级比较 | A/B 评级标准差异 |
| FQ3 | 按评级计数聚合 | 新缩写（A/B/C/D） |
| FQ4 | 供应商查询 + 合格率 | SUP-xxx 编号体系 |
| FQ5 | 日期范围 + 条件统计 | 6 月时间窗口 |
| FQ6 | 数值阈值过滤 | AQL > 1.5 |
| FQ7 | 状态过滤 + 供应商关联 | 隔离中状态 |
| FQ8 | 供应商聚合 + 排序 | 合格率计算 |
| FQ9 | 逾期判断 | 30 天时间窗口 |
| FQ10 | PDF 条款抽取 | CAPA/SCAR 流程 |
| FQ11 | 拒答（台账无付款条款） | 新领域拒答 |
| FQ12 | 拒答（CSV 无检验员） | 字段在其他 source |

### FS（6 子项）

| 搜索词 | 验证维度 | 新领域挑战 |
|---|---|---|
| 供应链质量管理 | sourceTitle | 新领域 sourceTitle |
| 来料检验 | anchorTitle | 新领域 section 标题 |
| AQL | 缩写 | 3 字符大写缩写 |
| SUP-001 | 供应商编号 | 字母+连字符+数字 |
| 不合格品 | 关键词 | 新领域关键词 |
| LOT-0601 | 批次号 | 混合格式（字母+连字符+数字） |

### FG（3 题）

| 题号 | 保护类型 |
|---|---|
| FG1 | 数值保护——极值排序 + 多字段不混淆 |
| FG2 | 跨文档保护——XLSX+CSV+PDF 三 source 组合 |
| FG3 | 拒答保护——不编造环保资质 |

## 5. 与 PE1-PE4 的差异

| 维度 | PE1-PE4 | PE5 |
|---|---|---|
| 领域 | K8s/实验室/合同/医疗设备 | 供应链/质检/供应商管理 |
| 核心缩写 | SL/TL/IM（2 字符） | AQL（3 字符大写）、CAPA、NC |
| 编号体系 | 端口号/合同号/WO-xxx | SUP-xxx/IQC-xxx/LOT-xxxx-xx |
| 数值类型 | 端口/金额/百分比/周期 | AQL 阈值(0.65-4.0)、缺陷数、评分(0-100) |
| 设计目的 | 能力建设与缺陷修复 | 泛化压力测试 |

## 6. 反过拟合与红线

- 全部术语（AQL/LOT/NC/CAPA/来料检验/隔离/放行/供应商评级）与 PE1-PE4 无重叠
- 引入新的编号格式（SUP-xxx、IQC-xxx、LOT-xxxx-xx）
- 虚构供应商和物料名称，无真实数据
- 未参考任何 hidden eval 内容
- 题目和答案仅在 `docs/test/` 内，未写入生产代码

## 7. 给 agentD 的验收建议

1. 先跑 PE1-PE4 保护回归
2. 清库 → 导入 5 个 source 文件 → 编译
3. 执行 FQ1-FQ12、FS1-FS4（6 子项）、FG1-FG3
4. 重点观察：AQL/LOT/SUP 等新缩写搜索召回、FQ9 逾期判断、FS3 "AQL" 搜索
5. 通过线：Answer >= 10/12、Search >= 5/6、Hallucination = 0

## 8. 明确声明

- [x] 虚构供应商和物料名称，无真实数据
- [x] 未修改生产代码、测试、prompt、config、schema、scripts
- [x] 未读取 hidden eval
- [x] PE5 是完全独立的公开评测，不是 hidden 的影子题集
- [x] 未提交 commit
