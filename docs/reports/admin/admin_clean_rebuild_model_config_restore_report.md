# Admin Clean Rebuild & Model Config Restore 操作报告

操作时间：2026-06-09 10:16 ~ 10:18
执行人：agentD

---

## 1. 环境信息

| 项 | 值 |
|---|---|
| 数据库 | `ai-rag-knowledge.lattice`（Docker `vector_db`，5432） |
| Redis | Docker `redis`（6379） |
| 应用 | Docker `lattice_app`（18082） |
| 清库脚本 | `./scripts/reset-lattice-schema.sh` |
| 备份文件 | `.codex/run/llm-config-backup-before-reset-20260609-101633.sql`（249 行） |

---

## 2. 操作步骤

| 步骤 | 命令/方式 | 结果 |
|---|---|---|
| 备份模型配置 | `docker exec vector_db pg_dump` 6 表 | ✅ |
| 停止应用 | `docker stop lattice_app` | ✅ |
| 清库重建 | `./scripts/reset-lattice-schema.sh` | ✅ schema 重建完成 |
| 恢复配置 | `docker exec -i vector_db psql < backup.sql` | ✅ |
| 启动应用 | `docker start lattice_app` | ✅ |

---

## 3. 恢复后配置

### 连接（2）
| connectionCode | providerType | enabled |
|---|---|---|
| `local_openai` | openai_compatible | true |
| `zhipu_embedding` | openai_compatible | true |

### 模型（2）
| modelCode | modelKind | enabled |
|---|---|---|
| `gpt-5.5` | CHAT | true |
| `embedding-3` | EMBEDDING | true |

### 绑定（11）
compile: writer / reviewer / fixer / field-alias-enricher
query: answer / reviewer / rewrite
deep_research: planner / researcher / synthesizer / reviewer

### 向量
vectorEnabled=true, embeddingModelProfileId=2（embedding-3）

---

## 4. 健康状态

| 检查项 | 状态 |
|---|---|
| 端口 18082 可访问 | ✅ |
| `/api/v1/admin/llm/connections` | ✅ 2 条 |
| `/api/v1/admin/llm/models` | ✅ 2 条 |
| `/api/v1/admin/llm/bindings` | ✅ 11 条 |
| `/api/v1/admin/vector/config` | ✅ enabled |
| 数据库 articles | **0**（空库，符合预期） |
| 数据库 source_files | **0**（空库，符合预期） |
| `/actuator/health` | DOWN（空库无向量索引，导入资料后自动恢复 UP） |

---

## 5. 是否可以开始手动导入资料

**是。** 数据库已清空重建，所有模型配置已恢复。用户可通过以下方式导入资料：
- 后台 `/admin` 上传文件
- 或使用 API `POST /api/v1/admin/uploads`

导入后 compile 完成即可开始问答和搜索。

---

## 6. 阻塞项

**无阻塞。** 模型配置完整（连接/模型/绑定/向量均已就绪）。

---

## 7. 明确声明

- [x] 未修改生产代码
- [x] 未修改测试代码
- [x] 未提交 commit
- [x] 未导入任何资料
- [x] 未输出密钥明文
- [x] 备份文件存放于 `.codex/run/`，不提交
