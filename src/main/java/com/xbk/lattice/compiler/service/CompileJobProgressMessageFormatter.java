package com.xbk.lattice.compiler.service;

/**
 * 编译作业进度文案格式化器
 *
 * 职责：统一运行态快照中的进度提示文案格式
 *
 * @author xiexu
 */
final class CompileJobProgressMessageFormatter {

    private CompileJobProgressMessageFormatter() {
    }

    /**
     * 格式化进度提示文案。
     *
     * @param action 动作说明
     * @param current 当前进度
     * @param total 总进度
     * @param targetLabel 目标标签
     * @return 格式化后的进度文案
     */
    static String format(String action, int current, int total, String targetLabel) {
        StringBuilder builder = new StringBuilder();
        builder.append(action)
                .append("（")
                .append(current)
                .append("/")
                .append(total)
                .append("）");
        if (targetLabel == null || targetLabel.isBlank()) {
            return builder.toString();
        }
        return builder.append("：").append(targetLabel).toString();
    }
}
