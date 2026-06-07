# Payment Service Mini

虚构的支付微服务，用于验证代码知识库导入和问答。

## 功能概述

- 创建支付订单（`POST /api/v1/payments`）
- 查询支付状态（`GET /api/v1/payments/{orderId}`）
- 申请退款（`POST /api/v1/refunds`）
- 查询退款进度（`GET /api/v1/refunds/{refundId}`）
- 幂等性保护（基于 `idempotencyKey`）

## 技术栈

- Spring Boot 3.2
- MyBatis-Plus 3.5.5
- MySQL 8.0
- Redis 7（幂等键缓存）

## 本地启动

```bash
# 使用 dev profile（连接本地 MySQL）
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# 使用 prod profile（连接生产 MySQL 集群）
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```

## 配置

- `application.yml`：公共配置（服务端口 8080、Redis 连接）
- `application-dev.yml`：开发环境 MySQL 连接（localhost:3306）
- `application-prod.yml`：生产环境 MySQL 连接（读写分离集群）

## 退款规则

- 订单创建后 **30 分钟**内可全额退款
- 超过 30 分钟但未超过 **2 小时**，收取 5% 手续费
- 超过 2 小时的订单不支持退款
