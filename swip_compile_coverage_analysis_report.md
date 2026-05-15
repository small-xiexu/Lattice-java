# SWIP 编译覆盖率分析报告

- **报告时间**：2026-05-14
- **数据来源**：`ai-rag-swip-eval` 数据库（完全隔离，不影响主 baseline）

---

## 1. Redline 检查

| 指标 | 值 |
|---|---|
| BLOCKER | **0** |
| 总命中 | 2047 |

结论：BLOCKER=0，可以继续分析。

---

## 2. 题集 Case 数核对

| 项目 | 数量 |
|---|---|
| `docs/test/swip-query-eval-candidates.json` 中 case 数 | **23** |
| 上一轮 eval 执行 case 数 | **23**（全部执行） |
| 上一轮报告声称 case 数 | 22（**报告误写**） |

未执行 case：**0**。23 个 case 均有对应查询结果文件。

---

## 3. 编译流程完整记录

### 3.1 Pipeline 步骤（17 步，全部 succeeded）

| 步骤 | 名称 | 耗时 | 关键信息 |
|---|---|---|---|
| 1 | initialize_job | 0.05s | conceptCount=0 |
| 2 | ingest_sources | 0.93s | conceptCount=2（2 份 docx 入库） |
| 3 | persist_source_files | 0.06s | 入库 2 条 source_files |
| 4 | persist_source_file_chunks | 0.09s | 切分 source chunk |
| 5 | extract_ast_graph | 0.01s | AST 结构图 |
| 6 | group_sources | 0.01s | 分组 |
| 7 | split_batches | 0.01s | 拆批 |
| **8** | **analyze_batches** | **0.02s** | **conceptCount: 2→4**（topic planner） |
| 9 | merge_concepts | 0.01s | 概念合并 |
| **10** | **compile_new_articles** | **2m04s** | WriterAgent / deepseek-v4-flash |
| 11 | review_articles | 0.05s | rule-based review |
| 12 | persist_articles | 0.08s | 4 篇 article 持久化 |
| 13 | rebuild_article_chunks | 0.00s | 建 chunk |
| 14 | refresh_vector_index | 4.72s | 向量索引刷新 |
| 15 | generate_synthesis_artifacts | 29.72s | LLM 合成产物 |
| 16 | capture_repo_snapshot | 0.04s | 快照 |
| 17 | finalize_job | 0.00s | 完成 |

### 3.2 编译产物统计

| 表 | 数量 |
|---|---|
| source_files | 2 |
| source_file_chunks | 5（使用手册 2 + 安装手册 3） |
| articles | 4 |
| article_chunks | 7（1→2, 2→1, 3→2, 4→2，另有 source chunks） |
| fact_cards | 14（全部为结构化抽取，article_ids={}，未关联文章） |
| synthesis_artifacts | 若干 |
| compile_jobs | 1（SUCCEEDED） |
| 编译失败 job | 0 |

---

## 4. 文本抽取深度分析 — **根因定位**

### 4.1 抽取数据量

| 文档 | 文件大小 | 段落数 | 提取文本字符数 | 平均每段字符 |
|---|---|---|---|---|
| 使用手册-20250702.docx | 10.3MB | 183 | **4,510** | ~25 |
| 安装手册-202509.docx | 12.9MB | 227 | **9,153** | ~40 |
| **合计** | 23.2MB | 410 | **13,663** | ~33 |

### 4.2 根因 #1：poi_xwpf 文本抽取量极低

两份 docx 共 23.2MB，但 `poi_xwpf` 仅提取出 **13,663 个字符**的纯文本。

> 使用手册拥有详细的操作流程（消费、退款、结算、重印、杉德签到/结算、得仕卡撤销等），仅靠 4510 字符不可能完整覆盖。对比文档中可读的中文正文量，预估实际正文量应远超此数。

抽取文本内容特征：
- 包含了**目录（TOC）**的标题和页码
- 包含了部分正文段落
- 但**正文覆盖率极低**，大量操作步骤（如银行退款输入参考号/日期、得仕卡撤销条件、杉德结算按卡种打印等）未出现在抽取文本中
- `parseMode: "office_extract"`, `parseProvider: "poi_xwpf"`, `ocrApplied: false`

### 4.3 根因 #2：docx 内嵌图片/表格未参与文本抽取

两份文档包含大量截图和表格（安装手册检测到 2 个表格），这些内容中的文字：
- **未经过 OCR** 处理（`ocrApplied: false`）
- **表格数据** 未被结构化抽取
- 大量操作流程是截图+标注形式，纯 poi_xwpf 无法获取

### 4.4 根因 #3：Topic Planner 输入不足 → 切分过粗

`analyze_batches` 步骤将 2 份 source file **仅拆分为 4 个 concept**：

| concept | 对应 article |
|---|---|
| 1 | Swip智能键盘系统使用手册 20250702 |
| 2 | 系统架构 5（SWIP智能键盘系统架构） |
| 3 | SWIP智能键盘系统安装手册 FAQ 33 |
| 4 | HTTPS证书安装（门店内网） |

Topic splitter 配置：
- `long-document-min-chars: 12000` — 超过才按长文档切分
- `medium-document-min-chars: 6000` — 超过才按中等文档切分
- 使用手册 4510 字符 → **被归类为短文档**，整体作为一个 concept
- 安装手册 9153 字符 → **被归类为中等文档**，仅切分为 3 个 concept

### 4.5 4 篇 Article 覆盖范围

| Article | 覆盖内容 | 字符数 | 覆盖的文档章节 |
|---|---|---|---|
| 1. 使用手册 20250702 | 整个使用手册 | 5313 | 系统目标 + SVC卡 + 银行卡 + 杉德卡 + 银行积分 + FAQ（**全部合并为 1 篇**） |
| 2. 系统架构 5 | 安装手册第 2 章 | 3561 | 当前门店架构图 + 目标门店架构图 + SWIP技术架构 |
| 3. FAQ 33 | 安装手册第 4 章 FAQ | 5200 | POS初始化文件 + 提示代码 + 终止交易 + APP升级影响 + 借调 + 打印 + 待机 + 证书更新 + 日志（**10+ 个 FAQ 合并为 1 篇**） |
| 4. HTTPS证书安装 | 安装手册证书相关 | 5432 | 证书申请/补下载 + 混杂了 log 目录 + IP 后缀等 |

**问题**：每个 article 的 `referential_keywords` 列表非常丰富（30-50 个关键词），但 article 正文无法深入覆盖这些关键词对应的细节。现象是"关键词索引够了但回答内容不足"。

---

## 5. 失败 Case 与未覆盖章节映射

### 5.1 使用手册 case（12 个）

| Case ID | 类别 | 对应章节 | 章节是否在抽取文本中 | 失败原因 |
|---|---|---|---|---|
| SWIP-USAGE-GOAL-001 | 系统目标 | §1 系统目标 | 仅标题 | 答案截断，缺"监控"要点 |
| SWIP-USAGE-SVC-READ-001 | SVC卡 | §2.1 SVC卡 | 部分 | PASS |
| SWIP-USAGE-BANK-REFUND-001 | 银行卡退款 | §2.2.2 退款 | **不足** | NO_RELEVANT_KNOWLEDGE |
| SWIP-USAGE-BANK-SETTLEMENT-001 | 银行卡结算 | §2.2.3 结算 | **不足** | 回答偏题 |
| SWIP-USAGE-REPRINT-001 | 银行卡重印 | §2.2.5 重印 | 部分 | FALLBACK 模式 |
| SWIP-USAGE-SAND-SIGN-001 | 杉德签到 | §2.3.1 杉德签到 | 部分 | **事实错误**（LLM 幻觉） |
| SWIP-USAGE-SAND-SETTLEMENT-001 | 杉德结算 | §2.3.2 杉德手工结算 | **不足** | 缺"按卡种"/"无交易不打印" |
| SWIP-USAGE-DESHI-VOID-001 | 得仕卡撤销 | §2.3.4? | **不足** | FALLBACK 模式 |
| SWIP-USAGE-EBUY-SETTLEMENT-001 | 银行积分 | §2.5 银行积分 | 部分 | 缺"返回处理中再次执行" |
| SWIP-FAQ-NO-RESPONSE-001 | FAQ 无响应 | FAQ > 系统无响应 | **不足** | 缺 HTTPS 服务/区域 IT |
| SWIP-FAQ-TERMINATE-STUCK-001 | FAQ 卡死 | FAQ > 终止交易 | 足够 | PASS |
| SWIP-FAQ-PRINT-PAPER-001 | FAQ 打印纸 | FAQ > 打印纸规格 | **不足** | 缺尺寸和供应商 |

### 5.2 安装手册 case（9 个）

| Case ID | 类别 | 对应章节 | 章节是否在抽取文本中 | 失败原因 |
|---|---|---|---|---|
| SWIP-INSTALL-CERT-NAMING-001 | 证书命名 | §3.2 智能键盘初始化 | 部分 | 答案截断 |
| SWIP-INSTALL-APP-LIST-001 | APP 列表 | §3.2.2 安装 APP | **不足** | 正确承认证据不足 |
| SWIP-INSTALL-IP-SUFFIX-001 | IP 后缀 | §3.2.1 入门店内网 | 部分 | FALLBACK 模式 |
| SWIP-INSTALL-SWIP-POS-JSON-001 | swip-pos.json | §3.3 POS初始化 | 部分 | FALLBACK 模式 |
| SWIP-INSTALL-KEY-FILE-001 | 交易密钥 | §3.3 POS初始化 | 部分 | PASS（仅缺"一机一密"） |
| SWIP-INSTALL-DLL-ORDER-001 | DLL 初始化 | §3.3 POS初始化 | **不足** | 缺 POS DLL/目录路径 |
| SWIP-INSTALL-APP-UPGRADE-IMPACT-001 | APP 升级影响 | §4.4 APP升级影响 | **不足** | 答案太短，未按 APP 分组 |
| SWIP-INSTALL-LOGS-001 | 日志目录 | §4.9-4.10 日志 | **严重不足** | 完全未覆盖 |
| SWIP-INSTALL-CERT-UPDATE-001 | 证书更新 | §4.8 证书更新 | **严重不足** | 答案不相关 |

### 5.3 无答案保护 case（2 个）

| Case ID | 结果 |
|---|---|
| SWIP-NEG-UNANSWERABLE-001 | PASS — 正确拒答 |
| SWIP-NEG-UNANSWERABLE-002 | FAIL — FALLBACK 模式，未正确拒答 |

---

## 6. 未覆盖的高价值章节清单

以下章节在文档中存在，但在抽取文本中不足或完全缺失，导致对应 case 失败：

### 使用手册

| 章节 | 对应 case | 优先级 | 缺失程度 |
|---|---|---|---|
| §2.2.2 银行卡退款（输入参考号+日期） | BANK-REFUND-001 | P0 | **严重** |
| §2.2.3 银行卡结算（时机+结果） | BANK-SETTLEMENT-001 | P1 | **严重** |
| §2.2.5 重印（两种方式+流水号） | REPRINT-001 | P1 | 中等 |
| §2.3.1 杉德签到（必须+每天） | SAND-SIGN-001 | P0 | 中等 |
| §2.3.2 杉德手工结算（时机+按卡种） | SAND-SETTLEMENT-001 | P1 | **严重** |
| §2.3.4 得仕卡当日撤销（仅得仕卡+条件） | DESHI-VOID-001 | P0 | **严重** |
| §2.5 银行积分（eBuy + 处理中重试） | EBUY-SETTLEMENT-001 | P1 | 中等 |
| FAQ 系统无响应（SNIFF+HTTPS+IT） | NO-RESPONSE-001 | P0 | **严重** |
| FAQ 打印纸规格（40mm×58mm+供应商） | PRINT-PAPER-001 | P2 | **严重** |

### 安装手册

| 章节 | 对应 case | 优先级 | 缺失程度 |
|---|---|---|---|
| §3.2.1 入门店内网（IP 后缀 149/150） | IP-SUFFIX-001 | P0 | 中等 |
| §3.2.2 安装 APP（9 个 APP 列表） | APP-LIST-001 | P0 | **严重** |
| §3.3.1 交易密钥（swip-keys.p13） | KEY-FILE-001 | P1 | 轻微 |
| §3.3 POS DLL 初始化顺序 | DLL-ORDER-001 | P1 | **严重** |
| §4.4 APP 升级影响（按 APP 分组） | APP-UPGRADE-IMPACT-001 | P1 | **严重** |
| §4.8 证书自动更新（51/31天+11点） | CERT-UPDATE-001 | P1 | **严重** |
| §4.9-4.10 日志访问（9999+6666+目录） | LOGS-001 | P1 | **严重** |

---

## 7. 根因分类总结

| 根因 | 严重度 | 说明 | 影响 case 数 |
|---|---|---|---|
| **docx 文本抽取不足** | **CRITICAL** | poi_xwpf 仅提取 13.6K 字符，大量正文缺失 | ~15 |
| **OCR 未启用** | HIGH | 截图中文字未参与文本抽取 | 3-5 |
| **Topic Planner 输入不足** | HIGH | 文本太少导致"短文档"判定，不切分 | 全部 |
| **Topic Splitter 合并过粗** | MEDIUM | 即使文本够，4 个 concept 也太少（FAQ 10+ 项合并为 1 篇） | 8-10 |
| **Article Writer 质量** | MEDIUM | LLM 在部分 case 上产生幻觉（杉德签到） | 1-2 |
| **答案截断** | LOW | 部分 LLM 答案被截断 | 2-3 |
| **Eval Runner 漏 case** | **无** | 23 个 case 全部执行，上一轮报告误写 22 个 | 0 |

---

## 8. 合规声明

| 项 | 状态 |
|---|---|
| 本轮是否修改代码 | **否** |
| 本轮是否修改测试 | **否** |
| 本轮是否修改配置 | **否** |
| 本轮是否修改题集 | **否** |
| 本轮是否污染主 baseline（ai-rag-knowledge） | **否** |
| 本轮是否提交代码 | **否** |

---

## 9. 下一轮最小修复建议

**唯一推荐：对 SWIP docx 启用 OCR 解析路径或更换 docx 解析器。**

当前 `poi_xwpf` 从 23MB docx 中仅提取出 13.6K 字符，这是全部问题的根因。具体选项：

1. **方案 A（推荐）**：将 source 的解析模式从 `office_extract` 改为 OCR 路径（`parseMode: "ocr"`），利用项目已有的 OCR 能力重新提取文本。需确认 OCR pipeline 对 docx 的支持程度。

2. **方案 B**：调查 poi_xwpf 为何只提取到少量文本，检查是否有分页/文本框/表格跳过逻辑，调整或修复。

3. **方案 C**：先用外部工具（如 pandoc）将 docx 转为 markdown，再作为 markdown 源导入 Lattice。

无论哪种方案，目标是将文本抽取量从 ~14K 字符提升至 **~100K+ 字符**，使 Topic Planner 能生成 15-25 个 topic，覆盖所有操作章节。

> 以上调整均在编译器配置或解析器层面，不涉及 query 主链、baseline suite、题集内容或生产代码逻辑。
