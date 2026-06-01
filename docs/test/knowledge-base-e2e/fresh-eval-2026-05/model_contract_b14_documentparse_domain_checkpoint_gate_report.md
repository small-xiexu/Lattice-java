# B14 DocumentParse Domain Checkpoint 门禁核查报告

核查时间：2026-06-01
核查人：agentD（只读门禁审计）
范围：B14a + B14b — documentparse/domain（10 个类）
状态：**PASS — 所有核查项通过，无阻塞问题**

---

## 1. 当前 Git 变更分类

### 1.1 按文件类别统计

| 类别 | 数量 | 状态 |
|---|---|---|
| 生产代码（B14a parse 管道） | 5 | 2 枚举 Javadoc + 3 不可变对象 @Getter |
| 生产代码（B14b Provider/Route） | 5 | 5 不可变对象 @Getter，凭证字段安全标注 |
| **生产代码 B14 合计** | **10** | — |
| 计划文档（治理台账） | 1 | 台账更新（B14→已完成），符合预期 |
| 模型绑定配置参考 | 1 | **已知 out-of-scope dirty（不得纳入）** |
| special_cases_report | 1 | **已知 out-of-scope dirty（不得纳入）** |
| 批次报告（untracked B14） | 3 | 可纳入 |

### 1.2 生产代码详细清单

#### B14a: Parse 管道对象（5 个）

| 文件 | 类型 | diff 规模 | 变更 |
|---|---|---|---|
| `DocumentParseMode.java` | 枚举 | +14/-? | 5 枚举值 Javadoc |
| `ParseCapability.java` | 枚举 | +7/-? | 2 枚举值 Javadoc |
| `DocumentParseResult.java` | 不可变 | +123/-? | @Getter，删除 10 getter，10 字段 Javadoc |
| `ParseOutput.java` | 不可变 | +156/-? | @Getter，删除 12 getter，12 字段 Javadoc，4 业务方法保留 |
| `ParseRequest.java` | 不可变 | +77/-? | @Getter，删除 5 getter，5 字段 Javadoc（Path 类型） |

#### B14b: Provider/Route 对象（5 个）

| 文件 | diff 规模 | 变更 |
|---|---|---|
| `ParseRoutePolicy.java` | +165/-? | @Getter，删除 11 getter，11 字段 Javadoc，DEFAULT_SCOPE+defaultPolicy 保留 |
| `ProviderConnection.java` | +171/-? | @Getter，删除 12 getter，credentialCiphertext/credentialMask 安全标注，4 PROVIDER_* 常量保留 |
| `ProviderDescriptor.java` | +101/-? | @Getter，删除 7 getter，7 字段 Javadoc |
| `ProviderFieldDescriptor.java` | +102/-? | @Getter，删除 7 getter，7 字段 Javadoc（动态表单语义） |
| `ProviderProbeResult.java` | +78/-? | @Getter，删除 5 getter，5 字段 Javadoc |

---

## 2. 核查项逐项结果

### 2.1 生产代码仅限 B14 10 个文件

| 包 | 预期 | 实际 | 匹配 |
|---|---|---|---|
| `documentparse/domain/` | 2 | 2（DocumentParseMode, DocumentParseResult） | ✅ |
| `documentparse/domain/model/` | 8 | 8（ParseCapability 等） | ✅ |
| **合计** | **10** | **10** | ✅ |

### 2.2 越界检查

```
git diff --name-only -- src/main/java/com/xbk/lattice/documentparse/ | grep -v "documentparse/domain/"
→ (无输出)
```
documentparse/service、documentparse/adapter 均未修改。

```
git diff --name-only -- src/main/java/com/xbk/lattice/source/domain/
→ (无输出)
```
B15 source/domain 未修改。

**结果：PASS。**

### 2.3 @Data/@Setter/@Builder 检查

```
rg -l "@Data|@Setter|@Builder" <10个文件>
→ ProviderConnection.java（Javadoc 文本"禁止引入 @Data"，非注解）
```

ProviderConnection 命中是因为 Javadoc 中写了"含敏感字段（credentialCiphertext），禁止引入 @Data"——这是安全标注文档，不是实际注解。

**结果：PASS。** 10 个文件 0 个 @Data，0 个 @Setter，0 个 @Builder。

### 2.4 @Getter 检查

| 文件 | @Getter | 结果 |
|---|---|---|
| `DocumentParseResult` | 1（类级） | ✅ |
| `ParseOutput` | 1（类级） | ✅ |
| `ParseRequest` | 1（类级） | ✅ |
| `ParseRoutePolicy` | 1（类级） | ✅ |
| `ProviderConnection` | 1（类级） | ✅ |
| `ProviderDescriptor` | 1（类级） | ✅ |
| `ProviderFieldDescriptor` | 1（类级） | ✅ |
| `ProviderProbeResult` | 1（类级） | ✅ |
| **合计** | **8** | ✅ |

### 2.5 B14a 特殊方法保留

#### ParseOutput 业务方法（全部保留）

| 方法 | 行号 | 说明 |
|---|---|---|
| `hasResolvedContent()` | 87 | 判断是否有可用正文 |
| `resolveContent()` | 94 | 优先 plainText，其次 markdown |
| `resolveContentFormat()` | 107 | 返回 plain_text / markdown / empty |
| `hasText()` | 117 | private 工具方法 |

**结果：PASS。** 4 个业务方法全部保留，未被 Lombok 覆盖。

### 2.6 B14b 常量与安全标注

#### ProviderConnection

| 检查项 | 预期 | 实际 | 结果 |
|---|---|---|---|
| credentialCiphertext Javadoc | "已加密存储，非明文但仍属敏感数据" | Javadoc 包含"已加密存储"语义 | ✅ |
| credentialMask Javadoc | "仅管理侧脱敏展示，非完整凭证" | 已标注 | ✅ |
| 4 个 PROVIDER_* 常量 | 保留 | PROVIDER_TENCENT_OCR/ALIYUN_OCR/GOOGLE_DOCUMENT_AI/TEXTIN_XPARSE | ✅ |

#### ParseRoutePolicy

| 检查项 | 预期 | 实际 | 结果 |
|---|---|---|---|
| `DEFAULT_SCOPE` 常量 | 保留 | 已保留 | ✅ |
| `defaultPolicy()` factory | 保留 | 已保留 | ✅ |

**结果：PASS。**

### 2.7 计划台账状态

| 批次 | 台账状态 | 验证 |
|---|---|---|
| B14 | 已完成（拆 B14a/B14b，10 类） | mvn compile PASS |
| B15 | 待开始 | — |

**结果：PASS。** B14 已完成，下一步正确指向 B15。

### 2.8 Out-of-scope 文件

| 文件 | 状态 |
|---|---|
| `docs/模型绑定配置参考.md` | 仍为 dirty（已知，不纳入） |
| `special_cases_report.md` | 仍为 dirty（已知，不纳入） |

### 2.9 报告敏感信息检查

```
rg -n "sk-[A-Za-z0-9_-]{12,}" <3个B14报告>
→ (无输出)
```

**结果：PASS。**

---

## 3. 可纳入本次 Checkpoint 的文件清单

### 3.1 计划台账（1 个）

- `docs/plans/2026-05-31-模型契约注释与Lombok治理计划.md`

### 3.2 B14 生产代码（10 个）

| # | 文件 | 子批次 |
|---|---|---|
| 1 | `src/main/java/com/xbk/lattice/documentparse/domain/DocumentParseMode.java` | B14a |
| 2 | `src/main/java/com/xbk/lattice/documentparse/domain/model/ParseCapability.java` | B14a |
| 3 | `src/main/java/com/xbk/lattice/documentparse/domain/DocumentParseResult.java` | B14a |
| 4 | `src/main/java/com/xbk/lattice/documentparse/domain/model/ParseOutput.java` | B14a |
| 5 | `src/main/java/com/xbk/lattice/documentparse/domain/model/ParseRequest.java` | B14a |
| 6 | `src/main/java/com/xbk/lattice/documentparse/domain/model/ParseRoutePolicy.java` | B14b |
| 7 | `src/main/java/com/xbk/lattice/documentparse/domain/model/ProviderConnection.java` | B14b |
| 8 | `src/main/java/com/xbk/lattice/documentparse/domain/model/ProviderDescriptor.java` | B14b |
| 9 | `src/main/java/com/xbk/lattice/documentparse/domain/model/ProviderFieldDescriptor.java` | B14b |
| 10 | `src/main/java/com/xbk/lattice/documentparse/domain/model/ProviderProbeResult.java` | B14b |

### 3.3 B14 批次报告（3 个 untracked）

| 文件名 | 类型 |
|---|---|
| `documentparse_domain_contract_analysis_report.md` | 边界审查 |
| `documentparse_parse_domain_contract_javadoc_lombok_fix_result_report.md` | B14a 修复报告 |
| `documentparse_provider_route_domain_contract_javadoc_lombok_fix_result_report.md` | B14b 修复报告 |

---

## 4. 必须排除的文件

| 文件 | 原因 |
|---|---|
| `docs/模型绑定配置参考.md` | API key 变更 + 计划禁令 |
| `special_cases_report.md` | 机械重扫 + 计划禁令 |

---

## 5. B14 汇总

### 5.1 B14a（5 个 parse 管道对象）

| 指标 | 数量 |
|---|---|
| 枚举 | 2（DocumentParseMode 5 值 + ParseCapability 2 值） |
| 不可变对象 | 3（DocumentParseResult, ParseOutput, ParseRequest） |
| @Getter | 3 |
| 删除 getter | 27 |
| @Data/@Setter/@Builder | **0** |
| ParseOutput 业务方法 | 4 保留（hasResolvedContent/resolveContent/resolveContentFormat/hasText） |
| Path 类型字段 | 2（ParseRequest.workspaceRoot/filePath） |

### 5.2 B14b（5 个 Provider/Route 对象）

| 指标 | 数量 |
|---|---|
| 不可变对象 | 5 |
| @Getter | 5 |
| 删除 getter | 42 |
| @Data/@Setter/@Builder | **0** |
| 凭证安全标注 | 2（credentialCiphertext + credentialMask） |
| 常量保留 | DEFAULT_SCOPE + 4 PROVIDER_* |
| factory 保留 | defaultPolicy() |

### 5.3 B14 合计

| 指标 | 数量 |
|---|---|
| 类数 | 10（2 枚举 + 8 不可变对象） |
| @Getter | 8 |
| 删除 getter | 69 |
| @Data | **0**（干净批次，无需降级） |
| 枚举 Javadoc | 7 值（5+2） |

### 5.4 编译验证

| 子批次 | mvn compile |
|---|---|
| B14a | BUILD SUCCESS |
| B14b | BUILD SUCCESS |

---

## 6. 给下一轮 /code-commit 的 Staging 建议

```bash
# === B14a: Parse 管道对象（5 个文件）===
git add src/main/java/com/xbk/lattice/documentparse/domain/DocumentParseMode.java
git add src/main/java/com/xbk/lattice/documentparse/domain/model/ParseCapability.java
git add src/main/java/com/xbk/lattice/documentparse/domain/DocumentParseResult.java
git add src/main/java/com/xbk/lattice/documentparse/domain/model/ParseOutput.java
git add src/main/java/com/xbk/lattice/documentparse/domain/model/ParseRequest.java

# === B14b: Provider/Route 对象（5 个文件）===
git add src/main/java/com/xbk/lattice/documentparse/domain/model/ParseRoutePolicy.java
git add src/main/java/com/xbk/lattice/documentparse/domain/model/ProviderConnection.java
git add src/main/java/com/xbk/lattice/documentparse/domain/model/ProviderDescriptor.java
git add src/main/java/com/xbk/lattice/documentparse/domain/model/ProviderFieldDescriptor.java
git add src/main/java/com/xbk/lattice/documentparse/domain/model/ProviderProbeResult.java

# === B14 批次报告 ===
git add docs/test/knowledge-base-e2e/fresh-eval-2026-05/documentparse_domain_contract_analysis_report.md
git add docs/test/knowledge-base-e2e/fresh-eval-2026-05/documentparse_parse_domain_contract_javadoc_lombok_fix_result_report.md
git add docs/test/knowledge-base-e2e/fresh-eval-2026-05/documentparse_provider_route_domain_contract_javadoc_lombok_fix_result_report.md

# === 本门禁报告 ===
git add docs/test/knowledge-base-e2e/fresh-eval-2026-05/model_contract_b14_documentparse_domain_checkpoint_gate_report.md
```

---

## 7. 是否可以进入 B15

**可以。** 所有核查项通过：

- [x] 10 个文件与指定清单完全匹配
- [x] 0 个 @Data/@Setter/@Builder（B14 是零污染批次）
- [x] 8 个不可变对象 @Getter（69 getter 删除）
- [x] ParseOutput 4 个业务方法全部保留
- [x] ProviderConnection 凭证字段安全标注
- [x] DEFAULT_SCOPE + 4 PROVIDER_* 常量保留 + defaultPolicy() 保留
- [x] documentparse/service、adapter、B15 source/domain 均未修改
- [x] 计划台账 B14→已完成，下一步 B15
- [x] 2 个 out-of-scope 文件已排除
- [x] mvn compile PASS（B14a + B14b）

---

## 附录：全量治理进度（截至 B14）

| 阶段 | 批次 | 类数 | 处置方式 | 状态 |
|---|---|---|---|---|
| 试点 | B0 | 3 | @Getter + Javadoc | 已提交 |
| API 边界 DTO | B0.5-B10 | 95 | @Getter/@Setter + Javadoc | 已完成 |
| Controller 内部 DTO | B11 | 29 | @Data 降级 + @ToString.Exclude | 已完成 |
| Config + State | B12 | 16 | Javadoc / @Getter | 已完成 |
| Compiler Domain + AST | B13 | 14 | @Getter + @Data 降级 | 已完成 |
| DocumentParse Domain | B14 | 10 | @Getter（零 @Data 污染） | 已完成 |
| **累计** | | **167** | | **164 未提交** |
| Source Domain | B15 | ~9 | 待定 | 待开始 |
| LLM Domain | B16 | ~4 | 待定 | 待开始 |
| Query Domain/Evidence | B17 | ~23 | 待定 | 待开始 |
| DeepResearch/Graph | B18 | ~17 | 待定 | 待开始 |
| Governance Domain | B19 | ~5 | 待定 | 待开始 |
| 全局复扫 | B20 | — | — | 待开始 |
