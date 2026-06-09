# fresh-eval-2026-09 题集

PE6 public eval，虚构项目 Library Lending System。16 FQ + 8 FS + 4 FG。

## FQ

### FQ1 — endpoint + DTO 校验
- query: 借书接口的 endpoint 是什么？一次最多借几本书？
- expected: `POST /api/v1/lending/borrow`；`@Size(min=1, max=5)` 限制 1-5 本；`LendingRequest.java` 中 `@NotNull userId`、`@Size bookIds`
- target: `LendingController.java` + `LendingRequest.java`
- covered: API endpoint + DTO 校验注解

### FQ2 — Service 分支逻辑
- query: 逾期扣分的规则是什么？1-7 天、8-14 天、15 天以上各扣几分？
- expected: `CreditServiceImpl.deductScore()`：<=7 天扣 2 分、<=14 天扣 5 分、>14 天扣 10 分。配置项 `library.credit.deduct-1-7-days=2`、`deduct-8-14-days=5`、`deduct-15-plus-days=10`
- target: `CreditServiceImpl.java` + `application.yml`
- covered: Service 阶梯分支 + 配置引用

### FQ3 — 代码 + Feature Flag
- query: 阶梯罚金的计算规则是什么？罚金有上限吗？
- expected: `FineServiceImpl`：<=7 天 1.0/天、<=14 天 2.0/天、>14 天 5.0/天。上限由 `library.fine.max-days=60` 控制
- target: `FineServiceImpl.java` + `feature-flags.md`
- covered: 代码逻辑 + Feature Flag 交叉

### FQ4 — ADR 与代码一致性
- query: ADR-002 中定义的逾期扣分阈值和代码实现是否一致？
- expected: 一致。ADR-002 定义 1-7 天扣 2 分、8-14 天扣 5 分、15+天扣 10 分。`CreditServiceImpl` 代码中 `<=7`、`<=14`、`else` 三段与 ADR 对齐
- target: `adr-002-overdue-penalty.md` + `CreditServiceImpl.java`
- covered: ADR 与代码一致性（跨 source 取证）

### FQ5 — ADR 与代码一致性
- query: 借书上限在 ADR-001 中是怎么决定的？代码实现是多少？
- expected: ADR-001：从 3 本调整为 5 本。`LendingServiceImpl` 中 `maxBooksPerUser` 校验 5 本，`LendingRequest.java` 中 `@Size(max=5)`
- target: `adr-001-lending-limits.md` + `LendingServiceImpl.java`
- covered: ADR 与代码一致性

### FQ6 — ADR 与 SQL 对齐
- query: 逻辑删除的规则是什么？CreditRecordMapper 的查询是否正确实现了逻辑删除？
- expected: ADR-003：所有表 `deleted=0`。`CreditRecordMapper.xml` 中 `WHERE user_id=#{userId} AND deleted=0`
- target: `adr-003-logical-delete.md` + `CreditRecordMapper.xml`
- covered: ADR 与 Mapper SQL 对齐

### FQ7 — Feature Flag 影响分析
- query: 信用分功能如果关闭，逾期还会扣分吗？罚金还会计算吗？
- expected: `feature-flags.md`：`library.credit.enabled=false` 时逾期不再扣分，但罚金照常计算。`CreditServiceImpl` 在 enabled=false 时跳过 `deductScore`
- target: `feature-flags.md` + `CreditServiceImpl.java`
- covered: Feature Flag 影响分析

### FQ8 — 配置差异
- query: 罚金计算在 dev 和 prod 环境下有什么区别？
- expected: prod 罚金为 dev 的 2 倍：rate-1-7(1.0→2.0)、rate-8-14(2.0→4.0)、rate-15-plus(5.0→10.0)。`application.yml` 默认值 + `application-prod.yml` 覆盖
- target: `application-config-reference.md`
- covered: dev/prod 配置差异

### FQ9 — CSV 聚合
- query: 缺陷清单里 P0 和 P1 级别的缺陷各有多少个？分别是什么状态？
- expected: P0：DEF-002(已验证)、DEF-006(已修复)、DEF-014(待修复) = 3 个。P1：DEF-001(已修复)、DEF-004(待修复)、DEF-008(待修复)、DEF-011(待修复) = 4 个
- target: `defect-list.csv`
- covered: CSV 条件过滤 + 按级别聚合

### FQ10 — XLSX 条件查询
- query: 发布检查表里还有哪些检查项没有完成？分别是哪个责任人？
- expected: CHK-04(性能测试,王工)、CHK-08(Feature Flag,张工)、CHK-11(P0未关闭,王工)、CHK-12(P1未关闭,王工)，共 4 项
- target: `release-checklist.xlsx`
- covered: XLSX 条件查询 + 责任人关联

### FQ11 — PDF 条款
- query: 根据发布验收 SOP，什么情况下必须立即回滚？
- expected: P0 级别缺陷在生产环境被发现 → 5 分钟内立即回滚。P1 影响核心功能 → 30 分钟内决策
- target: `release-acceptance-sop.pdf`
- covered: PDF 条款抽取

### FQ12 — SQL 表结构
- query: lending_records 表有哪些字段？due_date 和 return_date 的用途分别是什么？
- expected: `lending_records(id, user_id, book_id, borrow_date, due_date, return_date, deleted)`。`due_date` 应还日期，`return_date` 实际归还日期（NULL 表示未还）
- target: `schema-lending.sql`
- covered: SQL 表结构理解

### FQ13 — 排障流程
- query: 排障手册里说'借书失败'应该先排查什么？
- expected: 先检查用户已借数量和信用分是否低于 60；再检查 `max-books-per-user` 配置；最后查看 `LendingServiceImpl` 日志确认校验失败原因
- target: `troubleshooting.md`
- covered: 排障流程问答

### FQ14 — 接口文档与代码对齐
- query: API 文档中罚金计算接口的请求参数和代码实现是否一致？
- expected: 一致。文档定义 `POST /api/v1/fine/calculate`，请求体 `lendingId`(必填)+`returnDate`(可选)。`FineCalculationRequest.java` 中 `@NotNull lendingId`，`returnDate` 可选
- target: `api-spec-lending.md` + `FineController.java` + `FineCalculationRequest.java`
- covered: 接口文档与代码对齐

### FQ15 — Service 边界逻辑
- query: 借书时如果超过上限，代码会返回什么？
- expected: `IllegalArgumentException("exceeds max books per user: 5")`，`LendingServiceImpl.borrow()` 中抛出
- target: `LendingServiceImpl.java`
- covered: Service 边界逻辑

### FQ16 — 拒答
- query: 项目里有没有定义催还通知的发送规则？
- expected: 没有。项目未定义任何催还通知功能。逾期管理只有扣分和罚金，无主动通知机制
- target: 全项目
- covered: 拒答

## FS 搜索题

- FS1: `图书馆借阅管理系统` — sourceTitle → README.md
- FS2: `逾期扣分` — 关键词 → ADR-002 或 CreditServiceImpl
- FS3: `POST /api/v1/lending/borrow` — API 路径 → LendingController.java
- FS4a: `逻辑删除` — 关键词 → ADR-003 或 Mapper XML
- FS4b: `P0` — 短 token → defect-list.csv
- FS4c: `credit_score` — 代码标识符 → migration SQL 或 CreditRecordMapper
- FS4d: `library.credit.deduct-1-7-days` — 配置 key → application-config-reference.md
- FS5: `回滚条件` — 关键词 → release-acceptance-sop.pdf（发布验收 SOP 中的回滚条件条款）

## FG 保护题

### FG1 — 边界值保护
- query: 逾期恰好 14 天，扣几分？罚金日费率是多少？
- expected: 扣 5 分（≤14 天，属于 8-14 天区间），罚金 2.0/天。15 天及以上才是 10 分/5.0
- target: `CreditServiceImpl.java` + `FineServiceImpl.java`
- covered: 边界值——不让 14 天被误判为 15+天区间

### FG2 — 文档与代码不一致
- query: 发布验收 SOP 要求测试覆盖率 > 80%，代码里有没有强制检查？
- expected: 没有。SOP 定义了 >80% 的要求，但代码中无覆盖率强制检查逻辑
- target: `release-acceptance-sop.pdf` + 全项目
- covered: 文档约束 vs 代码实现

### FG3 — 拒答
- query: 项目里有没有定义用户借书优先级规则（比如教师优先于学生）？
- expected: 没有。项目未定义任何借书优先级
- target: 全项目
- covered: 拒答保护

### FG4 — 配置差异
- query: dev 和 prod 的罚金计算是否相同？如果不同，差异是多少？
- expected: 不同。prod 为 dev 的 2 倍：rate-1-7(1.0→2.0)、rate-8-14(2.0→4.0)、rate-15-plus(5.0→10.0)
- target: `application-config-reference.md` + `application-prod.yml`
- covered: 配置差异保护
