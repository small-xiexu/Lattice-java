# QueryResponse 构造器收敛改造报告

改造时间：2026-05-31
改造人：Codex（代码执行）

---

## 1. 改造目标

以 `QueryResponse` 作为 DTO 构造器治理样板，收敛历史遗留的多重重载构造器，降低后续全项目 DTO 改造时的调用复杂度。

本轮只处理 `QueryResponse` 及其直接调用点，不扩大到其他 DTO。

---

## 2. 修改范围

### 2.1 生产代码

- `src/main/java/com/xbk/lattice/api/query/QueryResponse.java`
- `src/main/java/com/xbk/lattice/query/graph/QueryGraphAnswerSupport.java`
- `src/main/java/com/xbk/lattice/query/graph/QueryFinalizationGraphFragment.java`
- `src/main/java/com/xbk/lattice/query/structured/StructuredQueryService.java`
- `src/main/java/com/xbk/lattice/query/service/QueryFacadeService.java`
- `src/main/java/com/xbk/lattice/query/service/DeepResearchOrchestrator.java`

### 2.2 测试代码

- `src/test/java/com/xbk/lattice/mcp/LatticeMcpToolsTest.java`
- `src/test/java/com/xbk/lattice/api/admin/AdminVectorIndexControllerTests.java`
- `src/test/java/com/xbk/lattice/api/admin/AdminVectorConfigControllerTests.java`
- `src/test/java/com/xbk/lattice/query/service/RedisQueryCacheStoreIntegrationTests.java`
- `src/test/java/com/xbk/lattice/query/service/RedisQueryCacheStoreTests.java`
- `src/test/java/com/xbk/lattice/query/service/QueryFacadeServiceDeepResearchRoutingTests.java`
- `src/test/java/com/xbk/lattice/query/graph/RedisQueryWorkingSetStoreTests.java`

---

## 3. 具体改造

### 3.1 QueryResponse

- 保留唯一的全参 `@JsonCreator` 构造器，继续作为 Jackson 反序列化入口。
- 在全参构造器上增加 Lombok `@Builder`，让业务代码用具名 builder 参数构建响应。
- 删除 6 个历史重载构造器。
- 保留类级 `@Getter`。
- 保留所有字段级 Javadoc。
- 保留 `@JsonProperty` 字段名映射。

### 3.2 调用点迁移

- 将项目内所有 `new QueryResponse(...)` 调用迁移为 `QueryResponse.builder()`。
- 迁移后执行扫描，`src/main/java` 与 `src/test/java` 中已无 `new QueryResponse(...)` 调用。
- 迁移时保持原字段语义不变；原先缺省传入的字段仍保持缺省或空列表语义。

---

## 4. 未做事项

- 未将 `QueryResponse` 改为 Java record。
- 未使用 `@Data`。
- 未使用 `@Setter`。
- 未使用 `@AllArgsConstructor`。
- 未修改 Jackson 配置。
- 未修改 Query/Answer/Citation/fallback/retrieval 主链逻辑。
- 未扩大到其他 DTO。

---

## 5. 验证结果

### 5.1 调用点扫描

命令：

`rg -n "new QueryResponse\\(" src/main/java src/test/java`

结果：

无命中。

### 5.2 空白检查

命令：

`git diff --check -- <本轮修改文件>`

结果：

通过，无输出。

### 5.3 定向测试

命令：

`mvn -Dtest=QueryControllerTests,RedisQueryCacheStoreTests,RedisQueryWorkingSetStoreTests,LatticeMcpToolsTest,AdminVectorConfigControllerTests,AdminVectorIndexControllerTests,QueryFacadeServiceDeepResearchRoutingTests test`

结果：

- Tests run: 34
- Failures: 0
- Errors: 0
- Skipped: 0
- BUILD SUCCESS

---

## 6. 后续建议

`QueryResponse` 构造器收敛样板可以作为后续 DTO 治理参考，但不建议一次性全项目机械替换。

建议顺序：

1. 先在 `api/query` 包内继续挑选 1-2 个 Response 类做小批量验证。
2. 对含 `@JsonCreator` 的不可变 DTO，只保留稳定反序列化构造器，业务侧用 builder 或静态工厂。
3. 对 MyBatis/JDBC `Record` 类暂不改构造器，只优先评估 getter 样板和字段注释。
4. 每轮都独立跑定向测试和 `mvn test`，避免 Jackson 或持久化映射行为被隐式改变。

---

## 7. 合规声明

- 本轮未修改 redline 脚本或 allowlist。
- 本轮未读取 hidden eval。
- 本轮未清库、未重建 schema、未导入资料、未运行业务 eval。
- 本轮未 stage、未 commit、未 push。
