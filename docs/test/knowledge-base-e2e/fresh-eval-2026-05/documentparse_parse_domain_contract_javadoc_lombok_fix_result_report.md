# B14a: DocumentParse 管道对象契约治理报告

改造时间：2026-06-01
改造人：agentA
批次：B14a（B14 第 1 子批次，5/10 类）

---

## 1. 修改文件清单

| 文件 | 类型 | 变更 |
|---|---|---|
| `DocumentParseMode.java` | 枚举 | 5 枚举值 Javadoc（TEXT_READ/OFFICE_EXTRACT/PDF_TEXT/OCR_IMAGE/OCR_SCANNED_PDF） |
| `ParseCapability.java` | 枚举 | 2 枚举值 Javadoc（IMAGE_OCR/SCANNED_PDF_OCR） |
| `DocumentParseResult.java` | 不可变 | `@Getter`，删除 10 getter，10 字段 Javadoc |
| `ParseOutput.java` | 不可变 | `@Getter`，删除 12 getter，12 字段 Javadoc，保留 4 业务方法 |
| `ParseRequest.java` | 不可变 | `@Getter`，删除 5 getter，5 字段 Javadoc（Path 类型特殊标注） |

---

## 2. Lombok 统计

| 类 | @Getter | 删除 getter |
|---|---|---|
| `DocumentParseResult` | 1 | 10 |
| `ParseOutput` | 1 | 12 |
| `ParseRequest` | 1 | 5 |
| **合计** | **3** | **27** |

**未使用**：@Data、@Setter、@Builder

---

## 3. 关键保留

| 类 | 保留项 |
|---|---|
| `ParseOutput` | `hasResolvedContent()`、`resolveContent()`、`resolveContentFormat()`、`hasText()`（4 个业务方法，非 getter） |
| `DocumentParseMode` | `code` 字段、构造器、`getCode()` |
| 全部 | 构造器签名和逻辑 |

---

## 4. 关键字段 Javadoc 标注

- `DocumentParseResult.extractedText`：可能为大型文本
- `ParseOutput.plainText/markdown/structuredContentJson`：大文本/大 JSON 风险
- `ParseRequest.workspaceRoot/filePath`：`java.nio.file.Path` 类型，路径安全规范标注
- `DocumentParseResult.parseMode`：驱动下游编译消费路径

---

## 5. 验证

```
mvn compile: BUILD SUCCESS
无 @Data/@Builder ✓
@Getter: 3/3 ✓
hasResolvedContent/resolveContent/resolveContentFormat: 保留 ✓
```

---

## 6. B14 剩余

| 子批次 | 状态 | 类数 |
|---|---|---|
| **B14a** | **已完成** | 5 |
| B14b | 待开始 | 5 (ProviderDescriptor/ParseRoutePolicy/ProbeResult) |

## 7. 合规确认

| 约束 | 状态 |
|---|---|
| 修改 5 个目标文件 | 通过 |
| 仅 @Getter，无 @Data/@Setter | 通过 |
| ParseOutput 业务方法保留 | 通过（4 个） |
| 枚举构造器/getCode 未改 | 通过 |
| 未 stage/commit/push | 通过 |
