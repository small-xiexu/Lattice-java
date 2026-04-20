# AGENTS.md

## 项目级环境约定

- 语言：始终使用简体中文回复
- JDK：本项目默认使用 **JDK 21**，不要使用 Java 8 运行 Maven 或测试
- Maven 全局本地仓库：`/Users/sxie/maven/repository`
- Maven 默认策略：优先沿用用户全局 Maven 配置与上述全局本地仓库，不要为项目长期保留独立本地仓库
- 项目级 `maven-settings.xml`：仅在排查全局镜像、网络或仓库污染问题时临时创建；问题确认后应删除
- 当前状态补充：由于全局 `alimaven` 握手不稳定，`.codex/maven-settings.xml` 已临时恢复用于开发验证；如全局镜像恢复正常，可再删除
- 临时缓存目录：`.m2/`、`.m2-central/` 仅用于一次性验证，不是项目必需目录，可直接删除
- PostgreSQL 默认依赖：使用现有 Docker 容器 `vector_db`（`0.0.0.0:5432->5432`），默认数据库为 `ai-rag-knowledge`
- Redis 默认依赖：使用现有 Docker 容器 `redis`（`0.0.0.0:6379->6379`）
- 日常开发、测试、回归默认直接复用上述现有容器；除非用户明确要求，不要自行 `docker compose up` 新的 PostgreSQL / Redis 实例，避免端口冲突和环境漂移
- 后台真实验收默认优先使用 `openai` 路由完成编译、审查与 query 回归；`claude` 仅在用户明确要求验证 Claude 路由，或需要专项核验 Anthropic 兼容性时再启用，避免高 token 成本成为日常回归负担

## 当前基线结论

- Spring Boot 基线：`3.5.1`
- Spring AI 基线：`1.1.2`
- Spring AI Alibaba Graph 基线：`1.1.2.0`
- Sa-Token Starter：`cn.dev33:sa-token-spring-boot3-starter:1.45.0`
- PostgreSQL Driver：`org.postgresql:postgresql:42.7.7`
- pgvector Java 坐标：`com.pgvector:pgvector:0.1.6`
- Embedding 基线：`text-embedding-3-small + vector(1536)`
- Hibernate 版本：`6.6.18.Final`

## 实施约定

- Git 分支策略：后续一切开发、修复、联调与验收默认直接在 `main` 分支进行；除非用户明确要求，否则不要新建任何功能分支、修复分支或临时分支
- 当前运行、验收与回归入口以 [`docs/项目启动配置清单.md`](/Users/sxie/xbk/Lattice-java/docs/%E9%A1%B9%E7%9B%AE%E5%90%AF%E5%8A%A8%E9%85%8D%E7%BD%AE%E6%B8%85%E5%8D%95.md)、[`README.md`](/Users/sxie/xbk/Lattice-java/README.md) 与 [`docs/项目全流程真实验收手册.md`](/Users/sxie/xbk/Lattice-java/docs/%E9%A1%B9%E7%9B%AE%E5%85%A8%E6%B5%81%E7%A8%8B%E7%9C%9F%E5%AE%9E%E9%AA%8C%E6%94%B6%E6%89%8B%E5%86%8C.md) 为准
- `B5-B9` 阶段历史文档、Graph 完整设计台账、专题技术方案与一次性回归附录均已退场，不再作为当前推进入口
- 若继续推进后续迭代，先读取上述运行/验收文档；如需多阶段执行，先新建或指定新的执行清单，再开始实施
- B1 默认不引入向量 ORM 映射；向量字段写入和检索后置到 B3，优先使用 `JdbcTemplate/jOOQ + SQL`
- 涉及 PostgreSQL 本机端口访问、Docker `exec` 或外网依赖下载时，注意当前环境可能需要额外权限
- 若做后台/真实链路验收，默认先走 OpenAI 成本更可控的模型绑定完成主回归；只有在“验证 Claude 角色选路是否仍可用”这一类专项场景下，才临时切到 Claude 绑定并在验收后恢复
