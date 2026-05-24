# E2E 干净库全链路 Query 验收套件 — 创建报告

- 执行 Agent：agentC
- 创建时间：2026-05-23
- 代码修改：否
- 数据库操作：否

---

## 1. 新建文件

| 文件 | 路径 | 说明 |
|------|------|------|
| E2E 回归套件 | `docs/test/e2e-clean-rebuild-suite.json` | 12 个 E2E Query 验收 case，格式与 `query-regression-suite.json` 兼容，可被 `scripts/run-query-regression.mjs` 直接执行 |

## 2. Suite 基本信息

- **suiteId**: `e2e-clean-rebuild-query-regression`
- **version**: `2026-05-23`
- **cases**: 12（E2E-001 ~ E2E-012）

## 3. Case 分布

| 类型 | 数量 | Case ID |
|------|------|---------|
| ANSWER | 10 | E2E-001 ~ E2E-010 |
| ABSTAIN | 2 | E2E-011, E2E-012 |

### 3.1 ANSWER case 覆盖矩阵

| caseId | category | 知识来源 | 覆盖维度 |
|--------|----------|----------|----------|
| E2E-001 | 长文档列举 | D1 `quality-progress-and-lessons.md` | Answer Accuracy, Recall@5, Citation Accuracy |
| E2E-002 | 架构边界 | D2 `卡券三期-迁移方案.md` | Answer Accuracy, Recall@5, Citation Accuracy |
| E2E-003 | 配置查值 | D3 `项目启动配置清单.md` | Answer Accuracy, Recall@5, Citation Accuracy |
| E2E-004 | 配置查值 | D4 `模型绑定配置参考.md` | Answer Accuracy, Recall@5, Citation Accuracy |
| E2E-005 | 运行态说明 | D6 `文档识别与OCR运行态说明.md` | Answer Accuracy, Recall@5, Citation Accuracy |
| E2E-006 | Excel 行定位 | D5 `scenarios.xlsx` | Answer Accuracy, Recall@5, Citation Accuracy |
| E2E-007 | Excel 聚合统计 | D5 `scenarios.xlsx` | Answer Accuracy, Recall@10, Citation Accuracy |
| E2E-008 | 多跳综合推理 | D2 `卡券三期-迁移方案.md` | Answer Accuracy, Recall@10, Citation Accuracy |
| E2E-009 | 踩坑经验提取 | D1 `quality-progress-and-lessons.md` | Answer Accuracy, Recall@5, Citation Accuracy |
| E2E-010 | 配置错误诊断 | D4 `模型绑定配置参考.md` | Answer Accuracy, Recall@5, Citation Accuracy |

### 3.2 ABSTAIN case 覆盖

| caseId | category | 场景 | 覆盖维度 |
|--------|----------|------|----------|
| E2E-011 | 无命中拒答 | 虚构内部配置项 `xbk_nonexistent_config_key_20260522` | Abstain Accuracy, Hallucination |
| E2E-012 | 无命中拒答 | 知识库外通用知识（量子纠缠） | Abstain Accuracy, Hallucination |

## 4. 复用的知识源文件

全部 6 份知识源均来自项目 `docs/` 目录，受 Git 版本控制，无需新增业务文件：

| 编号 | 文件 | 大小 |
|------|------|------|
| D1 | `docs/quality-progress-and-lessons.md` | 35KB |
| D2 | `docs/卡券三期-迁移方案.md` | 143KB |
| D3 | `docs/项目启动配置清单.md` | 16KB |
| D4 | `docs/模型绑定配置参考.md` | 10KB |
| D5 | `docs/scenarios.xlsx` | 572KB |
| D6 | `docs/文档识别与OCR运行态说明.md` | 3KB |

## 5. JSON 校验结果

### 5.1 结构校验（全部通过）

| 校验项 | 结果 |
|--------|------|
| JSON 可解析 | ✅ |
| `cases.length === 12` | ✅ |
| 每个 case 都有 `id` | ✅ |
| 所有 `id` 唯一 | ✅ |
| `suiteId`, `version`, `description`, `coverageDimensions`, `defaults`, `gates` 齐全 | ✅ |
| 每个 case 的 `expect` 关键字段齐全（httpStatus, answerOutcomeAny, generationModeAny, modelExecutionStatusAny, minSourceCount, requiredAnswerTerms, requiredSourceTerms, answerability, expectedPoints, expectedEvidence, mustNotClaim, humanJudgement.passRule） | ✅ |
| `scripts/run-query-regression.mjs` 兼容性（requestBody 构建 + evaluateCase 字段） | ✅ |

### 5.2 校验命令

```bash
node -e "const s=JSON.parse(require('fs').readFileSync('docs/test/e2e-clean-rebuild-suite.json','utf8'));console.log('cases:',s.cases.length,'ids:',s.cases.map(c=>c.id).join(', '))"
```

## 6. defaults 和 gates 说明

完全复用 `docs/test/query-regression-suite.json` 的 `defaults` 和 `gates`，无差异：

```json
"defaults": {
  "timeoutMs": 120000,
  "minSourceCount": 1,
  "minCitationCoverage": 0.6
},
"gates": {
  "minCasePassRate": 0.8,
  "maxHttpFailureRate": 0.0,
  "maxTimeoutRate": 0.05,
  "maxFallbackRate": 0.4,
  "minLlmSuccessRate": 0.4,
  "minAverageCitationCoverage": 0.6
}
```

仅在 E2E-008（多跳综合推理）将 `minCitationCoverage` 放宽至 **0.5**，原因：多跳推理涉及跨章节综合，citation 覆盖天然低于单点查值。此项差异在设计报告中已有预期（E2E-008 标注为复杂多跳题）。

## 7. 关键设计决策记录

### 7.1 embedding 维度口径

以 `docs/模型绑定配置参考.md` 为准，`expectedDimensions = 2000`。E2E-010 的 `requiredAnswerTerms` 中包含 `"2000"`，用于自动验证答案是否提及正确的基线维度。

### 7.2 abstain case 的 forbiddenAnswerTerms

- **E2E-011**: `["取值为", "负责人是"]` — 这两个短语如果出现在答案中，说明模型在编造具体取值或负责人
- **E2E-012**: `["量子比特", "叠加态", "Bell", "EPR", "波函数坍缩"]` — 这些是量子计算领域的专业术语，出现在答案中说明模型在用内部知识解释量子纠缠，而非拒答。注意 `"量子纠缠"` 本身不在 forbidden 列表中，因为问题回显会触发误判

### 7.3 forceSimple 使用

除 E2E-006（结构化行定位）和 E2E-007（结构化聚合统计）外，所有 ANSWER case 均设置 `forceSimple: true`，确保走通用 Query Graph。结构化 case 则允许 `RULE_BASED` 或 `LLM` 两种 generationMode。

### 7.4 retrievalTargets 配置

所有 ANSWER case 均配置了 `retrievalTargets`，指向对应知识源文件在 `docs/` 下的路径。这使得 `run-query-regression.mjs` 可自动计算 Recall@5、Recall@10 和 MRR 指标。

## 8. 为什么本轮不运行真实 query regression

1. **无运行时环境**：当前工作目录仅为代码仓库，未启动 Spring Boot 应用、未配置模型网关、未编译知识库
2. **数据库不为空**：当前开发数据库已有历史数据，不符合“干净库全链路”的前置条件（需先执行 `scripts/reset-lattice-schema.sh` 清库）
3. **设计职责分离**：`full_rebuild_e2e_validation_asset_design_report.md` 明确分配 agentC 只负责创建 JSON 资产，实际执行为 agentD 的职责

## 9. 下一步：交给 agentD 执行干净库全链路验证

agentD 的推荐执行流程（详见设计报告第 4 节）：

```
Phase 0: 前置检查（Docker、JDK 21、端口、模型网关）
Phase 1: 清库重建（reset-lattice-schema.sh → 验证空库）
Phase 2: 应用启动 + 模型绑定配置（2 连接 + 2 模型 + 10 绑定 + 向量配置）
Phase 3: 资料导入 + Compile（6 份知识源 → 全量 compile → Review Queue 观察）
Phase 4: 入库检查（articles/chunks/sources 数量 + 向量索引状态）
Phase 5: Query Regression 执行
  - 方式 A（推荐）: 合并执行已有 10 case + 新增 12 E2E case
    QUERY_REGRESSION_SUITE=docs/test/e2e-clean-rebuild-suite.json \
    QUERY_REGRESSION_BASE_URL=http://127.0.0.1:18082 \
      node scripts/run-query-regression.mjs
  - 方式 B: 分别执行两个 suite 后合并 metrics
Phase 6: 失败归因（按 9 类归因编码分类所有失败 case）
```

关键提示给 agentD：
- 必须先执行 `reset-lattice-schema.sh` 清库，确保干净基线
- embedding 模型必须设 `expectedDimensions=2000` 且 `supportsDimensionOverride=true`
- compile 完成后检查 Review Queue，记录 `needsHumanReviewCount`
- E2E case 和已有回归 case 可独立执行，建议合并后一次运行
- 失败归因使用设计报告第 4.8 节的 9 类编码体系

---

## 附录：Case ID 完整清单

```
E2E-001  长文档列举      ANSWERABLE   D1  quality-progress-and-lessons.md
E2E-002  架构边界        ANSWERABLE   D2  卡券三期-迁移方案.md
E2E-003  配置查值        ANSWERABLE   D3  项目启动配置清单.md
E2E-004  配置查值        ANSWERABLE   D4  模型绑定配置参考.md
E2E-005  运行态说明      ANSWERABLE   D6  文档识别与OCR运行态说明.md
E2E-006  Excel 行定位    ANSWERABLE   D5  scenarios.xlsx
E2E-007  Excel 聚合统计  ANSWERABLE   D5  scenarios.xlsx
E2E-008  多跳综合推理    ANSWERABLE   D2  卡券三期-迁移方案.md
E2E-009  踩坑经验提取    ANSWERABLE   D1  quality-progress-and-lessons.md
E2E-010  配置错误诊断    ANSWERABLE   D4  模型绑定配置参考.md
E2E-011  无命中拒答      UNANSWERABLE  —  虚构配置项
E2E-012  无命中拒答      UNANSWERABLE  —  知识库外通用知识
```
