# B9b: api/admin Retrieval Audit DTO 契约治理报告

改造时间：2026-06-01
改造人：agentA
批次：B9b（B9 第 2/最后子批次，5/11 类）

---

## 1. 修改文件清单

| 文件 | 类型 | 变更 |
|---|---|---|
| `AdminQueryRetrievalAuditListResponse.java` | Response | 类级 `@Getter`，删除 2 getter，2 字段 Javadoc |
| `AdminQueryRetrievalAuditDetailResponse.java` | Response | 类级 `@Getter`，删除 7 getter，7 字段 Javadoc（found=isFound() 一致） |
| `AdminQueryRetrievalAuditRunResponse.java` | Response | 类级 `@Getter`，删除 21 getter，21 字段 Javadoc，保留 `List.copyOf` 防御性拷贝，禁止 @Data |
| `AdminQueryRetrievalChannelRunResponse.java` | Response | 类级 `@Getter`，删除 8 getter，8 字段 Javadoc（timeout/zeroHit 计算字段标注），禁止 @Data |
| `AdminQueryRetrievalChannelHitResponse.java` | Response | 类级 `@Getter`，删除 20 getter，20 字段 Javadoc（RRF 融合语义+大 JSON 标注），禁止 @Data |

---

## 2. 关键字段语义

### Query 处理链（AuditRunResponse）
`question → normalizedQuestion → retrievalQuestion` 完整处理链路，`rewriteApplied=true` 时 retrievalQuestion 经过 LLM 改写。

### RRF 排序与融合（ChannelHitResponse）
- `hitRank` — 通道内排名（1-based）
- `fusedRank` — RRF 融合后排名，null=被淘汰
- `includedInFused` — false 时 fusedRank=null
- `channelWeight` — 通道在 RRF 中的权重，越大占比越高
- `score` — 通道内原始分，不可跨通道比较（不同通道尺度不同）

### 计算字段（ChannelRunResponse）
`timeout` / `zeroHit` 由 controller 从 status 枚举推导，非持久化字段——已在 Javadoc 明确标注。

### 检索覆盖状态（AuditRunResponse）
`coverageStatus`：sufficient / partial / empty。empty 时检索完全失败，需排查通道配置或数据完整性。

---

## 3. Lombok 统计

| 类 | 注解 | 替代 |
|---|---|---|
| `AdminQueryRetrievalAuditListResponse` | `@Getter` | 2 getter |
| `AdminQueryRetrievalAuditDetailResponse` | `@Getter` | 7 getter |
| `AdminQueryRetrievalAuditRunResponse` | `@Getter` | 21 getter |
| `AdminQueryRetrievalChannelRunResponse` | `@Getter` | 8 getter |
| `AdminQueryRetrievalChannelHitResponse` | `@Getter` | 20 getter |
| **合计** | | **58 getter** |

**B9 总计（B9a + B9b = 11 类）**：96 getter + 10 setter 已删除，96 字段 Javadoc 已补充。

---

## 4. 测试与验证

```
mvn compile: BUILD SUCCESS
bash scripts/scan-redline.sh /tmp/b9b_special_cases_report.md: (clean)
List.copyOf 保留: 第 180 行
```

无 api/admin 层 retrieval audit 测试类。已确认未修改 docs/模型绑定配置参考.md、special_cases_report.md。

---

## 5. B9 完整汇总

| 子批次 | 范围 | 类数 | Javadoc | 删除 getter | 删除 setter |
|---|---|---|---|---|---|
| B9a | query feedback | 6 | 38 | 38 | 10 |
| B9b | retrieval audit | 5 | 58 | 58 | 0 |
| **合计** | | **11** | **96** | **96** | **10** |

## 6. 合规确认

| 约束 | 状态 |
|---|---|
| 只修改 5 个目标文件 | 通过 |
| List.copyOf 防御性拷贝保留 | 通过（第 180 行） |
| 全部类禁止 @Data | 通过 |
| 构造器签名/逻辑未改 | 通过 |
| 未修改 docs/模型绑定配置参考.md | 通过 |
| 未修改 special_cases_report.md | 通过 |
| 未混入 B9a/B10 | 通过 |
| 未 stage/commit/push | 通过 |
