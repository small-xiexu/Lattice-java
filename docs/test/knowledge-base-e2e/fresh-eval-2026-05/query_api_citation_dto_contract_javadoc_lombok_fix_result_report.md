# api/query 引用 DTO 字段契约注释与 Lombok 改造报告

改造时间：2026-05-31
改造人：agentA（代码执行 Agent）
批次：B1

---

## 1. 修改文件清单

| 文件 | 变更 |
|---|---|
| `src/main/java/.../api/query/QueryCitationMarkerResponse.java` | 类级 @Getter + 7 字段 Javadoc + 删除 7 手写 getter |
| `src/main/java/.../api/query/QueryCitationSourceResponse.java` | 类级 @Getter + 11 字段 Javadoc + 删除 11 手写 getter |
| `src/main/java/.../api/query/CitationCheckSummary.java` | 类级 @Getter + 7 字段 Javadoc + 删除 7 手写 getter |
| `src/main/java/.../api/query/DeepResearchSummary.java` | 类级 @Getter + 8 字段 Javadoc + 删除 8 手写 getter |
| `docs/plans/2026-05-31-模型契约注释与Lombok治理计划.md` | B1 状态回写 |

**无调用点迁移。** 所有类的构造器、`@JsonCreator`、`@JsonProperty` 均未修改。

---

## 2. 各文件详细变更

### 2.1 QueryCitationMarkerResponse（7 字段）

| 字段 | 注释要点 |
|---|---|
| `markerOrdinal` | 引用角标序号（从 1 开始），前端用它确定展示顺序 |
| `markerId` | 系统生成的引用点 ID，用于审计追踪 |
| `citationLiteral` | 单个引用对应的原始引用文本 |
| `citationLiterals` | 多个引用文本列表（一个角标对应多个来源时），构造器 null→空列表 |
| `claimText` | 引用所属 claim 的文本，调用方据此在答案中定位引用上下文 |
| `sourceCount` | 关联来源数，构造器保证不小于实际来源列表大小 |
| `sources` | 引用资料明细，构造器 null→空列表 |

### 2.2 QueryCitationSourceResponse（11 字段）

| 字段 | 注释要点 |
|---|---|
| `sourceType` | 来源类别（SOURCE_FILE/ARTICLE/FACT_CARD_CLASSIFICATION 等），调用方据此决定展示样式 |
| `targetKey` | 多投影引用场景下的目标标识，区分不同 projection |
| `sourceId` | 原始资料 ID，非原始资料时可能为空 |
| `articleKey` | 编译文章标识，非文章来源时可能为空 |
| `conceptId` | 概念标识，用于按概念聚合 |
| `title` | 来源标题，引用详情面板展示 |
| `sourcePaths` | 文件路径列表，构造器 null→空列表，生成可点击文件链接 |
| `matchedExcerpt` | 匹配摘录文本片段，帮助用户判断引用准确性 |
| `validationStatus` | 校验结果（VERIFIED/DEMOTED/SKIPPED），调用方据此展示不用颜色图标 |
| `reason` | 校验结论说明，DEMOTED/SKIPPED 时有值 |
| `score` | 检索相关性得分 |

### 2.3 CitationCheckSummary（7 字段）

| 字段 | 注释要点 |
|---|---|
| `verifiedCount` | 已验证引用数，引用可信度的正面指标 |
| `demotedCount` | 疑似编造或无法验证的引用数，值高时应提示用户 |
| `skippedCount` | 未执行核验的引用数（超出范围/来源不可用等） |
| `coverageRate` | 已验证引用占比（0.0-1.0），核心质量信号 |
| `noCitation` | 无任何引用时为 true（通常来自 fallback 模板），调用方可隐藏引用面板 |
| `claimCount` | 可独立核验断言总数，反映答案复杂度 |
| `unsupportedClaimCount` | 无任何已验证引用支撑的 claim 数，>0 需高度关注 |

保留 2 个构造器（5-param 便利构造器 + 7-param @JsonCreator）。

### 2.4 DeepResearchSummary（8 字段）

| 字段 | 注释要点 |
|---|---|
| `routed` | 是否进入 Deep Research 路径，false 时其余字段无实际意义 |
| `layerCount` | 研究层数，层数越多研究越深入 |
| `taskCount` | 跨所有层的子任务总数，反映计算规模 |
| `evidenceCardCount` | 生成的证据卡总数 |
| `llmCallCount` | LLM 总调用次数，用于成本估算 |
| `citationCoverage` | 最终引用覆盖率（0.0-1.0），反映答案引用可靠性 |
| `partialAnswer` | 研究计划未全部完成时为 true，调用方可提示用户 |
| `hasConflicts` | 发现冲突证据时为 true，调用方可展示冲突信息 |

---

## 3. Lombok 使用

| 类 | 注解 | 替代 getter 数 | getter 是否简单访问 |
|---|---|---|---|
| `QueryCitationMarkerResponse` | 类级 `@Getter` | 7 | 全部是 |
| `QueryCitationSourceResponse` | 类级 `@Getter` | 11 | 全部是 |
| `CitationCheckSummary` | 类级 `@Getter` | 7 | 全部是（含 `isNoCitation()`，Lombok 对 boolean 同样生成 isXxx） |
| `DeepResearchSummary` | 类级 `@Getter` | 8 | 全部是（含 `isRouted/isPartialAnswer/isHasConflicts`） |

**未使用：** `@Data`、`@Setter`、`@AllArgsConstructor`、`@NoArgsConstructor`、`@Builder`

---

## 4. 保留内容

- 全部 `@JsonCreator` 构造器（参数名、`@JsonProperty` 注解未变）
- `CitationCheckSummary` 的 2 个构造器（便利 5-param + @JsonCreator 7-param）均保留
- Jackson 序列化/反序列化语义未变
- JSON 字段名未变

---

## 5. 测试与 Redline 结果

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

## 6. 合规确认

| 约束 | 状态 |
|---|---|
| 只修改 4 个生产类 | 通过 |
| 未修改 query/retrieval/answer/fallback/citation 主链行为 | 通过 |
| @JsonCreator/@JsonProperty/构造器语义未改 | 通过 |
| 未修改 src/test/java | 通过 |
| 未修改 scripts/scan-redline.sh、special_cases_report.md | 通过 |
| 未使用 @Data/@Setter/@AllArgsConstructor | 通过 |
| 未 stage/commit/push | 通过 |

---

## 7. 残留风险评估

无残留风险。所有 getter 均为简单字段访问，Lombok 生成的 getter 行为与原手写完全一致。boolean 字段的 isXxx getter 经 Lombok 自动生成确认兼容（`isNoCitation`/`isRouted`/`isPartialAnswer`/`isHasConflicts`）。构造器未改，Jackson 反序列化路径不变。
