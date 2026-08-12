## 变更内容

<!-- 说明问题、根因与本 PR 的单一目标。 -->

## 行为与风险

<!-- 说明用户可见变化，以及文件、Shell、网络、模型、MCP、权限或供应链影响。 -->

## 验证证据

<!-- 列出实际运行的命令和结果；未运行的检查请说明原因。 -->

- [ ] 已运行 `bash scripts/scan-redline.sh /tmp/lattice-special-cases-report.md`
- [ ] 已运行 `mvn --batch-mode --no-transfer-progress clean package`
- [ ] 已增加或更新覆盖行为变化的测试
- [ ] 未提交凭据、私有数据或敏感日志
- [ ] 未针对特定文档、文件名、业务术语、问题样例或答案写硬编码
- [ ] 已更新受影响的文档或配置说明
