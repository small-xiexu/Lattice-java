# DTO 字段契约注释与 Lombok 改造方案报告

审查时间：2026-05-31
审查人：agentB（治理/链路分析 Agent）
审查范围：只读分析，不修改任何文件

---

## 1. 审查范围与命令摘要

### 1.1 扫描范围

| 包 | 扫描内容 | 文件数（估算） |
|---|---|---|
| `api/query/**` | 对外查询 API 的 Request/Response/DTO | 18 个非 Controller 类 |
| `api/admin/**` | 管理侧 API 的 Request/Response | ~90 个非 Controller 类 |
| `api/compiler/**` | 编译 API 的 Request/Response | 6 个非 Controller 类 |
| `infra/persistence/*Record.java` | MyBatis/JDBC 持久化记录 | 43 个 Record 类 |
| `query/domain/**` | 查询领域对象 | 9 个类 |
| `query/evidence/domain/**` | 证据领域对象 | 14 个类 |
| `query/deepresearch/domain/**` | Deep Research 领域对象 | 11 个类 |
| `compiler/domain/**`, `compiler/ast/domain/**` | 编译领域对象 | ~15 个类 |

### 1.2 使用的主要命令

```bash
# Lombok 依赖配置
grep -A3 "lombok" pom.xml

# Lombok 使用统计
grep -r "import lombok." src/main/java --include="*.java" -l
grep -rh "@Data\|@Getter\|@Setter\|@Builder\|@Slf4j" src/main/java --include="*.java" | sort | uniq -c

# 手写 Getter 统计
grep -r "public .* get" src/main/java --include="*.java"

# JsonCreator 使用
grep -r "@JsonCreator" src/main/java --include="*.java" -l

# 字段 Javadoc 质量
grep -B1 "private " src/main/java/com/xbk/lattice/api/query/*.java

# 空 Javadoc 检测
grep -rPzo '(?s)\/\*\*\s*\*/\s*\n\s*private' src/main/java

# 构造函数手写统计
grep -c "public .*Record(" infra/persistence/*Record.java
```

---

## 2. Lombok 当前项目配置和已有使用方式

### 2.1 pom.xml 配置

- **版本**：`lombok 1.18.38`（properties 统一管理）
- **作用域**：`<optional>true</optional>` — 编译期注解处理器，不传递到依赖方
- **注解处理器**：maven-compiler-plugin 的 `annotationProcessorPaths` 中已配置
- **lombok.config**：**不存在**（项目根目录和 src 目录下均未找到）

### 2.2 已有使用方式统计

| 注解 | 使用次数 | 使用位置 |
|---|---|---|
| `@Slf4j` | 69 | 遍布 Service、Controller、Support、Gateway 等类 |
| `@Data` | 56 | deepresearch domain（9 个）、compiler AST domain（5 个）、query evidence domain（3 个）、compiler graph state、部分 support 类 |
| `@AllArgsConstructor` | 43 | 配合 `@Data`/`@Getter` 使用 |
| `@NoArgsConstructor` | 37 | 配合 `@Data`/`@AllArgsConstructor` 使用 |
| `@Getter` | 6 | DeepResearch 相关 Record 类（配合 `@AllArgsConstructor`） |
| `@Value` | 3 | 少数不可变配置/常量类 |

### 2.3 使用模式分类

**模式 A：全 Lombok（deepresearch domain + AST domain）**
```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResearchTask {
    private String taskId;
    private int layerIndex;
    // ... 无手写 getter/setter/constructor
}
```
出现在：`query/deepresearch/domain/**`（9/11）、`compiler/ast/domain/**`（5 个）

**模式 B：@Getter + @AllArgsConstructor（DeepResearch Records）**
```java
@Getter
@AllArgsConstructor
public class DeepResearchTaskRecord {
    private final Long runId;
    private final String taskId;
    // ... 无手写 getter/constructor
}
```
出现在：6 个 `DeepResearch*Record.java`

**模式 C：无 Lombok（API DTO + 大部分 Record）**
手写全参构造器 + 手写 getter。出现在：所有 `api/**` DTO、37/43 个 `*Record.java`

---

## 3. DTO/Request/Response/Record 类分类统计

### 3.1 按包分类

| 包 | 总类数 | 使用 Lombok | 手写 Getter 总数 | 平均字段数 | @JsonCreator 类数 |
|---|---|---|---|---|---|
| `api/query` DTO | 18 | **0** | ~85 | ~5 | **11** |
| `api/admin` DTO | ~67 | **0** | ~470 | ~8 | **0** |
| `api/compiler` DTO | 6 | **0** | ~28 | ~3 | 1 |
| `infra/persistence` Records | 43 | **6** (14%) | **421** | ~12 | 1 (ArticleRecord) |
| `query/domain` | 7 (对象类) | **0** | ~20 | ~5 | 3 |
| `query/evidence/domain` | 6 (对象类) | **3** (50%) | ~20 | ~8 | 1 |
| `query/deepresearch/domain` | 11 | **9** (82%) | 2 | ~6 | 1 |
| `compiler/ast/domain` | 5 | **5** (100%) | 0 | ~4 | 0 |

### 3.2 "四象限"分类法

```
                不可变(final fields)        可变(non-final fields)
                ┌─────────────────────┐    ┌─────────────────────┐
对外 API        │ api/query/*Response │    │ api/admin/*Request  │
(Jackson 序列化) │ @JsonCreator 多     │    │ 少量 hand-written   │
                │ 不能用 @Data        │    │ 可用 @Data/@Getter  │
                ├─────────────────────┤    ├─────────────────────┤
内部领域        │ query/domain/*      │    │ compiler graph      │
(不直接序列化)   │ deepresearch domain │    │ state               │
                │ 部分已用 @Data      │    │ 部分已用 @Data      │
                ├─────────────────────┤    ├─────────────────────┤
持久化 Record   │ *Record (37/43)     │    │ 少量可变 Record     │
(MyBatis 映射)  │ 手写全参构造+getter │    │                     │
                │ 6 个已用 @Getter    │    │                     │
                └─────────────────────┘    └─────────────────────┘
```

---

## 4. 字段注释缺失问题分级

### 4.1 严重程度定义

| 级别 | 定义 | 典型表现 |
|---|---|---|
| **P0 - 空注释/无注释** | 字段完全无 Javadoc，或仅有空 `/** */` | `private final String answer;` 上方为空 `/** */` |
| **P1 - 重复型注释** | 字段注释与字段名完全同义，无增量信息 | `/** 答案 */ private final String answer;` |
| **P2 - 缺少状态/约束说明** | 有基本描述但缺：写入方、可空条件、与状态字段的关系 | 注释说"审查状态"但没说取值来源和何时为 null |
| **P3 - 注释在构造器参数上** | 字段无 Javadoc，但全参构造器的 `@param` 有描述 | 大多数 Admin DTO 的模式 |

### 4.2 分级统计

| 级别 | api/query | api/admin | persistence Record | query domain | 合计（估算） |
|---|---|---|---|---|---|
| **P0** | 1 (QueryResponse.answer) | ~50 字段 | ~80 字段 | ~20 字段 | **~150** |
| **P1** | 大量 — getter 的 `@return` 仅重复字段名 | 所有 getter — `@return 状态` for `getStatus()` | 所有 getter | 部分 | **~1000+** |
| **P2** | 全部 — 缺写入方/可空条件 | 全部 — 同上 | 部分类有构造器 `@param` | 全部 | **~500+** |
| **P3** | QueryResponse 构造器 `@param` 有基本描述 | 大多数 Response 的构造器 `@param` 有基本描述 | 大多数 Record 的构造器 `@param` 有基本描述 | 部分 | **~500+** |

### 4.3 典型问题样例

**QueryResponse.java（api/query — 对外 API 的门面类）**：
```java
/**
 *          ← 空注释
 */
private final String answer;                          // P0

private final List<QuerySourceResponse> sources;      // P0: 无字段级 Javadoc
private final List<QueryArticleResponse> articles;    // P0
private final String queryId;                         // P0
// ... 11 个字段全部缺少字段级 Javadoc
```

**AdminCompileJobResponse.java（api/admin）**：
```java
private final String jobId;          // P3: 字段无 Javadoc，构造器 @param 有 "作业标识"
private final String derivedStatus;  // P2: 注释 "派生展示状态" — 但没说派生规则
// 23 个字段全部如此
```

**FactCardTerminalUnitRecord.java（persistence — 637 行，32 个字段）**：
```java
// 字段全部无 Javadoc，仅在构造器 @param 中有简短描述
// 手写 32 个 getter，每个 getter 的 @return 仅重复字段名
```

---

## 5. Lombok 改造风险分析

### 5.1 高风险类：不建议使用 @Data/@AllArgsConstructor

| 风险类型 | 涉及类 | 具体原因 |
|---|---|---|
| **@JsonCreator 多构造器** | `QueryResponse`、`QueryArticleResponse`、`QuerySourceResponse`、`SearchHitResponse`、`CitationCheckSummary`、`DeepResearchSummary` 等 ~20 个类 | Jackson 反序列化依赖 `@JsonCreator` + `@JsonProperty` 构造器。如果加上 `@AllArgsConstructor`，Jackson 可能无法确定使用哪个构造器，导致反序列化失败 |
| **构造器链（telescoping constructors）** | `QueryResponse`（7 个构造器）、`ArticleRecord`（7 个构造器）、`CompileJobRecord`（3 个） | `@AllArgsConstructor` 生成全参构造器会与已有构造器冲突 |
| **MyBatis resultMap 构造器映射** | 所有 `*Record.java`（43 个） | MyBatis XML 的 `<constructor>` 映射依赖精确的构造器参数顺序和类型。`@AllArgsConstructor` 的参数顺序按字段声明顺序，不一定与 MyBatis resultMap 的 `<arg column="..." javaType="..."/>` 顺序一致 |
| **final 字段 + @Data 生成 setter** | 所有 API 响应 DTO（final 字段） | `@Data` 包含 `@Setter`，对 final 字段生成 setter 会导致编译错误（或生成无意义的 setter） |

### 5.2 中风险类：可部分使用 Lombok

| 风险类型 | 涉及类 | 安全使用方式 |
|---|---|---|
| **仅有手写 getter，无特殊构造器** | `AdminCompileJobResponse`（1 个全参构造器 + 23 个 getter，无 @JsonCreator） | 可用 `@Getter` 取代所有手写 getter。保留手写构造器（参数有明确 `@param` 文档） |
| **不可变 DTO，有 @JsonCreator** | `QuerySourceResponse`、`CitationCheckSummary` 等 | **仅可用 `@Getter`**。不能加 `@AllArgsConstructor`（会与 @JsonCreator 构造器冲突）。Jackson 需要 @JsonCreator 构造器 |
| **可变 Request DTO** | `AdminCompileJobRequest`、`AdminQueryFeedbackCreateRequest` 等 | 可用 `@Data`（= `@Getter` + `@Setter` + `@ToString` + `@EqualsAndHashCode`）。这些类字段为 non-final，Jackson 通过 setter/field 注入 |
| **DeepResearch Records（已用）** | 6 个 `DeepResearch*Record.java` | 已使用 `@Getter` + `@AllArgsConstructor`，MyBatis 已验证兼容 |

### 5.3 低风险类：安全引入 Lombok

| 类型 | 示例 | 推荐注解 |
|---|---|---|
| 纯数据领域对象（无 Jackson 依赖） | `ResearchTask`、`EvidenceCard`、`LayerSummary`（已用 @Data） | `@Data`（保留当前） |
| AST 领域对象（已用） | `AstEntity`、`AstFact`、`AstRelation`（已用 @Data） | `@Data`（保留当前） |
| 内部不可变值对象 | `CompileGraphState`、`QueryGraphState`（已用 @Data） | `@Data`（保留当前） |
| Service 内部使用的简单 DTO | 各种 Support 类的内部类 | `@Value`（不可变）或 `@Data` |

---

## 6. 推荐规范

### 6.1 对外 API DTO（api/query, api/admin, api/compiler）

#### 不可变 Response 类（final 字段 + @JsonCreator）

**适用**：`QueryResponse`、`QueryArticleResponse`、`SearchHitResponse` 等

```java
/**
 * 查询响应。
 *
 * <p>由 {@link QueryController#ask} 构造，经 Jackson 序列化为 JSON 返回给调用方。
 * 所有字段均为不可变。
 *
 * @author xiexu
 */
public class QueryResponse {

    /**
     * 最终答案的 Markdown 文本。
     *
     * <p>由 {@code AnswerGenerationPayloadOrchestrator} 生成。
     * 当 generationMode=NO_ANSWER 时为空字符串。
     * 与 {@link #citationMarkers} 中的引用点索引对应。
     */
    @Getter
    private final String answer;

    // ... 其他字段，每个都有字段级 Javadoc

    @JsonCreator
    public QueryResponse(
            @JsonProperty("answer") String answer,
            // ...
    ) { ... }
}
```

**允许的 Lombok**：`@Getter`（在每个字段上，或类级别）
**禁止的 Lombok**：`@Data`、`@AllArgsConstructor`、`@Setter`

#### 可变 Request 类（non-final 字段 + Bean Validation）

**适用**：`QueryRequest`、`AdminCompileJobRequest` 等

```java
/**
 * 查询请求。
 *
 * <p>由 Spring MVC 从 JSON 请求体反序列化。
 *
 * @author xiexu
 */
@Data
public class QueryRequest {

    /**
     * 用户输入的自然语言问题。
     *
     * <p>不能为空。经 {@code QueryFacadeService} 归一化后作为检索问题。
     */
    @NotBlank(message = "question 不能为空")
    private String question;

    // ...
}
```

**允许的 Lombok**：`@Data`（= `@Getter` + `@Setter` + `@ToString` + `@EqualsAndHashCode`）
**禁止的 Lombok**：`@AllArgsConstructor`（Jackson 需要无参构造器 + setter 注入，`@Data` 不含构造器注解，需显式加 `@NoArgsConstructor` 如果需要）

### 6.2 内部领域对象（query/domain, compiler/domain, deepresearch/domain）

**适用**：纯数据承载，不直接序列化为对外 API JSON

**已有良好模式**（deepresearch domain 已用）：
```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResearchTask {
    private String taskId;
    // ...
}
```

**推荐**：继续使用 `@Data` + `@NoArgsConstructor` + `@AllArgsConstructor`

### 6.3 持久化 Record（infra/persistence/*Record.java）

**现状**：37/43 个类手写构造器 + getter。6 个 DeepResearch Record 已用 `@Getter` + `@AllArgsConstructor`。

**推荐分两阶段**：
- **第一阶段**：`@Getter` 取代手写 getter（低风险）
- **第二阶段**：评估每个 Record 的 MyBatis 映射兼容性后，选择性使用 `@AllArgsConstructor`

**判断 MyBatis 兼容性的规则**：
- 如果 XML mapper 使用 `<constructor>` 映射 → 保留手写构造器（**不能使用 @AllArgsConstructor**）
- 如果 XML mapper 使用自动映射（不指定 `<constructor>`）→ 需要无参构造器 + setter 或 MyBatis 自动字段映射

**当前建议**：Record 类**只使用 `@Getter`**，保留手写构造器。这是最安全的最小改造。

### 6.4 Lombok 使用白名单/黑名单

| 注解 | 白名单（允许） | 黑名单（禁止） | 说明 |
|---|---|---|---|
| `@Getter` | **全部类** | — | 纯生成 getter，无副作用 |
| `@Setter` | Request DTO、可变 domain 对象 | **不可变 Response DTO、final 字段类** | 对 final 字段无效 |
| `@Data` | Request DTO、内部 domain 对象、简单 POJO | **@JsonCreator 类、MyBatis Record、不可变 Response** | = @Getter+@Setter+@ToString+@EqualsAndHashCode+@RequiredArgsConstructor |
| `@AllArgsConstructor` | 无构造器冲突的简单类 | **多构造器类、MyBatis constructor 映射类** | 参数顺序敏感 |
| `@NoArgsConstructor` | Request DTO、MyBatis 默认映射类 | — | Jackson/MyBatis 反序列化需要 |
| `@Builder` | 复杂构造场景 | 对外的核心 API DTO | 隐藏构造细节，可能破坏 Jackson 兼容性 |
| `@Value` | 内部不可变值对象 | — | = @Getter+@FieldDefaults(makeFinal=true)+@AllArgsConstructor+@ToString+@EqualsAndHashCode |
| `@Slf4j` | Service、Controller、Support | — | 已有 69 处使用，继续沿用 |

---

## 7. 分阶段实施计划

### Phase 1：API query DTO 样板改造（~18 个类，最优先）

**目标**：对外查询 API 的 DTO 类统一字段注释规范，以 `QueryResponse` 为样板。

**范围**：
- `api/query/QueryResponse.java`
- `api/query/QueryArticleResponse.java`
- `api/query/QuerySourceResponse.java`
- `api/query/QueryRequest.java`
- `api/query/QueryStructuredEvidenceResponse.java`
- `api/query/SearchHitResponse.java`
- `api/query/SearchResponse.java`
- 其余 11 个 api/query DTO 类

**变更内容**：
1. 每个字段补充字段级 Javadoc（含义、来源、可空条件）
2. 对 `@JsonCreator` 类：字段级 `@Getter` 取代手写 getter（保留 @JsonCreator 构造器）
3. 对无 `@JsonCreator` 的 Request 类：类级 `@Data` 取代手写 getter/setter
4. 删除手写 getter（~85 个方法，约 500 行代码）
5. 清理空 Javadoc（如 QueryResponse.answer 的 `/** */`）

**风险**：低。`@Getter` 对 Jackson 序列化透明。`@Data` 对 Request 类透明。

### Phase 2：api/admin Request/Response（~67 个类）

**范围**：`api/admin/Admin*Request.java`、`api/admin/Admin*Response.java`

**变更内容**：
1. Response 类：字段级 `@Getter` 取代手写 getter（~470 个方法，约 3000 行代码）
2. Request 类：类级 `@Data` 取代手写 getter/setter
3. 字段级 Javadoc 补充（至少说明含义和来源表/服务）
4. 保留手写构造器（参数有 `@param` Javadoc）

**风险**：低。Admin Response 无 `@JsonCreator`，纯 `@Getter` 安全。

### Phase 3：compiler/query 内部 DTO（~30 个类）

**范围**：
- `query/domain/*`（7 个对象类）
- `query/evidence/domain/*`（6 个对象类）
- `compiler/domain/*`（~10 个对象类）
- `compiler/graph/*State.java`（已用 @Data，仅补注释）

**变更内容**：
1. 未用 Lombok 的对象类改为 `@Data` + `@NoArgsConstructor` + `@AllArgsConstructor`
2. 手写 getter 全部删除
3. 字段级 Javadoc 补充

**风险**：低。已有 deepresearch domain 和 AST domain 的成功先例。

### Phase 4：persistence Record 审慎处理（43 个类）

**范围**：`infra/persistence/*Record.java`

**变更内容**：
1. **全体**：字段级 `@Getter` 取代手写 getter（421 个方法，约 3000 行代码）
2. **包含**：字段级 Javadoc（至少说明映射的数据库列和类型）
3. **不包含**：不改构造器（MyBatis 兼容性需逐类评估）

**风险**：中。需逐类确认 MyBatis XML mapper 不使用 `<constructor>` 映射依赖。可先做 `@Getter` 改造（纯安全），构造器改造延后。

---

## 8. 下一轮 agentA 最小改造范围

### 8.1 Phase 1 样板改造（推荐先做）

**目标**：以 `QueryResponse` 为样板，建立字段契约注释规范，验证 `@Getter` + `@JsonCreator` 共存模式。

**允许修改文件**（仅 1 个）：
- `src/main/java/com/xbk/lattice/api/query/QueryResponse.java`

**变更内容**：
1. 每个字段上方补充字段级 Javadoc：
   ```
   /**
    * 最终答案的 Markdown 文本。
    *
    * <p>由 AnswerGenerationPayloadOrchestrator 生成。
    * 当 generationMode=NO_ANSWER 时为空字符串，不为 null。
    * 与 citationMarkers 中的引用点索引对应。
    */
   @Getter
   private final String answer;
   ```
2. 清理 `answer` 字段的空 `/** */` Javadoc
3. 字段级加 `@Getter`（lombok），删除 13 个手写 getter 方法
4. 保留 `@JsonCreator` 构造器和所有重载构造器

**验证方式**：
1. `mvn test` 全量通过
2. 定向验证 `QueryResponse` 的 Jackson 序列化/反序列化（如有测试）
3. 如无测试，agentD 做一次真实 API 调用验证 JSON 格式不退化

### 8.2 禁止事项

- 禁止在 `QueryResponse` 上加 `@Data`（会与 final 字段 + @JsonCreator 冲突）
- 禁止在 `QueryResponse` 上加 `@AllArgsConstructor`（会与 7 个重载构造器冲突）
- 禁止修改 `QueryController` 或其他 Service 代码
- 禁止修改 Jackson 配置或序列化行为
- 禁止混修 Query/Answer/Citation/fallback 主链

---

## 9. 验证方式

| 阶段 | 验证方式 | 阻塞条件 |
|---|---|---|
| Phase 1-3 | `mvn test` 全量通过 | 任何测试失败 |
| Phase 1-3 | Jackson 序列化/反序列化定向测试（如无，agentD 做真实 API 调用） | JSON 格式退化 |
| Phase 1-3 | IDE 编译无 Lombok 相关警告 | 编译错误 |
| Phase 4 (Record @Getter) | MyBatis mapper 测试全量通过 | 任何 Record 映射失败 |
| Phase 4 (Record 构造器) | **逐类评估后单独执行**，不与其他 Phase 混 | MyBatis resultMap 失效 |

---

## 10. QueryResponse 建议改造风格（样例，不修改文件）

```java
package com.xbk.lattice.api.query;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.xbk.lattice.query.domain.AnswerOutcome;
import com.xbk.lattice.query.domain.GenerationMode;
import com.xbk.lattice.query.domain.ModelExecutionStatus;
import lombok.Getter;

import java.util.List;

/**
 * 查询响应。
 *
 * <p>由 {@link com.xbk.lattice.api.query.QueryController} 构造，
 * 经 Jackson 序列化为 JSON 返回给 API 调用方。
 * 所有字段均为不可变，通过 {@link JsonCreator} 构造器注入。
 *
 * @author xiexu
 */
public class QueryResponse {

    /**
     * 最终答案的 Markdown 文本。
     *
     * <p>写入方：{@code AnswerGenerationPayloadOrchestrator}。
     * 当 {@code generationMode=NO_ANSWER} 时为空字符串。
     * 与 {@link #citationMarkers} 中的引用点索引位置对应。
     */
    @Getter
    private final String answer;

    /**
     * 答案引用来源列表。
     *
     * <p>写入方：{@code QueryResponseCitationAssembler}。
     * 当无引用来源时为空列表。
     */
    @Getter
    private final List<QuerySourceResponse> sources;

    /**
     * 检索命中的文章摘要列表。
     *
     * <p>写入方：{@code QueryFacadeService}，从 fused topK 投影。
     */
    @Getter
    private final List<QueryArticleResponse> articles;

    /**
     * 查询标识。
     *
     * <p>由系统生成的唯一 queryId。用于审计、反馈关联和待确认问答追踪。
     * 当查询未创建 answer audit 时为 {@code null}。
     */
    @Getter
    private final String queryId;

    /**
     * 答案审查状态。
     *
     * <p>取值来自 {@link com.xbk.lattice.query.domain.ReviewStatus} 枚举名。
     * 当审查未执行时为 {@code null}。
     */
    @Getter
    private final String reviewStatus;

    /**
     * 答案语义。
     *
     * <p>表示答案是否 SUCCESS / PARTIAL_ANSWER / INSUFFICIENT_EVIDENCE 等。
     * 写入方：{@code AnswerGenerationOutcomeSupport}。
     */
    @Getter
    private final AnswerOutcome answerOutcome;

    /**
     * 生成模式。
     *
     * <p>表示答案由 LLM 生成 / FALLBACK 确定 / 缓存返回。
     * 写入方：{@code AnswerGenerationPayloadOrchestrator}。
     */
    @Getter
    private final GenerationMode generationMode;

    /**
     * 模型执行状态。
     *
     * <p>表示 LLM 调用是否成功 / 降级 / 超时。
     * 写入方：{@code LlmInvocationExecutor}。
     */
    @Getter
    private final ModelExecutionStatus modelExecutionStatus;

    /**
     * 引用核验摘要。
     *
     * <p>由 {@code CitationCheckReport} 生成，核验答案引用是否可支撑 claim。
     * 当未启用引用核验时为 {@code null}。
     */
    @Getter
    private final CitationCheckSummary citationCheck;

    /**
     * Deep Research 摘要。
     *
     * <p>当查询触发 Deep Research 多层研究时填充。
     * 普通查询时为 {@code null}。
     */
    @Getter
    private final DeepResearchSummary deepResearch;

    /**
     * Fallback 触发原因。
     *
     * <p>仅当 {@code generationMode=FALLBACK} 时有值。说明为何不走 LLM 生成。
     * 写入方：{@code AnswerGenerationOutcomeSupport.resolveFallbackReason}。
     */
    @Getter
    private final String fallbackReason;

    /**
     * 答案引用点列表。
     *
     * <p>标记答案文本中各引用点的位置和对应的来源。
     * 写入方：{@code QueryResponseCitationAssembler}。
     * 当无可标记引用时为空列表。
     */
    @Getter
    private final List<QueryCitationMarkerResponse> citationMarkers;

    /**
     * 结构化证据。
     *
     * <p>包含结构化表格/列表/路径证据的详细信息。
     * 写入方：{@code QueryController} 从 answer audit 的 claim 中投影。
     * 当无结构化证据时为 {@code null}。
     */
    @Getter
    private final QueryStructuredEvidenceResponse structuredEvidence;

    // === 构造器保持不变（仅删除手写 getter 方法体）===

    /** 创建查询响应（3 参数简化版）。 */
    public QueryResponse(String answer, List<QuerySourceResponse> sources,
                         List<QueryArticleResponse> articles) {
        this(answer, sources, articles, null, null, null, null, null, null, null, "");
    }

    // ... 其余 6 个重载构造器保持不变 ...

    @JsonCreator
    public QueryResponse(
            @JsonProperty("answer") String answer,
            @JsonProperty("sources") List<QuerySourceResponse> sources,
            @JsonProperty("articles") List<QueryArticleResponse> articles,
            @JsonProperty("queryId") String queryId,
            @JsonProperty("reviewStatus") String reviewStatus,
            @JsonProperty("answerOutcome") AnswerOutcome answerOutcome,
            @JsonProperty("generationMode") GenerationMode generationMode,
            @JsonProperty("modelExecutionStatus") ModelExecutionStatus modelExecutionStatus,
            @JsonProperty("citationCheck") CitationCheckSummary citationCheck,
            @JsonProperty("deepResearch") DeepResearchSummary deepResearch,
            @JsonProperty("fallbackReason") String fallbackReason,
            @JsonProperty("citationMarkers") List<QueryCitationMarkerResponse> citationMarkers,
            @JsonProperty("structuredEvidence") QueryStructuredEvidenceResponse structuredEvidence
    ) {
        this.answer = answer;
        this.sources = sources;
        this.articles = articles;
        this.queryId = queryId;
        this.reviewStatus = reviewStatus;
        this.answerOutcome = answerOutcome;
        this.generationMode = generationMode;
        this.modelExecutionStatus = modelExecutionStatus;
        this.citationCheck = citationCheck;
        this.deepResearch = deepResearch;
        this.fallbackReason = fallbackReason;
        this.citationMarkers = citationMarkers == null ? List.of() : citationMarkers;
        this.structuredEvidence = structuredEvidence;
    }

    // === 13 个手写 getter 方法全部删除，由字段级 @Getter 替代 ===
}
```

**改进效果**：
- 删除 13 个手写 getter 方法（~100 行代码）
- 每个字段有明确的字段级 Javadoc，说明含义、写入方、可空条件
- 构造器逻辑不变（Jackson 兼容性 100% 保留）
- 仅增加 1 个 Lombok import + 13 个 `@Getter` 注解

---

## 附录 A：字段 Javadoc 模板

```java
/**
 * {字段含义的一句话描述}。
 *
 * <p>写入方：{@code {Service/Assembler 类名}}。
 * <p>取值约束：{可空条件，例如"不为 null""当 X 时为空字符串""当 Y 时为 null"}。
 * <p>关联字段：{与本字段有状态耦合的其他字段，例如 @link}。
 */
@Getter
private final String fieldName;
```

---

## 附录 B：类级 Javadoc 模板

```java
/**
 * {类的一句话职责描述}。
 *
 * <p>由 {@link {Controller/Service}} 构造，经 Jackson 序列化为 JSON 返回给调用方。
 * <p>所有字段均为不可变，通过 @JsonCreator 构造器注入。
 *
 * @author xiexu
 */
```

---

## 附录 C：统计数据汇总

| 指标 | 当前值 |
|---|---|
| Lombok 使用文件数 (src/main) | 112 |
| @Data 使用次数 | 56 |
| @Slf4j 使用次数 | 69 |
| @Getter 使用次数 | 6 (仅 DeepResearch Records) |
| 手写 getter 总数（API DTO） | ~583 |
| 手写 getter 总数（Record） | 421 |
| Record 类总行数 | 7,654 |
| 无 Lombok 的 Record 类 | 37/43 (86%) |
| 无 Lombok 的 API DTO 类 | ~93 |
| @JsonCreator 使用类数 | 36 |
| lombok.config 存在 | 否 |
| 空 Javadoc 字段 | ~150 (P0) |
| 测试中使用 Lombok | 0 |

---

## 合规声明

- 本轮未修改 `src/main/java`、`src/test/java`、`src/main/resources`、`scripts`
- 本轮未读取 hidden eval
- 本轮未把 eval 题面、答案、case id、文件名、业务词写入代码或配置
- 本轮未 stage、未 commit、未 push
- 本轮未清库、未重建、未重导
- 本轮未修改 DB 数据
- 本轮新增报告：`dto_field_javadoc_lombok_refactor_analysis_report.md`
