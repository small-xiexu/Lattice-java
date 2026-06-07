# fresh-eval-2026-07 资料包说明

第四套 public eval，领域为医疗设备维护 / 巡检 / 故障工单。核心差异化能力：**状态流转、时间窗口、周期计算、多表关联**。

## 内容

```
fresh-eval-2026-07/
├── README.md
├── _drafts/                                     # 源稿存档
├── sources/
│   ├── 01_markdown/
│   │   └── medical-equipment-maintenance-policy.md   # 维护总则
│   ├── 02_structured/
│   │   └── equipment-registry.yaml                   # 设备台账（10 台）
│   ├── 03_xlsx/
│   │   ├── inspection-schedule.xlsx                   # 巡检计划（17 条）
│   │   └── fault-severity-matrix.xlsx                 # 故障等级定义
│   ├── 04_csv/
│   │   └── fault-work-orders.csv                      # 故障工单（18 条）
│   └── 05_pdf/
│       └── emergency-repair-sop.pdf                   # P0 应急处置 SOP
└── eval/
    └── question-set.md
```

## 与前几套的差异

| 维度 | PE1-PE3 | PE4（本套） |
|---|---|---|
| 信息特征 | 静态事实/字段值/条款 | **状态机、时间窗口、周期、多表关联** |
| 核心验证 | "是什么" | **"现在是什么状态"和"下一步该做什么"** |
| CSV 规模 | 5-5 行 | **18 行**（支持批量聚合） |
| 新增能力 | — | 状态流转、逾期判断、多设备聚合、短 token（P0）、工单号搜索 |

## 红线

虚构医疗设备和厂商名，禁止写入生产代码/prompt/config。

## 执行

agentD 导入后执行全量验收。先跑 PE1-PE3 保护回归。
