# ADR-001: 借书上限调整

## 背景
系统上线初期，借书上限设为 3 本。用户反馈借阅需求超出了这个限制，现有资源不足以支撑正常的阅读需求。

## 决策
将借书上限从 3 本调整为 **5 本**。

## 考虑因素
- 用户反馈显示 67% 的用户在一个月内借阅超过 3 本书
- 图书馆当前藏书量约 5 万册，单用户 5 本的上限对库存压力在可接受范围内（预计影响 < 2% 的图书流通率）
- 同时启用了逾期信用分扣减机制来约束用户按时归还

## 影响
- `LendingRequest.java` 中的 `@Size(max=5)` 注解
- `LendingServiceImpl` 中的 `maxBooksPerUser` 校验逻辑
- `application.yml` 中的 `library.lending.max-books-per-user` 配置项
