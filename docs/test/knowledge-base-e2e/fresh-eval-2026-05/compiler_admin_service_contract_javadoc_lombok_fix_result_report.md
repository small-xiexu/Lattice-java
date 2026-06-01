# api/compiler + admin/service 字段契约注释与 Lombok 改造报告

改造时间：2026-05-31
改造人：agentA（代码执行 Agent）
批次：B4

---

## 1. 修改文件清单

| 文件 | 类型 | 变更 |
|---|---|---|
| `CompileRequest.java` | 可变 Request | 2 字段 Javadoc，保留 getter+setter |
| `CompileRetryRequest.java` | 可变 Request | 1 字段 Javadoc，保留 getter+setter |
| `CompileResponse.java` | 不可变 Response | 类级 @Getter + 2 字段 Javadoc + 删除 2 手写 getter |
| `CompileErrorResponse.java` | 不可变 Response | 类级 @Getter + 2 字段 Javadoc + 删除 2 手写 getter |
| `CompileArticleReviewQueueActionRequest.java` | 不可变（构造器注入） | 类级 @Getter + 3 字段 Javadoc + 删除 3 手写 getter |
| `CompileArticleReviewQueueActionResult.java` | 不可变 Result | 类级 @Getter + 3 字段 Javadoc + 删除 3 手写 getter |
| `docs/plans/...模型契约注释与Lombok治理计划.md` | — | B4 状态回写 + "当前下一步" → B5a |

**无调用点迁移。** 构造器、`@JsonCreator`、`@JsonProperty`、setter 均未修改。

---

## 2. 各文件详细变更

### 2.1 CompileRequest（2 字段，可变 Request——仅补 Javadoc）

Spring MVC 绑定请求 DTO，保留 getter/setter 和无参构造。

| 字段 | 注释要点 |
|---|---|
| `sourceDir` | 源目录路径，编译流程扫描该目录下所有文件；为空时使用系统默认 |
| `incremental` | 是否增量编译（true=只编译变更文件，false=全量编译） |

### 2.2 CompileRetryRequest（1 字段，可变 Request——仅补 Javadoc）

| 字段 | 注释要点 |
|---|---|
| `jobId` | 原始编译任务的 jobId，系统据此找到原配置并重新执行编译 |

### 2.3 CompileResponse（2 字段，2 构造器）

保留便利构造器（1-param persistedCount）和 @JsonCreator 构造器（2-param）。

| 字段 | 注释要点 |
|---|---|
| `persistedCount` | 通过 review gate 成功写入正式表的文章数量（只有 passed + ACTIVE 计入） |
| `jobId` | 编译任务唯一 ID，用于查询进度/审查详情/重试 |

### 2.4 CompileErrorResponse（2 字段）

| 字段 | 注释要点 |
|---|---|
| `code` | 机器可读错误码（INVALID_SOURCE_DIR/COMPILE_TIMEOUT/INTERNAL_ERROR），用于分类和重试策略 |
| `message` | 人可读错误描述，用于前端展示和日志排查 |

### 2.5 CompileArticleReviewQueueActionRequest（3 字段）

不可变 Request（构造器注入），包含乐观锁字段。

| 字段 | 注释要点 |
|---|---|
| `reviewedBy` | 复核人员标识，用于审计追踪和责任归属 |
| `comment` | 审批说明或拒绝原因，通过时可为空 |
| `expectedReviewStatus` | 乐观锁期望状态，防止并发 approve/reject 导致状态覆盖 |

### 2.6 CompileArticleReviewQueueActionResult（3 字段）

| 字段 | 注释要点 |
|---|---|
| `queueRecord` | 操作后队列记录快照，含最新 review_status 等 |
| `previousReviewStatus` | 操作前的原始状态，用于审计对比 |
| `auditId` | 审计记录 ID，用于关联审计日志和追踪审批链路 |

---

## 3. Lombok 使用统计

| 类 | 注解 | 替代 getter 数 | 保留 setter |
|---|---|---|---|
| `CompileResponse` | 类级 `@Getter` | 2 | — |
| `CompileErrorResponse` | 类级 `@Getter` | 2 | — |
| `CompileArticleReviewQueueActionRequest` | 类级 `@Getter` | 3 | — |
| `CompileArticleReviewQueueActionResult` | 类级 `@Getter` | 3 | — |
| **合计** | | **10** | |

### 未使用 Lombok 的类

| 类 | 原因 |
|---|---|
| `CompileRequest` | 可变 Request（含 setter），保持 Spring MVC 绑定方式稳定 |
| `CompileRetryRequest` | 可变 Request（含 setter），同上 |

**未使用：** `@Data`、`@Setter`、`@AllArgsConstructor`、`@NoArgsConstructor`、`@Builder`

---

## 4. 保留内容

- `CompileRequest` 和 `CompileRetryRequest` 的 getter/setter 全部保留
- `CompileResponse` 的 2 个构造器（便利 + @JsonCreator）
- 所有构造器逻辑、`@JsonCreator`、`@JsonProperty`
- `CompileArticleReviewQueueActionResult` 的 `CompileArticleReviewQueueRecord` import
- 编译主流程、review queue 状态流转未触碰

---

## 5. 测试与 Redline

```
mvn test
Tests run: 995, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS

bash scripts/scan-redline.sh special_cases_report.md
（无输出，clean）
```

---

## 6. B0-B4 累计统计

| 批次 | 范围 | 类数 | 字段 Javadoc | 删除 getter |
|---|---|---|---|---|
| B0 | api/query 核心 DTO | 3 | 24 | 24 |
| B0.5 | query/service 检索 DTO | 3 | 23 | 16 |
| B1 | api/query 引用 DTO | 4 | 33 | 33 |
| B2 | api/query 结构化证据 DTO | 4 | 18 | 18 |
| B3 | api/query 搜索/pending DTO | 7 | 23 | 17 |
| B4 | api/compiler + admin/service | 6 | 13 | 10 |
| **合计** | | **27** | **134** | **118** |

---

## 7. 合规确认

| 约束 | 状态 |
|---|---|
| 只修改 6 个目标文件 | 通过 |
| 可变 Request 保留 getter/setter | 通过 |
| 不可变 Response/Result 仅用 @Getter | 通过 |
| 未使用 @Data/@Setter/@AllArgsConstructor/@NoArgsConstructor | 通过 |
| 未修改 compile 主流程/review queue 状态流转 | 通过 |
| 未修改 src/test/java | 通过 |
| 未扩大到 B5a 或其他 api/admin DTO | 通过 |
| 未 stage/commit/push | 通过 |

---

## 8. 残留风险

无。4 个不可变类的 getter 均为简单字段访问，Lombok 生成行为与原手写一致。2 个可变 Request 保留所有 getter/setter，Spring MVC 绑定行为不变。
