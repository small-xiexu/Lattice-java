# Compile Writer Unit Routing Gate Fix Result Report

## 1. 修改了哪些文件和方法

- `src/main/java/com/xbk/lattice/compiler/node/AnalyzeNode.java`
  - 在长文档 topic 拆分结果进入 Writer 前，接入新的路由收紧 gate
- `src/main/java/com/xbk/lattice/compiler/node/DocumentTopicWriterGatePolicy.java`
  - 新增过度专题化长文档的 overview gate
  - 主要方法：
    - `rewrite(...)`
    - `shouldCollapse(...)`
    - `buildOverviewConcept(...)`
    - `buildOverviewLines(...)`
- `src/test/java/com/xbk/lattice/compiler/service/AnalyzeNodeTests.java`
  - 新增“topic 过多时收敛为单个 overview concept”测试

## 2. 当前收紧的是哪类内容路由

- 当前收紧的是：
  - **被长文档专题拆分切成过多 topic 的单源长文档内容**

更具体地说：

- 原先这类文档会被 `DocumentTopicConceptExtractor` 拆成很多 `AnalyzedConcept`
- 后续每个 concept 都可能变成一个 writer unit
- 现在当同一源文件被拆出的 topic 数超过阈值时，不再逐个送入 Writer，而是收敛成一个 `Document Overview` concept

## 3. 为什么这些内容不该继续进入 Writer

- 这类内容本质上是**同一份长文档的大纲型/专题型切分结果**
- 当 topic 数过多时，继续逐个进入 Writer 会线性放大：
  - Writer 次数
  - Reviewer 次数
  - Fixer / re-review 机会
- 它们更适合先保留成**单文档 overview**，而不是立刻变成一串叙述型文章单元
- 这类收敛不会影响：
  - 表格结构化保留
  - 普通小型长文档专题拆分
  - Query / approve / reject / publish 主链

## 4. 是否复用了现有 structured / overview / gate 机制

- 是。
- 复用了现有 `StructuredTableWriterGatePolicy` 的思路：
  - 先识别“这类内容不该大面积进入 Writer”
  - 然后生成少量 `overview concept`
- 本轮只是把同样的 gate 范式扩展到：
  - **过度专题化的长文档**

## 5. 是否新增业务特判

- 否。
- 没有按文档名、文件名、表名、业务词、样例字符串做特判。
- gate 只基于通用结构信号：
  - 单个源文件
  - 长文档 topic 拆分
  - 拆分出的 topic 数是否超过阈值

## 6. redline BLOCKER 是否仍为 0

- 是。
- 最终结果：
  - `BLOCKER=0`
  - `REVIEW=1863`
  - `ALLOWLIST=244`

## 7. 测试是否通过

定向测试通过：

- `mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -Dtest=AnalyzeNodeTests,AnalyzeNodeStructuredTableWriterGateTests test`
- 结果：`Tests run: 16, Failures: 0, Errors: 0, Skipped: 0`

全量测试通过：

- `mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test`
- 结果：`Tests run: 855, Failures: 0, Errors: 0, Skipped: 0`

## 8. 下一轮是否建议交给 agentD 做性能复验

- 是。
- 建议 agentD 下一轮重点复验：
  - 同一份大纲型长文档在 compile 前后的 writer unit 数变化
  - compile 总耗时是否下降
  - Writer / Reviewer 调用次数是否同步下降
  - 普通 2-4 topic 的长文档是否仍保持原有拆分质量
