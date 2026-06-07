# Java 代码库 Public Eval

本目录是 knowledge-base-e2e 的 Java 代码库 public eval 资料包，用于验证 INTERNAL_MIRROR 源码仓库的知识库导入、代码搜索、跨文件问答和 citation 能力。

## 内容

```
java-codebase-public-eval/
├── README.md
├── sources/
│   └── payment-service-mini/               # 虚构的 Spring Boot 支付微服务（21 个文件）
│       ├── pom.xml
│       ├── README.md
│       └── src/main/...
└── eval/
    └── question-set.md                     # 12 FQ + 6 FS + 3 FG
```

## fixture 规模

- 总文件数：21
- Java 文件：12
- XML 配置：3（Mapper ×2 + pom.xml）
- YAML 配置：3（application.yml、-dev、-prod）
- Markdown：1（项目 README）

## 验证能力

| 能力 | 对应题号 |
|---|---|
| Controller endpoint URL / HTTP method | FQ1 |
| Service 校验逻辑 | FQ2 |
| MyBatis XML SQL 条件 | FQ3 |
| Entity 字段 → DB 表列 | FQ4 |
| 多环境配置差异 | FQ5 |
| 条件分支 + 配置参数 | FQ6、FQ9 |
| Maven 依赖清单 | FQ7 |
| README 操作说明 | FQ8 |
| 跨文件调用链 | FQ10 |
| 拒答：不存在 API | FQ11 |
| 拒答：不存在配置 | FQ12 |
| 搜索：类名/文件名/方法名/关键词 | FS1-FS4 |
| 保护：数值/百分比/拒答 | FG1-FG3 |

## 红线

- 虚构项目 `com.example.payment`，不使用真实公司名或项目名
- 不使用真实业务数据
- 题目和答案仅存在于题集中

## 执行

agentD 导入本资料包后执行全量验收。
