# Admin 前端人工验收启动报告

- 生成时间：2026-05-22
- 执行 Agent：agentD（只读——启动服务，不改代码）
- 分支：`codex/qa-polish`
- 代码修改：否

---

## 1. 启动前环境检查

| 检查项 | 预期 | 结果 | 状态 |
|--------|------|------|------|
| JDK 版本 | 21 | `openjdk 21.0.9 2025-10-21 LTS` (Zulu) | 通过 |
| Docker `vector_db` | 运行中 | 运行中 | 通过 |
| Docker `redis` | 运行中 | 运行中 | 通过 |
| 端口 18082 | 预期空闲 | 未被占用 | 通过 |

---

## 2. 启动操作

### 2.1 启动方式

新启动（非复用已有实例）。

```bash
./scripts/run-local-dev.sh
```

脚本自动设定：
- profile：`local-dev`
- 端口：`18082`
- 数据库：`jdbc:postgresql://127.0.0.1:5432/ai-rag-knowledge?currentSchema=lattice`
- Redis：`127.0.0.1:6379`

### 2.2 关键参数

| 项目 | 值 |
|------|-----|
| 启动命令 | `mvn -q -s .codex/maven-settings.xml spring-boot:run -Dspring-boot.run.profiles=local-dev` |
| Java 进程 PID | `90353` |
| Maven 包装进程 PID | `90122` |
| 未执行清库 | 是 |
| 未执行 `reset-lattice-schema.sh` | 是 |
| 未执行 `mvn clean` | 是（复用已有 target/classes） |

---

## 3. 服务访问验证

### 3.1 Health Check

```bash
curl http://127.0.0.1:18082/actuator/health
```

响应：

```json
{"status":"UP"}
```

### 3.2 Admin 页面

```bash
curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:18082/admin/index.html
```

HTTP 状态码：**200**

页面标题：`邪修智库｜工作台`

---

## 4. 浏览器访问地址

```
http://127.0.0.1:18082/admin/index.html
```

---

## 5. 用户人工肉眼验收清单

以下清单来自 `admin_article_detail_review_metric_ux_fix_revision_report.md` 和 `admin_current_workspace_frontend_static_and_small_e2e_gate_report.md`，由用户在浏览器中逐项验收。

### 5.1 待人工确认说明（Review Queue）

- [ ] 选中一条待人工确认草稿后，问题列表在卡片内部滚动，不撑满页面
- [ ] 标题显示"待人工确认说明（共 N 个问题）"
- [ ] severity 显示中文（高风险/中风险/低风险）
- [ ] category 显示中文（来源不一致/低可追溯/OCR 低置信）
- [ ] description/suggestion 中文兜底文案正常

### 5.2 关键词降噪

- [ ] 打开有多个关键词的文章详情，默认可见区只展示 ≤6 个普通关键词
- [ ] 技术/调试关键词（含文件路径、扩展名等）默认折叠在"还有 N 个关键词（含技术/调试）"中
- [ ] 展开/收起 toggle 正常工作

### 5.3 关联信息低调展示

- [ ] dependsOn/related 以"关联信息"辅助区展示（小字号、muted 颜色），不抢正文

### 5.4 复核历史空状态

- [ ] 暂无复核历史时，空状态区域使用浅色背景、文字可读

### 5.5 治理指标卡片点击反馈

- [ ] 点击"热点待抽检"卡片 → 跳转到已入库内容 Tab，状态栏显示"已切换到已入库内容，并筛选热点待抽检"
- [ ] 点击"待人工确认草稿"卡片 → 跳转到当前处理任务 Tab，定位到待人工确认草稿列表
- [ ] 点击 count=0 的卡片（如"用户反馈风险"）→ 无反应、无"去处理 →"
- [ ] button 卡片与 div 卡片 hover/focus/普通态视觉一致

### 5.6 待分析提问

- [ ] 即使 pendingQueryCount > 0（当前为 27），也不显示"去处理 →"，不可点击

### 5.7 处理历史 Tab

- [ ] "处理历史"Tab 可见
- [ ] 点击"处理历史"Tab，无 ReferenceError，历史列表正常加载
- [ ] 筛选按钮（全部/已入库/失败/已跳过）可切换
- [ ] "查看详情"可跳转

### 5.8 二次渲染稳定性

- [ ] 反复切换文章 A→B→A，无 h4 重复、无 details 嵌套

---

## 6. 明确标注

| 标注 | 状态 |
|------|------|
| 未执行清库 | 确认 |
| 未执行 `reset-lattice-schema.sh` | 确认 |
| 未执行全链路 query 验证 | 确认 |
| 未执行全链路 compile 验证 | 确认 |
| 未覆盖浏览器视觉验收 | 由用户完成 |
| 数据库为已有开发库，非空白库 | 确认 |

---

## 7. 结论

服务已在 `http://127.0.0.1:18082/admin/index.html` 正常运行，health check 返回 `UP`，admin 页面返回 HTTP 200。用户可以立即在浏览器中按第 5 节清单逐项验收。
