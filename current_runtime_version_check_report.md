# 当前本地运行版本核查报告

**日期**: 2026-05-23
**分支**: `codex/qa-polish`
**核查人**: agentD（只读核查，未修改任何文件）

---

## 一、当前服务运行状态

### 1.1 进程信息

| 项目 | 值 |
|------|-----|
| PID | 90008 |
| 父进程 | 89784 (Maven `spring-boot:run`) |
| 启动时间 | 2026-05-23 16:55:41 |
| 运行时长 | ~3 小时 |
| 启动命令 | `LatticeApplication --spring.profiles.active=local-dev` |
| profile | `local-dev` |
| JVM | Azul JDK 21.0.9 |
| 内存 | ~293 MB |
| 线程数 | 56 |
| 进程状态 | SN (Sleeping, Niced) |

### 1.2 端口监听状态

| 端口 | 预期用途 | 实际状态 |
|------|---------|---------|
| 18082 | HTTP (local-dev 默认) | **未监听** |
| 8080 | HTTP (默认) | **未监听** |
| 35729 | Spring DevTools | **监听中** |

### 1.3 HTTP 端点可达性

- `http://127.0.0.1:18082/actuator/health` — 无法连接（exit code 7）
- `http://127.0.0.1:8080/actuator/health` — 无法连接（exit code 7）

### 1.4 结论：服务进程存活但 Web 容器未启动

JVM 进程正常运行（56 线程、293 MB 内存），但嵌入式 Web 服务器（Tomcat）未成功绑定 HTTP 端口。
仅有 DevTools 端口 35729 在监听。**无法通过浏览器访问任何页面或 API。**

可能原因（按可能性排序）：
1. 启动时端口 18082 被占用，Tomcat 绑定失败后未重试
2. 数据库连接失败导致 Web 上下文初始化中断
3. 其他 Spring Bean 初始化异常导致 Web 容器未完成启动

---

## 二、前端关键修复代码层面核查

> 由于服务 Web 端口不可达，以下核查基于源代码静态分析，无法通过 HTTP 响应验证运行态。

### 2.1 已删除的治理 UI 元素

| 检查项 | 搜索范围 | 结果 |
|--------|---------|------|
| `hotspot-refresh-status` | `src/main/resources/static/admin/**` | **已删除** — 0 匹配 |
| `governance-explain-panel` | `src/main/resources/static/admin/**` | **已删除** — 0 匹配 |
| `重新分析关注内容` | `src/main/resources/static/admin/**` | **已删除** — 0 匹配 |
| `requiresResultVerification:true">需关注` | `src/main/resources/static/admin/**` | **已删除** — 0 匹配 |
| `.governance-explain-*` CSS 规则 | `admin.css` | **已删除** — 0 匹配 |
| `renderGovernanceExplainPanel` 函数 | `management-runtime-part-04.js` | **已删除** — 0 匹配 |
| `buildGovernanceExplainContent` 函数 | `management-runtime-part-04.js` | **已删除** — 0 匹配 |
| `syncGovernanceExplainPanel` 函数 | `management-runtime-part-04.js` | **已删除** — 0 匹配 |
| `renderHotspotRefreshStatus` 函数 | `management-runtime-part-04.js` | **已删除** — 0 匹配 |
| `buildHotspotRefreshStatusText` 函数 | `management-runtime-part-04.js` | **已删除** — 0 匹配 |

### 2.2 已新增/保留的元素

| 检查项 | 位置 | 结果 |
|--------|------|------|
| `开发诊断信息` | `index.html:533-534` | **已存在** |
| `技术信息` 独立标题 | 全量 admin static | **已删除**（仅作为 `开发诊断信息` 内部出现） |
| 处理历史 Tab 入口 | `index.html:110` | **已存在** |
| 空段过滤逻辑 (`flushParagraph.trim()`) | `ask-runtime-part-02.js` | **已存在** — `if (!_content.trim())` 在 `flushParagraph` 中 |
| `去提问` 按钮 | `index.html` | **保留** |

### 2.3 结论：前端删除/替换项代码层面全部生效

源代码中已不再包含任何待删除的治理 UI 元素。新的 `开发诊断信息`、处理历史 Tab、空段过滤逻辑均已存在于源代码中。

---

## 三、后端质量检查阶段文案修复核查

### 3.1 新文案字符串

| 文案 | 文件 | 行号 | 触发条件 |
|------|------|------|---------|
| `正在检查内容质量` | `AdminCompileReviewSummaryService.java` | 95 | `RUNNING` + review 进行中 |
| `已发现待修复问题，正在自动修正` | `AdminCompileReviewSummaryService.java` | 93 | `RUNNING` + fix 已触发 |
| `已根据检查结果完成修正` | `AdminCompileReviewSummaryService.java` | 101 | `SUCCEEDED` + fix 完成 |
| `未发现需要修复的问题` | `AdminCompileReviewSummaryService.java` | 104 | `SUCCEEDED` + 无问题 |
| `质量检查后需要人工确认` | `AdminCompileReviewSummaryService.java` | 98 | `SUCCEEDED` + 需人工确认 |
| `质量检查已完成` | `AdminCompileReviewSummaryService.java` | 106 | 默认兜底 |

### 3.2 旧文案清理

| 旧文案 | 搜索结果 |
|--------|---------|
| `已根据检查结果修正内容`（旧终态措辞） | **已在源代码中完全删除** |

### 3.3 方法签名验证

- `buildStepDetail(summary, displayStatus)` — `displayStatus` 参数已新增（`AdminCompileReviewSummaryService.java:87`）
- `enrichReviewStepDetail(progressSteps, compileReviewSummary, displayStatus)` — `displayStatus` 参数已透传（`AdminProcessingTaskService.java:378-383`）
- 两处调用点（`toSourceSyncTask` / `toStandaloneCompileTask`）均已传入 `displayStatus`/`derivedStatus`

### 3.4 编译产物时间戳

| 文件 | 修改时间 | Unix Timestamp |
|------|---------|----------------|
| `AdminCompileReviewSummaryService.java` (源) | May 23 19:50:43 | 1779537043 |
| `AdminCompileReviewSummaryService.class` (编译) | May 23 19:52:14 | 1779537134 |

编译产物比源文件晚 ~91 秒 → **确认已重新编译，包含最新修复**。

### 3.5 结论：后端文案修复代码层面完全生效

- `RUNNING` 状态下不会再出现终态文案
- 旧措辞 `已根据检查结果修正内容` 已从源代码中彻底删除
- `buildStepDetail` 方法签名已增加状态感知能力
- 编译产物已更新

---

## 四、前端空指针修复核查

### 4.1 修复代码确认

`management-runtime-part-03.js` 中 `clearArticleDetail` 函数：

```javascript
// 修复后 — null guard 已就位
var _techInfo = document.getElementById("article-technical-info");
if (_techInfo) {
    _techInfo.innerHTML = "";
}
```

`article-technical-info` 的直接 `innerHTML` 写入已被 null guard 包裹。

### 4.2 编译产物时间戳

| 文件 | 修改时间 | Unix Timestamp |
|------|---------|----------------|
| `management-runtime-part-03.js` (源) | May 23 19:43:07 | 1779536587 |
| `management-runtime-part-03.js` (target) | May 23 19:44:47 | 1779536687 |

编译产物比源文件晚 ~100 秒 → **确认已复制到 target/classes**。

### 4.3 剩余风险分析

| 风险场景 | 是否仍可能触发 | 说明 |
|---------|---------------|------|
| 页面首次加载 → `clearArticleDetail()` | **已修复** | null guard 拦截 |
| 文章切换 → 先 clear 再 render | **已修复** | null guard 拦截 |
| Tab 切换回"已入库内容" | **已修复** | null guard 拦截 |
| 其他 `article-technical-info` 写入点 | **理论上已不存在** | 该元素仅由 `renderArticleDetail` 动态创建；`clearArticleDetail` 是唯一清空入口 |

### 4.4 结论：顶部 `Cannot set properties of null` 报错理论上已修复

`clearArticleDetail()` 中对 `article-technical-info` 的所有写入操作已被 null guard 保护。
在该修复正确部署的前提下，页面顶部不应再出现此报错。

---

## 五、综合结论

### 5.1 运行态结论

**运行的是新版本代码，但 Web 服务不可达，无法通过浏览器验证。**

具体分解：

1. **代码层面**：所有 4 项修复（治理 UI 删除、质量检查文案、空指针 null guard、前端替换项）在源代码和编译产物中均已生效。
2. **编译层面**：`target/classes` 中的 `.class` 文件和 `.js` 文件时间戳均晚于对应源文件，确认在最新代码修改后执行过构建。
3. **运行层面**：`spring-boot:run` 启动的 JVM 进程存活但 **Web 容器未成功绑定 HTTP 端口**，无法通过浏览器或 curl 访问任何页面。

### 5.2 如果浏览器仍看到旧内容，最可能原因排序

| 优先级 | 原因 | 判断依据 |
|--------|------|---------|
| **1** | **服务未重启到最新构建产物** | 当前进程已运行 3 小时，如果代码修改发生在进程启动之后，需要重启才能加载新编译的类文件。时间线：源码最后修改 ~19:43-19:50，class 编译 ~19:44-19:52，但进程启动在 16:55。修改发生在进程启动 **之后**，需要重启才能生效。 |
| 2 | **服务 HTTP 端口未启动** | 当前进程根本没有可访问的 Web 端口，浏览器无论访问什么都看不到。必须先解决 Web 容器启动问题。 |
| 3 | 浏览器缓存旧 JS/CSS | 即使服务正常，浏览器可能缓存了旧版本的静态资源。DevTools 模式下应自动热更新，但如果 DevTools 连接断开，需要手动强制刷新（Ctrl+Shift+R）。 |
| 4 | 旧任务展示的是历史快照数据 | 对于"质量检查"步骤的文案，如果是 3 小时前启动的旧任务，其步骤文案可能在任务运行时已经被写入数据库。新启动的任务才会使用新文案逻辑。 |

### 5.3 最终判定

```
运行的是新版本代码，但因进程启动时间早于代码修改时间，
当前运行中的 JVM 实际加载的是旧版本的类文件。
需要重启服务才能使修复生效。
此外，当前服务 Web 端口不可达，即使重启也可能需要排查 Web 容器启动失败的原因。
```

### 5.4 建议的下一步

1. **停掉当前进程**：`kill 90008`（以及父 Maven 进程 `kill 89784`）
2. **确认端口空闲**：`lsof -i :18082` 确保无残留进程占用
3. **重新启动**：`./scripts/run-local-dev.sh` 或 `mvn -s .codex/maven-settings.xml spring-boot:run -Dspring-boot.run.profiles=local-dev`
4. **等待 `Started LatticeApplication` 日志出现**
5. **确认端口监听**：`lsof -i :18082`
6. **验证健康检查**：`curl http://127.0.0.1:18082/actuator/health`
7. **浏览器强制刷新**：打开 `http://127.0.0.1:18082/admin/index.html`，Ctrl+Shift+R 跳过缓存
8. 进入"已入库内容"页验证：无顶部报错、无治理 UI、有"开发诊断信息"折叠区
9. 触发一次新编译任务验证：质量检查步骤文案在 RUNNING 态和终态均正确
