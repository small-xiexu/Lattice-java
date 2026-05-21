# SchemaAwarePrompts Spring DI 修复结果报告

## 1. 修复目标

修复 `SchemaAwarePrompts` 因多构造器无 `@Autowired` 标注导致 Spring 启动失败（`BeanCreationException`）的问题。

## 2. 根因分析

`SchemaAwarePrompts` 声明了两个构造器：

- 单参数构造器 `SchemaAwarePrompts(CompilerProperties)` —— 用于测试/手工构造
- 双参数构造器 `SchemaAwarePrompts(CompilerProperties, CompilerPromptProvider)` —— Spring DI 应使用的规范构造器

两个构造器均无 `@Autowired` 标注。Spring 在遇到多构造器且无显式注入标识时，无法确定应使用哪个构造器进行 Bean 创建，抛出 `BeanCreationException`。

## 3. 修改范围

仅修改 **1 个文件**：

| 文件 | 变更类型 |
|---|---|
| `src/main/java/com/xbk/lattice/compiler/prompt/SchemaAwarePrompts.java` | 新增 `@Autowired` 标注 + import |

## 4. 具体变更

### SchemaAwarePrompts.java

**变更 1**：新增 import

```java
// 之前
import com.xbk.lattice.compiler.config.CompilerProperties;
import org.springframework.stereotype.Service;

// 之后
import com.xbk.lattice.compiler.config.CompilerProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
```

**变更 2**：双参数构造器添加 `@Autowired`

```java
// 之前
public SchemaAwarePrompts(CompilerProperties compilerProperties, CompilerPromptProvider compilerPromptProvider) {

// 之后
@Autowired
public SchemaAwarePrompts(CompilerProperties compilerProperties, CompilerPromptProvider compilerPromptProvider) {
```

## 5. 未修改清单

以下文件均未触碰，符合任务约束：

- `src/main/resources/prompts/compiler/*.md`（6 个 prompt 文件）
- `CompilerPromptProvider.java`
- `CompileArticleNode.java`
- `ArticleCompileSupport.java`
- `ArticleReviewerGateway.java`
- `ReviewFixService.java`
- 任何测试文件
- 任何脚本或配置文件

## 6. 验证结果

### 6.1 Redline 扫描

```
bash scripts/scan-redline.sh special_cases_report.md
EXIT_CODE=0, BLOCKER=0
```

### 6.2 全量测试

```
mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test
Tests run: 822, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### 6.3 Spring 启动验证

`@SpringBootTest` 测试（如 `VaultExportServiceTests`）在 `mvn test` 中成功加载完整 `ApplicationContext`，日志输出 `Started VaultExportServiceTests in 2.466 seconds`，确认 `SchemaAwarePrompts` Bean 可被 Spring 正确创建，不再抛出 `BeanCreationException`。

单参数构造器保留不变，`SchemaAwarePromptsTests` 中 `new SchemaAwarePrompts(new CompilerProperties())` 测试全部通过，测试兼容性未受影响。

## 7. 修复原理

Spring 构造器注入规则：

1. 当 Bean 只有一个构造器时，Spring 自动使用该构造器注入，无需 `@Autowired`。
2. 当 Bean 有多个构造器时，Spring 要求必须有一个构造器标注 `@Autowired`，否则抛出 `BeanCreationException`。
3. 标注 `@Autowired` 的构造器是 Spring 容器使用的注入入口；未标注的构造器仅供非容器场景（如测试中手工 `new`）使用。

本修复在双参数构造器上添加 `@Autowired`，Spring 将使用该构造器注入 `CompilerProperties` 和 `CompilerPromptProvider`；单参数构造器保持无标注，仅供测试代码直接调用。

## 8. 影响评估

- **运行时影响**：修复后 Spring 容器能正确创建 `SchemaAwarePrompts` Bean，消除了启动失败。
- **行为一致性**：双参数构造器逻辑未变，仍与修改前一致（赋值两个字段）。
- **测试兼容性**：单参数构造器保留，所有使用 `new SchemaAwarePrompts(compilerProperties)` 的测试无需改动。
- **向后兼容**：`@Autowired` 是 Spring 标准注解，不影响非 Spring 环境下的手工构造。

## 9. 风险评估

| 风险项 | 评级 | 说明 |
|---|---|---|
| Spring 注入歧义 | 已消除 | `@Autowired` 明确指定注入构造器 |
| 测试回归 | 无风险 | 822/822 全量通过 |
| Redline 违规 | 无 | BLOCKER=0 |
| 行为变更 | 无 | 构造器逻辑未改动 |

## 10. 结论

通过在 `SchemaAwarePrompts` 双参数构造器上添加 `@Autowired` 注解，精确解决了 Spring 多构造器注入歧义导致的启动失败。修改范围严格控制在 1 个文件、2 处变更（1 行 import + 1 行注解），全量测试 822/822 通过，Redline BLOCKER=0，Spring 上下文启动验证通过。
