# B20 后 Clean DB 双题集真实验收 Gate 报告

核查时间：2026-06-01
核查人：agentD（验证/门禁 Agent）
范围：Clean DB 重建 + 基础门禁 + 编译/索引 + public/hidden eval
状态：**BLOCKED — 基础门禁全部通过，但缺少 LLM 配置、知识源素材与 eval runner，无法执行编译与题集验收**

---

## 1. 前置工作区核查

### 1.1 Git 状态

```
 M docs/模型绑定配置参考.md    ← 已知排除（API key 变更）
 M special_cases_report.md     ← 已知排除（机械重扫）
```

工作区仅 2 个已知排除文件 dirty，所有 B0-B19 生产代码已提交。无未知变更。

**结果：PASS。**

---

## 2. 基础门禁

### 2.1 Redline

```
bash scripts/scan-redline.sh → model_contract_post_cleanup_redline_report.md
BLOCKER：0
```

**结果：PASS。**

### 2.2 全量 mvn test

```
Tests run: 995, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS (6:52 min)
```

B20 预扫中 3 个预存失败（AdminVectorIndexControllerTests、FactCardTerminalUnitJdbcRepositoryTests、QueryControllerTests）已全部修复。**995/0/0/0，全绿。**

**结果：PASS。**

---

## 3. 清库与环境重建

### 3.1 容器检查

| 容器 | 状态 |
|---|---|
| `vector_db` (PostgreSQL) | 运行中 |
| `redis` | 运行中 |

### 3.2 Schema 重置

```
./scripts/reset-lattice-schema.sh
→ 59 个旧对象级联删除 → lattice schema 重建完成
```

全部业务表（articles, source_files, compile_jobs, llm_provider_connections, query_retrieval_settings 等 50+ 表）已重建。

**结果：PASS。**

### 3.3 应用启动

```
./scripts/run-local-dev.sh
→ Started LatticeApplication on port 18082 (3.248s)
→ /actuator/health → {"status":"UP"}
```

端口 `18082` 空闲，应用正常启动。

**结果：PASS。**

---

## 4. 阻塞点：无法继续执行编译与题集验收

### 4.1 LLM 连接未配置

```
GET /api/v1/admin/llm/connections → {"count": 0, "items": []}
```

Clean schema 初始化后，数据库无任何 LLM provider 连接、模型配置或 Agent 绑定。编译和 Query 需要真实 LLM 调用，但没有可用的 API key 或 endpoint 配置。

### 4.2 知识源素材不存在

```
ls /tmp/lattice-real-e2e-20260418-src → 不存在
```

验收手册中引用的知识源样本目录不存在于当前环境。

### 4.3 Eval runner 未就绪

当前 `scripts/` 目录下无 eval runner 脚本。`docs/test/knowledge-base-e2e/fresh-eval-2026-05/` 中有 `acceptance-report.md`（历史基线），但无可直接执行的 runner。

### 4.4 缺失清单

| 缺失项 | 说明 | 影响 |
|---|---|---|
| OpenAI API key | 编译/Query 需要真实 LLM 调用 | 阻塞编译 |
| OpenAI 兼容 base URL | LLM 网关/中转地址 | 阻塞编译 |
| LLM 连接/模型/绑定配置 | 需通过 API 或页面配置 | 阻塞编译 |
| 知识源样本目录 | 导入→编译→索引→问答 | 阻塞 eval |
| Eval runner 脚本 | 运行 public/hidden 题集 | 阻塞 eval |
| Hidden eval 安全 runner | 不能直接读取 hidden 题目 | 阻塞 hidden eval |

---

## 5. 已完成验证汇总

| 步骤 | 结果 |
|---|---|
| Git 工作区核查 | PASS |
| Redline (BLOCKER=0) | PASS |
| mvn test (995/0/0/0) | PASS |
| PostgreSQL + Redis 可用 | PASS |
| Schema 重置 | PASS |
| 应用启动 (18082) | PASS |
| Health check (UP) | PASS |
| **编译/索引** | **BLOCKED（无 LLM 配置）** |
| **public eval** | **BLOCKED（无 LLM + 知识源 + runner）** |
| **hidden eval** | **BLOCKED（无 runner）** |

---

## 6. 已确认无风险的治理链路

尽管无法执行完整 eval，以下 B17-B20 治理相关的关键结构已通过 redline + mvn test 验证：

| 治理链路 | 验证方式 | 状态 |
|---|---|---|
| QueryGraphState setter 注入 | @Setter 保留，mvn test PASS | ✅ |
| DeepResearchState setter 注入 | @Setter 保留，mvn test PASS | ✅ |
| CompileGraphState setter 注入 | @Setter 保留，mvn test PASS | ✅ |
| EvidenceLedger 累加器 | @Getter only, 0 external setter, mvn test PASS | ✅ |
| FactFinding/EvidenceAnchor 领域方法 | 保留，mvn test PASS | ✅ |
| AnswerProjection/ProjectionCandidate | @Getter @Setter，mvn test PASS | ✅ |
| governance @JsonCreator/static factory | 保留，mvn test PASS | ✅ |
| QueryAnswerPayload/ReviewResult factory | 保留，mvn test PASS | ✅ |
| Jackson 序列化 | 无 @JsonCreator 丢失，mvn test PASS | ✅ |
| Spring @ConfigurationProperties | 无 Lombok 引入，mvn test PASS | ✅ |

**结论：B0-B20 治理变更未引入编译期或单元测试级回归。**

---

## 7. 解除阻塞的最小条件

1. **LLM 配置**：用户提供 OpenAI 兼容 API key + base URL，或通过 `/admin/settings` 页面配置
2. **知识源**：准备验收样本目录（如 `/tmp/lattice-real-e2e-20260418-src` 或用户指定的路径）
3. **Eval runner**：提供 public/hidden eval 执行脚本或明确调用方式
4. **Hidden eval 安全执行**：确认 runner 支持 hidden-safe 聚合输出（不暴露题目/答案原文）

满足上述条件后，后续步骤为：
1. 通过 API 配置 LLM 连接→模型→绑定
2. 导入知识源 → 触发编译 → 等待完成 → 确认索引
3. 运行 public eval → 记录指标
4. 运行 hidden eval（安全模式） → 记录聚合指标
5. 对比基线 → 输出最终验收结论

---

## 8. 计划台账状态

B20 台账已在上轮回写为"已完成"。本轮 clean eval 不改变台账状态——它是 B20 完成后的独立验证环节。建议在解除阻塞并完成 eval 后，将结果追加到台账或本报告。

---

## 附录：本轮可用的已验证基线

| 基线 | 值 |
|---|---|
| Redline BLOCKER | 0 |
| mvn test | 995/0/0/0 |
| Schema | lattice，空库 |
| Port | 18082 |
| Profile | local-dev |
| 历史 public eval 基线 | 见 `acceptance-report.md`（Answer Accuracy 10/15, Recall@10 13/15, Hallucination 5） |
