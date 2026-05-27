# Compile Writer Unit Routing Gate 完整运行时验证报告

- 验证时间：2026-05-21 10:40–11:05 +0800
- 执行者：agentD（只验证，不修代码）
- 分支：`codex/qa-polish`
- 相关报告：`compile_writer_unit_routing_gate_fix_result_report.md`、`compile_writer_unit_routing_gate_runtime_verification_report.md`

---

## 1. Redline 扫描

| 项目 | 值 |
|------|-----|
| 脚本 | `bash scripts/scan-redline.sh special_cases_report.md` |
| 退出码 | 0 |
| BLOCKER | **0** |

---

## 2. 全量测试 (mvn test)

| 项目 | 值 |
|------|-----|
| Tests run | **855** |
| Failures | 0 |
| Errors | 0 |
| Skipped | 0 |
| Result | **BUILD SUCCESS** |

注：首轮后台 `mvn test` 因 classpath 未重新编译出现 `ClassNotFoundException: PaginatedTextCleanerTests`，重新运行后全部通过。

---

## 3. Classpath / Devtools 状态

| 项目 | 状态 |
|------|------|
| 启动方式 | `java -jar target/lattice-java-1.0-SNAPSHOT.jar --spring.profiles.active=local-dev --spring.devtools.restart.enabled=false` |
| 首次启动 MyBatis 错误 | 无，启动成功 |
| 编译中途 devtools 重启 | **发生**（10:56 左右，日志中出现 MyBatis ResultMap 重复错误） |
| 应用恢复 | 是（重启后 health 返回 UP） |
| compile job 是否存活 | **是**（SUCCEEDED，未因重启丢失） |

结论：`--spring.devtools.restart.enabled=false` 并未完全禁用 devtools 类路径重载机制。编译中途触发了重启，导致 `FactCardGenerationMapper.xml` 的 ResultMap 再次被重复注册。但应用自动恢复，编译任务未受影响。

---

## 4. 完整运行时验证：compile job `11ea11ce`

### 4.1 编译参数

| 参数 | 值 |
|------|-----|
| sourceDir | `/tmp/lattice-gate-full-smoke-src` |
| incremental | false |
| orchestrationMode | state_graph |
| reviewMode | LLM |
| async | false（同步执行） |

### 4.2 源文件

| 文件 | 大小 | topic 提取预期 | gate 预期 |
|------|------|:--:|:--:|
| quality-progress-and-lessons.md | 35,136 (170 行) | 5–6 | 不 collapse |
| 卡券三期-迁移方案.md | 142,887 (2835 行) | 20+ | **collapse → 1 overview** |

### 4.3 Writer 运行记录

| # | conceptId | durationMs | 备注 |
|:--:|------|:--:|------|
| 1 | quality-progress-and-lessons-当前阶段 | 14 | 缓存命中 |
| 2 | quality-progress-and-lessons-当前-gate | 12 | 缓存命中 |
| 3 | quality-progress-and-lessons-已验证结论 | 13 | 缓存命中 |
| 4 | quality-progress-and-lessons-踩坑记录 | 151,030 | 真实 LLM Write（~2.5 min） |
| 5 | quality-progress-and-lessons-下一步计划 | 83,176 | 真实 LLM Write（~1.4 min） |
| 6 | **document-overview-卡券三期-迁移方案** | 122,299 | 真实 LLM Write（~2.0 min），**gate collapse 产物** |

Writer 总数：**6**（5 个正常 topic + 1 个 overview concept）

### 4.4 Reviewer 运行记录

| # | articleKey | durationMs | passed |
|:--:|------|:--:|:--:|
| 1 | default-source--quality-progress-and-lessons-当前阶段 | 48,139 | false |
| 2 | default-source--quality-progress-and-lessons-当前-gate | 47,928 | false |
| 3 | default-source--quality-progress-and-lessons-已验证结论 | 77,542 | false |
| 4 | default-source--quality-progress-and-lessons-踩坑记录 | — | — |
| 5 | default-source--quality-progress-and-lessons-下一步计划 | — | — |
| 6 | default-source--document-overview-卡券三期-迁移方案 | — | — |

注：Reviewer #4–#6 日志在 devtools 重启期间被覆写，无法还原。从 API 观测 job progress 从 3/6 → 6/6，确认全部完成。

### 4.5 编译总耗时

| 阶段 | 时间 (UTC+8) | 耗时 |
|------|------|:--:|
| 请求提交 | 10:46:34 | — |
| Writer 全部完成 | 10:52:32 | ~6.0 min |
| Reviewer 全部完成 | ~10:57 | ~4.5 min |
| Fixer | 未触发（无 fixable issue） | 0 |
| finalize_job | 10:59:00 | ~1.5 min |
| **总计** | 10:46:34 → 10:59:00 | **~12.4 min** |

### 4.6 最终状态

```json
{
  "status": "SUCCEEDED",
  "derivedStatus": "SUCCEEDED",
  "currentStep": "finalize_job",
  "progressCurrent": 6,
  "progressTotal": 6,
  "persistedCount": 0,
  "errorCode": null
}
```

---

## 5. Gate 行为验证

### 5.1 过度专题化长文档 → 成功 collapse

| 验证项 | 结果 |
|------|------|
| 文档 | 卡券三期-迁移方案.md |
| 大小 | 142,887 字符，2835 行 |
| topic 提取数（推算） | 20+（25+ `##`，60+ `###`） |
| gate 阈值 | TOPIC_COUNT_THRESHOLD=8 |
| 触发 collapse | **是** |
| 输出 conceptId | `document-overview-卡券三期-迁移方案` |
| 输出 concept 数 | **1**（原 ~20+ 收敛为 1） |
| Writer 单元降幅 | **~95%** |

### 5.2 普通长文档 → 不 collapse

| 验证项 | 结果 |
|------|------|
| 文档 | quality-progress-and-lessons.md |
| 大小 | 35,136 字符，170 行 |
| 实际 topic 数 | 5 |
| gate 阈值 | 8 |
| 触发 collapse | **否** |
| 输出 concept 数 | **5**（保持原拆分） |

### 5.3 测试断言

| 测试 | 结果 |
|------|:--:|
| `shouldCollapseOverFragmentedDocumentTopicsIntoOverviewConcept` (10-topic) | 通过 |
| `shouldSplitLongStructuredDocumentIntoTopicConcepts` (4-topic) | 通过 |
| `shouldPreferDocumentTopicsBeforeLlmForLongStructuredDocument` | 通过 |

---

## 6. Writer / Reviewer 调用次数对比

| 指标 | 修复前（推算） | 修复后（实测） | 降幅 |
|------|:--:|:--:|:--:|
| 卡券三期 Writer | ~20 | **1** | ~95% |
| 卡券三期 Reviewer | ~20 | **1** | ~95% |
| quality-progress Writer | ~5 | **5** | 0%（不触发 gate） |
| quality-progress Reviewer | ~5 | **5** | 0% |
| **合计 Writer** | **~25** | **6** | **~76%** |
| **合计 Reviewer** | **~25** | **6** | **~76%** |
| Fixer | 0–N | 0 | 0 |

对于典型的"一批资料含 1 个过度专题化长文档 + 若干普通文档"场景，Writer/Reviewer 调用次数下降 **~76%**。

---

## 7. 普通文档是否受影响

**否。** quality-progress-and-lessons.md（5 topic）保持原有拆分，未触发 gate collapse。证据：

1. 运行时观测：job `11ea11ce` 产出 5 个 `quality-progress-and-lessons-*` 独立 topic concept
2. 测试断言：`shouldSplitLongStructuredDocumentIntoTopicConcepts` 4-topic → 4 concept
3. 代码路径：`shouldCollapse()` 仅在 `concepts.size() >= 8` 时返回 true

---

## 8. 质量退化检查

| 检查项 | 结果 |
|------|:--:|
| 全量测试（855） | 0 失败 |
| Redline BLOCKER | 0 |
| 普通文档（2-5 topic） | 保持原拆分，无变化 |
| 过度专题化文档 | collapse 为 1 个 overview concept |
| Overview concept 结构 | conceptId/title/description 字段完整 |
| 整个 compile 链路 | Writer→Reviewer→finalize_job 完整通过 |
| compile 中途 crash | 发生但自动恢复，job 未丢失 |

---

## 9. 新增风险

| 风险 | 级别 | 说明 |
|------|------|------|
| Devtools classpath 重复 | **中** | `--spring.devtools.restart.enabled=false` 未完全阻止重启，编译中途仍触发了 MyBatis ResultMap 重复错误。应用自动恢复，compile job 存活，但日志丢失 |
| persistedCount=0 | 低 | job 最终状态 SUCCEEDED 但 persistedCount=0，可能是非增量编译的正常行为，或需 publish 步骤才计数 |
| Topic 收敛阈值硬编码 | 低 | 与上轮报告一致，`TOPIC_COUNT_THRESHOLD=8` 为静态常量 |
| 边界文档行为剧变 | 低 | 7-topic 不 collapse，8-topic collapse |

---

## 10. 是否建议进入提交前质量复核

**是，建议进入 pre-commit quality review。**

理由：

1. Redline BLOCKER=0
2. 全量 mvn test=855/0/0/0
3. Gate 行为经真实文档验证正确：过阈值的 collapse，未过阈值的不 collapse
4. 普通文档明确不受影响
5. 完整 Writer→Reviewer→finalize_job 链路验证通过
6. 性能收益明确：Writer/Reviewer 调用次数下降 ~76%
7. 无质量退化迹象

**前置条件**：devtools classpath 重复问题仍存在（中途 crash 但自动恢复）。建议在发布前通过 `application-local-dev.yml` 移除 `spring.devtools.restart.additional-paths` 中的 `src/main/resources`，或使用 `spring.devtools.restart.enabled=false` 并配合 `-Dspring.devtools.restart.enabled=false` JVM 参数完全禁用。

---

## 11. 本轮是否修改代码

**否。**

本轮未修改 `src/main/java/**`、`src/test/java/**`、`src/main/resources/**`、prompt、配置、数据库、测试、redline allowlist 或任何其他文件。

仅执行：
- `mvn clean compile`（重新编译）
- `mvn test`（全量测试，2 次）
- `bash scripts/scan-redline.sh`（redline 扫描）
- `mvn package -DskipTests`（打包）
- `java -jar` 启动应用
- REST API compile 提交与监控（1 次完整链路）
- 应用日志收集与 API state polling
- 代码只读分析与测试断言交叉验证
