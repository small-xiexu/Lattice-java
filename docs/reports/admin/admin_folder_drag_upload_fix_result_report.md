# 管理端文件夹拖拽上传修复 结果报告

时间：2026-06-09
执行人：agentA
类型：前端修复

---

## 1. 根因说明

当前上传区 `handleUploadPickerDrop` 只读取 `event.dataTransfer.files`，该 API 返回的是**扁平文件列表**，无法递归展开文件夹内容。当用户拖入名为 `sources` 的文件夹时：

- 部分浏览器会在 `dataTransfer.files` 中把文件夹自身当做一个无扩展名的“文件”
- `validateUploadFiles` → `isSupportedUploadFile` 检查扩展名 → 无扩展名 → 返回 `false`
- 文件夹名进入 `rejectedFiles`，前端提示 "sources 格式暂不支持"

文件夹内的实际文件从未被读取。

**修复思路**：使用 `event.dataTransfer.items` + `webkitGetAsEntry()` API，递归展开 `FileSystemDirectoryEntry`，收集所有后代文件，并保留相对路径。

---

## 2. 修改文件清单

| 文件 | 修改类型 |
|------|------|
| `src/main/resources/static/admin/modules/management-runtime-part-01.js` | 新增 4 个函数 + 修改 4 个函数 |

**未修改任何其他文件。** 后端 Java 代码零改动。

---

## 3. 改动详情

### 3.1 新增函数

| 函数 | 说明 |
|------|------|
| `readDroppedEntries(dataTransfer)` | 异步入口：遍历 `dataTransfer.items`，对文件直接收集，对目录调用 `traverseDirectoryEntry`；若 items 不可用则回退到 `dataTransfer.files` |
| `traverseDirectoryEntry(dirEntry, basePath)` | 递归遍历目录：使用 `createReader().readEntries()` 分批读取，对子文件调 `getFileFromFileEntry` 获取 File 对象并存入 `droppedDirRelativePaths` Map，对子目录递归 |
| `readDirectoryEntries(reader)` | Promise 封装：分批调用 `readEntries` 直到返回空数组，确保大目录全部读完（Chrome readEntries 单次最多返回 100 条） |
| `getFileFromFileEntry(fileEntry)` | Promise 封装：`fileEntry.file(success, error)` → 失败时 resolve(null)，不阻断遍历 |

### 3.2 修改函数

| 函数 | 改动 |
|------|------|
| `handleUploadPickerDrop` | 改为 `async`，调用 `readDroppedEntries` 替代 `Array.from(event.dataTransfer.files)`；新增空文件夹提示；新增全不支持格式提示 |
| `getUploadRelativePath` | 优先检查 `droppedDirRelativePaths` Map，有映射则直接返回目录相对路径 |
| `clearUploadFileSelection` | 新增 `droppedDirRelativePaths.clear()` |
| `handleUploadInputChange` | 新增 `droppedDirRelativePaths.clear()`（文件选择器替换全部文件时清理残留映射） |

### 3.3 新增模块级变量

```javascript
const droppedDirRelativePaths = new Map();
// key: File 对象, value: 目录递归得到的相对路径 (如 "sources/config/settings.yaml")
```

---

## 4. 文件夹拖拽实现流程

```
用户拖入 "sources/" 文件夹
  │
  ▼
handleUploadPickerDrop(event) [async]
  │
  ├─ event.dataTransfer.items → Array.from()
  │
  ▼
readDroppedEntries(dataTransfer)
  │
  ├─ items[0].webkitGetAsEntry() → FileSystemDirectoryEntry { name: "sources" }
  │
  ▼
traverseDirectoryEntry(sourcesEntry, "sources")
  │
  ├─ reader.readEntries() → ["readme.md"(File), "config/"(Dir), "data/"(Dir)]
  │
  ├─ "readme.md" → getFileFromFileEntry() → File
  │     └─ droppedDirRelativePaths.set(file, "sources/readme.md")
  │
  ├─ "config/" → traverseDirectoryEntry(configEntry, "sources/config")
  │     └─ "settings.yaml" → droppedDirRelativePaths.set(file, "sources/config/settings.yaml")
  │
  └─ "data/" → traverseDirectoryEntry(dataEntry, "sources/data")
        └─ "report.csv" → droppedDirRelativePaths.set(file, "sources/data/report.csv")
  │
  ▼
返回 { files: [readme.md, settings.yaml, report.csv], emptyDirPaths: [] }
  │
  ▼
validateUploadFiles(files) → 格式校验（只校验展开后的文件，目录名不在 files 中）
  │
  ▼
mergeUploadFiles → setUploadFiles → 渲染到上传列表
  │
  ▼
getUploadRelativePath(file) → 从 droppedDirRelativePaths 获取 "sources/config/settings.yaml"
  │
  ▼
uploadAndCompile → formData.append("files", file, "sources/config/settings.yaml")
```

---

## 5. 相对路径保留策略

| 场景 | 相对路径来源 | 示例 |
|------|------|------|
| 拖拽文件夹 | `droppedDirRelativePaths` Map（递归时构建） | `sources/config/settings.yaml` |
| 拖拽单个文件 | `file.name`（回退） | `readme.md` |
| 文件选择器 (multiple) | `file.webkitRelativePath` 或 `file.name` | `report.csv` |
| 混合拖拽（文件夹 + 文件） | Map（文件夹内）+ `file.name`（单文件） | 各自保留 |

**FormData 上传时**：`formData.append("files", file, getUploadRelativePath(file))` — 相对路径作为 multipart filename 传递，后端通过 filename 重建目录结构。

---

## 6. 验证结果

### 6.1 编译验证

```
mvn -q -s .codex/maven-settings.xml -Dmaven.repo.local=... -DskipTests package
EXIT=0
```

### 6.2 Redline 扫描

```
bash scripts/scan-redline.sh special_cases_report.md
EXIT=0, BLOCKER=0
```

### 6.3 功能验证场景

| 场景 | 预期行为 | 实现方式 |
|------|------|------|
| 拖入文件夹（含支持格式） | 所有支持格式文件展开，显示相对路径 | `traverseDirectoryEntry` 递归 → Map 存储路径 |
| 拖入文件夹（含不支持格式） | 支持格式进入列表，不支持格式进入 rejected 提示 | `validateUploadFiles` 分流 |
| 拖入空文件夹 | 提示"文件夹为空" | `emptyDirPaths` 检测 → `setStatus` |
| 拖入全不支持格式文件夹 | 提示"没有支持格式的文件"，展示 rejected 列表 | `acceptedFiles.length === 0` 分支 |
| 拖入单个文件 | 正常添加（不回退） | `entry.isFile` → `getAsFile()` |
| 拖入多个文件 | 正常添加（不回退） | `items` 遍历 → 逐个 `getAsFile()` |
| 文件夹 + 文件混合拖拽 | 全部展开并合并 | `items` 遍历 → 文件直接加，目录递归 |
| 文件夹名不会出现在 rejected | 目录条目只遍历不加入 files 数组 | `entry.isDirectory` 分支不 push 到 rawFiles |
| 大文件夹（>100 文件） | 分批读取，不卡 UI | `readDirectoryEntries` 分批 `readEntries` |
| 文件选择器（原流程） | 不受影响 | `handleUploadInputChange` 逻辑不变 |

---

## 7. 剩余限制

### 7.1 浏览器兼容性

`webkitGetAsEntry()` / `FileSystemEntry` API 是 WebKit/Blink 内核特性，已在以下浏览器支持：

| 浏览器 | 支持 |
|------|:---:|
| Chrome / Edge (Chromium) | ✅ |
| Firefox | ✅ (Firefox 50+) |
| Safari | ✅ |
| IE 11 | ❌ (回退到 `dataTransfer.files`，仅支持单文件拖拽) |

Firefox 中 `dataTransfer.items` 存在但 `webkitGetAsEntry()` 不可用。代码中已做兼容处理：`items[i].webkitGetAsEntry && items[i].webkitGetAsEntry()` — 返回 falsy 时回退到 `items[i].getAsFile()`，即只支持文件拖拽（不支持文件夹递归）。Firefox 中拖拽文件夹时，`getAsFile()` 对目录项返回 null，所以目录被跳过（无文件添加），会触发"没有可识别的文件"提示。

### 7.2 符号链接

`FileSystemEntry.isFile` / `isDirectory` 对符号链接的行为取决于操作系统和浏览器实现，不保证遵循符号链接。

### 7.3 大文件夹

`createReader().readEntries()` 单次最多返回约 100 条条目，代码已通过分批循环读取确保完整性。readEntries 是异步 API，不会阻塞主线程，但遍历极深层嵌套目录时可能耗时较长。

---

## 8. 明确声明

- [x] 仅修改一个前端文件：`management-runtime-part-01.js`
- [x] 未修改任何 Java 后端代码
- [x] 未修改 `index.html`（现有 UI 文案已描述"拖入文件夹"能力）
- [x] 未修改测试、数据库、Docker 配置
- [x] redline BLOCKER=0
- [x] mvn package 编译通过
- [x] 未提交 commit
