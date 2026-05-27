# knowledge-base-e2e Fresh Eval 设计方案

- 设计时间：2026-05-27
- 设计 Agent：agentB（治理/链路分析）
- 设计性质：只读设计，仅新增本报告
- 约束声明：本轮未修改 `src/main/java/**`、`src/test/java/**`、`src/main/resources/**`、`scripts/**`、`docs/模型绑定配置参考.md`；未清库、未重建、未导入资料；未调用真实模型；未跑 API 验收；未 stage、未 commit、未 push
- 使用条件：本报告及后续资料包、题集均为 **public fresh eval**，允许 AI 读取，用于调试与失败归因；不是 hidden eval

---

## 1. 设计目标

第一套 knowledge-base-e2e 题集（Q1-Q12、S1-S4、保护场景）已完成本轮闭环：
- Q1-Q12 验证通过 11/12（Q6 原始问法为已知 terminal field alias 局限，保护场景已通过）
- S1-S4 全部通过（S2 chunk/anchor identity 修复后）
- Q6 保护场景（6/6）全部通过
- 所有相关修复均为通用能力修复，未写入 case 特判

当前不能继续围绕第一套题集打磨，避免 public eval 过拟合。需要设计第二套 fresh eval，用于验证系统泛化能力——换领域、换文档、换问题、换答案，覆盖与第一套相同的能力面。

---

## 2. 新领域选择：校园实验室安全与设备借用规范

### 2.1 选择理由

选择"校园实验室安全与设备借用规范"作为新领域，与第一套的"Kubernetes 探针与事件响应"完全隔离：

| 维度 | 第一套 | 第二套（fresh） |
|---|---|---|
| 领域 | 云原生 / 基础设施 / 事件响应 | 校园安全 / 实验室管理 / 设备借用 |
| 核心术语 | probe, readiness, liveness, incident, severity | 化学品等级, 设备借用, 安全巡检, 紧急处置 |
| 数值字段语义 | 端口号、周期秒数、阈值 | 借用天数、押金金额、最大存量、维护周期 |
| 角色 | Situation Lead, Technical Lead, Scribe | 安全员, 设备管理员, 实验指导教师, 实验室主任 |
| 枚举/等级 | High/Medium/Low, Extended | A/B/C/D 危险等级, 一级/二级/三级维护 |

### 2.2 为什么适合覆盖 RAG 能力

1. **弱标题丰富**：安全管理手册天然包含"化学品分类存储""紧急洗眼冲淋""废弃试剂处置""实验室准入制度""安全检查周期"等弱标题，适合验证 anchor/chunk 定位
2. **结构化配置自然**：设备借用规则天然是 YAML/JSON 配置，包含嵌套字段、sibling 数值（不同设备类型的 max_borrow_days, deposit_amount, late_fee_per_day），适合验证 exact path terminal field 与 sibling 区分
3. **表格枚举密集**：化学品分级表包含危险等级（A/B/C/D）、存储条件、保管人角色等枚举值，适合验证表格事实问答
4. **跨文档关联**：化学品存储规范 + 紧急处置流程天然存在跨文档信息组合需求
5. **天然拒答场景**："实验室餐饮服务""灭火器更换周期（如果未定义）"等场景不显突兀
6. **字段保护充分**：设备借用规则中 URL/path/version 字段与数值字段（天数、金额、数量）共存，可验证字段保护

---

## 3. 资料包结构（规划，本轮不创建）

```
docs/test/knowledge-base-e2e/fresh-eval-2026-05/
├── README.md                          # 资料包说明
├── sources/
│   ├── 01_markdown/
│   │   └── lab-safety-management-handbook.md    # 实验室安全管理手册
│   ├── 02_structured/
│   │   └── equipment-borrowing-policy.yaml      # 设备借用管理规则
│   ├── 03_xlsx/
│   │   └── chemical-storage-grading.xlsx        # 化学品存储分级管理表
│   ├── 04_pdf/
│   │   └── lab-emergency-response-procedures.pdf # 实验室紧急响应处置流程
│   └── 05_csv/
│       └── equipment-maintenance-schedule.csv    # 设备维护保养计划表
├── eval/
│   └── question-set.md                # 题集（FQ1-FQ12, FS1-FS4, FG1-FG3）
└── acceptance-report.md               # 验收报告（后续 agentD 输出）
```

---

## 4. 资料类型与内容大纲

### 4.1 01_markdown — lab-safety-management-handbook.md

长 Markdown 手册，至少包含以下章节（弱标题 + 正文）：

| 章节标题 | 类型 | 内容要点 |
|---|---|---|
| `# 校园实验室安全管理手册` | 主标题 | 文档元信息、适用范围 |
| `## 化学品分类存储` | 弱标题 | 危险等级 A/B/C/D 的定义与对应存储条件 |
| `## 紧急洗眼冲淋设备` | 弱标题 | 设备位置、使用方法、检查周期 |
| `## 废弃试剂处置` | 弱标题 | 废弃流程、分类收集、移交记录 |
| `## 实验室准入制度` | 弱标题 | 人员类别、培训要求、门禁权限 |
| `## 安全检查周期` | 弱标题 | 日检/周检/月检/季检的项目与责任人 |
| `## 人员职责定义` | 二级标题 | 安全员、设备管理员、实验指导教师的职责区分 |

### 4.2 02_structured — equipment-borrowing-policy.yaml

结构化 YAML 配置，嵌套字段设计：

```yaml
# 概念示例（实际资料包中会更详细）
borrowing_system:
  api_endpoint: "https://lab-equip.campus.edu/api/v2/borrow"
  version: "v2.3.1"
  max_concurrent_requests: 50

equipment_types:
  - type: "常规设备"
    category_id: "GEN"
    max_borrow_days: 14
    deposit_amount: 100
    late_fee_per_day: 5
    approval_required: "设备管理员"
    
  - type: "精密仪器"
    category_id: "PREC"
    max_borrow_days: 7
    deposit_amount: 500
    late_fee_per_day: 20
    approval_required: "实验室主任"
    
  - type: "大型设备"
    category_id: "LARGE"
    max_borrow_days: 3
    deposit_amount: 1000
    late_fee_per_day: 50
    approval_required: "院系分管领导"

approval_chain:
  - stage: "指导教师审批"
    sla_hours: 4
  - stage: "设备管理员审批"
    sla_hours: 8
  - stage: "实验室主任审批"
    sla_hours: 24
```

关键特征：
- 嵌套路径：`equipment_types[0].max_borrow_days` 等 exact path
- sibling 字段：同一 `equipment_types` 条目下有 `max_borrow_days`、`deposit_amount`、`late_fee_per_day` 三个数值 sibling
- URL 字段：`borrowing_system.api_endpoint`
- version 字段：`borrowing_system.version`
- 普通数值字段：`borrowing_system.max_concurrent_requests`
- 流程阶段：`approval_chain[].stage`

### 4.3 03_xlsx — chemical-storage-grading.xlsx

表格至少包含以下列：

| 化学品名称 | 危险等级 | 存储条件 | 最大存放量 | 保管人角色 | 备注 |
|---|---|---|---|---|---|
| 浓硫酸 | A | 防腐蚀柜、双人双锁 | 500ml | 实验室安全员 | |
| 乙醚 | A | 防爆冰箱、避光 | 200ml | 实验室安全员 | |
| 丙酮 | B | 通风橱、防火柜 | 1000ml | 设备管理员 | |
| 氢氧化钠 | B | 防潮柜、密封 | 500g | 设备管理员 | |
| 无水乙醇 | C | 普通试剂柜、远离热源 | 2000ml | 实验指导教师 | |
| 氯化钠 | D | 普通试剂架 | 5000g | 实验指导教师 | |

关键特征：
- 枚举列：危险等级（A/B/C/D）
- 角色列：保管人角色
- 条件列：存储条件

### 4.4 04_pdf — lab-emergency-response-procedures.pdf

PDF 文档，内容结构：

- 标题层级：一级标题"实验室紧急响应处置流程"，二级标题"化学品泄漏""火灾响应""人员受伤""设备故障"
- 每个紧急类型下有编号步骤列表
- 包含响应等级表格：紧急类型、响应等级（一级/二级/三级）、响应时间要求、负责人

### 4.5 05_csv — equipment-maintenance-schedule.csv

CSV 表格，至少包含：

| 设备编号 | 设备名称 | 设备类型 | 上次维护日期 | 维护周期(天) | 下次维护日期 | 维护等级 | 负责人 |
|---|---|---|---|---|---|---|---|
| EQ-001 | 气相色谱仪 | 精密仪器 | 2026-04-15 | 90 | 2026-07-14 | A | 设备管理员 |
| EQ-002 | 离心机 | 常规设备 | 2026-05-01 | 180 | 2026-10-28 | B | 设备管理员 |
| EQ-003 | 电子天平 | 常规设备 | 2026-03-20 | 365 | 2027-03-20 | C | 实验指导教师 |

关键特征：
- 枚举列：维护等级（A/B/C）
- 日期字段
- 数值字段：维护周期

---

## 5. 题集设计

### 5.1 问答题 FQ1-FQ12

| ID | Query | Expected Answer | Expected Source | Evidence Type | 判定口径 | 覆盖能力 |
|---|---|---|---|---|---|---|
| FQ1 | "化学品分类存储"这段主要讲什么？ | 描述危险等级 A/B/C/D 的分类原则及对应存储条件，不应只重复"化学品分类存储"这 5 个字 | `lab-safety-management-handbook.md`, `## 化学品分类存储` 段 | ARTICLE (chunk) | 答案覆盖该段核心内容，非仅复述标题；citation 指向正确 chunk | Markdown 弱标题/anchor 定位 |
| FQ2 | 实验室安全员和设备管理员的职责有什么区别？ | 安全员：安全检查、隐患排查、应急演练组织；设备管理员：设备台账、借用审批、维护保养安排。两者职责不混淆 | `lab-safety-management-handbook.md`, `## 人员职责定义` | ARTICLE | 明确区分两个角色，不混答 | 角色定义区分 |
| FQ3 | equipment-borrowing-policy.yaml 里，精密仪器的单次最长借用天数是多少？ | `7` | `equipment-borrowing-policy.yaml`, `equipment_types[1].max_borrow_days` | FACT_CARD | 精确返回 7，不是 14 或 3；citation 指向正确 structured path | 结构化路径 exact lookup |
| FQ4 | equipment-borrowing-policy.yaml 里，常规设备和大型设备的押金分别是多少？ | 常规设备 `100`，大型设备 `1000` | `equipment-borrowing-policy.yaml`, `equipment_types[0].deposit_amount` + `equipment_types[2].deposit_amount` | FACT_CARD | 两个数值均正确，不混淆 | sibling 字段区分 |
| FQ5 | equipment-borrowing-policy.yaml 里，设备预约系统的 API 地址是什么？ | `https://lab-equip.campus.edu/api/v2/borrow` | `equipment-borrowing-policy.yaml`, `borrowing_system.api_endpoint` | FACT_CARD | 返回完整 URL，非其他字段值 | endpoint/URL/path 字段提取 |
| FQ6 | equipment-borrowing-policy.yaml 里，预约系统当前的版本号是什么？ | `v2.3.1` | `equipment-borrowing-policy.yaml`, `borrowing_system.version` | FACT_CARD | 返回版本号，非 max_concurrent_requests 或其他数值 | version/identifier 字段提取 |
| FQ7 | chemical-storage-grading.xlsx 里，B 级危险化学品的存储条件是什么？由谁保管？ | 通风橱/防火柜，设备管理员保管 | `chemical-storage-grading.xlsx`, B 级行 | TABLE | 同时正确回答存储条件和保管人角色 | 表格枚举/等级/角色查找 |
| FQ8 | 如果实验室发生丙酮泄漏，应该参照哪些流程处理？并说明丙酮的存储要求 | 处置流程：参照化学品泄漏紧急响应步骤；存储要求：通风橱、防火柜、最大 1000ml | `chemical-storage-grading.xlsx` + `lab-emergency-response-procedures.pdf` | TABLE + ARTICLE | 答案同时包含处置流程与存储要求，分别引用两个文档 | 跨文档信息组合 |
| FQ9 | 实验室的餐饮服务管理规定是什么？ | 拒答，资料中未定义此内容 | N/A | N/A | 明确表示资料中无相关信息，不编造 | 应拒答但不能编造 |
| FQ10 | lab-emergency-response-procedures.pdf 里，化学品泄漏的紧急处置步骤有哪些？ | 列出关键步骤（如：立即疏散→隔离区域→报告安全员→穿戴防护→专业处置→记录归档） | `lab-emergency-response-procedures.pdf`, 化学品泄漏章节 | ARTICLE | 步骤覆盖主要环节，顺序基本正确 | PDF 内容问答 |
| FQ11 | equipment-maintenance-schedule.csv 里，哪些设备的维护等级是"A 级"？ | 气相色谱仪（EQ-001）等 A 级设备 | `equipment-maintenance-schedule.csv`, 维护等级 = A 的行 | TABLE | 列出所有 A 级设备，不遗漏不编造 | CSV 表格数据查询 |
| FQ12 | 借用精密仪器需要经过哪些审批阶段？ | 指导教师审批 → 设备管理员审批 → 实验室主任审批 | `equipment-borrowing-policy.yaml`, `approval_chain[].stage` | FACT_CARD | 阶段顺序正确，不遗漏 | 流程阶段提取 |

### 5.2 搜索题 FS1-FS4

| ID | 搜索维度 | Query | Expected | 判定口径 | 覆盖能力 |
|---|---|---|---|---|---|
| FS1 | sourceTitle | `校园实验室安全管理手册` | 命中 `lab-safety-management-handbook.md` 主条目，排在前面 | Top5 内出现目标文档的 ARTICLE 或 SOURCE 条目 | sourceTitle 搜索 |
| FS2 | anchorTitle（弱标题） | `化学品分类存储` | 命中对应的弱标题切分条目，以 chunk 级身份展示 | 搜索结果能定位到该章节内容，不要求标题完全一致，但应能区分于整篇文档 | anchorTitle / 弱标题搜索 |
| FS3 | representativeTitle | `实验室化学品分级存储管理规范`（假设此为弱标题改写后的主标题） | 命中自身条目，排在前面 | 搜索结果首位或前列为该条目自身 | representativeTitle 搜索 |
| FS4 | 正文关键词 | `安全员`、`B 级`、`精密仪器` | `安全员` 命中 Markdown 条目；`B 级` 命中 XLSX 条目；`精密仪器` 命中 YAML 条目 | 每个关键词在 Top5 内命中对应资料类型的条目 | 正文关键词搜索（跨资料类型） |

### 5.3 保护场景题 FG1-FG3

| ID | Query | Expected | 判定口径 | 覆盖能力 |
|---|---|---|---|---|
| FG1 | equipment-borrowing-policy.yaml 里精密仪器的逾期罚金是多少？常规设备的逾期罚金是多少？ | 精密仪器 `20`，常规设备 `5` | 两个值正确区分，不混淆；citation 分别指向正确的 equipment_types 条目 | 结构化字段 sibling 不混淆 |
| FG2 | equipment-borrowing-policy.yaml 里预约系统的最大并发请求数是多少？ | `50` | 返回正确的 `max_concurrent_requests: 50`；如果系统无法识别该字段，应拒答，不能让 `max_borrow_days` 或其他数值字段抢占答案 | URL/path/version/普通数值字段保护，不被其他数值字段抢占 |
| FG3 | 实验室配置的灭火器更换周期是多久？ | 拒答，资料中未定义灭火器更换周期 | 明确表示资料中无此信息，不编造周期数值 | 无证据问题必须拒答 |

---

## 6. 覆盖能力矩阵

| 能力维度 | 第一套对应题 | 第二套对应题 | 说明 |
|---|---|---|---|
| Markdown 弱标题/anchor 定位 | Q1 | FQ1 | 不同文档、不同弱标题 |
| 角色定义区分 | Q3 | FQ2 | SL/TL vs 安全员/设备管理员 |
| 结构化路径 exact lookup | Q5, Q6 | FQ3 | path/port vs max_borrow_days |
| sibling 字段区分 | Q6 (保护) | FQ4, FG1 | tcpSocket.port vs periodSeconds → deposit vs late_fee |
| endpoint / URL / path 字段 | Q5, 保护场景 | FQ5, FG2 | /healthz:8080 vs api_endpoint URL |
| image / version 字段 | 保护场景 | FQ6 | goproxy:0.1 vs v2.3.1 |
| 表格枚举/角色/等级 | Q9, Q10, Q11, Q12 | FQ7, FQ11 | severity/recoverability vs danger grade/maintenance grade |
| 跨文档信息组合 | Q3 (部分) | FQ8 | incident + role → storage + emergency |
| 应拒答但不能编造 | Q4, Q8 | FQ9, FG3 | 绩效奖金/DB用户名 vs 餐饮服务/灭火器周期 |
| citation 真实支撑 | 全部 | 全部 | 同口径 |
| sourceTitle 搜索 | S1 | FS1 | 不同文档标题 |
| anchorTitle 搜索 | S2 | FS2 | "下一步计划" vs "化学品分类存储" |
| representativeTitle 搜索 | S3 | FS3 | 不同改写标题 |
| 正文关键词搜索 | S4 | FS4 | Situation Lead/healthz/Extended vs 安全员/B级/精密仪器 |
| 保护：无证据拒答 | Q4, Q8 | FG3 | 同能力，不同问题 |

**覆盖结论**：FQ1-FQ12 + FS1-FS4 + FG1-FG3 覆盖了第一套题集全部 15 个能力维度，且领域、文档、问题、答案均不重叠。

---

## 7. 红线与防过拟合说明

### 7.1 题集性质

- 本套题集是 **public fresh eval**，不是 hidden eval
- 题目和答案**允许 AI 读取**，用于调试和失败归因
- 公开的目的：让后续 agent 能基于明确的预期答案判断 PASS/FAIL，而非盲测

### 7.2 严格禁止

- **禁止**将题目、答案、关键词、文件名、case id、expected citation 写入 `src/main/java/**`、`src/main/resources/**`、prompt 模板、`config/rules.yaml`、`config/synonyms.yaml`、SQL 初始化数据或回归脚本
- **禁止**后续修复时为 FQ1-FQ12、FS1-FS4、FG1-FG3 写业务特判（如 `"实验室" → 特定路由`、`"精密仪器" → 特定字段`、`"化学品" → 特定处理`）
- **禁止**在 Java 主链硬编码中文字段语义（如 `"押金" → deposit`、`"逾期罚金" → late_fee`、`"借用天数" → max_borrow_days`）
- **禁止**为这套 fresh eval 修改 redline allowlist 或扫描规则

### 7.3 后续修复只能按失败类型归因

后续 agentD 验收后，agentB 的失败归因必须且只能使用以下类别之一：
- 资料缺失
- 编译抽取缺失
- chunk 切分问题
- 检索未召回
- rerank 排序低
- 证据已召回但回答漏点
- 引用错误
- 应拒答但编造
- 多证据冲突未处理

不允许出现：
- "FQ3 特判"
- "精密仪器 case 修复"
- "实验室领域适配"
- 类似第一套中已禁止的 case 特判模式

### 7.4 如需 hidden eval

如果后续需要 hidden eval（用于最终验收、防止过拟合），必须：
- 另建不能放入 `docs/test/` 目录的题集（因为 AI 可读取 `docs/`）
- 不在本报告中包含 hidden eval 的任何题目、答案或线索
- hidden eval 的交付物不能位于 AI 可读路径

---

## 8. 与第一套的隔离校验

| 检查项 | 第一套 | 第二套 | 隔离判定 |
|---|---|---|---|
| 核心领域 | Kubernetes + incident response | 校园实验室安全 + 设备借用 | 通过 |
| 文件名 | `probe-and-incident-operations.md`, `tcp-liveness-readiness.yaml`, `incident-response-checklists-lite.xlsx`, `grpc-liveness.yaml`, `http-liveness.yaml` | `lab-safety-management-handbook.md`, `equipment-borrowing-policy.yaml`, `chemical-storage-grading.xlsx`, `lab-emergency-response-procedures.pdf`, `equipment-maintenance-schedule.csv` | 通过 |
| 端口号 | `8080` | 无 | 通过 |
| 容器镜像 | `goproxy`, `etcd`, `registry.k8s.io` | 无 | 通过 |
| 业务术语 | probe, readiness, liveness, incident, Situation Lead, Scribe | 安全员, 设备管理员, 精密仪器, 化学品等级, 维护等级 | 通过 |
| 弱标题 | `下一步计划`, `说明`, `总结` | `化学品分类存储`, `紧急洗眼冲淋`, `废弃试剂处置`, `实验室准入制度`, `安全检查周期` | 通过 |
| 枚举/等级 | High/Medium/Low, Extended | A/B/C/D, 一级/二级/三级 | 通过 |
| URL/endpoint | 无（第一套 endpoint 问法在保护场景） | `https://lab-equip.campus.edu/api/v2/borrow` | 通过 |
| version | `3.5.1-0` (etcd) | `v2.3.1` | 通过 |

---

## 9. 后续 Agent 分工建议

### 9.1 资料包生成阶段（本轮不执行）

| 任务 | 建议 Agent | 说明 |
|---|---|---|
| 生成 5 份资料文件（Markdown/YAML/XLSX/PDF/CSV） | agentC（文档 Agent）或专门的资料生成 Agent | 仅创建文档文件，不涉及代码 |
| 资料内容审核 | agentB（治理/链路分析） | 确认资料覆盖所有能力维度，字段结构符合设计要求 |

### 9.2 入库与验收阶段（本轮不执行）

| 任务 | 建议 Agent | 说明 |
|---|---|---|
| 清库、重建 schema、导入 fresh eval 资料包 | agentD（验证/测试） | 参考 `scripts/reset-lattice-schema.sh` 与既有入库流程 |
| 端到端验收（FQ1-FQ12, FS1-FS4, FG1-FG3） | agentD | 逐题调用真实 API，记录 queryId、answer、outcome、citation |
| 验收报告输出 | agentD | 输出 `acceptance-report.md` |

### 9.3 失败归因阶段（本轮不执行）

| 任务 | 建议 Agent | 说明 |
|---|---|---|
| 逐题失败归因 | agentB（治理/链路分析） | 按 7.3 的 9 种失败类型归因，不允许写 case 特判 |
| 输出归因报告 | agentB | 输出 `*_root_cause_analysis_report.md` |

### 9.4 代码修复阶段（本轮不执行）

| 任务 | 建议 Agent | 说明 |
|---|---|---|
| 通用能力修复 | agentA | 仅在根因明确、修复范围为通用能力、agentB 归因完成后介入 |
| 修复后回归验证 | agentD | redline + mvn test + 完整 fresh eval 回归 |

### 9.5 本轮（agentB）交付后建议的执行顺序

1. **agentC 或资料 Agent**：按第 4 节大纲生成 5 份资料文件，放入 `fresh-eval-2026-05/sources/`
2. **agentC**：基于第 5 节目录生成 `fresh-eval-2026-05/eval/question-set.md` 和 `fresh-eval-2026-05/README.md`
3. **agentD**：清库 → 导入 fresh eval 资料包 → 运行 FQ1-FQ12 + FS1-FS4 + FG1-FG3 端到端验收
4. **agentB**：基于 agentD 的验收结果做失败归因
5. **agentA**：仅在根因明确且为通用能力缺陷时介入修复
6. **agentD**：修复后回归验证

---

## 10. 是否建议进入资料包生成阶段

**建议进入。**

理由：
1. 第一套题集已形成闭环，继续围绕它打磨会导致 public eval 过拟合
2. 本设计方案覆盖了第一套全部 15 个能力维度，且领域、文档、问题、答案均完全隔离
3. 资料包结构清晰，5 份文档的生成复杂度可控
4. 后续执行分工明确，不会出现多 agent 并行改代码的冲突

进入资料包生成前的前置检查：
- [x] 第一套 scoped commit 已全部收口
- [x] S2 chunk/anchor identity 修复已独立归档
- [x] 本报告已通过红线隔离校验（第 8 节）
- [ ] 资料包生成 agent 已就绪（需用户指派 agentC 或资料 Agent）

---

## 11. 未做事项声明

- 未修改 `src/main/java/**`
- 未修改 `src/test/java/**`
- 未修改 `src/main/resources/**`
- 未修改 `scripts/**`
- 未读取、输出或修改 `docs/模型绑定配置参考.md`
- 未创建资料文件（仅规划目录结构）
- 未导入资料
- 未清库、未重建库
- 未调用真实模型
- 未跑 API 验收
- 未 stage、未 commit、未 push
