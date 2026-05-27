# 知识库标题优化真实验收报告

## 1. 验收环境
- 验收日期：2026-05-25 14:41 CST
- JDK：21.0.9
- Maven：3.6.3
- 应用端口：18082
- Profile：`local-dev`
- Schema：`lattice`
- PostgreSQL：`vector_db` 容器，`ai-rag-knowledge`
- Redis：`redis` 容器
- 启动方式：`./scripts/run-local-dev.sh`
- Chat 网关：`http://127.0.0.1:8888/v1`

## 2. 清库与重建结果
- 本轮沿用 `P4-A` 已清库重建后的验收环境
- 在 `P4-B` 中补做了人工确认发布
- 当前系统健康检查：`/actuator/health = UP`
- 当前知识状态：
  - 正式文章：`6`
  - 待人工确认草稿：`0`
  - 源文件：`6`

## 3. 模型绑定与向量配置
- compile / query 已稳定走兼容当前应用的非流式 JSON Chat 路由
- `stream=false` 返回标准 `chat.completion` JSON
- `stream=true` 返回标准 SSE
- 当前 query 结果已可稳定返回引用与 queryId

## 4. 入库与编译结果
- `P4-A` compile job：`d8cb80ae-9058-4ac5-b582-17d0842185c5`
- compile 链路：`writer -> reviewer -> fixer -> reviewer` 已真实跑通
- 人工确认发布已完成，4 条待确认草稿均已通过：
  - `Kubernetes 探针与事件响应协同手册`
  - `tcp liveness readiness`
  - `incident response reference lite`
  - `incident checklist`
- 当前正式文章共 6 篇：
  - `Kubernetes 探针与事件响应协同手册`
  - `tcp liveness readiness`
  - `incident response reference lite`
  - `incident checklist`
  - `grpc liveness`
  - `http liveness`

## 5. 标题验收结果
- 标题优化目标已通过：
  - 不再出现 `01 Markdown / 02 Structured / === Page / 04 Office` 这类目录名占位稿
  - 4 篇原待确认稿已以可读主题标题进入正式知识条目
- 代表性样本：
  - Markdown：`Kubernetes 探针与事件响应协同手册`
  - YAML：`tcp liveness readiness`
  - PDF：`incident response reference lite`
  - XLSX：`incident checklist`
- 结论：
  - 标题链路本身已进入最终正式文章层
  - 但 `anchorTitle` 维度的检索能力仍未完全达标，见搜索结果

## 6. 搜索验收结果

### S1：按 `sourceTitle` 搜索 `Kubernetes 探针与事件响应协同手册`
- 结果：PASS
- 现象：Top1 命中正式文章 `Kubernetes 探针与事件响应协同手册`

### S2：按 `anchorTitle` 搜索 `下一步计划`
- 结果：FAIL
- 现象：Top1 命中 `Kubernetes 探针与事件响应协同手册`，没有稳定定位到弱标题切分块
- 结论：当前更像文档级主题命中，而不是切分标题级命中

### S3：按 `representativeTitle` 搜索 `incident checklist`
- 结果：PASS
- 现象：Top1 命中正式文章 `incident checklist`

### S4：按正文关键词搜索
- `Situation Lead`
  - 结果：PASS
  - 说明：Top3 中包含 `incident checklist`、`incident response reference lite`、`Kubernetes 探针与事件响应协同手册`
- `/healthz`
  - 结果：PASS
  - 说明：Top1 命中 `http liveness`
- `Extended`
  - 结果：PASS
  - 说明：Top2 命中 `incident response reference lite`、`incident checklist`

### 搜索结论
- `S1 / S3 / S4` 通过
- `S2` 失败
- 按题集标准，`S2/S3` 任一失败都应判定标题链路未完全验收通过

## 7. 问答验收结果

### Q1 `“下一步计划”这段主要讲什么？`
- 结果：PASS
- 评价：抓到了“先验证关键问题是否能稳定回答”的主线，但没有完整提到“最小场景落地 + 人工演练”

### Q2 `这份手册里，startup probe、readiness probe 和 liveness probe 各自负责什么？`
- 结果：PASS
- 评价：三类 probe 的职责已经回答完整

### Q3 `Situation Lead 和 Technical Lead 的职责有什么区别？`
- 结果：PASS
- 评价：回答到了组织/决策 vs 技术事实/修复路径的差异

### Q4 `这份手册有没有定义绩效奖金怎么计算？`
- 结果：PASS
- 评价：能够明确拒答，没有再误答成 YAML 内容

### Q5 `http-liveness.yaml 里的 liveness probe 检查的是哪个 path 和端口？`
- 结果：PASS
- 评价：正确回答 `/healthz` 和 `8080`

### Q6 `tcp-liveness-readiness.yaml 里 readiness probe 使用了哪个端口？`
- 结果：FAIL
- 评价：仍然退化到 fallback 文案，没有给出 `8080`

### Q7 `哪一个示例使用了 gRPC probe？`
- 结果：PASS
- 评价：正确回答 `grpc liveness`

### Q8 `这些 YAML 里有没有定义数据库用户名？`
- 结果：PASS
- 评价：明确回答没有

### Q9 `事件响应流程包含哪些主要阶段？`
- 结果：PASS
- 评价：答到了 `Initiate / Assess / Contain / Remediate / Retrospect`

### Q10 `高严重级别和中严重级别的区别主要体现在哪里？`
- 结果：PASS
- 评价：围绕影响范围、系统性风险、响应强度给出了有效差异说明

### Q11 `哪个角色负责记录时间线和关键动作？`
- 结果：PASS
- 评价：正确回答 `Scribe`

### Q12 `哪类恢复级别表示需要额外资源，而且恢复时间仍不确定？`
- 结果：PASS
- 评价：回答到了 `Extended`，但引用仍偏弱

### 问答结论
- 总体：`11 / 12` 通过
- 已达到题集要求的 `>= 10 / 12`
- 唯一明确失败项：`Q6`

## 8. 截图或证据摘要
- `/api/v1/admin/overview`
  - `articleCount = 6`
  - `humanReviewDraftPendingCount = 0`
- `/api/v1/admin/articles?count=20`
  - 6 篇 `passed` 正式文章全部可见
- `/api/v1/admin/compile/review-queue?limit=20`
  - `total = 0`
- 搜索关键结果：
  - `Kubernetes 探针与事件响应协同手册` -> Top1 `Kubernetes 探针与事件响应协同手册`
  - `incident checklist` -> Top1 `incident checklist`
  - `下一步计划` -> Top1 `Kubernetes 探针与事件响应协同手册`

## 9. 失败清单与风险清单
- 失败项 1：`S2` 未通过，按弱标题 `anchorTitle` 搜索仍不能稳定定位到切分块
- 失败项 2：`Q6` 未通过，`tcp-liveness-readiness.yaml` 的 readiness 端口提取仍不稳定
- 风险项 1：`Q12` 虽然答到了 `Extended`，但引用仍偏弱，后续应继续加强表格型证据绑定
- 风险项 2：当前搜索更偏主题文章命中，弱标题切分后的专门定位能力仍不足

## 10. 最终结论
- 结论：PASS WITH RISKS
- 已通过项：
  - 标题优化已进入正式知识条目
  - 正式文章已全部发布
  - 问答达到 `11 / 12`
  - 大部分搜索场景已恢复
- 残余风险：
  - `anchorTitle` 搜索仍未通过
  - `Q6` 仍存在结构化端口提取 / 回答退化问题
- 是否支持继续下一轮真实资料扩样：可以，但建议先优先修 `S2` 和 `Q6`
