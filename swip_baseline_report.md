# SWIP Baseline Eval Report

- **报告时间**：2026-05-14
- **题集文件**：`docs/test/swip-query-eval-candidates.json`
- **题集版本**：0.1.0
- **源文件**：
  - SWIP智能键盘系统使用手册-20250702.docx（10.3MB）
  - SWIP智能键盘系统安装手册-202509.docx（12.9MB）

---

## 1. Redline 检查

| 指标 | 值 |
|---|---|
| BLOCKER | 0 |
| REVIEW | 若干（均为已知 allowlist 候选） |
| ALLOWLIST | 若干（路径/URL/文件后缀/工程枚举/状态常量等） |

结论：BLOCKER=0，可以继续。

---

## 2. 数据库隔离

| 项 | 值 |
|---|---|
| 编号 | `ai-rag-swip-eval` |
| Schema | `lattice` |
| 主 baseline 库 | `ai-rag-knowledge`（未受任何影响） |
| 是否隔离 | **是，完全独立数据库** |
| LLM 配置 | 从主库 `ai-rag-knowledge` 复制 provider_connections / model_profiles / agent_model_bindings 到 SWIP 库 |
| 风险 | 无。SWIP 库与主 baseline 库物理隔离，编译和查询均在 SWIP 库内完成 |

---

## 3. Source Set 清单和污染检查

### 3.1 Source Set

| 文件 | 大小 | 类型 |
|---|---|---|
| SWIP智能键盘系统使用手册-20250702.docx | 10.3MB | usage_manual |
| SWIP智能键盘系统安装手册-202509.docx | 12.9MB | installation_manual |

### 3.2 污染检查

```
rg -n "query-regression-suite|expectedPoints|mustNotClaim|baseline_report|_report|caseId|eval" /tmp/swip-eval-sources
```

结果：**0 命中**，无 eval/report/test 文件混入。

---

## 4. Compile 结果

| 指标 | 值 |
|---|---|
| source_files | 2（仅两份 SWIP docx） |
| articles | 4（LLM Writer 生成） |
| article 列表 | 1. Swip智能键盘系统使用手册 20250702（5313 chars, high confidence） |
|  | 2. 系统架构 5（SWIP智能键盘系统架构）（3561 chars, medium confidence） |
|  | 3. SWIP智能键盘系统安装手册 FAQ 33（5200 chars, high confidence） |
|  | 4. HTTPS证书安装（门店内网）（5432 chars, high confidence） |
| article_chunks | 12 |
| fact_cards | 5 |
| compile_jobs 失败 | 0 |

**注意**：
- 首次编译在无 LLM 配置的情况下执行（产生 FALLBACK 级 articles），已识别该问题后清库重新编译。
- 第二次编译正确使用了 LLM（compile.writer.deepseek-v4-flash），每篇文章 Writer 阶段约 33 秒。
- 两份文档仅产出 4 篇文章，覆盖率偏低——两个大文档的大量章节（如银行操作、杉德操作、证书更新、日志等）未作为独立 article 生成，影响后续查询命中。
- article 生成依赖 LLM 对文档结构的理解和 topic 切分质量，本次切分较粗。

---

## 5. SWIP Eval 总体指标

### 5.1 Generation Mode 分布

| Mode | 数量 |
|---|---|
| LLM | 17 |
| FALLBACK | 5 |
| RULE_BASED | 1 |

### 5.2 Answer Outcome 分布

| Outcome | 数量 |
|---|---|
| SUCCESS | 7 |
| PARTIAL_ANSWER | 14 |
| INSUFFICIENT_EVIDENCE | 1 |
| NO_RELEVANT_KNOWLEDGE | 1 |

### 5.3 总体指标

| 指标 | 值 |
|---|---|
| 总 case 数 | 22 |
| LLM 模式占比 | 77.3%（17/22） |
| Answer 完整通过率（SUCCESS） | 31.8%（7/22） |
| Partial + Success 率 | 95.5%（21/22 给出某种回答） |
| No-answer 率 | 4.5%（1/22） |
| FALLBACK 模式占比 | 22.7%（5/22） |

---

## 6. 逐 Case 结果表

### 6.1 Usage Manual Cases（使用手册）

| ID | Question | Pass/Fail | Mode | Outcome | AnswerTerms | SourceTerms | MustNot | 失败原因 |
|---|---|---|---|---|---|---|---|---|
| SWIP-USAGE-GOAL-001 | SWIP 系统目标 | FAIL | LLM | PARTIAL_ANSWER | 3/4 | 0/1 | 0 | 答案在关键位置截断，缺少"监控"要点 |
| SWIP-USAGE-SVC-READ-001 | SVC 卡读卡 | PASS | LLM | PARTIAL_ANSWER | 3/3 | 1/2 | 0 | 核心要点全部覆盖 |
| SWIP-USAGE-BANK-REFUND-001 | 银行卡退款 | FAIL | RULE_BASED | NO_RELEVANT_KNOWLEDGE | 0/3 | 0/2 | 0 | 检索未命中，文档中退款内容未被 article 覆盖 |
| SWIP-USAGE-BANK-SETTLEMENT-001 | 银行卡结算 | FAIL | LLM | PARTIAL_ANSWER | 0/3 | 2/2 | 0 | 回答内容偏题，未覆盖结算时机和结果 |
| SWIP-USAGE-REPRINT-001 | 银行卡重印 | FAIL | FALLBACK | PARTIAL_ANSWER | 1/3 | 1/2 | 0 | FALLBACK 模式，仅返回检索片段 |
| SWIP-USAGE-SAND-SIGN-001 | 杉德签到 | FAIL | LLM | SUCCESS | 2/3 | 1/1 | 0 | **事实错误**：答"只需一次"实际应每天开店前执行 |
| SWIP-USAGE-SAND-SETTLEMENT-001 | 杉德结算 | FAIL | LLM | SUCCESS | 1/3 | 1/1 | 0 | 缺失"按卡种打印""无交易不打印" |
| SWIP-USAGE-DESHI-VOID-001 | 得仕卡撤销 | FAIL | FALLBACK | PARTIAL_ANSWER | 1/4 | 1/2 | 0 | FALLBACK 模式，检索未命中得仕卡撤销条款 |
| SWIP-USAGE-EBUY-SETTLEMENT-001 | 银行积分日结 | FAIL | LLM | PARTIAL_ANSWER | 2/3 | 2/2 | 0 | 缺少"返回处理中时再次执行" |
| SWIP-FAQ-NO-RESPONSE-001 | 系统无响应排障 | FAIL | LLM | PARTIAL_ANSWER | 1/4 | 1/1 | 0 | 答案太简略，缺失 HTTPS 服务/区域 IT 等 |
| SWIP-FAQ-TERMINATE-STUCK-001 | 键盘卡死终止 | PASS | LLM | SUCCESS | 4/4 | 0/1 | 0 | 所有关键步骤覆盖完整 |
| SWIP-FAQ-PRINT-PAPER-001 | 打印纸规格 | FAIL | LLM | PARTIAL_ANSWER | 0/4 | 1/1 | 0 | 缺少尺寸和供应商信息 |

### 6.2 Installation Manual Cases（安装手册）

| ID | Question | Pass/Fail | Mode | Outcome | AnswerTerms | SourceTerms | MustNot | 失败原因 |
|---|---|---|---|---|---|---|---|---|
| SWIP-INSTALL-CERT-NAMING-001 | 证书命名 | FAIL | LLM | PARTIAL_ANSWER | 0/3 | 2/2 | 0 | 答案截断，只给出入网证书规则，缺少 HTTPS 规则 |
| SWIP-INSTALL-APP-LIST-001 | APP 列表 | FAIL | LLM | PARTIAL_ANSWER | 3/8 | 0/2 | 0 | 正确回答"证据不足"但未给出完整列表 |
| SWIP-INSTALL-IP-SUFFIX-001 | IP 后缀 | FAIL | FALLBACK | SUCCESS | 1/3 | 1/2 | 0 | FALLBACK 模式，未覆盖 149→151→150 调整步骤 |
| SWIP-INSTALL-SWIP-POS-JSON-001 | swip-pos.json | FAIL | FALLBACK | SUCCESS | 3/4 | 1/1 | 0 | FALLBACK 模式，缺失"有线键盘回退" |
| SWIP-INSTALL-KEY-FILE-001 | 交易密钥文件 | PASS | LLM | PARTIAL_ANSWER | 2/3 | 2/2 | 0 | 给出文件路径和含义，仅缺"一机一密" |
| SWIP-INSTALL-DLL-ORDER-001 | POS 初始化顺序 | FAIL | LLM | PARTIAL_ANSWER | 1/3 | 0/1 | 0 | 缺少 POS DLL 和目录路径 |
| SWIP-INSTALL-APP-UPGRADE-IMPACT-001 | APP 升级影响 | FAIL | LLM | PARTIAL_ANSWER | 3/7 | 3/4 | 0 | 答案太短，未按 APP 分组说明 |
| SWIP-INSTALL-LOGS-001 | 日志目录 | FAIL | LLM | PARTIAL_ANSWER | 0/7 | 0/2 | 0 | 完全未覆盖日志访问路径和目录映射 |
| SWIP-INSTALL-CERT-UPDATE-001 | 证书自动更新 | FAIL | LLM | SUCCESS | 0/5 | 1/1 | 0 | 答案内容不相关，未覆盖提前天数/时间/条件 |

### 6.3 无答案保护

| ID | Question | Pass/Fail | Mode | Outcome | AnswerTerms | SourceTerms | MustNot | 失败原因 |
|---|---|---|---|---|---|---|---|---|
| SWIP-NEG-UNANSWERABLE-001 | 支付宝微信支持 | PASS | LLM | INSUFFICIENT_EVIDENCE | 2/3 | 0/1 | 0 | 正确拒答，说明证据不足，未编造 |
| SWIP-NEG-UNANSWERABLE-002 | 数据库表结构 | FAIL | FALLBACK | SUCCESS | 1/3 | 0/1 | 0 | FALLBACK 模式，返回了不相关片段 |

---

## 7. Case 分类

### 7.1 可自动化稳定验收（6 个）

| ID | 说明 |
|---|---|
| SWIP-USAGE-SVC-READ-001 | LLM 正确覆盖所有要点 |
| SWIP-FAQ-TERMINATE-STUCK-001 | LLM 完美回答，步骤完整 |
| SWIP-INSTALL-KEY-FILE-001 | 核心路径正确，仅缺次要术语 |
| SWIP-NEG-UNANSWERABLE-001 | 正确拒答，证据引用合理 |
| SWIP-USAGE-GOAL-001 | 核心要点覆盖 3/4，截断问题可修复 |
| SWIP-USAGE-EBUY-SETTLEMENT-001 | 入口正确，缺"再次执行"细节 |

### 7.2 需要人工判断（7 个）

| ID | 说明 |
|---|---|
| SWIP-USAGE-SAND-SETTLEMENT-001 | 核心语义正确但术语不匹配 |
| SWIP-USAGE-REPRINT-001 | FALLBACK 模式，需检查检索覆盖 |
| SWIP-INSTALL-CERT-NAMING-001 | 答案截断，需确认是 article 覆盖不足还是 LLM 截断 |
| SWIP-INSTALL-APP-LIST-001 | 正确回答"证据不足"，但 article 应能覆盖 |
| SWIP-INSTALL-DLL-ORDER-001 | 部分覆盖，缺失技术细节 |
| SWIP-INSTALL-APP-UPGRADE-IMPACT-001 | 多点比较题，当前答案太短 |
| SWIP-INSTALL-CERT-UPDATE-001 | 答案不相关，可能 retrieval 方向错误 |

### 7.3 需要脱敏（6 个）

| ID | 原因 |
|---|---|
| SWIP-INSTALL-CERT-NAMING-001 | 含证书命名规则（VISIBILITY: INTERNAL_ONLY） |
| SWIP-INSTALL-IP-SUFFIX-001 | 含内部 IP 规则（VISIBILITY: INTERNAL_ONLY） |
| SWIP-INSTALL-SWIP-POS-JSON-001 | 含内部配置文件路径（VISIBILITY: INTERNAL_ONLY） |
| SWIP-INSTALL-KEY-FILE-001 | 含密钥文件路径（VISIBILITY: INTERNAL_ONLY） |
| SWIP-INSTALL-LOGS-001 | 含内部日志访问地址（VISIBILITY: INTERNAL_ONLY） |
| SWIP-INSTALL-CERT-UPDATE-001 | 含证书更新内部时间窗口（VISIBILITY: INTERNAL_ONLY） |

### 7.4 可能依赖截图/OCR（2 个）

| ID | 说明 |
|---|---|
| SWIP-INSTALL-LOGS-001 | 文档中日志目录截图较多，纯文本抽取可能丢失关键信息 |
| SWIP-USAGE-BANK-REFUND-001 | 退款流程有截图辅助说明，纯文本可能不足 |

### 7.5 题目或 expectedEvidence 需要修订（2 个）

| ID | 说明 |
|---|---|
| SWIP-USAGE-BANK-REFUND-001 | 未找到知识，可能是 article 未覆盖或术语不匹配 |
| SWIP-USAGE-SAND-SIGN-001 | 签到频率预期可能不准确，LLM 回答"一次"与 expectedPoints 矛盾 |

---

## 8. 关键发现和风险

### 8.1 编译覆盖率不足
- 两份大型 docx（合计 23MB）仅生成 4 篇 article，远低于预期。
- 大量手册章节（银行退款、杉德结算、得仕卡撤销、日志、证书更新等）未被独立 article 化。
- **根因**：LLM topic 切分过粗，将 FAQ 等大量内容合并为单篇。

### 8.2 事实错误
- SWIP-USAGE-SAND-SIGN-001：LLM 断言"只需签到一次"，与文档原文"每天开店前签到一次"矛盾。
- 需排查是 article 生成阶段的 Writer 幻觉，还是查询阶段的 LLM 综合错误。

### 8.3 FALLBACK 模式残留
- 5 个 case 仍触发 FALLBACK，说明检索未命中足够证据。
- 需要提升 article 覆盖率后才能改善。

### 8.4 答案截断
- 多个 LLM 答案在未完成时截断，可能由于 `maxTokens` 或答案长度限制。
- 影响完整性和可评估性。

---

## 9. 合规声明

| 项 | 状态 |
|---|---|
| 是否修改源代码（src/main/java/**） | **否** |
| 是否修改测试代码（src/test/java/**） | **否** |
| 是否修改资源文件（src/main/resources/**） | **否** |
| 是否修改题集（docs/test/swip-query-eval-candidates.json） | **否** |
| 是否修改 regression suite（docs/test/query-regression-suite.json） | **否** |
| 是否修改 scripts/** | **否** |
| 是否污染当前主 baseline（ai-rag-knowledge） | **否** |
| 是否接入 run-query-regression.sh | **否** |
| 是否提交代码 | **否** |
| 是否 push | **否** |

---

## 10. 下一步推荐

**最小动作：提升 SWIP article 编译覆盖率。**

当前 4 篇 article 远不足以覆盖 22 个 case 所需的事实维度。建议：
1. 调整 LLM compile 的 topic 切分配置（减小合并粒度），使每个手册章节独立生成 article。
2. 重新编译后再次运行 SWIP eval，对比 pass rate。
3. 在 article 覆盖率提升后再评估哪些 case 可以纳入自动化验收。

> 注意：以上调整需要修改编译配置（非本次范围），不在当前禁止修改清单内，但应先与团队确认。
