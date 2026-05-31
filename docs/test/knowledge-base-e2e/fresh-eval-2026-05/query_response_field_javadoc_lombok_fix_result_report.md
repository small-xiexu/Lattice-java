# QueryResponse 字段注释样板改造报告

改造时间：2026-05-31
改造人：agentA（代码执行 Agent）

---

## 1. 修改内容

### 1.1 修改文件

- `src/main/java/com/xbk/lattice/api/query/QueryResponse.java`（唯一修改文件）

### 1.2 字段注释重写

将所有 13 个字段的 Javadoc 从"写入方/取值约束/关联字段"模板风格重写为自然解释型风格，参考了 `QueryRetrievalSettingsState.java` 的写法：

- **第一行：** 一句话说明字段是什么。
- **`<p>` 段落：** 说明字段在运行时承载什么语义、被谁消费、对响应展示/审计/引用/降级判断有什么影响。

具体变更：

| 字段 | 旧注释风格 | 新注释风格 |
|---|---|---|
| `answer` | "写入方：查询回答生成链路..." | 说明前端如何渲染、与引用/证据的关系 |
| `sources` | "写入方：查询检索链路..." | 说明调用方如何通过来源理解回答依据 |
| `articles` | "写入方：查询检索链路，来自 article FTS..." | 说明 articles 与 sources 的互补关系 |
| `queryId` | "写入方：查询入口在创建 QueryGraphState..." | 说明调用方如何用它关联审计和反馈 |
| `reviewStatus` | "写入方：查询回答生成链路在完成回答后..." | 说明与 citationCheck 的分工差异 |
| `answerOutcome` | "写入方：查询回答生成链路根据证据充分性..." | 说明调用方如何快速判断答案质量 |
| `generationMode` | "写入方：查询回答生成链路在进入回答组装前..." | 说明调用方如何据此决定前端展示策略 |
| `modelExecutionStatus` | "写入方：查询回答生成链路在 LLM 调用完成后..." | 说明如何区分"没调用"和"调用失败" |
| `citationCheck` | "写入方：引用组装链路在完成引用检查后..." | 说明调用方如何评估引用可靠性 |
| `deepResearch` | "写入方：Deep Research 编排链路..." | 说明调用方如何通过判空区分场景 |
| `fallbackReason` | "写入方：查询回答生成链路在 decision 阶段..." | 说明调用方如何向用户解释降级原因 |
| `citationMarkers` | "写入方：引用组装链路在完成引用标记后..." | 说明前端如何渲染引用角标 |
| `structuredEvidence` | "写入方：查询回答生成链路在结构化..." | 说明结构化信息的展示场景 |

### 1.3 Lombok 使用

- 使用**类级 `@Getter`**（第 21 行），Lombok 自动生成所有 13 个字段的 getter 方法。
- 无手写 getter 方法（已在前序改造中删除）。

### 1.4 未使用的注解

- 未使用 `@Data`
- 未使用 `@Setter`
- 未使用 `@AllArgsConstructor`

### 1.5 保留内容

- 全部 7 个构造器保持不变
- 全部 `@JsonCreator` 和 `@JsonProperty` 注解保持不变
- JSON 字段名和 getter 行为不变
- 类级 Javadoc 未修改

---

## 2. 注释风格参考

字段注释风格参考了 `QueryRetrievalSettingsState.java`（`/Users/sxie/Downloads/QueryRetrievalSettingsState.java`）的自然解释型写法：

- 避免机械的"写入方/取值约束/关联字段"三段式模板
- 用自然中文解释字段的运行时语义和调用方视角
- 对状态字段说明它帮助调用方判断什么
- 对关联字段说明它们之间的关系和分工

---

## 3. 合规确认

| 约束 | 状态 |
|---|---|
| 只修改 QueryResponse.java | 通过 |
| 未使用 @Data | 通过 |
| 未使用 @Setter | 通过 |
| 未使用 @AllArgsConstructor | 通过 |
| 构造器未删除/合并/重排 | 通过 |
| @JsonCreator/@JsonProperty 未改 | 通过 |
| 未修改 QueryController 等业务主链 | 通过 |
| 未修改测试/配置/脚本 | 通过 |
| 未 stage/commit/push | 通过 |
| 未清库/重建 schema/导入资料 | 通过 |

---

## 4. 测试结果

执行命令：
```
mvn -Dtest=QueryControllerTests test
```

结果：
```
Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

全部 12 个测试用例通过，无失败、无错误。
