# fresh-eval-2026-06 资料包说明

## 性质

本目录是 knowledge-base-e2e 的第三套 public eval 资料包，领域为**采购合同 / 售后 SLA / 付款条款**。与前两套（Kubernetes 探针、实验室安全/设备借用）不同，本套聚焦**合同类长文档的精确信息提取与冲突判断**。

本资料包不是 hidden eval。题目、标准答案与预期来源可以被 AI 读取，用于调试、失败归因和公开回归。

## 内容

```text
fresh-eval-2026-06/
├── README.md
├── _drafts/                                   # 源稿存档（不导入）
├── sources/
│   ├── 01_pdf/
│   │   ├── procurement-contract.pdf           # 主合同（含条款号、金额、期限、责任方）
│   │   └── supplementary-agreement.pdf        # 补充协议（修改付款比例、延长质保）
│   ├── 02_structured/
│   │   └── payment-terms.yaml                 # 结构化付款条款配置
│   ├── 03_xlsx/
│   │   └── after-sales-sla-metrics.xlsx       # 售后 SLA 指标表
│   └── 04_csv/
│       └── payment-schedule.csv               # 付款计划/对账表
└── eval/
    └── question-set.md
```

> `_drafts/` 目录存放合同和 SLA 表的 Markdown 源稿，仅供人工查阅，**不要导入系统**。`sources/` 中均为最终可导入文件。

## 验收重点

| 能力 | 前两套是否覆盖 | 本套如何验证 |
|---|---|---|
| PDF 条款抽取与条款级定位 | PE1 仅覆盖流程概述和角色定义 | 条款号 + 条款文本精确抽取与引用 |
| 金额/百分比/期限精确提取 | PE2 覆盖简单数值 | 百分比（30%/70%）、工作日期限、罚金比例 |
| 责任方识别 | PE1 覆盖 SL/TL/IM | 甲/乙方、厂商/乙方/甲方区分 |
| 主合同/补充协议冲突判断 | 未覆盖 | 补充协议与主合同条款冲突 + 优先级声明 |
| SLA 指标表查询 | PE2 XLSX 未覆盖 SLA | 可用性、响应时间、修复时间多条件查询 |
| 多文档 citation 精确性 | 单源文件 | 合同引用"详见附件"，citation 指向附件 |
| 证据不足拒答 | 已覆盖 | 条款存在但子条款未定义时的拒答 |

## 与前两套的差异

| 维度 | PE1 | PE2 | PE3（本套） |
|---|---|---|---|
| 核心文档 | Markdown+YAML+PDF+XLSX | Markdown+YAML+XLSX+CSV+PDF | PDF×2+YAML+XLSX+CSV |
| 信息粒度 | 段落级/字段级 | 字段级/表格行级 | **条款级**（条款号+条款文本+条款间引用） |
| 数值类型 | 端口号、版本号 | 金额、天数 | **百分比、工作日、罚金比例** |
| 文档关系 | 独立文档 | 跨文档组合 | **主合同↔补充协议冲突** |

## 红线

禁止把本资料包中的题目、标准答案、关键词、文件名、case id、expected source 或 expected citation 写入：
- `src/main/java/**`
- prompt 模板
- 配置文件
- 回归脚本
- redline allowlist
- SQL 初始化数据

## 执行边界

本资料包由 agentC 生成。导入、编译、端到端验收和验收报告应交给 agentD 执行。

agentD 执行验收前，必须先跑 PE1+PE2 保护回归，确认第三套的导入和编译不影响已有两套。
