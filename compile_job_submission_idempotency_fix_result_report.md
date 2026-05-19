# compile job active 提交幂等修复结果报告

## 1. 修改了哪些文件和方法

- `src/main/java/com/xbk/lattice/compiler/service/CompileJobService.java`
  - 修改 `submitInternal(...)`：保存新 `compile_jobs` 记录前，先按提交目标查询已有 active job；命中时直接返回已有 `CompileJobRecord`。
  - 新增 `findActiveSubmissionJob(...)`：封装 active job 幂等查询入口。
  - 新增 `shouldAllowSourceIdOnlyMatch(...)`：仅允许显式传入、且不是 `default-source` 的 managed source 使用 sourceId-only 匹配。
  - 新增 `normalizeSourceDir(...)`：对提交路径做 `toAbsolutePath().normalize()` 归一。
- `src/main/java/com/xbk/lattice/infra/persistence/CompileJobJdbcRepository.java`
  - 新增 `findActiveBySubmissionTarget(...)`，查询同一提交目标下已有 active compile job。
- `src/main/java/com/xbk/lattice/infra/persistence/mapper/CompileJobMapper.java`
  - 新增 `findActiveBySubmissionTarget(...)` mapper 方法。
- `src/main/resources/com/xbk/lattice/infra/persistence/mapper/CompileJobMapper.xml`
  - 新增 `findActiveBySubmissionTarget` SQL。
- `src/test/java/com/xbk/lattice/compiler/service/CompileJobServiceTests.java`
  - 新增 service 层幂等测试。
- `src/test/java/com/xbk/lattice/infra/persistence/CompileJobJdbcRepositoryTests.java`
  - 新增 repository / mapper 层 active job 查询测试。

## 2. active job 判定范围

仅判定以下状态为 active job：

- `QUEUED`
- `RUNNING`

`SUCCEEDED` / `FAILED` 不作为幂等命中。

## 3. 幂等匹配 key

按以下提交目标匹配 active job：

1. `source_sync_run_id` 相同。
2. 归一后的 `source_dir` 相同。
3. `source_id + source_dir` 相同。
4. 显式传入且非 `default-source` 的 managed `source_id` 相同。

`source_dir` 在进入新 job 保存前会先归一，避免同一路径不同写法造成重复提交。

## 4. 命中时是否返回已有 jobId

是。命中 active job 时，`CompileJobService.submitInternal(...)` 直接返回已有 `CompileJobRecord`，因此调用方拿到已有 `jobId`。

命中时不会插入新的 `compile_jobs` 记录，也不会修改旧 job 状态。

## 5. 是否避免 default-source 全局互斥

是。`default-source` 不允许仅凭 `source_id` 做全局互斥。

direct compile 仍可通过相同 normalized `source_dir` 命中幂等，但不会因为都落到 `default-source` 就互相阻塞。

## 6. SUCCEEDED / FAILED 是否仍可重新提交

是。SQL 只查询 `status in ('QUEUED', 'RUNNING')`，因此已经结束的 `SUCCEEDED` / `FAILED` job 不会命中幂等，可以后续重新提交。

## 7. 是否修改数据库 schema

否。本轮未修改 `src/main/resources/db/schema.sql`，未新增唯一索引。

## 8. 是否清库或删除数据

否。本轮未清库，未删除历史重复数据。

## 9. redline BLOCKER 是否仍为 0

是。

- `bash scripts/scan-redline.sh special_cases_report.md`
- 结果：`BLOCKER=0`，`REVIEW=1860`，`ALLOWLIST=244`

## 10. mvn test 是否通过

通过。

- 定向测试：
  - 命令：`mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository -Dtest=CompileJobServiceTests,CompileJobJdbcRepositoryTests test`
  - 结果：`Tests run: 13, Failures: 0, Errors: 0, Skipped: 0`
- 全量测试：
  - 命令：`mvn -s .codex/maven-settings.xml -Dmaven.repo.local=/Users/sxie/maven/repository test`
  - 结果：`Tests run: 834, Failures: 0, Errors: 0, Skipped: 0`

## 11. 是否新增测试，覆盖哪些场景

是，新增 / 补强了以下覆盖：

- service 层：
  - 同一 `sourceDir` 已有 active job 时返回已有 job，不保存新 job。
  - managed 非 `default-source` sourceId-only 命中 active job 时返回已有 job。
- repository / mapper 层：
  - `source_sync_run_id` 命中 active job。
  - `source_dir` 命中 active job。
  - `default-source` 不因 sourceId-only 形成全局互斥。
  - managed source 允许 sourceId-only 命中 active job。
  - `SUCCEEDED` / `FAILED` terminal job 不命中 active 查询。

## 12. 下一步建议

交给 agentD 做重复提交运行时验证：在真实后台入口连续提交同一编译目标，确认第二次提交返回已有 active `jobId`，且 `compile_jobs` 不新增重复 `QUEUED` / `RUNNING` 记录。
