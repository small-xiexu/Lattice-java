# Contributing to Lattice

感谢你参与 Lattice。项目会读取外部文件和 Git 仓库，调用模型与网络服务，并提供 MCP、Vault、快照和回滚能力，因此可复现性与安全边界和功能正确性同等重要。

## 开始之前

- 使用 JDK 21 和 Maven；不要用 Java 8 运行构建或测试。
- Maven 构建会在项目内安装 Node `24.18.0` 与 npm `11.16.0`，并执行前端门禁。
- 完整 `mvn clean package` 包含 PostgreSQL/pgvector 与 Redis 集成测试，需要 `127.0.0.1:5432` 的 `ai-rag-knowledge-test` 数据库和 `127.0.0.1:6379` 的 Redis。GitHub Actions 会自动提供隔离服务。
- 测试与构建不应调用真实模型 Provider。
- 不要提交 API Key、Token、Cookie、私有资料、真实用户数据、绝对凭据路径或含敏感内容的日志。
- 安全问题请按 [SECURITY.md](SECURITY.md) 私密报告，不要在公开 Issue 中披露细节。

## 提交改动

1. 从最新 `main` 创建自己的分支，并让一个 PR 只解决一个明确问题。
2. 先写清失败现象、根因、行为边界和验证方式，再修改实现。
3. 为行为变化增加最小失败用例；不要通过修改测试来掩盖回归。
4. 更新受到影响的 README、配置说明或验收文档。
5. 在 PR 中列出实际执行的命令和结果；未执行的检查必须明确说明原因。

Query、检索、回答、引用和编译链路禁止加入面向特定文档、文件名、业务术语、问题样例或期望答案的硬编码。效果问题应回到通用抽取、结构化证据、召回、排序、citation binding 或 grounding 能力解决。

## 本地门禁

完整门禁前，确认 PostgreSQL/pgvector 与 Redis 已就绪。首次建立测试库、DDL 变化或失败测试留下脏状态时，才显式重建测试 schema：

```bash
LATTICE_DEV_DB_NAME=ai-rag-knowledge-test ./scripts/reset-lattice-schema.sh
```

该命令会删除并重建 `ai-rag-knowledge-test.lattice`。执行前必须核对环境变量和目标数据库，禁止指向生产或共享数据。

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"  # macOS 示例
bash scripts/scan-redline.sh /tmp/lattice-special-cases-report.md
mvn --batch-mode --no-transfer-progress clean package
```

`clean package` 会执行前端 `npm ci`、lint、typecheck、Vitest、Vite build 和 Java 测试。只修改前端时可先运行：

```bash
cd frontend
npm ci
npm run lint
npm run typecheck
npm run test
npm run build
```

Playwright 和真实模型验收需要额外运行环境，步骤见 [项目全流程真实验收手册](docs/%E9%A1%B9%E7%9B%AE%E5%85%A8%E6%B5%81%E7%A8%8B%E7%9C%9F%E5%AE%9E%E9%AA%8C%E6%94%B6%E6%89%8B%E5%86%8C.md)。不要为了普通 PR 重建主库或调用真实 Provider。

## 安全与供应链要求

- 把上传内容、Git 仓库、文档元数据、`SCHEMA.md`、Issue 和 PR 文本视为不可信输入。
- 文件操作必须验证允许根目录、规范化路径、符号链接和最终写入/删除目标。
- 网络访问必须验证协议、主机、重定向和内网地址；凭据只传给预期目标。
- MCP、Admin、Vault、重建和回滚等高风险能力必须保留明确授权与审计边界。
- 新增 Maven、npm 或 GitHub Actions 依赖时，说明必要性，优先固定可审查版本，并检查维护状态和传递依赖。
- 测试使用 Fake/Mock、临时目录和可恢复数据。真实 Provider 调用、费用和共享环境变更必须事先获得明确授权。

## Pull Request 检查项

- 变更范围和根因清楚，没有夹带无关重构。
- 新增或更新的测试能覆盖行为变化，且没有针对评测样例写特判。
- 红线扫描与 `mvn clean package` 通过，或已说明未验证项。
- 日志、截图、fixture 和提交历史中没有凭据或私有数据。
- 已评估文件、Shell、网络、模型、MCP、权限和依赖供应链影响。
