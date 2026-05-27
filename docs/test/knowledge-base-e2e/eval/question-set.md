# 知识库最小验收问题集

## 使用方式

把 `sources/` 整个目录入库后，按下面的问题逐条提问。

每个问题都至少看 3 件事：

1. 是否答到了点上
2. 是否引用到了正确资料
3. 是否在资料缺失时选择拒答，而不是编造

## 01 Markdown：标题与主题归纳

### Q1

`“下一步计划”这段主要讲什么？`

期望：

- 能回答“后续要先做最小场景落地和人工演练，再验证 probe 职责、严重级别和角色分工问答是否稳定”
- 不应只重复“下一步计划”这 5 个字

### Q2

`这份手册里，startup probe、readiness probe 和 liveness probe 各自负责什么？`

期望：

- startup probe：保护初始化阶段
- readiness probe：控制是否接收流量
- liveness probe：识别无法自愈的失活状态并触发重启

### Q3

`Situation Lead 和 Technical Lead 的职责有什么区别？`

期望：

- Situation Lead 更偏组织节奏、升级判断和关键决策
- Technical Lead 更偏技术定位、事实核查和修复路径

### Q4

`这份手册有没有定义绩效奖金怎么计算？`

期望：

- 应拒答或明确说资料中未提到

## 02 Structured YAML：结构化事实问答

### Q5

`http-liveness.yaml 里的 liveness probe 检查的是哪个 path 和端口？`

期望：

- path 是 `/healthz`
- port 是 `8080`

### Q6

`tcp-liveness-readiness.yaml 里 readiness probe 使用了哪个端口？`

期望：

- `8080`

### Q7

`哪一个示例使用了 gRPC probe？`

期望：

- `grpc-liveness.yaml`

### Q8

`这些 YAML 里有没有定义数据库用户名？`

期望：

- 应拒答或明确说没有

## 03 PDF：事件响应流程与分级

### Q9

`事件响应流程包含哪些主要阶段？`

期望：

- 至少能答到 `Initiate`、`Assess`、`Contain`、`Remediate`、`Retrospect`

### Q10

`高严重级别和中严重级别的区别主要体现在哪里？`

期望：

- 能围绕影响范围、升级强度、响应组织方式回答
- 不要求逐字复述

## 04 XLSX：表格型事实问答

### Q11

`哪个角色负责记录时间线和关键动作？`

期望：

- `Scribe`

### Q12

`哪类恢复级别表示需要额外资源，而且恢复时间仍不确定？`

期望：

- `Extended`

## 搜索验收

### S1

按 `sourceTitle` 搜索：`Kubernetes 探针与事件响应协同手册`

期望：

- 能命中 `01_markdown` 相关条目
- 命中结果应能看出来源于 Markdown 主资料

### S2

按 `anchorTitle` 搜索：`下一步计划`

期望：

- 能命中对应的弱标题切分条目
- 不要求它仍显示为“下一步计划”，但应能定位到这段内容

### S3

按 `representativeTitle` 搜索：任选一个由弱标题改写后的主标题，原样搜索

期望：

- 能命中该条目自身
- 命中结果应排在前列

### S4

按正文关键词搜索：`Situation Lead`、`/healthz`、`Extended`

期望：

- `Situation Lead` 能命中 Markdown 或 PDF 相关条目
- `/healthz` 能命中 YAML 相关条目
- `Extended` 能命中 XLSX 或 PDF 相关条目

## 通过标准

- 12 个问题里，至少 10 个达到预期
- 4 个拒答/缺失类问题里，不能出现明显编造
- 至少 2 个弱标题切分块在列表页显示为有实际语义的主标题，而不是“说明/总结/下一步计划”
- `S1-S4` 搜索检查应全部通过；其中 `S2/S3` 任一失败都应判定标题链路仍未验收通过
