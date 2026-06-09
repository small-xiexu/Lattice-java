# PE5 资料源导入"仅 2 份文档"异常 — 只读根因分析报告

分析时间：2026-06-08
执行人：agentB（治理/归因 Agent）
类型：只读根因分析，不修改任何文件

---

## 1. 结论：问题在 INTERNAL_MIRROR 物化层的文件扩展名白名单

**`.xlsx`、`.csv`、`.pdf` 不在 `SourceMaterializationService.DEFAULT_INCLUDED_EXTENSIONS` 白名单中。INTERNAL_MIRROR 物化阶段将这 3 份文件静默排除，只有 `.md` 和 `.yaml` 进入了 staging 目录和后续编译链路。这不是上传失败、不是编译失败、不是 article 生成失败——是物化层的文件扩展名白名单过滤。**

---

## 2. 证据链

### 2.1 PE5 有 5 份源文件

| 文件 | 扩展名 | agentC 已生成 |
|------|:---:|:---:|
| `supply-chain-quality-policy.md` | `.md` | ✅ |
| `supplier-registry.yaml` | `.yaml` | ✅ |
| `incoming-inspection-records.xlsx` | `.xlsx` | ✅ |
| `batch-tracking-log.csv` | `.csv` | ✅ |
| `nonconformance-handling-sop.pdf` | `.pdf` | ✅ |

### 2.2 Gate 报告确认只有 2 articles

```
PE5 数据（2 articles, 2 source_files, SUCCEEDED）
```

### 2.3 物化白名单不包含文档格式

**文件**：`SourceMaterializationService.java` 第 71-76 行

```java
private static final Set<String> DEFAULT_INCLUDED_EXTENSIONS = Set.of(
    ".java", ".xml", ".yml", ".yaml", ".properties", ".json",
    ".sql", ".md", ".txt", ".sh", ".js", ".ts", ".vue", ".css", ".html",
    ".gradle"
);
```

**`.xlsx`、`.csv`、`.pdf` 均不在其中。** 白名单设计初衷是为 CODE_LIGHT（Java 代码项目）服务，未考虑 DOCUMENT profile 下的文档格式。

### 2.4 白名单过滤逻辑

**文件**：`SourceMaterializationService.java` 第 324-331 行

```java
int dotIndex = lowerName.lastIndexOf('.');
if (dotIndex > 0) {
    String ext = lowerName.substring(dotIndex);
    if (DEFAULT_EXCLUDED_EXTENSIONS.contains(ext)) {
        return false;                            // 排除已知不安全格式
    }
    return DEFAULT_INCLUDED_EXTENSIONS.contains(ext);  // 白名单检查
}
```

逻辑是**白名单制**：不在白名单中的扩展名一律被排除。`.xlsx`/`.csv`/`.pdf` 不在排除名单中，但也不在白名单中 → 被静默排除。

### 2.5 物化扫描中的调用链

`materializeInternalMirrorSource()`（第 219 行）→ `Files.walkFileTree` 遍历镜像目录（第 231 行）→ 每个文件调用 `shouldIncludeMirrorFile()`（第 243 行）→ 白名单过滤。

---

## 3. 排除的其他假设

| 假设 | 判定 | 证据 |
|------|:---:|------|
| 上传失败 | **排除** | agentC 报告确认 5 份文件全部成功生成 |
| 编译失败 | **排除** | compile status = SUCCEEDED |
| article Writer 失败 | **排除** | 物化阶段文件就没进入 staging，根本没有机会到 Writer |
| INTERNAL_MIRROR 同步失败 | **排除** | sync status = SUCCEEDED；物化成功（只是过滤了部分文件） |
| 前端展示问题 | **排除** | DB 中 articles 表确实只有 2 条（不是前端展示过滤） |
| 文件过大/过小被排除 | **排除** | 无文件大小限制 |
| 文件损坏 | **排除** | agentC 生成的文件格式正确 |

---

## 4. 为什么之前没有暴露

- PE1-PE4 使用 UPLOAD 模式导入，不走 INTERNAL_MIRROR 物化路径。UPLOAD 模式直接上传指定文件，不经过扩展名白名单
- INTERNAL_MIRROR 的 dogfood 验证使用的是 Lattice-java 自身（纯 Java 项目，`.java`/`.xml`/`.yml` 均在白名单中），未触发此限制
- PE5 是第一个通过 INTERNAL_MIRROR 导入含 XLSX/CSV/PDF 格式的 DOCUMENT profile 资料包

---

## 5. 下一步建议

### 交给 agentA 做最小修复

**修改范围**：仅 `SourceMaterializationService.java` 的 `DEFAULT_INCLUDED_EXTENSIONS`

**修改内容**：添加 `.xlsx`、`.xls`、`.csv`、`.pdf`

```java
private static final Set<String> DEFAULT_INCLUDED_EXTENSIONS = Set.of(
    ".java", ".xml", ".yml", ".yaml", ".properties", ".json",
    ".sql", ".md", ".txt", ".sh", ".js", ".ts", ".vue", ".css", ".html",
    ".gradle",
    ".xlsx", ".xls", ".csv", ".pdf"   // 新增：文档格式
);
```

**通用性**：这些扩展名是通用文档格式，不是 PE5 特判。对所有 INTERNAL_MIRROR 的 DOCUMENT profile 导入均生效。

**验证**：agentD 清库 → 重新 INTERNAL_MIRROR 导入 PE5 → 确认 5 份 source_file 均入库 → 确认 articles >= 3（Markdown + YAML + PDF 至少各一篇）

---

## 6. 明确声明

- [x] 未修改任何文件
- [x] 未提交 commit
- [x] 根因唯一：`SourceMaterializationService.DEFAULT_INCLUDED_EXTENSIONS` 白名单不含文档格式
- [x] 证据链完整：agentC 生成 5 文件 → gate 报告显示 2 articles → 源码确认白名单过滤
- [x] 与 upload/compile/writer/frontend 无关
- [x] 修复方案为通用扩展名添加，非 PE5 特判
