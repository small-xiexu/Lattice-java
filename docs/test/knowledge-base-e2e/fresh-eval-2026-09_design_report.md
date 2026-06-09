# Public Eval 6 — 项目协作混合知识库评测 设计报告

设计时间：2026-06-08
执行人：agentB（只读设计/归因 Agent）
类型：题集设计方案，不落地生成文件，不导入资料，不跑模型

---

## 1. 背景与目标

### 1.1 设计动机

PE1-PE5 和 Java Codebase Eval 各验证了系统在单一资料类型上的能力——纯文档问答、纯代码问答、新领域泛化。但在真实团队协作中，知识库同时包含代码、技术文档、接口说明、配置、SQL migration、CSV 缺陷清单、PDF SOP 等多种资料源。**用户问一个问题，正确答案往往需要跨多个 source 取证。**

PE6 的目标是：**验证系统在混合资料源（代码 + 文档 + 配置 + 数据表）环境下，能否跨 source 准确定位、关联、引用证据。**

### 1.2 虚构场景

一个虚构 Java 后端团队正在开发"图书借阅管理系统（Library Lending System）"的新功能——**"逾期罚金与信用分扣减"**。知识库里同时包含：

- 正在开发中的 Java 代码（Controller/Service/DTO/Entity/Mapper）
- 需求文档、ADR（架构决策记录）、接口说明、排障手册
- 应用配置（application.yml、feature flags）
- SQL 表结构或 migration
- CSV 缺陷清单、XLSX 发布检查表
- PDF SOP 或验收说明

**所有内容均为虚构，不涉及任何真实公司、真实项目、真实数据。**

### 1.3 关键声明

PE6 是公开评测集，不是 hidden eval 的影子题集。全部题目、答案、资料公开，可用于 agentA/B/C/D 的调试、归因和修复。设计不参考任何 hidden eval 内容。

---

## 2. 与 PE1-PE5 / Java Codebase Eval 的差异

| 维度 | PE1-PE5 | Java Codebase Eval | **PE6** |
|------|---------|-------------------|------|
| **资料类型** | 纯文档（Markdown/YAML/XLSX/CSV/PDF） | 纯代码（Java/XML/YML） | **代码 + 文档 + 配置 + SQL + 数据表混合** |
| **问答模式** | 单 source 或 2 source 组合 | 单 source 或同项目多文件 | **跨代码+文档+配置的复合取证** |
| **典型问题** | "XX 的评级标准是什么" | "PaymentController 的 endpoint 是什么" | **"CreditService 的扣分阈值和 ADR 中定义的是否一致"** |
| **最大挑战** | 检索召回、结构化聚合 | 单字符值召回、调用链串联 | **跨 source 类型取证、代码与文档对齐验证** |
| **新能力验证** | — | — | **ADR 与代码一致性、DTO 校验与文档对齐、Feature Flag 影响分析、SQL 条件与逻辑删除行为** |
| **资料规模** | 5-6 文件 | ~20 文件 | **~30 文件（Java×12 + XML×3 + YML×3 + MD×4 + SQL×2 + CSV×1 + XLSX×1 + PDF×1）** |

---

## 3. 推荐资料包目录结构

```
fresh-eval-2026-09/
├── README.md
├── sources/
│   ├── 01_java/                                  # Java 后端代码
│   │   └── library-lending-system/
│   │       ├── pom.xml
│   │       ├── src/main/java/com/example/library/
│   │       │   ├── api/
│   │       │   │   ├── LendingController.java        # POST/GET /api/v1/lending/borrow, /return
│   │       │   │   ├── CreditController.java         # GET /api/v1/credit/{userId}
│   │       │   │   └── FineController.java           # POST /api/v1/fine/calculate
│   │       │   ├── service/
│   │       │   │   ├── LendingService.java            # 接口
│   │       │   │   ├── CreditService.java             # 接口（扣分、恢复）
│   │       │   │   ├── FineService.java               # 接口（罚金计算）
│   │       │   │   └── impl/
│   │       │   │       ├── LendingServiceImpl.java    # 借书上限校验 + 库存扣减
│   │       │   │       ├── CreditServiceImpl.java     # 逾期扣分逻辑 + 恢复规则
│   │       │   │       └── FineServiceImpl.java       # 阶梯罚金：1-7天/8-14天/15+天
│   │       │   ├── domain/
│   │       │   │   ├── LendingRecord.java             # lending_records 实体
│   │       │   │   ├── CreditRecord.java              # credit_records 实体
│   │       │   │   └── FineRecord.java                # fine_records 实体
│   │       │   ├── dto/
│   │       │   │   ├── LendingRequest.java            # @NotNull @Max(5)
│   │       │   │   ├── LendingResponse.java
│   │       │   │   └── FineCalculationRequest.java
│   │       │   └── mapper/
│   │       │       ├── LendingRecordMapper.java
│   │       │       ├── CreditRecordMapper.java
│   │       │       └── FineRecordMapper.java
│   │       └── src/main/resources/
│   │           ├── application.yml                    # library.* 参数
│   │           ├── application-dev.yml
│   │           ├── application-prod.yml               # 不同扣分阈值
│   │           └── mapper/
│   │               ├── LendingRecordMapper.xml        # WHERE deleted=0
│   │               ├── CreditRecordMapper.xml
│   │               └── FineRecordMapper.xml           # 按借阅记录查罚金
│   ├── 02_docs/                                  # 技术文档
│   │   ├── README.md                                 # 项目总览与模块说明
│   │   ├── adr-001-lending-limits.md                  # ADR: 借书上限从 3 本改为 5 本
│   │   ├── adr-002-overdue-penalty.md                 # ADR: 逾期扣分 + 阶梯罚金方案
│   │   ├── adr-003-logical-delete.md                  # ADR: 所有表统一逻辑删除
│   │   ├── api-spec-lending.md                        # 接口说明：借书/还书/罚金计算
│   │   ├── feature-flags.md                           # Feature Flag 说明
│   │   └── troubleshooting.md                         # 排障手册：常见问题与排查步骤
│   ├── 03_config/                                # 配置
│   │   └── application-config-reference.md            # 配置项参考（library.* 含义）
│   ├── 04_sql/                                   # 数据库
│   │   ├── schema-lending.sql                         # 表结构：lending_records, credit_records, fine_records
│   │   └── migration-002-credit-score.sql             # Migration: ALTER TABLE ADD credit_score
│   ├── 05_data/                                  # 数据文件
│   │   ├── defect-list.csv                            # 缺陷清单（按模块/严重级别/状态）
│   │   └── release-checklist.xlsx                     # 发布检查表（检查项/状态/责任人）
│   └── 06_pdf/                                   # PDF
│       └── release-acceptance-sop.pdf                  # 发布验收 SOP（含回滚条件、验收标准）
└── eval/
    └── question-set.md
```

---

## 4. 每个 Source 文件的作用与大致内容

### 4.1 Java 代码（12 个文件）

| 文件 | 关键内容 | 验证能力 |
|------|---------|------|
| `LendingController.java` | `POST /api/v1/lending/borrow`, `GET /api/v1/lending/return`，含 `@Valid` 校验 | endpoint 定位、请求校验 |
| `CreditController.java` | `GET /api/v1/credit/{userId}` | endpoint 定位 |
| `FineController.java` | `POST /api/v1/fine/calculate` | endpoint 定位 |
| `LendingService.java` | 接口定义：`borrow()`, `return()`, `getBorrowedCount()` | 接口方法签名 |
| `CreditService.java` | 接口定义：`deductScore()`, `restoreScore()` | 接口方法签名 |
| `FineService.java` | 接口定义：`calculateOverdueFine()` | 接口方法签名 |
| `LendingServiceImpl.java` | 借书上限 5 本，库存扣减，事务 | Service 分支逻辑、借书上限 |
| `CreditServiceImpl.java` | 逾期扣分：1-7天扣2分/8-14天扣5分/15+天扣10分；恢复：30天无逾期恢复3分 | **核心业务逻辑——阶梯规则** |
| `FineServiceImpl.java` | 阶梯罚金：1-7天=1元/天，8-14天=2元/天，15+天=5元/天 | **核心业务逻辑——阶梯罚金** |
| `LendingRequest.java` | `@NotNull @Max(5) bookIds`，`@NotNull userId` | **DTO 校验注解** |
| `FineCalculationRequest.java` | `@NotNull Long lendingId`, `@NotNull LocalDate returnDate` | DTO 校验注解 |
| `CreditRecordMapper.xml` | 查询：`WHERE user_id=? AND deleted=0 ORDER BY created_at DESC` | **Mapper SQL + 逻辑删除** |

### 4.2 技术文档（7 个 Markdown 文件）

| 文件 | 关键内容 | 验证能力 |
|------|---------|------|
| `README.md` | 项目总览：模块划分、技术栈（Spring Boot + MyBatis-Plus）、启动方式 | 项目概述问答 |
| `adr-001-lending-limits.md` | 决策：借书上限从 3→5，原因是用户反馈；考虑了库存压力 | **ADR 与代码一致性** |
| `adr-002-overdue-penalty.md` | 决策：逾期扣分+阶梯罚金；扣分阈值和罚金阶梯的具体数值；考虑了用户体验和图书周转率 | **ADR 与 CreditServiceImpl/FineServiceImpl 代码对齐** |
| `adr-003-logical-delete.md` | 决策：所有表统一使用 `deleted` 字段做逻辑删除，查询必须带 `deleted=0` | **ADR 与 Mapper XML 对齐** |
| `api-spec-lending.md` | 接口说明：借书/还书/罚金计算的请求参数、返回值、错误码 | **接口文档与 Controller/DTO 对齐** |
| `feature-flags.md` | Feature Flag：`library.credit.enabled`（信用分功能开关）、`library.fine.max-days`（罚金最大天数上限） | **Feature Flag 影响代码行为** |
| `troubleshooting.md` | 排障手册：借书失败、罚金计算异常、信用分不更新的排查步骤 | 排障流程问答 |

### 4.3 配置（1 个文件）

| 文件 | 关键内容 | 验证能力 |
|------|---------|------|
| `application-config-reference.md` | `library.credit.deduct-1-7-days=2`, `library.credit.deduct-8-14-days=5`, `library.fine.rate-1-7=1.0` 等，含 dev/prod 差异（prod 罚金翻倍） | **配置项含义 + dev/prod 差异** |

### 4.4 SQL（2 个文件）

| 文件 | 关键内容 | 验证能力 |
|------|---------|------|
| `schema-lending.sql` | `lending_records(id, user_id, book_id, borrow_date, due_date, return_date, deleted)`, `credit_records`, `fine_records` | 表结构、字段定义 |
| `migration-002-credit-score.sql` | `ALTER TABLE users ADD COLUMN credit_score INT DEFAULT 100` | Schema 变更 |

### 4.5 数据文件（2 个）

| 文件 | 关键内容 | 验证能力 |
|------|---------|------|
| `defect-list.csv` | 15 条缺陷记录：模块、严重级别（P0/P1/P2/P3）、状态（待修复/已修复/已验证/关闭）、处理人 | CSV 条件过滤、聚合 |
| `release-checklist.xlsx` | 15 项检查项：检查类别、检查内容、状态、责任人、备注 | XLSX 条件查询、责任人关联 |

### 4.6 PDF（1 个文件）

| 文件 | 关键内容 | 验证能力 |
|------|---------|------|
| `release-acceptance-sop.pdf` | 发布验收标准：代码审查通过、测试覆盖率>80%、缺陷清单无P0/P1未关闭、回滚条件（P0>5分钟内回滚）、验收签字流程 | PDF 条款抽取、与 CSV/XLSX 交叉验证 |

---

## 5. FQ/FS/FG 题目设计表

### 5.1 FQ 问答题（16 题）

| 题号 | 问题方向 | 目标 source | 验证能力 | 预期引用类型 |
|:---:|------|------|------|------|
| FQ1 | "借书接口的 endpoint 是什么？一次最多借几本书？" | `LendingController.java` + `LendingRequest.java` | endpoint 定位 + DTO 校验注解 | Java 源码引用 |
| FQ2 | "逾期扣分的规则是什么？逾期 1-7 天、8-14 天、15 天以上各扣几分？" | `CreditServiceImpl.java` | Service 分支逻辑提取 | Java 源码引用 |
| FQ3 | "阶梯罚金的计算规则是什么？有没有罚金上限？" | `FineServiceImpl.java` + `feature-flags.md`（max-days） | 代码逻辑 + Feature Flag 交叉 | 代码 + 配置文档 |
| FQ4 | "ADR-002 中定义的逾期扣分阈值和代码实现是否一致？" | `adr-002-overdue-penalty.md` + `CreditServiceImpl.java` | **ADR 与代码一致性** | 文档 + 源码交叉引用 |
| FQ5 | "借书上限在 ADR-001 中是怎么决定的？最终代码实现是多少？" | `adr-001-lending-limits.md` + `LendingServiceImpl.java` | **ADR 与代码一致性** | 文档 + 源码交叉引用 |
| FQ6 | "逻辑删除的规则是什么？CreditRecordMapper 的查询是否正确实现了逻辑删除？" | `adr-003-logical-delete.md` + `CreditRecordMapper.xml` | **ADR 与 Mapper SQL 对齐** | 文档 + SQL 交叉引用 |
| FQ7 | "信用分功能在生产环境是否默认开启？如果关闭会有什么影响？" | `feature-flags.md` + `CreditServiceImpl.java` | Feature Flag 影响分析 | 配置文档 + 代码 |
| FQ8 | "罚金计算在 dev 和 prod 环境下有什么区别？" | `application-config-reference.md` | dev/prod 配置差异 | 配置文档 |
| FQ9 | "缺陷清单里，P0 和 P1 级别的缺陷各有多少个？分别是什么状态？" | `defect-list.csv` | CSV 条件过滤 + 聚合 | CSV 数据 |
| FQ10 | "发布检查表里，还有哪些检查项没有完成？分别是哪个责任人？" | `release-checklist.xlsx` | XLSX 条件查询 | XLSX 数据 |
| FQ11 | "根据发布验收 SOP，什么情况下必须立即回滚？" | `release-acceptance-sop.pdf` | PDF 条款抽取 | PDF 引用 |
| FQ12 | "lending_records 表有哪些字段？due_date 和 return_date 的用途分别是什么？" | `schema-lending.sql` | SQL 表结构理解 | SQL 引用 |
| FQ13 | "排障手册里说'借书失败'应该先排查什么？" | `troubleshooting.md` | 排障流程问答 | 文档引用 |
| FQ14 | "接口文档里定义的罚金计算 API 的请求参数和代码实现是否一致？" | `api-spec-lending.md` + `FineController.java` + `FineCalculationRequest.java` | **接口文档与代码对齐** | 文档 + 源码交叉引用 |
| FQ15 | "借书时如果超过上限，代码会返回什么？" | `LendingServiceImpl.java` | Service 边界逻辑 | Java 源码引用 |
| FQ16 | "项目里有没有定义催还通知的发送规则？" | 全项目 | **拒答**——项目未定义催还通知 | 无（应拒答） |

### 5.2 FS 搜索题（8 个子项）

| 题号 | 搜索词 | 搜索维度 | 期望命中 | 验证能力 |
|:---:|------|------|------|------|
| FS1 | `图书馆借阅管理系统` | sourceTitle | README.md 或项目总览 article | sourceTitle 搜索 |
| FS2 | `逾期扣分` | anchorTitle / 正文关键词 | ADR-002 或 CreditServiceImpl | 跨文档关键词搜索 |
| FS3 | `POST /api/v1/lending/borrow` | API 路径 | LendingController.java | API endpoint 搜索 |
| FS4a | `逻辑删除` | 正文关键词 | ADR-003 或 Mapper XML | 关键词搜索 |
| FS4b | `P0` | 正文关键词 | defect-list.csv 中 P0 缺陷 | 短 token 搜索 |
| FS4c | `credit_score` | 代码标识符 | CreditRecordMapper.xml 或 migration SQL | 代码标识符搜索 |
| FS4d | `library.credit.deduct-1-7-days` | 配置 key | application-config-reference.md | 配置 key 精确搜索 |

### 5.3 FG 保护题（4 题）

| 题号 | 问题方向 | 验证能力 | 为什么是保护题 |
|:---:|------|------|------|
| FG1 | "CreditService 的扣分规则中，1-7 天扣 2 分、8-14 天扣 5 分、15+天扣 10 分——如果逾期恰好 14 天，扣几分？" | 数值边界保护 | **边界值保护**——不让用户混淆区间边界（14天属于8-14天还是15+天？代码用 `<=` 和 `>` 需要精确匹配） |
| FG2 | "发布验收 SOP 要求测试覆盖率 > 80%，但代码里有没有强制检查这个阈值？" | 文档与代码不一致 | **文档约束 vs 代码实现保护**——SOP 要求 >80% 但代码中可能无强制检查 |
| FG3 | "项目里有没有定义用户的借书优先级规则（比如教师优先于学生）？" | 拒答保护 | **拒答保护**——项目未定义借书优先级 |
| FG4 | "dev 和 prod 的罚金计算是否相同？如果不同，差异是多少？" | 配置差异提取 | **配置保护**——prod 翻倍，不能让 dev 的值抢占 |

---

## 6. FQ/FS/FG 覆盖矩阵

| 能力维度 | FQ 覆盖 | FS 覆盖 | FG 覆盖 |
|------|:---:|:---:|:---:|
| API endpoint 定位 | FQ1, FQ14 | FS3 | — |
| DTO 校验注解与文档对齐 | FQ1, FQ14 | — | — |
| Service 分支逻辑提取 | FQ2, FQ15 | — | FG1 |
| ADR 与代码一致性 | **FQ4, FQ5, FQ6** | — | — |
| Mapper SQL 条件 + 逻辑删除 | FQ6 | FS4a | — |
| Feature Flag 影响分析 | FQ3, FQ7 | — | — |
| 配置项 dev/prod 差异 | FQ8 | FS4d | FG4 |
| CSV 条件过滤/聚合 | FQ9 | FS4b | — |
| XLSX 条件查询 | FQ10 | — | — |
| PDF 条款抽取 | FQ11 | — | FG2 |
| SQL 表结构理解 | FQ12 | — | — |
| 接口文档与代码对齐 | FQ14 | FS3 | — |
| 排障流程 | FQ13 | — | — |
| 跨 source 取证 | FQ4, FQ5, FQ6, FQ14 | — | FG2 |
| 拒答 | FQ16 | — | FG3 |
| 搜索（多维度） | — | FS1-FS4d | — |
| 边界值/配置保护 | — | — | FG1, FG4 |

---

## 7. 红线说明

- 所有题目、答案、资料内容**不得写入 `src/main/java/**`、prompt、配置、SQL、scripts、allowlist**
- 不得为 PE6 的具体类名、方法名、配置 key（如 `CreditServiceImpl`、`library.credit.deduct-1-7-days`）做硬编码映射
- PE6 不得参考任何 hidden eval 的内容
- 虚构项目名称"Library Lending System"、包名 `com.example.library` 均为通用示例，不涉及真实公司
- 禁止把 PE6 设计成 hidden eval 的影子题集

---

## 8. 推荐给 agentC 的落地提示词草案

```text
你现在是 agentC（文档/题集落地 Agent）。

任务：按 PE6 设计方案生成资料包，包含 ~30 个 source 文件 + question-set.md + README.md。

资料包目录：docs/test/knowledge-base-e2e/fresh-eval-2026-09/

Source 文件分类：
1. 01_java/ — 12 个 Java 文件 + 3 个 Mapper XML + 3 个 YML 配置
   - 虚构项目：library-lending-system
   - Controller 含 @PostMapping/@GetMapping 注解和路径
   - Service 含完整的逾期扣分阶梯逻辑和罚金阶梯逻辑
   - DTO 含 @NotNull/@Max/@DecimalMin 校验注解
   - Mapper XML 含 WHERE deleted=0 逻辑删除条件
   - application-prod.yml 中的罚金参数为 dev 的 2 倍

2. 02_docs/ — 7 个 Markdown 文档
   - 3 份 ADR（借书上限、逾期扣分、逻辑删除）
   - 接口说明、Feature Flag 说明、排障手册、项目 README

3. 03_config/ — 1 个配置参考文档

4. 04_sql/ — 2 个 SQL 文件（表结构 + migration）

5. 05_data/ — defect-list.csv（15条）+ release-checklist.xlsx（15项）

6. 06_pdf/ — release-acceptance-sop.pdf（含回滚条件、验收标准）

eval/question-set.md — 16 FQ + 8 FS + 4 FG

红线：虚构内容，不涉及真实公司/项目；不参考 hidden eval
```

---

## 9. 推荐给 agentD 的验收提示词草案

```text
你现在是 agentD（验证/测试 Agent）。

任务：清库 → 导入 PE6 资料 → 编译 → 执行完整验收。

前置条件：PE1-PE5 + Java Codebase Eval 保护回归已通过

验收指标：
| 指标 | 通过线 |
|------|:---:|
| Answer Accuracy | >= 80%（16 题中 >= 13 题完全正确或 PARTIAL 可接受） |
| Search Accuracy | >= 7/8 |
| Citation Accuracy | >= 70%（跨 source 引用必须指向正确的 source 文件） |
| Abstain Accuracy | FQ16/FG3 必须正确拒答 |
| Hallucination Count | <= 1 |

验收重点：
1. FQ4/FQ5/FQ6：ADR 与代码的一致性——citation 必须同时引用文档和源码
2. FQ3/FQ7：Feature Flag 影响分析——必须引用配置文档
3. FG1：边界值保护——逾期 14 天应扣 5 分（8-14天区间），不能混淆为 15+天
4. FS3：API endpoint 搜索——"/api/v1/lending/borrow" 必须命中 LendingController.java
5. Hallucination：不能编造不存在的催还通知规则（FQ16）

输出报告：docs/test/knowledge-base-e2e/fresh-eval-2026-09_acceptance_report.md
```

---

## 10. 明确声明

- [x] 本轮为纯设计，未生成任何资料文件
- [x] 未修改生产代码、测试、prompt、config、schema、scripts
- [x] 未导入资料、未清库、未跑模型
- [x] 未读取 hidden eval
- [x] 未提交 commit
- [x] PE6 是完全独立的公开评测，虚构项目名和包名均为通用示例
- [x] PE6 的核心创新是"跨代码+文档的复合取证"——验证 ADR 与代码一致性、DTO 校验与文档对齐、Feature Flag 与行为关联
- [x] 设计方案遵守 eval-validation-roadmap.md 的全部红线
