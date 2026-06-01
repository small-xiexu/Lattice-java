package com.xbk.lattice.api.admin;

/**
 * 资料源创建请求。
 *
 * <p>承载 Git 资料源创建所需的配置参数，由 Spring MVC 从 JSON 请求体绑定。
 * remoteUrl 可能包含 access token（如 https://token@host/repo.git），
 * 禁止对此类加 {@code @Data} 或任何会在 toString() 中输出该字段的注解。
 *
 * @author xiexu
 */
public class AdminSourceCreateRequest {

    /**
     * 资料源编码。
     *
     * <p>对应 knowledge_sources.source_code，创建后不可变。用于跨环境引用和 API 标识。</p>
     */
    private String sourceCode;

    /**
     * 展示名称。
     *
     * <p>调用方在后台管理界面展示的资料源可读名称。</p>
     */
    private String name;

    /**
     * 内容画像。
     *
     * <p>取值 DOCUMENT 或 CODE，决定编译解析策略——DOCUMENT 走文档解析链路，
     * CODE 走代码 AST 抽取链路。</p>
     */
    private String contentProfile;

    /**
     * 可见性。
     *
     * <p>取值 NORMAL 或 ADMIN_ONLY。ADMIN_ONLY 的资料源在普通查询中不可见，
     * 仅管理端可访问。</p>
     */
    private String visibility;

    /**
     * 默认同步模式。
     *
     * <p>取值 AUTO、FULL 或 INCREMENTAL。AUTO 模式由系统根据首次/后续同步自动选择；
     * FULL 每次全量拉取；INCREMENTAL 增量拉取。</p>
     */
    private String defaultSyncMode;

    /**
     * Git 远程仓库地址。
     *
     * <p>可能包含 access token（如 https://token@host/repo.git）。
     * 对应的 source domain 对象会在存储前分离 URL 和凭证。</p>
     */
    private String remoteUrl;

    /**
     * Git 分支名。
     *
     * <p>指定要拉取和编译的目标分支。</p>
     */
    private String branch;

    /**
     * 关联凭据编码引用。
     *
     * <p>指向 source_credentials.credential_code，用于从凭据存储中获取访问该仓库的认证信息。
     * 为空时表示无需认证的公开仓库。</p>
     */
    private String credentialRef;

    public String getSourceCode() {
        return sourceCode;
    }

    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getContentProfile() {
        return contentProfile;
    }

    public void setContentProfile(String contentProfile) {
        this.contentProfile = contentProfile;
    }

    public String getVisibility() {
        return visibility;
    }

    public void setVisibility(String visibility) {
        this.visibility = visibility;
    }

    public String getDefaultSyncMode() {
        return defaultSyncMode;
    }

    public void setDefaultSyncMode(String defaultSyncMode) {
        this.defaultSyncMode = defaultSyncMode;
    }

    public String getRemoteUrl() {
        return remoteUrl;
    }

    public void setRemoteUrl(String remoteUrl) {
        this.remoteUrl = remoteUrl;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public String getCredentialRef() {
        return credentialRef;
    }

    public void setCredentialRef(String credentialRef) {
        this.credentialRef = credentialRef;
    }
}
