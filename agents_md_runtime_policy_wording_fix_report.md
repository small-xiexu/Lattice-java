# AGENTS.md Deep Research 绑定策略措辞修正报告

## 修改前后对比

### 修改前（第 34 行）

> \- Deep Research 绑定策略：启动期默认校验 `deep_research` 角色绑定，不完整时只告警不阻塞启动；运行期仍按 fail-closed 执行。仅在临时排障时使用 `LATTICE_LLM_DEEP_RESEARCH_STARTUP_VALIDATION_ENABLED=false`（配置源：`src/main/resources/config/lattice-llm.yml`）

### 修改后（第 34 行）

> \- Deep Research 绑定策略：运行期默认按 fail-closed 执行；启动期校验与临时降级开关的具体使用方式，以项目启动配置清单为准

## 为什么这样改更适合 AGENTS

1. **AGENTS 定位是顶层项目约定，不是配置手册。** 原写法展开了具体环境变量名和 YAML 文件路径，这些细节更适合放在 `docs/项目启动配置清单.md` 中，而不是作为 AGENTS 的独立段落在每次会话中被加载。

2. **降低上下文噪音。** 每个 agent 每次启动都会加载 AGENTS.md，包含开关细节增加了认知负担，但对大多数只读 agent（agentB/agentC/agentD）而言该信息无关。

3. **减少信息漂移风险。** 环境变量名和配置源路径是易变信息，集中到配置清单文档后只需更新一处，不会出现 AGENTS 与配置清单不一致的情况。

4. **保留项目级约定的本质。** 新表述保留了核心语义（fail-closed 运行期策略），并将操作细节委托给启动配置清单，符合 AGENTS 约定层的抽象层级。

## 本轮是否修改代码

**否。** 本轮仅修改 `AGENTS.md` 中 Deep Research 绑定策略一条，未触碰任何 `src/**`、其他文档或配置。
