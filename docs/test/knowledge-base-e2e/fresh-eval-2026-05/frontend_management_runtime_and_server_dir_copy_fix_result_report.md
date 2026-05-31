# 前端 management.js 运行时语法错误与 SERVER_DIR 文案残留修复报告

## 根因

### 问题 1：management.js 拼接后语法错误

`management-runtime-part-01.js` 中 `consumeInitialRoute()` 函数存在多余的闭合花括号。该函数尾部代码为：

```javascript
        const nextQuery = params.toString();
            const nextUrl = ...;     // 缩进不一致（多 4 空格）
            if (window.history...) {
                ...
            }
        }       // ← 多余的闭合花括号（8 空格缩进）
    }           // ← 函数正常结束（4 空格缩进）
```

中间多出的 `}` 提前关闭了 `consumeInitialRoute`，导致其后的 `function normalizeKnowledgeTab(tabName)` 出现在非法语法上下文，Node 执行时报 `SyntaxError: Unexpected token 'function'`。

该额外花括号使 IIFE 内花括号数量不匹配（5 开 6 闭），`new Function(...)` 拼接后无法通过 Node `vm.runInNewContext` 解析。

### 问题 2：SERVER_DIR 文案残留

项目已移除 SERVER_DIR 资料源，但前端 HTML 和 JS 中仍有多处提及"服务器目录资料源""接部署机目录"等文案，分布在：
- `index.html` — FAQ 条目"什么时候该去系统配置"
- `settings.html` — 高级维护面板标题、描述、FAQ
- `settings-page-runtime-part-02.js` — `deriveMaintenanceOverview()` 函数的 overview card description

## 修改文件清单

| 文件 | 修改说明 |
|---|---|
| `src/main/resources/static/admin/modules/management-runtime-part-01.js` | 修复 `consumeInitialRoute()` 花括号问题：删除多余的 `}`，修正 `const nextUrl` 缩进 |
| `src/main/resources/static/admin/index.html` | 删除 FAQ 中的"服务器目录资料源"文案 |
| `src/main/resources/static/admin/settings.html` | 共 6 处修改：删除/替换"服务器目录""接部署机目录""服务器目录资料源"文案 |
| `src/main/resources/static/admin/modules/settings-page-runtime-part-02.js` | `deriveMaintenanceOverview()` 中删除"服务器目录资料源"文案 |

## syntax error 修复说明

**修改位置**：`management-runtime-part-01.js` 中 `consumeInitialRoute()` 函数体尾部

**修改前**（转义后）：
```javascript
        const nextQuery = params.toString();
            const nextUrl = window.location.pathname + ...;
            if (window.history && ...) {
                window.history.replaceState(...);
            }
        }       // 多余花括号 → 导致 normalizeKnowledgeTab 出现在非法上下文
    }
```

**修改后**：
```javascript
        const nextQuery = params.toString();
        const nextUrl = window.location.pathname + ...;
        if (window.history && ...) {
            window.history.replaceState(...);
        }
    }
```

变更：移除多余 `}`、统一 `const nextUrl` 缩进为 8 空格（与同级代码一致）。

## SERVER_DIR 文案清理清单

### index.html (1 处)
- `:693` "服务器目录资料源" → 删除，改为仅提"模型与向量、OCR"

### settings.html (6 处)
- `:466` "服务器目录资料源和知识切片重建" → "和知识切片重建"
- `:471` "接部署机目录" → 删除该分句
- `:542` `<h2>服务器目录与知识切片</h2>` → `<h2>知识切片重建</h2>`
- `:543` "接部署机目录" → 删除，文案收敛为仅提切片策略
- `:572` "服务器目录资料源" → 删除
- `:632-634` FAQ 标题和正文中的"服务器目录资料源""接部署机目录" → 删除

### settings-page-runtime-part-02.js (1 处)
- `deriveMaintenanceOverview()` description："服务器目录资料源和知识切片重建" → "和知识切片重建"

### 文案收敛结果
- 普通资料导入：UPLOAD
- 仓库资料导入：GIT
- 高级维护：检索调参、知识切片重建等低频动作

## 定向测试结果

### ManagementJsRuntimeTests

```
mvn -Dtest=ManagementJsRuntimeTests#shouldVerifyRunFallbackAndErrorPresentationViaNode test
```

结果：**BUILD SUCCESS** — Tests run: 1, Failures: 0, Errors: 0, Skipped: 0

### 前端残留扫描

```bash
# 扫描 1：中文文案
rg -n "服务器目录|接部署机目录|服务器目录资料源" src/main/resources/static/
# 结果：无匹配

# 扫描 2：代码级引用
rg -n "SERVER_DIR|server-dir|serverDir|create-server-source|createServerSourceAndSync" src/main/resources/static/
# 结果：无匹配
```

两项扫描均为零残留。

## 是否仍有未处理风险

无。本次修改仅涉及前端静态文案和 JS 语法修正，未触及后端代码、DTO、Service、Controller、数据库 schema、redline 脚本或 allowlist。

工作台初始化流程（`refreshPage()` → `refreshSummary()` + `fetchHealthStatus()` + `loadProcessingTasks()` + `loadSources()`）的代码逻辑本身未变更，修复语法错误后应能正常执行。服务健康调用 `/actuator/health`、导入进度调用 `/api/v1/admin/processing-tasks?limit=50` 的 API 路径未变，仅因此前 JS 文件无法完整解析而导致这些调用从未被执行。

建议后续做一次浏览器 smoke：打开 `/admin` 和 `/admin/settings` 确认无 console syntax error、服务健康状态正常更新、导入进度可显示空态或任务列表、设置页不再出现服务器目录相关文案。
