# PE5 INTERNAL_MIRROR 物化阶段文档格式白名单修复结果报告

时间：2026-06-08
执行人：agentA（代码执行 Agent）
依据：architect 分配的 PE5 导入阶段 `.xlsx` / `.csv` / `.pdf` 被静默过滤修复任务

---

## 1. 根因定位

### 1.1 唯一根因

**`SourceMaterializationService.DEFAULT_INCLUDED_EXTENSIONS` 是 INTERNAL_MIRROR 物化扫描的文件扩展名白名单。当前白名单仅包含代码/项目文件格式（`.java`、`.xml`、`.gradle` 等），缺少通用办公文档格式。`.xlsx`、`.xls`、`.csv`、`.pdf` 等文件在 `shouldIncludeMirrorFile()` 第 330 行 `DEFAULT_INCLUDED_EXTENSIONS.contains(ext)` 中被静默过滤，不会进入后续的编译、分块、检索链路。**

### 1.2 故障链路

```
1. PE5 数据目录包含 5 份文档: .md / .yml / .xlsx / .csv / .pdf
2. INTERNAL_MIRROR 物化扫描 → shouldIncludeMirrorFile:
   - .md  → DEFAULT_INCLUDED_EXTENSIONS.contains(".md")  → true  ✓
   - .yml → DEFAULT_INCLUDED_EXTENSIONS.contains(".yml") → true  ✓
   - .xlsx → DEFAULT_INCLUDED_EXTENSIONS.contains(".xlsx") → false ✗ 静默丢弃
   - .csv  → DEFAULT_INCLUDED_EXTENSIONS.contains(".csv")  → false ✗ 静默丢弃
   - .pdf  → DEFAULT_INCLUDED_EXTENSIONS.contains(".pdf")  → false ✗ 静默丢弃
3. 物化结果: 仅 2 份文档进入后续链路 → 检索召回不足 → 结构化查询无数据可查
```

### 1.3 为什么这不是 PE5 特判

- `.xlsx` / `.xls` — Microsoft Excel 电子表格，企业知识库的核心载体之一（台账、报表、检验记录）
- `.csv` — 通用数据交换格式，广泛用于结构化数据存储
- `.pdf` — 通用文档格式，技术规范、检验报告、合同等核心文档载体

当前白名单偏向于"代码仓库"场景（`.java`、`.xml`、`.gradle`、`.js`、`.ts` 等），但 Lattice 是一个**知识库系统**，其内部镜像物化应天然支持通用文档格式。这四个扩展名的缺失是白名单覆盖度不足的通用缺陷，不是某个 eval 数据集的特有问题。

---

## 2. 修复内容

### 2.1 修改清单

**仅修改 1 个文件**：`SourceMaterializationService.java`

| 变更 | 说明 |
|------|------|
| `DEFAULT_INCLUDED_EXTENSIONS` 新增 4 个扩展名 | `.xlsx`、`.xls`、`.csv`、`.pdf` |

### 2.2 修改前后对比

```java
// 修改前
private static final Set<String> DEFAULT_INCLUDED_EXTENSIONS = Set.of(
        ".java", ".xml", ".yml", ".yaml", ".properties", ".json",
        ".sql", ".md", ".txt", ".sh", ".js", ".ts", ".vue", ".css", ".html",
        ".gradle"
);

// 修改后
private static final Set<String> DEFAULT_INCLUDED_EXTENSIONS = Set.of(
        ".java", ".xml", ".yml", ".yaml", ".properties", ".json",
        ".sql", ".md", ".txt", ".sh", ".js", ".ts", ".vue", ".css", ".html",
        ".gradle", ".xlsx", ".xls", ".csv", ".pdf"
);
```

### 2.3 修改的属性

| 属性 | 说明 |
|------|------|
| 修改文件数 | **1**（仅 `SourceMaterializationService.java`） |
| 修改行数 | **1 行**（`DEFAULT_INCLUDED_EXTENSIONS` 常量增加 4 个值） |
| 不含业务词/题号/文件名/样例值 | **是** — `.xlsx`、`.xls`、`.csv`、`.pdf` 均为通用格式扩展名 |
| 不含白名单/特判 | **是** |
| 不影响 UPLOAD / GIT 路径 | **是** — `DEFAULT_INCLUDED_EXTENSIONS` 仅在 `shouldIncludeMirrorFile` 中使用，该方法仅在 INTERNAL_MIRROR 的 `walkFileTree` 回调中调用 |
| 未改 Executor / Compiler / 检索 / Planner | **是** |
| 未改 src/test/java | **是** |
| 未改 prompt / config / schema / scripts / 题集 | **是** |

---

## 3. 影响分析

### 3.1 对现有行为的影响

| 场景 | 影响 |
|------|------|
| INTERNAL_MIRROR 物化 `.xlsx` | 修复前：静默丢弃 → 修复后：纳入扫描 |
| INTERNAL_MIRROR 物化 `.xls` | 修复前：静默丢弃 → 修复后：纳入扫描 |
| INTERNAL_MIRROR 物化 `.csv` | 修复前：静默丢弃 → 修复后：纳入扫描 |
| INTERNAL_MIRROR 物化 `.pdf` | 修复前：静默丢弃 → 修复后：纳入扫描 |
| INTERNAL_MIRROR 物化其他格式（`.java`、`.md` 等） | 无变化 |
| GIT 物化 | 无影响（GIT 路径不使用 `DEFAULT_INCLUDED_EXTENSIONS`） |
| UPLOAD | 无影响（UPLOAD 路径不使用 `DEFAULT_INCLUDED_EXTENSIONS`） |
| 排除规则（`.class`、`.jar`、`.zip` 等） | 无变化（`DEFAULT_EXCLUDED_EXTENSIONS` 未修改） |

### 3.2 纳入后的处理链路

新增的 4 种格式在物化进入 staging 目录后，由后续的 SourceSyncService / Compiler 处理：
- `.xlsx` / `.xls` / `.csv`：已有 Excel/CsvPoiCompiler 支持
- `.pdf`：已有 PdfCompiler 支持
- 这些编译器在本次修复前已存在，只是因物化阶段被过滤而从未对 INTERNAL_MIRROR 来源执行

---

## 4. 测试结果

### 4.1 redline

```
BLOCKER=0
```

### 4.2 mvn test

| 指标 | 值 |
|------|-----|
| Tests run | **1018** |
| Failures | **0** |
| Errors | **0** |
| Skipped | **0** |
| 结论 | **BUILD SUCCESS** |

---

## 5. 明确声明

- [x] 根因唯一：`DEFAULT_INCLUDED_EXTENSIONS` 缺少通用文档格式扩展名，导致 INTERNAL_MIRROR 物化时 `.xlsx` / `.xls` / `.csv` / `.pdf` 被静默过滤
- [x] 修复策略：将 4 个通用文档格式扩展名加入白名单
- [x] 仅修改 1 个文件：`SourceMaterializationService.java`
- [x] 仅修改 1 行：`DEFAULT_INCLUDED_EXTENSIONS` 常量增加 4 个值
- [x] 无业务词/题号/文件名/样例值特判
- [x] 无白名单/case-specific fallback
- [x] GIT / UPLOAD / 排除规则 行为不变
- [x] 未改 src/test/java
- [x] 未改 prompt / config / schema / scripts / 题集
- [x] redline BLOCKER=0
- [x] mvn test 1018/0/0/0 BUILD SUCCESS
- [x] 未提交 commit
