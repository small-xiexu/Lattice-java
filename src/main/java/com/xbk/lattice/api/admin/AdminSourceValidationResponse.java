package com.xbk.lattice.api.admin;

import lombok.Getter;

/**
 * 资料源校验响应。
 *
 * <p>对外返回 Git 资料源的校验结果，由 {@code AdminSourceController.validateSource()}
 * 构造。valid 为 false 时 message 包含失败原因；为 true 时 resolvedRef/branch/gitCommit
 * 包含解析后的仓库信息。
 *
 * @author xiexu
 */
@Getter
public class AdminSourceValidationResponse {

    /**
     * 校验是否通过。
     *
     * <p>为 true 表示远程仓库可达、分支存在、凭证有效。为 false 时调用方应读取 message
     * 向用户展示失败原因。Lombok @Getter 对 boolean 生成 isValid()。</p>
     */
    private final boolean valid;

    /**
     * 资料源类型。
     *
     * <p>如 GIT、LOCAL 等，标识校验的资料源类别。</p>
     */
    private final String sourceType;

    /**
     * 校验消息。
     *
     * <p>valid=true 时通常为成功提示；valid=false 时包含具体失败原因（如"仓库不存在""认证失败""分支未找到"等），
     * 调用方直接展示给用户。</p>
     */
    private final String message;

    /**
     * 解析后的引用。
     *
     * <p>Git 仓库校验时解析到的实际引用（如 refs/heads/main）。</p>
     */
    private final String resolvedRef;

    /**
     * 校验到的分支名。
     *
     * <p>从远程仓库解析到的目标分支名称。</p>
     */
    private final String branch;

    /**
     * Git 提交哈希。
     *
     * <p>校验时目标分支的最新 commit SHA，调用方可通过它确认仓库的具体版本。</p>
     */
    private final String gitCommit;

    /**
     * 创建资料源校验响应。
     *
     * @param valid 校验是否通过
     * @param sourceType 资料源类型
     * @param message 校验消息
     * @param resolvedRef 解析后的引用
     * @param branch 校验到的分支
     * @param gitCommit Git 提交哈希
     */
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
}
