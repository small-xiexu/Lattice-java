package com.xbk.lattice.api.admin;

/**
 * 资料源校验响应。
 *
 * 职责：对外返回 Git / SERVER_DIR 资料源校验结果
 *
 * @author xiexu
 */
public class AdminSourceValidationResponse {

    private final boolean valid;

    private final String sourceType;

    private final String message;

    private final String resolvedRef;

    private final String branch;

    private final String gitCommit;

    public AdminSourceValidationResponse(
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
