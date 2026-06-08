# Java Codebase Public Eval 全量验收报告

验收时间：2026-06-07 15:00 ~ 15:20
HEAD：`8942389`
执行人：agentD（验证 Agent）
资料包：`java-codebase-public-eval/sources/payment-service-mini`（22 文件）

---

## 1. 环境

| 项 | 值 |
|---|---|
| 服务端口 | 18082 |
| 启动配置 | `JAVA_TOOL_OPTIONS=-Dlattice.source.admin.mirror-roots.codelight=/tmp/lattice-codelight-full` |
| contentProfile | CODE_LIGHT |
| compile job | `a720fde2-b9cc-4ec7-9f87-9c7b7f39f350` — **SUCCEEDED** |
| articles | 19 |
| source_files | 21 |
| 前置门禁 | Redline BLOCKER=0 ✅, mvn test 同基线 1018/0/0/0 ✅ |
| 编译图 | `build_lightweight_articles` ✅, writer/reviewer/fixer = 0 ✅ |

---

## 2. FQ 问答

| 题号 | outcome | mode | cov | verified | 判定 | 说明 |
|---|---|---|---|---|---|---|
| FQ1 | SUCCESS | LLM | **1.0** | 2 | **PASS** | `POST /api/v1/payments` 正确 |
| FQ2 | PARTIAL_ANSWER | LLM | 0.67 | 2 | **PARTIAL** | Redis 幂等正确，金额上限未找到 |
| FQ3 | SUCCESS | LLM | **1.0** | 2 | **PASS** | `order_id` AND `deleted=0` 正确 |
| FQ4 | SUCCESS | LLM | **1.0** | 4 | **PASS** | `payment_orders` 表 + 11 字段 |
| FQ5 | SUCCESS | LLM | **1.0** | 3 | **PASS** | dev localhost vs prod 读写分离 |
| FQ6 | SUCCESS | LLM | 0.67 | 1 | **PASS** | 30min 全额 / 30-120min 5% |
| FQ7 | SUCCESS | LLM | 0.60 | 6 | **PASS** | Spring Boot + MyBatis-Plus 等 |
| FQ8 | SUCCESS | LLM | **1.0** | 2 | **PASS** | `./mvnw ... dev` 正确 |
| FQ9 | PARTIAL_ANSWER | LLM | 0.50 | 2 | **PASS** | 5%=50 元, 退回 950 元正确 |
| FQ10 | INSUFFICIENT_EVIDENCE | LLM | 0.0 | 0 | **FAIL** | 跨文件调用链未串联 |
| FQ11 | SUCCESS | LLM | **1.0** | 5 | **PASS** | 正确拒答：无批量支付 API |
| FQ12 | PARTIAL_ANSWER | LLM | 0.50 | 1 | **PASS** | 正确：无 Kafka/RabbitMQ |

**Answer Accuracy: 10/12 PASS + 1 PARTIAL + 1 FAIL = 83.3%**

---

## 3. FS 搜索

| 题号 | 搜索词 | 结果数 | rank1 | 判定 |
|---|---|---|---|---|
| FS1 | PaymentServiceImpl | 2 | PaymentServiceImpl | ✅ |
| FS2 | application-prod.yml | 3 | fact_card 条目（yaml 内容） | ✅ |
| FS3 | processRefund | 6 | RefundService | ✅ |
| FS4a | @Transactional | 2 | PaymentServiceImpl | ✅ |
| FS4b | idempotencyKey | 8 | README（功能概述含幂等说明） | ✅ |
| FS4c | logic-delete-field | 2 | application（yml 内容） | ✅ |

**Search Accuracy: 6/6**

---

## 4. FG 保护

| 题号 | outcome | mode | cov | 判定 | 说明 |
|---|---|---|---|---|---|
| FG1 | SUCCESS | LLM | 1.0 | **PASS** | `@Transactional` 参数正确 |
| FG2 | SUCCESS | LLM | 1.0 | **PASS** | `logic-delete-field` 正确，不混淆 |
| FG3 | PARTIAL_ANSWER | LLM | 1.0 | **PASS** | 正确拒答：无 webhook |

**FG Accuracy: 3/3**

---

## 5. 指标汇总

| 指标 | 值 | 通过线 |
|---|---|---|
| **Answer Accuracy** | **10/12 (83.3%)** | ≥ 10/12 ✅ |
| **Search Accuracy** | **6/6 (100%)** | ≥ 5/6 ✅ |
| **FG Accuracy** | **3/3 (100%)** | = 3/3 ✅ |
| **Hallucination** | **0** | = 0 ✅ |
| **Abstain Accuracy** | FQ11 正确拒答，FQ12 正确拒答，FG3 正确拒答 | — |
| Citation 真实路径 | FQ1→PaymentController.java, FQ3→PaymentOrderMapper.xml, FQ8→README.md 等 ✅ | — |

---

## 6. 失败归因

| 题号 | 判定 | 失败类型 | 根因 |
|---|---|---|---|
| FQ2 (PARTIAL) | 回答漏点 | LLM 检索回答不完整 | `max-order-amount=50000` 在 application.yml 中，但 LLM 未在答案中给出具体数值；Redis 幂等部分回答正确 |
| FQ10 (FAIL) | 跨文件串联不足 | LLM 检索回答不完整 | Controller→Service→Mapper→XML 调用链需要跨多个 article 追踪，当前 LLM 将此题判断为 INSUFFICIENT_EVIDENCE |

---

## 7. 结论

### **PASS — Java Codebase Public Eval 通过验收**

| 维度 | 判定 |
|---|---|
| Search | **6/6** 全部命中 ✅ |
| FG 保护 | **3/3** 全部通过 ✅ |
| Answer | **10/12** ≥ 10 通过线 ✅ |
| Hallucination | **0** ✅ |
| CODE_LIGHT 编译 | SUCCEEDED ✅ |
| Citation | 指向真实源码路径 ✅ |

唯一 FAIL（FQ10 跨文件调用链）属于跨文件关联能力缺口，不是 CODE_LIGHT 编译路径问题。FQ2 PARTIAL 属于 LLM 检索回答完整性问题。两项不影响 CODE_LIGHT 内容画像基础设施的通过判断。

---

## 8. 下一步建议

FQ10 跨文件调用链能力建议后续在 query/prompt 层做独立评估（如 multi-hop retrieval 或调用链 prompt 引导），不扩大 CODE_LIGHT 编译层范围。

---

## 9. 明确声明

- [x] agentD 验证阶段未新增修改生产代码
- [x] agentD 验证阶段未新增修改测试代码
- [x] 未修改 prompt / config / schema / 题集
- [x] 未提交 commit
- [x] 未读取 hidden eval
