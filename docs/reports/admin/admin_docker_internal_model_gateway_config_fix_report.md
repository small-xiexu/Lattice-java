# Docker 内部模型网关配置修复报告

操作时间：2026-06-09 10:40 ~ 10:55
执行人：agentD

---

## 1. 问题诊断

| 检查项 | 结果 |
|---|---|
| lattice_app、sub2api、redis、vector_db 在同一网络 | ✅ binghe-network |
| sub2api:8080 在 lattice_app 内是否可达 | ❌（走代理失败） |
| sub2api:8080 去代理后是否可达 | ✅（401 API_KEY_REQUIRED，网络正常） |
| local_openai base_url 当前值 | ❌ `http://127.0.0.1:8888`（容器内指向自身） |
| 代理设置是否覆盖 sub2api | ❌ NO_PROXY 不含 sub2api |

---

## 2. 修复内容

### 2.1 base_url 修复

```sql
UPDATE lattice.llm_provider_connections
SET base_url = 'http://sub2api:8080'
WHERE connection_code = 'local_openai';
```

| 项 | 修复前 | 修复后 |
|---|---|---|
| local_openai base_url | `http://127.0.0.1:8888` | **`http://sub2api:8080`** |

### 2.2 NO_PROXY 修复

容器重建时添加 `sub2api,vector_db,redis,binghe-network` 到 NO_PROXY 环境变量。

### 2.3 容器重建命令

```bash
docker run -d --name lattice_app --network binghe-network -p 18082:18082 \
  -e NO_PROXY="sub2api,vector_db,redis,localhost,127.0.0.1,..." \
  lattice-java:local
```

---

## 3. 修复后验证

| 检查项 | 结果 |
|---|---|
| Health | ✅ `{"status":"UP"}` |
| sub2api 可达性 | ✅ 401 API_KEY_REQUIRED（网络正常） |
| local_openai base_url | ✅ `http://sub2api:8080` |
| 模型配置完整 | ✅ 2 连接 + 2 模型 + 11 绑定 + 向量 |

---

## 4. 数据清理

清理了本次 reviewer 调用失败产生的 34 条不可信待人工确认草稿及相关编译记录：

| 清理项 | 数量 |
|---|---|
| compile_article_review_queue（34 条） | ✅ 已清理 |
| source_files（37 条） | ✅ 已清理 |
| source_sync_runs（2 条） | ✅ 已清理 |
| compile_jobs（2 条） | ✅ 已清理 |
| knowledge_sources（2 条） | ✅ 已清理 |
| articles（0 条，未正式入库） | 无需清理 |

---

## 5. 遗留风险

| 风险 | 说明 |
|---|---|
| 容器重建依赖临时命令 | 建议将 NO_PROXY 设置固化到 docker-compose.yml，避免下次重建丢失 |
| 代理依赖外部基础设施 | 如果公司代理（proxyproxy.orb.internal:8305）不可用，子请求可能受影响 |

---

## 6. 用户下一步

**可以重新导入 Fresh Eval 资料。** 数据库已清空，模型配置已修复（base_url → sub2api:8080），编译链路应正常工作。导入后 compile 应不再产生大量 needs_human_review。

---

## 7. 明确声明

- [x] 未修改生产代码
- [x] 未修改测试代码
- [x] 未提交 commit
- [x] 仅修改本地开发库配置和容器环境变量
