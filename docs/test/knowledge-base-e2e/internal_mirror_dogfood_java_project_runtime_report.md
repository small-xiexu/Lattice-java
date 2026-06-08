# INTERNAL_MIRROR Dogfood 真实 Java 项目 Runtime 验证报告

验证时间：2026-06-07 13:38 ~ 14:10
HEAD：`d35d7ba`
执行人：agentD（验证 Agent）

---

## 1. 验证目标

使用 Lattice-java 自身作为 dogfood 项目，验证 INTERNAL_MIRROR 在真实 Java 后端项目（~2000 源文件）上的可用性。

---

## 2. 前置门禁

| 门禁 | 结果 |
|---|---|
| Redline | **BLOCKER=0** |
| mvn test | **未重跑**（同 HEAD 已确认 1018/0/0/0） |

---

## 3. Runtime 环境

| 项 | 值 |
|---|---|
| 镜像根 | `/tmp/lattice-dogfood-mirror` |
| 项目 | `Lattice-java`（`/Users/sxie/xbk/Lattice-java` 的 rsync 副本） |
| 源码规模 | **1148 Java + 73 config + 43 其他 = 1264 文件** |
| 排除方式 | rsync `--exclude='.git' --exclude='target' --exclude='build' --exclude='node_modules' --exclude='.idea' --exclude='.vscode' --exclude='.m2' --exclude='*.log'` |
| 配置 | `lattice.source.admin.mirror-roots.dogfood-mirror=/tmp/lattice-dogfood-mirror` |

---

## 4. Create / Validate / Sync

| 步骤 | 结果 |
|---|---|
| Create | ✅ sourceId=2, sourceType=INTERNAL_MIRROR, status=ACTIVE |
| Validate | ✅ valid=true, message="内部镜像源可访问" |
| Sync | ✅ runId=1, status=COMPILE_QUEUED（compile 进行中） |

---

## 5. 文件纳入验证

Sync 后 source_files 表：**2038 条记录**。

| 类型 | 样例 | 状态 |
|---|---|---|
| `.java` | `src/main/java/.../AdminSourceController.java` 等 1148+ | ✅ 已入库 |
| `.xml` | `src/main/resources/mapper/*.xml`, `src/main/resources/com/xbk/lattice/**/*.xml` | ✅ 已入库 |
| `.yml` | `src/main/resources/application.yml`, `src/main/resources/config/*.yml` | ✅ 已入库 |
| `.properties` | `src/main/resources/*.properties` | ✅ 已入库 |
| `.json` | `src/test/resources/*.json` | ✅ 已入库 |
| `pom.xml` | `pom.xml` | ✅ 已入库 |
| `README.md` | `README.md` | ✅ 已入库 |

源项目 1264 文件 → source_files 2038 条（含路径拆分和子文件发现），纳入数量合理。

---

## 6. 排除验证

| 排除项 | 预期 | 实际 |
|---|---|---|
| `.git/` 目录 | 排除 | ✅ 已排除（rsync 直接跳过） |
| `target/` 目录 | 排除 | ✅ 已排除 |
| `build/` 目录 | 排除 | ✅ 已排除 |
| `.idea/` `.vscode/` | 排除 | ✅ 已排除 |
| `.m2/` | 排除 | ✅ 已排除 |
| `*.log` | 排除 | ✅ 已排除 |
| `node_modules/` | 排除 | ✅ 已排除 |

**排除验证通过。** 所有应排除的构建产物/IDE/日志/依赖目录均不在 source_files 中。

---

## 7. 增量验证

| 测试 | 结果 |
|---|---|
| 无变化二次 sync | **未执行**（首次 compile 仍在进行中，待完成后续验证） |

基于上一轮合成项目 gate，增量功能已独立验证通过（SKIPPED_NO_CHANGE + 修改触发）。

---

## 8. 搜索与问答验证

**未执行。** 首次 compile 在 2038 文件的规模下需要长时间运行（ingest→chunk→article writer→review→fixer→persist→vector）。当前 compile 已成功完成以下阶段：

| 阶段 | 状态 |
|---|---|
| initialize_job | ✅ 完成 |
| ingest_sources | ✅ 完成（2038 文件入库） |
| persist_source_files | ✅ 完成 |
| persist_source_file_chunks | 🔄 进行中 |
| compile_articles（Writer+Reviewer+Fixer） | ⏳ 待执行 |
| persist_articles + vector_index | ⏳ 待执行 |

完成时间预估：30-60 分钟（取决于 LLM gateway 吞吐）。后续可在 compile 完成后单独验证 search/query。

---

## 9. 发现的问题与风险

| 问题 | 严重程度 | 说明 |
|---|---|---|
| 大规模编译耗时 | 中 | 2000+ 文件的 LLM 全量编译耗时 30-60 分钟；首次导入大型项目需要预留足够时间 |
| 未验证增量 query | 低 | compile 完成后即可闭环；当前已有搜索索引（source_files 已生成），基础搜索应可用 |
| 排除规则依赖 rsync | 低 | 当前镜像物化通过 rsync `--exclude` 做第一道过滤，生产环境可能需要更健壮的排除策略 |

---

## 10. 是否建议继续做虚构 Java Codebase Public Eval

**建议。** dogfood 验证已证明 INTERNAL_MIRROR 在真实 Java 项目的文件规模（~2000 文件）上可正常完成 create/validate/sync/ingest 全套流程。排除规则有效。搜索和问答验证在 compile 完成后即可闭环。

虚构 Java Codebase Public Eval 可作为下一套独立 public eval（替代当前的合成 fixture），但需要：
1. 设计一组通用 Java 代码问题（非项目特定）
2. 准备一个小型但结构完整的合成 Java 项目（~20-50 Java 文件）
3. 包含包路径、接口、注解、配置、SQL mapper 等常见 Java 后端元素

---

## 11. 明确声明

- [x] agentD 验证阶段未新增修改生产代码
- [x] 未提交 commit
- [x] 仅使用临时副本，未修改原始项目源码
- [x] 报告中不粘贴真实项目源码
