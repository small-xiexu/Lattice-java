# 文档识别与 OCR 运行态说明

## 功能概述

Lattice 支持从多种格式的源文件中提取文本内容，包括 Markdown、纯文本、Excel、PDF（含扫描 PDF）和图片。对于包含文字信息的 PDF 和图片，系统通过 **文档解析连接（DocumentParse Connection）** 路由到外部 OCR Provider 完成识别。

## 管理入口

文档识别连接的增删改查通过管理 API 完成：

- **API 端点**：`/api/v1/admin/document-parse/connections`
- **支持的方法**：
  - `GET` — 列出所有已配置的连接
  - `POST` — 新增连接
  - `PUT /{id}` — 更新指定连接
  - `DELETE /{id}` — 删除指定连接

## 连接模型

每个连接包含以下核心字段：

| 字段 | 说明 |
|---|---|
| `connectionCode` | 连接标识，用于系统内引用 |
| `providerType` | Provider 类型（由系统支持的 Provider Descriptor 决定） |
| `baseUrl` | Provider 服务地址 |
| `credentialJson` | 访问凭证（加密存储） |
| `enabled` | 是否启用 |

## 识别能力

系统对不同文件类型的处理路径：

| 文件类型 | 处理方式 |
|---|---|
| Markdown / 纯文本 / 代码文件 | 直接读取文本内容 |
| Excel (.xlsx) | 结构化解析，按 sheet 抽取行列数据 |
| 可搜索 PDF（含文字层） | 直接提取文字层内容 |
| 扫描 PDF / 图片（无文字层） | 路由到 OCR Provider 进行光学字符识别 |

## 运行态依赖

**图片和扫描 PDF 的识别能力取决于 OCR Provider 连接是否已正确配置且启用。** 具体而言：

1. 如果**未配置任何启用的 OCR Provider 连接**，系统可以正常处理文本文件、Excel 和有文字层的 PDF，但**无法识别图片和扫描 PDF**。
2. 如果**已配置启用的 OCR Provider 连接**，图片和扫描 PDF 在上传后会被路由到指定 Provider 完成 OCR，识别后的文本进入后续编译流程。
3. OCR 路由策略（例如哪些文件类型走哪个 Provider）由连接配置中的 `configJson` 字段和系统内置的 `DocumentParseProviderDescriptor` 共同决定。

## 查看当前 Provider 连接状态

通过以下方式查看当前环境的文档解析连接配置：

```
GET /api/v1/admin/document-parse/connections
```

返回列表中每一条连接的 `enabled` 字段表示是否生效。系统根据 `enabled` 状态和 `providerType` 决定图片/扫描 PDF 的实际处理路径。

如果返回列表为空或所有连接 `enabled=false`，则图片和扫描 PDF 的 OCR 识别不可用。

## 与知识库问答的关系

**运行态问题通过知识库资料回答，不依赖代码特判。** 当用户询问"OCR 当前是否可用"时：

- 回答基于知识库中已导入的文档（包括本文档），反映的是资料记载的事实状态
- 实际运行状态以 `/api/v1/admin/document-parse/connections` 的实时查询结果为准
- 系统不会为 OCR 运行态问题单独开启代码旁路或硬编码答案
