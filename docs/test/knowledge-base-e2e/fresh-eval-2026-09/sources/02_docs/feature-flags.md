# Feature Flag 说明

## library.credit.enabled
- **默认值**: `true`
- **作用**: 控制信用分功能是否启用。关闭后，逾期不再扣分，但罚金照常计算。
- **影响范围**: `CreditServiceImpl.deductScore()` 在 `enabled=false` 时跳过扣分逻辑。

## library.fine.max-days
- **默认值**: `60`
- **作用**: 罚金计算的最大逾期天数上限。即使实际逾期超过 60 天，也按 60 天计算。

## 环境差异
- `dev` 环境罚金为基准值（rate-1-7=1.0, rate-8-14=2.0, rate-15-plus=5.0）
- `prod` 环境罚金为基准值的 **2 倍**（rate-1-7=2.0, rate-8-14=4.0, rate-15-plus=10.0）
