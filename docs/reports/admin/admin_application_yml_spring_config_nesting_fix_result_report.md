# application.yml Spring 配置层级修复 结果报告

时间：2026-06-09
执行人：agentA
类型：最小配置修复

---

## 1. 问题根因

`application.yml` 中 `server:` 被错误地缩进在 `spring:` 下面（2 空格缩进），导致本应属于 `spring:` 的 `ai`、`datasource`、`data` 三个顶级 key 被解析为 `server` 的子节点。

**错误结构（修复前）：**

```yaml
spring:
  application:
  servlet:
server:            # ← 缩进 2 空格，被解析为 spring.server
  tomcat:
  ai:              # ← 缩进 4 空格，被解析为 spring.server.ai
  datasource:      # ← 缩进 4 空格，被解析为 spring.server.datasource
  data:            # ← 缩进 4 空格，被解析为 spring.server.data
```

这导致 Spring Boot 从未读取到 `spring.data.redis.host` / `spring.data.redis.port`，无法使用环境变量 `LATTICE_REDIS_HOST=redis` / `LATTICE_REDIS_PORT=6379`，回退到 Spring Data Redis 默认值 `localhost:6379`。

Docker 环境中 Redis 运行在 `redis` 主机，`localhost:6379` 无法连接 → `RedisConnectionFailureException` → `ingest_sources` 失败 → 前端显示 `COMPILE_IO_ERROR`。

**正确结构（修复后）：**

```yaml
spring:
  application:
  servlet:
  ai:              # ← 缩进 2 空格，spring.ai
  datasource:      # ← 缩进 2 空格，spring.datasource
  data:            # ← 缩进 2 空格，spring.data
server:            # ← 缩进 0 空格，顶层 key
  tomcat:
```

---

## 2. 修改内容

| 项 | 说明 |
|------|------|
| 修改文件 | `src/main/resources/application.yml` |
| 修改类型 | 纯 YAML 缩进修复，无任何配置值变更 |
| `server:` | 从 `spring:` 子节点提升为顶层 key |
| `spring.ai:` | 回归 `spring:` 子节点（原被嵌套在 `server:` 下） |
| `spring.datasource:` | 回归 `spring:` 子节点 |
| `spring.data.redis:` | 回归 `spring:` 子节点 |
| `server.tomcat.max-part-count` | 保留，值不变 |
| `server.tomcat.max-part-header-size` | 保留，值不变 |
| `spring.servlet.multipart` | 保留，值不变 |
| `mybatis:` | 不变 |
| `management:` | 不变 |

**未修改任何 Java 代码、前端代码、测试代码、Docker 配置。**

---

## 3. 保留项确认

| 配置 | 状态 |
|------|:---:|
| `server.tomcat.max-part-count: ${SERVER_TOMCAT_MAX_PART_COUNT:10000}` | 保留 |
| `server.tomcat.max-part-header-size: ${SERVER_TOMCAT_MAX_PART_HEADER_SIZE:8KB}` | 保留 |
| `spring.servlet.multipart.max-file-size` | 保留 |
| `spring.servlet.multipart.max-request-size` | 保留 |
| `spring.datasource.url` | 保留 |
| `spring.data.redis.host: ${LATTICE_REDIS_HOST:127.0.0.1}` | 保留 |
| `spring.data.redis.port: ${LATTICE_REDIS_PORT:6379}` | 保留 |
| 所有 `spring.config.import` | 保留 |

---

## 4. 验证结果

| 验证项 | 结果 |
|------|:---:|
| YAML 语法 | OK（Ruby `YAML.load_file` 解析通过） |
| redline 扫描 | EXIT=0, BLOCKER=0 |
| Maven package (`-DskipTests`) | EXIT=0 |

---

## 5. 后续建议

1. 让 agentD 重启 `lattice_app` 容器，使新配置生效
2. 重新拖拽上传 `fresh-eval-2026-09` 验证 `ingest_sources` 不再报 `COMPILE_IO_ERROR`
3. 确认容器日志中 Redis 连接地址为 `redis:6379` 而非 `localhost:6379`

---

## 6. 明确声明

- [x] 仅修改 `application.yml` 的 YAML 层级缩进
- [x] 未修改任何配置值
- [x] 未修改任何 Java / 前端 / 测试 / Docker 文件
- [x] redline BLOCKER=0
- [x] Maven package 编译通过
- [x] 未提交 commit
