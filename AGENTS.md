# AGENTS.md

## 项目级环境约定

- 语言：始终使用简体中文回复
- JDK：本项目默认使用 **JDK 21**，不要使用 Java 8 运行 Maven 或测试
- Maven 全局本地仓库：`/Users/sxie/maven/repository`
- Maven 默认策略：优先沿用用户全局 Maven 配置与上述全局本地仓库，不要为项目长期保留独立本地仓库
- 项目级 `maven-settings.xml`：仅在排查全局镜像、网络或仓库污染问题时临时创建；问题确认后应删除
- 当前状态补充：由于全局 `alimaven` 握手不稳定，`.codex/maven-settings.xml` 已临时恢复用于开发验证；如全局镜像恢复正常，可再删除
- 临时缓存目录：`.m2/`、`.m2-central/` 仅用于一次性验证，不是项目必需目录，可直接删除

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

- 当前以 [`.codex/B5-B8 对齐原始项目完整改动方案.md`](/Users/sxie/xbk/Lattice-java/.codex/B5-B8%20对齐原始项目完整改动方案.md)、[`.codex/B9 原版能力超越执行清单.md`](/Users/sxie/xbk/Lattice-java/.codex/B9%20原版能力超越执行清单.md) 和 [`.codex/项目启动配置清单.md`](/Users/sxie/xbk/Lattice-java/.codex/项目启动配置清单.md) 作为当前实施、验收与运行入口
- 若继续推进后续迭代，先读取上述文档，再决定下一步
- B1 默认不引入向量 ORM 映射；向量字段写入和检索后置到 B3，优先使用 `JdbcTemplate/jOOQ + SQL`
- 涉及 PostgreSQL 本机端口访问、Docker `exec` 或外网依赖下载时，注意当前环境可能需要额外权限
