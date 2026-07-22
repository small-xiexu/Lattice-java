# Fresh Eval 2026-09 FQ9 Docker Runtime 根因分析报告

分析时间：2026-06-09 12:45 ~ 12:55
HEAD：`76eb4eb`
执行人：agentD

---

## 1. 部署版本

| 检查项 | 结果 |
|---|---|
| 本地 jar sha256 | `f1dcc2f15a92cc77f108feac288526c39aec157bdddf614b914db03a82fbba2f` |
| 容器 jar sha256 | `f1dcc2f15a92cc77f108feac288526c39aec157bdddf614b914db03a82fbba2f` |
| jar 是否一致 | ✅ 相同 |
| `PROMPT_FOCUS_LONG_CONTENT_LINE_THRESHOLD` | **0 次** ❌ 容器 jar 不含 |
| `selectSegmentDiversePromptFocusWindows` | **0 次** ❌ 容器 jar 不含 |
| 工作区 `AnswerGenerationPromptEvidenceSupport.java` | **已修改**（未编译打包） |

**结论：部署未生效。** 修复代码存在于工作区源码，但从未编译到 jar。容器运行的是旧版本。

---

## 2. FQ9 运行时状态

| 字段 | 值 |
|---|---|
| queryId | `ccaf4d17-4c92-4c6b-b409-337cd21f5f72` |
| answerOutcome | SUCCESS |
| generationMode | **FALLBACK** |
| final answer | 错误——输出 PDF 中的验收条件，而非缺陷 P0/P1 计数 |

---

## 3. 检索分析

### 3.1 Retrieval Top5

| rank | channel | title | score |
|---|---|---|---|
| 1 | article_chunk_fts | defect list | 29.1 |
| 1 | article_chunk_fts | defect list | 23.2 |
| 1 | article_chunk_fts | defect list / 字段与列解释 | 23.1 |
| 1 | article_chunk_fts | defect list / 数据质量说明 | 17.1 |
| 1 | article_chunk_fts | defect list / **高严重级别缺陷** | 17.1 |

**defect-list.csv 在检索中排首位，Recall@10=10/10。** 包含"高严重级别缺陷"和"按状态汇总"的 chunk 均已进入 fused Top5。检索不存在问题。

### 3.2 数据库确认

- `source_files` 中 defect-list.csv 存在 ✅
- `articles` 中 defect list 存在 ✅
- chunks 包含 P0/P1 完整数据 ✅

---

## 4. 根因判断

### **主根因：部署未生效**

修复代码（`AnswerGenerationPromptEvidenceSupport.java` 中的 `PROMPT_FOCUS_LONG_CONTENT_LINE_THRESHOLD` 和 `selectSegmentDiversePromptFocusWindows`）存在于工作区源码，但从未编译打包进容器 jar。容器运行的是旧版本。

FQ9 走 FALLBACK 路径，旧版 evidence packing 未对长文档做分布式窗口选择，导致 PDF 中"P0/P1 未关闭"等无关证据被优先选中，而 defect-list.csv 的 P0/P1 聚合行被截断在 1200 字符阈值之后未进入 prompt。

**不是检索问题**（Recall@10 = 10/10），**不是 RRF 排序问题**（defect-list 排首位），**不是入库缺失**（数据完整）。是 **evidence packing 未触发修复**。

---

## 5. 修复步骤

```bash
# 1. 重新编译（确保工作区修改进入 jar）
mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository \
  package -DskipTests

# 2. 确认 jar 包含修复
sha256sum target/lattice-java-1.0-SNAPSHOT.jar
strings target/lattice-java-1.0-SNAPSHOT.jar | grep -c "PROMPT_FOCUS_LONG_CONTENT_LINE_THRESHOLD"
# 应输出 >= 1

# 3. 重启容器（替换 jar）
docker cp target/lattice-java-1.0-SNAPSHOT.jar lattice_app:/app/app.jar
docker restart lattice_app

# 4. 验证
docker exec lattice_app strings /app/app.jar | grep -c "PROMPT_FOCUS_LONG_CONTENT_LINE_THRESHOLD"
# 应输出 >= 1
```

重新部署后重跑 FQ9 应恢复正常。

---

## 6. 下一步

1. **agentD 验证**：部署生效后重跑 FQ9 + PE9 全量 gate
2. **不需要 agentA 修代码**——修复已完成，仅需重新编译部署

---

## 7. 明确声明

- [x] 未修改生产代码
- [x] 未修改测试代码
- [x] 未清库、未重建、未重新导入
- [x] 未提交 commit
- [x] 所有结论基于运行时只读检查
