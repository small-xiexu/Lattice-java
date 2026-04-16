package com.xbk.lattice.governance;

import java.util.List;

/**
 * Lint 自动修复结果
 *
 * 职责：承载单次 lint fix 的修复统计与错误目标
 *
 * @author xiexu
 */
public class LintFixResult {

    private final int fixed;

    private final int skipped;

    private final List<String> errors;

    /**
     * 创建 lint 自动修复结果。
     *
     * @param fixed 已修复数
     * @param skipped 已跳过数
     * @param errors 出错目标
     */
    public LintFixResult(int fixed, int skipped, List<String> errors) {
        this.fixed = fixed;
        this.skipped = skipped;
        this.errors = errors;
    }

    public int getFixed() {
        return fixed;
    }

    public int getSkipped() {
        return skipped;
    }

    public List<String> getErrors() {
        return errors;
    }
}
