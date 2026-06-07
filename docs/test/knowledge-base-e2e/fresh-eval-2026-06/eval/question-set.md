# fresh-eval-2026-06 题集

本题集为第三套 public eval，领域为采购合同 / 售后 SLA / 付款条款。包含 FQ1-FQ12、FS1-FS4、FG1-FG3。

题目与标准答案用于公开调试和失败归因，**不得写入生产代码、prompt、配置、脚本或 allowlist**。

## FQ 问答题

### FQ1 — 条款归纳

- case id: FQ1
- query: 主合同里，采购内容包含哪些项？
- expected answer: 应包含 4 项：高性能计算服务器（HS-8800G，10 台）、万兆核心交换机（NS-48XG，4 台）、企业级存储阵列（SA-960T，2 台）、三年原厂维保服务（1 批）。不应遗漏任一项
- expected source: `procurement-contract.pdf`，第二条 2.1 款
- evidence type: ARTICLE
- pass criteria: 覆盖全部 4 项采购内容
- covered capability: PDF 条款抽取与概括

### FQ2 — 数值精确提取

- case id: FQ2
- query: 主合同的付款条件是什么？预付款比例是多少？
- expected answer: 分三期：预付款 30%（150,000 元，签约后 10 个工作日）、验收款 60%（300,000 元，验收合格后 30 个工作日）、尾款 10%（50,000 元，质保期满后 15 个工作日）。注意：这是主合同的原始比例，补充协议已修改为 50%/50%
- expected source: `procurement-contract.pdf`，第三条 3.2 款
- evidence type: ARTICLE
- pass criteria: 百分比和金额均正确；如同时提及补充协议的修改则更佳
- covered capability: 金额、百分比精确提取

### FQ3 — 条件→结果链

- case id: FQ3
- query: 如果乙方交付延期超过 30 天，违约金怎么计算？
- expected answer: 每延期一日按合同总金额（¥500,000）的 0.05% 支付违约金，即每日 250 元。30 天累计为 7,500 元。违约金总额上限为合同总金额的 10%（¥50,000）
- expected source: `procurement-contract.pdf`，第六条 6.1 款
- evidence type: ARTICLE
- pass criteria: 正确计算每日违约金金额和 30 天累计值；提及 10% 上限
- covered capability: 条件→结果链，百分比计算

### FQ4 — YAML 多字段聚合

- case id: FQ4
- query: 合同总金额是多少？分几期支付？每期各多少？
- expected answer: 总金额 ¥500,000（伍拾万元整）。按补充协议：分两期——预付款 50%（¥250,000）+ 验收款 50%（¥250,000）。取消原合同的尾款
- expected source: `payment-terms.yaml` 或 `supplementary-agreement.pdf`
- evidence type: FACT_CARD / ARTICLE
- pass criteria: 总金额正确；如引用补充协议的两期方案而非主合同的三期方案则为更优
- covered capability: 结构化路径 + 聚合 + 跨文档优先级

### FQ5 — YAML 嵌套路径精确提取

- case id: FQ5
- query: 质保期是多少个月？质保期内乙方需要在几小时内响应？
- expected answer: 质保期 48 个月（补充协议延长后）；响应时间 4 小时内；到达现场 24 小时内；修复或提供替代方案 72 小时内
- expected source: `payment-terms.yaml` 或 `supplementary-agreement.pdf`
- evidence type: FACT_CARD / ARTICLE
- pass criteria: 质保期和响应时间均正确；48 个月而非主合同的 36 个月
- covered capability: 嵌套路径精确提取 + 跨文档字段更新

### FQ6 — YAML 百分比多字段

- case id: FQ6
- query: 延期罚金的每日比例和上限比例分别是多少？
- expected answer: 每日罚金比例 0.05%（万分之五），上限比例 10%。甲方逾期付款的每日罚金比例为 0.03%
- expected source: `payment-terms.yaml`，`late_penalty` + `buyer_late_penalty`
- evidence type: FACT_CARD
- pass criteria: 乙方延期罚金 0.05%/日和上限 10% 均正确；甲方罚金 0.03% 正确
- covered capability: 百分比提取 + 多字段区分（乙方罚金 vs 甲方罚金）

### FQ7 — 表格条件查询

- case id: FQ7
- query: 金牌售后服务的响应时间和可用性分别是什么？
- expected answer: 服务器硬件（金牌）：响应 < 15 分钟，可用性 99.9%；软件系统（金牌）：响应 < 5 分钟，可用性 99.99%
- expected source: `after-sales-sla-metrics.xlsx`，服务器硬件行 + 软件系统行（均为金牌）
- evidence type: ARTICLE
- pass criteria: 正确列出两个金牌服务的响应时间和可用性；两个服务的数值不混淆
- covered capability: 表格条件查询 + 多列值提取

### FQ8 — 表格排序 + 责任方

- case id: FQ8
- query: 哪个服务的修复时间最短？谁负责？
- expected answer: 软件系统（金牌），修复时间 < 2 小时，由乙方负责
- expected source: `after-sales-sla-metrics.xlsx`，软件系统行
- evidence type: ARTICLE
- pass criteria: 正确识别修复时间最短的服务（< 2 小时）和责任方（乙方）
- covered capability: 表格排序 + 责任方识别

### FQ9 — CSV 条件判断

- case id: FQ9
- query: 付款计划中，哪些付款已逾期？
- expected answer: 根据当前日期判断。期数 1（合同签订，计划日期 2026-05-20）已支付。如当前日期已超过其他待支付项的计划日期，则应标记为逾期。期数 2 计划日期 2026-08-15，是否逾期取决于验收时间
- expected source: `payment-schedule.csv`
- evidence type: ARTICLE
- pass criteria: 正确区分已支付/待支付/逾期状态；基于计划日期和实际支付日期做判断
- covered capability: CSV 条件判断 + 日期比较 + 状态区分

### FQ10 — 跨文档冲突

- case id: FQ10
- query: 主合同的预付款比例是 30%，但补充协议改成了多少？
- expected answer: 补充协议第二条将预付款改为 50%（¥250,000），验收款改为 50%（¥250,000），取消原合同的 10% 尾款。补充协议声明"与主合同冲突时，以本补充协议为准"
- expected source: `supplementary-agreement.pdf`，第二条 + `procurement-contract.pdf`，第三条
- evidence type: ARTICLE
- pass criteria: 明确识别补充协议修改了付款比例（50% vs 30%）；引用"以补充协议为准"的优先级声明；至少提及尾款被取消
- covered capability: 多文档冲突检测 + 优先级判断

### FQ11 — 拒答（补充协议未涉及）

- case id: FQ11
- query: 补充协议里有没有提到质量不合格的处理方式？
- expected answer: 有。补充协议第四条（新增条款）明确：同一故障出现 3 次以上，乙方无条件更换全新设备（15 个工作日内）；因设备质量导致业务中断超 4 小时，赔偿上限为合同总金额 20%
- expected source: `supplementary-agreement.pdf`，第四条
- evidence type: ARTICLE
- pass criteria: 正确回答补充协议中定义的质量不合格处理方式
- covered capability: 新增条款定位（补充协议独有内容）

### FQ12 — 拒答（合同中未定义）

- case id: FQ12
- query: 合同里有没有定义保密条款的违约金？
- expected answer: 没有。主合同第七条定义了保密义务（期限 5 年）和违反保密义务的赔偿责任，但未定义具体的违约金金额或计算方式
- expected source: `procurement-contract.pdf`，第七条
- evidence type: ARTICLE
- pass criteria: 明确说明保密条款存在但未定义具体违约金金额；不应编造违约金数字
- covered capability: 条款存在但子条款未定义时的拒答/精确说明

## FS 搜索题

### FS1 — sourceTitle 搜索

- case id: FS1
- query: 信息技术设备采购与维护合同
- search dimension: sourceTitle
- 期望：主合同 PDF article 排在首位；结果列表中能看到明确的合同来源标识
- pass criteria: 排名首位命中主合同相关条目，而非 SLA 表或 CSV

### FS2 — 条款标题/anchorTitle 搜索

- case id: FS2
- query: 违约责任
- search dimension: clause title / anchorTitle
- 期望：能定位到主合同"第六条 违约责任"的相关条目
- pass criteria: 搜索结果中包含违约责任条款内容，而非其他条款的模糊匹配

### FS3 — 条款号/关键词搜索

- case id: FS3
- query: 售后 SLA 指标表
- search dimension: representativeTitle / 正文关键词
- 期望：能命中 XLSX SLA 表的条目
- pass criteria: 搜索结果首位或前列出现 SLA 相关条目

### FS4 — 多维度搜索

- case id: FS4a
- query: 99.99%
- search dimension: 正文关键词
- 期望：命中 SLA 表格中"软件系统 金牌"行
- pass criteria: 搜索结果中包含 99.99% 可用性的条目

- case id: FS4b
- query: 质保期
- search dimension: anchorTitle / 正文关键词
- 期望：命中 payment-terms.yaml 或合同质保条款的相关条目
- pass criteria: 能定位到 48 个月质保期的内容

- case id: FS4c
- query: 乙方
- search dimension: 正文关键词
- 期望：命中合同正文中的乙方责任条款
- pass criteria: 搜索结果中乙方相关条目排在前面

## FG 保护题

### FG1 — 数值保护

- case id: FG1
- query: 违约金的上限是多少？超出上限后如何处理？
- expected answer: 违约金上限为合同总金额的 10%（¥50,000）。超出上限后，合同未进一步定义处理方式
- expected source: `procurement-contract.pdf`，第六条 6.1 款
- evidence type: ARTICLE
- pass criteria: 精确返回 10%（¥50,000）上限值；不应编造超出上限后的处理方式；不应被 cap_percent=10 和 rate_percent=0.05 混淆
- covered capability: 数值字段保护——不让 sibling 字段（rate_percent）抢占 cap_percent

### FG2 — 百分比保护

- case id: FG2
- query: SLA 中可用性最低的是哪个服务？
- expected answer: 网络设备（银牌），可用性 99.5%。耗材更换为"—"（不适用），不计入排序
- expected source: `after-sales-sla-metrics.xlsx`
- evidence type: ARTICLE
- pass criteria: 正确识别 99.5% 为最低可用性；不混淆 99.9% 和 99.5%；不把"—"当作可用性值
- covered capability: 百分比保护——不让相近百分比（99.5%/99.9%/99.99%）混淆

### FG3 — 拒答保护

- case id: FG3
- query: 协议里有没有定义甲方的知识产权归属？
- expected answer: 没有。主合同和补充协议均未定义知识产权归属条款
- expected source: 无（证据不足）
- evidence type: N/A
- pass criteria: 明确拒答或说明未定义；不应从保密条款或其他条款编造知识产权内容
- covered capability: 拒答保护——不能从无关条款编造答案

## 通过标准

| 指标 | 目标 |
|---|---|
| Answer Accuracy | >= 10/12（不含 BLOCKED 题） |
| Search Accuracy | >= 5/6（FS1-FS4 含子项） |
| FG Accuracy | >= 3/3 |
| Hallucination | = 0 |
| Abstain Accuracy | >= 2/2（FQ12 + FG3） |

附加门槛：
- redline `BLOCKER=0`
- mvn test 通过
- PE1 + PE2 保护回归无下降
