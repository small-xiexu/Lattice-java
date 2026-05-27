# fresh-eval-2026-05 资料包生成报告

## 生成范围

本轮只在 `docs/test/knowledge-base-e2e/fresh-eval-2026-05/` 目录下生成 public fresh eval 资料包。

未清库、未重建 schema、未导入资料、未调用真实模型或 API、未跑 query 验收、未 stage、未 commit、未 push。

## 文件清单

- `README.md`
- `sources/01_markdown/lab-safety-management-handbook.md`
- `sources/02_structured/equipment-borrowing-policy.yaml`
- `sources/03_xlsx/chemical-storage-grading.xlsx`
- `sources/04_pdf/lab-emergency-response-procedures.pdf`
- `sources/05_csv/equipment-maintenance-schedule.csv`
- `eval/question-set.md`
- `fresh_eval_assets_generation_report.md`

## 内容覆盖

- Markdown 手册覆盖化学品分类存储、紧急洗眼冲淋设备、废弃试剂处置、实验室准入制度、安全检查周期、人员职责定义，以及安全员、设备管理员、实验指导教师职责区别。
- YAML 覆盖 `borrowing_system.api_endpoint`、`borrowing_system.version`、`borrowing_system.max_concurrent_requests`、三类设备、sibling 字段和审批链阶段。
- XLSX 覆盖 6 条化学品存储分级记录。
- PDF 覆盖化学品泄漏、火灾响应、人员受伤、设备故障和响应等级表。
- CSV 覆盖 3 条设备维护保养计划记录。
- 题集覆盖 FQ1-FQ12、FS1-FS4、FG1-FG3。

## 生成边界

本资料包是 public fresh eval，不是 hidden eval。后续导入、清库、API 验收和模型调用应交给 agentD 执行。

禁止把题目、答案、关键词、文件名、case id 写入 `src/main/java/**`、prompt、配置、脚本或 allowlist。

## 自检结论

- [x] 目录结构完整
- [x] 5 个 source 文件存在
- [x] `question-set.md` 包含 FQ1-FQ12、FS1-FS4、FG1-FG3
- [x] 未发现真实凭据或密钥形态字符串
- [x] XLSX 与 PDF 为真实文件

## 校验记录

- 目录校验：已确认 README、5 个 source 文件、题集和本生成报告均位于 `fresh-eval-2026-05/` 目录下。
- 题集校验：已确认题集包含 FQ1-FQ12、FS1-FS4、FG1-FG3。
- XLSX 校验：已读回 `chemical-storage-grading.xlsx` 的 `A1:F7`，确认 6 条化学品记录、危险等级、存储条件、最大存放量和保管人角色存在；公式错误扫描无命中；渲染检查通过。
- PDF 校验：已抽取 `lab-emergency-response-procedures.pdf` 文本，确认包含化学品泄漏、火灾响应、人员受伤、设备故障和响应等级表；已渲染分页缩略图并检查版面无明显截断或重叠。
- 凭据校验：已扫描常见凭据形态字符串，未发现真实密钥、访问令牌或口令。
