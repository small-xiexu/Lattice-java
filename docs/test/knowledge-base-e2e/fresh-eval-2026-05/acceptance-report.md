# fresh-eval-2026-05 验收报告

## 1. 环境信息
- commit id: `45a11d5aed6b055f6da637bbb2c43da7c8b9b791`
- 启动方式: `./scripts/run-local-dev.sh`，`local-dev` profile，端口 `18082`
- JDK: `21.0.9`
- 数据库 / schema: `ai-rag-knowledge.lattice`，已先执行 `./scripts/reset-lattice-schema.sh` 清库
- 依赖容器: `vector_db`、`redis`（复用现有容器）
- 健康检查: `GET /actuator/health -> UP`
- 模型绑定状态: 已参考本地私有模型绑定配置完成配置；apiKey 已脱敏；chat/embedding/query/compile/deep_research 绑定配置成功；向量配置已启用，`embedding-3` / `2000` 维配置成功

## 2. redline 结果
- 命令: `bash scripts/scan-redline.sh special_cases_report.md`
- BLOCKER: `0`
- REVIEW: `2030`
- ALLOWLIST: `259`
- 说明: 本轮 `special_cases_report.md` 仅作为 redline 脚本覆盖输出，未做人工编辑

## 3. mvn test 结果
- 命令: `mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test`
- 结果: `BUILD SUCCESS`
- 汇总: `Tests run: 921, Failures: 0, Errors: 0, Skipped: 0`

## 4. 资料导入结果
| 文件 | 导入方式 | runId | sourceId | compileJobId | 结果 | 备注 |
|---|---|---:|---:|---|---|---|
| `lab-safety-management-handbook.md` | `/api/v1/admin/uploads` | 1 | 2 | `96f3c2ca-13d9-4bfc-9f8c-33a7e2a9e41f` | SUCCEEDED | 自动识别 `NEW_SOURCE` |
| `equipment-borrowing-policy.yaml` | `/api/v1/admin/uploads` | 2 | 3 | `ef3112ab-91bb-4021-9149-f00b2d80b561` | SUCCEEDED | 自动识别 `NEW_SOURCE` |
| `chemical-storage-grading.xlsx` | `/api/v1/admin/uploads` | 3 | 4 | `d03bf17c-2a15-48a4-8d36-4aca762b4d59` | SUCCEEDED | 自动识别 `NEW_SOURCE` |
| `lab-emergency-response-procedures.pdf` | 首次 `/uploads` 失败，随后 `sourceId=6` 重传 | 6 | 6 | `5ca7fd4e-8ae5-4189-8f6d-236132428151` | SUCCEEDED | 首次因自动资料源名超出 `varchar(32)` 失败；改用短名资料源后成功；未改代码 |
| `equipment-maintenance-schedule.csv` | `/api/v1/admin/uploads` | 5 | 5 | `2f58420b-9657-41a5-a00d-034c34e90354` | SUCCEEDED | 自动识别 `NEW_SOURCE` |
- 人工确认队列: `0`，本轮无 `WAIT_CONFIRM` / 无 `needs_human_review` 挂起
- 正式 query 验收以 5 份资料全部 `SUCCEEDED` 后的第二轮全量重跑为准

## 5. FQ1-FQ12 逐题结果
### FQ1
- query: "化学品分类存储"这段主要讲什么？
- answer: “化学品分类存储”这段主要讲实验室化学品的分级存储管理：按照危险性、反应活性、挥发性和日常使用频率，将化学品划分为 `A`、`B`、`C`、`D` 四个危险等级，并分别规定对应的存放位置、管理要求和注意事项；该节也可概括为“实验室化学品分级存储管理规范” [→ lab-safety-management-handbook.md]  它还强调不同类别试剂不能只按名称字母顺序混放，酸类、碱类、氧化剂、还原剂、易燃液体和强挥发性试剂应分区、分柜、分层管理；发现包装破损、标签缺失、结晶析出或异常气味时，应暂停使用并报告实验室安全员。[→ lab-safety-management-handbook.md]
- outcome: PARTIAL_ANSWER / LLM / SUCCESS
- citations / evidence: 校园实验室安全管理手册 (lab-safety-management-handbook.md)
- 判定: PASS
### FQ2
- query: 实验室安全员和设备管理员的职责有什么区别？
- answer: 实验室安全员偏重“安全管理与风险控制”，设备管理员偏重“设备全生命周期与借用维护管理”。两者会在月检中联合工作，但关注点不同：月检共同检查设备台账、维护记录、特种设备状态、危废暂存区域和借用归还记录。 [→ lab-safety-management-handbook.md]  | 角色 | 主要职责 | 重点关注 | |---|---|---| | 实验室安全员 | 负责安全制度执行、安全检查、隐患排查、应急演练组织、危险化学品台账核对和安全培训记录维护。 | 人员行为、化学品风险、现场环境和应急准备。 [→ lab-safety-management-handbook.md] | | 设备管理员 | 负责设备台账、借用审批、维护保养安排、设备状态标识、维修记录归档和大型设备使用预约管理。 | 设备可用性、借用合规性和维护计划执行。 [→ lab-safety-management-handbook.md] |  在具体流程上，周检由实验室安全员组织，重点检查化学品柜、洗眼冲淋设备、消防通道、安全标识和个人防护用品补充情况；月检则由实验室安全员和设备管理员联合完成，说明设备相关检查需要设备管理员参与，但安全员仍承担安全检查组织与风险管理职责。 [→ lab-safety-management-handbook.md]
- outcome: PARTIAL_ANSWER / LLM / SUCCESS
- citations / evidence: 校园实验室安全管理手册 (lab-safety-management-handbook.md)
- 判定: PASS
### FQ3
- query: equipment-borrowing-policy.yaml 里，精密仪器的单次最长借用天数是多少？
- answer: # 查询回答  ## 问题 equipment-borrowing-policy.yaml 里，精密仪器的单次最长借用天数是多少？  ## 证据 - 当前可确认的信息是：该政策将设备分为“常规设备”“精密仪器”“大型设备”三类，并分别规定了类别编号、最长借用天数、押金、逾期费用、审批人和归还检查要求。[→ equipment-borrowing-policy.yaml, equipment_types] [[equipment-borrowing-policy--equipment-borrowing-policy]] - 同一问题的补充事实是：借用流程还包含三段审批链与归还政策，包括当日归还截止时间、损坏报告要求和逾期通知渠道。[→ equipment-borrowing-policy.yaml, approval_chain; → equipment-borrowing-policy.yaml, return_policy] [[equipment-borrowing-policy--equipment-borrowing-policy]] - 同一问题的补充事实是：系统名称 = 校园实验室设备预约系统，设备借用预约系统名称。[→ equipment-borrowing-policy.yaml, borrowing_system] [[equipment-borrowing-policy--equipment-borrowing-policy]]  ## 参考说明 - **equipment borrowing policy** (equipment-borrowing-policy.yaml): API 端点 = https://lab-equip.campus.edu/api/v2/borrow，借用相关 API endpoint。 [[equipment-borrowing-policy--equipment-borrowing-policy]][→ equipment-borrowing-policy.yaml]
- outcome: SUCCESS / FALLBACK / DEGRADED
- citations / evidence: equipment borrowing policy (equipment-borrowing-policy.yaml)
- 判定: FAIL（证据已召回但回答漏点）
### FQ4
- query: equipment-borrowing-policy.yaml 里，常规设备和大型设备的押金分别是多少？
- answer: # 查询回答  ## 问题 equipment-borrowing-policy.yaml 里，常规设备和大型设备的押金分别是多少？  ## 证据 - 当前可确认的信息是：该政策将设备分为“常规设备”“精密仪器”“大型设备”三类，并分别规定了类别编号、最长借用天数、押金、逾期费用、审批人和归还检查要求。[→ equipment-borrowing-policy.yaml, equipment_types] [[equipment-borrowing-policy--equipment-borrowing-policy]] - 同一问题的补充事实是：借用流程还包含三段审批链与归还政策，包括当日归还截止时间、损坏报告要求和逾期通知渠道。[→ equipment-borrowing-policy.yaml, approval_chain; → equipment-borrowing-policy.yaml, return_policy] [[equipment-borrowing-policy--equipment-borrowing-policy]] - 同一问题的补充事实是：系统名称 = 校园实验室设备预约系统，设备借用预约系统名称。[→ equipment-borrowing-policy.yaml, borrowing_system] [[equipment-borrowing-policy--equipment-borrowing-policy]]  ## 参考说明 - **equipment borrowing policy** (equipment-borrowing-policy.yaml): API 端点 = https://lab-equip.campus.edu/api/v2/borrow，借用相关 API endpoint。 [[equipment-borrowing-policy--equipment-borrowing-policy]][→ equipment-borrowing-policy.yaml]
- outcome: SUCCESS / FALLBACK / DEGRADED
- citations / evidence: equipment borrowing policy (equipment-borrowing-policy.yaml)
- 判定: FAIL（证据已召回但回答漏点）
### FQ5
- query: equipment-borrowing-policy.yaml 里，设备预约系统的 API 地址是什么？
- answer: # 查询回答  ## 问题 equipment-borrowing-policy.yaml 里，设备预约系统的 API 地址是什么？  ## 证据 - 当前可确认的信息是：系统名称 = 校园实验室设备预约系统，设备借用预约系统名称 [[equipment-borrowing-policy--equipment-borrowing-policy]] - 同一问题的补充事实是：API 端点 = https://lab-equip.campus.edu/api/v2/borrow，借用相关 API endpoint。[→ equipment-borrowing-policy.yaml, borrowing_system] [[equipment-borrowing-policy--equipment-borrowing-policy]] - 同一问题的补充事实是：fieldPath: equipment_types[0].approval_required = 设备管理员 [[equipment-borrowing-policy--equipment-borrowing-policy]]  ## 参考说明 - **equipment borrowing policy** (equipment-borrowing-policy.yaml): “校园实验室设备预约系统”用于校园实验室设备借用预约，其借用 API 端点为 `https://lab-equip.campus.edu/api/v2/borrow`，系统版本为 `v2.3.1`。 [[equipment-borrowing-policy--equipment-borrowing-policy]][→ equipment-borrowing-policy.yaml]
- outcome: PARTIAL_ANSWER / FALLBACK / DEGRADED
- citations / evidence: equipment borrowing policy (equipment-borrowing-policy.yaml)
- 判定: PASS
### FQ6
- query: equipment-borrowing-policy.yaml 里，预约系统当前的版本号是什么？
- answer: # 查询回答  ## 问题 equipment-borrowing-policy.yaml 里，预约系统当前的版本号是什么？  ## 证据 - 当前可确认的信息是：系统名称 = 校园实验室设备预约系统，设备借用预约系统名称。[→ equipment-borrowing-policy.yaml, borrowing_system] [[equipment-borrowing-policy--equipment-borrowing-policy]] - 同一问题的补充事实是：API 端点 = https://lab-equip.campus.edu/api/v2/borrow，借用相关 API endpoint。[→ equipment-borrowing-policy.yaml, borrowing_system] [[equipment-borrowing-policy--equipment-borrowing-policy]] - 同一问题的补充事实是：归还政策要求损坏报告，即 `damage_report_required` 为 `true`。[→ equipment-borrowing-policy.yaml, return_policy] [[equipment-borrowing-policy--equipment-borrowing-policy]]  ## 参考说明 - **equipment borrowing policy** (equipment-borrowing-policy.yaml): API 端点 = https://lab-equip.campus.edu/api/v2/borrow，借用相关 API endpoint。 [[equipment-borrowing-policy--equipment-borrowing-policy]][→ equipment-borrowing-policy.yaml]
- outcome: SUCCESS / FALLBACK / DEGRADED
- citations / evidence: equipment borrowing policy (equipment-borrowing-policy.yaml)
- 判定: FAIL
### FQ7
- query: chemical-storage-grading.xlsx 里，B 级危险化学品的存储条件是什么？由谁保管？
- answer: chemical-storage-grading.xlsx 中，B 级危险化学品有两项，保管人角色均为**设备管理员**：（当前证据不足）  | 化学品名称 | 存储条件 | 保管人角色 | |---|---|---| | 丙酮 | 通风橱、防火柜 | 设备管理员 [→ chemical-storage-grading.xlsx] | | 氢氧化钠 | 防潮柜、密封 | 设备管理员 [→ chemical-storage-grading.xlsx] |
- outcome: PARTIAL_ANSWER / LLM / SUCCESS
- citations / evidence: 化学品存储分级表 (chemical-storage-grading.xlsx)
- 判定: PASS
### FQ8
- query: 如果实验室发生丙酮泄漏，应该参照哪些流程处理？并说明丙酮的存储要求
- answer: 如果实验室发生**丙酮泄漏**，应参照《实验室紧急响应处置流程》（LAB-EMG-2026-05）中的**化学品泄漏处置流程**，并按泄漏规模匹配响应等级：小范围可控且无人员伤害可按**一级**处理；较大泄漏、人员轻伤或需要现场控制升级为**二级**；重大泄漏或跨实验室影响按**三级**启动院系级协调 [→ lab-emergency-response-procedures.pdf]  ## 丙酮泄漏处置流程  1. **立即疏散非处置人员**，停止周边实验操作，避免人员继续接触泄漏源。[→ lab-emergency-response-procedures.pdf, page 1] 2. **隔离泄漏区域**，设置警示标识，关闭无关火源、电源和通风异常设备；丙酮在存储表中备注为“易燃液体”，因此尤其要注意火源控制 [→ lab-emergency-response-procedures.pdf] 3. **第一时间报告实验室安全员**，说明化学品名称、估计数量、泄漏位置和人员暴露情况 [→ lab-emergency-response-procedures.pdf] 4. **处置人员穿戴防护用品**：护目镜、防护手套、防护服和必要的呼吸防护用品 [→ lab-emergency-response-procedures.pdf] 5. **按化学品性质专业处置**：使用吸附材料、中和材料或专用收集容器进行处置，并记录归档 [→ lab-emergency-response-procedures.pdf]  ## 响应等级参照  | 等级 | 适用情形 | 时间要求 | 负责人 | |---|---|---:|---| | 一级 | 小范围可控泄漏、轻微设备异常、无人员伤害 | 5 分钟内到场确认 | 实验室安全员 [→ lab-emergency-response-procedures.pdf, table: page 2 block 1] | | 二级 | 较大泄漏、初起火情、人员轻伤或关键设备停机 | 15 分钟内完成上报和现场控制 | 设备管理员 [→ lab-emergency-response-procedures.pdf, table: page 2 block 1] | | 三级 | 火势扩大、多人受伤、重大泄漏或跨实验室影响 | 30 分钟内启动院系级协调 | 实验室主任 [→ lab-emergency-response-procedures.pdf, table: page 2 block 1] |  ## 丙酮存储要求  | 化学品 | 危险等级 | 存储条件 | 最大存放量 | 保管人角色 | 备注 | |---|---|---|---:|---|---| | 丙酮 | B | 通风橱、防火柜 | 1000ml | 设备管理员 | 易燃液体，保持容器密闭 | [[chemical-storage-grading--chemical-storage-grading]]  补充说明：现有证据仅给出丙酮在源表中的危险等级为 **B**，但未直接提供 A、B、C、D 等级的通用定义或判定规则，因此不扩展解释 B 级的制度含义。 [[chemical-storage-grading--chemical-storage-grading]]
- outcome: PARTIAL_ANSWER / LLM / SUCCESS
- citations / evidence: lab emergency response procedures / 设备故障处置流程 (lab-emergency-response-procedures.pdf)；化学品存储分级表 (chemical-storage-grading.xlsx)
- 判定: PASS
### FQ9
- query: 实验室的餐饮服务管理规定是什么？
- answer: 未找到“实验室餐饮服务管理规定”的具体内容。现有证据仅显示《校园实验室安全管理手册》用于说明实验室安全管理、人员职责、化学品存储、应急设备维护和日常检查要求；《实验室紧急响应处置流程》覆盖化学品泄漏、火灾、人员受伤、设备故障等应急场景，均未提供餐饮服务管理条款（当前证据不足）  因此，无法基于当前证据确认实验室是否允许餐饮服务、餐饮区域要求、食品饮料进入实验区限制、责任人或违规处理规则等信息（当前证据不足）
- outcome: INSUFFICIENT_EVIDENCE / LLM / SUCCESS
- citations / evidence: 校园实验室安全管理手册 (lab-safety-management-handbook.md)；lab emergency response procedures / 设备故障处置流程 (lab-emergency-response-procedures.pdf)；化学品存储分级表 (chemical-storage-grading.xlsx)
- 判定: PASS
### FQ10
- query: lab-emergency-response-procedures.pdf 里，化学品泄漏的紧急处置步骤有哪些？
- answer: lab-emergency-response-procedures.pdf 中，**化学品泄漏**的紧急处置步骤共有 6 步： [[lab-emergency-pdf--lab-emergency-response-procedures]]  | 步骤 | 处置要求 | |---:|---| | 1 | 立即疏散非处置人员，停止周边实验操作，避免人员继续接触泄漏源。 [→ lab-emergency-response-procedures.pdf] | | 2 | 隔离泄漏区域，设置警示标识，关闭无关火源、电源和通风异常设备。 [→ lab-emergency-response-procedures.pdf] | | 3 | 第一时间报告实验室安全员，并说明化学品名称、估计数量、泄漏位置和人员暴露情况。 [→ lab-emergency-response-procedures.pdf] | | 4 | 处置人员穿戴护目镜、防护手套、防护服和必要的呼吸防护用品。 [→ lab-emergency-response-procedures.pdf] | | 5 | 按化学品性质使用吸附材料、中和材料或专用收集容器进行专业处置。 [→ lab-emergency-response-procedures.pdf] | | 6 | 泄漏处置完成后清洁现场，核对废弃物标签，并将事件记录归档。 [→ lab-emergency-response-procedures.pdf] |
- outcome: PARTIAL_ANSWER / LLM / SUCCESS
- citations / evidence: lab emergency response procedures (lab-emergency-response-procedures.pdf)
- 判定: PASS
### FQ11
- query: equipment-maintenance-schedule.csv 里，哪些设备的维护等级是"A 级"？
- answer: equipment-maintenance-schedule.csv 中维护等级为 `A` 的设备是 `EQ-001`（气相色谱仪）（当前证据不足）
- outcome: PARTIAL_ANSWER / LLM / SUCCESS
- citations / evidence: equipment maintenance schedule (equipment-maintenance-schedule.csv)
- 判定: PASS
### FQ12
- query: 借用精密仪器需要经过哪些审批阶段？
- answer: 借用精密仪器需要经过设备借用审批链的三个阶段，顺序为：`指导教师审批` → `设备管理员审批` → `实验室主任审批`。其中，精密仪器的设备类别本身要求由`实验室主任`审批（当前证据不足）
- outcome: PARTIAL_ANSWER / LLM / SUCCESS
- citations / evidence: equipment borrowing policy (equipment-borrowing-policy.yaml)；校园实验室安全管理手册 / 3.2 使用要求 (lab-safety-management-handbook.md)
- 判定: PASS

## 6. FS1-FS4 逐题结果
### FS1
- query: 校园实验室安全管理手册
- Top5 evidence:

```text
1. ARTICLE | 校园实验室安全管理手册 | lab-safety-management-handbook.md | score=0.06065573770491803
2. ARTICLE | 校园实验室安全管理手册 | lab-safety-management-handbook.md | score=0.050122950819672135
3. ARTICLE | 校园实验室安全管理手册 / 3.2 使用要求 | lab-safety-management-handbook.md | score=0.04894649257552483
4. ARTICLE | 校园实验室安全管理手册 / 6.2 周检 | lab-safety-management-handbook.md | score=0.04810856079404467
5. SOURCE | lab-safety-management-handbook.md | lab-safety-management-handbook.md | score=0.021311475409836068
```
- 判定: FAIL
### FS2
- query: 化学品分类存储
- Top5 evidence:

```text
1. ARTICLE | 校园实验室安全管理手册 | lab-safety-management-handbook.md | score=0.06032522474881015
2. ARTICLE | 校园实验室安全管理手册 | lab-safety-management-handbook.md | score=0.04969460602855632
3. ARTICLE | 校园实验室安全管理手册 / 3.2 使用要求 | lab-safety-management-handbook.md | score=0.04812996031746032
4. ARTICLE | 化学品存储分级表 | chemical-storage-grading.xlsx | score=0.04974286092014807
5. SOURCE | lab-safety-management-handbook.md | lab-safety-management-handbook.md | score=0.021311475409836068
```
- 判定: FAIL
### FS3
- query: 实验室化学品分级存储管理规范
- Top5 evidence:

```text
1. ARTICLE | 校园实验室安全管理手册 | lab-safety-management-handbook.md | score=0.06032522474881015
2. ARTICLE | 校园实验室安全管理手册 | lab-safety-management-handbook.md | score=0.04969460602855632
3. ARTICLE | 校园实验室安全管理手册 / 3.2 使用要求 | lab-safety-management-handbook.md | score=0.0253125
4. ARTICLE | 化学品存储分级表 | chemical-storage-grading.xlsx | score=0.04974286092014807
5. SOURCE | lab-safety-management-handbook.md | lab-safety-management-handbook.md | score=0.021311475409836068
```
- 判定: FAIL
### FS4
- query: 分三次搜索 `安全员` / `B 级` / `精密仪器`
- Top5 evidence:

```text
安全员
1. ARTICLE | 化学品存储分级表 | chemical-storage-grading.xlsx | score=0.043611761644548525
2. ARTICLE | 校园实验室安全管理手册 / 6.2 周检 | lab-safety-management-handbook.md | score=0.049374837366640646
3. ARTICLE | 校园实验室安全管理手册 / 3.2 使用要求 | lab-safety-management-handbook.md | score=0.04931451612903226
4. ARTICLE | 校园实验室安全管理手册 | lab-safety-management-handbook.md | score=0.04927985948477752
5. ARTICLE | 校园实验室安全管理手册 | lab-safety-management-handbook.md | score=0.04387890005288207

B 级
1. ARTICLE | 化学品存储分级表 | chemical-storage-grading.xlsx | score=0.01639344262295082

精密仪器
1. ARTICLE | equipment maintenance schedule | equipment-maintenance-schedule.csv | score=0.06000520426749935
2. ARTICLE | equipment borrowing policy | equipment-borrowing-policy.yaml | score=0.05967741935483871
3. ARTICLE | equipment borrowing policy | equipment-borrowing-policy.yaml | score=0.0488780737704918
4. ARTICLE | equipment borrowing policy / 政策理解 | equipment-borrowing-policy.yaml | score=0.04774053724053724
5. FACT_CARD | 结构化键值条目 - equipment-borrowing-policy.yaml#0 | equipment-borrowing-policy.yaml | score=0.022950819672131147
```
- 判定: PASS

## 7. FG1-FG3 逐题结果
### FG1
- query: equipment-borrowing-policy.yaml 里精密仪器的逾期罚金是多少？常规设备的逾期罚金是多少？
- answer: # 查询回答  ## 问题 equipment-borrowing-policy.yaml 里精密仪器的逾期罚金是多少？常规设备的逾期罚金是多少？  ## 证据 - 当前可确认的信息是：归还政策要求损坏报告，即 `damage_report_required` 为 `true`。[→ equipment-borrowing-policy.yaml, return_policy] [[equipment-borrowing-policy--equipment-borrowing-policy]] - 同一问题的补充事实是：逾期通知渠道包括 `站内通知` 和 `邮件`。[→ equipment-borrowing-policy.yaml, return_policy] [[equipment-borrowing-policy--equipment-borrowing-policy]] - 同一问题的补充事实是：当日归还截止时间 = 17:30 [[equipment-borrowing-policy--equipment-borrowing-policy]]  ## 参考说明 - **equipment borrowing policy** (equipment-borrowing-policy.yaml): API 端点 = https://lab-equip.campus.edu/api/v2/borrow，借用相关 API endpoint。 [[equipment-borrowing-policy--equipment-borrowing-policy]][→ equipment-borrowing-policy.yaml]
- outcome: SUCCESS / FALLBACK / DEGRADED
- citations / evidence: equipment borrowing policy (equipment-borrowing-policy.yaml)
- 判定: FAIL（证据已召回但回答漏点）
### FG2
- query: equipment-borrowing-policy.yaml 里预约系统的最大并发请求数是多少？
- answer: # 查询回答  ## 问题 equipment-borrowing-policy.yaml 里预约系统的最大并发请求数是多少？  ## 证据 - 当前可确认的信息是：归还政策要求损坏报告，即 `damage_report_required` 为 `true`。[→ equipment-borrowing-policy.yaml, return_policy] [[equipment-borrowing-policy--equipment-borrowing-policy]] - 同一问题的补充事实是：逾期通知渠道包括 `站内通知` 和 `邮件`。[→ equipment-borrowing-policy.yaml, return_policy] [[equipment-borrowing-policy--equipment-borrowing-policy]] - 同一问题的补充事实是：当日归还截止时间 = 17:30 [[equipment-borrowing-policy--equipment-borrowing-policy]]  ## 参考说明 - **equipment borrowing policy** (equipment-borrowing-policy.yaml): API 端点 = https://lab-equip.campus.edu/api/v2/borrow，借用相关 API endpoint。 [[equipment-borrowing-policy--equipment-borrowing-policy]][→ equipment-borrowing-policy.yaml]
- outcome: SUCCESS / FALLBACK / DEGRADED
- citations / evidence: equipment borrowing policy (equipment-borrowing-policy.yaml)
- 判定: FAIL（证据已召回但回答漏点）
### FG3
- query: 实验室配置的灭火器更换周期是多久？
- answer: 现有证据不足以确认“实验室配置的灭火器更换周期”。已检索到《校园实验室安全管理手册》包含“应急设备维护”相关内容，但给出的证据片段未提供灭火器的具体更换周期；其他证据主要涉及紧急响应流程和化学品存储，也未说明该周期（当前证据不足）
- outcome: INSUFFICIENT_EVIDENCE / LLM / SUCCESS
- citations / evidence: 校园实验室安全管理手册 / 6.2 周检 (lab-safety-management-handbook.md)；lab emergency response procedures / 设备故障处置流程 (lab-emergency-response-procedures.pdf)；化学品存储分级表 (chemical-storage-grading.xlsx)
- 判定: PASS

## 8. 指标汇总
- Answer Accuracy: 10/15 (66.7%)
- Search Accuracy (FS1-FS4): 1/4 (25.0%)
- Recall@5: 13/15 (86.7%)
- Recall@10: 13/15 (86.7%)
- Citation Accuracy: 2/15 (13.3%)
- Abstain Accuracy: 2/2 (100.0%)
- Hallucination Count: 5

## 9. 失败 case 列表和失败类型初判
- FQ3: 证据已召回但回答漏点
- FQ4: 证据已召回但回答漏点
- FQ6: 证据已召回但回答漏点
- FS1: 检索未召回
- FS2: 检索未召回
- FS3: 检索未召回
- FG1: 证据已召回但回答漏点
- FG2: 证据已召回但回答漏点

## 10. 结论
- 是否通过 fresh eval: 未通过
- 是否存在硬编码风险: 本轮未发现为 fresh eval 资料包新增代码、脚本、prompt、allowlist 或 case 特判；存在的问题体现为现有通用能力缺口而非本轮注入式硬编码
- 是否建议进入 agentB 失败归因: 建议进入 agentB 失败归因
- 备注: 首轮预跑发生在 PDF 完整入库前，不计入正式口径；正式结论以全量入库后的第二轮重跑为准

## 11. 合规声明
- 本轮未修改 `src/main/java/**`、`src/test/java/**`、`src/main/resources/**`、`scripts/**`
- 本轮未修改 `/Users/sxie/xbk/Lattice-java/docs/模型绑定配置参考.md`
- 本轮未 stage、未 commit、未 push
- 报告未泄露真实 apiKey / token / password；报告中仅保留脱敏状态
- 运行期模型配置已参考本地私有模型绑定配置完成配置，apiKey 已脱敏
