# Admin Dashboard Usability Wording Alignment Fix Result Report

## 本轮目标

- 统一后台管理页“待人工确认”相关用户主文案。
- 保证真实页面用户可见区域不再出现 `Reviewer` 主文案。
- 避免“待确认草稿 / 质量检查说明 / Reviewer 判定”等同一语义多套说法混用。

## 最终统一采用的文案

- 主状态 / 入口 / 队列名称：`待人工确认`
- 草稿队列名称：`待人工确认草稿`
- 说明区标题：`待人工确认说明`
- 质量检查原因说明：`质量检查需要人工确认`
- 空态引导：`从左侧选择一条待人工确认草稿后，可查看正文、来源和待人工确认说明。`

## 修改文件

- `src/main/resources/static/admin/index.html`
- `src/main/resources/static/admin/compile-review-queue.js`
- `src/main/resources/static/admin/modules/management-runtime-part-02.js`
- `src/main/resources/static/admin/modules/management-runtime-part-03.js`
- `src/main/resources/static/admin/modules/management-runtime-part-04.js`
- `src/main/resources/static/admin/modules/management-runtime-part-05.js`
- `src/test/java/com/xbk/lattice/api/admin/ManagementJsRuntimeTests.java`

## 真实页面已统一位置

- “当前处理任务”说明：统一为“待人工确认的任务”。
- “待人工确认”面板说明：统一为“质量检查需要人工确认的草稿会在这里处理”。
- 刷新按钮：统一为“刷新待人工确认”。
- 队列状态：统一为“正在加载待人工确认草稿 / 当前没有待人工确认草稿 / 待人工确认草稿已加载”。
- 队列标题：统一为“待人工确认草稿”。
- 详情空态：统一为“待人工确认说明”。
- 运行态状态标签：`WAIT_CONFIRM`、`NEEDS_HUMAN_REVIEW`、`NEEDS_MANUAL_CONFIRMATION` 统一展示为“待人工确认”。
- 已入库内容筛选项 `needs_human_review` 统一展示为“待人工确认”。

## Reviewer 残留结论

- 真实浏览器页面检查：用户可见正文不再出现 `Reviewer`。
- 静态页面与运行时模块检查：未发现 `Reviewer 判定`、`Reviewer 判定原因`、`Reviewer 判定需要人工确认` 等用户主文案。
- 测试代码中仍保留 `Reviewer` 负向断言，用于防止旧文案回归；这不是用户主文案残留。

## 验证

- 真实浏览器验证：通过临时静态服务打开 `http://127.0.0.1:18090/admin/index.html`，切到“当前处理任务”，DOM 与可见文案检查通过；验证后临时服务已关闭。
- stale wording 扫描：`Reviewer / 质量检查说明 / 待确认草稿 / 需人工复核` 等旧主文案在目标前端文件中无残留。
- redline：`bash scripts/scan-redline.sh special_cases_report.md`，`BLOCKER：0`。
- 相关前端运行态测试：`mvn -s .codex/maven-settings.xml -Dtest=ManagementJsRuntimeTests test`，通过，`Tests run: 3, Failures: 0, Errors: 0`。

## 是否建议再交给 agentD

建议可以交给 agentD 做最后一次只读验收，但不需要再按单一精确字符串卡点；建议验收口径改为“用户可见主文案是否统一、是否无 Reviewer 主文案、相关测试与 redline 是否通过”。
