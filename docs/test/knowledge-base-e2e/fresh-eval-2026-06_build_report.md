# Public Eval 3 资料包落地报告

生成时间：2026-06-07
执行人：agentC（文档/题集落地 Agent）
设计依据：`docs/test/knowledge-base-e2e/fresh-eval-2026-06_design_report.md`（agentB，2026-06-07）

## 1. 新资料包目录

```
docs/test/knowledge-base-e2e/fresh-eval-2026-06/
├── README.md                                          # 资料包说明（验收重点、差异、红线）
├── sources/
│   ├── 01_pdf/
│   │   ├── procurement-contract.md                    # 主合同 → 转换为 PDF
│   │   └── supplementary-agreement.md                 # 补充协议 → 转换为 PDF
│   ├── 02_structured/
│   │   └── payment-terms.yaml                         # 结构化付款条款
│   ├── 03_xlsx/
│   │   └── after-sales-sla-metrics.md                 # SLA 指标表 → 转换为 XLSX
│   └── 04_csv/
│       └── payment-schedule.csv                       # 付款计划/对账表
└── eval/
    └── question-set.md                                # 题集（12FQ + 4FS + 3FG）
```

## 2. 每个 Source 的作用

| Source | 格式 | 验证能力 |
|---|---|---|
| `procurement-contract.md` | PDF（主合同 2-3 页） | PDF 条款抽取、条款号定位、金额/百分比/期限精确提取、责任方识别、条款间引用（"详见附件"） |
| `supplementary-agreement.md` | PDF（补充协议 1 页） | 多文档冲突检测、优先级声明、条款修改追溯（50% vs 30%） |
| `payment-terms.yaml` | YAML 结构化配置 | 嵌套路径精确查找、多值聚合、百分比字段区分（乙方罚金 vs 甲方罚金） |
| `after-sales-sla-metrics.md` | XLSX 表格 | 多条件查询、排序、责任方区分、百分比/时间精确匹配 |
| `payment-schedule.csv` | CSV 对账表 | 条件判断、日期比较、状态区分、金额聚合 |

## 3. 题集覆盖能力

### FQ 问答题（12 题）

| 题号 | 能力 | 目标 Source |
|---|---|---|
| FQ1 | PDF 条款抽取与概括 | 主合同 PDF |
| FQ2 | 金额/百分比精确提取 | 主合同 PDF |
| FQ3 | 条件→结果链（延期→违约金计算） | 主合同 PDF |
| FQ4 | 结构化路径 + 聚合 + 跨文档优先级 | payment-terms.yaml |
| FQ5 | 嵌套路径精确提取 + 跨文档字段更新 | payment-terms.yaml + 补充协议 |
| FQ6 | 百分比多字段区分 | payment-terms.yaml |
| FQ7 | 表格条件查询 + 多列值 | SLA XLSX |
| FQ8 | 表格排序 + 责任方 | SLA XLSX |
| FQ9 | CSV 条件判断 + 日期 | payment-schedule.csv |
| FQ10 | 多文档冲突检测 + 优先级判断 | 主合同 + 补充协议 |
| FQ11 | 补充协议新增条款定位 | 补充协议 PDF |
| FQ12 | 拒答（条款存在但子条款未定义） | 主合同 PDF |

### FS 搜索题（4 组 6 子项）

| 题号 | 搜索维度 | 验证能力 |
|---|---|---|
| FS1 | sourceTitle | 主合同排首位 |
| FS2 | 条款标题/anchorTitle | 条款号搜索结果精度 |
| FS3 | representativeTitle | SLA 表代表性标题 |
| FS4a | 正文关键词（99.99%） | 百分比搜索 |
| FS4b | 正文关键词（质保期） | 条款标题搜索 |
| FS4c | 正文关键词（乙方） | 责任方搜索 |

### FG 保护题（3 题）

| 题号 | 保护类型 | 防止什么 |
|---|---|---|
| FG1 | 数值保护 | 不让 rate_percent 抢占 cap_percent |
| FG2 | 百分比保护 | 不让相近百分比（99.5/99.9/99.99）混淆 |
| FG3 | 拒答保护 | 不从不相关条款编造答案 |

## 4. 与前两套的差异

| 维度 | PE1（K8s/探针） | PE2（实验室/设备） | PE3（本套） |
|---|---|---|---|
| 核心文档 | Markdown+YAML+PDF+XLSX | Markdown+YAML+XLSX+CSV+PDF | **PDF×2**+YAML+XLSX+CSV |
| 信息粒度 | 段落级/字段级 | 字段级/表格行级 | **条款级**（条款号+条款文本+条款间引用） |
| 数值类型 | 端口号、版本号 | 金额、天数 | **百分比、工作日、罚金比例** |
| 逻辑复杂度 | 简单事实查找 | 单字段多实体区分 | **多文档冲突、条款优先级、条件→结果链** |
| 搜索重点 | 标题/弱标题/anchor | sourceTitle/关键词 | **条款标题/条款号/百分比搜索** |
| 新增风险 | — | — | 避免因为合同类文档引入"合同模板解析"式特判 |

## 5. 红线检查结果

| 检查项 | 结果 |
|---|---|
| Source 文件中无真实公司名/合同号 | ✅ 使用虚构公司名"星辰科技""鸿图信息" |
| Source 文件中无 API Key/密码/密钥 | ✅ |
| 题集 case id 仅存在于题集文件中 | ✅ |
| 未将题目/答案写入生产代码 | ✅ 本轮未触碰 src/** |
| 未修改 prompt/config/schema/scripts | ✅ |
| 未使用 hidden eval 内容 | ✅ |
| 与前两套题型不重复 | ✅ 领域、文档类型、验证能力均不同 |
| 无 case 特判逻辑 | ✅ 所有题为通用合同场景 |

## 6. 已知限制

- **PDF 文件**：当前为 Markdown 源文件（`.md`），需转换为实际 PDF 后导入。转换命令：`pandoc procurement-contract.md -o procurement-contract.pdf --pdf-engine=weasyprint`
- **XLSX 文件**：当前为 Markdown 表格源文件（`.md`），需转换为 XLSX 后导入。可在 Excel/LibreOffice 中直接粘贴表格保存
- 以上转换可在 agentD 执行验收前完成

## 7. 后续交给 agentD 的验证建议

1. 执行顺序：
   - 先跑 PE1 + PE2 保护回归（确认旧题集无下降）
   - 再导入本套资料（转换为 PDF/XLSX 后）
   - 编译 → 执行 FQ1-FQ12、FS1-FS4、FG1-FG3
   - 采集 Answer Accuracy、Search Accuracy、Recall@5/10、Citation、Hallucination、Abstain
2. 通过线：Answer >= 10/12，Search >= 5/6，FG = 3/3，Hallucination = 0
3. 如未通过：只输出指标和失败桶，不改代码，交给 agentB 做归因分析

## 8. 明确声明

- [x] 本轮未修改任何生产代码、测试、prompt、config、schema、scripts
- [x] 本轮未读取 hidden eval
- [x] 本轮未提交 commit
- [x] 所有题目和答案仅存在于 `docs/test/knowledge-base-e2e/fresh-eval-2026-06/` 内
- [x] 资料包中不含真实公司名、真实合同号或真实商业条款
- [x] 题集设计为通用合同场景，不绑定任何具体业务域
- [x] case id 仅存在于题集文件中，未泄漏到 source 文件
