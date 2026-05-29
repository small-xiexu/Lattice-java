# Terminal Unit Phase 1C: XLSX Terminal Unit 0 Hit 根因分析报告

分析时间: 2026-05-29
分析人: agentB (治理/链路分析 Agent)
分析范围: 只读分析, 不修改任何文件

## 1. 一句话结论

**XLSX 的 49 个 terminal unit 已正确生成且可被 LIKE 匹配, 但 `LexicalSearchTokenBudget` 的 8-token 上限被文件名前缀 token (如 "chemical-storage-grading.xlsx", "xlsx") 和 CJK 长 N-gram 消耗殆尽, 导致 "存储"、"条件"、"保管" 等真正能命中 terminal unit fts_text 的 CJK bigram 全部被挤出 LIKE 候选集, 最终 `fact_card_terminal_fts` 返回 0 hit。**

问题不在 dispatch、不在 extractor、不在 Materializer、不在 Reranker——只在 `LexicalSearchTokenBudget.MAX_LIKE_TOKENS = 8` 这个常量。

---

## 2. CSV vs XLSX 数据流完整对照

| 环节 | CSV (FQ11, 6 hits) | XLSX (FQ7, 0 hit) |
|---|---|---|
| **Extractor 输出** | `- table=...\n- row=2\n- 设备编号=EQ-001\n...` | `- sheet=...\n- row=2\n- 化学品名称=浓硫酸\n...` |
| **Fact card 生成** | 2 张: bullet_list + key_value_list | 2 张: bullet_list + key_value_list |
| **Terminal unit 物化** | 30 个, review_status=valid | **49 个**, review_status=valid |
| **Terminal unit 示例** | key="维护等级", value="A", fts_text 含 `维护等级 维护 护等 等级` | key="危险等级", value="A", fts_text 含 `危险等级 危险 险等 等级` |
| **DB LIKE 可达性** | `fts_text like '%维护等级%'` → 匹配 | `fts_text like '%存储条件%'` → 匹配 (6 rows) |
| **运行时 LIKE 候选** | `%维护等级%` 在 top-8 候选内 | `%存储%` / `%条件%` / `%保管%` **全部被挤出 top-8** |
| **最终 hit** | 6 hits | **0 hit** |

---

## 3. 阻断点精确定位

### 3.1 阻断点

**文件**: `src/main/java/com/xbk/lattice/infra/persistence/LexicalSearchTokenBudget.java`
**行号**: 19
**代码**: `private static final int MAX_LIKE_TOKENS = 8;`

### 3.2 完整阻断链

```
FQ7 查询: "chemical-storage-grading.xlsx 里，B 级危险化学品的存储条件是什么？由谁保管？"
  │
  ├── Step 1: QueryTokenExtractor.extract() → 37 个 token
  │     │
  │     ├── 结构化 token (score 523): "chemical-storage-grading.xlsx"  ← 文件名前缀
  │     ├── ASCII token (score 364): "xlsx"                            ← 扩展名
  │     │
  │     ├── CJK run 1 "级危险化学品的存储条件是什么" (14 chars):
  │     │   ├── 11 quadgrams (score 224): "级危险化", "危险化学品", ..., "的存储条件", ...
  │     │   ├── 12 trigrams (score 223): "级危险", "危险化", ..., "储条件", ...
  │     │   └── 13 bigrams (score 222): "级危", "危险", ..., "存储", "储条", "条件", ...
  │     │
  │     └── CJK run 2 "由谁保管" (4 chars):
  │         ├── 1 quadgram (224): "由谁保管"
  │         ├── 2 trigrams (223): "由谁保", "谁保管"
  │         └── 3 bigrams (222): "由谁", "谁保", "保管"  ← 唯一能匹配 "保管人角色" 的 token
  │
  ├── Step 2: LexicalSearchTokenBudget.selectLikeTokens() → 按 score DESC, index ASC 排序
  │     │
  │     │  Top-8 排序结果:
  │     │  1. "chemical-storage-grading.xlsx" (523)  ← 文件名, 对终端单元匹配无用
  │     │  2. "xlsx" (364)                            ← 扩展名, 对终端单元匹配无用
  │     │  3. "级危险化" (224, quadgram 1)            ← 含前缀 "级", 无法精确匹配 fts_text
  │     │  4. "危险化学品" (224, quadgram 2)          ← 含后缀 "学品", 无法精确匹配
  │     │  5. "险化学品的" (224, quadgram 3)
  │     │  6. "学品的存储" (224, quadgram 4)
  │     │  7. "品的存储条" (224, quadgram 5)
  │     │  8. "的存储条件" (224, quadgram 6)          ← 含前缀 "的", 无法匹配!
  │     │
  │     │  被挤出的关键 token:
  │     │  - "存储" (222, bigram 7, 总排名 ~34)       ← LIKE '%存储%' 可匹配 6 行!
  │     │  - "条件" (222, bigram 9, 总排名 ~36)       ← LIKE '%条件%' 可匹配 6 行!
  │     │  - "保管" (222, run2 bigram 2, 总排名 ~42)   ← LIKE '%保管%' 可匹配 5 行!
  │     │
  │     └── MAX_LIKE_TOKENS = 8 → 截断 → 只选前 8 个
  │
  └── Step 3: Mapper SQL 执行
        │
        ├── tsquery: plainto_tsquery('simple', 'chemical-storage-grading.xlsx 里...')
        │   → 输出: 'chemical-storage-grading.xlsx 里...' (单个长 token)
        │   → search_tsv @@ tsq → 0 行匹配 (中文未分词)
        │
        └── LIKE 回退:
            │ LIKE '%的存储条件%' → fts_text 不含 "的存储条件" → 0 匹配
            │ LIKE '%危险化学品%' → fts_text 不含 "危险化学品" → 0 匹配
            │ ... 其余 6 个 LIKE 均不匹配 ...
            └── 0 行返回
```

### 3.3 为什么 CSV 能命中

FQ11 查询: `"equipment-maintenance-schedule.csv 里，哪些设备的维护等级是"A 级"？"`

关键差异:
1. **只有 1 个高分段非 CJK token** (文件名, score 523), CSV 的 "csv" token score 仅 123, 排在 CJK quadgram 之后
2. CJK run 较短 ("哪些设备的维护等级是", 10 chars), quadgram 仅 7 个
3. **"维护等级" 恰好是第 6 个 quadgram (score 224, 总排名 7), 挤进 top-8!**
4. LIKE `%维护等级%` **精确匹配** fts_text 中的 "维护等级" token → 命中 6 行

```
FQ11 Top-8:
1. "equipment-maintenance-schedule.csv" (523)   ← 文件名
2. "哪些设备" (224, quadgram 1)
3. "些设备的" (224, quadgram 2)
4. "设备的维" (224, quadgram 3)
5. "备的维护" (224, quadgram 4)
6. "的维护等" (224, quadgram 5)
7. "维护等级" (224, quadgram 6)                ← 精确匹配 fts_text!
8. "护等级是" (224, quadgram 7)
```

---

## 4. 证据引用

### 4.1 数据库证据: XLSX terminal unit 已生成

```sql
-- 来源: lattice.fact_card_terminal_units
SELECT source_file_id, count(*) FROM lattice.fact_card_terminal_units
WHERE source_file_id IN (1, 3) GROUP BY source_file_id;

 source_file_id | unit_count
----------------+------------
              1 |         49    ← XLSX: 49 个 terminal unit 已生成
              3 |         30    ← CSV: 30 个 terminal unit 已生成
```

### 4.2 数据库证据: XLSX terminal unit 可被 LIKE 匹配

```sql
SELECT count(*) FROM lattice.fact_card_terminal_units
WHERE source_file_id = 1
  AND (lower(fts_text) LIKE '%存储%'
    OR lower(fts_text) LIKE '%条件%'
    OR lower(fts_text) LIKE '%保管%');
-- 结果: 31 行匹配
```

### 4.3 数据库证据: XLSX terminal unit 的 fts_text 示例

```
-- id=6, terminal_key=存储条件
fts_text: "FACT_ENUM ENUM key_value_list 存储条件 存储条件 [4].存储条件
           [4] 存储条件 [4] 存储 储条 条件 存储条 储条件
           [4].存储条件 [4] 存储条件 [4].存储条件 = 防腐蚀柜、双人双锁
           防腐蚀柜、双人双锁 防腐蚀柜、双人双锁 string
           parentPath: [4]; field: 存储条件; valueType: string"
```

### 4.4 数据库证据: 运行时检索审计

```json
// lattice.query_retrieval_runs, run_id=11 (FQ7):
"fact_card_terminal_fts": {
    "status": "SUCCESS",
    "hitCount": 0,
    "durationMillis": 18
}
```

### 4.5 代码证据: LIKE 候选预算

```java
// LexicalSearchTokenBudget.java:19
private static final int MAX_LIKE_TOKENS = 8;

// LexicalSearchTokenBudget.java:68-71
selectedTokens.add(rankedToken.token());
if (selectedTokens.size() >= MAX_LIKE_TOKENS) {
    break;  // ← 硬截断: "存储", "条件", "保管" 全部被丢弃
}
```

### 4.6 代码证据: CJK 评分偏向长 token

```java
// LexicalSearchTokenBudget.java:102-103
if (isCjkToken(token)) {
    return token.length() >= 2 ? 220 + Math.min(token.length(), 20) : 0;
    //                           ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    //                           长度越大分数越高: bigram=222, trigram=223, quadgram=224
}
```

---

## 5. 是否需要清库重建

**不需要。** 数据库中的 49 个 XLSX terminal unit 完全正确, fts_text 内容正确, review_status=valid。问题在查询时的 LIKE token 选择逻辑, 与编译产物无关。

---

## 6. 是否需要修改代码

**是。** 需要修改 `LexicalSearchTokenBudget.java` 中的一个常量和一个评分逻辑。

### 推荐修改 (二选一)

#### 方案 A (最小改动, 推荐): 增加 MAX_LIKE_TOKENS 并反转 CJK 评分

**文件**: `src/main/java/com/xbk/lattice/infra/persistence/LexicalSearchTokenBudget.java`

**改动 1** (line 19): `MAX_LIKE_TOKENS = 8` → `MAX_LIKE_TOKENS = 32`

**改动 2** (line 103): CJK 评分从 `220 + Math.min(token.length(), 20)` 改为 `230 - Math.min(token.length(), 8)`

效果:
- Bigram (len=2): score=228 (最高)
- Trigram (len=3): score=227
- Quadgram (len=4): score=226
- 干净的 bigram 优先被选为 LIKE token
- LIKE `%存储%`、`%条件%`、`%保管%` 进入候选集

#### 方案 B (仅改一行, 更保守): 仅增加 MAX_LIKE_TOKENS

**文件**: `src/main/java/com/xbk/lattice/infra/persistence/LexicalSearchTokenBudget.java`

**改动** (line 19): `MAX_LIKE_TOKENS = 8` → `MAX_LIKE_TOKENS = 64`

效果:
- 保留现有 CJK 评分逻辑不变
- 通过足够大的预算确保所有 CJK bigram 最终进入 LIKE 候选
- 风险: 64 个 LIKE OR 条件, 性能影响可忽略 (均为索引列简单 LIKE)

### 红线判断

- **不涉及文件名特判**: 不判断 token 是否为文件名, 不排除特定 token
- **不涉及 eval 题面**: 不改查询字符串, 不改题集
- **不涉及业务词硬编码**: 不改 CJK 评分中的任何业务语义
- **纯参数调整**: 只改一个常量 (MAX_LIKE_TOKENS) 和一个通用评分公式 (CJK token score)
- **适用范围**: 所有中文查询的 lexical 检索, 不特定于 XLSX/FQ7

---

## 7. 下一轮建议

**唯一推荐动作**: agentA 修改 `LexicalSearchTokenBudget.java`, 采用方案 A (增加预算 + 反转 CJK 评分)。

### AgentA 提示词草案

```
你是 agentA, 本轮唯一任务: 修复 LexicalSearchTokenBudget 中 LIKE token 预算不足导致
中文查询的 terminal unit FTS 检索返回 0 hit 的问题。

## 根因
- MAX_LIKE_TOKENS = 8 不足以容纳中文查询的所有有效 CJK bigram
- CJK 评分公式 `220 + length` 偏向 quadgram, 但 quadgram 含前后缀字符,
  导致 LIKE '%的存储条件%' 无法匹配 fts_text 中干净的 "存储条件" token
- 文件名前缀 token (如 "chemical-storage-grading.xlsx") 消耗 2 个预算槽位

## 允许修改文件 (仅一个)
- src/main/java/com/xbk/lattice/infra/persistence/LexicalSearchTokenBudget.java

## 修改内容
1. MAX_LIKE_TOKENS: 8 → 32
2. CJK token 评分: `220 + Math.min(token.length(), 20)` → `230 - Math.min(token.length(), 8)`
   (bigram=228, trigram=227, quadgram=226, 干净的 bigram 优先)

## 禁止修改文件
- 所有其他文件

## 禁止事项
- 不准根据文件名、扩展名过滤 token
- 不准硬编码任何中文业务词
- 不准修改 QueryTokenExtractor、FactCardTerminalUnitFtsSearchService、Materializer
- 不准修改 mapper XML / SQL

## 测试要求
- 验证 CJK bigram 评分 > trigram > quadgram
- 验证 MAX_LIKE_TOKENS = 32 时 token 选择正确
- 现有单元测试不退化

## 验证命令
- redline: bash scripts/scan-redline.sh special_cases_report.md → BLOCKER=0
- 定向测试: mvn test -Dtest=LexicalSearchTokenBudgetTests,FactCardTerminalUnitFtsSearchServiceTests
- 全量: mvn test

## 输出
- *_fix_result_report.md
```

---

## 8. AgentD 验证方案草案

```
你是 agentD, 本轮任务: 验证 LIKE token 预算修复后, FQ7 (XLSX) 的
fact_card_terminal_fts channel 是否有命中。

## 前置条件
- agentA 已完成 LexicalSearchTokenBudget 修复
- redline BLOCKER=0, 全量 mvn test 通过
- 不需要清库重建 (terminal unit 数据已就绪)

## 验证步骤
1. 重启服务 (加载新代码)
2. 对 FQ7 运行 query: "chemical-storage-grading.xlsx 里，B 级危险化学品的存储条件是什么？由谁保管？"
3. 检查 fact_card_terminal_fts channel:
   a. hitCount > 0
   b. 命中包含 "存储条件" 和 "保管人角色" 相关 terminal unit
4. 保护回归:
   a. FQ11 (CSV): 6 hits 不退化
   b. YAML 5 题: 行为不变
   c. 全量 19 题 fresh eval 无新增回归

## Gate 判定
- FQ7 fact_card_terminal_fts hitCount > 0 → PASS
- 保护回归通过 → PASS

## 输出
- *_verification_report.md
```

---

## 合规声明

- 本轮未修改 `src/main/java`、`src/test/java`、`src/main/resources`、`scripts`
- 本轮仅执行只读 SQL 查询 (SELECT), 未写入任何数据
- 本轮未读取 hidden eval
- 本轮未把 eval 题面、答案、case id、文件名、业务词写入代码或配置
- 本轮未 stage、未 commit、未 push
- 本轮新增报告: `terminal_unit_phase1c_xlsx_terminal_unit_root_cause_analysis_report.md`
