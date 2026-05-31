# QuerySourceResponse / QueryArticleResponse 字段注释与 Lombok 改造报告

改造时间：2026-05-31
改造人：agentA（代码执行 Agent）

---

## 1. 修改文件清单

### DTO 文件（2 个）

| 文件 | 变更内容 |
|---|---|
| `src/main/java/com/xbk/lattice/api/query/QuerySourceResponse.java` | 字段 Javadoc + @Getter + @Builder + 删除便利构造器 + 删除手写 getter |
| `src/main/java/com/xbk/lattice/api/query/QueryArticleResponse.java` | 字段 Javadoc + @Getter + @Builder + 删除便利构造器 + 删除手写 getter |

### 调用点迁移文件（3 个测试文件）

| 文件 | 变更内容 |
|---|---|
| `src/test/java/com/xbk/lattice/mcp/LatticeMcpToolsTest.java` | `new QuerySourceResponse(...)` / `new QueryArticleResponse(...)` → builder |
| `src/test/java/com/xbk/lattice/query/service/RedisQueryCacheStoreTests.java` | 同上 |
| `src/test/java/com/xbk/lattice/query/service/RedisQueryCacheStoreIntegrationTests.java` | 同上 |

### 未修改的生产调用点（7 处，无需改动）

这些调用点使用全参构造器，匹配保留的 `@JsonCreator` 入口：

| 文件 | 行号 |
|---|---|
| `src/main/java/.../QueryResponseCitationProjectionSupport.java` | 218, 234 |
| `src/main/java/.../QueryResponseCitationBaseSupport.java` | 264 |
| `src/main/java/.../StructuredQueryAnswerRenderer.java` | 306 |
| `src/main/java/.../QueryResponseCitationBaseSupport.java` | 154, 172, 294 |

---

## 2. 字段注释补齐说明

### QuerySourceResponse（6 个字段）

| 字段 | 注释要点 |
|---|---|
| `sourceId` | 资料源主键，调用方可用于溯源查询，非持久化资料时可能为空 |
| `articleKey` | 文章业务标识，用于文章维度关联和去重 |
| `conceptId` | 概念稳定标识，用于按概念聚合展示 |
| `title` | 来源标题，调用方在引用列表和溯源面板中展示 |
| `sourcePaths` | 文件路径列表，支撑溯源跳转和文件级引用展示 |
| `derivation` | 推导方式，说明来源是检索命中/projection/top-K 兜底，用于判断置信度 |

### QueryArticleResponse（5 个字段）

| 字段 | 注释要点 |
|---|---|
| `sourceId` | 资料源主键，纯编译产物时可能为空 |
| `articleKey` | 文章业务标识，用于跨查询关联和去重 |
| `conceptId` | 概念标识，用于按概念聚合 |
| `title` | 文章标题，来自编译阶段元数据提取或自动生成 |
| `derivation` | 推导方式，说明命中来源和置信度 |

所有字段注释风格参照 `QueryRetrievalSettingsState.java` 和已完成的 `QueryResponse.java`，均为自然解释型中文注释。

---

## 3. Lombok / 构造器收敛说明

### 使用的 Lombok 注解

| 注解 | 位置 | 用途 |
|---|---|---|
| `@Getter` | 类级 | 替代所有手写 getter 方法 |
| `@Builder` | `@JsonCreator` 构造器 | 提供 builder 模式构造 |

### 未使用的 Lombok 注解

- 未使用 `@Data`
- 未使用 `@Setter`
- 未使用 `@AllArgsConstructor`
- 未使用 `@NoArgsConstructor`

### 构造器收敛

| 类 | 改造前 | 改造后 |
|---|---|---|
| `QuerySourceResponse` | 3-param 便利构造器 + 6-param @JsonCreator | 仅保留 6-param @JsonCreator + @Builder |
| `QueryArticleResponse` | 2-param 便利构造器 + 5-param @JsonCreator | 仅保留 5-param @JsonCreator + @Builder |

### 删除的方法

| 类 | 删除项 | 数量 |
|---|---|---|
| `QuerySourceResponse` | 3-param 便利构造器 | 1 |
| `QuerySourceResponse` | 手写 getter 方法 | 6 |
| `QueryArticleResponse` | 2-param 便利构造器 | 1 |
| `QueryArticleResponse` | 手写 getter 方法 | 5 |

### 保留内容

- `@JsonCreator` 构造器参数和注解不变
- `@JsonProperty` 注解和 JSON 字段名不变
- Jackson 序列化/反序列化行为不变

---

## 4. 调用点迁移情况

| 文件 | 迁移前 | 迁移后 |
|---|---|---|
| `LatticeMcpToolsTest.java:62` | `new QuerySourceResponse("concept-1", "Payment Timeout", List.of(...))` | `QuerySourceResponse.builder().conceptId(...).title(...).sourcePaths(...).build()` |
| `LatticeMcpToolsTest.java:66` | `new QueryArticleResponse("concept-1", "Payment Timeout")` | `QueryArticleResponse.builder().conceptId(...).title(...).build()` |
| `RedisQueryCacheStoreTests.java:45-49` | 同上模式 | 同上模式 |
| `RedisQueryCacheStoreIntegrationTests.java:68-72` | 同上模式 | 同上模式 |

生产代码 7 处调用点全部使用全参构造器，无需迁移。

---

## 5. 测试与 redline 结果

### 定向测试

```
mvn -Dtest="QueryControllerTests,RedisQueryCacheStoreTests,RedisQueryCacheStoreIntegrationTests,LatticeMcpToolsTest" test
Tests run: 25, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### 全量测试

```
mvn test
Tests run: 995, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### Redline 扫描

```
bash scripts/scan-redline.sh special_cases_report.md
（无输出，clean）
```

---

## 6. 残留风险评估

| 风险项 | 状态 |
|---|---|
| JSON 序列化兼容性 | 无风险 — `@JsonCreator` 构造器参数和 `@JsonProperty` 未变 |
| Lombok `@Builder` 与 `@JsonCreator` 共存 | 已验证 — 全量测试通过，Jackson 反序列化正常 |
| 生产调用点编译 | 无风险 — 所有生产代码使用全参构造器，未受影响 |
| 便利构造器删除影响 | 已全部迁移 — 残留搜索确认无遗漏 |
| `@Getter` 替代手写 getter | 无风险 — Lombok 生成的 getter 方法与原名一致 |

---

## 7. 合规确认

| 约束 | 状态 |
|---|---|
| 只修改 QuerySourceResponse.java / QueryArticleResponse.java + 调用点 | 通过 |
| 未修改 docs/** | 通过 |
| 未修改 query/retrieval/fallback/answer 主链逻辑 | 通过 |
| 未使用 @Data / @Setter / @AllArgsConstructor | 通过 |
| JSON 字段名和序列化语义稳定 | 通过 |
| 未新建分支 / 未 stage / 未 commit / 未 push | 通过 |
| 未清库 / 未重建 schema / 未导入资料 | 通过 |
