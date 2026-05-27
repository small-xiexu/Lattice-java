# `titleProfile` 元数据契约

**版本**：v1
**更新时间**：2026-05-24
**作用域**：`articles.metadata_json.titleProfile`、`article_snapshots.metadata_json.titleProfile`

---

## 1. 设计目标

- 让知识条目主标题、来源标题、切分原标题同时可追溯。
- 让标题生成方式可审计、可回填、可回归。
- 让历史数据在缺少新字段时仍能兼容读取。

---

## 2. 存储位置

标题画像统一存放在文章 metadata 的 `titleProfile` 节点中：

```json
{
  "titleProfile": {
    "sourceTitle": "quality-progress-and-lessons",
    "anchorTitle": "下一步计划",
    "representativeTitle": "Dashboard 状态摘要接入与质量台账回写要求",
    "titleGenerationMode": "RULE_BASED",
    "titleGenerationConfidence": "HIGH",
    "titleGenerationVersion": "v1"
  }
}
```

---

## 3. 字段定义

| 字段名 | 必填 | 类型 | 含义 |
|---|---|---|---|
| `sourceTitle` | 是 | `string` | 来源文档标题 |
| `anchorTitle` | 是 | `string` | 切分命中的原标题 |
| `representativeTitle` | 是 | `string` | 最终用户主展示标题 |
| `titleGenerationMode` | 是 | `string` | 标题生成方式 |
| `titleGenerationConfidence` | 否 | `string` | 当前生成结果置信度 |
| `titleGenerationVersion` | 是 | `string` | 标题生成规则版本 |

---

## 4. 枚举约束

### 4.1 `titleGenerationMode`

允许值：

- `ANCHOR_DIRECT`
- `RULE_BASED`
- `LLM_FALLBACK`
- `LEGACY_UNSET`

含义：

- `ANCHOR_DIRECT`
  - 直接采用锚点标题
- `RULE_BASED`
  - 由通用规则归纳得到
- `LLM_FALLBACK`
  - 规则低置信度时由 LLM 兜底生成
- `LEGACY_UNSET`
  - 历史数据尚未补齐标题画像

### 4.2 `titleGenerationConfidence`

允许值：

- `HIGH`
- `MEDIUM`
- `LOW`

---

## 5. 兼容策略

- 新数据：必须完整写入 `titleProfile`。
- 历史数据：若 `titleProfile` 缺失，读取侧必须兼容为空，不得抛错。
- 历史数据读取时，可在视图层按以下策略兼容：
  - `sourceTitle = null`
  - `anchorTitle = null`
  - `representativeTitle = article.title`
  - `titleGenerationMode = LEGACY_UNSET`
- 回填任务执行后，历史数据再转为新契约。

---

## 6. 与 `articles.title` 的关系

- `articles.title`：保存当前主展示标题。
- `titleProfile.representativeTitle`：必须与 `articles.title` 保持一致。
- `titleProfile.anchorTitle`：不能覆盖 `articles.title`。
- `titleProfile.sourceTitle`：用于来源追溯，不直接替代主标题。

---

## 7. 禁止事项

- 禁止把业务域、文档名映射、样例词、答案模板写入 `titleProfile`。
- 禁止把 LLM prompt 原文、内部中间推理过程写入 `titleProfile`。
- 禁止在 `titleProfile` 中存放与标题无关的大段正文。

---

## 8. 当前配套文件

- 契约文档：`docs/test/title-generation/title-profile-contract.md`
- JSON Schema：`docs/test/title-generation/title-profile.schema.json`
- 示例数据：`src/test/resources/title-generation/title-profile.example.json`
- 样本集：`docs/test/title-generation/title-generation-sample-set.md`
- 机器样本：`src/test/resources/title-generation/title-generation-sample-set.json`
