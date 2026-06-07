# Java 代码库 Public Eval 构建报告

生成时间：2026-06-07
执行人：agentC

## 1. 目录结构

```
java-codebase-public-eval/
├── README.md
├── sources/payment-service-mini/
│   ├── pom.xml                                         # Maven 依赖
│   ├── README.md                                       # 项目说明
│   ├── src/main/java/com/example/payment/
│   │   ├── api/
│   │   │   ├── PaymentController.java                  # POST/GET /api/v1/payments
│   │   │   └── RefundController.java                   # POST/GET /api/v1/refunds
│   │   ├── service/
│   │   │   ├── PaymentService.java                     # 接口
│   │   │   ├── RefundService.java                      # 接口
│   │   │   └── impl/
│   │   │       ├── PaymentServiceImpl.java             # 金额校验 + Redis 幂等
│   │   │       └── RefundServiceImpl.java              # 退款窗口 + 手续费计算
│   │   ├── domain/
│   │   │   ├── PaymentOrder.java                       # payment_orders 实体
│   │   │   └── RefundOrder.java                        # refund_orders 实体
│   │   ├── dto/
│   │   │   ├── PaymentRequest.java                     # @DecimalMin(0.01) @DecimalMax(50000)
│   │   │   ├── PaymentResponse.java
│   │   │   ├── RefundRequest.java
│   │   │   └── RefundResponse.java
│   │   └── mapper/
│   │       ├── PaymentOrderMapper.java                 # MyBatis-Plus BaseMapper
│   │       └── RefundOrderMapper.java
│   └── src/main/resources/
│       ├── application.yml                             # 公共配置 + payment.* 参数
│       ├── application-dev.yml                         # dev MySQL
│       ├── application-prod.yml                        # prod 读写分离 MySQL
│       └── mapper/
│           ├── PaymentOrderMapper.xml                   # SQL: WHERE order_id + deleted=0
│           └── RefundOrderMapper.xml
└── eval/
    └── question-set.md
```

## 2. fixture 规模统计

| 类型 | 数量 |
|---|---|
| Java 源文件 | 12 |
| XML 文件 | 3 |
| YAML 配置 | 3 |
| Markdown | 1 |
| **总计** | **21** |

## 3. 每类文件验证什么

| 文件类型 | 数量 | 验证能力 |
|---|---|---|
| Controller（api/） | 2 | endpoint URL、HTTP method、路径变量 |
| Service 接口 + 实现 | 4 | 业务校验逻辑、金额/百分比计算、条件分支、Redis 幂等 |
| Domain Entity | 2 | 表名、字段名、列映射、逻辑删除 |
| DTO | 4 | 请求校验注解、响应字段 |
| Mapper 接口 | 2 | MyBatis 方法签名 |
| Mapper XML | 2 | SQL 查询条件（WHERE、AND） |
| application*.yml | 3 | 公共配置参数、dev/prod 差异、Redis/MySQL 连接 |
| pom.xml | 1 | Maven 依赖、版本号 |
| README.md | 1 | 项目说明、启动命令 |

## 4. FQ/FS/FG 覆盖矩阵

### FQ 问答题（12 题）

| 题号 | 能力 | 关键文件 |
|---|---|---|
| FQ1 | endpoint URL + HTTP method | PaymentController |
| FQ2 | 校验上限 + Redis 幂等 | PaymentServiceImpl + application.yml |
| FQ3 | SQL WHERE 条件 | PaymentOrderMapper.xml |
| FQ4 | Entity→DB 表列 | PaymentOrder.java |
| FQ5 | dev/prod 配置差异 | application-dev.yml + application-prod.yml |
| FQ6 | 退款条件分支 | RefundServiceImpl + application.yml |
| FQ7 | Maven 依赖清单 | pom.xml |
| FQ8 | README 启动说明 | README.md |
| FQ9 | 手续费百分比计算 | RefundServiceImpl |
| FQ10 | Controller→Service→Mapper→SQL 链 | 5 个文件 |
| FQ11 | 拒答：不存在 API | 全部 Controller |
| FQ12 | 拒答：不存在配置 | 全部配置文件 |

### FS 搜索题（6 子项）

| 题号 | 搜索维度 | 搜索词 |
|---|---|---|
| FS1 | 类名 | PaymentServiceImpl |
| FS2 | 文件名 | application-prod.yml |
| FS3 | 方法名 | processRefund |
| FS4a | 注解 | @Transactional |
| FS4b | 字段/keyword | idempotencyKey |
| FS4c | 配置 key | logic-delete-field |

### FG 保护题（3 题）

| 题号 | 保护类型 | 防止 |
|---|---|---|
| FG1 | 数值 | 注解约束值（0.01）与配置值（50000）混淆 |
| FG2 | 百分比 | `partial-refund-fee-percent=5` 与 `refund-window-minutes=30` 混淆 |
| FG3 | 拒答 | 编造不存在的 webhook 功能 |

## 5. 后续交给 agentD

1. 导入 `sources/payment-service-mini/`（作为 INTERNAL_MIRROR Git 仓库或直接上传）
2. 编译 → 等待 JavaParser AST 图谱抽取完成
3. 执行 FQ1-FQ12、FS1-FS4、FG1-FG3
4. 采集 Answer Accuracy、Search Accuracy、Citation
5. 通过线：Answer >= 10/12，Search >= 5/6，FG = 3/3

## 6. 明确声明

- [x] 虚构项目 `com.example.payment`，无真实公司名/项目名
- [x] 未修改生产代码、测试、prompt、config、schema、scripts
- [x] 未读取 hidden eval
- [x] 未提交 commit
