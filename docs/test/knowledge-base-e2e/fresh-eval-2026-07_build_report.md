# Public Eval 4 资料包构建报告

生成时间：2026-06-07
执行人：agentC
设计依据：`fresh-eval-2026-07_design_report.md`（agentB）

## 1. 资产目录结构

```
fresh-eval-2026-07/
├── README.md
├── _drafts/                                            # 源稿存档（不导入）
├── sources/
│   ├── 01_markdown/
│   │   └── medical-equipment-maintenance-policy.md      # 维护总则
│   ├── 02_structured/
│   │   └── equipment-registry.yaml                      # 10 台设备台账
│   ├── 03_xlsx/
│   │   ├── inspection-schedule.xlsx                     # 17 条巡检记录
│   │   └── fault-severity-matrix.xlsx                   # 4 级故障等级
│   ├── 04_csv/
│   │   └── fault-work-orders.csv                        # 18 条故障工单
│   └── 05_pdf/
│       └── emergency-repair-sop.pdf                     # P0 应急处置 SOP
└── eval/
    └── question-set.md
```

## 2. 文件清单与规模

| 文件 | 格式 | 规模 |
|---|---|---|
| `medical-equipment-maintenance-policy.md` | Markdown | 5 个 section，含表格 |
| `equipment-registry.yaml` | YAML | 10 台设备，每台 10 字段 |
| `inspection-schedule.xlsx` | XLSX | 17 行 × 7 列 |
| `fault-severity-matrix.xlsx` | XLSX | 4 行 × 7 列 |
| `fault-work-orders.csv` | CSV | 18 条工单 × 9 列 |
| `emergency-repair-sop.pdf` | PDF | 1 页，6 个步骤 |

**共 6 个 source 文件，覆盖 5 种格式。**

## 3. 每类文件验证的能力

| 文件类型 | 验证能力 |
|---|---|
| Markdown 总则 | 内容归纳、角色区分、故障等级定义 |
| YAML 设备台账 | 嵌套路径、多设备过滤、日期比较、聚合计数 |
| XLSX 巡检表 | 日期范围过滤、条件查询、异常统计 |
| XLSX 等级表 | 等级→时限映射、多条件排序 |
| CSV 工单 | 状态过滤、设备聚合、**逾期判断**、排序、计数 |
| PDF SOP | 流程步骤提取、跨文档组合（与等级表） |

## 4. FQ/FS/FG 覆盖矩阵

### FQ（12 题）

| 题号 | 能力 | 关键差异 vs PE1-PE3 |
|---|---|---|
| FQ1 | Markdown 归纳 + 角色 | — |
| FQ2 | YAML 单设备路径 | — |
| FQ3 | YAML 跨设备过滤 | **新增**：多设备条件过滤 |
| FQ4 | YAML 日期比较+聚合 | **新增**：日期<2027 的比较查询 |
| FQ5 | XLSX 等级查询 | — |
| FQ6 | XLSX 日期范围 | **新增**："6月份"日期范围 |
| FQ7 | CSV 状态过滤 | **新增**：状态机查询 |
| FQ8 | CSV 设备聚合+排序 | **新增**：按设备聚合+最近排序 |
| FQ9 | CSV 逾期判断 | **新增核心能力**：时间窗口 |
| FQ10 | 跨文档组合 | **新增**：SOP+等级表组合 |
| FQ11 | 拒答 | — |
| FQ12 | 拒答（跨 source） | **新增**：信息在另一 source |

### FS（7 子项）

| 搜索词 | 新增 | 验证维度 |
|---|---|---|
| 医疗设备维护总则 | — | sourceTitle |
| 故障等级 | — | anchorTitle |
| 巡检 | — | representativeTitle |
| P0 | **新增短 token** | 等级短 token |
| 核磁共振 | — | 关键词 |
| WO-042 | **新增工单号** | 混合大小写+数字+连字符 |

### FG（3 题）

| 题号 | 保护类型 | 新增 |
|---|---|---|
| FG1 | 日期保护 | **新增**：warranty_expiry vs maintenance_cycle |
| FG2 | 聚合保护 | **新增**：CSV 精确计数 |
| FG3 | 拒答保护 | — |

## 5. 与 PE1/PE2/PE3 的差异

| 能力 | PE1 | PE2 | PE3 | PE4 |
|---|---|---|---|---|
| 状态流转 | — | — | — | ✓ |
| 时间窗口/逾期 | — | — | — | ✓ |
| 周期计算 | — | — | — | ✓ |
| 多表关联 | — | — | — | ✓ |
| CSV 批量聚合 | — | — | — | ✓ |
| 短 token P0/WO-042 | — | — | — | ✓ |

## 6. 后续交给 agentD

1. 先跑 PE1+PE2+PE3 保护回归
2. 清库 → 导入 6 个 source 文件
3. 编译 → 执行 FQ1-FQ12、FS1-FS4(7 子项)、FG1-FG3
4. 采集 Answer/Search/Recall/Citation/Hallucination/Abstain
5. 通过线：Answer >= 10/12，Search >= 5/7，Hallucination = 0

## 7. 明确声明

- [x] 虚构医疗设备和厂商名，无真实数据
- [x] 未修改生产代码、测试、prompt、config、schema、scripts
- [x] 未读取 hidden eval
- [x] 未提交 commit
- [x] 6 个 source 文件覆盖 5 种格式，12 FQ + 7 FS + 3 FG
