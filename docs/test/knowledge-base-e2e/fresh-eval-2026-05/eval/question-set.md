# fresh-eval-2026-05 题集

本题集为 public fresh eval，包含 FQ1-FQ12、FS1-FS4、FG1-FG3。题目与标准答案用于公开调试和失败归因，不得写入生产代码、prompt、配置、脚本或 allowlist。

## FQ1

- case id: FQ1
- query: "化学品分类存储"这段主要讲什么？
- expected answer: 描述危险等级 A/B/C/D 的分类原则及对应存储条件，不应只重复"化学品分类存储"这 5 个字
- expected source: `lab-safety-management-handbook.md`, `## 化学品分类存储` 段
- evidence type: ARTICLE (chunk)
- pass criteria: 答案覆盖该段核心内容，非仅复述标题；citation 指向正确 chunk
- covered capability: Markdown 弱标题/anchor 定位

## FQ2

- case id: FQ2
- query: 实验室安全员和设备管理员的职责有什么区别？
- expected answer: 安全员：安全检查、隐患排查、应急演练组织；设备管理员：设备台账、借用审批、维护保养安排。两者职责不混淆
- expected source: `lab-safety-management-handbook.md`, `## 人员职责定义`
- evidence type: ARTICLE
- pass criteria: 明确区分两个角色，不混答
- covered capability: 角色定义区分

## FQ3

- case id: FQ3
- query: equipment-borrowing-policy.yaml 里，精密仪器的单次最长借用天数是多少？
- expected answer: `7`
- expected source: `equipment-borrowing-policy.yaml`, `equipment_types[1].max_borrow_days`
- evidence type: FACT_CARD
- pass criteria: 精确返回 7，不是 14 或 3；citation 指向正确 structured path
- covered capability: 结构化路径 exact lookup

## FQ4

- case id: FQ4
- query: equipment-borrowing-policy.yaml 里，常规设备和大型设备的押金分别是多少？
- expected answer: 常规设备 `100`，大型设备 `1000`
- expected source: `equipment-borrowing-policy.yaml`, `equipment_types[0].deposit_amount` + `equipment_types[2].deposit_amount`
- evidence type: FACT_CARD
- pass criteria: 两个数值均正确，不混淆
- covered capability: sibling 字段区分

## FQ5

- case id: FQ5
- query: equipment-borrowing-policy.yaml 里，设备预约系统的 API 地址是什么？
- expected answer: `https://lab-equip.campus.edu/api/v2/borrow`
- expected source: `equipment-borrowing-policy.yaml`, `borrowing_system.api_endpoint`
- evidence type: FACT_CARD
- pass criteria: 返回完整 URL，非其他字段值
- covered capability: endpoint/URL/path 字段提取

## FQ6

- case id: FQ6
- query: equipment-borrowing-policy.yaml 里，预约系统当前的版本号是什么？
- expected answer: `v2.3.1`
- expected source: `equipment-borrowing-policy.yaml`, `borrowing_system.version`
- evidence type: FACT_CARD
- pass criteria: 返回版本号，非 max_concurrent_requests 或其他数值
- covered capability: version/identifier 字段提取

## FQ7

- case id: FQ7
- query: chemical-storage-grading.xlsx 里，B 级危险化学品的存储条件是什么？由谁保管？
- expected answer: 通风橱/防火柜，设备管理员保管
- expected source: `chemical-storage-grading.xlsx`, B 级行
- evidence type: TABLE
- pass criteria: 同时正确回答存储条件和保管人角色
- covered capability: 表格枚举/等级/角色查找

## FQ8

- case id: FQ8
- query: 如果实验室发生丙酮泄漏，应该参照哪些流程处理？并说明丙酮的存储要求
- expected answer: 处置流程：参照化学品泄漏紧急响应步骤；存储要求：通风橱、防火柜、最大 1000ml
- expected source: `chemical-storage-grading.xlsx` + `lab-emergency-response-procedures.pdf`
- evidence type: TABLE + ARTICLE
- pass criteria: 答案同时包含处置流程与存储要求，分别引用两个文档
- covered capability: 跨文档信息组合

## FQ9

- case id: FQ9
- query: 实验室的餐饮服务管理规定是什么？
- expected answer: 拒答，资料中未定义此内容
- expected source: N/A
- evidence type: N/A
- pass criteria: 明确表示资料中无相关信息，不编造
- covered capability: 应拒答但不能编造

## FQ10

- case id: FQ10
- query: lab-emergency-response-procedures.pdf 里，化学品泄漏的紧急处置步骤有哪些？
- expected answer: 列出关键步骤（如：立即疏散→隔离区域→报告安全员→穿戴防护→专业处置→记录归档）
- expected source: `lab-emergency-response-procedures.pdf`, 化学品泄漏章节
- evidence type: ARTICLE
- pass criteria: 步骤覆盖主要环节，顺序基本正确
- covered capability: PDF 内容问答

## FQ11

- case id: FQ11
- query: equipment-maintenance-schedule.csv 里，哪些设备的维护等级是"A 级"？
- expected answer: 气相色谱仪（EQ-001）等 A 级设备
- expected source: `equipment-maintenance-schedule.csv`, 维护等级 = A 的行
- evidence type: TABLE
- pass criteria: 列出所有 A 级设备，不遗漏不编造
- covered capability: CSV 表格数据查询

## FQ12

- case id: FQ12
- query: 借用精密仪器需要经过哪些审批阶段？
- expected answer: 指导教师审批 → 设备管理员审批 → 实验室主任审批
- expected source: `equipment-borrowing-policy.yaml`, `approval_chain[].stage`
- evidence type: FACT_CARD
- pass criteria: 阶段顺序正确，不遗漏
- covered capability: 流程阶段提取

## FS1

- case id: FS1
- query: `校园实验室安全管理手册`
- expected answer: 命中 `lab-safety-management-handbook.md` 主条目，排在前面
- expected source: `lab-safety-management-handbook.md`
- evidence type: ARTICLE 或 SOURCE
- pass criteria: Top5 内出现目标文档的 ARTICLE 或 SOURCE 条目
- covered capability: sourceTitle 搜索

## FS2

- case id: FS2
- query: `化学品分类存储`
- expected answer: 命中对应的弱标题切分条目，以 chunk 级身份展示
- expected source: `lab-safety-management-handbook.md`, `## 化学品分类存储` 段
- evidence type: ARTICLE (chunk)
- pass criteria: 搜索结果能定位到该章节内容，不要求标题完全一致，但应能区分于整篇文档
- covered capability: anchorTitle / 弱标题搜索

## FS3

- case id: FS3
- query: `实验室化学品分级存储管理规范`
- expected answer: 命中自身条目，排在前面
- expected source: `lab-safety-management-handbook.md`, 化学品分类存储相关条目
- evidence type: ARTICLE 或 representativeTitle
- pass criteria: 搜索结果首位或前列为该条目自身
- covered capability: representativeTitle 搜索

## FS4

- case id: FS4
- query: `安全员`、`B 级`、`精密仪器`
- expected answer: `安全员` 命中 Markdown 条目；`B 级` 命中 XLSX 条目；`精密仪器` 命中 YAML 条目
- expected source: `lab-safety-management-handbook.md` + `chemical-storage-grading.xlsx` + `equipment-borrowing-policy.yaml`
- evidence type: ARTICLE + TABLE + FACT_CARD
- pass criteria: 每个关键词在 Top5 内命中对应资料类型的条目
- covered capability: 正文关键词搜索（跨资料类型）

## FG1

- case id: FG1
- query: equipment-borrowing-policy.yaml 里精密仪器的逾期罚金是多少？常规设备的逾期罚金是多少？
- expected answer: 精密仪器 `20`，常规设备 `5`
- expected source: `equipment-borrowing-policy.yaml`, `equipment_types[1].late_fee_per_day` + `equipment_types[0].late_fee_per_day`
- evidence type: FACT_CARD
- pass criteria: 两个值正确区分，不混淆；citation 分别指向正确的 equipment_types 条目
- covered capability: 结构化字段 sibling 不混淆

## FG2

- case id: FG2
- query: equipment-borrowing-policy.yaml 里预约系统的最大并发请求数是多少？
- expected answer: `50`
- expected source: `equipment-borrowing-policy.yaml`, `borrowing_system.max_concurrent_requests`
- evidence type: FACT_CARD
- pass criteria: 返回正确的 `max_concurrent_requests: 50`；如果系统无法识别该字段，应拒答，不能让 `max_borrow_days` 或其他数值字段抢占答案
- covered capability: URL/path/version/普通数值字段保护，不被其他数值字段抢占

## FG3

- case id: FG3
- query: 实验室配置的灭火器更换周期是多久？
- expected answer: 拒答，资料中未定义灭火器更换周期
- expected source: N/A
- evidence type: N/A
- pass criteria: 明确表示资料中无此信息，不编造周期数值
- covered capability: 无证据问题必须拒答

