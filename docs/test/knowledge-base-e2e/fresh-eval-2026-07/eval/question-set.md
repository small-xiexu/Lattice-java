# fresh-eval-2026-07 题集

本题集为第四套 public eval，领域为医疗设备维护 / 巡检 / 故障工单。包含 FQ1-FQ12、FS1-FS4（含子项）、FG1-FG3。

## FQ 问答题

### FQ1 — 内容归纳

- query: 设备维护总则里，维护周期分为哪几种？各由谁负责？
- expected: 日检（操作员）、周检（操作员）、月检（责任工程师）、季检（责任工程师+厂商）、年检（厂商）
- target: `medical-equipment-maintenance-policy.md`
- covered capability: Markdown 内容归纳 + 角色区分

### FQ2 — YAML 路径单设备

- query: 核磁共振成像仪的维护周期是多久？下次维护是什么时候？
- expected: 3 个月；2026-07-15
- target: `equipment-registry.yaml`, `equipment[0]`
- covered capability: YAML 嵌套路径精确查找

### FQ3 — YAML 多设备过滤

- query: 哪些设备的维护周期是 1 个月（每月维护）？
- expected: LAB-004（全自动生化分析仪）、LAB-005（血细胞分析仪）、DIAL-010（血液透析机），共 3 台
- target: `equipment-registry.yaml`
- covered capability: YAML 条件过滤 + 跨设备计数

### FQ4 — YAML 日期比较聚合

- query: 有多少台设备的保修期在 2027 年底前（含 2027 年）到期？
- expected: CT-002（2027-09-15）、US-003（2026-12-31）、VENT-006（2027-05-31）、ANES-008（2026-11-30），共 4 台
- target: `equipment-registry.yaml`
- covered capability: 日期比较 + 聚合计数

### FQ5 — XLSX 等级表

- query: P0 故障的响应时限和解决时限分别是多少？需要停机吗？
- expected: 响应 < 15 分钟，解决 < 2 小时，需要停机
- target: `fault-severity-matrix.xlsx`
- covered capability: XLSX 条件查询 + 多列值

### FQ6 — XLSX 日期范围

- query: 6 月份有哪些巡检结果是异常的？分别是什么设备？
- expected: CT-002（球管温度偏高）、LAB-005（血红蛋白模块偏差）、LAMP-009（3 个灯珠不亮），共 3 条
- target: `inspection-schedule.xlsx`
- covered capability: 日期范围过滤 + 条件过滤

### FQ7 — CSV 状态过滤

- query: 哪些工单当前还在处理中？
- expected: WO-042（MRI-001, P1, 梯度线圈异常）和 WO-027（CT-002, P1, 重建图像模糊），共 2 条
- target: `fault-work-orders.csv`
- covered capability: CSV 状态过滤

### FQ8 — CSV 聚合排序

- query: CT-002 设备一共有几条历史工单？最近一次是什么故障？
- expected: 4 条工单（WO-041 P0、WO-040 P2、WO-034 P1、WO-027 P1）；最近一次是 WO-041（P0，高压发生器故障停机）
- target: `fault-work-orders.csv`
- covered capability: 按设备聚合 + 排序

### FQ9 — 时间窗口逾期

- query: 以 2026-06-07 为准，有没有超期未完成的工单？
- expected: WO-042（计划完成 2026-06-05，状态"处理中"，逾期 2 天）和 WO-027（计划完成 2026-04-15，状态"处理中"，逾期 53 天），共 2 条
- target: `fault-work-orders.csv`
- covered capability: 时间窗口判断（逾期检测）

### FQ10 — 跨文档组合

- query: P0 故障除了响应时限外，还有哪些完整的处置步骤？
- expected: 停机确认→通知升级→隔离防护→厂商维修→恢复验证→验收关闭。6 个步骤，各有限定时间。30 天内 2 次 P0 触发更换评估
- target: `emergency-repair-sop.pdf` + `fault-severity-matrix.xlsx`
- covered capability: 跨文档组合（SOP+等级表）

### FQ11 — 拒答

- query: 设备台账里有没有定义设备的报废标准？
- expected: 没有。台账只记录了维护周期、保修期、厂商信息，无报废标准
- target: `equipment-registry.yaml`
- covered capability: 证据不足拒答

### FQ12 — 拒答（信息不在此表）

- query: 巡检计划表里有没有记录外部厂商的联系方式？
- expected: 没有。巡检表只有巡检日期、设备、巡检人、结果。厂商联系方式在设备台账 YAML 中
- target: `inspection-schedule.xlsx`
- covered capability: 信息存在但不在当前 source

## FS 搜索题

- FS1: `医疗设备维护总则` — sourceTitle → 命中总则 Markdown article
- FS2: `故障等级` — anchorTitle → 命中等级定义表的 chunk
- FS3: `巡检` — representativeTitle → 命中计划表或总则
- FS4a: `P0` — 短 token → 命中等级表中的 P0 行
- FS4b: `核磁共振` — 关键词 → 命中设备台账 MRI-001
- FS4c: `WO-042` — 工单号 → 命中工单 CSV 对应行

## FG 保护题

### FG1 — 日期保护

- query: 核磁共振仪的保修期到什么时候？保修范围和厂商联系方式是什么？
- expected: 保修期至 2028-03-31；厂商西门子医疗，电话 400-810-5888。台账中未定义保修范围。不得将维护周期（3 个月）误作为保修期
- covered capability: 日期保护——不让 next_maintenance_due 抢占 warranty_expiry

### FG2 — 聚合保护

- query: 张工一共处理了多少条工单？其中几条是 P0/P1 的？
- expected: fault-work-orders.csv 中张工处理了 5 条工单：WO-042(P1)、WO-037(P2)、WO-036(P0)、WO-028(P2)、WO-025(P2)。其中 P0/P1 = 2 条（WO-036 P0、WO-042 P1）
- target: `fault-work-orders.csv`
- covered capability: 聚合计数必须精确

### FG3 — 拒答保护

- query: 设备台账里有没有定义设备的日常使用规范？
- expected: 没有。台账只定义维护信息和厂商信息，无日常使用规范。总则定义了维护周期和故障等级，但也没有使用规范
- covered capability: 拒答——不能从维护流程编造使用规范

## 通过标准

| 指标 | 目标 |
|---|---|
| Answer Accuracy | >= 10/12 |
| Search Accuracy | >= 5/7（FS4 含 3 子项） |
| FG Accuracy | >= 3/3 |
| Hallucination | = 0 |
