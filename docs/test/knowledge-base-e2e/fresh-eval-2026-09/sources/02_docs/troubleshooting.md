# 排障手册

## 借书失败
1. 先检查用户当前已借数量：`GET /api/v1/credit/{userId}` 确认信用分是否低于 60
2. 检查 `library.lending.max-books-per-user` 配置值
3. 查看 `LendingServiceImpl.borrow()` 日志，确认是上限校验还是库存校验失败
4. 若数据库连接异常，检查 `application.yml` 中的数据源配置

## 罚金计算异常
1. 确认 `library.fine.rate-*` 配置项未被误修改
2. 检查 `library.fine.max-days` 是否被设为 0
3. 查看 `FineServiceImpl` 日志，确认 `due_date` 和 `return_date` 差值计算是否正确
4. dev/prod 环境差异检查：prod 罚金为 dev 的 2 倍

## 信用分不更新
1. 确认 `library.credit.enabled=true`
2. 检查 `CreditRecordMapper.getCurrentScore()` 是否正确聚合了 `change_amount`
3. 检查 `credit_records` 表中是否有对应的扣分/恢复记录
