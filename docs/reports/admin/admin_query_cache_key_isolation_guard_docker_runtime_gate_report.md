# Query Cache Key 隔离 + Guard 修复 Docker Runtime Gate 报告

验证时间：2026-06-10 14:19 ~ 14:25
HEAD：`27400a5`
执行人：agentD
修复报告：`admin_query_cache_key_isolation_long_term_fix_result_report.md`（agentA）

---

## 1. 门禁

| 门禁 | 结果 |
|---|---|
| Redline | **BLOCKER=0** |
| QueryCacheKeyCanonicalizerTests + QueryGraph* | **33/0/0/0 BUILD SUCCESS** |
| Health | **`{"status":"UP"}`** |

---

## 2. 部署确认

| 检查项 | 结果 |
|---|---|
| 本地 jar sha256 | `7a000b83e1be7415580eb886082ef41c8e26fb0a82250dba54d89ea2d74b02fa` |
| 容器 jar sha256 | 相同 |
| 新 class 在 jar | `BOOT-INF/classes/.../QueryCacheKeyCanonicalizer.class` ✅ |
| 容器重启后 UP | ✅ |

---

## 3. 缓存归并验证

| 查询 | qid | answer |
|---|---|---|
| "发布检查表里还有哪些检查项没有完成**呢**？分别是哪个责任人**呢**？" | bf401554 | CHK-04、CHK-08（进行中）、CHK-11、CHK-12（未完成） |
| "发布检查表里还有哪些检查项没有完成？分别是哪个责任人？" | 0a41b9ef | **同上**（一致） |

**两个仅差语气词/标点的查询返回一致答案。**

---

## 4. Redis 缓存键检查

```
KEYS llm:query:cache:*
→ llm:query:cache:发布检查表里还有哪些检查项没有完成 分别是哪个责任人
```

| 检查项 | 结果 |
|---|---|
| 缓存键数量 | **1**（已归并） |
| 缓存键格式 | `llm:query:cache:{canonicalizedQuestion}` ✅ |
| null/空 key | **0** ❌ 不存在 |
| canonical form | 去掉了 `呢` 和 `？` ✅ |
| 原始问题在 normalized_question | 保留（query trace 中原始文本不变） |

---

## 5. Guard 行为验证

| 检查项 | 结果 |
|---|---|
| `llm:query:cache:null` | **不存在** ✅ |
| 空 cache key | **0** ✅ |
| 仅存在 canonicalized 格式 key | ✅ |

---

## 6. 结论

### **PASS — 修复已生效，可提交**

- ✅ 两个近似问题命中同一 canonical cache key
- ✅ 答案一致
- ✅ normalizedQuestion 保留原始语义
- ✅ 无 null/空 cache key
- ✅ guard 正确保护了缓存键格式

---

## 7. 明确声明

- [x] 未修改生产代码
- [x] 未清库、未重建
- [x] 未提交 commit
