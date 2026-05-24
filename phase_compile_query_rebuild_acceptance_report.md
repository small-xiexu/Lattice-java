# Phase: Compile + Query 阶段性重建验收报告

- 生成时间：2026-05-22
- 执行 Agent：agentD（只读验证）
- 目标：Schema 重建 + 模型配置恢复 + Compile + Query 整体验证
- 代码修改：否（严格遵守，本轮未修改任何代码/测试/文档）

---

## 1. Redline 扫描结果

| 指标 | 值 |
|------|-----|
| BLOCKER | **0** |
| REVIEW | 1912 |
| ALLOWLIST | 246 |
| 总命中 | 2158 |

**结论：BLOCKER=0，无阻断项。** 所有 REVIEW/ALLOWLIST 命中均为已有的工程标识匹配（`.equals`/`.matches`/`.contains` 用于状态码、配置常量、Markdown 解析等），不属于 Query 主链红线违规。

---

## 2. 模型配置恢复结果

### 2.1 配置一致性验证

| 配置项 | 参考文档要求 | 实际恢复 | 一致 |
|--------|------------|---------|------|
| Chat 连接 | `local_openai`, openai_compatible, localhost:8888 | id=1, connectionCode=local_openai, openai_compatible, localhost:8888 | ✅ |
| Embedding 连接 | `zhipu_embedding`, openai_compatible, bigmodel.cn | id=2, connectionCode=zhipu_embedding, openai_compatible, bigmodel.cn | ✅ |
| Chat 模型 | `gpt-5.5`, chat, temperature=0.1, maxTokens=4096, timeout=120s | id=1, modelCode=gpt-5.5, temperature=0.1, maxTokens=4096, timeoutSeconds=120 | ✅ |
| Embedding 模型 | `embedding-3`, embedding, expectedDimensions=2000, supportsDimensionOverride=true | id=2, modelCode=embedding-3, expectedDimensions=2000, supportsDimensionOverride=true | ✅ |
| 绑定总数 | 10 条 | 10 条 | ✅ |
| compile 绑定 | writer, reviewer, fixer | writer (id=1), reviewer (id=2), fixer (id=3) | ✅ |
| query 绑定 | answer, reviewer, rewrite | answer (id=4), reviewer (id=5), rewrite (id=6) | ✅ |
| deep_research 绑定 | planner, researcher, synthesizer, reviewer | planner (id=7), researcher (id=8), synthesizer (id=9), reviewer (id=10) | ✅ |
| 向量配置 | vectorEnabled=true, embeddingModelProfileId 非 null | vectorEnabled=true, embeddingModelProfileId=2 | ✅ |
| 配置来源 | database | configSource=database | ✅ |

### 2.2 验证命令输出

```
连接数: 2 → 期望 2 ✅
模型数: 2 → 期望 2 ✅
绑定数: 10 → 期望 10 ✅

Scene 角色分布:
  compile:       ["fixer","reviewer","writer"]
  query:         ["answer","reviewer","rewrite"]
  deep_research: ["planner","researcher","reviewer","synthesizer"]

向量配置: vectorEnabled=true, embeddingModelProfileId=2 ✅
```

**结论：配置恢复与 `docs/模型绑定配置参考.md` 完全一致。**

---

## 3. Compile 验证结果

### 3.1 测试数据

| 项目 | 文件 | 行数 | 内容类型 |
|------|------|------|---------|
| 测试文档 1 | test-iot-bridge.md | 38 行 | 硬件配置表 + MQTT/CoAP 配置参数 + 安全模式 + 故障码 |
| 测试文档 2 | test-app-config.md | 32 行 | 配置类型表 + 灰度发布 + 版本管理 + 权限模型 |

### 3.2 规则审查模式（RULE_BASED）

| 阶段 | 结果 | 详情 |
|------|------|------|
| Writer | ✅ | 调用 Writer 生成草稿：test-iot-bridge |
| Reviewer | ✅（规则审查） | reviewRoute=rule-based，acceptedCount=1 |
| Fixer | ✅（未触发） | 无 fixable issue，未触发自动修复 |
| Human Review | ✅（无需） | needsHumanReviewCount=0 |
| 入库 | ✅ | persistedCount=1 |

### 3.3 LLM 审查模式（LLM）

| 阶段 | 结果 | 详情 |
|------|------|------|
| Writer | ✅ | 调用 Writer 生成草稿：test-app-config |
| Reviewer | ✅（LLM 审查） | reviewRoute=compile.reviewer.gpt-5-5，acceptedCount=1 |
| Fixer | ✅（未触发） | 无 fixable issue，未触发自动修复 |
| Human Review | ✅（无需） | needsHumanReviewCount=0 |
| 入库 | ✅ | persistedCount=1 |

### 3.4 入库一致性验证

```
数据库验证:
 id |      title      | review_status |           article_key
----+-----------------+---------------+---------------------------------
  1 | Test Iot Bridge | passed        | default-source--test-iot-bridge
  2 | Test App Config | passed        | default-source--test-app-config

chunks: 2 条
vectors: 2 条
```

### 3.5 Fixer Payload Slimming 影响评估

由于两次 compile 均未触发 Fixer（文档内容干净，Writer 产出无 fixable issue），**本轮无法直接验证 Fixer payload slimming 是否造成回归**。Fixer 路径为：
- 规则审查模式：未触发修复
- LLM 审查模式：未触发修复

Fixer slimming 代码改动（ReviewFixService.java）涉及 Writer payload 预算限制和消息裁剪。需要**构造包含可修复缺陷的文档**才能验证完整 Fixer 链路。

---

## 4. Query 验证结果

### 4.1 测试环境

- Query LLM 绑定: `query/answer -> gpt-5.5 (id=1)`
- LLM 网关 (localhost:8888) 直连可用：`gpt-5.5` 返回正常
- 向量配置: enabled, embedding-3, 2000 维
- 入库资料: 2 篇，2 chunks，2 vectors

### 4.2 场景覆盖

| 场景 | 问题 | Outcome | Generation Mode | Model Status | 关键观察 |
|------|------|---------|-----------------|-------------|---------|
| 精确查值 | "IoT桥接器的MQTT默认端口是多少？" | PARTIAL_ANSWER | FALLBACK | DEGRADED | 证据已检索（articleHits=1），回答列出原始证据但未 LLM 合成 |
| 解释类 | "应用配置中心支持哪些配置类型？" | PARTIAL_ANSWER | FALLBACK | DEGRADED | 证据检索不足（仅 1 条 chunk），LLM 未合成 |
| 无答案保护 | "什么是量子计算中的量子纠缠？" | NO_RELEVANT_KNOWLEDGE | FALLBACK | DEGRADED | ✅ 正确拒答："当前未找到与该问题直接相关的知识" |
| 多点答案 | "IoT桥接器MQTT和CoAP的默认端口分别是多少？需要列出所有安全模式" | PARTIAL_ANSWER | FALLBACK | DEGRADED | 证据完整（MQTT 1883 + CoAP 5683 + 3 种安全模式均已检索），但仍走 FALLBACK |

### 4.3 核心发现

**所有 query 回答均走入 FALLBACK 模式，fallbackReason 统一为 `CITATION_QUALITY_INSUFFICIENT`。**

具体表现：
1. **证据检索正常**：article vector 检索能找到正确的文档和 chunk
2. **LLM 网关正常**：直接调用 `gpt-5.5` 返回正确响应
3. **回答合成未触发**：LLM answer generation 未被调用，直接输出结构化证据列表代替合成回答
4. **fallbackReason = CITATION_QUALITY_INSUFFICIENT**：即使证据已完整覆盖所有信息点，评估仍判为 insufficient
5. **无答案保护正常**：不相关问题正确返回 `NO_RELEVANT_KNOWLEDGE`

### 4.4 多点答案完整度评估

在场景 4 中：
- MQTT 默认端口 1883：**已被检索** ✅
- CoAP 默认端口 5683：**已被检索** ✅
- 三种安全模式（开放/PSK/证书）：**全部被检索** ✅
- **但回答仍是 FALLBACK 结构化证据列表，未做 LLM 合成**

**多点答案完整度未改善**：证据检索覆盖了所有信息点，但回答格式未从 FALLBACK 升级到 SYNTHESIZED。这与当前未提交改动中 `AnswerParagraphPostProcessor` 和 `AnswerPromptBuilder` 的"多点展开完整度"目标直接相关——该改动尚未完成，FALLBACK 模式下的段落展开逻辑可能尚未生效或存在阻断。

---

## 5. 当前两条未提交改动建议

### 5.1 改动 1: compile fixer payload slimming
- **文件**: `ReviewFixService.java`, `ReviewFixServiceTests.java`
- **状态**: ⚠️ **本轮未触发 Fixer，未验证到实际效果**
- **风险**: 需要构造可修复缺陷文档来验证 slimming 不破坏 Fixer 核心逻辑
- **建议**: **继续保留**，但需补充一次含 fixable issue 的 compile 验证后再提交

### 5.2 改动 2: query partial answer 多点展开完整度
- **文件**: `AnswerPromptBuilder.java`, `AnswerParagraphPostProcessor.java`, `AnswerGenerationServiceTests.java`
- **状态**: ⚠️ **本轮验证发现 PARTIAL_ANSWER/FALLBACK 问题仍存在**
- **风险**: 当前改动可能尚未完成——所有回答仍走 FALLBACK 且 citation quality 评估为 insufficient
- **建议**: **继续保留并推进**，但需确认改动目标（是修复 generationMode=FALLBACK → SYNTHESIZED 的升级逻辑，还是改进 FALLBACK 模式内的段落展开）。当前状态是改动未实质性改善端到端回答质量

---

## 6. 最显著剩余问题

### 6.1 Query 回答始终走 FALLBACK 模式

**严重程度：高**

所有 query 的 generationMode 都是 FALLBACK，fallbackReason 为 CITATION_QUALITY_INSUFFICIENT。即使 LLM 网关正常、证据检索正常，LLM answer synthesis 也未触发。

**可能根因方向**（仅分析，未修改代码）：
1. Answer quality review / citation quality 评估阈值过于严格
2. `CITATION_QUALITY_INSUFFICIENT` 判定逻辑可能误判可用证据
3. Fallback decision 在 answer generation pipeline 中过早触发

### 6.2 Query 解释类场景证据检索覆盖不足

场景 2（"应用配置中心支持哪些配置类型？"）仅检索到 1 条 chunk，但原文档包含完整的配置类型表格（应用/公共/密钥配置 + 作用域/热加载/加密支持）。chunk 切分或向量检索可能未将相邻结构化表格行合并到同一 chunk。

---

## 7. 本轮是否修改代码

**否。** 本轮严格遵守只读约束：
- 未修改 `src/main/java/**`
- 未修改 `src/test/java/**`
- 未修改 `docs/模型绑定配置参考.md`
- 未修改 AGENTS.md、CLAUDE.md、redline scripts
- 未修改配置文件
- 未提交任何代码

所有验证操作限于：
- 执行 `scripts/reset-lattice-schema.sh` 重建 schema
- 通过 `scripts/run-local-dev.sh` 启动应用
- 通过 Admin API 恢复模型配置（curl POST/PUT）
- 通过 `curl` 上传测试文档触发 compile
- 通过 `curl` 执行 query 验证
- 只读查询数据库（psql）/API（curl）

---

## 8. 总结

| 验收维度 | 状态 | 备注 |
|---------|------|------|
| Redline BLOCKER | ✅ PASS | BLOCKER=0 |
| 模型配置一致性 | ✅ PASS | 与参考文档 100% 一致 |
| Schema 重建 | ✅ PASS | 57 张表，`reset-lattice-schema.sh` |
| 应用启动 | ✅ PASS | health=UP, DB/Redis/LLM 绑定正常 |
| Compile: Writer | ✅ PASS | 两篇文档均成功生成 |
| Compile: Reviewer (规则) | ✅ PASS | RULE_BASED accepted |
| Compile: Reviewer (LLM) | ✅ PASS | LLM review via gpt-5.5 |
| Compile: Fixer | ⚠️ 未触发 | 无 fixable issue，需构造缺陷文档验证 |
| Compile: Human Review | ✅ PASS | 无需人工确认（LLM 审查自动通过） |
| Compile: 入库 | ✅ PASS | 2 篇入库，chunks + vectors 一致 |
| Query: 精确查值 | ⚠️ PARTIAL_ANSWER | 证据已检索但始终 FALLBACK |
| Query: 解释类 | ⚠️ PARTIAL_ANSWER | 检索不足 + FALLBACK |
| Query: 无答案保护 | ✅ PASS | NO_RELEVANT_KNOWLEDGE |
| Query: 多点答案完整度 | ❌ 未改善 | 证据完整但仍在 FALLBACK，未做 LLM 合成 |
| Fixer slimming 回归验证 | ⚠️ 未验证 | Fixer 未触发 |
| Partial answer 改善验证 | ❌ 未改善 | FALLBACK 模式未升级 |
