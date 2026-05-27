# 知识库最小真实验收资料包

这套资料包用于验证 4 件事：

1. 资料是否能稳定入库
2. 标题是否能代表切分块整体内容
3. 搜索是否能按主标题、来源标题、切分标题、关键词定位
4. 问答是否基于证据，不会对无证据问题乱答

## 目录

- `sources/01_markdown/`
  - 长 Markdown 验收样例
  - 重点验证弱标题切分后的标题生成质量
- `sources/02_structured/`
  - Kubernetes 官方 YAML 示例
  - 重点验证结构化配置事实问答和引用
- `sources/03_pdf/`
  - 本地生成的 PDF 验收样例
  - 重点验证 PDF 标题提取、正文抽取和问答
- `sources/04_office/`
  - 本地生成的 XLSX 验收样例
  - 重点验证 Office 抽取和表格事实问答
- `eval/`
  - 推荐问题集和通过标准

## 推荐入库方式

直接把 `sources/` 整个目录作为一组资料源入库。

如果你想分批看效果，建议顺序是：

1. 先只入库 `01_markdown`
2. 再追加 `02_structured`
3. 最后追加 `03_pdf` 和 `04_office`

这样更容易看清是哪一类资料影响了结果。

## 这套资料的设计意图

### 01 Markdown

这份长文档故意包含：

- `说明`
- `总结`
- `下一步计划`

这类弱标题，用来验证当前标题优化是否能把它们改写成更能代表整段内容的知识条目标题。

### 02 YAML

这组文件来自 Kubernetes 官方示例，字段清晰，适合问：

- 哪个端口被探测
- 哪种 probe 使用了 gRPC
- `failureThreshold` 或 `initialDelaySeconds` 是多少

### 03 PDF

PDF 内容围绕事件响应的阶段、分级、角色和处理节奏，适合问：

- 事件响应包含哪些阶段
- 高/中/低严重级别怎么区分
- Situation Lead 和 Technical Lead 分别做什么

### 04 XLSX

XLSX 内容围绕检查项、角色、严重级别和恢复性判断，适合问：

- 哪类场景需要先进入 containment
- 哪个角色负责记录
- 哪类恢复级别表示额外资源已介入但恢复时间仍不确定

完整验收时，还应额外覆盖搜索链路：

- 按 `sourceTitle` 搜索是否能命中 Markdown 主资料
- 按 `anchorTitle` 搜索是否能命中弱标题切分块
- 按 `representativeTitle` 搜索是否能命中对应条目
- 按正文关键词搜索是否能命中 YAML / PDF / XLSX 条目

## 官方来源说明

这套资料包不是随机拼的，主要参考了以下公开资料：

- Kubernetes Probes 概念文档
  - https://kubernetes.io/docs/concepts/workloads/pods/probes/
- Kubernetes Probes 配置文档
  - https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/
- Kubernetes 官方示例 YAML
  - https://kubernetes.io/examples/pods/probe/http-liveness.yaml
  - https://kubernetes.io/examples/pods/probe/tcp-liveness-readiness.yaml
  - https://kubernetes.io/examples/pods/probe/grpc-liveness.yaml
- Login.gov Incident Response Guide
  - https://handbook.login.gov/articles/incident-response-guide.html
- Microsoft Incident Response Reference Guide
  - https://www.microsoft.com/en-us/download/details.aspx?id=103148

本目录中的 Markdown、PDF、XLSX 为了便于验收做了本地重组和结构化整理，不等同于原始资料镜像。
