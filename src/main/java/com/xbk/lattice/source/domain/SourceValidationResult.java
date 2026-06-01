package com.xbk.lattice.source.domain;

import lombok.Getter;

/**
 * 资料源校验结果。
 *
 * <p>承载管理员校验 Git 资料源配置后的反馈——含连通性、分支和 commit 信息。
 *
 * @author xiexu
 */
@Getter
public class SourceValidationResult {

    /** 校验是否通过。 */
    private final boolean valid;
    /** 资料源类型（如 GIT）。 */
    private final String sourceType;
    /** 提示信息（失败时含错误原因）。 */
    private final String message;
    /** 解析后的 Git 引用（如 branch/tag/commit）。 */
    private final String resolvedRef;
    /** 分支名。 */
    private final String branch;
    /** Git commit hash。 */
    private final String gitCommit;

    public SourceValidationResult(
            boolean valid, String sourceType, String message,
            String resolvedRef, String branch, String gitCommit
    ) {
        this.valid = valid;
        this.sourceType = sourceType;
        this.message = message;
        this.resolvedRef = resolvedRef;
        this.branch = branch;
        this.gitCommit = gitCommit;
    }
}
