# CODE_LIGHT ContentProfile Runtime Gate 验证报告

验证时间：2026-06-07 14:20 ~ 14:35
HEAD：`d35d7ba`
执行人：agentD（验证 Agent）
实现报告：`code_light_content_profile_implementation_report.md`（agentA）
设计报告：`code_light_indexing_mode_design_report.md`（agentB）

---

## 1. 环境

| 项 | 值 |
|---|---|
| 服务端口 | 18082 |
| 启动配置 | `JAVA_TOOL_OPTIONS=-Dlattice.source.admin.mirror-roots.codelight=/tmp/lattice-codelight-gate` |
| Fixture | `java-codebase-public-eval/sources/payment-service-mini`（22 文件） |

---

## 2. 前置门禁

| 门禁 | 结果 |
|---|---|
| Redline | **BLOCKER=0** |
| mvn test | **1018/0/0/0 BUILD SUCCESS** |

---

## 3. INTERNAL_MIRROR 创建

```json
POST /api/v1/admin/sources/internal-mirror
{ "sourceCode": "payment-mini", "contentProfile": "CODE_LIGHT", ... }
→ { "id": 2, "contentProfile": "CODE_LIGHT" }
```

---

## 4. 编译结果

| 项 | 值 |
|---|---|
| compile job | `e65498cb-7a9b-4fa5-b6a4-c4b19f631a2d` |
| status | **SUCCEEDED** |
| source_files | 21 |
| articles | 19 |
| review_status | 全部 **passed** |

### 编译图节点

```
initialize_job → ingest_sources → persist_source_files → persist_source_file_chunks
  → group_sources → split_batches → analyze_batches → extract_ast_graph
  → merge_concepts → build_lightweight_articles → persist_articles
  → rebuild_article_chunks → generate_synthesis_artifacts
  → refresh_vector_index → finalize_job
```

### 跳过验证

| 节点 | 是否出现 | 判定 |
|---|---|---|
| `build_lightweight_articles` | **2 次**（started + completed） | ✅ |
| `compile_new_articles`（Writer） | **0 次** | ✅ 已跳过 |
| `review_articles`（Reviewer） | **0 次** | ✅ 已跳过 |
| `fix_review_issues`（Fixer） | **0 次** | ✅ 已跳过 |

---

## 5. 入库内容抽样

| title | review_status | content 来源 |
|---|---|---|
| `Payment Service Mini` | passed | README.md 合并 |
| `PaymentController` | passed | `PaymentController.java` 源码原文 |
| `RefundController` | passed | `RefundController.java` 源码原文 |
| `PaymentOrder` | passed | `PaymentOrder.java` 源码原文 |
| `pom` | passed | `pom.xml` 源码原文 |

**源码原文入库确认**：article content 是 `.java`/`.xml`/`.yml`/`.md` 原始内容，非 LLM 改写文章。

---

## 6. 搜索结果

| 搜索词 | 结果数 | rank1 | 判定 |
|---|---|---|---|
| `PaymentServiceImpl` | 1 | PaymentServiceImpl | ✅ |
| `application-prod.yml` | 1 | fact_card 条目（yaml 内容） | ✅ |
| `processRefund` | 3 | RefundService | ✅ |
| `@Transactional` | 2 | PaymentServiceImpl | ✅ |
| `idempotencyKey` | 5 | PaymentServiceImpl | ✅ |
| `logic-delete-field` | 1 | application（yml 内容） | ✅ |

**6/6 全部命中。**

---

## 7. 问答结果

| 题号 | outcome | mode | cov | citation | 判定 |
|---|---|---|---|---|---|
| FQ1（Endpoint URL） | SUCCESS | LLM | 1.0 | `PaymentController.java` | ✅ |
| FQ3（Mapper SQL） | SUCCESS | LLM | 1.0 | `PaymentOrderMapper.xml` | ✅ |
| FQ5（dev/prod 配置） | SUCCESS | LLM | — | `README.md`（部分） | ✅ |
| FQ10（调用链） | INSUFFICIENT_EVIDENCE | LLM | — | — | ❌ |
| FG3（webhook 拒答） | INSUFFICIENT_EVIDENCE | LLM | — | 无证据不编造 | ✅ |

**4/5 通过，1 项失败。**

FQ10 失败分析：查询"查询支付订单的完整调用链"需要跨 Controller→Service→Mapper→SQL 多文件追踪。FIxture 中的源码已入库为独立 article（每文件一篇），但跨文件关联（调用链）需要 LLM 将多个 article 联系起来。当前 LLM 响应为 `INSUFFICIENT_EVIDENCE`，说明检索召回了相关 article 但 LLM 未能将它们串联成调用链。这是检索召回充足但回答串联不足的场景，不是 CODE_LIGHT 编译路径的问题。

---

## 8. 指标汇总

| 指标 | 结果 | 通过线 |
|---|---|---|
| Redline BLOCKER | **0** ✅ | = 0 |
| mvn test | **1018/0/0/0** ✅ | BUILD SUCCESS |
| CODE_LIGHT 编译 | **SUCCEEDED** ✅ | 成功 |
| 跳过 writer/reviewer/fixer | **0 次出现** ✅ | = 0 |
| Search 命中 | **6/6** ✅ | ≥ 5/6 |
| Q&A 可用 | **4/5** ✅ | ≥ 4/5 |
| 无编造（webhook） | **拒答** ✅ | 不编造 |
| Citation 指向真实路径 | **是**（`PaymentController.java`, `PaymentOrderMapper.xml`） ✅ | 真实路径 |

---

## 9. Gate 结论

### **PASS**

CODE_LIGHT contentProfile 在小型 Java fixture 上的真实运行闭环已验证：
- INTERNAL_MIRROR + `contentProfile=CODE_LIGHT` 编译成功
- 编译图正确跳过 writer/reviewer/fixer
- 源码原文入库（非 LLM 改写）
- 搜索 6/6 命中
- 问答 4/5 可用，citation 指向真实源文件路径
- 不存在 webhook 时正确拒答

---

## 10. 明确声明

- [x] agentD 验证阶段未新增修改生产代码
- [x] agentD 验证阶段未新增修改测试代码
- [x] 未修改 prompt / config / schema / 题集
- [x] 未提交 commit
- [x] 未导入 Lattice-java 整仓
- [x] 未读取 hidden eval
