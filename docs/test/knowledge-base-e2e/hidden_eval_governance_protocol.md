# Hidden Eval 治理协议

版本：v1.0
制定时间：2026-06-07
制定人：agentC
适用阶段：5 套 public eval 稳定通过后的最终泛化验收

## 一、目的

Hidden eval 用于验证系统在**未见过资料、未参与调参的数据集**上的泛化能力。Hidden eval 不是调参工具，不用于驱动代码修复或 prompt 优化。

与 public eval 的差异：

| 维度 | Public Eval | Hidden Eval |
|---|---|---|
| 用途 | 暴露能力缺口、驱动通用修复、形成回归保护 | 最终泛化验收、防过拟合 |
| AI 可读 | 是（agentA/B/C/D 均可读取题目和答案） | 否（仅用户和授权验证 agent 可读） |
| 存放位置 | `docs/test/knowledge-base-e2e/` | 仓库外（用户本地或独立加密存储） |
| 修复依据 | 可直接基于失败 case 归因和修复 | 只能通过抽象能力缺口 + public bad case 复现来间接修复 |
| 提交策略 | 随代码提交 | 不提交到 Git 仓库 |

## 二、文件存放与访问控制

### 2.1 存放建议

Hidden eval 资产（题目文件、标准答案、资料包）**不放入 `docs/test/**` 或任何 Git 仓库路径**。推荐存放位置：

| 选项 | 适用场景 | 说明 |
|---|---|---|
| 用户本地目录 | 单用户 | 路径如 `~/.lattice/hidden-eval/`，由用户自行保管 |
| 加密压缩包 | 需要传输 | zip + AES-256 加密，密码通过独立渠道传递 |
| 独立私有仓库 | 团队协作 | 独立于主仓库的私有 Git 仓库，权限严格控制 |

**不推荐**存入项目主仓库，即使加密，因为 Git 历史不可逆。

### 2.2 访问控制矩阵

| 角色 | 创建阶段 | 验收阶段 | 失败归因阶段 | 修复阶段 |
|---|---|---|---|---|
| **用户** | 可读可写 | 可读 | 可读 | 可读 |
| **agentC（文档/题集）** | 可读可写（生成资产） | 不可读 | 不可读 | 不可读 |
| **agentD（验证/测试）** | 不可读 | 可读（仅验收时） | 不可读 | 不可读 |
| **agentB（治理/归因）** | 不可读 | 不可读 | **不可读题目和答案** | 不可读 |
| **agentA（代码修复）** | 不可读 | 不可读 | 不可读 | **禁止读取** |

**关键规则**：
- agentC 若参与生成 hidden eval，在生成完成后即视为"被污染"，后续不得参与该套 hidden eval 的失败归因或修复提示词编写。
- agentD 在验收完成后，只能输出脱敏指标和失败类型统计，不能将题目原文、答案、关键词写入任何仓库文件。

## 三、报告脱敏规范

### 3.1 可以输出的内容

- 总分：Answer Accuracy、Search Accuracy、Recall@5、Recall@10、Citation Accuracy、Abstain Accuracy
- Hallucination Count
- 按失败类型分类的计数（如"拒答失败 3 题、数值混淆 2 题"）
- 按能力维度的通过率（如"条款定位 4/5"、"状态流转 3/4"）
- 与 public eval 的指标对比
- Gate 结论（PASS/FAIL）

### 3.2 禁止输出的内容

- 题目原文或任何可还原题目的描述
- 标准答案、部分答案或答案关键词
- 资料文件名、source title、段落标题
- case id
- expected citation 或 source 引用
- 任何可以定位到具体 hidden case 的信息

### 3.3 脱敏报告模板

```markdown
# Hidden Eval A 验收报告（脱敏版）

验收时间：YYYY-MM-DD
代码基线：<commit hash>

## 指标

| 指标 | 值 | 通过线 | 判定 |
|---|---|---|---|
| Answer Accuracy | XX/XX | >= 85% | PASS/FAIL |
| Search Accuracy | XX/XX | >= 85% | PASS/FAIL |
| Recall@10 | XX/XX | >= 90% | PASS/FAIL |
| Citation Accuracy | XX/XX | >= 75% | PASS/FAIL |
| Abstain Accuracy | XX/XX | >= 95% | PASS/FAIL |
| Hallucination Count | X | <= 1 | PASS/FAIL |

## 失败类型分布

| 类型 | 数量 |
|---|---|
| 检索未召回 | X |
| 证据已召回但答案漏点 | X |
| 引用错误 | X |
| 应拒答但编造 | X |
| 多证据冲突未处理 | X |

## 与 Public Eval 对比

| 指标 | Public Eval（均值） | Hidden Eval | 差异 |
|---|---|---|---|
| Answer Accuracy | XX% | XX% | ±X% |

## 结论

PASS / FAIL — <一句话判定>
```

## 四、失败处理流程

Hidden eval 失败**不能**直接把题目或答案交给 agentA/agentB 进行修复。处理流程如下：

```
Hidden eval FAIL
    ↓
① agentD 输出脱敏报告（只有指标和失败类型）
    ↓
② 用户/架构师审阅脱敏报告，识别抽象能力缺口
    （如"CSV 逾期判断失败 3 例"、"条款冲突选择失败 2 例"）
    ↓
③ 用户/架构师用 public eval 或合成 public bad case 复现同类问题
    （不引用 hidden case 原文）
    ↓
④ agentB 对复现的 public case 做只读归因
    ↓
⑤ agentA 做最小通用修复（只能基于 public case）
    ↓
⑥ agentD 验证 public case 修复 + public eval 保护回归
    ↓
⑦ 回归通过后，重新跑 hidden eval 验证泛化效果
```

**禁止**：
- 把 hidden case 原文贴给 agentA/agentB
- 基于 hidden case 写任何生产代码特判
- 在 prompt 中引用 hidden case 的答案模式
- 因为 hidden eval 失败而降低验收门槛

## 五、提交策略

| 资产 | 是否提交 Git | 说明 |
|---|---|---|
| Hidden eval 题目文件 | **不提交** | 用户本地保管 |
| Hidden eval 资料包 | **不提交** | 用户本地保管；资料本身可以是公开内容，但题集不提交 |
| Hidden eval 标准答案 | **不提交** | 最高密级 |
| 脱敏验收报告 | **可提交** `docs/test/knowledge-base-e2e/hidden_eval_gates/` | 仅含脱敏指标和失败类型，不含题目原文 |
| 本治理协议 | **随代码提交** | 本文档 |

**推荐**：脱敏验收报告使用独立目录 `docs/test/knowledge-base-e2e/hidden_eval_gates/`，并在此目录的 README 中声明"本目录不含任何 hidden eval 题目、答案或关键词"。

## 六、最终验收指标

与 public eval 相同口径，但通过线可以略低于 public eval（允许 3-5% 泛化损耗）：

| 指标 | 通过线 |
|---|---|
| Answer Accuracy | >= 80%（public 目标 >= 85%） |
| Search Accuracy | >= 80% |
| Recall@10 | >= 85%（public 目标 >= 90%） |
| Citation Accuracy | >= 70%（public 目标 >= 75%） |
| Abstain Accuracy | >= 95% |
| Hallucination Count | <= 2（public 目标 <= 1） |

附加条件：
- redline `BLOCKER=0`
- mvn test 通过
- 5 套 public eval 均稳定通过（不能因为 hidden 失败回退 public 基线）

## 七、推荐主题方向（不写具体题目）

### Hidden A：文档类泛化

- **领域**：与 PE1-PE4 完全不同的业务域（如教育/教务管理、物业/设施管理、金融合规）
- **格式**：覆盖 Markdown、YAML、PDF、XLSX、CSV 的组合，不引入新格式
- **验证重点**：
  - 在完全未见过资料上的条款定位、数值提取、状态判断、拒答能力是否保持
  - 是否存在对 PE1-PE4 资料的过拟合（如对"医疗设备"域有偏好）
- **规模**：12-15 题问答 + 5-6 搜索子项 + 3 保护题

### Hidden B：Java 代码类泛化

- **项目类型**：与 payment-service-mini 不同的虚构 Java 项目（如订单管理、库存管理、审批流）
- **技术栈**：Spring Boot + MyBatis-Plus，与 public codebase eval 相同技术栈
- **验证重点**：
  - endpoint 定位、Service 逻辑提取、Mapper SQL 理解是否泛化
  - 是否对 payment-service-mini 的具体类名/方法名有过拟合
- **规模**：10-12 题问答 + 4-5 搜索子项 + 2 保护题

## 八、生命周期

```
① agentC 生成 hidden eval 资产（题目 + 资料包 + 答案）
       ↓
     agentC 生成完成后即退出该 hidden eval 后续流程
       ↓
② 用户审阅资产，确认无污染、无过拟合
       ↓
③ 5 套 public eval 全部稳定 PASS
       ↓
④ agentD 清库 → 导入 hidden eval 资料 → 编译 → 执行验收
       ↓
⑤ agentD 输出脱敏验收报告（不泄露题目/答案）
       ↓
⑥ 若 FAIL：按第四节失败处理流程执行（不能用 hidden case 原文修复）
   若 PASS：记录为最终泛化验收通过
       ↓
⑦ 脱敏报告提交到 `docs/test/knowledge-base-e2e/hidden_eval_gates/`
```

## 九、违规判定

以下行为视为协议违规：

1. 将 hidden eval 题目、答案、关键词写入生产代码、prompt、config、SQL、脚本
2. 在 agentA/agentB 的修复提示词中包含 hidden case 原文或可还原题目的描述
3. 在 public eval 题集中刻意模仿 hidden eval 的题目模式（测试集污染）
4. 在 GitHub issue、PR 评论、commit message 中提及 hidden eval 的具体内容
5. agentC 在生成 hidden eval 后参与同一套的失败归因或修复

## 十、与已有规范的衔接

本协议是对以下已有规范的细化和操作化：

- `AGENTS.md` 第 132-138 行：Eval 使用规则（public vs hidden）
- `docs/test/knowledge-base-e2e/eval-validation-roadmap.md` 第 136-163 行：Hidden Eval 规则与红线

以上条款在本协议生效后继续有效，本协议不替代它们。
