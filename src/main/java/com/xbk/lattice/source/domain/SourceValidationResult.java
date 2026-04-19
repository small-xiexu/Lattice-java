package com.xbk.lattice.source.domain;

/**
 * 资料源校验结果。
 *
 * 职责：承载管理员校验 Git / SERVER_DIR 资料源配置后的反馈
 *
 * @author xiexu
 */
public class SourceValidationResult {

    private final boolean valid;

    private final String sourceType;

    private final String message;

    private final String resolvedRef;

    private final String branch;

    private final String gitCommit;

    /**
     * 创建资料源校验结果。
     *
     * @param valid 是否通过
     * @param sourceType 资料源类型
     * @param message 提示信息
     * @param resolvedRef 解析后的引用
     * @param branch 分支
     * @param gitCommit Git 提交
     */
    public SourceValidationResult(
            boolean valid,
            String sourceType,
            String message,
            String resolvedRef,
            String branch,
            String gitCommit
    ) {
        this.valid = valid;
        this.sourceType = sourceType;
        this.message = message;
        this.resolvedRef = resolvedRef;
        this.branch = branch;
        this.gitCommit = gitCommit;
    }

    public boolean isValid() {
        return valid;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getMessage() {
        return message;
    }

    public String getResolvedRef() {
        return resolvedRef;
    }

    public String getBranch() {
        return branch;
    }

    public String getGitCommit() {
        return gitCommit;
    }
}
