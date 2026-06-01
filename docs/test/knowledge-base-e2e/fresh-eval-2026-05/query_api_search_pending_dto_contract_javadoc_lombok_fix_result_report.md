# api/query 搜索与 Pending DTO 字段契约注释与 Lombok 改造报告

改造时间：2026-05-31
改造人：agentA（代码执行 Agent）
批次：B3

---

## 1. 修改文件清单

| 文件 | 类型 | 变更 |
|---|---|---|
| `QueryRequest.java` | 可变 Request | 5 字段 Javadoc，保留 getter+setter |
| `PendingQueryCorrectionRequest.java` | 可变 Request | 1 字段 Javadoc，保留 getter+setter |
| `SearchResponse.java` | 不可变 Response | 类级 @Getter + 2 字段 Javadoc + 删除 2 手写 getter |
| `SearchHitResponse.java` | 不可变 Response | 类级 @Getter + 9 字段 Javadoc + 删除 9 手写 getter |
| `QueryErrorResponse.java` | 不可变 Response | 类级 @Getter + 2 字段 Javadoc + 删除 2 手写 getter |
| `PendingQueryAnswerResponse.java` | 不可变 Response | 类级 @Getter + 3 字段 Javadoc + 删除 3 手写 getter |
| `PendingQueryStatusResponse.java` | 不可变 Response | 类级 @Getter + 1 字段 Javadoc + 删除 1 手写 getter |
| `docs/plans/...模型契约注释与Lombok治理计划.md` | — | B3 状态回写 + "当前下一步" → B4 |

**无调用点迁移。** 构造器、`@JsonCreator`、`@JsonProperty`、setter 均未修改。

---

## 2. 各文件详细变更

### 2.1 QueryRequest（5 字段，可变 Request——仅补 Javadoc）

Spring MVC 绑定请求 DTO，保留所有 getter/setter，保持 `@NotBlank` 校验。

| 字段 | 注释要点 |
|---|---|
| `question` | 必填(@NotBlank)，经 Query Rewrite 标准化后作为检索输入 |
| `forceDeep` | 强制走 Deep Research，null/false 时系统自动决定 |
| `forceSimple` | 强制走简单问答，与 forceDeep 互斥 |
| `maxLlmCalls` | Deep Research LLM 调用上限，null 时用默认值 |
| `overallTimeoutMs` | 整体超时（毫秒），超时后中断返回错误 |

### 2.2 SearchResponse（2 字段）

| 字段 | 注释要点 |
|---|---|
| `count` | 命中总数，用于分页和总数展示 |
| `items` | 搜索结果条目列表，调用方逐条渲染 |

### 2.3 SearchHitResponse（9 字段，含 2 构造器）

不可变 Response，保留 7-param 便利构造器和 9-param @JsonCreator 构造器。

| 字段 | 注释要点 |
|---|---|
| `evidenceType` | 证据类别（ARTICLE/FACT_CARD/SOURCE），决定搜索卡片展示样式 |
| `sourceId` | 原始资料 ID，非原始资料命中时可能为空 |
| `articleKey` | 编译文章标识，非文章来源命中时可能为空 |
| `conceptId` | 概念标识，用于按概念聚合 |
| `title` | 命中标题，搜索结果列表中展示 |
| `content` | 命中内容摘要，搜索结果卡片中展示帮助判断相关性 |
| `metadataJson` | 附加元信息（chunk 身份、结构化字段路径等） |
| `sourcePaths` | 文件路径列表，用于生成可点击文件链接 |
| `score` | RRF 融合评分，决定排序位置 |

### 2.4 QueryErrorResponse（2 字段）

| 字段 | 注释要点 |
|---|---|
| `code` | 机器可读错误标识（INVALID_QUESTION/QUERY_TIMEOUT/INTERNAL_ERROR），用于分类和重试策略 |
| `message` | 人可读错误描述，用于前端展示和日志记录 |

### 2.5 PendingQueryCorrectionRequest（1 字段，可变 Request——仅补 Javadoc）

Spring MVC 绑定请求，保留 getter+setter。

| 字段 | 注释要点 |
|---|---|
| `correction` | 调用方提交的纠错文本，用于修正 pending query 的错误答案 |

### 2.6 PendingQueryAnswerResponse（3 字段）

| 字段 | 注释要点 |
|---|---|
| `queryId` | 查询唯一标识，用于关联原始请求和审计 |
| `answer` | 纠错后重新生成的答案正文 |
| `status` | 当前处理阶段（confirmed/discarded 等） |

### 2.7 PendingQueryStatusResponse（1 字段）

| 字段 | 注释要点 |
|---|---|
| `status` | confirm/discard 操作结果 |

---

## 3. Lombok 使用统计

| 类 | 注解 | 替代 getter 数 | 保留 setter |
|---|---|---|---|
| `SearchResponse` | 类级 `@Getter` | 2 | — |
| `SearchHitResponse` | 类级 `@Getter` | 9 | — |
| `QueryErrorResponse` | 类级 `@Getter` | 2 | — |
| `PendingQueryAnswerResponse` | 类级 `@Getter` | 3 | — |
| `PendingQueryStatusResponse` | 类级 `@Getter` | 1 | — |
| **合计** | | **17** | |

### 未使用 Lombok 的类

| 类 | 原因 |
|---|---|
| `QueryRequest` | 可变 Request（含 setter），保持 Spring MVC 绑定方式稳定 |
| `PendingQueryCorrectionRequest` | 可变 Request（含 setter），同上 |

**未使用：** `@Data`、`@Setter`、`@AllArgsConstructor`、`@NoArgsConstructor`、`@Builder`

---

## 4. 保留内容

- `QueryRequest` 的 `@NotBlank` 校验注解、全部 getter/setter
- `SearchHitResponse` 的 2 个构造器（便利 7-param + @JsonCreator 9-param）
- 所有类的构造器、`@JsonCreator`、`@JsonProperty`
- Jackson 序列化/反序列化语义

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

## 6. 合规确认

| 约束 | 状态 |
|---|---|
| 只修改 api/query 下 7 个目标文件 | 通过 |
| 可变 Request 保留 setter 和无参构造 | 通过 |
| 不可变 Response 仅用 @Getter | 通过 |
| 未使用 @Data/@AllArgsConstructor/@NoArgsConstructor | 通过 |
| 未修改 query/retrieval/answer/fallback/citation 主链 | 通过 |
| 未修改 src/test/java | 通过 |
| 未扩大到 B4 或 api/admin | 通过 |
| 未 stage/commit/push | 通过 |

---

## 7. 残留风险

无。可分两个维度确认：
- **Lombok 维度**：5 个不可变 Response 类的 getter 均为简单字段访问，`@Getter` 生成行为与原手写一致。
- **Spring 绑定维度**：2 个可变 Request 类的 getter/setter 全部保留原样，Spring MVC 绑定行为不变。
