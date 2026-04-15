package com.xbk.lattice.governance;

import java.util.List;

/**
 * Lint 报告
 *
 * 职责：承载治理检查的维度与问题列表
 *
 * @author xiexu
 */
public class LintReport {

    private final List<String> checkedDimensions;

    private final List<LintIssue> issues;

    /**
     * 创建 Lint 报告。
     *
     * @param checkedDimensions 已检查维度
     * @param issues 问题列表
     */
    public LintReport(List<String> checkedDimensions, List<LintIssue> issues) {
        this.checkedDimensions = checkedDimensions;
        this.issues = issues;
    }

    /**
     * 获取已检查维度。
     *
     * @return 已检查维度
     */
    public List<String> getCheckedDimensions() {
        return checkedDimensions;
    }

    /**
     * 获取问题列表。
     *
     * @return 问题列表
     */
    public List<LintIssue> getIssues() {
        return issues;
    }

    /**
     * 获取问题总数。
     *
     * @return 问题总数
     */
    public int getTotalIssues() {
        return issues.size();
    }
}
