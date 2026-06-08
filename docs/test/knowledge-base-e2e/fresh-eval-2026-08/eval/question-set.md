# fresh-eval-2026-08 题集

本题集为第五套 public eval，领域为供应链来料质量管理。包含 FQ1-FQ12、FS1-FS4（6 子项）、FG1-FG3。

## FQ 问答题

### FQ1 — 流程归纳

- query: 来料检验的完整流程是什么？从到货到入库有哪些步骤？
- expected: 6 步：到货登记→报检通知→抽样（按 AQL 标准）→检验→判定（合格/不合格）→入库或隔离。不合格物料移至不合格品隔离区，启动不合格品处理流程
- target: `supply-chain-quality-policy.md`
- covered capability: 流程归纳 + 新术语（AQL/报检/隔离）

### FQ2 — 条件过滤 + 比较

- query: A 级供应商的评分标准是什么？和 B 级有什么区别？
- expected: A 级：质量 >=90、交付 >=85、响应 >=90，综合"优秀"，每 5 批抽检 1 批。B 级：质量 >=80、交付 >=75、响应 >=80，综合"良好"，每 3 批抽检 1 批。A 级三个评分维度阈值均高于 B 级 5-10 分，检验频率低于 B 级
- target: `supply-chain-quality-policy.md`
- covered capability: 表格条件过滤 + 多级比较

### FQ3 — 计数聚合

- query: 供应商台账里有多少家供应商的评级是 A？评级是 C 或 D 的各有多少？
- expected: A 级 2 家（SUP-001、SUP-005），C 级 2 家（SUP-003、SUP-008），D 级 1 家（SUP-004）
- target: `supplier-registry.yaml`
- covered capability: 按评级字段计数聚合

### FQ4 — 条件查询 + 合格率计算

- query: SUP-003 的最近一次来料检验结果是什么？合格率是多少？
- expected: 最近一次检验为 IQC-040（不锈钢板材，2026-06-03），合格数 80/80 = 100%。历史总计 6 条检验记录（IQC-040/036/035/033/031/023），合格 4 条（IQC-040/035/033/023），不合格 2 条（IQC-036/031），整体合格率 66.7%
- target: `incoming-inspection-records.xlsx`
- covered capability: 供应商条件查询 + 聚合计算

### FQ5 — 日期范围 + 条件过滤

- query: 6 月份有多少批来料检验被判为不合格？
- expected: 6 月共 6 条检验记录（IQC-042/041/040/039/038/037），不合格 2 条：IQC-039（瓦楞纸箱，SUP-004）和 IQC-037（弹簧垫圈，SUP-006）
- target: `incoming-inspection-records.xlsx`
- covered capability: 日期范围过滤 + 条件统计

### FQ6 — 数值条件过滤

- query: 哪些批次的 AQL 标准超过 1.5？
- expected: AQL=2.5 的批次：IQC-039、IQC-037、IQC-032、IQC-029、IQC-028、IQC-026。共 6 批
- target: `incoming-inspection-records.xlsx`
- covered capability: 数值阈值过滤（AQL > 1.5）

### FQ7 — 状态过滤 + 跨 source 关联

- query: 批次追踪记录里，当前隔离中的批次有哪些？分别是哪些供应商的？
- expected: 4 批：LOT-0604-B（SUP-004，瓦楞纸箱）、LOT-0522-B（SUP-002，IC 芯片）、LOT-0511-A（SUP-004，塑料托盘）、LOT-0415-C（SUP-007，氧化铝粉末）
- target: `batch-tracking-log.csv`
- covered capability: 状态过滤 + 供应商关联

### FQ8 — 供应商维度聚合

- query: SUP-003 的批次合格率是多少？最近一批合格批次是什么时候？
- expected: SUP-003 共 5 条批次记录（LOT-0603-A/0520-B/0521-A/0510-C/0505-B），合格 4 条（LOT-0603-A/0521-A/0510-C/0505-B），不合格 1 条（LOT-0520-B 退货）。合格率 = 4/5 = 80%。最近合格批次为 LOT-0603-A（2026-06-03）
- target: `batch-tracking-log.csv`
- covered capability: 供应商维度聚合 + 排序

### FQ9 — 时间窗口逾期

- query: 以 2026-06-07 为准，来料超过 30 天仍未完成入库的批次有哪些？
- expected: 来料日期在 2026-05-08 之前且状态非"已入库"的批次。LOT-0428-C（04-28，已入库不计）、LOT-0425-B（04-25，退货不计）、LOT-0420-A（04-20，已入库不计）、LOT-0415-C（04-15，隔离中，超 53 天）。另有 LOT-0511-A（05-15，隔离中，23 天未超 30 天）。共 1 批（LOT-0415-C）
- target: `batch-tracking-log.csv`
- covered capability: 时间窗口逾期判断

### FQ10 — PDF 条款抽取

- query: 不合格品分级为"严重缺陷"时，应该执行什么处理流程？谁负责审批？
- expected: 整批退货，不得让步接收。检验员 30 分钟内标识隔离，质检主管 2 小时内发出 CAPA，供应商 5 个工作日内提交根因分析和纠正措施。质量经理审批退货和 CAPA 报告
- target: `nonconformance-handling-sop.pdf`
- covered capability: PDF 条款抽取 + 责任人识别 + 时限提取

### FQ11 — 拒答（台账无此信息）

- query: 供应商台账里有没有定义供应商的付款条款和账期？
- expected: 没有。供应商台账只定义了评级、评分（quality/delivery/response）、审核日期、状态和联系方式，无付款条款或账期信息
- target: `supplier-registry.yaml`
- covered capability: 证据不足拒答

### FQ12 — 拒答（CSV 无此字段）

- query: 批次追踪记录里有没有记录每批次的检验员是谁？
- expected: 没有。批次追踪 CSV 记录了批次号、物料、供应商、来料日期、检验结果、入库日期、状态和备注，不含检验员字段。检验员信息在检验记录 XLSX 中
- target: `batch-tracking-log.csv`
- covered capability: 信息缺失拒答（字段存在于另一 source）

## FS 搜索题

- FS1: `供应链质量管理` — sourceTitle → 命中制度 Markdown article
- FS2: `来料检验` — anchorTitle / section title → 命中制度第二条或检验记录
- FS3: `AQL` — 缩写关键词 → 命中制度第二条和检验记录中的 AQL 列
- FS4a: `SUP-001` — 供应商编号 → 命中供应商台账 YAML 和检验记录
- FS4b: `不合格品` — 正文关键词 → 命中制度第四条和 PDF SOP
- FS4c: `LOT-0601` — 批次号（字母+连字符+数字） → 命中批次追踪 CSV 和检验记录

## FG 保护题

### FG1 — 数值保护

- query: 供应商台账里评分最高的是哪家？各项评分分别是多少？它的评级是什么？
- expected: SUP-005（精密加工供应商E），质量 95、交付 92、响应 90，评级 A。各项评分不能与其他供应商混淆
- target: `supplier-registry.yaml`
- covered capability: 极值排序 + 多字段不混淆

### FG2 — 跨文档保护

- query: LOT-0520-B 这个批次为什么被退货？退货后供应商需要做什么？
- expected: 检验记录（IQC-036）显示：轴承座，尺寸超差，AQL 1.5，抽样 100 合格 95 缺陷 5，判定不合格。批次追踪显示：LOT-0520-B，状态退货。PDF SOP 规定：退货按缺陷等级执行处置——质检主管 2 小时内发出《供应商纠正措施要求》，供应商 5 个工作日内提交根因分析和纠正措施（CAPA）。XLSX + CSV + PDF 三个 source 的信息需要组合
- target: `incoming-inspection-records.xlsx` + `batch-tracking-log.csv` + `nonconformance-handling-sop.pdf`
- covered capability: 跨 source 信息组合（XLSX + CSV + PDF）

### FG3 — 拒答保护

- query: 供应商台账里有没有定义供应商的环保资质要求？
- expected: 没有。台账只定义了评级、评分（quality/delivery/response）、审核日期、状态、联系方式，不含环保资质或 ISO 14001 等认证信息
- target: `supplier-registry.yaml`
- covered capability: 拒答——不能从评分字段编造环保资质
