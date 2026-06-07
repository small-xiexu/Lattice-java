# Java 代码库 Public Eval 题集

本题集为 Java 后端代码知识库的 public eval，验证代码导入、搜索、问答和 citation。包含 FQ1-FQ12、FS1-FS4（含子项）、FG1-FG3。

## FQ 问答题

### FQ1 — Endpoint URL 查询

- query: Payment Service 里创建支付订单的 API 是什么？用哪个 HTTP 方法？
- target: `PaymentController.java`
- expected: `POST /api/v1/payments`，方法 `createPayment`，注解 `@PostMapping`
- covered capability: Controller endpoint URL + HTTP method 定位

### FQ2 — Service 逻辑查询

- query: 创建支付订单时，金额的校验上限是多少？设置了什么 Redis 幂等机制？
- target: `PaymentServiceImpl.java` + `application.yml`
- expected: 金额上限 `50000`（`max-order-amount`）；Redis 幂等键前缀 `idem:pay:`，TTL `86400` 秒
- covered capability: Service 校验逻辑 + 配置值交叉引用

### FQ3 — Mapper SQL 查询

- query: PaymentOrderMapper 的 `selectByOrderId` 查询里，WHERE 条件有哪些？
- target: `PaymentOrderMapper.xml`
- expected: `order_id = #{orderId}` AND `deleted = 0`（逻辑删除）
- covered capability: MyBatis XML SQL 条件提取

### FQ4 — Entity 字段查询

- query: PaymentOrder 对应哪个数据库表？有哪些字段？
- target: `PaymentOrder.java`
- expected: 表 `payment_orders`；字段 id、order_id、merchant_id、amount、currency、channel、status、idempotency_key、created_at、updated_at、deleted（逻辑删除）
- covered capability: Entity → DB table/column 映射

### FQ5 — 配置文件差异查询

- query: dev 环境和 prod 环境的 MySQL 连接有什么不同？
- target: `application-dev.yml` + `application-prod.yml`
- expected: dev 连接 `localhost:3306/payment_dev`（用户名 `dev_user`）；prod 连接 `prod-db-master.internal:3306/payment_prod` + 读写分离从库 `prod-db-slave.internal`；prod 密码从环境变量读取
- covered capability: 多环境配置差异对比

### FQ6 — 退款规则查询

- query: 退款服务里，什么条件下可以全额退款？什么条件下需要收手续费？
- target: `RefundServiceImpl.java` + `application.yml`
- expected: 30 分钟内全额退款（`refund-window-minutes`）；30-120 分钟收 5% 手续费（`partial-refund-window-minutes` / `partial-refund-fee-percent`）；超过 120 分钟不支持退款
- covered capability: Service 条件分支逻辑 + 配置参数

### FQ7 — pom.xml 依赖查询

- query: 这个项目用到了哪些 Spring Boot Starter 和第三方库？
- target: `pom.xml`
- expected: `spring-boot-starter-web`、`spring-boot-starter-validation`、`mybatis-plus-spring-boot3-starter`（3.5.5）、`mysql-connector-j`、`spring-boot-starter-data-redis`；Java 版本 17；Spring Boot 版本 3.2.0
- covered capability: Maven 依赖清单提取

### FQ8 — README 启动说明查询

- query: 这个项目的 README 里，本地启动用哪个命令？需要什么 profile？
- target: `README.md`
- expected: `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`；需要 dev profile 连本地 MySQL
- covered capability: README 操作说明提取

### FQ9 — 退款金额和手续费

- query: 如果用户退款 1000 元，超过了 30 分钟但没超过 2 小时，手续费是多少？实际退回多少？
- target: `RefundServiceImpl.java` + `application.yml`
- expected: 手续费 5% = 50 元；实际退回 950 元；退款状态 `PARTIAL_REFUND`
- covered capability: 条件逻辑 + 百分比计算

### FQ10 — 跨文件能力：Controller→Service→Mapper 链

- query: 查询支付订单的完整调用链是什么？从 Controller 到数据库 SQL。
- target: `PaymentController.java` → `PaymentService.java` → `PaymentServiceImpl.java` → `PaymentOrderMapper.java` → `PaymentOrderMapper.xml`
- expected: `GET /api/v1/payments/{orderId}` → `PaymentController.getPaymentStatus()` → `PaymentService.findByOrderId()` → `PaymentServiceImpl.findByOrderId()` → `PaymentOrderMapper.selectByOrderId()` → SQL: `SELECT ... FROM payment_orders WHERE order_id = #{orderId} AND deleted = 0`
- covered capability: 跨文件调用链追踪

### FQ11 — 不存在接口的正确拒答

- query: Payment Service 有没有提供批量支付的 API？
- target: 所有 Controller 文件
- expected: 没有。项目中不存在批量支付接口。只有单个创建（`POST /api/v1/payments`）和查询（`GET /api/v1/payments/{orderId}`）
- covered capability: 证据不足拒答（不编造不存在的 API）

### FQ12 — 不存在配置的正确回答

- query: application.yml 里有没有配置 Kafka 或 RabbitMQ 的连接信息？
- target: `application.yml` 等配置文件
- expected: 没有。项目使用 Redis 做幂等，未集成消息队列
- covered capability: 配置缺失时的正确回答

## FS 搜索题

### FS1 — sourceTitle 搜索

- query: PaymentServiceImpl
- dimension: sourceTitle / 类名
- expected: 搜索结果首位命中 `PaymentServiceImpl.java`
- covered capability: 类名搜索

### FS2 — 配置文件命名搜索

- query: application-prod.yml
- dimension: sourceTitle / 文件名
- expected: 命中 `application-prod.yml`，不混淆 `application-dev.yml`
- covered capability: 同名前缀文件区分

### FS3 — 方法名搜索

- query: processRefund
- dimension: 方法名 / AST 实体
- expected: 命中 `RefundServiceImpl.processRefund()`
- covered capability: 方法名搜索 + 代码图谱定位

### FS4 — 关键词搜索

- 4a: `@Transactional` → 命中带事务注解的方法（`PaymentServiceImpl.processPayment`, `RefundServiceImpl.processRefund`）
- 4b: `idempotencyKey` → 命中 `PaymentOrder.java` 字段 + `PaymentRequest.java` DTO + `PaymentServiceImpl.java` 幂等逻辑
- 4c: `logic-delete-field` → 命中 `application.yml` 的 MyBatis-Plus 逻辑删除配置

## FG 保护题

### FG1 — 数值保护

- query: 创建支付订单的金额最小值和最大值是多少？
- target: `PaymentRequest.java` + `application.yml`
- expected: 最小值 `0.01`（`@DecimalMin`），最大值 `50000`（`max-order-amount`）
- covered capability: 注解约束值 + 配置值不混淆

### FG2 — 百分比保护

- query: 退款手续费的百分比配置项叫什么？值是多少？
- target: `application.yml`
- expected: `payment.partial-refund-fee-percent`，值 `5`
- covered capability: 百分比配置值精确提取，不与 `refund-window-minutes`（30）混淆

### FG3 — 拒答保护

- query: 这个项目有没有实现支付回调通知（webhook）？
- target: 所有 Controller/Service
- expected: 没有。项目中不存在 webhook 或支付回调相关代码
- covered capability: 不编造不存在的功能
