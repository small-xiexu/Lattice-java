# 应用配置参考

所有 `library.*` 前缀的配置项：

| 配置项 | 默认值 | dev | prod | 说明 |
|-------|-------|-----|------|------|
| `library.lending.max-books-per-user` | 5 | 5 | 5 | 单用户最大借书数量 |
| `library.lending.max-borrow-days` | 30 | 30 | 30 | 最大借阅天数 |
| `library.credit.enabled` | true | true | true | 信用分功能开关 |
| `library.credit.deduct-1-7-days` | 2 | 2 | 2 | 逾期1-7天扣分 |
| `library.credit.deduct-8-14-days` | 5 | 5 | 5 | 逾期8-14天扣分 |
| `library.credit.deduct-15-plus-days` | 10 | 10 | 10 | 逾期15+天扣分 |
| `library.credit.restore-after-clean-days` | 30 | 30 | 30 | 无逾期恢复所需天数 |
| `library.credit.restore-amount` | 3 | 3 | 3 | 每次恢复信用分数量 |
| `library.fine.rate-1-7` | 1.0 | 1.0 | **2.0** | 逾期1-7天日罚金 |
| `library.fine.rate-8-14` | 2.0 | 2.0 | **4.0** | 逾期8-14天日罚金 |
| `library.fine.rate-15-plus` | 5.0 | 5.0 | **10.0** | 逾期15+天日罚金 |
| `library.fine.max-days` | 60 | 60 | 60 | 罚金计算最大天数上限 |
