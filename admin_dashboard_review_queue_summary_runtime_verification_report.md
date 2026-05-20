# Admin Dashboard Review Queue Summary Runtime Verification Report

## 1. Redline

- 命令：`bash scripts/scan-redline.sh special_cases_report.md`
- 结果：通过，退出码 0
- `BLOCKER=0`
- `REVIEW=1863`
- `ALLOWLIST=244`

## 2. 验证环境

- 应用地址：`http://127.0.0.1:8080`
- 后台页面：`http://127.0.0.1:8080/admin`
- 启动方式：`SERVER_PORT=8080 ./scripts/run-local-dev.sh`
- 数据库：现有 `ai-rag-knowledge` / `lattice` schema
- 本轮未清库、未重建知识库、未制造新的待确认草稿。

## 3. 数据库计数

只读 SQL：

```sql
select count(*)
from lattice.compile_article_review_queue
where review_status = 'needs_human_review';
```

结果：

- `needs_human_review count = 0`

## 4. Overview API 结果

接口：

```text
GET http://127.0.0.1:8080/api/v1/admin/overview
```

关键字段：

- `status.humanReviewDraftPendingCount = 0`
- 响应中已包含字段 `humanReviewDraftPendingCount`

同一响应中的 `status` 摘要：

```json
{
  "articleCount": 2,
  "sourceFileCount": 4,
  "reviewPendingArticleCount": 0,
  "humanReviewDraftPendingCount": 0
}
```

## 5. 一致性结论

- 数据库 `needs_human_review count`：`0`
- Overview `status.humanReviewDraftPendingCount`：`0`
- 是否一致：是。

## 6. 前端状态摘要

浏览器打开后台首页后，状态摘要区域可见。

页面状态摘要中已显示卡片：

```text
待人工确认草稿
0
当前没有待发布草稿
```

结论：前端已真实展示“待人工确认草稿”卡片，并使用 overview 返回值渲染当前 0 场景。

## 7. 正数引导验证

当前运行库 `needs_human_review count = 0`，不存在待确认草稿。

因此“有待确认草稿时是否引导到当前处理任务 / 待人工确认”的正数场景本轮不适用。页面当前 help card 显示正常可用状态；没有出现“去待人工确认”的正数引导，这是 0 场景下的合理表现。

## 8. 禁止项确认

- 是否修改代码：否。
- 是否修改 `src/main/java/**`：否。
- 是否修改 `src/test/java/**`：否。
- 是否修改 `src/main/resources/**`：否。
- 是否修改 `static/admin/**`：否。
- 是否修改 `scripts/scan-redline.sh`：否。
- 是否修改 redline allowlist：否。
- 是否清库：否。
- 是否重建知识库：否。
- 是否跑 SWIP eval：否。
- 是否提交代码：否。

仅新增本验证报告；`special_cases_report.md` 由 redline 扫描命令按既有流程更新。

## 9. 是否建议提交

建议提交前质量复核后提交。

本轮 runtime 验证未发现阻塞：API 字段存在，数据库计数一致，后台状态摘要卡片可见，0 场景显示合理。

