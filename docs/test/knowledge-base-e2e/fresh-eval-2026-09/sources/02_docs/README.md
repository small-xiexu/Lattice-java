# 图书馆借阅管理系统

虚构的 Spring Boot 项目，管理图书借阅、还书、逾期罚金计算和信用分扣减。

## 技术栈
- Spring Boot 3.2 + MyBatis-Plus 3.5.5
- MySQL 8.0
- 逻辑删除（deleted=0）

## 模块
- **lending**：借书/还书，借书上限校验
- **credit**：逾期扣分 + 30天无逾期恢复
- **fine**：阶梯罚金计算

## 启动
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```
